package com.kingdoms.neoforge.world;

import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingRole;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Walks a settlement's buildings and reports what is built wrong.
 *
 * <p>This exists because every fault it checks for is silent. A farm placed in a
 * lake, a hall perched a storey above its own door, a settler-proof fence, crops
 * popped into item drops — none of them throws, none of them logs, and all of
 * them are obvious within thirty seconds of walking through the town. The audit
 * turns that walk into something a headless run can do, so regressions in world
 * geometry get caught by the harness instead of by the player.
 *
 * <p>Everything here is read-only and judged against the same rules the builders
 * work to: floors sit at grade ({@link BlueprintPlacer#floorFor}), the cleared
 * shelf around a building is part of its plot, and every building needs a way in
 * at ground level.
 *
 * <p>One check is not about blocks at all — see {@link #auditTown}. A town that
 * starves to death does it just as silently as a hall built in a lake, and it
 * does it whether or not anybody has its chunks loaded.
 */
public final class TownAuditor {

    /**
     * One thing wrong with one building — or, when {@code at} is null, with the
     * town itself.
     *
     * <p>A nullable position was the cheapest honest way to admit a
     * settlement-scope fault. Pinning a synthetic one to the town centre would
     * have kept the field non-null at the price of sending whoever read it to
     * stand on a block where nothing is wrong: a famine is not located anywhere.
     */
    public record Fault(String blueprintId, BlockPos at, String problem) {

        /** What stands in the blueprint id's place for a fault of the whole town. */
        public static final String TOWN = "town";

        /** A fault of the settlement rather than of anything standing in it. */
        public static Fault ofTown(String problem) {
            return new Fault(TOWN, null, problem);
        }

        public boolean isTownScope() {
            return at == null;
        }

        public String describe() {
            // The null branch is not defensive: town-scope faults have no
            // position by design, and this is the only place that would notice.
            return at == null
                    ? blueprintId + ": " + problem
                    : blueprintId + " @ " + at.toShortString() + ": " + problem;
        }
    }

    /**
     * How close a town is to famine, worst first.
     *
     * <p>Judged here, from the settlement's public state, only because the
     * simulation has no crisis rule of its own yet. When {@code :common} grows
     * one this must defer to it — two thresholds that can drift apart are one
     * threshold too many, and an audit that disagrees with the simulation it is
     * auditing is worse than an audit that says nothing.
     */
    public enum Distress {
        /** Somebody is at severe hunger: the starvation clock is already running. */
        SEVERE,
        /** Somebody is too weak to work, which is how the spiral feeds itself. */
        WEAK,
        /** Nobody is weak yet, but the larder no longer covers a lost harvest. */
        LEAN,
        NONE;

        /** Stable lowercase token, because this goes on a line the harness greps. */
        public String token() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A larder covering fewer steps than this can no longer see the town through
     * one resident's whole descent from fed to dead, which is the shortest
     * warning worth giving. Above it an empty granary is a hiccup in the supply
     * chain; below it the town is spending its last reserve.
     */
    public static final int LEAN_RESERVE_STEPS =
            Person.HUNGER_MAX / FoodPlanner.HUNGER_PER_STEP
                    + FoodPlanner.STARVATION_GRACE_STEPS;

    /**
     * Simulation steps a build may sit without advancing before its queue reads
     * as frozen.
     *
     * <p>A dozen steps is about a minute of play: long enough that a build merely
     * waiting on the next grant of work is never accused, short enough that a
     * head task nobody can afford gets named while the town can still be saved.
     */
    private static final int FROZEN_QUEUE_STEPS = 12;

    /**
     * The build queue's head as it last looked, per settlement, and the step it
     * first looked that way.
     *
     * <p>Measured in simulation steps rather than game ticks on purpose: 26.2's
     * per-dimension clocks can hold {@code getGameTime()} at a fixed value (the
     * fault {@code SimWorld#onGameTick} counts its own ticks to avoid), and a
     * frozen clock would report every queue in the world as frozen forever.
     */
    private static final java.util.Map<Settlement.Id, HeadState> LAST_HEAD =
            new java.util.HashMap<>();

    private record HeadState(String signature, long sinceStep) {
    }

    /** A course of slack either way before a shelf reads as banked or hanging. */
    private static final int SHELF_TOLERANCE = 1;

    /** Sample every other column on rings, so a big plot stays cheap to judge. */
    private static final int RING_STEP = 2;

    /** Fewer loose items than this is dropped tools and clutter, not a fault. */
    private static final int LOOSE_ITEM_THRESHOLD = 5;

    /** A field this small is still being cleared; do not judge its planting. */
    private static final int MIN_FIELD_TO_JUDGE = 8;

    /** Vanished-crop reports per farm per sweep, so one bad night stays readable. */
    private static final int MAX_VANISHED_REPORTED = 4;

    /**
     * Where crops stood last sweep, per farm.
     *
     * <p>The whole point of remembering: when a crop disappears, the next sweep
     * can say <em>which block</em> and <em>what stands there now</em> — soil
     * intact means the crop broke off it (a survival check failed), soil turned
     * to dirt means something trampled it, a path block means it was paved over.
     * Counting losses never identified the mechanism; naming them does.
     */
    private static final java.util.Map<BlockPos, java.util.Set<BlockPos>> LAST_PLANTED =
            new java.util.HashMap<>();

    /**
     * Buildings that had no blocks on the ground when we last looked.
     *
     * <p>Because "recorded here, but nothing stands" is not a fault the first
     * time you see it. A building finished while nobody was watching is a
     * record with nothing drawn by design, and stays that way until the next
     * settlement step reaches it — a gap of one step, which the auditor is
     * quick enough to walk straight into.
     *
     * <p>It reported 61 of them in a seven-minute run. Every single one was
     * drawn before the next sweep thirty seconds later: each was named exactly
     * once, while a genuine fault in the same run — a building with no doorway
     * — was named on six to fifteen consecutive sweeps. That is the difference
     * between a race and a defect, and until now the report could not tell
     * them apart. Sixty-one false alarms is also precisely how a real one goes
     * unnoticed.
     *
     * <p>So a building must be found undrawn twice running before it is worth
     * complaining about. Anything genuinely stuck stays stuck and is still
     * reported; anything that was merely caught mid-step is not.
     */
    private static final java.util.Set<BlockPos> LAST_UNDRAWN = new java.util.HashSet<>();

    /** Filled during a sweep, and swapped into {@link #LAST_UNDRAWN} at the end of it. */
    private static final java.util.Set<BlockPos> UNDRAWN_THIS_SWEEP = new java.util.HashSet<>();

    private TownAuditor() {
    }

    /**
     * Drops everything the auditor remembers from one sweep to the next.
     *
     * <p>Both memories are session-scoped by nature and neither says so on its
     * own: one holds simulation step numbers, which restart at zero when the
     * server does, and the other holds block positions in a world about to
     * close. Carried into the next session, a stall would be dated against a
     * clock that no longer exists and a crop would be missed from a field that
     * is not there.
     */
    public static void forget() {
        LAST_HEAD.clear();
        LAST_PLANTED.clear();
        LAST_UNDRAWN.clear();
        UNDRAWN_THIS_SWEEP.clear();
    }

    /**
     * Audits the town, and every one of its buildings that stands in a loaded
     * chunk.
     *
     * <p>Unloaded buildings are skipped, not judged: there is nothing there to
     * look at, and guessing would report ghosts. The town-level check has no such
     * excuse — it reads simulation state — so it runs even when not one chunk of
     * the settlement is loaded, and callers must be ready for a fault list from a
     * town with nothing visible in it.
     */
    /** Convenience for callers holding a live level. */
    public static List<Fault> audit(ServerLevel level, Settlement settlement) {
        return audit(new LevelWorldView(level), settlement);
    }

    /**
     * The audit proper, against whatever can answer questions about the world.
     *
     * <p>Takes a {@link WorldView} rather than a level so the geometry can be
     * driven from a hand-built world in a test. The checks below are the only
     * instrument this project has for what a town actually looks like, and
     * until this seam existed the only way to run one was to found a town.
     */
    public static List<Fault> audit(WorldView world, Settlement settlement) {
        List<Fault> faults = new ArrayList<>();
        List<Building> present = new ArrayList<>();
        // What was undrawn when we last looked, so this sweep can tell a
        // building caught mid-step from one that is genuinely stuck. Collected
        // fresh below and swapped in at the end.
        UNDRAWN_THIS_SWEEP.clear();
        for (Building building : settlement.buildings()) {
            if (isPath(building.blueprintId())) {
                continue;   // steps are a path, not a building with an inside
            }
            BlockPos origin = new BlockPos(building.origin().x(),
                    building.origin().y(), building.origin().z());
            if (!world.isLoaded(origin)) {
                continue;
            }
            present.add(building);
            auditOne(world, building, origin, faults);
        }
        auditOverlaps(present, faults);
        auditTown(world, settlement, faults);
        // Only the buildings this sweep actually looked at. A building in an
        // unloaded chunk is not evidence of anything either way, and letting it
        // fall out of the set here means it starts its two-sweep count again
        // when somebody next walks past — which is right, because a building
        // nobody has seen for an hour has not been "stuck" for an hour.
        LAST_UNDRAWN.clear();
        LAST_UNDRAWN.addAll(UNDRAWN_THIS_SWEEP);
        return faults;
    }

    /**
     * How many simulation steps of appetite a larder still covers.
     *
     * <p>Hunger is the lagging indicator: it only climbs once the food has
     * already run out, by which point the town has minutes. This is the leading
     * one, and it is the whole reason a vitals line can warn rather than report.
     */
    public static int reserveSteps(int larder, int population) {
        if (population <= 0) {
            return 0;
        }
        return larder * Foods.nutrition(Foods.PROVISION)
                / (population * FoodPlanner.HUNGER_PER_STEP);
    }

    /**
     * The verdict, from figures the caller has already counted.
     *
     * <p>Takes the numbers rather than the settlement so that the verdict on a
     * vitals line is guaranteed to be the verdict on <em>those</em> figures. A
     * second count of the larder in here could disagree with the one printed
     * beside it, and a log that argues with itself is worse than no log.
     */
    public static Distress distress(int worstHunger, int larder, int population) {
        if (population <= 0) {
            return Distress.NONE;   // nobody left to go hungry; this is an obituary
        }
        Distress byHunger = hungerTier(worstHunger);
        if (byHunger != Distress.NONE) {
            return byHunger;
        }
        return reserveSteps(larder, population) < LEAN_RESERVE_STEPS
                ? Distress.LEAN : Distress.NONE;
    }

    /**
     * Checks the auditor against cases whose answers are already known.
     *
     * <p>Worth having because a silent auditor and a healthy town look exactly
     * the same from the outside: "clean" is only worth reading if the thing
     * saying it can be shown to say otherwise when there is something to say.
     * So every check here comes in both directions — a fault that must be
     * caught, and a near miss that must not be.
     *
     * <p>Only the parts that need no world: plot overlap, which reads the
     * buildings' own geometry, and the hunger verdicts, which take plain
     * numbers. The checks that walk blocks — doorways, bare fields, litter —
     * cannot be judged without a town to walk, and {@code /civ audit} on a
     * building you have just walled up is the honest test of those.
     *
     * <p>Expectations are written against the named constants rather than the
     * numbers they currently hold, so tuning a threshold does not turn this
     * red on its own.
     */
    public static List<String> selfTest() {
        List<String> lines = new ArrayList<>();

        // --- does it see a fault that is really there? ---
        Building house = plot("kingdoms:house", 0, 0, 5, 5);
        Building smith = plot("kingdoms:smith", 1, 1, 5, 5);
        List<Fault> onSameGround = new ArrayList<>();
        auditOverlaps(List.of(house, smith), onSameGround);
        check(lines, !onSameGround.isEmpty(), "two plots on the same ground are reported");

        // --- and does it stay quiet when there is not? ---
        List<Fault> setApart = new ArrayList<>();
        auditOverlaps(List.of(house, plot("kingdoms:smith", 500, 500, 5, 5)), setApart);
        check(lines, setApart.isEmpty(), "plots set well apart are left alone");

        Building unsurveyed = new Building("kingdoms:house", new SimPos(0, 64, 0), 1, true);
        List<Fault> unjudged = new ArrayList<>();
        auditOverlaps(List.of(house, unsurveyed), unjudged);
        check(lines, unjudged.isEmpty(), "a plot with no surveyed footprint is not guessed at");

        // --- the larder, which is the warning the vitals line is for ---
        check(lines, reserveSteps(100, 0) == 0, "a town with nobody in it has no appetite");
        check(lines, reserveSteps(1000, 10) > reserveSteps(100, 10),
                "a fuller larder is a longer reserve");
        check(lines, reserveSteps(100, 5) > reserveSteps(100, 50),
                "and more mouths is a shorter one");

        // --- the verdict ---
        int full = LEAN_RESERVE_STEPS * 100;
        check(lines, distress(Person.HUNGER_SEVERE, full, 10) == Distress.SEVERE,
                "a starving resident outranks a full granary");
        check(lines, distress(Person.HUNGER_WEAK, full, 10) == Distress.WEAK,
                "somebody too weak to work is still reported");
        check(lines, distress(0, full, 10) == Distress.NONE,
                "a fed town with food in hand is left in peace");
        check(lines, distress(0, 0, 10) == Distress.LEAN,
                "an empty larder warns before anybody has gone hungry");
        check(lines, distress(Person.HUNGER_SEVERE, 0, 0) == Distress.NONE,
                "an empty town is an obituary, not a famine");

        return lines;
    }

    private static void check(List<String> lines, boolean held, String what) {
        lines.add((held ? "PASS  " : "FAIL  ") + what);
    }

    /** A building with a surveyed plot of the given span, for the self-test. */
    private static Building plot(String blueprintId, int x, int z, int width, int depth) {
        Building building = new Building(blueprintId, new SimPos(x, 64, z), 1, true);
        building.setFootprint(new Footprint(64, width, depth, 4));
        return building;
    }

    /** What the worst stomach in town earns on its own, before the larder counts. */
    private static Distress hungerTier(int worstHunger) {
        if (worstHunger >= Person.HUNGER_SEVERE) {
            return Distress.SEVERE;
        }
        if (worstHunger >= Person.HUNGER_WEAK) {
            return Distress.WEAK;
        }
        return Distress.NONE;
    }

    /** How many of a settlement's buildings the audit could actually see. */
    /** Convenience for callers holding a live level. */
    public static int visibleCount(ServerLevel level, Settlement settlement) {
        return visibleCount(new LevelWorldView(level), settlement);
    }

    public static int visibleCount(WorldView world, Settlement settlement) {
        int seen = 0;
        for (Building building : settlement.buildings()) {
            if (isPath(building.blueprintId())) {
                continue;
            }
            if (world.isLoaded(new BlockPos(building.origin().x(),
                    building.origin().y(), building.origin().z()))) {
                seen++;
            }
        }
        return seen;
    }

    // --- the checks ---

    private static void auditOne(WorldView world, Building building, BlockPos origin,
                                 List<Fault> faults) {
        if (!building.isMaterialized()) {
            // The chunk is loaded and the simulation says this building exists,
            // yet nothing has been drawn. That is expected for exactly one step
            // — materializePending draws it the next time the settlement runs —
            // so it is only worth reporting if it is STILL true next sweep. See
            // LAST_UNDRAWN for the measurements that forced this distinction.
            UNDRAWN_THIS_SWEEP.add(origin);
            if (LAST_UNDRAWN.contains(origin)) {
                faults.add(new Fault(building.blueprintId(), origin,
                        "recorded here, but nothing stands on the ground —"
                                + " and it was the same last sweep"));
            }
            return;
        }
        Footprint plot = building.footprint();
        if (!plot.isKnown()) {
            return;   // an old record with no measurements; nothing to judge against
        }
        int floor = plot.y();
        int wallHalfW = Math.max(1, plot.width() / 2 - BlueprintPlacer.APRON_MARGIN);
        int wallHalfD = Math.max(1, plot.depth() / 2 - BlueprintPlacer.APRON_MARGIN);

        checkShelf(world, building, origin, floor, wallHalfW, wallHalfD, faults);
        checkFluid(world, building, origin, floor, wallHalfW, wallHalfD, faults);
        checkDoorway(world, building, origin, floor, wallHalfW, wallHalfD, faults);
        if (isCropFarm(building.blueprintId())) {
            checkField(world, building, origin, floor, wallHalfW, wallHalfD, faults);
        }
    }

    /**
     * The cleared shelf around the walls must sit at the floor's own level.
     *
     * <p>That shelf is what makes a building enterable: the door opens onto it.
     * If the ground one step out from the walls stands above the floor on every
     * side, the building is at the bottom of a pit; if it falls short on every
     * side, the building is perched with its doorway in the air. Either way
     * nobody is walking in. Partial banking is left alone — a hillside build is
     * banked uphill by nature, and one open side is all an entrance needs.
     */
    private static void checkShelf(WorldView world, Building building, BlockPos origin,
                                   int floor, int wallHalfW, int wallHalfD,
                                   List<Fault> faults) {
        int samples = 0;
        int banked = 0;
        int hanging = 0;
        int worstAbove = 0;
        int worstBelow = 0;
        for (BlockPos spot : ring(origin, wallHalfW + 1, wallHalfD + 1, RING_STEP)) {
            if (!world.isLoaded(spot)) {
                continue;
            }
            int grade = world.groundLevel(spot.getX(), spot.getZ()) - 1;
            samples++;
            if (grade > floor + SHELF_TOLERANCE) {
                banked++;
                worstAbove = Math.max(worstAbove, grade - floor);
            } else if (grade < floor - SHELF_TOLERANCE) {
                hanging++;
                worstBelow = Math.max(worstBelow, floor - grade);
            }
        }
        if (samples == 0) {
            return;
        }
        if (banked == samples) {
            faults.add(new Fault(building.blueprintId(), origin,
                    "buried — the ground stands up to " + worstAbove
                            + " above its floor on every side"));
        } else if (hanging == samples) {
            faults.add(new Fault(building.blueprintId(), origin,
                    "perched — its floor hangs up to " + worstBelow
                            + " above the ground on every side"));
        }
    }

    /** Water or lava standing in the rooms is a building in a lake. */
    private static void checkFluid(WorldView world, Building building, BlockPos origin,
                                   int floor, int wallHalfW, int wallHalfD,
                                   List<Fault> faults) {
        int wet = 0;
        for (int dx = -wallHalfW; dx <= wallHalfW; dx++) {
            for (int dz = -wallHalfD; dz <= wallHalfD; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos pos = new BlockPos(origin.getX() + dx, floor + dy,
                            origin.getZ() + dz);
                    if (world.isLoaded(pos) && world.hasFluid(pos)) {
                        wet++;
                    }
                }
            }
        }
        if (wet > 0) {
            faults.add(new Fault(building.blueprintId(), origin,
                    "standing fluid inside it (" + wet + " blocks)"));
        }
    }

    /**
     * Somewhere along the walls there must be a way in a person can use.
     *
     * <p>A doorway is a two-high gap in the wall ring with standable ground just
     * outside it — where "standable" means anything with collision to stand on,
     * and "just outside" allows one block up or down. Both clauses were paid
     * for. The first version demanded a sturdy full top face at exactly floor
     * level, and was corrected by a player who walked straight through a door it
     * had reported as no way in, across even ground: the block outside that door
     * was the town's own path, and a dirt path is a fifteen-sixteenths block
     * whose top face is not "sturdy" — so the houses with a track laid to their
     * doorstep, the best-connected houses in town, were precisely the ones
     * flagged as unenterable. The step tolerance covers doors at the head of
     * their own stair flight, whose top tread sits one below the floor.
     *
     * <p>A fence gate counts whether open or shut — it is an intended access
     * point even where it is kept closed to pen animals. No gap on any side is
     * the town hall with its door a storey up, or a doorway the terrain has
     * swallowed.
     */
    private static void checkDoorway(WorldView world, Building building, BlockPos origin,
                                     int floor, int wallHalfW, int wallHalfD,
                                     List<Fault> faults) {
        // Every column, no sampling. A doorway is one column wide, and stepping
        // by two walked straight past the door on two of the four sides — which
        // reported half the town as having no way in when it plainly did.
        for (BlockPos wall : ring(origin, wallHalfW, wallHalfD, 1)) {
            BlockPos feet = new BlockPos(wall.getX(), floor + 1, wall.getZ());
            if (world.isFenceGate(feet)) {
                return;   // a gate is a way in, even one kept shut on purpose
            }
            if (!world.isPassable(feet) || !world.isPassable(feet.above())) {
                continue;   // solid wall here
            }
            // A gap. Is there ground to stand on just outside it — level with
            // the floor, one step down (a stair tread), or one hop up (a shelf
            // the terrain left a block proud)?
            Direction out = outward(origin, wall);
            BlockPos outside = feet.relative(out);
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos ground = new BlockPos(outside.getX(), floor + dy, outside.getZ());
                if (world.isStandable(ground)
                        && world.isPassable(ground.above())
                        && world.isPassable(ground.above(2))) {
                    return;
                }
            }
        }
        faults.add(new Fault(building.blueprintId(), origin,
                "no way in — no doorway at grade on any side"));
    }

    /**
     * A farm should be mostly planted, and not strewn with popped seed items.
     *
     * <p>Bare farmland at scale means the crops were destroyed after placement —
     * trampled, flooded, or updated off their soil — which the player sees as a
     * field of floating seeds.
     */
    private static void checkField(WorldView world, Building building, BlockPos origin,
                                   int floor, int wallHalfW, int wallHalfD,
                                   List<Fault> faults) {
        // Every judgement below is about something happening over time — a field
        // filling up, or being emptied. On ground that is loaded but not running,
        // nothing happens at all: crops do not grow, farmers are not asked to
        // work, and dropped items never despawn. A field there is frozen exactly
        // as it was when the last person walked away from it, and reporting that
        // as "something is destroying the crops" is an accusation with no
        // evidence behind it.
        //
        // This is the same rule the rest of the auditor already follows — a plot
        // with no surveyed footprint is not guessed at. Not knowing is not a
        // fault, and saying nothing is the honest answer.
        if (!world.isTicking(origin)) {
            return;
        }
        int farmland = 0;
        int planted = 0;
        java.util.Set<BlockPos> nowPlanted = new java.util.HashSet<>();
        for (int dx = -wallHalfW; dx <= wallHalfW; dx++) {
            for (int dz = -wallHalfD; dz <= wallHalfD; dz++) {
                BlockPos soil = new BlockPos(origin.getX() + dx, floor - 1, origin.getZ() + dz);
                if (!world.isLoaded(soil)) {
                    continue;
                }
                if (world.isFarmland(soil)) {
                    farmland++;
                    if (world.isCrop(soil.above())) {
                        planted++;
                        nowPlanted.add(soil.above());
                    }
                }
            }
        }

        // Name what vanished since last sweep, and what stands in its place.
        java.util.Set<BlockPos> before = LAST_PLANTED.put(origin, nowPlanted);
        if (before != null) {
            int reported = 0;
            for (BlockPos was : before) {
                if (nowPlanted.contains(was) || !world.isLoaded(was)) {
                    continue;
                }
                if (reported++ >= MAX_VANISHED_REPORTED) {
                    break;
                }
                faults.add(new Fault(building.blueprintId(), origin,
                        "crop vanished at " + was.toShortString()
                                + " — there now: " + world.blockNameAt(was)
                                + ", soil: " + world.blockNameAt(was.below())));
            }
        }
        if (farmland >= MIN_FIELD_TO_JUDGE && planted * 2 < farmland) {
            faults.add(new Fault(building.blueprintId(), origin,
                    "half the field is bare — " + farmland + " farmland, "
                            + planted + " planted"));
        }

        // Loose items alone convict nobody: nearby excavation showers a plot
        // with leaf litter and grass seeds from support-loss drops, which is
        // untidy but harmless. Items only testify when the field is ALSO losing
        // its planting — that pairing is what a genuine crop-killer looks like.
        if (farmland >= MIN_FIELD_TO_JUDGE && planted * 2 < farmland) {
            AABB box = new AABB(
                    origin.getX() - wallHalfW, floor - 1, origin.getZ() - wallHalfD,
                    origin.getX() + wallHalfW + 1, floor + 3, origin.getZ() + wallHalfD + 1);
            int loose = world.looseItemsIn(box);
            if (loose >= LOOSE_ITEM_THRESHOLD) {
                faults.add(new Fault(building.blueprintId(), origin,
                        "bare AND strewn with " + loose
                                + " items — something is destroying the crops"));
            }
        }
    }

    /**
     * Two buildings whose walls share ground have been built through each other.
     *
     * <p>Judged on walls rather than plots: neighbouring cleared shelves may meet
     * by design, but masonry inside masonry is always the destructive case.
     */
    private static void auditOverlaps(List<Building> present, List<Fault> faults) {
        for (int i = 0; i < present.size(); i++) {
            for (int j = i + 1; j < present.size(); j++) {
                Building a = present.get(i);
                Building b = present.get(j);
                if (!a.footprint().isKnown() || !b.footprint().isKnown()) {
                    continue;
                }
                int reachX = wallHalf(a.footprint().width()) + wallHalf(b.footprint().width());
                int reachZ = wallHalf(a.footprint().depth()) + wallHalf(b.footprint().depth());
                if (Math.abs(a.origin().x() - b.origin().x()) <= reachX
                        && Math.abs(a.origin().z() - b.origin().z()) <= reachZ) {
                    faults.add(new Fault(a.blueprintId(),
                            new BlockPos(a.origin().x(), a.origin().y(), a.origin().z()),
                            "its walls run through " + b.blueprintId()
                                    + " at " + b.origin()));
                }
            }
        }
    }

    /**
     * The town itself, judged on simulation state rather than on blocks.
     *
     * <p>One fault, named after the way the founding death spiral actually
     * presented: residents at severe hunger while the build queue's head sat
     * unaffordable, so nothing behind it — a farm, a granary — could ever be
     * ordered. Either half alone is survivable and common; the conjunction is
     * the spiral, and in hindsight it was the one part of the autopsy a reader
     * outside the code could have spotted while the town was still alive.
     *
     * <p>Reads no blocks, so this is the one check that still runs for a town
     * nobody is watching — which is exactly the town that starves unnoticed.
     */
    private static void auditTown(WorldView world, Settlement settlement,
                                  List<Fault> faults) {
        int worstHunger = 0;
        int severe = 0;
        for (Person person : settlement.residents()) {
            worstHunger = Math.max(worstHunger, person.hunger());
            if (person.hunger() >= Person.HUNGER_SEVERE) {
                severe++;
            }
        }
        List<BuildTask> queue = settlement.buildQueue();
        BuildTask head = queue.isEmpty() ? null : queue.getFirst();
        // Unconditionally, even when the town is fed: the stall clock has to be
        // running before the hunger arrives, or the first sweep that sees a
        // famine would report the queue as freshly frozen and start counting.
        long stalled = trackHead(world, settlement, head);
        if (head == null || stalled < FROZEN_QUEUE_STEPS
                || hungerTier(worstHunger) != Distress.SEVERE) {
            return;
        }
        faults.add(Fault.ofTown("starving with a frozen build queue — "
                + severe + " of " + settlement.population() + " at severe hunger, and "
                + head.blueprintId() + " has held the head at " + head.progress()
                + "/" + head.requiredWork() + " for " + stalled + " steps with "
                + (queue.size() - 1) + " behind it"));
    }

    /**
     * How many simulation steps the build queue's head has sat exactly as it is.
     *
     * <p>Remembering the head rather than counting sweeps is what makes this
     * immune to how often it is asked: {@code /civ audit} run twice in a second
     * must not convict a build that simply had no simulation step in between.
     */
    private static long trackHead(WorldView world, Settlement settlement, BuildTask head) {
        if (head == null) {
            LAST_HEAD.remove(settlement.id());
            return 0L;
        }
        // Every figure here moves the moment a builder does anything — a block
        // dug, a block laid, a step credited. Granted-but-unspent work is left
        // out deliberately: the simulation keeps offering it to a build nobody is
        // able to work on, which would read as progress in a town making none.
        String signature = head.blueprintId() + "@" + head.origin() + " "
                + head.progress() + "/" + head.workDone() + "/" + head.stepsDone();
        long now = world.stepsElapsed();
        HeadState seen = LAST_HEAD.get(settlement.id());
        // The step count restarts at zero with the server, and forget() only
        // runs if the server stops cleanly. A stamp from the future is therefore
        // a memory that outlived a crash, not a stall: start the clock again.
        if (seen == null || !seen.signature().equals(signature) || now < seen.sinceStep()) {
            LAST_HEAD.put(settlement.id(), new HeadState(signature, now));
            return 0L;
        }
        return now - seen.sinceStep();
    }

    /** The simulation's own step count, which — unlike the level clock — never freezes. */
    private static long stepsElapsed(WorldView world) {
        return world.stepsElapsed();
    }

    // --- small helpers ---

    private static int wallHalf(int plotSpan) {
        return Math.max(1, plotSpan / 2 - BlueprintPlacer.APRON_MARGIN);
    }

    /** Which way is out, from a spot on a building's wall ring. */
    private static Direction outward(BlockPos origin, BlockPos wall) {
        int dx = wall.getX() - origin.getX();
        int dz = wall.getZ() - origin.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** The rectangle of columns at these half-extents around the origin. */
    private static List<BlockPos> ring(BlockPos origin, int halfW, int halfD, int step) {
        List<BlockPos> spots = new ArrayList<>();
        for (int dx = -halfW; dx <= halfW; dx += step) {
            spots.add(origin.offset(dx, 0, -halfD));
            spots.add(origin.offset(dx, 0, halfD));
        }
        for (int dz = -halfD + 1; dz < halfD; dz += step) {
            spots.add(origin.offset(-halfW, 0, dz));
            spots.add(origin.offset(halfW, 0, dz));
        }
        return spots;
    }

    private static boolean isPath(String blueprintId) {
        return !BuildPlanner.holdsGround(blueprintId);
    }

    /** A crop farm, as opposed to the animal one. Levels and styles both allowed for. */
    private static boolean isCropFarm(String blueprintId) {
        return BuildingRole.of(blueprintId) == BuildingRole.CROP_FARM;
    }
}
