package com.kingdoms.neoforge.view;

import com.kingdoms.neoforge.KingdomsAttachments;
import com.kingdoms.neoforge.KingdomsEntities;
import com.kingdoms.neoforge.KingdomsItems;
import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.neoforge.bridge.NeoForgeWorldBridge;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.neoforge.world.BlueprintPlacer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.kingdoms.neoforge.world.Excavation;
import com.kingdoms.neoforge.world.PathLayer;
import com.kingdoms.neoforge.world.PerimeterLayer;
import com.kingdoms.neoforge.world.StoreSync;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.settlement.Tallies;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.person.BuildLoad;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.neoforge.bridge.Menace;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import com.kingdoms.neoforge.world.HandDig;
import com.kingdoms.sim.economy.Economy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import com.kingdoms.sim.settlement.Alarm;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingRole;
import com.kingdoms.sim.settlement.FieldRoster;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.Stock;
import com.kingdoms.sim.view.EmbodimentPlanner;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
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
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;

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

    /** Gates we swung open for somebody, and when. The town closes its gates. */
    private final Map<BlockPos, Long> heldOpenGates = new HashMap<>();

    /** Ticks a gate stays open after someone passes, before the town shuts it. */
    private static final long GATE_CLOSE_AFTER_TICKS = 60L;

    /** How near a person must be for a gate to swing, or to stay open for them. */
    private static final double GATE_REACH = 2.0;

    /**
     * The hole being dug for a settlement's current build.
     *
     * <p>Keyed by settlement and stamped with what it belongs to, so moving on to
     * the next building opens a new one rather than inheriting a half-finished
     * job that was measured somewhere else.
     */
    private static final class SiteDig {
        final String blueprint;
        final BlockPos site;
        final Excavation yard;
        int credited;
        long opened = Long.MIN_VALUE;
        boolean reported;
        /** Ticks this hole was actually the job in hand, as opposed to elapsed. */
        int workedTicks;

        SiteDig(String blueprint, BlockPos site, Excavation yard) {
            this.blueprint = blueprint;
            this.site = site;
            this.yard = yard;
        }
    }

    /**
     * Holes by the exact job they belong to, not merely by town.
     *
     * <p>A settlement can be pulled off a build and back onto it: a flight of
     * access steps jumps the queue the moment somebody cannot get home, and the
     * town hall it interrupted has to be waiting where it was left. Keyed by the
     * whole job so the interrupted hole is still there afterwards, half dug.
     */
    private record DigKey(UUID settlement, String blueprint, BlockPos site) {
    }

    private final Map<DigKey, SiteDig> siteDigs = new HashMap<>();

    /** How long a hole may stay open before it is worth complaining about. */
    private static final int STALLED_DIG_TICKS = 1200;

    /** Clearances a player asked for by hand, which outrank the build queue. */
    private final Map<UUID, Excavation> clearOrders = new HashMap<>();

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
    /** Which stretch of each town's road network is next for inspection. */
    private final Map<UUID, Integer> pathCursor = new HashMap<>();

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

    /** Ticks between survey redraws. */
    private static final int SURVEY_EVERY = 4;

    private int surveyBeat;

    /**
     * How often this manager's own pass really runs.
     *
     * <p>Per manager, never static, and the reason is on the record: a static
     * counter shared across three dimension managers once read the Nether's
     * empty world as the overworld losing its kingdoms. Each dimension is
     * scheduled from the same server tick and falls behind together, but a
     * figure that cannot say which world it came from is not a measurement.
     */
    private final TickRate rate = new TickRate(System::nanoTime);

    public PersonEntityManager(ServerLevel level, SimWorld world) {
        this.level = Objects.requireNonNull(level, "level");
        this.world = Objects.requireNonNull(world, "world");
    }

    /**
     * What this manager's real cadence has been over the last minute.
     *
     * <p>Read by the audit sweep and by {@code /civ info}. Sixty a minute is
     * what {@link #TICK_INTERVAL} asks for; anything much below it means the
     * server never caught up and everything paced per pass is running slow.
     */
    public TickRate rate() {
        return rate;
    }

    /** One pass: sync positions, release the unwatched, embody the watched, herd stragglers. */
    public void tick() {
        // First thing in the pass, so what is timed is how often the pass
        // arrives rather than how long it takes.
        rate.mark();
        // The survey draws every fourth tick rather than every one. Its lines are
        // solid now, which is roughly eight times the particles of the old dotted
        // ones, and a spark outlives four ticks many times over -- so the picture
        // is continuous to look at while costing less than the dots did.
        if (++surveyBeat % SURVEY_EVERY == 0) {
            renderClaimBorders();
            renderBuildingBorders();
        }
        tendGates();
        boolean changed = false;
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                changed |= syncPositions(settlement);

                EmbodimentPlanner.Plan plan =
                        EmbodimentPlanner.plan(settlement, world.bridge(), world.settings());
                for (Person person : plan.toRelease()) {
                    release(settlement, person);
                    changed = true;
                }
                for (Person person : plan.toEmbody()) {
                    changed |= embody(person);
                }

                pickUpLitter(settlement);
                unloadAtStore(settlement);
                ringTheBell(settlement);
                dailyRoutine(settlement);
                checkHouseAccess(settlement);
                changed |= workLumberjacks(settlement);
                workFarmers(settlement);
                changed |= workMiners(settlement);
                changed |= workShepherds(settlement);
                changed |= layPaths(settlement);
                workWall(settlement);
                PerimeterLayer.draw(level, settlement);
                StoreSync.reconcile(level, settlement);
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
                // No game ticks pass during a step, so nothing can be dug a tick
                // at a time. Take the whole hole out at once instead.
                Excavation hole = siteDig(settlement);
                if (hole != null && !hole.isComplete()) {
                    builders.getFirst().swing(InteractionHand.MAIN_HAND);
                    hole.flush(level);
                }
                task.creditExcavation();
                // Deliberately not gated on the carry rule. No game ticks pass
                // during a step, so nobody can walk anywhere: a builder required
                // to fetch a load first would never arrive at the warehouse, and
                // /civ step would build precisely nothing. What the crew is
                // already holding is spent first all the same, and only then does
                // the ledger pay — otherwise a load drawn at the warehouse would
                // be charged for once at pickup and again here, and the same
                // block would come out of the town twice.
                //
                // The plan is a hard ceiling on the loop: completeStep also stops
                // on its own, but a build that cannot progress must not be able
                // to spin here.
                int guard = task.planWork() + 1;
                while (guard-- > 0) {
                    String owed = BlueprintPlacer.materialOwedForStep(level, task);
                    PersonEntity mason = whoIsCarrying(settlement, builders, owed);
                    boolean fromHand = mason != null;
                    if (BlueprintPlacer.nextStep(level, settlement, task,
                            fromHand ? owed : null) == null) {
                        break;
                    }
                    if (!BlueprintPlacer.completeStep(level, settlement, task, fromHand)) {
                        break;
                    }
                    // Whoever paid for it is the one who lays it. Swinging the
                    // first body in the list while a second one's armful went
                    // down is a builder miming somebody else's work.
                    if (fromHand) {
                        mason.swing(InteractionHand.MAIN_HAND);
                        personOf(settlement, mason).spendCarry();
                    } else {
                        builders.getFirst().swing(InteractionHand.MAIN_HAND);
                    }
                }
            }
        }
    }

    // --- gates ---

    /**
     * Gates yield to the people of the town, then close behind them.
     *
     * <p>Vanilla treats a closed fence gate as a wall: mobs will not path
     * through one and cannot open one, which is exactly what keeps animals
     * penned and exactly what kept penning the shepherds in with them. Rather
     * than teaching the pathfinder new physics, gates work like saloon doors —
     * a citizen walking up to one swings it open, and once nobody has been near
     * it for a moment the town shuts it again, so a pen is only ever open for
     * the seconds somebody is actually passing through.
     *
     * <p>Navigation converges on its own: a failed route ends adjacent to the
     * closed gate, the gate opens, and the next of the steering loops' constant
     * repaths goes through. Only gates this opened are ever closed — one a
     * player or a plan left open stays exactly as it was left.
     */
    private void tendGates() {
        long now = level.getGameTime();
        for (PersonEntity person : tracked.values()) {
            if (person.isRemoved()) {
                continue;
            }
            BlockPos feet = person.blockPosition();
            Direction facing = person.getDirection();
            for (BlockPos spot : new BlockPos[]{
                    feet.relative(facing), feet.relative(facing, 2), feet}) {
                BlockState state = level.getBlockState(spot);
                if (state.getBlock() instanceof FenceGateBlock
                        && !state.getValue(FenceGateBlock.OPEN)) {
                    level.setBlock(spot,
                            state.setValue(FenceGateBlock.OPEN, true), Block.UPDATE_ALL);
                    level.levelEvent(null, 1008, spot, 0);
                    heldOpenGates.put(spot.immutable(), now);
                }
            }
        }

        heldOpenGates.entrySet().removeIf(held -> {
            BlockPos gate = held.getKey();
            if (!level.isLoaded(gate)) {
                return true;   // let it be; it will read as left-open, which is honest
            }
            BlockState state = level.getBlockState(gate);
            if (!(state.getBlock() instanceof FenceGateBlock)
                    || !state.getValue(FenceGateBlock.OPEN)) {
                return true;   // gone, or somebody else closed it already
            }
            if (now - held.getValue() < GATE_CLOSE_AFTER_TICKS) {
                return false;
            }
            for (PersonEntity person : tracked.values()) {
                if (!person.isRemoved() && person.distanceToSqr(
                        gate.getX() + 0.5, gate.getY() + 0.5, gate.getZ() + 0.5)
                        <= GATE_REACH * GATE_REACH) {
                    return false;   // still passing through; hold it for them
                }
            }
            level.setBlock(gate,
                    state.setValue(FenceGateBlock.OPEN, false), Block.UPDATE_ALL);
            level.levelEvent(null, 1014, gate, 0);
            return true;
        });
    }

    // --- excavation ---

    /**
     * One tick of digging, for every crew with ground in their way.
     *
     * <p>Runs on <em>every</em> server tick, unlike the rest of construction, and
     * that is the entire point. A block is worth what vanilla says it is worth in
     * ticks, so the only way for a digger to take as long over it as a player
     * would is to be asked the same question at the same rate. Sampling every
     * fifth tick and rounding up is what used to make soft ground look like the
     * builders were pausing for breath halfway through it.
     *
     * <p>The work itself is shared out by {@link Excavation}: top layers first,
     * one three-by-three cell to a digger, claimed from a common pool. There is
     * no standing per-builder assignment to go stale, and no single block for a
     * crowd to fight over.
     */
    /**
     * Advances every settler who is part-way through a block of their own.
     *
     * <p>Every tick, and separately from the excavation, because a lumberjack at
     * a trunk and a miner at a face are not on a building site and have no yard
     * between them. What they share with the excavation is the reason for the
     * cadence: block hardness is counted in ticks, so a coarser pass cannot
     * reproduce the time a player would spend on the same block.
     *
     * <p>The coarse pass chooses the target and walks them to it; this only
     * keeps swinging at whatever they are already on. A worker who has wandered
     * out of reach drops it, and starts again from nothing when they return —
     * swings taken at a trunk you walked away from are not banked.
     */
    private void tickHandWork() {
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                for (Person person : settlement.residents()) {
                    Profession trade = person.profession();
                    if (trade != Profession.LUMBERJACK && trade != Profession.MINER) {
                        continue;
                    }
                    if (!person.isEmbodied()) {
                        continue;
                    }
                    PersonEntity view = tracked.get(person.id().value());
                    if (view == null || view.isRemoved()) {
                        continue;
                    }
                    BlockPos target = HandDig.targetOf(view);
                    if (target == null) {
                        continue;
                    }
                    double reach = trade == Profession.LUMBERJACK
                            ? LumberjackWorker.WORK_REACH : MinerWorker.WORK_REACH;
                    if (view.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5,
                            target.getZ() + 0.5) > reach * reach) {
                        HandDig.stop(level, view);
                        continue;
                    }
                    if (!HandDig.strike(level, view, target)) {
                        continue;
                    }
                    if (trade == Profession.LUMBERJACK) {
                        LumberjackWorker.harvest(level, settlement, view, target);
                    } else {
                        MinerWorker.harvest(level, settlement, view, target);
                    }
                }
            }
        }
    }

    public void tickDigging(long tick) {
        tickHandWork();
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                // No hands, no hole. Opening one for an unwatched town would both
                // log a dig nobody is doing and leave the build waiting on it,
                // when what should happen out of sight is the abstract clock.
                if (embodiedBuilders(settlement).isEmpty()) {
                    continue;
                }
                Excavation ordered = clearOrders.get(settlement.id().value());
                if (ordered != null) {
                    if (workDig(settlement, ordered, tick)) {
                        continue;   // a hand-issued clearance comes before the queue
                    }
                    finishClearOrder(settlement, ordered);
                }
                SiteDig dig = currentDig(settlement);
                if (dig == null || dig.yard.isComplete()) {
                    continue;
                }
                if (dig.opened == Long.MIN_VALUE) {
                    dig.opened = tick;
                    KingdomsMod.LOGGER.info("{} opens a {}-block excavation ({} trees) for {}",
                            settlement.name(), dig.yard.total(), dig.yard.treeCount(),
                            dig.blueprint);
                }
                dig.workedTicks++;
                workDig(settlement, dig.yard, tick);
                creditDig(settlement, dig);
                // A hole that will not close is invisible from outside: the build
                // sits at the same percentage and nothing says why. Say it once.
                // Worked ticks, not elapsed. A hole put down while its town runs
                // off to cut somebody a flight of steps is not a stalled hole, and
                // measuring wall-clock reported it as one.
                if (!dig.yard.isComplete() && dig.workedTicks > STALLED_DIG_TICKS
                        && dig.workedTicks % STALLED_DIG_TICKS == 0) {
                    KingdomsMod.LOGGER.warn("{} has spent {} ticks digging {}: {}",
                            settlement.name(), dig.workedTicks, dig.blueprint,
                            dig.yard.describe());
                }
                if (!dig.reported && dig.yard.isComplete()) {
                    dig.reported = true;
                    KingdomsMod.LOGGER.info(
                            "{} cleared {} blocks for {} in {} ticks ({} out of reach)",
                            settlement.name(), dig.yard.cleared(), dig.blueprint,
                            dig.workedTicks, dig.yard.abandonedCount());
                }
            }
        }
    }

    /**
     * Puts every embodied builder to work on a hole.
     *
     * @return false once there is nothing left in it
     */
    private boolean workDig(Settlement settlement, Excavation yard, long tick) {
        if (yard.isComplete()) {
            return false;
        }
        for (PersonEntity digger : embodiedBuilders(settlement)) {
            yard.serve(level, digger, tick);
        }
        return !yard.isComplete();
    }

    /** Moves blocks already out of the ground onto the build task's progress. */
    private void creditDig(Settlement settlement, SiteDig dig) {
        if (dig == null || settlement.buildQueue().isEmpty()) {
            return;
        }
        BuildTask task = settlement.buildQueue().getFirst();
        int delta = dig.yard.cleared() - dig.credited;
        if (delta > 0) {
            task.recordDigDone(delta);
            dig.credited = dig.yard.cleared();
        }
        if (dig.yard.isComplete()) {
            // The plan counted what stood in the way at survey time. Square the
            // books, so a site partly cleared by other means still reads as done.
            task.creditExcavation();
        }
    }

    /**
     * The hole for this settlement's current build, opened if the site is ready.
     *
     * <p>Null when there is nothing to dig for: no build queued, the site not yet
     * surveyed, or nobody near enough for any of it to be real.
     */
    private Excavation siteDig(Settlement settlement) {
        SiteDig held = currentDig(settlement);
        return held == null ? null : held.yard;
    }

    /** The dig record for whatever this settlement is building now, opening one if needed. */
    private SiteDig currentDig(Settlement settlement) {
        if (settlement.buildQueue().isEmpty()) {
            forgetDigsOf(settlement);
            return null;
        }
        BuildTask task = settlement.buildQueue().getFirst();
        if (task.siteY() == BuildTask.UNSET_SITE_Y
                || !BlueprintPlacer.isBuildableByHand(level, task)) {
            return null;
        }
        BlockPos site = new BlockPos(task.origin().x(), task.siteY(), task.origin().z());
        DigKey key = new DigKey(settlement.id().value(), task.blueprintId(), site);
        SiteDig held = siteDigs.get(key);
        if (held != null) {
            return held;
        }
        // Only keep holes for jobs still in the queue. Without this, a town that
        // abandons a build leaves its excavation behind forever.
        pruneDigs(settlement);
        SiteDig fresh = new SiteDig(task.blueprintId(), site, new Excavation(level,
                BlueprintPlacer.excavationTargets(level, task), task.blueprintId()));
        siteDigs.put(key, fresh);
        return fresh;
    }

    /** Drops holes for jobs this settlement is no longer queued to build. */
    private void pruneDigs(Settlement settlement) {
        Set<BlockPos> wanted = new HashSet<>();
        for (BuildTask queued : settlement.buildQueue()) {
            if (queued.siteY() != BuildTask.UNSET_SITE_Y) {
                wanted.add(new BlockPos(
                        queued.origin().x(), queued.siteY(), queued.origin().z()));
            }
        }
        siteDigs.entrySet().removeIf(entry ->
                entry.getKey().settlement().equals(settlement.id().value())
                        && !wanted.contains(entry.getKey().site()));
    }

    private void forgetDigsOf(Settlement settlement) {
        siteDigs.entrySet().removeIf(
                entry -> entry.getKey().settlement().equals(settlement.id().value()));
    }

    /** Whether this settlement is working a clearance somebody asked for by hand. */
    private boolean isClearing(Settlement settlement) {
        Excavation ordered = clearOrders.get(settlement.id().value());
        return ordered != null && !ordered.isComplete();
    }

    /**
     * Sets a settlement's builders to clearing a box.
     *
     * <p>The test harness for the digging, and useful in its own right. Only
     * blocks that genuinely stand in the way are taken: anything a block can be
     * placed into is skipped, and bedrock is left alone rather than stalling the
     * job forever.
     *
     * <p>Held in memory only. A clearance is a thing you stand and watch, so
     * surviving a restart buys nothing worth the save-format churn; the order is
     * simply forgotten if the server stops mid-dig.
     *
     * @return how many blocks the crew were set to remove
     */
    public int orderClear(Settlement settlement, BlockPos from, BlockPos to) {
        BlockPos min = new BlockPos(Math.min(from.getX(), to.getX()),
                Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
        BlockPos max = new BlockPos(Math.max(from.getX(), to.getX()),
                Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));

        List<SimPos> targets = new ArrayList<>();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.canBeReplaced()) {
                        continue;   // nothing there, or nothing in the way
                    }
                    if (state.getDestroySpeed(level, pos) < 0) {
                        continue;   // bedrock; no crew is getting through that
                    }
                    targets.add(new SimPos(x, y, z));
                }
            }
        }
        cancelClear(settlement);
        if (targets.isEmpty()) {
            return 0;
        }
        clearOrders.put(settlement.id().value(), new Excavation(level, targets, "clearance"));
        return targets.size();
    }

    /** Calls the crew off a clearance. */
    public boolean cancelClear(Settlement settlement) {
        Excavation dropped = clearOrders.remove(settlement.id().value());
        if (dropped == null) {
            return false;
        }
        for (PersonEntity digger : embodiedBuilders(settlement)) {
            dropped.forget(level, digger);
        }
        return true;
    }

    /** How a standing clearance is getting on, or null if there is not one. */
    public Excavation clearOrder(Settlement settlement) {
        return clearOrders.get(settlement.id().value());
    }

    /**
     * What this settlement is digging, if anything, for {@code /civ info}.
     *
     * <p>Worth reporting separately from build progress: a town that looks stuck
     * at four per cent is usually not stuck at all, it is thirty blocks into a
     * hillside, and one line here is the difference between reading that and
     * guessing at it.
     */
    public String digStatus(Settlement settlement) {
        Excavation ordered = clearOrders.get(settlement.id().value());
        if (ordered != null && !ordered.isComplete()) {
            return ordered.describe();
        }
        SiteDig dig = currentDig(settlement);
        if (dig != null && !dig.yard.isComplete()) {
            return dig.yard.describe();
        }
        return null;
    }

    private void finishClearOrder(Settlement settlement, Excavation done) {
        clearOrders.remove(settlement.id().value());
        settlement.logEvent(world.stepsElapsed(),
                "cleared " + done.cleared() + " blocks of ground");
        KingdomsMod.LOGGER.info("{} finished a clearance of {} blocks",
                settlement.name(), done.cleared());
    }

    /** Drops any cell and any half-dug block this person was holding. */
    private void forgetDigger(UUID personId) {
        for (SiteDig dig : siteDigs.values()) {
            dig.yard.forget(level, personId);
        }
        for (Excavation ordered : clearOrders.values()) {
            ordered.forget(level, personId);
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
                // Nothing is laid until the ground is out of the way. Excavation
                // runs on its own clock in tickDigging; this simply waits for it.
                Excavation hole = siteDig(settlement);
                if ((hole != null && !hole.isComplete()) || isClearing(settlement)) {
                    continue;
                }
                // Out of something? Go and build whatever makes it.
                //
                // Asked of the ledger and not of the crew's hands, deliberately.
                // A load a builder is carrying left the books when they picked it
                // up, so a town whose last stone is in somebody's arms really is
                // out of stone — it will be visibly out of it a dozen blocks from
                // now, and ordering the mine a dozen blocks early is the right
                // answer to that. requestProducer refuses one that already stands
                // or is queued, so saying it every pass costs nothing.
                BlueprintPlacer.Shortage shortage =
                        BlueprintPlacer.shortageFor(level, settlement, task);
                if (shortage != null) {
                    BuildPlanner.requestProducer(settlement, shortage.resource(), world.stepsElapsed());
                }

                boolean workedAny = false;
                for (PersonEntity builder : builders) {
                    // What this builder has in hand has to be known before the
                    // step is asked for: a step the town can no longer pay for is
                    // still payable by the builder carrying it, because that
                    // stock left the ledger at the warehouse rather than at the
                    // wall. Asking without it stranded a loaded builder beside
                    // the very wall their load had just emptied the store for.
                    Person carrier = personOf(settlement, builder);
                    String inHand = carrier == null ? null : carrier.carriedMaterial();

                    BlueprintPlacer.NextStep next =
                            BlueprintPlacer.nextStep(level, settlement, task, inHand);
                    if (next == null) {
                        // As far along as the current work allows. A builder
                        // holding the wrong material waits here rather than
                        // walking a load back: the shelves are not underfoot, and
                        // setting stock down where they happen to be standing
                        // would be a teleport dressed up as tidiness. So a load
                        // that does not fit the course sits off the town's books
                        // until the builder is next sent to the stores, or until
                        // they are released and it goes back into the pool. One
                        // armful per builder is the whole of the exposure.
                        clearHands(builder);
                        continue;
                    }
                    String owed = BlueprintPlacer.materialOwedForStep(level, task);
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
                    // building rather than a number, and what stops a block going
                    // down on the strength of stock across the village.
                    if (owed != null
                            && !BuildLoad.canLay(owed, carrier)
                            && !fetchLoad(settlement, carrier, builder, owed)) {
                        continue;   // on the road to the stores, or nothing to load
                    }
                    // Past here the rule holds: either the step costs nothing, or
                    // it is in this builder's hands and was paid for at pickup.
                    boolean fromHand = owed != null;

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
                                level, settlement, task, fromHand);
                        if (laid && fromHand) {
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
                } else if (hasWorkTheCrewCouldDo(settlement, task, builders)) {
                    int stalled = constructionStalls.merge(key, 1, Integer::sum);
                    if (stalled >= STALL_PASSES_BEFORE_ASSIST) {
                        // Reset whether or not the assist found hands to lay it.
                        // Holding the count at the threshold to retry sooner was
                        // tried and is worse: the map is keyed by settlement, so a
                        // primed count outlives the task that earned it and the
                        // next head gets its first block assisted on its first
                        // idle pass, with no grace at all.
                        assistStalledSite(settlement, task);
                        constructionStalls.remove(key);
                    }
                }
            }
        }
    }

    /**
     * Whether the site still has a step somebody could actually do.
     *
     * <p>Not simply "the town can pay for it". A builder who has walked to the
     * warehouse and drawn the last of the timber is holding work the town no
     * longer owns, and a site whose only remaining hope is that armful must
     * still be allowed to look stalled — otherwise the one case the assist exists
     * for, a loaded builder who cannot path into reach, silently stops counting.
     *
     * <p>The second {@code nextStep} is what keeps this from counting every
     * ordinary idle tick. Work is granted once per simulation step and this loop
     * runs several times a second, so most passes have an exhausted budget and no
     * step to do; asking with the material in hand puts the budget and the plan
     * back in the question, and only the ledger out of it.
     */
    private boolean hasWorkTheCrewCouldDo(Settlement settlement, BuildTask task,
                                          List<PersonEntity> crew) {
        if (BlueprintPlacer.nextStep(level, settlement, task) != null) {
            return true;   // the town can pay for it outright
        }
        String owed = BlueprintPlacer.materialOwedForStep(level, task);
        if (owed == null || BlueprintPlacer.nextStep(level, settlement, task, owed) == null) {
            // A free step the town cannot start is out of budget, not stalled;
            // so is a step no load could unlock. Waiting is not stalling.
            return false;
        }
        for (PersonEntity builder : crew) {
            Person carrier = personOf(settlement, builder);
            if (carrier != null && carrier.carries(owed)) {
                return true;   // paid for already, and in somebody's hands
            }
        }
        return false;
    }

    /**
     * A site that has gone nowhere for a while, unwedged by hand.
     *
     * <p>What this is for: a builder who is genuinely at the site and simply
     * cannot path into reach of one block — a roof course with nowhere to stand.
     * Mob navigation cannot climb everything a town builds on, and without this
     * a site can deadlock on pathfinding, which is not a thing a player can see
     * or fix.
     *
     * <p>What it is not for: building out of nothing. It lays the block out of
     * somebody's load exactly as the reach path would have, so a crew with empty
     * hands gets no help here — a town short of materials has a shortage to
     * report, not a stall to assist, and that shortage already went to
     * {@link BuildPlanner#requestProducer}. This used to be a hole straight
     * through the carry rule: twenty unproductive passes and the block appeared
     * whoever was or was not holding one.
     *
     * <p>Refusing rarely costs the case it exists for, because fetching comes
     * before reaching in the loop above: an empty-handed builder is sent to the
     * stores long before twenty unproductive passes are up, so by the time the
     * count trips they are carrying, or the town has nothing to carry. Only a
     * builder who can reach neither the block nor the shelves loses out, and
     * that is a builder in a hole rather than a site in a deadlock.
     *
     * <p>Nothing wedges forever on the strength of this refusing. A watched build
     * that lays nothing for {@code Settlement.WATCHED_BUILD_GRACE_STEPS}
     * simulation steps stops being the queue head on its own, and that backstop
     * belongs to the simulation rather than to this loop.
     *
     * @return true if a block went down
     */
    private boolean assistStalledSite(Settlement settlement, BuildTask task) {
        String owed = BlueprintPlacer.materialOwedForStep(level, task);
        for (PersonEntity present : buildersAtSite(settlement, task)) {
            Person carrier = personOf(settlement, present);
            if (!BuildLoad.canLay(owed, carrier)) {
                continue;   // nothing in these hands to lay
            }
            boolean fromHand = owed != null;
            if (!BlueprintPlacer.completeStep(level, settlement, task, fromHand)) {
                // Not this builder's fault and not fixable by trying the next
                // one: the step is finished, or the work budget has not cleared
                // it, and neither of those depends on whose hands are asking.
                return false;
            }
            // After the block, not before. A swing at nothing is a builder
            // miming, which is what the readout used to show on a refused step.
            present.swing(InteractionHand.MAIN_HAND);
            if (fromHand) {
                carrier.spendCarry();
            }
            return true;
        }
        return false;
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
     *
     * <p><strong>Two ways to be stuck, and only one used to be seen.</strong>
     * Over a hole, the first solid thing is a long way below and
     * {@link #floorBelow} finds it. On a roof it is directly underfoot, the drop
     * reads nought against {@link #MIN_STRANDED_DROP}, and the old test threw the
     * case away as a kerb — so the crew who had just laid the last course of a
     * cottage stood on it until something else happened to them. They are not
     * over a hole; they are on top of the building they finished.
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
            Building under = floor == Integer.MIN_VALUE ? standingOn(settlement, at) : null;
            boolean perched = under != null;
            if (floor == Integer.MIN_VALUE && !perched) {
                strandedPasses.remove(key);
                repathTries.remove(key);   // not stuck; the patience resets with them
                continue;
            }
            if (strandedPasses.merge(key, 1, Integer::sum) < STRANDED_PASSES) {
                continue;
            }

            // The lateral hunt is deliberately behind the patience count. It reads
            // a few hundred blocks and this runs for every resident of every town
            // every sweep; a settler who has been standing still for three of them
            // is rare, and paying for the search only then costs nothing at all.
            BlockPos down = perched
                    ? ledgeNear(at)
                    : new BlockPos(at.getX(), floor, at.getZ());
            if (down == null) {
                strandedPasses.remove(key);
                repathTries.remove(key);
                orderStepsDown(settlement, under);
                continue;
            }

            int tried = repathTries.getOrDefault(key, 0);
            if (tried < REPATH_ATTEMPTS
                    && view.getNavigation().moveTo(
                            down.getX() + 0.5, down.getY(), down.getZ() + 0.5, WALK_SPEED)) {
                // A route exists. Let them take it, and come back to this if the
                // walk does not actually get them anywhere.
                repathTries.put(key, tried + 1);
                strandedPasses.remove(key);
                continue;
            }

            strandedPasses.remove(key);
            repathTries.remove(key);
            if (at.getY() - down.getY() <= SURVIVABLE_DROP) {
                view.snapTo(down.getX() + 0.5, down.getY(), down.getZ() + 0.5);
                person.setPosition(NeoForgeWorldBridge.toSimPos(view.blockPosition()));
            }
        }
    }

    /** How far to either side to look for somewhere to step down onto. */
    private static final int LATERAL_ESCAPE = 3;

    /**
     * The building somebody is standing on top of, or null if they are on the ground.
     *
     * <p>The question that separates a builder on a roof from a farmer standing in
     * a field, and it cannot be asked of the blocks: both of them are on something
     * solid, and the world's own surface height counts a roof <em>as</em> the
     * surface, so a settler on a ridge reads as standing at grade with a drop of
     * nought. Every block-level answer says the same thing.
     *
     * <p>So it is asked of the town's books instead, which know exactly where each
     * building's floor is, how much ground it covers and how tall it came out.
     * Somebody over the walls and at or above the roof line is on the roof. That
     * is cheap — no block reads at all — and it is exactly the population this is
     * for: the crew who have just laid the last course of something. Nobody on a
     * hillside, in a field, or on their own doorstep is ever named.
     *
     * <p><strong>The walls, not the recorded plot.</strong> A footprint is saved
     * as the building plus its doorstep ring — see {@code BlueprintPlacer.plotOf},
     * which adds {@code APRON_MARGIN} on every side — and two neighbours' rings
     * are allowed to meet, so recorded plots overlap where the buildings do not.
     * Asked of the plot, a builder on one cottage's roof standing inside the
     * next cottage's apron could be attributed to the neighbour, and the flight
     * of steps would then be ordered onto a roof he is not on.
     *
     * <p><strong>The roof line, not three courses.</strong> "Three above the
     * floor" is also true of somebody indoors on an upper course — a watchtower's
     * platform, a loft — and being indoors is not being stranded; snapping them
     * downward would drop them through their own ceiling. The drop test is kept
     * as well, so a two-course camp post is something you step off rather than
     * something the town builds stairs for.
     */
    static Building standingOn(Settlement settlement, BlockPos at) {
        for (Building building : settlement.buildings()) {
            Footprint plot = building.footprint();
            if (!plot.isKnown() || !overWalls(building, plot, at)) {
                continue;
            }
            if (at.getY() >= plot.y() + plot.height()
                    && at.getY() - plot.y() >= MIN_STRANDED_DROP) {
                return building;
            }
        }
        return null;
    }

    /** Whether a column falls on the building itself rather than on its doorstep ring. */
    private static boolean overWalls(Building building, Footprint plot, BlockPos at) {
        int rx = Math.max(0, plot.width() / 2 - BlueprintPlacer.APRON_MARGIN);
        int rz = Math.max(0, plot.depth() / 2 - BlueprintPlacer.APRON_MARGIN);
        return Math.abs(at.getX() - building.origin().x()) <= rx
                && Math.abs(at.getZ() - building.origin().z()) <= rz
                && plot.covers(building.origin().x(), building.origin().z(),
                        at.getX(), at.getZ());
    }

    private BlockPos ledgeNear(BlockPos from) {
        return ledgeNear(from, this::fitsBody);
    }

    /**
     * The nearest place within reach that is lower than here and holds a body.
     *
     * <p>A short search on purpose. This is the eaves, the course of scaffolding,
     * the step down onto the lean-to — one movement a person could plausibly make
     * for themselves and would, if their navigation had noticed the edge. Anything
     * further is not a step down, it is a fall, and the answer to a fall is a
     * flight of steps rather than a shove.
     *
     * <p>Nearest horizontally first and only then lowest, so a settler goes to the
     * edge they are already standing beside rather than across the whole roof to
     * the far one. That is the same ordering mistake {@code standCandidates}
     * documents having made, avoided here rather than repeated.
     *
     * <p>Geometry and an ordering, so it takes what a square must be like as a
     * predicate and needs no world — the part worth testing, left testable.
     */
    static BlockPos ledgeNear(BlockPos from, java.util.function.Predicate<BlockPos> fits) {
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int dx = -LATERAL_ESCAPE; dx <= LATERAL_ESCAPE; dx++) {
            for (int dz = -LATERAL_ESCAPE; dz <= LATERAL_ESCAPE; dz++) {
                if (dx == 0 && dz == 0) {
                    // Their own column, which is straight down through whatever
                    // they are standing on. That is floorBelow's question and it
                    // has already been asked; offering it here would score zero
                    // for distance and beat every real ledge, and the space it
                    // finds is the room under the roof — no route to it, so the
                    // walk fails and the fallback drops somebody through their own
                    // ceiling into a sealed loft.
                    continue;
                }
                for (int drop = 1; drop <= MIN_STRANDED_DROP; drop++) {
                    BlockPos feet = from.offset(dx, -drop, dz);
                    if (!fits.test(feet)) {
                        continue;
                    }
                    // Horizontal distance dominates: the deepest step down is
                    // MIN_STRANDED_DROP, so scaling the reach by it leaves no
                    // drop able to outrank a nearer square.
                    int score = (dx * dx + dz * dz) * (MIN_STRANDED_DROP + 1) + drop;
                    if (score < bestScore) {
                        bestScore = score;
                        best = feet;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Orders a flight down from a perch nobody can step off.
     *
     * <p>The fork a crew on a roof leaves: there is no ledge within reach and the
     * ground is far enough down to hurt. So the town builds, which is what it does
     * about every other place a person cannot get to — this is the same job
     * {@code checkHouseAccess} orders for a door a family cannot climb to, from
     * the other end. The flight runs from the roof to wherever it meets the hill.
     *
     * <p><strong>At the eaves, not at the middle of the roof.</strong> That is not
     * taste. {@code accessStairs} draws its treads outward from the origin it is
     * given and stops the moment a tread would be at or below
     * {@code groundLevel}, which is the surface heightmap — and over a building
     * the surface heightmap <em>is the roof</em>. Anchored at the centre, the very
     * first tread reads as underground and the flight comes out empty: a job worth
     * four units of work with a plan of nothing in it, which can never read
     * complete, pins the head of a head-blocking queue until the watched-build
     * grace times it out, and then records a phantom flight of steps on the roof
     * that stops any real one ever being ordered there again. Anchored at the last
     * wall column, the first tread lands on the doorstep ring beside the house,
     * where the heightmap is the ground it looks like.
     *
     * <p><strong>Anchored on the building, not on the settler — in all three
     * dimensions.</strong> {@code requestAccessStairs} refuses a duplicate by
     * comparing the whole origin it is given against the queue and against what
     * already stands, so a flight ordered from wherever somebody's feet happen to
     * be is a fresh flight every time they shuffle a block along the ridge or up
     * onto the chimney — each one urgent, each one at the head of the build queue,
     * and two settlers standing at two heights on one roof order two of them. Read
     * off the town's books instead, it is the same place however many of them are
     * up there and however they move about while they wait for it.
     */
    private void orderStepsDown(Settlement settlement, Building roof) {
        Footprint plot = roof.footprint();
        int climb = plot.height();
        SimPos eaves = new SimPos(roof.origin().x(), plot.y() + climb,
                roof.origin().z() + Math.max(0, plot.depth() / 2 - BlueprintPlacer.APRON_MARGIN));
        if (BuildPlanner.requestAccessStairs(settlement, eaves, climb, world.stepsElapsed())) {
            KingdomsSavedData.get(level).setDirty();
            KingdomsMod.LOGGER.info("{} has somebody stranded on the {} at {}; steps ordered",
                    settlement.name(), roof.blueprintId(), eaves);
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
        // Alarmed rather than merely wary: at wary most of the town is still
        // out working, and a door they are not trying to use is not a fault.
        boolean headingHome = level.isDarkOutside()
                || settlement.alarm() == Alarm.ALARMED;
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

                // The building's own door, not a guess at one. This used to
                // assume every door faced south while the placer turned three
                // houses in four to face the centre, so the flight of steps was
                // built against a blank wall of the house it was meant to open.
                SimPos doorway = doorwayOf(settlement, home);
                if (BuildPlanner.requestAccessStairs(settlement, doorway, climb, world.stepsElapsed())) {
                    KingdomsSavedData.get(level).setDirty();
                    KingdomsMod.LOGGER.info("{} cannot reach home; steps ordered at {}",
                            person.name(), doorway);
                }
            }
        }
    }

    /** The doorstep of whatever stands at this origin, or a southward guess. */
    private static SimPos doorwayOf(Settlement settlement, SimPos origin) {
        for (Building building : settlement.buildings()) {
            if (building.origin().equals(origin)) {
                SimPos step = building.doorstep();
                return new SimPos(step.x(), origin.y() + 1, step.z());
            }
        }
        return new SimPos(origin.x(), origin.y() + 1, origin.z() + HOUSE_DOOR_OFFSET);
    }

    /**
     * Lumberjacks fell and replant inside the camp's work area. Like builders,
     * they steer themselves while working, so the daily routine leaves them be.
     */
    /** Miners cut stone by daylight and under no threat, same terms as the woodland. */
    /** Shepherds stock the pens, one beast at a time, by daylight. */
    /**
     * Keeps the town's roads on the ground.
     *
     * <p>Where they run is the simulation's business — {@code PathPlanner}
     * remembers the network and joins one building a step to it. This walks one
     * segment a sweep, laying what is missing from it, which draws new roads and
     * mends worn ones through the same mechanism: a new segment is entirely
     * missing, so it gets laid; a sound one costs a few block reads.
     *
     * <p>One a sweep, round-robin. A town with forty stretches of road looks at
     * each of them every forty seconds, which is far more often than grass
     * grows back over one.
     */
    private boolean layPaths(Settlement settlement) {
        List<PathNetwork.Segment> segments = settlement.paths().segments();
        if (segments.isEmpty()) {
            return false;
        }
        UUID id = settlement.id().value();

        // A town that grew while nobody was here arrives with its whole network
        // already planned and opened -- the roads exist, as surely as the
        // buildings do. Paving them one a second meant a hundred and seventy
        // stretches took three minutes to appear, so a settlement you walked
        // back into showed its houses at once and then drew its streets in
        // slowly around you, one at a time, for as long as you stood there.
        //
        // So: everything opened since the last sweep goes down together. Only
        // the tail is new, because segments are appended, so this costs nothing
        // on the steps where nothing has changed.
        int swept = pathsSwept.getOrDefault(id, 0);
        if (swept < segments.size()) {
            int done = 0;
            int i = swept;
            for (; i < segments.size() && done < PAVE_AT_ONCE; i++) {
                if (!settlement.paths().isOpened(i)) {
                    break;   // the network is opened in order; wait for this one
                }
                if (!groundIsHere(segments.get(i))) {
                    // Nobody can see this stretch, so nothing can be laid on it.
                    // Stopping here rather than stepping over it is the whole
                    // point: PathLayer no-ops on unloaded ground and reports
                    // nothing, so a mark that advanced anyway would tick every
                    // road of an away town off as done without a block being
                    // placed -- and then the arrival this exists to serve would
                    // find the work already crossed out.
                    break;
                }
                PathLayer.mend(level, segments.get(i));
                done++;
            }
            pathsSwept.put(id, i);
            if (done > 0) {
                if (done > 4) {
                    KingdomsMod.LOGGER.info("PAVED {} laid {} stretches at once ({} of {})",
                            settlement.name(), done, i, segments.size());
                }
                return true;
            }
        }

        int cursor = pathCursor.merge(id, 1, Integer::sum) - 1;
        int index = Math.floorMod(cursor, segments.size());
        // Only stretches somebody has actually opened. Paving one nobody has
        // walked out yet would put a street on the ground ahead of the people
        // laying it -- which is what this did before roads were a job.
        if (!settlement.paths().isOpened(index)) {
            return false;
        }
        return PathLayer.mend(level, segments.get(index)) > 0;
    }

    /** Whether this stretch's ground is loaded, and so can actually be paved. */
    private boolean groundIsHere(PathNetwork.Segment segment) {
        return level.isLoaded(new BlockPos(
                segment.from().x(), level.getSeaLevel(), segment.from().z()));
    }

    /**
     * How much of a waiting network is paved in one pass.
     *
     * <p>Bounded because mending a stretch writes blocks, and a town arriving
     * with two hundred of them owed would otherwise land in a single frame.
     * Sixty-four a pass clears any real backlog in a few seconds and is not felt.
     */
    private static final int PAVE_AT_ONCE = 64;

    /**
     * How far through each settlement's network the paving has swept.
     *
     * <p>Not a boolean: a town keeps growing, and roads planned while you were
     * away are appended to the same list. Remembering the high-water mark makes
     * the next arrival lay exactly the stretches added since the last one.
     */
    private final Map<UUID, Integer> pathsSwept = new HashMap<>();


    private boolean workShepherds(Settlement settlement) {
        // The pens are on a ring plot, behind the wall. A shepherd only comes in
        // when everybody does.
        if (settlement.alarm().callsIn(Profession.SHEPHERD)) {
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
        if (settlement.mineArea() == null
                || settlement.alarm().callsIn(Profession.MINER)) {
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

    /** Every embodied farmer works their field: harvest, tend, plant. */
    private void workFarmers(Settlement settlement) {
        for (Person person : settlement.residents()) {
            if (!settlement.laboursAs(person, Profession.FARMER) || !person.isEmbodied()
                    || person.isTooWeakToWork() || person.haul() != null) {
                continue;   // a hauling farmer is on the road, not in the rows
            }
            PersonEntity view = tracked.get(person.id().value());
            if (view != null && !view.isRemoved()) {
                FarmWorker.work(level, settlement, world.stepsElapsed(), view, person);
            }
        }
    }

    private boolean workLumberjacks(Settlement settlement) {
        if (settlement.lumberArea() == null || level.isDarkOutside()
                || settlement.alarm().callsIn(Profession.LUMBERJACK)) {
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

    /** How close a builder has to be to the stores to load up. */
    private static final double LOAD_REACH = 4.0;

    /**
     * Sends a builder for materials, and loads them up once they arrive.
     *
     * <p>The stock leaves the ledger at the moment it is picked up, not when it is
     * laid — so a load in transit is genuinely out of the stores, and a builder
     * killed carrying one takes it with them. This is the only place the watched
     * path charges a town for a building; see {@link BuildLoad#pickUp}.
     *
     * @return true if they are loaded and can get on with it
     */
    private boolean fetchLoad(Settlement settlement, Person carrier, PersonEntity builder,
                              String material) {
        if (carrier == null) {
            return false;   // an entity with no record behind it has no hands to fill
        }
        // The store that can actually pay, in preference to the closest one:
        // an empty storehouse underfoot must not strand a builder while the
        // full one stands across the village.
        SimPos at = new SimPos((int) Math.floor(builder.getX()),
                (int) Math.floor(builder.getY()), (int) Math.floor(builder.getZ()));
        Building store = settlement.nearestStore(at, material);
        if (store == null) {
            store = settlement.nearestStore(at);
        }
        SimPos stores = store == null ? settlement.centre() : store.origin();
        double dx = builder.getX() - (stores.x() + 0.5);
        double dz = builder.getZ() - (stores.z() + 0.5);
        if (dx * dx + dz * dz > LOAD_REACH * LOAD_REACH) {
            builder.getNavigation().moveTo(stores.x() + 0.5, stores.y(), stores.z() + 0.5, WALK_SPEED);
            return false;
        }
        // Drawn from the building they walked to, not from a town-wide figure.
        // The walk and the withdrawal have to name the same shelves or the trip
        // is theatre — which is exactly what it used to be. Whatever they were
        // still carrying goes back on those same shelves rather than evaporating.
        Stock from = store == null ? settlement.stores() : store.stores();
        if (BuildLoad.pickUp(from, carrier, material) <= 0) {
            return false;   // the stores are empty; the shortage is reported elsewhere
        }
        builder.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /**
     * Where the town's goods are kept, for anyone who only needs a landmark.
     *
     * <p>Which building counts as a store is the settlement's business now, so
     * this asks rather than reading blueprint names itself. Builders do not use
     * it: they want the nearest store holding a particular thing, which is a
     * different and better question.
     */
    public static SimPos storesPos(Settlement settlement) {
        Building store = settlement.nearestStore(settlement.centre());
        return store == null ? settlement.centre() : store.origin();
    }

    /**
     * The record behind an embodied builder, or null if they are not tracked.
     *
     * <p>Matched on being <em>this</em> body rather than on an id written into
     * the entity, so anything that came out of {@link #embodiedBuilders} always
     * resolves. That matters more than the scan costs: a builder whose record
     * cannot be found now lays nothing at all, because the carry rule has nobody
     * to ask what is in their hands.
     */
    private Person personOf(Settlement settlement, PersonEntity view) {
        for (Person person : settlement.residents()) {
            if (view == tracked.get(person.id().value())) {
                return person;
            }
        }
        return null;
    }

    /**
     * Whichever of these builders is holding the material, or null if none is.
     *
     * <p>Null for a step that costs nothing too: there is nothing to hold and
     * nothing to spend, so the caller pays the ledger, which will charge nothing.
     */
    private PersonEntity whoIsCarrying(Settlement settlement, List<PersonEntity> crew,
                                       String material) {
        if (material == null) {
            return null;
        }
        for (PersonEntity builder : crew) {
            Person carrier = personOf(settlement, builder);
            if (carrier != null && carrier.carries(material)) {
                return builder;
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
            if (!settlement.laboursAs(person, Profession.BUILDER)
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
                backingOff.remove(person.id().value());
                continue;
            }
            arm(settlement, guard);

            // A creeper is not fought the way a zombie is fought. Standing in
            // reach and swinging until it dies kills the guard too — the fuse is
            // shorter than the creeper's health. So: one hit, then out of the
            // blast until the fuse has had time to reset, then back in. It takes
            // longer and the guard survives it.
            boolean volatileFoe = Menace.blowsUp(target);
            long now = level.getGameTime();
            Long clear = backingOff.get(person.id().value());
            if (clear != null && now >= clear) {
                backingOff.remove(person.id().value());
                clear = null;
            }
            if (volatileFoe && clear != null) {
                retreatFrom(guard, target);
                continue;
            }

            if (guard.distanceTo(target) <= GUARD_STRIKE_RANGE) {
                guard.swing(InteractionHand.MAIN_HAND);
                boolean wasAlive = target.isAlive();
                target.hurtServer(level, level.damageSources().mobAttack(guard),
                        GUARD_DAMAGE + armedBonus(guard));
                if (wasAlive && !target.isAlive()) {
                    settlement.tallies().record(Tallies.MOBS_SLAIN);
                }
                if (volatileFoe && target.isAlive()) {
                    backingOff.put(person.id().value(), now + FUSE_RESET_TICKS);
                    retreatFrom(guard, target);
                }
            } else {
                guard.getNavigation().moveTo(target, GUARD_CHARGE_SPEED);
            }
        }
    }

    /**
     * When each guard mid-retreat may turn round again, by game time.
     *
     * <p>Not persisted, and it does not need to be: an unloaded guard is not
     * standing next to a creeper.
     */
    private final Map<UUID, Long> backingOff = new HashMap<>();

    /**
     * How long a guard stays out of it after hitting a creeper.
     *
     * <p>The fuse runs thirty ticks and stops climbing once its target is more
     * than seven blocks off, so this is that plus enough margin to have covered
     * the seven blocks. Too short and the guard walks back into its own detonation.
     */
    private static final int FUSE_RESET_TICKS = 45;

    /** How far to get before turning round. */
    private static final int RETREAT_DISTANCE = 12;

    /** Walks the guard away from something about to go off. */
    private void retreatFrom(PersonEntity guard, Monster blast) {
        Vec3 away = DefaultRandomPos.getPosAway(guard, RETREAT_DISTANCE, 7, blast.position());
        if (away != null) {
            guard.getNavigation().moveTo(away.x, away.y, away.z, GUARD_CHARGE_SPEED);
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

    private void release(Settlement settlement, Person person) {
        forgetDigger(person.id().value());
        // Whatever they were carrying goes back on the town's books. It came off
        // them at the warehouse, and the clock that is about to take over pays
        // out of the pooled ledger and cannot see a load in somebody's arms — so
        // a builder released mid-trip used to take up to a full armful of the
        // town's stone out of its own reckoning every time the player walked
        // away, and hand it back to nobody.
        BuildLoad.putBack(settlement.stores(), person);
        PersonEntity view = tracked.remove(person.id().value());
        if (view != null && !view.isRemoved()) {
            person.setPosition(NeoForgeWorldBridge.toSimPos(view.blockPosition()));
            view.discard();
        }
        person.setEmbodied(false);
    }

    /**
     * Sends a spare builder to whatever public work needs a body next.
     *
     * <p>Only when there is nothing else for them: shelter and stores before
     * roads and walls, which is the same order the abstract clock uses. A
     * builder with a building to raise is not spared for fencing.
     *
     * <p>Which work, and in what order, is the settlement's own opinion — see
     * {@code PublicWorks}. This only finds somebody free to go and do it.
     */
    private void workWall(Settlement settlement) {
        if (!settlement.buildQueue().isEmpty()) {
            return;   // shelter and stores before roads and walls
        }
        for (Person person : settlement.residents()) {
            if (!settlement.laboursAs(person, Profession.BUILDER)
                    || !person.isEmbodied() || person.isTooWeakToWork()
                    || person.haul() != null) {
                continue;
            }
            PersonEntity view = tracked.get(person.id().value());
            if (view == null || view.isRemoved()) {
                continue;
            }
            if (Foreman.work(level, settlement, view)) {
                return;   // one station at a time, by one pair of hands
            }
        }
    }

    /** How close a settler has to pass to notice something on the ground. */
    private static final double PICKUP_REACH = 1.6;

    /** How close to the market counter a sale happens. */
    private static final double MARKET_REACH = 4.0;

    /**
     * Settlers pick up what they walk over.
     *
     * <p>Everything a town owns used to arrive by being produced. Nothing on the
     * ground was anybody's business — a sword dropped where the guards killed a
     * skeleton lay there until it despawned, in full view of forty people who
     * walked round it all day.
     *
     * <p>What they pick up is <em>theirs</em>, which is the whole point. It goes
     * into their own pockets, not the town stores, and the town buys it off them
     * at the market if it wants it. A lumberjack's timber belongs to the town
     * because the town paid them to cut it; a sword they trod on does not.
     */
    private void pickUpLitter(Settlement settlement) {
        for (Person person : settlement.residents()) {
            if (!person.isEmbodied() || Economy.pocketsFull(person)) {
                continue;
            }
            PersonEntity view = tracked.get(person.id().value());
            if (view == null || view.isRemoved()) {
                continue;
            }
            AABB reach = view.getBoundingBox().inflate(PICKUP_REACH);
            for (ItemEntity dropped : level.getEntitiesOfClass(ItemEntity.class, reach,
                    item -> item.isAlive() && !item.hasPickUpDelay())) {
                ItemStack stack = dropped.getItem();
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                int taken = person.inventory().add(id, stack.getCount());
                if (taken <= 0) {
                    continue;   // no room for this kind; leave it where it lies
                }
                stack.shrink(taken);
                level.playSound(null, view.blockPosition(), SoundEvents.ITEM_PICKUP,
                        SoundSource.PLAYERS, 0.2F, 1.6F);
                if (stack.isEmpty()) {
                    dropped.discard();
                } else {
                    dropped.setItem(stack);
                }
                if (Economy.pocketsFull(person)) {
                    break;
                }
            }
        }
    }

    /**
     * A settler puts what they are carrying into the town's stores.
     *
     * <p>No coin changes hands and none is owed. They found it while working for
     * the town, so it was the town's before they picked it up — the walk in is
     * the whole of the transaction. Money exists in this mod for exactly one
     * relationship, and it is not this one; see {@code TRADE.md}.
     */
    private void unloadAtStore(Settlement settlement) {
        SimPos where = marketPos(settlement);
        if (where == null) {
            where = settlement.centre();
        }
        for (Person person : settlement.residents()) {
            if (!person.isEmbodied() || !Economy.wantsToUnload(person)) {
                continue;
            }
            PersonEntity view = tracked.get(person.id().value());
            if (view == null || view.isRemoved()) {
                continue;
            }
            if (view.distanceToSqr(where.x() + 0.5, where.y(), where.z() + 0.5)
                    > MARKET_REACH * MARKET_REACH) {
                continue;   // on their way; workplaceFor is steering them
            }
            SimPos shelf = where;
            int given = Economy.handIn(settlement, person,
                    (itemId, count) -> settlement.storeNear(shelf).add(itemId, count));
            if (given <= 0) {
                continue;
            }
            settlement.logEvent(world.stepsElapsed(),
                    person.name() + " brings " + given + " item(s) in to the stores");
            level.playSound(null, new BlockPos(where.x(), where.y(), where.z()),
                    SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.5F, 1.0F);
        }
    }

    /** Where the town trades, or null before a market stands. */
    private SimPos marketPos(Settlement settlement) {
        Building market = settlement.buildingWithRole(BuildingRole.MARKET);
        return market == null ? null : market.origin();
    }

    private static String plainName(String itemId) {
        int colon = itemId.indexOf(':');
        return itemId.substring(colon + 1).replace('_', ' ');
    }

    /**
     * What each settlement's alarm was last pass, so a rise can be heard.
     *
     * <p>Not persisted. A bell rings at the moment the alarm goes up, and a
     * reload is not that moment.
     */
    private final Map<UUID, Alarm> lastAlarm = new HashMap<>();

    /** How far from the tower a bell can be heard, in blocks. */
    private static final double BELL_REACH = 8.0;

    /**
     * Sounds the town's bell when its alarm rises to alarmed.
     *
     * <p>The simulation has already decided — {@code RaidPlanner} rings when
     * what has been seen outnumbers the watch — and this is that decision made
     * audible. It swings a real bell if the town has one, so the sound comes
     * from the tower rather than from nowhere, and falls back to the town centre
     * for a settlement whose tower has not gone up yet.
     *
     * <p>Only on the rise. A bell that tolled every second for as long as a raid
     * lasted would be a fire alarm, not a warning.
     */
    private void ringTheBell(Settlement settlement) {
        Alarm now = settlement.alarm();
        Alarm before = lastAlarm.put(settlement.id().value(), now);
        if (now != Alarm.ALARMED || before == Alarm.ALARMED) {
            return;
        }
        BlockPos bell = findBell(settlement);
        SimPos centre = settlement.centre();
        BlockPos from = bell != null ? bell
                : new BlockPos(centre.x(), centre.y(), centre.z());
        if (bell != null && level.getBlockEntity(bell) instanceof BellBlockEntity ringing) {
            // Swings the real thing: the model moves and vanilla's own bell
            // effects run, so it reads as somebody pulling the rope.
            ringing.onHit(Direction.NORTH);
        }
        level.playSound(null, from, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 4.0F, 1.0F);
        settlement.logEvent(world.stepsElapsed(),
                "The bell is rung — more than the watch can hold");
    }

    /** The bell on a standing watchtower, or null if the town has no tower yet. */
    private BlockPos findBell(Settlement settlement) {
        for (Building building : settlement.buildings()) {
            if (!building.blueprintId().contains("watchtower")) {
                continue;
            }
            BlockPos origin = new BlockPos(building.origin().x(),
                    building.origin().y(), building.origin().z());
            if (!level.isLoaded(origin)) {
                continue;
            }
            for (int dy = 0; dy <= BELL_SEARCH_HEIGHT; dy++) {
                BlockPos at = origin.above(dy);
                if (level.getBlockState(at).is(Blocks.BELL)) {
                    return at;
                }
            }
        }
        return null;
    }

    /** How far up a tower the bell might have ended up. */
    private static final int BELL_SEARCH_HEIGHT = 10;

    /**
     * The village day. Threatened civilians run home; at night everyone but the
     * watch turns in; by day people head to their work — farmers to the fields,
     * builders to the site, traders to the storehouse, guards to the tower,
     * idlers about their homes. The wander goal mills them around whatever spot
     * this chooses, so the town reads as lived-in rather than marched.
     */
    private void dailyRoutine(Settlement settlement) {
        boolean night = level.isDarkOutside();
        Alarm alarm = settlement.alarm();

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
            if (settlement.laboursAs(person, Profession.BUILDER)
                    && !alarm.callsIn(person.profession()) && !night
                    && (!settlement.buildQueue().isEmpty() || isClearing(settlement))
                    && !person.isTooWeakToWork()) {
                continue;
            }
            if (person.profession() == Profession.LUMBERJACK
                    && !alarm.callsIn(Profession.LUMBERJACK) && !night
                    && person.haul() == null
                    && settlement.lumberArea() != null
                    && !person.isTooWeakToWork()) {
                continue;   // steered tree by tree in workLumberjacks
            }
            if (settlement.laboursAs(person, Profession.FARMER)
                    && !alarm.callsIn(person.profession()) && !night
                    && person.haul() == null
                    && !person.isTooWeakToWork()) {
                continue;   // steered row by row in workFarmers
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
            if (alarm.callsIn(person.profession())) {
                // Wary is a walk indoors; alarmed is a run. A town that sprints
                // for its doors over one skeleton reads as hysterical, and a
                // town that strolls through a raid reads as asleep.
                target = home != null ? home : settlement.centre();
                speed = alarm == Alarm.ALARMED ? SHELTER_SPEED : WALK_SPEED;
            } else if (night && !guard) {
                target = home != null ? home : settlement.centre();
                speed = WALK_SPEED;
            } else {
                target = workplaceFor(settlement, person, home);
                speed = WALK_SPEED;
            }

            if (view.isFleeing()) {
                // Getting away from a creeper beats every errand on this list.
                // Steering them now would just cancel the escape path.
                continue;
            }

            double dx = view.getX() - (target.x() + 0.5);
            double dz = view.getZ() - (target.z() + 0.5);
            double arrive = alarm == Alarm.ALARMED && !guard ? 2.0 : ARRIVE_RADIUS;
            if (dx * dx + dz * dz > arrive * arrive) {
                // The target's own Y, never the surface heightmap: a building's
                // "surface" is its ROOF, and routing people there is what put
                // villagers on rooftops in the first place.
                view.getNavigation().moveTo(target.x() + 0.5, target.y(), target.z() + 0.5, speed);
            }
        }
    }

    /** The farmer's rostered field, falling back to the nearest if the town has none. */
    private SimPos fieldOrNearest(Settlement settlement, Person person) {
        Building field = FieldRoster.fieldFor(settlement, person);
        return field != null ? field.origin()
                : nearestBuilding(settlement, "farm", person.position());
    }

    private SimPos workplaceFor(Settlement settlement, Person person, SimPos home) {
        // An errand outranks the day job: haulers walk to the store they are
        // collecting from, then to the one they are delivering to.
        // Ranked below the town's own errands. A haul comes first; putting away
        // an armful of what they picked up comes next, once it is worth the
        // walk. See Economy.wantsToUnload.
        if (person.haul() == null && Economy.wantsToUnload(person)) {
            SimPos market = marketPos(settlement);
            if (market != null) {
                return market;
            }
        }
        HaulTask haul = person.haul();
        if (haul != null) {
            return haul.target();
        }
        return switch (person.profession()) {
            // The field this farmer is answerable for, not whichever one they
            // happen to be closest to -- a farmer standing on a farm is always
            // closest to that one, so the first field anybody reached became the
            // only field the town ever worked. See FieldRoster.
            case FARMER -> fieldOrNearest(settlement, person);
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
            case MILLER -> nearestBuilding(settlement, "mill", person.position());
            case CARPENTER -> nearestBuilding(settlement, "carpentry", person.position());
            case SHEPHERD -> nearestBuilding(settlement, "animal_farm", person.position());
            case GUARD -> patrolPost(settlement, person);
            // A pioneer's workplace is whatever the camp is doing: the build
            // site while anything is queued, the fields otherwise, and
            // nearestBuilding already falls back to the camp centre before
            // there is a field to stand in.
            case PIONEER -> settlement.buildQueue().isEmpty()
                    ? nearestBuilding(settlement, "farm", person.position())
                    : settlement.buildQueue().getFirst().origin();
            case IDLER -> home != null ? home : settlement.centre();
        };
    }

    /**
     * Where the sentry should be: the next node of the perimeter walk.
     *
     * <p>The ring's vertices double as patrol nodes, exactly as FOUNDING.md
     * promises. Stateless on purpose — near a corner the sentry heads for the
     * next one along, otherwise for the nearest, which resolves to a steady
     * clockwise round without any patrol state to persist.
     */
    private static SimPos patrolPost(Settlement settlement, Person person) {
        var perimeter = settlement.perimeter();
        if (perimeter == null || perimeter.laid() <= 0) {
            return nearestBuilding(settlement, "watchtower", person.position());
        }
        var nodes = perimeter.vertices();
        int nearest = 0;
        long best = Long.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            long d = nodes.get(i).horizontalDistanceSq(person.position());
            if (d < best) {
                best = d;
                nearest = i;
            }
        }
        boolean atCorner = best <= 3 * 3;
        return nodes.get(atCorner ? (nearest + 1) % nodes.size() : nearest);
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
        forgetDigger(personId);
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
                        release(settlement, person);
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

    // --- surveying ---

    /**
     * Blocks between sparks along a surveyed line.
     *
     * <p>A quarter of a block, each mark a little over natural size, so the
     * marks touch and a surveyed edge reads as a drawn line. It used to be two
     * blocks apart: that drew a row of separate floating dots, and you could see
     * that something had been measured but not what shape it was. Tried larger
     * marks further apart first -- that reads as a line at surveying distance
     * and as a row of blobs when you stand next to it, so the marks are small
     * and the step is short instead.
     */
    private static final double SPARK_STEP = 0.25;

    /**
     * What a surveyed line is drawn with.
     *
     * <p>Coloured dust rather than end rods. An end rod spark is a point of
     * light: a row of them half a block apart still reads as a row of dots,
     * which was the whole complaint. A dust particle takes a size, so at one and
     * a half it is wider than the gap between two of them and the row closes
     * into a line. It also takes a colour, which lets the streets be told apart
     * from the buildings at a glance -- amber ways, white walls.
     */
    private static final DustParticleOptions WALL_LINE =
            new DustParticleOptions(0xFFFFFF, 1.2f);

    private static final DustParticleOptions STREET_LINE =
            new DustParticleOptions(0xFFA326, 1.2f);

    // --- claim borders ---

    /** How far from the border line a player still sees it. */
    private static final double BORDER_VIEW_RANGE = 64.0;

    /** Blocks between sparkles along the border. */
    private static final double BORDER_POINT_SPACING = SPARK_STEP;

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
                    surveyStreets(player, settlement);
                    for (Building building : settlement.buildings()) {
                        outline(player, building);
                    }
                    // Planned work lights up too: the lamp is for reading the
                    // town's shape, and a plot that has been claimed but not yet
                    // built is part of that shape.
                    for (BuildTask task : settlement.buildQueue()) {
                        if (!BuildPlanner.holdsGround(task.blueprintId())) {
                            continue;
                        }
                        Footprint footprint = task.footprint();
                        if (!footprint.isKnown()) {
                            int span = BuildPlanner.plotSpanOf(
                                    task.blueprintId(), settlement.catalogue());
                            footprint = new Footprint(task.site().y(), span, span, 3);
                        }
                        outline(player, task.site(), footprint);
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
        outline(player, building.origin(), footprint);
    }

    private void outline(ServerPlayer player, SimPos origin, Footprint footprint) {
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
        for (double x = -rx; x <= rx; x += SPARK_STEP) {
            spark(origin.x() + x, floor, origin.z() - rz);
            spark(origin.x() + x, floor, origin.z() + rz);
            spark(origin.x() + x, roof, origin.z() - rz);
            spark(origin.x() + x, roof, origin.z() + rz);
        }
        for (double z = -rz; z <= rz; z += SPARK_STEP) {
            spark(origin.x() - rx, floor, origin.z() + z);
            spark(origin.x() + rx, floor, origin.z() + z);
            spark(origin.x() - rx, roof, origin.z() + z);
            spark(origin.x() + rx, roof, origin.z() + z);
        }
        // And the four posts joining them, so the box reads as a volume.
        for (double y = floor; y <= roof; y += SPARK_STEP) {
            spark(origin.x() - rx, y, origin.z() - rz);
            spark(origin.x() - rx, y, origin.z() + rz);
            spark(origin.x() + rx, y, origin.z() - rz);
            spark(origin.x() + rx, y, origin.z() + rz);
        }
    }

    // --- streets ---

    /**
     * Draws the streets a settlement has opened, as lines along the ground.
     *
     * <p>The lamp used to light buildings and nothing else, which showed a town
     * as a field of unrelated boxes. The streets are what make it a town -- what
     * the buildings face, what the plan is actually made of -- so a survey that
     * omits them cannot answer the question anybody picks the lamp up to ask,
     * which is whether the place hangs together.
     *
     * <p>Drawn only where opened: a street that has been planned but not yet
     * walked out is not somewhere you can go, and drawing it the same as a real
     * one would be a lie told in light.
     */
    private void surveyStreets(ServerPlayer player, Settlement settlement) {
        PathNetwork paths = settlement.paths();
        List<PathNetwork.Segment> runs = paths.segments();
        for (int i = 0; i < runs.size(); i++) {
            if (!paths.isOpened(i)) {
                continue;
            }
            PathNetwork.Segment run = runs.get(i);
            if (!withinLamp(player, run.from()) && !withinLamp(player, run.to())) {
                continue;
            }
            traceOnGround(run.from(), run.to());
        }
    }

    private boolean withinLamp(ServerPlayer player, SimPos at) {
        double dx = player.getX() - at.x();
        double dz = player.getZ() - at.z();
        return dx * dx + dz * dz <= OUTLINE_VIEW_RANGE * OUTLINE_VIEW_RANGE;
    }

    /**
     * A line between two points, laid on whatever the ground turns out to be.
     *
     * <p>A street climbs, so a line drawn at one height would sink into a rise
     * and float over a dip. Each spark asks the world how high the ground is
     * beneath it -- a heightmap lookup, which is cheap, and only for the stretch
     * a player is close enough to see.
     */
    private void traceOnGround(SimPos from, SimPos to) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        double run = Math.hypot(dx, dz);
        int steps = Math.max(1, (int) Math.ceil(run / SPARK_STEP));
        for (int i = 0; i <= steps; i++) {
            double x = from.x() + dx * i / steps;
            double z = from.z() + dz * i / steps;
            int y = BlueprintPlacer.groundLevel(level,
                    (int) Math.floor(x), (int) Math.floor(z));
            level.sendParticles(STREET_LINE,
                    x + 0.5, y + 0.3, z + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void spark(double x, double y, double z) {
        level.sendParticles(WALL_LINE,
                x + 0.5, y + 0.5, z + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
