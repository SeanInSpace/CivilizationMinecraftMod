package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.geom.Hull;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.PerimeterPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town that outgrows its wall moves it.
 *
 * <p>{@code chooseSite} has always preferred ground inside the ring and built
 * beyond it when nothing inside would do, with a comment saying that was the
 * wall's cue to re-stake. Nothing re-staked. {@code PerimeterPlanner.advance}
 * staked once and never again, so a settlement that grew past its palisade
 * stayed past it for the rest of the world's life — measured on the rough seed
 * at seven hundred steps, <strong>58 of 85 buildings stood outside a ring that
 * had closed at 648 posts</strong>. That is not a walled town with some
 * outbuildings; it is a fenced-off old quarter with a town around it.
 */
class WallRestakeTest {

    private static final SimPos CENTRE = new SimPos(0, 72, 0);

    /** One re-staking: the line before, the line after, and the town at the time. */
    private record Restake(long step, Perimeter before, Perimeter after,
                           List<Building> standing) {
    }

    /**
     * The one grown town every assertion here reads.
     *
     * <p>Seven hundred steps of a real settlement is the expensive part of this
     * file by two orders of magnitude, and six tests asking six different
     * questions of the same run should not grow six towns to do it. The run is
     * deterministic, so one is exactly as good as six.
     */
    private static List<Restake> cached;

    private static List<Restake> restakes() {
        if (cached == null) {
            cached = restakesOf(700);
        }
        return cached;
    }

    /**
     * A town grown on the rough seed, with every re-staking of its wall kept.
     *
     * <p>Kept rather than measured at the end because a wall is only ever
     * <em>correct</em> at the moment it is staked: the town goes on building
     * afterwards, and the next building beyond the line is the next re-stake's
     * business, not this one's failure.
     */
    private static List<Restake> restakesOf(int steps) {
        TerrainFake ground = new TerrainFake(11);
        Settlement town = new Settlement(Settlement.Id.random(), "Survey", CENTRE, 512);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        town.setCultureId("kingdoms:burgher");
        town.setLayoutId(Culture.of("kingdoms:burgher").layouts().get(0));
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(Person.Id.random(), name, Profession.PIONEER, CENTRE));
        }
        List<Restake> restakes = new ArrayList<>();
        Perimeter last = null;
        for (int step = 1; step <= steps; step++) {
            // Taken before the step, because the wall is staked partway through
            // one and the build queue is emptied after it. A building that
            // finished on the same step the line moved was not standing when it
            // moved, and holding the wall to ground nobody had built on yet
            // would be asking it to see the future.
            List<Building> stood = List.copyOf(town.buildings());
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
            Perimeter now = town.perimeter();
            // Identity, not equality: the planner replaces the whole object when
            // it re-stakes and mutates it in place for everything else, so a new
            // object is exactly a new line.
            if (now != null && last != null && now != last) {
                restakes.add(new Restake(step, last, now, stood));
            }
            last = now;
        }
        return restakes;
    }

    /** Whether every corner of this building's reserved plot is inside the line. */
    private static boolean whollyInside(Building building, List<SimPos> loop) {
        int half = BuildPlanner.plotSpanOf(
                building.blueprintId(), BuildCatalogue.DEFAULT) / 2;
        SimPos at = building.origin();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                if (!Hull.contains(loop, new SimPos(
                        at.x() + sx * half, at.y(), at.z() + sz * half))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Test
    void aTownThatBuildsBeyondItsWallGetsAWiderOne() {
        List<Restake> restakes = restakes();

        assertFalse(restakes.isEmpty(),
                "seven hundred steps of growth and the wall never moved once");
        for (Restake restake : restakes) {
            assertTrue(restake.after().length() > restake.before().length(),
                    "the wall was re-staked shorter at step " + restake.step()
                            + ": " + restake.after().length() + " posts for "
                            + restake.before().length());
        }
    }

    @Test
    void theNewRingHoldsEveryPlotTheTownHasTaken() {
        // The whole point of moving the wall. Asserted at the moment of each
        // staking, on the plot rather than the origin -- a line that clips the
        // back of a farm has not enclosed the farm.
        for (Restake restake : restakes()) {
            for (Building building : restake.standing()) {
                if (!BuildPlanner.holdsGround(building.blueprintId())) {
                    continue;
                }
                assertTrue(whollyInside(building, restake.after().vertices()),
                        "the wall re-staked at step " + restake.step()
                                + " still leaves the " + building.blueprintId()
                                + " at " + building.origin() + " outside it");
            }
        }
    }

    @Test
    void theSupersededLineIsRetiredForDemolitionRatherThanKept() {
        // A town has one wall. The old ring is handed on to whatever draws the
        // world so its posts come down, and an inner fence line through the
        // middle of a town is the partition fault the concave hull was written
        // to remove -- no better for having been a wall once.
        for (Restake restake : restakes()) {
            assertTrue(restake.after().retired().contains(new Perimeter.Retired(
                            restake.before().vertices(), restake.before().laid())),
                    "the line superseded at step " + restake.step()
                            + " was dropped rather than retired");
            assertEquals(restake.before().retired().size() + 1,
                    restake.after().retired().size(),
                    "a re-stake lost track of a line retired earlier");
        }
    }

    @Test
    void noStretchOfAStakedRingIsDrawnThroughAStandingBuilding() {
        // Asserted of the line the town is actually given, not of the hull it
        // came from. Hull.concave refuses to dig a leg across a plot, and
        // HullSimplicityTest holds it to that -- but staking then pushes every
        // vertex out by the margin and relaxes the line over the terrain, and a
        // property proved of the input is not a property of the output. This is
        // also what PerimeterLayer.lineIsClosed now leans on when it says
        // `shutByBuilding` has stopped being load-bearing, so it is worth
        // asserting where the claim is actually made.
        for (Restake restake : restakes()) {
            List<Hull.Keepout> plots = new ArrayList<>();
            for (Building building : restake.standing()) {
                if (!BuildPlanner.holdsGround(building.blueprintId())) {
                    continue;
                }
                plots.add(new Hull.Keepout(building.origin().x(), building.origin().z(),
                        BuildPlanner.plotSpanOf(building.blueprintId(),
                                BuildCatalogue.DEFAULT) / 2.0));
            }
            List<SimPos> loop = restake.after().vertices();
            for (int i = 0; i < loop.size(); i++) {
                SimPos from = loop.get(i);
                SimPos to = loop.get((i + 1) % loop.size());
                assertFalse(Hull.crossesKeepout(from, to, plots),
                        "the wall re-staked at step " + restake.step()
                                + " runs from " + from + " to " + to
                                + " through somebody's floor");
            }
        }
    }

    @Test
    void everyGateOfANewRingIsAPostOnIt() {
        // The gates are re-sited by the existing rule when the line moves, and
        // the rule that matters is unchanged: a gate is a hole in a wall, so it
        // has to be somewhere the wall runs.
        for (Restake restake : restakes()) {
            Set<SimPos> onRing = new HashSet<>(restake.after().ringPositions());
            assertFalse(restake.after().gates().isEmpty(),
                    "the ring re-staked at step " + restake.step() + " has no way in");
            for (SimPos gate : restake.after().gates()) {
                assertTrue(onRing.contains(gate), "the gate at " + gate
                        + " on the ring re-staked at step " + restake.step()
                        + " is not a post on that ring");
            }
        }
    }

    @Test
    void aWallIsNotRestakedForEveryShedBuiltBeyondTheGate() {
        // The hysteresis, which is the whole difference between a wall that
        // follows a town and a wall that is permanently under construction. The
        // trigger -- a plot outside the line -- is a latch: it goes true the
        // moment one shed is raised beyond the gate and stays true until
        // something is done about it. Two things bound what that can cost:
        // the question is only asked every hundredth step, and the answer is
        // only acted on when the new line is at least an eighth longer than the
        // one it replaces. Both are asserted here rather than read off the
        // planner's constants, because it is the behaviour that matters.
        List<Restake> restakes = restakes();

        long previous = 0;
        for (Restake restake : restakes) {
            assertTrue(restake.step() - previous >= 100,
                    "the wall was re-staked at step " + restake.step()
                            + ", only " + (restake.step() - previous)
                            + " steps after the last time");
            assertTrue(restake.after().length() >= restake.before().length() * 1.125,
                    "the wall was moved at step " + restake.step()
                            + " for a ring only " + restake.after().length()
                            + " posts against " + restake.before().length());
            previous = restake.step();
        }
        // Three or four on this ground, at the hundred-step reviews from 400
        // or 500 on, taking a 648-post ring to somewhere between 1200 and 1700.
        // A range rather than a number because this fixture is not bit-stable
        // across a whole suite: the terrain fake is trigonometry, and the first
        // town grown in a fresh JVM comes out a shade different from the ones
        // after it -- which shifts the town, not the wall. Everything asserted
        // above holds in both, because none of it is about where the buildings
        // happened to land.
        //
        // A collapse-catcher rather than a measurement, then: seven would mean
        // the band had stopped biting and the town was moving its wall at every
        // review it was offered.
        assertTrue(restakes.size() <= 5,
                restakes.size() + " re-stakings in seven hundred steps is a wall"
                        + " permanently under construction");
    }

    @Test
    void aRefusedRingIsNotStakedAgainUntilSomethingElseSpillsOut() {
        // The trigger is a latch: one shed past the line leaves "this town has
        // outgrown its wall" true for ever, and a town can perfectly well stop
        // growing in that state. Deciding not to move the wall costs a whole
        // candidate staking -- a concave hull over every corner of every plot
        // and four relaxation sweeps, a tenth of a second on a town of
        // eighty-six buildings and a second and a half on one of two hundred.
        // A settlement that reached this state used to pay that every hundredth
        // step until the world ended, in step with every other settlement in
        // the dimension. The refusal is remembered instead.
        Settlement town = new Settlement(Settlement.Id.random(), "Stayput", CENTRE, 256);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setStage(SettlementStage.FORTIFIED);
        town.setPerimeter(new Perimeter(box(-60, 60), List.of(), 0));
        town.addBuilding(new Building("kingdoms:house", new SimPos(64, 72, 0), 1, true));
        Perimeter ring = town.perimeter();

        PerimeterPlanner.advance(town, new SimContext(
                new TerrainFake(11), 100, SimSettings.SANDBOX));

        assertSame(ring, town.perimeter(),
                "a ring was replaced by one no bigger, for one house past the line");
        assertEquals(1, ring.refusedAt(),
                "the refusal was not remembered, so the next review pays for it again");

        PerimeterPlanner.advance(town, new SimContext(
                new TerrainFake(11), 200, SimSettings.SANDBOX));

        assertSame(ring, town.perimeter(), "and the answer did not change");
        assertEquals(1, ring.refusedAt(),
                "nothing new spilled out, so nothing new should have been measured");
    }

    @Test
    void aRetiredLineNeverIncludesGroundTheStandingWallIsOn() {
        // The exclusion is the half that matters. Two rings can share ground,
        // and a demolition that pulls down a post the drawing puts straight
        // back is the treadmill that has halted this wall twice already.
        List<SimPos> old = box(-20, 20);
        List<SimPos> wider = List.of(new SimPos(-20, 72, -20), new SimPos(40, 72, -20),
                new SimPos(40, 72, 20), new SimPos(-20, 72, 20));
        int wholeOldRing = new Perimeter(old, List.of(), 0).length();
        Perimeter ring = new Perimeter(wider, List.of(), 0,
                List.of(new Perimeter.Retired(old, wholeOldRing)));

        Set<SimPos> standing = new HashSet<>(ring.ringPositions());
        List<SimPos> retired = ring.retiredPositions();

        assertFalse(retired.isEmpty(), "the old line was not handed over at all");
        for (SimPos post : retired) {
            assertFalse(standing.contains(post), "the demolition would pull down "
                    + post + ", which the standing wall is built on");
        }
        // The west side is shared between the two, so it must be in one list
        // and not the other.
        assertTrue(standing.contains(new SimPos(-20, 72, 0)));
        assertTrue(retired.contains(new SimPos(20, 72, 0)),
                "the old east side is the stretch that actually has to come down");

        ring.forgetRetired();
        assertTrue(ring.retiredPositions().isEmpty(),
                "a wall pulled down is still being carried");
    }

    private static List<SimPos> box(int from, int to) {
        return List.of(new SimPos(from, 72, from), new SimPos(to, 72, from),
                new SimPos(to, 72, to), new SimPos(from, 72, to));
    }
}
