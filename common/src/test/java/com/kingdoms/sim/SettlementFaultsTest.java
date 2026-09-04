package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.Layout;
import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eight faults a survey of one grown town turned up, each held shut.
 *
 * <p>Every one of them was found by dumping a real settlement's geometry and
 * measuring it — not by reading the code and not by looking at a screenshot.
 * That is the point of the class: these are the faults that no unit test was
 * ever going to notice, because each one lives in the gap between two parts
 * that were separately correct.
 */
class SettlementFaultsTest {

    private static final SimPos CENTRE = new SimPos(0, 72, 0);

    private static Settlement grow(String cultureId, TerrainFake ground, int steps) {
        return growOn(cultureId, ground, steps);
    }

    private static Settlement growOn(String cultureId,
                                     com.kingdoms.sim.platform.WorldBridge ground,
                                     int steps) {
        return growAs(cultureId, surveyedArrangement(cultureId), ground, steps);
    }

    private static Settlement growAs(String cultureId, String layout,
                                     com.kingdoms.sim.platform.WorldBridge ground,
                                     int steps) {
        Settlement town = new Settlement(Settlement.Id.random(), "Survey", CENTRE, 512);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        town.setCultureId(cultureId);
        town.setLayoutId(layout);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(
                    Person.Id.random(), name, Profession.PIONEER, CENTRE));
        }
        for (int step = 1; step <= steps; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }
        return town;
    }

    /**
     * The arrangement the town these faults were found in was built in.
     *
     * <p>Named rather than left to the culture, because a people builds in
     * several arrangements now and picks between them by hashing the centre.
     * Every fault below was measured on one grown town and the numbers here are
     * that town's — the ceiling on posts staked through a building is four
     * because four is what a burgher high street managed, and the same fixture
     * left to choose for itself came out as a compass-drawn radial town and
     * staked through six. Which is worth knowing, and is not what any of these
     * tests are about.
     */
    private static String surveyedArrangement(String cultureId) {
        return Culture.of(cultureId).layouts().get(0);
    }

    /** The buildings a staked ring's posts pass through, plot for plot. */
    private static List<Building> stakedThrough(Settlement town, Perimeter ring) {
        Set<SimPos> posts = new HashSet<>(ring.ringPositions());
        List<Building> onTheLine = new java.util.ArrayList<>();
        for (Building b : holdingGround(town)) {
            int half = BuildPlanner.plotSpanOf(b.blueprintId(), town.catalogue()) / 2;
            for (SimPos post : posts) {
                if (Math.abs(post.x() - b.origin().x()) <= half
                        && Math.abs(post.z() - b.origin().z()) <= half) {
                    onTheLine.add(b);
                    break;
                }
            }
        }
        return onTheLine;
    }

    private static List<Building> holdingGround(Settlement town) {
        List<Building> out = new java.util.ArrayList<>();
        for (Building b : town.buildings()) {
            if (BuildPlanner.holdsGround(b.blueprintId())) {
                out.add(b);
            }
        }
        return out;
    }

    // --- 1 ---------------------------------------------------------------

    @Test
    void puttingASettlementBackDoesNotRestampItsPeople() {
        // Loading a save is not founding a town. The codec restored each
        // settlement's own culture and then handed the list to addSettlement,
        // which overwrote it with the kingdom's -- so /civ culture worked for as
        // long as the server stayed up and reverted on the next load. A town set
        // to the vale folk grew 214 ring-road carriageways and came back as a
        // Norman town laid out in rings, keeping streets no ring town would ever
        // build.
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Normandy", "kingdoms:norman");
        Settlement town = new Settlement(Settlement.Id.random(), "Ringmere", CENTRE, 256);
        town.setCultureId("kingdoms:vale");

        kingdom.restoreSettlement(town);
        assertEquals("kingdoms:vale", town.cultureId(),
                "a load restamped the settlement with its kingdom's culture");
        assertTrue(Culture.VALE.layouts().contains(town.arrangement().id()),
                "and so it came back laid out as somebody else");
        assertFalse(Culture.NORMAN.layouts().contains(town.arrangement().id()),
                "specifically, laid out as its kingdom rather than as itself");

        // Founding still adopts the kingdom's people, which is the other half of
        // why these are two methods and not one with a flag.
        Settlement fresh = new Settlement(Settlement.Id.random(), "New", CENTRE, 256);
        kingdom.addSettlement(fresh);
        assertEquals("kingdoms:norman", fresh.cultureId(),
                "a newly founded town takes its kingdom's people");
    }

    // --- 2, 3, 8 -----------------------------------------------------------

    @Test
    void everyGateIsAPostOnTheWallAndAWayThroughIt() {
        // Gates were computed on the town's bounding box while the ring is a
        // concave hull, so three of four stood 9, 10 and 53 blocks from any
        // wall. They cut no opening, the wall was raised solid across the roads,
        // and one gate opened onto nothing 29 blocks from the nearest road.
        Settlement town = grow("kingdoms:burgher", new TerrainFake(11), 500);
        Perimeter ring = town.perimeter();
        assertTrue(ring != null, "five hundred steps is plenty to stake a ring");

        Set<SimPos> onRing = new HashSet<>(ring.ringPositions());
        for (SimPos gate : ring.gates()) {
            assertTrue(onRing.contains(gate),
                    "gate " + gate + " is not a post on the wall, so it cuts no opening");
        }
        int openings = 0;
        for (SimPos post : ring.ringPositions()) {
            if (ring.isGateway(post)) {
                openings++;
            }
        }
        assertTrue(openings >= 2 * ring.gates().size(),
                "only " + openings + " gateway posts for " + ring.gates().size()
                        + " gates: the wall is solid where it should be open");

        // And a gate is somewhere somebody would walk.
        for (SimPos gate : ring.gates()) {
            double nearest = Double.MAX_VALUE;
            for (PathNetwork.Segment run : town.paths().segments()) {
                nearest = Math.min(nearest, gate.horizontalDistance(run.nearestTo(gate)));
            }
            assertTrue(nearest < 24,
                    "the gate at " + gate + " stands " + Math.round(nearest)
                            + " blocks from the nearest road");
        }
    }

    // --- 4 ----------------------------------------------------------------

    @Test
    void aTownStopsBuildingAcrossItsOwnWall() {
        // Two halves, and only one of them is a growth fault.
        //
        // The town keeps building after the ring is staked, and nothing used to
        // keep a new building off the line. That half is closed: siting refuses
        // wall ground, and nothing is raised across the palisade ever again.
        TerrainFake ground = new TerrainFake(11);
        Settlement town = new Settlement(Settlement.Id.random(), "Survey", CENTRE, 512);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        town.setCultureId("kingdoms:burgher");
        town.setLayoutId(surveyedArrangement("kingdoms:burgher"));
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(
                    Person.Id.random(), name, Profession.PIONEER, CENTRE));
        }
        long staked = -1;
        for (int step = 1; step <= 500; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
            if (staked < 0 && town.perimeter() != null) {
                staked = step;
            }
        }
        Perimeter ring = town.perimeter();
        assertTrue(ring != null, "five hundred steps is plenty to stake a ring");

        List<Building> onTheLine = stakedThrough(town, ring);
        int across = onTheLine.size();
        int raisedAfter = 0;
        for (Building b : onTheLine) {
            if (b.completedOnStep() > staked) {
                raisedAfter++;
            }
        }
        // Deliberately NOT asserted at zero on a grown town. completedOnStep is
        // when a building FINISHED, not when its plot was chosen: one sited
        // before the ring went up and finished after it counts as "raised
        // after" though nothing could have refused its ground. Measuring the
        // wrong moment is how a green test gets written about a fault that is
        // still there, and how a red one gets written about a fault that never
        // was. The rule itself is asserted directly below instead.
        assertTrue(raisedAfter <= RAISED_ACROSS_CEILING,
                raisedAfter + " buildings were raised across a wall that already stood,"
                        + " past the " + RAISED_ACROSS_CEILING + " this measure allows");
        assertTrue(across <= STAKED_THROUGH_CEILING,
                "the ring was staked through " + across + " buildings, past the "
                        + STAKED_THROUGH_CEILING + " it is allowed");
    }

    /**
     * How many buildings this circumstantial measure tolerates being "raised
     * across" the wall, and why it is not one.
     *
     * <p>It flaps. Any change anywhere in siting shifts the town, and a building
     * sited before the ring went up but finished after it lands on the wrong
     * side of this count through no fault of the rule. Tightening it to one
     * produced a red suite twice from changes that had nothing to do with walls.
     *
     * <p>A test that fails for reasons other than the thing it names teaches
     * people to ignore it. The rule itself is asserted exactly in
     * {@code groundUnderTheWallIsNotFreeToBuildOn}; this stays only to catch a
     * collapse, and a collapse looks like ten, not three.
     */
    private static final int RAISED_ACROSS_CEILING = 3;

    /**
     * How many buildings the ring may still be staked through, and why not none.
     *
     * <p>The other half of the fault, and it is a property of the hull rather
     * than of the growth. {@code Hull.concave} wraps the outermost points, so a
     * building sitting just inside the edge contributes nothing to the boundary
     * and a stretch running between two further-out buildings cuts across it.
     * A measured town of sixteen had the wall through ten of them.
     *
     * <p>What fixed most of it was offering the hull each plot's corners pushed
     * a yard outward, which makes a near-edge building an extreme point in its
     * own right: twenty-one fouled stretches fell to one, and ten buildings to
     * two. What did <em>not</em> work, and is worth recording so nobody spends
     * the afternoon again: giving the hull the plots' edge midpoints (interior
     * points do not constrain a boundary); pushing fouled vertices outward
     * (on a concave line that drops a building in the notch beyond a neighbour,
     * and guarding the move with containment then rejects nearly all of them —
     * ten stayed ten); inserting a detour at the plot's outer corner (the new
     * stretches cut across its neighbours, and twenty-one fouled stretches
     * became thirty-two); and simply widening the margin, which plateaus at four
     * and costs timber for it.
     *
     * <p>Two to three is what it measures, and which of those depends on
     * changes elsewhere in siting: the town shifts, and a plot that used to sit
     * clear of the line now sits on it. Pinned at two it went red twice from
     * work that had nothing to do with walls, which is how a test teaches people
     * to ignore it.
     *
     * <p>So this is a collapse-catcher, not a measurement — a collapse looks
     * like ten, which is what it was before the corners were pushed outward.
     * Lower it when the ring is traced round the union of the plots rather than
     * wrapped about their corners; do not raise it to make room for a
     * regression.
     */
    private static final int STAKED_THROUGH_CEILING = 4;

    /**
     * The same measure for a compass-drawn town, which does worse.
     *
     * <p>Six, against the high street's four, on the same seed and the same five
     * hundred steps. Not a regression in the hull — the fault above is unchanged
     * — but the arrangement feeds it more to get wrong: a radial town packs
     * frontage on both faces of every ring road, so far more buildings sit just
     * inside the outermost course, and every one of them is a point the hull
     * wraps past rather than round.
     *
     * <p>Recorded rather than hidden. The burghers build in this arrangement now,
     * so about half their towns meet this number in a real world, and pinning the
     * fault fixture to the high street alone would have made the whole thing
     * invisible. Comes down with the same fix as the constant above — trace the
     * ring round the union of the plots — and must not go up.
     */
    private static final int RADIAL_STAKED_THROUGH_CEILING = 6;

    @Test
    void aCompassDrawnTownStakesItsRingThroughMoreThanAHighStreetDoes() {
        Settlement town = growAs("kingdoms:burgher", Culture.LAYOUT_RADIAL_CONCENTRIC,
                new TerrainFake(11), 500);
        Perimeter ring = town.perimeter();
        assertTrue(ring != null, "five hundred steps is plenty to stake a ring");

        int across = stakedThrough(town, ring).size();
        assertTrue(across <= RADIAL_STAKED_THROUGH_CEILING,
                "the ring was staked through " + across + " buildings, past the "
                        + RADIAL_STAKED_THROUGH_CEILING + " a radial town is allowed");
    }

    @Test
    void groundUnderTheWallIsNotFreeToBuildOn() {
        // The mechanism, asserted where it can be asserted exactly: a plot that
        // covers a post of the staked ring is not free ground, and one clear of
        // it is. The grown-town measure above can only ever be circumstantial,
        // because nothing records when a plot was chosen.
        Settlement town = new Settlement(Settlement.Id.random(), "Ring", CENTRE, 256);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setCultureId("kingdoms:burgher");
        town.setPerimeter(new Perimeter(
                List.of(new SimPos(-40, 72, -40), new SimPos(40, 72, -40),
                        new SimPos(40, 72, 40), new SimPos(-40, 72, 40)),
                List.of(), 0));

        SimPos onTheLine = new SimPos(40, 72, 0);      // a post of that ring
        SimPos wellInside = new SimPos(0, 72, 0);
        assertFalse(town.isPlotFree(onTheLine, 9, null),
                "ground under the palisade was offered as free");
        assertTrue(town.isPlotFree(wellInside, 9, null),
                "ground nowhere near the palisade was refused");
    }

    // --- 5 ----------------------------------------------------------------

    @Test
    void nothingIsBuiltOnACarriageway() {
        // The plan refuses to OFFER a plot standing in a road, and that
        // invariant is real -- but it only covers what a Layout hands out.
        // Farms, pastures and lumber camps are sited by their own planners and
        // were never asked: eight buildings in a measured town stood on an
        // eight-wide street, every one of them a farm.
        Settlement town = grow("kingdoms:burgher", new TerrainFake(11), 500);
        for (Building b : holdingGround(town)) {
            int span = BuildPlanner.plotSpanOf(b.blueprintId(), town.catalogue());
            for (PathNetwork.Segment run : town.paths().segments()) {
                if (run.width() <= PathNetwork.TRACK_WIDTH) {
                    continue;   // a footpath is a consequence of a building
                }
                assertFalse(run.touches(b.origin(), span / 2.0),
                        b.blueprintId() + " at " + b.origin() + " stands on a "
                                + run.width() + "-wide street");
            }
        }
    }

    /**
     * Ground that refuses nearly everything, so the give-up paths are reached.
     *
     * <p>The reason faults 5 and 7 survived a fix that measured clean. On the
     * ordinary sandbox terrain a town of sixty never exhausts its candidates,
     * so both the water rule and the street rule read a perfect zero — and in a
     * world, where slopes and unread chunks refuse far more, the same town put
     * five farms on carriageways and two houses in a river.
     *
     * <p>A test that only exercises the happy path certifies the happy path. So
     * this one refuses four sites in five, which is a great deal of refusing.
     *
     * <p>It is <strong>not</strong> enough to reach the give-up path, and that
     * is worth writing down because this class used to claim it was. Refusing
     * four in five means ninety-six candidates in a row are all refused about
     * once in every two hundred million buildings: measured, a town grown here
     * ends up with not one building on ground it had refused. Real ground
     * refuses in <em>families</em> — a lake, a hillside, a quarter nobody has
     * loaded — and it is that, not the rate, that empties a search.
     * {@code LeastBadSiteTest} has the ground that does.
     */
    private static final class CruelGround
            implements com.kingdoms.sim.platform.WorldBridge {
        private final TerrainFake ground;

        CruelGround(long seed) {
            this.ground = new TerrainFake(seed);
        }

        boolean wetAt(int x, int z) {
            return ground.wetAt(x, z);
        }

        @Override
        public boolean isSiteSuitable(SimPos plot, int radius) {
            return siteFault(plot, radius) == SITE_FAULT_NONE;
        }

        /**
         * The cruelty, scored: a spread of grudges rather than one flat refusal.
         *
         * <p>Four candidates in five are refused for nothing to do with their
         * own merits, which is the whole point of this ground — but they are
         * refused by <em>different amounts</em>, so a town falling back on the
         * least-bad plot it examined has something to prefer. A single flat
         * charge would make every refused plot identical and the fallback would
         * be testing its tie-break rather than its ranking.
         */
        @Override
        public int siteFault(SimPos plot, int radius) {
            int fault = ground.siteFault(plot, radius);
            if (fault == SITE_FAULT_OPEN_WATER) {
                return fault;   // never a quantity, so never added to
            }
            return fault + Math.floorMod(plot.x() * 31 + plot.z() * 17, 5);
        }

        @Override
        public int surfaceHeight(SimPos pos) {
            return ground.surfaceHeight(pos);
        }

        @Override
        public boolean isLoaded(SimPos pos) {
            return ground.isLoaded(pos);
        }

        @Override
        public boolean playerWithin(SimPos pos, double radius) {
            return ground.playerWithin(pos, radius);
        }

        @Override
        public boolean standsInWater(SimPos pos, int radius) {
            return ground.standsInWater(pos, radius);
        }

        @Override
        public com.kingdoms.sim.settlement.Footprint materializeBlueprint(
                String id, SimPos origin, boolean surveyed, int facing) {
            return ground.materializeBlueprint(id, origin, surveyed, facing);
        }

        @Override
        public int woodedness(SimPos centre, int radius) {
            return ground.woodedness(centre, radius);
        }

        @Override
        public void log(String message) {
        }
    }

    @Test
    void aTownOnCruelGroundStillRefusesRoadsAndRivers() {
        // Desperation is a reason to take poor ground. It is never a reason to
        // take taken ground, the carriageway, or the river -- there is always
        // more ground further out.
        CruelGround ground = new CruelGround(11);
        Settlement town = growOn("kingdoms:vale", ground, 500);
        List<Building> held = holdingGround(town);
        assertTrue(held.size() >= 12,
                "only " + held.size() + " buildings: the ground was too cruel to test with");

        int refusedGround = 0;
        for (Building b : held) {
            int span = BuildPlanner.plotSpanOf(b.blueprintId(), town.catalogue());
            assertFalse(ground.wetAt(b.origin().x(), b.origin().z()),
                    b.blueprintId() + " at " + b.origin() + " was built in the water");
            if (ground.siteFault(b.origin(), BuildPlanner.PLOT_PROBE_RADIUS) > 0) {
                refusedGround++;
            }
            for (PathNetwork.Segment run : town.paths().segments()) {
                if (run.width() <= PathNetwork.TRACK_WIDTH) {
                    continue;
                }
                assertFalse(run.touches(b.origin(), span / 2.0),
                        b.blueprintId() + " at " + b.origin() + " stands on a "
                                + run.width() + "-wide street");
            }
        }
        // And what this ground does NOT reach, measured rather than assumed.
        // Zero of twenty-two, which is the finding recorded against the fixture
        // above: a refusal rate, however high, does not exhaust a search of
        // ninety-six independent candidates. Allowed a couple so this reads as
        // a fact about the fixture and not a bar somebody has to keep.
        assertTrue(refusedGround <= 2,
                refusedGround + " of " + held.size() + " buildings stand on ground "
                        + "this town refused, so the give-up path is being reached "
                        + "here after all and this fixture means something new");
    }

    // --- 6 ----------------------------------------------------------------

    @Test
    void aPlannedTownsDoorsFaceTheirStreet() {
        // TownPlan.Plot has carried the street a plot fronts and the way its
        // door should look since streets were planned. Both were discarded:
        // takeNextPlot returned a bare position and BuildPlanner set
        // facingToward(centre), so every door in every planned town still turned
        // to the middle. The claim was true of the plan and false of the game.
        Layout planned = Layouts.of(Culture.LAYOUT_HIGH_STREET);
        int differs = 0;
        int fronting = 0;
        for (com.kingdoms.sim.culture.TownPlan.Plot plot
                : planned.planFor(CENTRE, 120).plots()) {
            if (!plot.frontsAStreet()) {
                continue;
            }
            fronting++;
            int fromLayout = planned.facingFor(CENTRE, plot.at());
            assertEquals(plot.facing(), fromLayout,
                    "the layout disagrees with its own plan at " + plot.at());
            if (fromLayout != Layout.facingToward(plot.at(), CENTRE)) {
                differs++;
            }
        }
        assertTrue(fronting > 50, "only " + fronting + " plots fronted a street");
        assertTrue(differs > fronting / 4,
                "only " + differs + " of " + fronting + " doors face anywhere other"
                        + " than the middle of town, which is the old rule wearing"
                        + " the new one's name");

        // A lattice has no streets and must still answer, the old way.
        Layout lattice = Layouts.RING;
        SimPos plot = lattice.plotFor(CENTRE, 4);
        assertEquals(Layout.facingToward(plot, CENTRE), lattice.facingFor(CENTRE, plot));
    }

    // --- 7 ----------------------------------------------------------------

    @Test
    void theGiveUpPathStopsChoosingRivers() {
        // Everything above it refuses water and then the last resort handed back
        // whichever plot index came next with no check of any kind -- not that
        // the ground was free, not that it was dry. It is the path that put the
        // last buildings of a town in a river.
        TerrainFake ground = new TerrainFake(11);
        Settlement town = grow("kingdoms:burgher", ground, 500);
        List<Building> wet = new java.util.ArrayList<>();
        for (Building b : holdingGround(town)) {
            if (ground.wetAt(b.origin().x(), b.origin().z())) {
                wet.add(b);
            }
        }
        assertTrue(wet.isEmpty(),
                "put " + wet.size() + " of " + holdingGround(town).size()
                        + " buildings in the water, first at "
                        + (wet.isEmpty() ? "-" : wet.get(0).origin().toString()));
    }
}
