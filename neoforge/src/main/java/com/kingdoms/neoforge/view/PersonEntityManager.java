package com.kingdoms.neoforge.view;

import com.kingdoms.neoforge.KingdomsAttachments;
import com.kingdoms.neoforge.KingdomsEntities;
import com.kingdoms.neoforge.KingdomsItems;
import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.neoforge.bridge.NeoForgeWorldBridge;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.neoforge.world.BlueprintPlacer;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.view.EmbodimentPlanner;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Executes the embodiment plan: spawns villagers for people players can see,
 * writes their state back and despawns them when nobody can.
 *
 * <p>All decisions live in {@link EmbodimentPlanner} in the loader-free core â€”
 * this class only carries them out. The invariants it maintains:
 * <ul>
 *   <li>at most one entity per person, ever;</li>
 *   <li>the {@code Person} record is the authority â€” entities are disposable
 *       views, culled on sight if they leak to disk;</li>
 *   <li>positions flow entity â†’ record every cycle, so a crash loses at most a
 *       second of movement and never a person.</li>
 * </ul>
 */
public final class PersonEntityManager {

    /** Game ticks between manager passes (20 = once a second). */
    public static final int TICK_INTERVAL = 20;

    /** Close enough to a destination counts as arrived; the wander goal takes over. */
    private static final double ARRIVE_RADIUS = 8.0;

    private static final double WALK_SPEED = 0.6;

    /** Civilians run (not stroll) for home while the settlement is threatened. */
    private static final double SHELTER_SPEED = 0.9;

    /** Hunger debuffs are reapplied every manager pass; this outlasts the gap. */
    private static final int EFFECT_REFRESH_TICKS = 40;

    /** Game ticks between construction passes — brisk, so hands look busy. */
    public static final int CONSTRUCTION_TICK_INTERVAL = 5;

    /** How close a builder must be to lay a block. Generous: roofs have nowhere to stand. */
    private static final double PLACE_REACH = 6.0;

    /** Passes a site may make no progress before a block is placed regardless. */
    private static final int STALL_PASSES_BEFORE_ASSIST = 20;

    /** Sites that have made no progress recently, by settlement id. */
    private final Map<UUID, Integer> constructionStalls = new HashMap<>();

    /** How far to hunt for a spot a body fits when spawning someone in. */
    private static final int FOOTING_SEARCH = 8;

    /** A drop of at least this counts as stranded rather than a kerb. */
    private static final int MIN_STRANDED_DROP = 3;

    /** How far down to look for a floor to climb to. */
    private static final int DESCENT_SEARCH = 24;

    /** Beyond this drop, staying put beats a fatal fall. */
    private static final int SURVIVABLE_DROP = 16;

    /** Passes standing still up high before someone climbs down. */
    private static final int STRANDED_PASSES = 3;

    /** People going nowhere while stranded aloft, by person id. */
    private final Map<UUID, Integer> strandedPasses = new HashMap<>();

    /** Houses are 5x5, so their door sits two blocks south of the origin. */
    private static final int HOUSE_DOOR_OFFSET = 2;

    /** Close enough, horizontally and vertically, to count as home. */
    private static final double HOME_ARRIVED = 4.0;

    /** Beyond this they are not locked out, they are simply elsewhere. */
    private static final double STAIRS_WOULD_HELP_WITHIN = 24.0;

    /** A door this far above them is a climb no settler can make unaided. */
    private static final int STAIR_MIN_CLIMB = 2;

    /** Passes failing to get in before the town is asked to build steps. */
    private static final int ACCESS_FAIL_PASSES = 8;

    /** People who cannot get into their own house, by person id. */
    private final Map<UUID, Integer> homeAccessFailures = new HashMap<>();

    /** Guards engage hostiles within this range, strike within melee reach. */
    private static final double GUARD_ENGAGE_RANGE = 20.0;
    private static final double GUARD_STRIKE_RANGE = 2.5;
    private static final double GUARD_CHARGE_SPEED = 0.9;
    private static final float GUARD_DAMAGE = 4.0F;

    private final ServerLevel level;
    private final SimWorld world;

    /** Person id â†’ the live view entity for that person. */
    private final Map<UUID, PersonEntity> tracked = new HashMap<>();

    public PersonEntityManager(ServerLevel level, SimWorld world) {
        this.level = Objects.requireNonNull(level, "level");
        this.world = Objects.requireNonNull(world, "world");
    }

    /** One pass: sync positions, release the unwatched, embody the watched, herd stragglers. */
    public void tick() {
        renderClaimBorders();
        boolean changed = false;
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                changed |= syncPositions(settlement);

                EmbodimentPlanner.Plan plan =
                        EmbodimentPlanner.plan(settlement, world.bridge(), world.settings());
                for (Person person : plan.toRelease()) {
                    release(person);
                    changed = true;
                }
                for (Person person : plan.toEmbody()) {
                    changed |= embody(person);
                }

                dailyRoutine(settlement);
                checkHouseAccess(settlement);
                changed |= workLumberjacks(settlement);
                freeStrandedPeople(settlement);
                applyHungerEffects(settlement);
                guardCombat(settlement);
            }
        }
        reapOrphans();
        if (changed) {
            KingdomsSavedData.get(level).setDirty();
        }
    }

    /**
     * Builders lay the structure by hand, one block each per pass.
     *
     * <p>Each healthy embodied builder is given the next block in the plan: if it
     * is within reach they look at it, swing, and place it; otherwise they walk
     * toward it. So the wall genuinely rises under the hands of the people
     * standing there, in mason's order, paced by the simulation's own progress.
     *
     * <p>If builders are present but boxed out of reach — a roof course with
     * nowhere to stand — a stall counter places one anyway after a few seconds,
     * so a site can never deadlock on pathfinding.
     */
    /**
     * Lay every block the builders have been cleared for, immediately.
     *
     * <p>For {@code /civ step}, which advances the simulation without any game
     * ticks passing — so builders never get a chance to walk anywhere and the
     * normal reach-gated path would place nothing at all. Stepping should move
     * the masonry forward by exactly what it granted, not bank an allowance for
     * the completion pass to stamp in later.
     */
    public void flushConstruction() {
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                if (settlement.buildQueue().isEmpty()) {
                    continue;
                }
                BuildTask task = settlement.buildQueue().getFirst();
                if (!BlueprintPlacer.isBuildableByHand(level, task)) {
                    continue;
                }
                BlueprintPlacer.prepareSite(level, task);

                List<PersonEntity> builders = embodiedBuilders(settlement);
                // The plan is a hard ceiling on the loop: placeNextBlock also
                // stops on its own, but a build that cannot progress must not be
                // able to spin here.
                int guard = task.planSize() + 1;
                while (task.pendingBlocks() > 0 && guard-- > 0) {
                    if (!builders.isEmpty()) {
                        builders.getFirst().swing(InteractionHand.MAIN_HAND);
                    }
                    if (!BlueprintPlacer.placeNextBlock(level, task)) {
                        break;
                    }
                }
            }
        }
    }

    public void tickConstruction() {
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                if (settlement.buildQueue().isEmpty()) {
                    continue;
                }
                BuildTask task = settlement.buildQueue().getFirst();
                if (!BlueprintPlacer.isBuildableByHand(level, task)) {
                    continue;
                }
                List<PersonEntity> builders = embodiedBuilders(settlement);
                if (builders.isEmpty()) {
                    continue;   // nobody here to build; it materializes on return
                }
                BlueprintPlacer.prepareSite(level, task);

                boolean placedAny = false;
                for (PersonEntity builder : builders) {
                    BlueprintPlacer.NextBlock next = BlueprintPlacer.nextBlock(level, task);
                    if (next == null) {
                        clearHands(builder);
                        continue;   // as far along as the current work allows
                    }
                    // Carry the material: the builder is holding the very block
                    // they are about to lay, so placement reads as work rather
                    // than staring blocks into existence.
                    carry(builder, next.block());

                    BlockPos pos = next.pos();
                    if (builder.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)
                            <= PLACE_REACH * PLACE_REACH) {
                        builder.getLookControl().setLookAt(
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        builder.swing(InteractionHand.MAIN_HAND);
                        placedAny |= BlueprintPlacer.placeNextBlock(level, task);
                    } else {
                        builder.getNavigation().moveTo(
                                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, WALK_SPEED);
                    }
                }

                UUID key = settlement.id().value();
                if (placedAny) {
                    constructionStalls.remove(key);
                } else if (BlueprintPlacer.nextBlock(level, task) != null) {
                    int stalled = constructionStalls.merge(key, 1, Integer::sum);
                    if (stalled >= STALL_PASSES_BEFORE_ASSIST) {
                        // Still credited to a builder, so no block is ever laid
                        // without a visible hand behind it.
                        builders.getFirst().swing(InteractionHand.MAIN_HAND);
                        BlueprintPlacer.placeNextBlock(level, task);
                        constructionStalls.remove(key);
                    }
                }
            }
        }
    }

    // --- footing ---

    /**
     * A Y where a body actually fits at this column, preferring the recorded one.
     *
     * <p>Never the motion-blocking surface: that is the top of whatever stands
     * there, so spawning "on the surface" at a house drops people onto its roof.
     * Searches down first (the usual case — the record points at a floor under a
     * roof), then up, then gives up and uses the surface.
     */
    private int standableY(SimPos pos) {
        BlockPos start = new BlockPos(pos.x(), pos.y(), pos.z());
        if (fitsBody(start)) {
            return start.getY();
        }
        for (int d = 1; d <= FOOTING_SEARCH; d++) {
            if (fitsBody(start.below(d))) {
                return start.getY() - d;
            }
            if (fitsBody(start.above(d))) {
                return start.getY() + d;
            }
        }
        return world.bridge().surfaceHeight(pos);
    }

    /** Two blocks of clear space on something solid. */
    private boolean fitsBody(BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).blocksMotion();
    }

    /**
     * Gets people down off roofs they should never have been on.
     *
     * <p>Somebody standing well above a floor they could occupy, going nowhere for
     * several seconds, climbs down to it — but only when the drop is survivable.
     * Stranded higher than that, they stay put, because falling would kill them.
     */
    private void freeStrandedPeople(Settlement settlement) {
        for (Person person : settlement.residents()) {
            UUID key = person.id().value();
            PersonEntity view = person.isEmbodied() ? tracked.get(key) : null;
            if (view == null || view.isRemoved() || !view.getNavigation().isDone()) {
                strandedPasses.remove(key);
                continue;
            }
            BlockPos at = view.blockPosition();
            int floor = floorBelow(at);
            if (floor == Integer.MIN_VALUE) {
                strandedPasses.remove(key);
                continue;
            }
            if (strandedPasses.merge(key, 1, Integer::sum) < STRANDED_PASSES) {
                continue;
            }
            strandedPasses.remove(key);
            if (at.getY() - floor <= SURVIVABLE_DROP) {
                view.snapTo(at.getX() + 0.5, floor, at.getZ() + 0.5);
                person.setPosition(NeoForgeWorldBridge.toSimPos(view.blockPosition()));
            }
        }
    }

    /** First standable floor meaningfully below this spot, or MIN_VALUE if none. */
    private int floorBelow(BlockPos from) {
        for (int dy = MIN_STRANDED_DROP; dy <= DESCENT_SEARCH; dy++) {
            BlockPos candidate = from.below(dy);
            if (fitsBody(candidate)) {
                return candidate.getY();
            }
        }
        return Integer.MIN_VALUE;
    }

    /** What a hauled load looks like in somebody's hands. */
    private static final Item CARGO_ITEM = Items.WHEAT;

    private static void carry(PersonEntity person, Block block) {
        carry(person, block.asItem());
    }

    /** Put something in the person's hand, if not already held. */
    private static void carry(PersonEntity builder, Item item) {
        ItemStack held = builder.getMainHandItem();
        if (held.is(item)) {
            return;
        }
        builder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        // Materials are scenery, not loot — a killed builder must not shower
        // the ground with the cobblestone they happened to be holding.
        builder.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    /** Down tools — nothing left to lay, or the day is done. */
    private static void clearHands(PersonEntity builder) {
        if (!builder.getMainHandItem().isEmpty()) {
            builder.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /**
     * Notices residents who cannot get into their own homes, and has the town
     * build them steps.
     *
     * <p>Only counted while they are actually trying to go home — at dusk, or
     * sheltering from a raid — and only when the door stands above them and they
     * are near enough that a flight of steps is the answer. Somebody merely out
     * at the fields is not locked out.
     *
     * <p>Failures must persist for {@link #ACCESS_FAIL_PASSES} passes before an
     * order goes in, so one unlucky bit of pathfinding does not commission
     * masonry.
     */
    private void checkHouseAccess(Settlement settlement) {
        boolean headingHome = level.isDarkOutside() || settlement.threatLevel() > 0;
        if (!headingHome) {
            homeAccessFailures.clear();
            return;
        }
        for (Household household : settlement.households()) {
            if (!household.isHoused()) {
                continue;
            }
            SimPos home = household.home();
            for (Person.Id memberId : household.members()) {
                Person person = settlement.resident(memberId);
                if (person == null || !person.isEmbodied()) {
                    continue;
                }
                UUID key = memberId.value();
                PersonEntity view = tracked.get(key);
                if (view == null || view.isRemoved()) {
                    homeAccessFailures.remove(key);
                    continue;
                }

                double dx = view.getX() - (home.x() + 0.5);
                double dz = view.getZ() - (home.z() + 0.5);
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                int climb = home.y() - view.getBlockY();

                boolean home_ = horizontal <= HOME_ARRIVED && Math.abs(climb) <= 1;
                boolean lockedOut = !home_
                        && climb >= STAIR_MIN_CLIMB
                        && horizontal <= STAIRS_WOULD_HELP_WITHIN
                        && view.getNavigation().isDone();
                if (!lockedOut) {
                    homeAccessFailures.remove(key);
                    continue;
                }
                if (homeAccessFailures.merge(key, 1, Integer::sum) < ACCESS_FAIL_PASSES) {
                    continue;
                }
                homeAccessFailures.remove(key);

                SimPos doorway = new SimPos(home.x(), home.y() + 1, home.z() + HOUSE_DOOR_OFFSET);
                if (BuildPlanner.requestAccessStairs(settlement, doorway, climb, world.stepsElapsed())) {
                    KingdomsSavedData.get(level).setDirty();
                    KingdomsMod.LOGGER.info("{} cannot reach home; steps ordered at {}",
                            person.name(), doorway);
                }
            }
        }
    }

    /**
     * Lumberjacks fell and replant inside the camp's work area. Like builders,
     * they steer themselves while working, so the daily routine leaves them be.
     */
    private boolean workLumberjacks(Settlement settlement) {
        if (settlement.lumberArea() == null || level.isDarkOutside()
                || settlement.threatLevel() > 0) {
            return false;
        }
        boolean changed = false;
        for (Person person : settlement.residents()) {
            if (person.profession() != Profession.LUMBERJACK
                    || !person.isEmbodied()
                    || person.isTooWeakToWork()
                    || person.haul() != null) {
                continue;
            }
            PersonEntity view = tracked.get(person.id().value());
            if (view != null && !view.isRemoved()) {
                changed |= LumberjackWorker.work(level, settlement, view);
            }
        }
        return changed;
    }

    private List<PersonEntity> embodiedBuilders(Settlement settlement) {
        List<PersonEntity> builders = new ArrayList<>();
        for (Person person : settlement.residents()) {
            if (person.profession() != Profession.BUILDER
                    || !person.isEmbodied()
                    || person.isTooWeakToWork()) {
                continue;
            }
            PersonEntity view = tracked.get(person.id().value());
            if (view != null && !view.isRemoved()) {
                builders.add(view);
            }
        }
        return builders;
    }

    /**
     * Hunger made visible. Weak (60+) people move slowly; the severely starved
     * (90+) barely crawl and hit like children. Effects refresh each second so
     * they lift on their own once the person eats.
     */
    private void applyHungerEffects(Settlement settlement) {
        for (Person person : settlement.residents()) {
            if (!person.isEmbodied() || person.hunger() < Person.HUNGER_WEAK) {
                continue;
            }
            PersonEntity view = tracked.get(person.id().value());
            if (view == null || view.isRemoved()) {
                continue;
            }
            if (person.hunger() >= Person.HUNGER_SEVERE) {
                view.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EFFECT_REFRESH_TICKS, 1), null);
                view.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_REFRESH_TICKS, 0), null);
            } else {
                view.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EFFECT_REFRESH_TICKS, 0), null);
            }
        }
    }

    /**
     * A person can die inside the simulation — starvation, off-screen raid math —
     * while their view entity still stands. The record is the authority: the
     * orphaned body collapses here, on screen, rather than living on as a ghost.
     */
    private void reapOrphans() {
        for (Map.Entry<UUID, PersonEntity> entry : List.copyOf(tracked.entrySet())) {
            if (world.settlementOf(new Person.Id(entry.getKey())).isPresent()) {
                continue;
            }
            PersonEntity view = entry.getValue();
            tracked.remove(entry.getKey());
            if (view != null && !view.isRemoved()) {
                view.hurtServer(level, level.damageSources().starve(), Float.MAX_VALUE);
                if (!view.isRemoved() && !view.isDeadOrDying()) {
                    view.discard();
                }
            }
        }
    }

    /**
     * Guards fight. Once a second each embodied guard picks the nearest hostile
     * in range: close enough, they strike; otherwise they close the distance.
     *
     * <p>Deliberately puppeteered from here rather than grafted onto the view
     * brain â€” vanilla villagers cannot fight, and mixing custom goals into a
     * brain-driven mob makes two AIs wrestle over the navigator. The hostiles
     * retaliate through normal vanilla anger, so guards genuinely can lose.
     */
    private void guardCombat(Settlement settlement) {
        for (Person person : settlement.residents()) {
            if (person.profession() != Profession.GUARD || !person.isEmbodied()) {
                continue;
            }
            PersonEntity guard = tracked.get(person.id().value());
            if (guard == null || guard.isRemoved()) {
                continue;
            }
            Monster target = nearestHostile(guard);
            if (target == null) {
                continue;
            }
            if (guard.distanceTo(target) <= GUARD_STRIKE_RANGE) {
                guard.swing(InteractionHand.MAIN_HAND);
                target.hurtServer(level, level.damageSources().mobAttack(guard), GUARD_DAMAGE);
            } else {
                guard.getNavigation().moveTo(target, GUARD_CHARGE_SPEED);
            }
        }
    }

    private Monster nearestHostile(PersonEntity guard) {
        AABB box = guard.getBoundingBox().inflate(GUARD_ENGAGE_RANGE);
        return level.getEntitiesOfClass(Monster.class, box, LivingEntity::isAlive).stream()
                .min(Comparator.comparingDouble(guard::distanceToSqr))
                .orElse(null);
    }

    /** Entity positions are truth while embodied â€” copy them into the records. */
    private boolean syncPositions(Settlement settlement) {
        boolean changed = false;
        for (Person person : settlement.residents()) {
            if (!person.isEmbodied()) {
                continue;
            }
            PersonEntity view = tracked.get(person.id().value());
            if (view == null || view.isRemoved()) {
                // The entity vanished outside our control (chunk unloaded under it,
                // another mod removed it). The person is unharmed; drop the view
                // and let the next plan respawn it if anyone is still watching.
                tracked.remove(person.id().value());
                person.setEmbodied(false);
                changed = true;
                continue;
            }
            person.setPosition(NeoForgeWorldBridge.toSimPos(view.blockPosition()));
            changed = true;
        }
        return changed;
    }

    private boolean embody(Person person) {
        PersonEntity view = new PersonEntity(KingdomsEntities.PERSON.get(), level);
        SimPos pos = person.position();
        int y = standableY(pos);
        view.setPos(pos.x() + 0.5, y, pos.z() + 0.5);
        view.setCustomName(Component.literal(person.name() + " — " + pretty(person)));
        view.setCustomNameVisible(true);
        view.setPersistenceRequired();
        view.setData(KingdomsAttachments.PERSON_ID.get(), person.id().value());

        // Registered before addFreshEntity so the join hook recognises our own spawn.
        tracked.put(person.id().value(), view);
        if (!level.addFreshEntity(view)) {
            tracked.remove(person.id().value());
            return false;
        }
        person.setEmbodied(true);
        return true;
    }

    private void release(Person person) {
        PersonEntity view = tracked.remove(person.id().value());
        if (view != null && !view.isRemoved()) {
            person.setPosition(NeoForgeWorldBridge.toSimPos(view.blockPosition()));
            view.discard();
        }
        person.setEmbodied(false);
    }

    /**
     * The village day. Threatened civilians run home; at night everyone but the
     * watch turns in; by day people head to their work — farmers to the fields,
     * builders to the site, traders to the storehouse, guards to the tower,
     * idlers about their homes. The wander goal mills them around whatever spot
     * this chooses, so the town reads as lived-in rather than marched.
     */
    private void dailyRoutine(Settlement settlement) {
        boolean night = level.isDarkOutside();
        boolean underThreat = settlement.threatLevel() > 0;

        Map<UUID, SimPos> homes = new HashMap<>();
        for (Household household : settlement.households()) {
            if (household.isHoused()) {
                for (Person.Id member : household.members()) {
                    homes.put(member.value(), household.home());
                }
            }
        }

        for (Person person : settlement.residents()) {
            if (!person.isEmbodied()) {
                continue;
            }
            PersonEntity view = tracked.get(person.id().value());
            if (view == null || view.isRemoved()) {
                continue;
            }

            boolean guard = person.profession() == Profession.GUARD;
            SimPos home = homes.get(person.id().value());

            // Builders on an active site are steered block by block by
            // tickConstruction; overriding them here would tug them off the wall.
            if (person.profession() == Profession.BUILDER
                    && !underThreat && !night
                    && !settlement.buildQueue().isEmpty()
                    && !person.isTooWeakToWork()) {
                continue;
            }
            if (person.profession() == Profession.LUMBERJACK
                    && !underThreat && !night
                    && person.haul() == null
                    && settlement.lumberArea() != null
                    && !person.isTooWeakToWork()) {
                continue;   // steered tree by tree in workLumberjacks
            }
            if (person.profession() == Profession.BUILDER) {
                // Reached only when not on an active site — work is finished, the
                // day is over, danger is near, or they are too hungry. Down tools.
                clearHands(view);
            }
            // Show the load: a hauler carrying grain is visibly carrying grain,
            // and sets it down the moment it is delivered.
            HaulTask carrying = person.haul();
            if (carrying != null && carrying.isLoaded()) {
                carry(view, CARGO_ITEM);
            } else if (person.profession() != Profession.BUILDER) {
                clearHands(view);
            }

            SimPos target;
            double speed;
            if (underThreat && !guard) {
                target = home != null ? home : settlement.centre();
                speed = SHELTER_SPEED;
            } else if (night && !guard) {
                target = home != null ? home : settlement.centre();
                speed = WALK_SPEED;
            } else {
                target = workplaceFor(settlement, person, home);
                speed = WALK_SPEED;
            }

            double dx = view.getX() - (target.x() + 0.5);
            double dz = view.getZ() - (target.z() + 0.5);
            double arrive = underThreat && !guard ? 2.0 : ARRIVE_RADIUS;
            if (dx * dx + dz * dz > arrive * arrive) {
                // The target's own Y, never the surface heightmap: a building's
                // "surface" is its ROOF, and routing people there is what put
                // villagers on rooftops in the first place.
                view.getNavigation().moveTo(target.x() + 0.5, target.y(), target.z() + 0.5, speed);
            }
        }
    }

    private SimPos workplaceFor(Settlement settlement, Person person, SimPos home) {
        // An errand outranks the day job: haulers walk to the store they are
        // collecting from, then to the one they are delivering to.
        HaulTask haul = person.haul();
        if (haul != null) {
            return haul.target();
        }
        return switch (person.profession()) {
            case FARMER -> nearestBuilding(settlement, "farm", person.position());
            case BUILDER -> settlement.buildQueue().isEmpty()
                    ? nearestBuilding(settlement, "hall", person.position())
                    : settlement.buildQueue().getFirst().origin();
            case TRADER -> nearestBuilding(settlement, "market", person.position());
            case LUMBERJACK -> settlement.lumberArea() != null
                    ? settlement.lumberArea().centre()
                    : nearestBuilding(settlement, "lumber_camp", person.position());
            case GUARD -> nearestBuilding(settlement, "watchtower", person.position());
            case IDLER -> home != null ? home : settlement.centre();
        };
    }

    /** Nearest completed building whose blueprint path ends with the suffix, else the centre. */
    private static SimPos nearestBuilding(Settlement settlement, String pathSuffix, SimPos from) {
        SimPos best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Building building : settlement.buildings()) {
            if (!building.blueprintId().endsWith(pathSuffix)) {
                continue;
            }
            long d = building.origin().horizontalDistanceSq(from);
            if (d < bestDistance) {
                bestDistance = d;
                best = building.origin();
            }
        }
        return best != null ? best : settlement.centre();
    }

    /** Whether this exact entity is the live view we spawned for this person. */
    public boolean owns(UUID personId, Entity entity) {
        return tracked.get(personId) == entity;
    }

    /**
     * A view view died, so the person it represented dies with it. This is
     * the one place the view writes anything other than a position back into
     * the simulation â€” deliberate, and the seed of the Phase 3 defense loop.
     */
    public void onViewEntityDeath(LivingEntity view) {
        UUID personId = view.getData(KingdomsAttachments.PERSON_ID.get());
        tracked.remove(personId);

        Person.Id id = new Person.Id(personId);
        world.settlementOf(id).ifPresent(settlement -> {
            Person fallen = settlement.removePerson(id);
            if (fallen != null) {
                settlement.logEvent(world.stepsElapsed(), fallen.name() + " was killed");
                KingdomsMod.LOGGER.info("{} of {} was killed", fallen.name(), settlement.name());
                KingdomsSavedData.get(level).setDirty();
            }
        });
    }

    /** Write everything back and drop every view. Called as the server stops. */
    public void releaseAll() {
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                for (Person person : settlement.residents()) {
                    if (person.isEmbodied()) {
                        release(person);
                    }
                }
            }
        }
        tracked.clear();
        KingdomsSavedData.get(level).setDirty();
    }

    private static String pretty(Person person) {
        String name = person.profession().name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // --- claim borders ---

    /** How far from the border line a player still sees it. */
    private static final double BORDER_VIEW_RANGE = 64.0;

    /** Blocks between sparkles along the border. */
    private static final double BORDER_POINT_SPACING = 3.0;

    /**
     * Players holding a Founding Charter see every nearby settlement's claim as a
     * ring of green sparkles laid over the terrain. Server-side particles only â€”
     * no client rendering code, so it works for vanilla-client observers too.
     */
    private void renderClaimBorders() {
        for (ServerPlayer player : level.players()) {
            if (!player.getMainHandItem().is(KingdomsItems.FOUNDING_CHARTER.get())
                    && !player.getOffhandItem().is(KingdomsItems.FOUNDING_CHARTER.get())) {
                continue;
            }
            for (Kingdom kingdom : world.kingdoms()) {
                for (Settlement settlement : kingdom.settlements()) {
                    drawBorderNear(player, settlement);
                }
            }
        }
    }

    private void drawBorderNear(ServerPlayer player, Settlement settlement) {
        double radius = settlement.claimRadius();
        SimPos centre = settlement.centre();
        double dx = player.getX() - centre.x();
        double dz = player.getZ() - centre.z();
        if (Math.sqrt(dx * dx + dz * dz) > radius + BORDER_VIEW_RANGE) {
            return;
        }
        int points = Math.max(16, (int) (2 * Math.PI * radius / BORDER_POINT_SPACING));
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = centre.x() + radius * Math.cos(angle) + 0.5;
            double z = centre.z() + radius * Math.sin(angle) + 0.5;
            double px = player.getX() - x;
            double pz = player.getZ() - z;
            if (px * px + pz * pz > BORDER_VIEW_RANGE * BORDER_VIEW_RANGE) {
                continue;   // draw only the arc the player can actually see
            }
            int y = world.bridge().surfaceHeight(new SimPos((int) Math.floor(x), centre.y(), (int) Math.floor(z)));
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.6, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

}

