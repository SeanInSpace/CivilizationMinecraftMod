package com.kingdoms.neoforge.view;

import com.kingdoms.neoforge.KingdomsAttachments;
import com.kingdoms.neoforge.KingdomsEntities;
import com.kingdoms.neoforge.KingdomsItems;
import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.neoforge.bridge.NeoForgeWorldBridge;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.neoforge.world.BlueprintPlacer;
import com.kingdoms.neoforge.world.PathLayer;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.settlement.Tallies;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
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

    /**
     * How close a builder must be to the site to count as working on it.
     *
     * <p>Wider than {@link #PLACE_REACH}, because a builder standing on a finished
     * course is genuinely present even when the next block is across the footprint
     * — but far short of "somewhere in town", which is what let buildings finish
     * with nobody there.
     */
    private static final double SITE_RADIUS = 16.0;

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

    /** How many times to ask somebody to walk down before carrying them. */
    private static final int REPATH_ATTEMPTS = 3;

    /** People going nowhere while stranded aloft, by person id. */
    private final Map<UUID, Integer> strandedPasses = new HashMap<>();

    /** Repath attempts spent per person before a teleport is allowed. */
    private final Map<UUID, Integer> repathTries = new HashMap<>();

    /** Buildings already joined to the hall, so a path is laid once per session. */
    private final java.util.Set<String> pathsLaid = new java.util.HashSet<>();

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
        renderBuildingBorders();
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
                changed |= workMiners(settlement);
                changed |= workShepherds(settlement);
                layPaths(settlement);
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
                if (BlueprintPlacer.isSiteBlocked(level, task)) {
                    settlement.abandonBuild(world.stepsElapsed(), "the ground will not give");
                    continue;
                }

                List<PersonEntity> builders = buildersAtSite(settlement, task);
                if (builders.isEmpty()) {
                    continue;   // nobody is at the site, so stepping builds nothing
                }
                // The plan is a hard ceiling on the loop: placeNextBlock also
                // stops on its own, but a build that cannot progress must not be
                // able to spin here.
                int guard = task.planWork() + 1;
                while (BlueprintPlacer.nextStep(level, settlement, task) != null && guard-- > 0) {
                    builders.getFirst().swing(InteractionHand.MAIN_HAND);
                    if (!BlueprintPlacer.completeStep(level, settlement, task)) {
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
                if (BlueprintPlacer.isSiteBlocked(level, task)) {
                    settlement.abandonBuild(world.stepsElapsed(), "the ground will not give");
                    continue;
                }
                // Out of something? Go and build whatever makes it.
                BlueprintPlacer.Shortage shortage =
                        BlueprintPlacer.shortageFor(level, settlement, task);
                if (shortage != null) {
                    BuildPlanner.requestProducer(settlement, shortage.resource(), world.stepsElapsed());
                }

                boolean workedAny = false;
                for (PersonEntity builder : builders) {
                    BlueprintPlacer.NextStep next = BlueprintPlacer.nextStep(level, settlement, task);
                    if (next == null) {
                        clearHands(builder);
                        continue;   // as far along as the current work allows
                    }
                    // Hold the right thing for the job: the block about to be laid,
                    // or the tool for the ground about to come out. Either way the
                    // work reads as work rather than blocks blinking in and out.
                    Item held = BlueprintPlacer.toolFor(level, task);
                    if (held != null) {
                        carry(builder, held);
                    }

                    // Materials do not appear in a builder's hands. If this step
                    // needs stock they are not carrying, they go and fetch a load
                    // from the stores first — which is what makes the warehouse a
                    // building rather than a number.
                    String material = BlueprintPlacer.materialOfStep(level, task);
                    Person carrier = personOf(settlement, builder);
                    boolean paying = material != null && carrier != null;
                    if (paying && !carrier.carries(material)
                            && !fetchLoad(settlement, carrier, builder, material)) {
                        continue;   // on the road to the stores
                    }

                    BlockPos pos = next.pos();
                    if (builder.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)
                            <= PLACE_REACH * PLACE_REACH) {
                        builder.getLookControl().setLookAt(
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        builder.swing(InteractionHand.MAIN_HAND);
                        // A swing at a stubborn block is progress even when the
                        // block does not give yet, or a long dig would read as a
                        // stall and get assisted out from under the builder.
                        boolean laid = BlueprintPlacer.swingAtStep(
                                level, settlement, task, paying);
                        if (laid && paying) {
                            carrier.spendCarry();
                        }
                        workedAny = true;
                    } else {
                        builder.getNavigation().moveTo(
                                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, WALK_SPEED);
                    }
                }

                UUID key = settlement.id().value();
                if (workedAny) {
                    constructionStalls.remove(key);
                } else if (BlueprintPlacer.nextStep(level, settlement, task) != null) {
                    int stalled = constructionStalls.merge(key, 1, Integer::sum);
                    if (stalled >= STALL_PASSES_BEFORE_ASSIST) {
                        // Only for a builder who is genuinely at the site and
                        // simply cannot path to this one block. No hand present,
                        // no block — a stall is not a licence to build remotely.
                        List<PersonEntity> present = buildersAtSite(settlement, task);
                        if (!present.isEmpty()) {
                            present.getFirst().swing(InteractionHand.MAIN_HAND);
                            BlueprintPlacer.completeStep(level, settlement, task);
                        }
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
    /**
     * Somewhere at this column a body actually fits.
     *
     * <p>Looks up before it looks down, and never searches past solid ground on
     * the way down. Preferring the first fit in either direction put settlers in
     * caves under the village; being stuck on a roof is a nuisance, being sealed
     * underground is not recoverable.
     */
    private int standableY(SimPos pos) {
        BlockPos start = new BlockPos(pos.x(), pos.y(), pos.z());
        if (fitsBody(start)) {
            return start.getY();
        }
        for (int d = 1; d <= FOOTING_SEARCH; d++) {
            if (fitsBody(start.above(d))) {
                return start.getY() + d;
            }
        }
        for (int d = 1; d <= FOOTING_SEARCH; d++) {
            BlockPos candidate = start.below(d);
            if (fitsBody(candidate)) {
                return candidate.getY();
            }
            if (level.getBlockState(candidate).blocksMotion()) {
                break;   // the ground: whatever is under it is not ours to stand on
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
    /**
     * Gets somebody down off wherever they have got stuck.
     *
     * <p>Walking first, always. A teleport is a last resort that looks like a bug
     * even when it is correct, so a stranded settler is asked to path down several
     * times over before anybody moves them by hand — and only then if the drop is
     * survivable.
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
                repathTries.remove(key);   // not stuck; the patience resets with them
                continue;
            }
            if (strandedPasses.merge(key, 1, Integer::sum) < STRANDED_PASSES) {
                continue;
            }

            int tried = repathTries.getOrDefault(key, 0);
            if (tried < REPATH_ATTEMPTS
                    && view.getNavigation().moveTo(
                            at.getX() + 0.5, floor, at.getZ() + 0.5, WALK_SPEED)) {
                // A route exists. Let them take it, and come back to this if the
                // walk does not actually get them anywhere.
                repathTries.put(key, tried + 1);
                strandedPasses.remove(key);
                continue;
            }

            strandedPasses.remove(key);
            repathTries.remove(key);
            if (at.getY() - floor <= SURVIVABLE_DROP) {
                view.snapTo(at.getX() + 0.5, floor, at.getZ() + 0.5);
                person.setPosition(NeoForgeWorldBridge.toSimPos(view.blockPosition()));
            }
        }
    }

    /** First standable floor meaningfully below this spot, or MIN_VALUE if none. */
    /**
     * Where a straight drop from here would actually land, or nothing.
     *
     * <p>Stops at the first thing in the way. Scanning on past it used to find the
     * floor of whatever cave happened to run under the village and treat that as a
     * fine place to put somebody standing on the grass above it — the whole town
     * relocated underground one block at a time.
     */
    private int floorBelow(BlockPos from) {
        for (int dy = 1; dy <= DESCENT_SEARCH; dy++) {
            BlockPos candidate = from.below(dy);
            if (!level.getBlockState(candidate).blocksMotion()) {
                continue;   // still falling
            }
            BlockPos feet = candidate.above();
            int drop = from.getY() - feet.getY();
            if (drop < MIN_STRANDED_DROP || !fitsBody(feet)) {
                return Integer.MIN_VALUE;
            }
            return feet.getY();
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
    /** Miners cut stone by daylight and under no threat, same terms as the woodland. */
    /** Shepherds stock the pens, one beast at a time, by daylight. */
    /**
     * Joins the town's doors up with trodden ways.
     *
     * <p>One building a pass, and only ones not yet connected — laying a whole
     * town's paths in a single tick would be a visible hitch, and there is no
     * hurry. Paths run to the hall, which is what makes a settlement read as
     * having a centre rather than being a scatter of huts.
     */
    private boolean layPaths(Settlement settlement) {
        if (settlement.buildings().size() < 2) {
            return false;
        }
        SimPos hall = null;
        for (Building building : settlement.buildings()) {
            if (BuildPlanner.baseIdOf(building.blueprintId()).endsWith("town_hall")) {
                hall = building.origin();
                break;
            }
        }
        if (hall == null) {
            return false;   // no centre to join up to yet
        }
        for (Building building : settlement.buildings()) {
            if (building.origin().equals(hall) || !building.isMaterialized()) {
                continue;
            }
            String key = settlement.id().value() + "@" + building.origin();
            if (!pathsLaid.add(key)) {
                continue;
            }
            // Doors face south, so aim a block out from the doorway rather than
            // at the middle of the building, and the track meets the entrance.
            SimPos door = new SimPos(building.origin().x(),
                    building.origin().y(), building.origin().z() + HOUSE_DOOR_OFFSET + 1);
            PathLayer.connect(level, door, hall);
            return true;
        }
        return false;
    }

    private boolean workShepherds(Settlement settlement) {
        if (settlement.threatLevel() > 0) {
            return false;
        }
        boolean changed = false;
        for (Person person : settlement.residents()) {
            if (person.profession() != Profession.SHEPHERD
                    || !person.isEmbodied()
                    || person.isTooWeakToWork()
                    || person.haul() != null) {
                continue;
            }
            PersonEntity view = tracked.get(person.id().value());
            if (view != null && !view.isRemoved()) {
                changed |= ShepherdWorker.work(level, settlement, view);
            }
        }
        return changed;
    }

    private boolean workMiners(Settlement settlement) {
        if (settlement.mineArea() == null || settlement.threatLevel() > 0) {
            return false;
        }
        boolean changed = false;
        for (Person person : settlement.residents()) {
            if (person.profession() != Profession.MINER
                    || !person.isEmbodied()
                    || person.isTooWeakToWork()
                    || person.haul() != null) {
                continue;
            }
            PersonEntity view = tracked.get(person.id().value());
            if (view != null && !view.isRemoved()) {
                changed |= MinerWorker.work(level, settlement, view);
            }
        }
        return changed;
    }

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

    /** How much a builder shoulders in one trip to the stores. */
    private static final int LOAD_SIZE = 16;

    /** How close a builder has to be to the stores to load up. */
    private static final double LOAD_REACH = 4.0;

    /**
     * Sends a builder for materials, and loads them up once they arrive.
     *
     * <p>The stock leaves the ledger at the moment it is picked up, not when it is
     * laid — so a load in transit is genuinely out of the stores, and a builder
     * killed carrying one takes it with them.
     *
     * @return true if they are loaded and can get on with it
     */
    private boolean fetchLoad(Settlement settlement, Person carrier, PersonEntity builder,
                              String material) {
        SimPos stores = storesPos(settlement);
        double dx = builder.getX() - (stores.x() + 0.5);
        double dz = builder.getZ() - (stores.z() + 0.5);
        if (dx * dx + dz * dz > LOAD_REACH * LOAD_REACH) {
            builder.getNavigation().moveTo(stores.x() + 0.5, stores.y(), stores.z() + 0.5, WALK_SPEED);
            return false;
        }
        int drawn = settlement.stores().takeUpTo(material, LOAD_SIZE);
        if (drawn <= 0) {
            return false;   // the stores are empty; the shortage is reported elsewhere
        }
        carrier.setCarry(material, drawn);
        builder.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /** Where a builder goes to load up: the warehouse, else a storehouse, else the hall. */
    private static SimPos storesPos(Settlement settlement) {
        SimPos fallback = settlement.centre();
        for (Building building : settlement.buildings()) {
            if (building.blueprintId().contains("warehouse")) {
                return building.origin();
            }
            if (building.blueprintId().contains("storehouse")) {
                fallback = building.origin();
            }
        }
        return fallback;
    }

    /** The record behind an embodied builder, or null if they are not tracked. */
    private Person personOf(Settlement settlement, PersonEntity view) {
        for (Person person : settlement.residents()) {
            if (view == tracked.get(person.id().value())) {
                return person;
            }
        }
        return null;
    }

    /** Embodied builders standing close enough to the site to be working on it. */
    private List<PersonEntity> buildersAtSite(Settlement settlement, BuildTask task) {
        SimPos site = task.site();
        List<PersonEntity> present = new ArrayList<>();
        for (PersonEntity builder : embodiedBuilders(settlement)) {
            if (builder.distanceToSqr(site.x() + 0.5, site.y(), site.z() + 0.5)
                    <= SITE_RADIUS * SITE_RADIUS) {
                present.add(builder);
            }
        }
        return present;
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
    /**
     * Draws a guard whatever the forge has made for them.
     *
     * <p>Taken from the rack once and kept: the stores pay for the kit, the guard
     * wears it, and a town that never built a smithy sends its watch out with
     * bare hands. That is the whole reason to build one.
     */
    private void arm(Settlement settlement, PersonEntity guard) {
        if (guard.getMainHandItem().isEmpty()
                && settlement.stores().take(TownStores.WEAPONS, 1)) {
            guard.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        }
        if (guard.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                && settlement.stores().take(TownStores.ARMOUR, 1)) {
            guard.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        }
    }

    /** A sword hits harder than a fist. */
    private static float armedBonus(PersonEntity guard) {
        return guard.getMainHandItem().is(Items.IRON_SWORD) ? 3.0F : 0.0F;
    }

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
            arm(settlement, guard);
            if (guard.distanceTo(target) <= GUARD_STRIKE_RANGE) {
                guard.swing(InteractionHand.MAIN_HAND);
                boolean wasAlive = target.isAlive();
                target.hurtServer(level, level.damageSources().mobAttack(guard),
                        GUARD_DAMAGE + armedBonus(guard));
                if (wasAlive && !target.isAlive()) {
                    settlement.tallies().record(Tallies.MOBS_SLAIN);
                }
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
            case MINER -> settlement.mineArea() != null
                    ? settlement.mineArea().centre()
                    : nearestBuilding(settlement, "mine", person.position());
            case SMITH -> nearestBuilding(settlement, "smith", person.position());
            case SHEPHERD -> nearestBuilding(settlement, "animal_farm", person.position());
            case GUARD -> nearestBuilding(settlement, "watchtower", person.position());
            case IDLER -> home != null ? home : settlement.centre();
        };
    }

    /** Nearest completed building whose blueprint path ends with the suffix, else the centre. */
    private static SimPos nearestBuilding(Settlement settlement, String pathSuffix, SimPos from) {
        SimPos best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Building building : settlement.buildings()) {
            if (!BuildPlanner.baseIdOf(building.blueprintId()).endsWith(pathSuffix)) {
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

    // --- building outlines ---

    /** How far a building's outline is drawn from the player. */
    private static final double OUTLINE_VIEW_RANGE = 48.0;

    /** Blocks between sparkles along an outline edge. */
    private static final int OUTLINE_SPACING = 2;

    /** Tallest outline drawn, so a watchtower does not become a pillar of light. */
    private static final int OUTLINE_MAX_HEIGHT = 12;

    /**
     * Players holding a Surveyor's Lamp see every nearby building's bounds as a
     * wireframe of sparks.
     *
     * <p>Server-side particles, like the charter's claim ring — no client render
     * code, so a vanilla client sees it too. Only the floor and roof rectangles
     * and the four corner posts are drawn: filling the volume would hide the
     * building it is meant to describe.
     *
     * <p>Buildings whose plan has never been built have no recorded size and are
     * skipped rather than guessed at.
     */
    private void renderBuildingBorders() {
        for (ServerPlayer player : level.players()) {
            if (!holdingLamp(player)) {
                continue;
            }
            for (Kingdom kingdom : world.kingdoms()) {
                for (Settlement settlement : kingdom.settlements()) {
                    for (Building building : settlement.buildings()) {
                        outline(player, building);
                    }
                }
            }
        }
    }

    private static boolean holdingLamp(ServerPlayer player) {
        return player.getMainHandItem().is(KingdomsItems.SURVEYORS_LAMP.get())
                || player.getOffhandItem().is(KingdomsItems.SURVEYORS_LAMP.get());
    }

    private void outline(ServerPlayer player, Building building) {
        Footprint footprint = building.footprint();
        if (!footprint.isKnown()) {
            // Raised before sizes were recorded. Measure it once, now that
            // somebody is here to look at it, so worlds that predate this
            // feature light up too rather than staying dark forever.
            footprint = BlueprintPlacer.measure(level, building.blueprintId(),
                    new BlockPos(building.origin().x(), building.origin().y(),
                            building.origin().z()));
            if (!footprint.isKnown()) {
                return;
            }
            building.setFootprint(footprint);
            KingdomsSavedData.get(level).setDirty();
        }
        SimPos origin = building.origin();
        double dx = player.getX() - origin.x();
        double dz = player.getZ() - origin.z();
        if (dx * dx + dz * dz > OUTLINE_VIEW_RANGE * OUTLINE_VIEW_RANGE) {
            return;
        }

        int rx = footprint.width() / 2;
        int rz = footprint.depth() / 2;
        int floor = origin.y();
        int roof = floor + Math.min(footprint.height(), OUTLINE_MAX_HEIGHT);

        // The two rectangles, floor and roof.
        for (int x = -rx; x <= rx; x += OUTLINE_SPACING) {
            spark(origin.x() + x, floor, origin.z() - rz);
            spark(origin.x() + x, floor, origin.z() + rz);
            spark(origin.x() + x, roof, origin.z() - rz);
            spark(origin.x() + x, roof, origin.z() + rz);
        }
        for (int z = -rz; z <= rz; z += OUTLINE_SPACING) {
            spark(origin.x() - rx, floor, origin.z() + z);
            spark(origin.x() + rx, floor, origin.z() + z);
            spark(origin.x() - rx, roof, origin.z() + z);
            spark(origin.x() + rx, roof, origin.z() + z);
        }
        // And the four posts joining them, so the box reads as a volume.
        for (int y = floor; y <= roof; y += OUTLINE_SPACING) {
            spark(origin.x() - rx, y, origin.z() - rz);
            spark(origin.x() - rx, y, origin.z() + rz);
            spark(origin.x() + rx, y, origin.z() - rz);
            spark(origin.x() + rx, y, origin.z() + rz);
        }
    }

    private void spark(int x, int y, int z) {
        level.sendParticles(ParticleTypes.END_ROD,
                x + 0.5, y + 0.5, z + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
