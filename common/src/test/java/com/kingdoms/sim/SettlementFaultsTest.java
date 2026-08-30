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
        Settlement town = new Settlement(Settlement.Id.random(), "Survey", CENTRE, 512);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        town.setCultureId(cultureId);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(
                    Person.Id.random(), name, Profession.PIONEER, CENTRE));
        }
        for (int step = 1; step <= steps; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }
        return town;
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
        assertEquals(Culture.LAYOUT_RING_STREETS, town.arrangement().id(),
                "and so it came back laid out as somebody else");

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
        Set<SimPos> posts = new HashSet<>(ring.ringPositions());

        int across = 0;
        int raisedAfter = 0;
        for (Building b : holdingGround(town)) {
            int half = BuildPlanner.plotSpanOf(b.blueprintId(), town.catalogue()) / 2;
            boolean onWall = false;
            for (SimPos post : posts) {
                if (Math.abs(post.x() - b.origin().x()) <= half
                        && Math.abs(post.z() - b.origin().z()) <= half) {
                    onWall = true;
                    break;
                }
            }
            if (!onWall) {
                continue;
            }
            across++;
            if (b.completedOnStep() > staked) {
                raisedAfter++;
            }
        }
        assertEquals(0, raisedAfter,
                raisedAfter + " buildings were raised across a wall that already stood");
        assertTrue(across <= STAKED_THROUGH_CEILING,
                "the ring was staked through " + across + " buildings, past the "
                        + STAKED_THROUGH_CEILING + " it is allowed");
    }

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
     * <p>Two is what it measures today. It is recorded as a ceiling rather than
     * waived, so the number cannot quietly grow while the real fix — a ring
     * traced round the union of the plots rather than wrapped about their
     * corners — waits. Lower it when that lands; do not raise it.
     */
    private static final int STAKED_THROUGH_CEILING = 2;

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
