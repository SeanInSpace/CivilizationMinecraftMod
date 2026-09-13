package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.BuildTask;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Perimeter;
import com.civilization.sim.settlement.PerimeterPlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The grown towns every wall measurement in this project is taken on.
 *
 * <p><strong>A main rather than a test, on purpose.</strong> It grows every
 * arrangement on eight sandbox seeds and on the recorded ground of seed 8675309
 * — 126 towns of about eighty buildings each, fourteen hundred steps apiece so
 * that a wall has time to be moved once — and that takes four and a half
 * minutes. A suite that paid for this on every run is a suite nobody runs. The
 * cheap regression detectors for the same faults live in {@code HullTest},
 * {@code WallRestakeTest} and {@code PerimeterLayerPlanTest}; this is what the
 * before-and-after numbers in the changelog come from, kept so that the next
 * person can take them again rather than trust them.
 *
 * <p>Run it by hand, from the test runtime classpath:
 * {@code java -cp <common test classes>:<common classes>:<junit jars> com.civilization.sim.WallProbe}
 * — or point an IDE at {@link #main}. {@code -Dprobe.steps=700} grows them less
 * far, which is the fixture the earlier reports used.
 *
 * <p>What it counts, and why each number is here:
 * <ul>
 *   <li><strong>postsInPlots</strong> — plots with a wall post inside them at the
 *       moment the ring was staked, standing and ordered counted apart. 36 before
 *       the hull was taught to repair its own starting legs, 1 after.</li>
 *   <li><strong>buildingsOnRetiredPosts</strong> — buildings standing on the posts
 *       of a line the town has replaced. 347 before siting consulted the retired
 *       loop, 23 after, and those 23 predate the re-staking.</li>
 *   <li><strong>duplicateColumns</strong> — the same column twice on one ring,
 *       which the staircase walk can produce at an acute vertex. Measured at 7 to
 *       11 over the whole fixture, none of them a gateway, which is what ruled
 *       that out as the cause of the missing posts.</li>
 * </ul>
 */
public final class WallProbe {

    private static final int STEPS = Integer.getInteger("probe.steps", 1400);
    private static final SimPos CENTER = new SimPos(0, 72, 0);

    private static List<String> layouts() {
        Set<String> named = new LinkedHashSet<>();
        for (Culture culture : Culture.all()) {
            named.addAll(culture.layouts());
        }
        List<String> out = new ArrayList<>(named);
        out.sort(String::compareTo);
        return out;
    }

    private record Staking(int step, Perimeter line, List<Building> standing,
                           List<BuildTask> ordered) {
    }

    static void probe() {
        List<WorldBridge> grounds = new ArrayList<>();
        List<String> groundNames = new ArrayList<>();
        for (int seed = 1; seed <= 8; seed++) {
            grounds.add(new TerrainFake(seed));
            groundNames.add("fake" + seed);
        }
        grounds.add(RecordedTerrain.of(RecordedTerrain.SEED_8675309));
        groundNames.add("recorded8675309");

        int towns = 0;
        int postsInPlots = 0;
        int postsInStanding = 0;
        int postsInOrdered = 0;
        int onRetired = 0;
        int ringPositions = 0;
        int duplicateColumns = 0;
        int duplicateGatewayConflicts = 0;
        int strayGateways = 0;
        int stakings = 0;
        int buildingsAtEnd = 0;
        int orderedAtStakings = 0;
        int retiredLines = 0;
        int retiredPositions = 0;
        Map<String, Integer> perGround = new HashMap<>();
        long began = System.nanoTime();

        for (String layout : layouts()) {
            for (int g = 0; g < grounds.size(); g++) {
                towns++;
                Grown grown = grow(layout, grounds.get(g));
                for (Staking staked : grown.stakings) {
                    stakings++;
                    orderedAtStakings += staked.ordered().size();
                    Set<SimPos> posts = new HashSet<>(staked.line().ringPositions());
                    for (Building b : staked.standing()) {
                        if (!BuildPlanner.holdsGround(b.blueprintId())) {
                            continue;
                        }
                        if (anyPostInside(posts, b.origin(),
                                BuildPlanner.plotSpanOf(b.blueprintId(), BuildCatalog.DEFAULT))) {
                            postsInPlots++;
                            postsInStanding++;
                        }
                    }
                    for (BuildTask t : staked.ordered()) {
                        if (!BuildPlanner.holdsGround(t.blueprintId()) || t.isUpgrade()) {
                            continue;
                        }
                        if (anyPostInside(posts, t.origin(),
                                BuildPlanner.plotSpanOf(t.blueprintId(), BuildCatalog.DEFAULT))) {
                            postsInPlots++;
                            postsInOrdered++;
                            perGround.merge(groundNames.get(g), 1, Integer::sum);
                        }
                    }
                }
                Perimeter ring = grown.town.perimeter();
                if (ring == null) {
                    continue;
                }
                ringPositions += ring.length();
                buildingsAtEnd += grown.town.buildings().size();
                retiredLines += ring.retired().size();
                retiredPositions += ring.retiredPositions().size();
                // --- bug 4: buildings standing on a retired line's posts ---
                Set<SimPos> retired = new HashSet<>(ring.retiredPositions());
                if (!retired.isEmpty()) {
                    for (Building b : grown.town.buildings()) {
                        if (!BuildPlanner.holdsGround(b.blueprintId())) {
                            continue;
                        }
                        if (anyPostInside(retired, b.origin(),
                                BuildPlanner.plotSpanOf(b.blueprintId(), BuildCatalog.DEFAULT))) {
                            onRetired++;
                        }
                    }
                }
                // --- bug 1: the same column twice on one ring ---
                List<SimPos> walk = ring.ringPositions();
                Map<Long, Integer> firstAt = new HashMap<>();
                for (int i = 0; i < walk.size(); i++) {
                    SimPos at = walk.get(i);
                    long column = ((long) at.x() << 32) ^ (at.z() & 0xffffffffL);
                    Integer seen = firstAt.putIfAbsent(column, i);
                    if (seen == null) {
                        continue;
                    }
                    duplicateColumns++;
                    if (ring.isGateway(walk.get(seen)) != ring.isGateway(at)) {
                        duplicateGatewayConflicts++;
                    }
                }
                // --- and a gateway position nowhere near its opening ---
                for (int i = 0; i < walk.size(); i++) {
                    SimPos at = walk.get(i);
                    if (!ring.isGateway(at)) {
                        continue;
                    }
                    boolean nearInIndex = false;
                    for (SimPos gate : ring.gates()) {
                        int gi = walk.indexOf(gate);
                        if (gi < 0) {
                            continue;
                        }
                        int apart = Math.abs(gi - i);
                        apart = Math.min(apart, walk.size() - apart);
                        if (apart <= 2 && Math.max(Math.abs(gate.x() - at.x()),
                                Math.abs(gate.z() - at.z())) <= 1) {
                            nearInIndex = true;
                            break;
                        }
                    }
                    if (!nearInIndex) {
                        strayGateways++;
                    }
                }
            }
        }
        long took = (System.nanoTime() - began) / 1_000_000L;
        System.out.println("PROBE steps=" + STEPS + " towns=" + towns
                + " stakings=" + stakings
                + " orderedAtStakings=" + orderedAtStakings
                + " buildingsAtEnd=" + buildingsAtEnd
                + " retiredLines=" + retiredLines
                + " retiredPositions=" + retiredPositions);
        System.out.println("PROBE towns=" + towns
                + " postsInPlots=" + postsInPlots
                + " (standing=" + postsInStanding + " ordered=" + postsInOrdered + ")"
                + " buildingsOnRetiredPosts=" + onRetired
                + " ringPositionsTotal=" + ringPositions
                + " duplicateColumns=" + duplicateColumns
                + " duplicateGatewayConflicts=" + duplicateGatewayConflicts
                + " strayGateways=" + strayGateways
                + " tookMs=" + took);
        System.out.println("PROBE orderedByGround=" + perGround);
    }

    /** One ground, and what the legs themselves answer. */
    static void diagnose() {
        RecordedTerrain ground = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        int fouledLegs = 0;
        int stakings = 0;
        int postsInPlots = 0;
        for (String layout : layouts()) {
            Grown grown = grow(layout, ground);
            for (Staking staked : grown.stakings) {
                stakings++;
                List<com.civilization.sim.geom.Hull.Keepout> squares = new ArrayList<>();
                for (Building b : staked.standing()) {
                    if (BuildPlanner.holdsGround(b.blueprintId())) {
                        squares.add(new com.civilization.sim.geom.Hull.Keepout(
                                b.origin().x(), b.origin().z(),
                                BuildPlanner.plotSpanOf(b.blueprintId(),
                                        BuildCatalog.DEFAULT) / 2.0));
                    }
                }
                for (BuildTask t : staked.ordered()) {
                    if (BuildPlanner.holdsGround(t.blueprintId()) && !t.isUpgrade()) {
                        squares.add(new com.civilization.sim.geom.Hull.Keepout(
                                t.origin().x(), t.origin().z(),
                                BuildPlanner.plotSpanOf(t.blueprintId(),
                                        BuildCatalog.DEFAULT) / 2.0));
                    }
                }
                List<SimPos> loop = staked.line().vertices();
                for (int i = 0; i < loop.size(); i++) {
                    if (com.civilization.sim.geom.Hull.crossesKeepout(
                            loop.get(i), loop.get((i + 1) % loop.size()), squares)) {
                        fouledLegs++;
                    }
                }
                Set<SimPos> posts = new HashSet<>(staked.line().ringPositions());
                for (com.civilization.sim.geom.Hull.Keepout square : squares) {
                    if (anyPostInside(posts, new SimPos(square.x(), 0, square.z()),
                            (int) (square.half() * 2))) {
                        postsInPlots++;
                    }
                }
            }
        }
        System.out.println("PROBE diagnose stakings=" + stakings
                + " fouledLegs=" + fouledLegs + " postsInPlots=" + postsInPlots);
    }

    /** The staking of a town of two hundred, timed. */
    static void timeStakingATownOfTwoHundred() {
        RecordedTerrain ground = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        Settlement town = new Settlement(Settlement.Id.random(), "Big", CENTER, 512);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.TOWN);
        town.setCultureId("civilization:human/burgher");
        // A grid of houses twelve apart, which is a plot and a lane, spiralling
        // out from the middle: two hundred of them reach about 84 blocks out,
        // which is the size of town the timing was taken on.
        int placed = 0;
        for (int ring = 0; placed < 200; ring++) {
            for (int dx = -ring; dx <= ring && placed < 200; dx++) {
                for (int dz = -ring; dz <= ring && placed < 200; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    town.addBuilding(new Building("civilization:house",
                            new SimPos(CENTER.x() + 12 * dx, CENTER.y(),
                                    CENTER.z() + 12 * dz), 1, true));
                    placed++;
                }
            }
        }
        SimContext ctx = new SimContext(ground, 2000, SimSettings.SANDBOX);
        Perimeter warm = PerimeterPlanner.stake(town, ctx);
        long best = Long.MAX_VALUE;
        long total = 0;
        int runs = 10;
        for (int run = 0; run < runs; run++) {
            long began = System.nanoTime();
            PerimeterPlanner.stake(town, ctx);
            long took = System.nanoTime() - began;
            best = Math.min(best, took);
            total += took;
        }

        // And the hull on its own, off the same town, because the staking is
        // three other things as well -- the push-out and four relaxation passes
        // that read the ground -- and only the hull is being changed.
        List<SimPos> points = new ArrayList<>();
        List<com.civilization.sim.geom.Hull.Keepout> squares = new ArrayList<>();
        for (Building b : town.buildings()) {
            int half = BuildPlanner.plotSpanOf(b.blueprintId(), BuildCatalog.DEFAULT) / 2;
            squares.add(new com.civilization.sim.geom.Hull.Keepout(
                    b.origin().x(), b.origin().z(), half));
            for (int sx = -1; sx <= 1; sx++) {
                for (int sz = -1; sz <= 1; sz++) {
                    if (sx != 0 || sz != 0) {
                        points.add(new SimPos(b.origin().x() + sx * half, CENTER.y(),
                                b.origin().z() + sz * half));
                    }
                }
            }
        }
        com.civilization.sim.geom.Hull.concave(points, 24, squares);
        long hullBest = Long.MAX_VALUE;
        for (int run = 0; run < runs; run++) {
            long began = System.nanoTime();
            com.civilization.sim.geom.Hull.concave(points, 24, squares);
            hullBest = Math.min(hullBest, System.nanoTime() - began);
        }
        System.out.println("PROBE hull200 points=" + points.size()
                + " squares=" + squares.size() + " bestMs=" + hullBest / 1_000_000L);
        System.out.println("PROBE stake200 buildings=" + town.buildings().size()
                + " vertices=" + warm.vertices().size()
                + " posts=" + warm.length()
                + " bestMs=" + best / 1_000_000L
                + " meanMs=" + total / runs / 1_000_000L);
    }

    public static void main(String[] args) {
        timeStakingATownOfTwoHundred();
        diagnose();
        probe();
    }

    private WallProbe() {
    }

    private static boolean anyPostInside(Set<SimPos> posts, SimPos origin, int span) {
        double half = span / 2.0 - 1;
        for (SimPos post : posts) {
            if (Math.abs(post.x() - origin.x()) <= half
                    && Math.abs(post.z() - origin.z()) <= half) {
                return true;
            }
        }
        return false;
    }

    private static final class Grown {
        Settlement town;
        List<Staking> stakings = new ArrayList<>();
    }

    private static Grown grow(String layout, WorldBridge ground) {
        Grown grown = new Grown();
        Settlement town = new Settlement(Settlement.Id.random(), "Probe", CENTER, 512);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        for (Culture culture : Culture.all()) {
            if (culture.layouts().contains(layout)) {
                town.setCultureId(culture.id());
                break;
            }
        }
        town.setLayoutId(layout);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(Person.Id.random(), name, Profession.PIONEER, CENTER));
        }
        Perimeter last = null;
        for (int step = 1; step <= STEPS; step++) {
            List<Building> stood = List.copyOf(town.buildings());
            List<BuildTask> queued = List.copyOf(town.buildQueue());
            town.stores().add(TownStores.WOOD, 8);
            town.stores().add(TownStores.STONE, 6);
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
            Perimeter now = town.perimeter();
            if (now != null && now != last) {
                grown.stakings.add(new Staking(step, now, stood, queued));
            }
            last = now;
        }
        grown.town = town;
        return grown;
    }
}
