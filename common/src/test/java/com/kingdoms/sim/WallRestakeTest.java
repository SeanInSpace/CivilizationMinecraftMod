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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town walls itself once, at its charter, and lives inside that line.
 *
 * <p>This file used to hold the opposite claim. A wall that followed its town
 * was re-staked four to seven times in fourteen hundred steps on every
 * arrangement measured, which is a settlement permanently rebuilding its own
 * outline — and an outline is not what a wall is for. Real towns walled
 * themselves at their charter and stayed inside that circuit for generations:
 * growth went <em>outside</em> the wall as suburbs, unwalled, and a second
 * circuit was raised only when the suburbs had come to rival the walled town
 * itself. Paris managed three in four hundred and fifty years; London, holding
 * a Roman wall, never built another.
 *
 * <p>So the claims here are the three rules that reproduce that: nothing is
 * staked before TOWN, a town stakes once, and the line moves only when the
 * suburbs outnumber the quarter inside it, the wall is paid for to the last
 * post, and a generation has passed since it was last staked.
 */
class WallRestakeTest {

    private static final SimPos CENTRE = new SimPos(0, 72, 0);

    private static SimContext at(long step) {
        return new SimContext(new TerrainFake(11), step, SimSettings.SANDBOX);
    }

    // --- the constructed town, for the rules themselves --------------------

    /**
     * A walled town with a named number of buildings inside the line and beyond
     * it.
     *
     * <p>Built rather than grown, because every rule below is a question about
     * a <em>ratio</em> and a grown town hands you whatever ratio it happens to
     * reach. The wall is a plain box, the buildings are houses, and the only
     * thing that varies between the tests is how many of them stand where.
     *
     * @param complete whether the wall has been paid for to its last post
     */
    private static Settlement walled(int inside, int outside, boolean complete) {
        Settlement town = new Settlement(Settlement.Id.random(), "Faubourg", CENTRE, 512);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setStage(SettlementStage.TOWN);
        Perimeter ring = new Perimeter(box(-60, 60), List.of(), 0);
        ring.setLaid(complete ? ring.length() : ring.length() - 1);
        town.setPerimeter(ring);
        for (int i = 0; i < inside; i++) {
            town.addBuilding(new Building("kingdoms:house",
                    new SimPos(-40 + 20 * (i % 5), 72, -40 + 20 * (i / 5)), 1, true));
        }
        for (int i = 0; i < outside; i++) {
            town.addBuilding(new Building("kingdoms:house",
                    new SimPos(-100 + 40 * (i % 5), 72, 100 + 40 * (i / 5)), 1, true));
        }
        return town;
    }

    @Test
    void aWallIsNotMovedForSuburbsSmallerThanTheTown() {
        // Four of ten outside — forty per cent of the settlement standing past
        // the gate, which on the old rule was four re-stakings' worth of reason
        // to move the line. It is not one here. A town with a faubourg is the
        // ordinary state of a medieval town, not a fault to be corrected, and
        // the ground outside the wall is exactly where a town was supposed to
        // put its growth.
        Settlement town = walled(6, 4, true);
        Perimeter standing = town.perimeter();

        PerimeterPlanner.advance(town, at(2000));

        assertSame(standing, town.perimeter(),
                "the wall was moved for a suburb of four against a town of six");
    }

    @Test
    void aWallIsMovedWhenTheSuburbsOutnumberTheTown() {
        // The historical trigger, and the only one: the suburb has become the
        // town and the old circuit is now the old quarter.
        Settlement town = walled(4, 6, true);
        Perimeter standing = town.perimeter();

        PerimeterPlanner.advance(town, at(2000));

        Perimeter moved = town.perimeter();
        assertNotSame(standing, moved,
                "six of ten standing outside the line and the wall did not move");
        assertTrue(moved.length() > standing.length(),
                "the new circuit is not bigger than the one it replaced: "
                        + moved.length() + " posts for " + standing.length());
        for (Building building : town.buildings()) {
            assertTrue(whollyInside(building, moved.vertices()),
                    "the new circuit still leaves the " + building.blueprintId()
                            + " at " + building.origin() + " outside it");
        }
    }

    @Test
    void anUnfinishedWallIsNeverReplaced() {
        // One post short of closed, and the whole town standing outside would
        // not move it. A settlement that abandons a line it has not finished
        // paying for has bought nothing twice: the posts already in the ground
        // travel to the longer line, so the longer line starts further from
        // being finished than the one it replaced. That is how a town ends up
        // with no wall at all rather than a small one.
        Settlement town = walled(2, 12, false);
        Perimeter standing = town.perimeter();

        PerimeterPlanner.advance(town, at(2000));

        assertSame(standing, town.perimeter(),
                "an unfinished wall was abandoned for a longer one it can afford less");

        standing.setLaid(standing.length());
        PerimeterPlanner.advance(town, at(2000));
        assertNotSame(standing, town.perimeter(),
                "and the last post is the only thing that was holding it");
    }

    @Test
    void aWallIsNotMovedTwiceInOneGeneration() {
        Settlement town = walled(4, 6, true);
        PerimeterPlanner.advance(town, at(500));
        Perimeter moved = town.perimeter();
        assertNotSame(null, moved);

        // Paid for, and the suburbs run away again — twelve houses out on the
        // far side, against the ten the new line holds.
        moved.setLaid(moved.length());
        for (int i = 0; i < 12; i++) {
            town.addBuilding(new Building("kingdoms:house",
                    new SimPos(-220 + 40 * (i % 6), 72, 300 + 40 * (i / 6)), 400, true));
        }

        PerimeterPlanner.advance(town, at(999));
        assertSame(moved, town.perimeter(),
                "the wall moved twice inside five hundred steps — the whole founding"
                        + " ladder is shorter than that");

        PerimeterPlanner.advance(town, at(1000));
        assertNotSame(moved, town.perimeter(),
                "and a generation later it is allowed to move again");
    }

    @Test
    void aWallLoadedIntoASessionWhoseClockRestartedIsNotFrozenForEver() {
        // SimWorld does not save its step counter — every session begins at
        // step zero — while the step the wall was staked on comes back out of
        // the save file. A town walled at step nine hundred therefore reloads
        // holding a line that was staked nine hundred steps from now, and a
        // plain subtraction would refuse to move it until this session had run
        // longer than the last one did. On a world reloaded more often than
        // that, which is every world anybody plays, the rule would simply stop
        // working and nothing would say so. The cooldown runs from the reload.
        Settlement town = walled(4, 6, true);
        Perimeter reloaded = new Perimeter(town.perimeter().vertices(), List.of(),
                town.perimeter().length(), List.of(), 900L);
        town.setPerimeter(reloaded);

        PerimeterPlanner.advance(town, at(499));
        assertSame(reloaded, town.perimeter(),
                "the wall moved before the reloaded session had served the cooldown");

        PerimeterPlanner.advance(town, at(500));
        assertNotSame(reloaded, town.perimeter(),
                "a wall out of a save whose clock has restarted can never move again");
    }

    @Test
    void theSupersededLineIsRetiredForDemolitionRatherThanKept() {
        // A town has one wall. The old ring is handed on to whatever draws the
        // world so its posts come down, and an inner fence line through the
        // middle of a town is the partition fault the concave hull was written
        // to remove -- no better for having been a wall once.
        Settlement town = walled(4, 6, true);
        Perimeter standing = town.perimeter();

        PerimeterPlanner.advance(town, at(2000));

        assertTrue(town.perimeter().retired().contains(
                        new Perimeter.Retired(standing.vertices(), standing.laid())),
                "the superseded line was dropped rather than retired");
        assertEquals(1, town.perimeter().retired().size());
        assertEquals(standing.laid(), town.perimeter().laid(),
                "the posts already paid for did not travel to the new line");
    }

    // --- the grown town, for the shape of what is actually staked ----------

    /** One staking: the line the town was given, and the town at the time. */
    private record Staking(long step, Perimeter line, List<Building> standing) {
    }

    /**
     * The one grown town every assertion below reads.
     *
     * <p>Seven hundred steps of a real settlement is the expensive part of this
     * file by two orders of magnitude, and the questions below should not grow
     * a town each to ask it. The run is deterministic, so one is as good as
     * five.
     */
    private static List<Staking> cached;
    private static long reachedTown;
    private static int spilledAtTheEnd;

    private static List<Staking> stakings() {
        if (cached == null) {
            cached = grow(700);
        }
        return cached;
    }

    /** A town grown on the rough seed, with every staking of its wall kept. */
    private static List<Staking> grow(int steps) {
        TerrainFake ground = new TerrainFake(11);
        Settlement town = new Settlement(Settlement.Id.random(), "Survey", CENTRE, 512);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        town.setCultureId("kingdoms:burgher");
        town.setLayoutId(Culture.of("kingdoms:burgher").layouts().get(0));
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(Person.Id.random(), name, Profession.PIONEER, CENTRE));
        }
        List<Staking> staked = new ArrayList<>();
        Perimeter last = null;
        reachedTown = -1;
        for (int step = 1; step <= steps; step++) {
            // Taken before the step, because the wall is staked partway through
            // one and the build queue is emptied after it. A building that
            // finished on the same step the line was staked was not standing
            // when it was staked, and holding the wall to ground nobody had
            // built on yet would be asking it to see the future.
            List<Building> stood = List.copyOf(town.buildings());
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
            if (reachedTown < 0 && town.stage() == SettlementStage.TOWN) {
                reachedTown = step;
            }
            Perimeter now = town.perimeter();
            // Identity, not equality: the planner replaces the whole object
            // when it stakes and mutates it in place for everything else, so a
            // new object is exactly a new line.
            if (now != null && now != last) {
                staked.add(new Staking(step, now, stood));
            }
            last = now;
        }
        spilledAtTheEnd = 0;
        for (Building building : town.buildings()) {
            if (BuildPlanner.holdsGround(building.blueprintId())
                    && !whollyInside(building, town.perimeter().vertices())) {
                spilledAtTheEnd++;
            }
        }
        return staked;
    }

    @Test
    void aTownStakesItsWallOnceAndThenLivesInsideIt() {
        List<Staking> staked = stakings();

        assertEquals(1, staked.size(),
                "a town that stakes its wall " + staked.size() + " times in seven"
                        + " hundred steps is drawing its own outline, not defending"
                        + " itself");
        assertTrue(staked.getFirst().step() >= reachedTown,
                "the wall was staked at step " + staked.getFirst().step()
                        + ", before the settlement was chartered a town at "
                        + reachedTown);
        // And the town goes on growing past it, which is the point of the rule
        // rather than a failure of it: what a town builds after its charter is
        // a suburb, and suburbs are outside the gate. Measured at 56 and then
        // 62 of the same 86 ground-holding buildings on two runs of this
        // fixture -- RaidPlanner draws its timing from the settlement's own
        // random id, so the tail of a grown town moves a few buildings between
        // runs and only the sign of this is worth asserting.
        assertTrue(spilledAtTheEnd > 0,
                "nothing at all stands outside the wall after seven hundred steps,"
                        + " so this fixture no longer exercises the suburb rule");
    }

    @Test
    void theRingHoldsEveryPlotStandingWhenItWasStaked() {
        // Asserted at the moment of the staking, on the plot rather than the
        // origin -- a line that clips the back of a farm has not enclosed it.
        for (Staking staked : stakings()) {
            for (Building building : staked.standing()) {
                if (!BuildPlanner.holdsGround(building.blueprintId())) {
                    continue;
                }
                assertTrue(whollyInside(building, staked.line().vertices()),
                        "the wall staked at step " + staked.step()
                                + " leaves the " + building.blueprintId()
                                + " at " + building.origin() + " outside it");
            }
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
        for (Staking staked : stakings()) {
            List<Hull.Keepout> plots = new ArrayList<>();
            for (Building building : staked.standing()) {
                if (!BuildPlanner.holdsGround(building.blueprintId())) {
                    continue;
                }
                plots.add(new Hull.Keepout(building.origin().x(), building.origin().z(),
                        BuildPlanner.plotSpanOf(building.blueprintId(),
                                BuildCatalogue.DEFAULT) / 2.0));
            }
            List<SimPos> loop = staked.line().vertices();
            for (int i = 0; i < loop.size(); i++) {
                SimPos from = loop.get(i);
                SimPos to = loop.get((i + 1) % loop.size());
                assertFalse(Hull.crossesKeepout(from, to, plots),
                        "the wall staked at step " + staked.step()
                                + " runs from " + from + " to " + to
                                + " through somebody's floor");
            }
        }
    }

    @Test
    void everyGateOfARingIsAPostOnIt() {
        // A gate is a hole in a wall, so it has to be somewhere the wall runs.
        for (Staking staked : stakings()) {
            Set<SimPos> onRing = new HashSet<>(staked.line().ringPositions());
            assertFalse(staked.line().gates().isEmpty(),
                    "the ring staked at step " + staked.step() + " has no way in");
            for (SimPos gate : staked.line().gates()) {
                assertTrue(onRing.contains(gate), "the gate at " + gate
                        + " on the ring staked at step " + staked.step()
                        + " is not a post on that ring");
            }
        }
    }

    // --- the stage gate ----------------------------------------------------

    @Test
    void nothingBelowATownStakesAWall() {
        // Every stage below TOWN, each handed the whole founding ladder's
        // buildings so that nothing but the stage itself can be what is holding
        // the wall back. A frontier post has a watch, not a circuit of walls:
        // FORTIFIED means somebody standing guard, and walling a settlement is
        // a chartered town's business.
        for (SettlementStage stage : SettlementStage.values()) {
            if (!stage.before(SettlementStage.TOWN)) {
                continue;
            }
            Settlement young = ladder(stage);
            for (int step = 1; step <= 40; step++) {
                PerimeterPlanner.advance(young, at(step));
            }
            assertNull(young.perimeter(),
                    "a settlement at " + stage + " staked itself a wall");
        }

        Settlement chartered = ladder(SettlementStage.TOWN);
        PerimeterPlanner.advance(chartered, at(1));
        assertTrue(chartered.perimeter() != null,
                "a chartered town with its hall standing stakes its wall");
    }

    @Test
    void aWallAlreadyStandingIsKeptHoweverYoungTheSettlementIs() {
        // Saves from before this rule have walls, and so does any settlement
        // knocked back down the ladder. A change of mind about when a wall
        // should have been built is not a reason to pull one down.
        Settlement young = ladder(SettlementStage.FORTIFIED);
        Perimeter standing = new Perimeter(box(-60, 60), List.of(), 20);
        young.setPerimeter(standing);

        for (int step = 1; step <= 40; step++) {
            PerimeterPlanner.advance(young, at(step));
        }

        assertSame(standing, young.perimeter(),
                "a settlement below TOWN lost the wall it had already paid for");
    }

    /** A settlement at this stage with every ladder building standing. */
    private static Settlement ladder(SettlementStage stage) {
        Settlement town = new Settlement(Settlement.Id.random(), "Ladder", CENTRE, 512);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setStage(stage);
        String[] program = {"kingdoms:camp_post", "kingdoms:cache", "kingdoms:bunkhouse",
                "kingdoms:hearth", "kingdoms:farm", "kingdoms:granary",
                "kingdoms:lumber_camp", "kingdoms:storehouse", "kingdoms:cottage",
                "kingdoms:cottage", "kingdoms:mill", "kingdoms:carpentry",
                "kingdoms:market", "kingdoms:inn", "kingdoms:town_hall"};
        for (int i = 0; i < program.length; i++) {
            town.addBuilding(new Building(program[i],
                    new SimPos(-32 + 16 * (i % 5), 72, -32 + 16 * (i / 5)), 1, true));
        }
        return town;
    }

    // --- the demolition ----------------------------------------------------

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

    private static List<SimPos> box(int from, int to) {
        return List.of(new SimPos(from, 72, from), new SimPos(to, 72, from),
                new SimPos(to, 72, to), new SimPos(from, 72, to));
    }
}
