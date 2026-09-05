package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town's planned streets, made real.
 *
 * <p>Roads here have always been a <em>consequence</em>: a building went up, and
 * afterwards a track was run from its door to whatever passed nearest. That is
 * backwards from how settlements work and it is why a street in this simulation
 * had houses presenting their backs to it. The plan carries streets now, and
 * these are the rules about turning them into ground somebody can walk on.
 */
class PavedStreetsTest {

    private static final SimPos CENTRE = new SimPos(0, 72, 0);

    private static Settlement town(String layout, TerrainFake ground, int steps) {
        Settlement town = new Settlement(Settlement.Id.random(), "Paved", CENTRE, 512);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        for (Culture culture : Culture.all()) {
            if (culture.layouts().contains(layout)) {
                town.setCultureId(culture.id());
                break;
            }
        }
        // After the culture, which un-settles it: a people builds in several
        // arrangements now, and the fixture wants the one it asked for rather
        // than the one this center happens to hash to.
        town.setLayoutId(layout);
        assertEquals(layout, town.arrangement().id(), "fixture did not select " + layout);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(
                    Person.Id.random(), name, Profession.PIONEER, CENTRE));
        }
        for (int step = 1; step <= steps; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }
        return town;
    }

    @Test
    void aPlannedTownLaysItsStreetsBeforeItsBuildings() {
        // The point of the whole exercise. A street exists as ground because the
        // plan said so, not because a house went up and needed joining.
        Settlement town = town(Culture.LAYOUT_HIGH_STREET, new TerrainFake(11), 400);
        List<PathNetwork.Segment> wide = new java.util.ArrayList<>();
        for (PathNetwork.Segment segment : town.paths().segments()) {
            if (segment.width() > PathNetwork.TRACK_WIDTH) {
                wide.add(segment);
            }
        }
        assertFalse(wide.isEmpty(), "a high street town laid no streets at all");
        assertTrue(wide.size() >= 20,
                "only " + wide.size() + " stretches of street for a grown town");
    }

    @Test
    void anArrangementWithNoStreetsLaysNone() {
        // A warren is knots of huts with open ground between them. Seeding it a
        // high street would not improve it, it would delete it -- and the code
        // that decides has to agree with the code that draws.
        Settlement town = town(Culture.LAYOUT_WARREN, new TerrainFake(11), 400);
        for (PathNetwork.Segment segment : town.paths().segments()) {
            assertEquals(PathNetwork.TRACK_WIDTH, segment.width(),
                    "a warren laid a carriageway at " + segment.from());
        }
    }

    @Test
    void streetsAreNotRelaidOnEveryStep() {
        // A settlement steps every tick, and checking whether four hundred runs
        // are already in the network costs a scan of four hundred runs each. The
        // reach it has already laid to is remembered so the work is done once
        // per distance rather than once per tick.
        TerrainFake ground = new TerrainFake(11);
        Settlement town = town(Culture.LAYOUT_HIGH_STREET, ground, 300);
        int before = town.paths().segments().size();
        assertTrue(town.paths().streetsLaidFor() >= 0,
                "the town never recorded laying any streets");
        town.step(new SimContext(ground, 301, SimSettings.SANDBOX));
        assertTrue(town.paths().segments().size() - before <= 2,
                "a single step added " + (town.paths().segments().size() - before)
                        + " runs to a network that was already laid");
    }

    @Test
    void aTownOpensItsStreetsRatherThanHavingThemAppear() {
        // Where there is a hand there is no clock. An unwatched town opens one
        // stretch a step, so a player who walks away and comes back finds a town
        // that grew -- not one that was finished the instant it was planned.
        //
        // Measured early, while it is still happening. A settled town catches up
        // with its own plan (266 of 268 open by five hundred steps), so a test
        // that waited would be asserting the opposite of what it means to.
        //
        // Twenty-five steps rather than fifty. Buildings are bigger and so cost
        // more work, so a town of the same age has raised fewer of them and
        // planned fewer streets — and the opener, which lays one stretch a step
        // whatever else is happening, catches up with a shorter plan sooner.
        // Measured: 20 of 40 open at step 25, 43 of 45 at step 50, level from
        // step 75 on. Fifty is no longer early; it is the moment the two curves
        // cross, which is a coin toss rather than a test.
        Settlement town = town(Culture.LAYOUT_HIGH_STREET, new TerrainFake(11), 25);
        int opened = town.paths().openedCount();
        int total = town.paths().segments().size();
        assertTrue(total > 0, "the town planned nothing to open");
        assertTrue(opened > 0, "nothing was ever opened");
        assertTrue(opened < total,
                "every one of " + total + " stretches was open at once, which is"
                        + " a town that appeared rather than one that was built");
    }

    @Test
    void streetsGrowWithTheTownRatherThanArrivingWhole() {
        // The plan describes a town of two hundred and fifty-six and a village
        // has sixty. Laying all of it gave a settlement of sixty-two buildings
        // 405 stretches of carriageway and thirty thousand paved columns -- a
        // market town grid around a hamlet, most of it running past nothing.
        // Streets are laid where the town has reached, so they arrive with it.
        TerrainFake ground = new TerrainFake(11);
        Settlement town = town(Culture.LAYOUT_HIGH_STREET, ground, 50);
        int young = streetsOf(town);
        for (int step = 51; step <= 500; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }
        int grown = streetsOf(town);
        assertTrue(young > 0, "a young town laid no streets to build along");
        assertTrue(grown > young,
                "the town grew from " + young + " to " + grown
                        + " stretches of street, which is not growth");
        assertTrue(young < grown / 2,
                "a village of nine buildings already had " + young
                        + " of the " + grown + " stretches a town of sixty needs");
    }

    private static int streetsOf(Settlement town) {
        int wide = 0;
        for (PathNetwork.Segment segment : town.paths().segments()) {
            if (segment.width() > PathNetwork.TRACK_WIDTH) {
                wide++;
            }
        }
        return wide;
    }

    @Test
    void buildingsStandCloseToTheStreetsTheyFront() {
        // The measure that says the streets are real rather than decorative: if
        // the town builds on its plan, its buildings are a doorstep from a road.
        // Before streets were planned this number was whatever the join paths
        // happened to make it.
        TerrainFake ground = new TerrainFake(11);
        Settlement town = town(Culture.LAYOUT_HIGH_STREET, ground, 500);
        PathNetwork paths = town.paths();
        int counted = 0;
        double worst = 0;
        double total = 0;
        for (Building building : town.buildings()) {
            if (!BuildPlanner.holdsGround(building.blueprintId())) {
                continue;
            }
            double away = paths.distanceToRoad(building.origin());
            if (away < 0) {
                continue;
            }
            counted++;
            total += away;
            worst = Math.max(worst, away);
        }
        assertTrue(counted >= 8, "only " + counted + " buildings to measure");
        assertTrue(total / counted < 20,
                "the average building stands " + Math.round(total / counted)
                        + " blocks from any road");
    }

    // --- the geometry a bending street needs of a run ---

    @Test
    void aRunGivesItsOldAnswersWhenItIsAxisAligned() {
        // The generalization to diagonals is only safe if nothing that existed
        // changed, and every road in every existing world is axis-aligned.
        PathNetwork.Segment run = new PathNetwork.Segment(
                new SimPos(0, 64, 0), new SimPos(40, 64, 0));
        assertEquals(41, run.length());
        assertEquals(41, run.positions().size());
        assertEquals(new SimPos(0, 64, 0), run.positions().get(0));
        assertEquals(new SimPos(40, 64, 0), run.positions().get(40));
        assertEquals(new SimPos(12, 64, 0), run.nearestTo(new SimPos(12, 64, 90)));
        assertEquals(new SimPos(40, 64, 0), run.nearestTo(new SimPos(500, 64, 0)));
        assertEquals(PathNetwork.TRACK_WIDTH, run.width());
    }

    @Test
    void aDiagonalRunWalksTheWholeWayRatherThanStoppingShort() {
        // Stepping by the sign of each axis -- which is what the axis-aligned
        // version did -- sends a run that is eight east and three south off at
        // forty-five degrees and leaves it five blocks short of its own end.
        PathNetwork.Segment run = new PathNetwork.Segment(
                new SimPos(0, 64, 0), new SimPos(8, 64, 3), 8);
        List<SimPos> walk = run.positions();
        assertEquals(9, run.length());
        assertEquals(new SimPos(0, 64, 0), walk.get(0));
        assertEquals(new SimPos(8, 64, 3), walk.get(walk.size() - 1),
                "the walk did not reach the end of its own run");
        for (int i = 1; i < walk.size(); i++) {
            int step = Math.max(Math.abs(walk.get(i).x() - walk.get(i - 1).x()),
                                Math.abs(walk.get(i).z() - walk.get(i - 1).z()));
            assertEquals(1, step, "the walk jumped a gap at step " + i);
        }
        assertFalse(run.isAxisAligned());
    }
}
