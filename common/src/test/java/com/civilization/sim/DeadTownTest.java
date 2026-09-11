package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildTask;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.PathNetwork;
import com.civilization.sim.settlement.RoadUpkeep;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town with nobody alive in it does nothing at all.
 *
 * <p>The doctrine everywhere else in this simulation is "where there is a hand
 * there is no clock": the clock does the work an unwatched town's people would
 * have done, so a town you walked away from still grows. The corollary was
 * never written down, and so it was never true — with <em>no</em> people there
 * is nobody the clock is standing in for, and a clock that keeps running is a
 * clock doing work nobody could have done. A plague town went on opening
 * streets, planting wall posts and trading at its inn with every last resident
 * in the ground.
 *
 * <p>So: a dead town is left standing, is not demolished and is not abandoned.
 * It simply stops, and stays stopped until somebody lives there again.
 */
class DeadTownTest {

    private static final SimPos CENTER = new SimPos(0, 72, 0);
    private static final int GROWTH_STEPS = 300;
    private static final int SILENT_STEPS = 500;

    /** Everything about a town that only work can change. */
    private record Ledger(int buildings, int completedWork, int openRoads, int wallPosts,
                          int wood, int stone, int food, int iron, int treasury,
                          int population) {

        static Ledger of(Settlement town) {
            int work = 0;
            for (BuildTask task : town.buildQueue()) {
                work += task.workDone();
            }
            PathNetwork paths = town.paths();
            int open = 0;
            for (int i = 0; i < paths.segments().size(); i++) {
                if (paths.isOpened(i)) {
                    open++;
                }
            }
            int farmFood = 0;
            for (Building building : town.buildings()) {
                farmFood += building.foodStored();
            }
            return new Ledger(town.buildings().size(), work, open,
                    town.perimeter() == null ? 0 : town.perimeter().laid(),
                    town.woodStock(), town.stoneStock(), town.foodStock() + farmFood,
                    town.stores().get(com.civilization.sim.settlement.TownStores.IRON),
                    town.treasury(), town.population());
        }
    }

    private static Settlement grown(TerrainFake ground) {
        Settlement town = new Settlement(Settlement.Id.random(), "Mourn", CENTER, 512);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(
                    Person.Id.random(), name, Profession.PIONEER, CENTER));
        }
        for (int step = 1; step <= GROWTH_STEPS; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }
        return town;
    }

    private static void bury(Settlement town) {
        List<Person.Id> everyone = new ArrayList<>();
        for (Person person : town.residents()) {
            everyone.add(person.id());
        }
        for (Person.Id id : everyone) {
            town.removePerson(id);
        }
        assertEquals(0, town.population(), "the fixture did not actually empty the town");
    }

    /**
     * Every rule at once, reported together.
     *
     * <p>Deliberately not a run of separate assertions: the first draft stopped
     * at whichever one happened to be listed first, which meant one run of the
     * suite named one leaking planner and hid the rest. Finding out that a dead
     * town still trades, still paves and still cuts stone took three edits of
     * the test. Now it takes one run.
     */
    private static void assertNothingHappened(Ledger before, Ledger after) {
        List<String> leaks = new ArrayList<>();
        rose(leaks, "finished a building", before.buildings(), after.buildings());
        rose(leaks, "laid work on its build queue",
                before.completedWork(), after.completedWork());
        rose(leaks, "opened a road", before.openRoads(), after.openRoads());
        rose(leaks, "planted a wall post", before.wallPosts(), after.wallPosts());
        rose(leaks, "brought in timber", before.wood(), after.wood());
        rose(leaks, "cut stone", before.stone(), after.stone());
        rose(leaks, "grew food", before.food(), after.food());
        rose(leaks, "won iron", before.iron(), after.iron());
        rose(leaks, "earned coin", before.treasury(), after.treasury());
        rose(leaks, "gained a resident", before.population(), after.population());
        assertTrue(leaks.isEmpty(),
                "a town with nobody alive " + String.join("; ", leaks));
        assertEquals(0, after.population(), "the dead did not stay dead");
    }

    private static void rose(List<String> leaks, String what, int before, int after) {
        if (after > before) {
            leaks.add(what + " (" + before + " -> " + after + ")");
        }
    }

    @Test
    void aTownWhoseLastResidentDiesStopsDead() {
        TerrainFake ground = new TerrainFake(11);
        Settlement town = grown(ground);
        assertTrue(town.buildings().size() > 1,
                "the fixture never grew a town to kill");

        bury(town);
        Ledger before = Ledger.of(town);
        for (int step = GROWTH_STEPS + 1; step <= GROWTH_STEPS + SILENT_STEPS; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }
        assertNothingHappened(before, Ledger.of(town));
    }

    @Test
    void aTownThatNeverHadAnybodyNeverStarts() {
        TerrainFake ground = new TerrainFake(11);
        Settlement town = new Settlement(Settlement.Id.random(), "Hollow", CENTER, 512);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.CAMP);

        Ledger before = Ledger.of(town);
        for (int step = 1; step <= SILENT_STEPS; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }
        assertNothingHappened(before, Ledger.of(town));
    }

    /**
     * The street the grown fixture cannot show, asked directly.
     *
     * <p>A town stepped three hundred times has opened every stretch it ever
     * planned, so burying it leaves nothing for the paving clock to do and the
     * fixture above is silent about roads. Handed a network with a run still to
     * open and no one to walk it, the clock used to pave it anyway: the test
     * for hands there is "is somebody standing on this job", and nobody at all
     * answers that the same way an unwatched town does.
     */
    @Test
    void aDeadTownDoesNotPaveItsStreets() {
        TerrainFake ground = new TerrainFake(11);
        Settlement town = new Settlement(Settlement.Id.random(), "Silent", CENTER, 512);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        SimPos from = new SimPos(0, ground.groundAt(0, 0), 0);
        SimPos to = new SimPos(0, ground.groundAt(0, 6), 6);
        town.setPaths(new PathNetwork(
                List.of(new PathNetwork.Segment(from, to)), List.of()));

        for (int step = 1; step <= 20; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }
        assertEquals(0, town.paths().unwalkableCount(),
                "the fixture laid its run on ground no road could use");
        assertEquals(0, town.paths().openedCount(),
                "a town with nobody alive opened a road");
    }

    /**
     * The road the dead town already has, and what happens to it after.
     *
     * <p>The half of the doctrine that was still leaking, and it leaked where
     * nobody was looking for it: not in the clock, which had learned to stop,
     * but in the sweep that keeps a laid road clear of the grass. Drawing a road
     * for the first time and patching a broken one are one operation down in the
     * blocks, so a town whose streets had been laid once had them re-laid every
     * pass forever — every resident buried and the gravel still going back down.
     *
     * <p>Both halves are asserted, because a fix that simply stopped a dead town
     * touching roads at all would pass the second and break the first: a village
     * that opened its streets by the clock and then died before anybody arrived
     * to see them must still show them, exactly as it still shows its houses.
     */
    @Test
    void aDeadTownDrawsARoadOnceAndNeverMendsIt() {
        TerrainFake ground = new TerrainFake(11);
        Settlement town = new Settlement(Settlement.Id.random(), "Silent", CENTER, 512);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        SimPos from = new SimPos(0, ground.groundAt(0, 0), 0);
        SimPos to = new SimPos(0, ground.groundAt(0, 6), 6);
        town.setPaths(new PathNetwork(
                List.of(new PathNetwork.Segment(from, to)), List.of()));
        // A street this town walked out while it still had people in it.
        town.paths().markOpened(0);

        assertTrue(RoadUpkeep.mayDraw(town, 0),
                "a road the town opened while alive was never drawn, and now never will be");
        assertFalse(RoadUpkeep.mayMend(town, 0),
                "a town with nobody alive was allowed to mend a road");

        // Drawn: the record goes down, once.
        town.paths().setLaidThrough(1);

        for (int step = 1; step <= SILENT_STEPS; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
            assertFalse(RoadUpkeep.mayDraw(town, 0),
                    "a dead town re-drew a road it had already laid, on step " + step);
            assertFalse(RoadUpkeep.mayMend(town, 0),
                    "a dead town mended its road on step " + step);
        }
        assertEquals(1, town.paths().openedCount(),
                "a town with nobody alive opened another road");
    }

    /** A road once drawn stays drawn, so nothing can walk the mark backwards. */
    @Test
    void theMarkOnADrawnRoadOnlyEverGoesForward() {
        PathNetwork paths = new PathNetwork(
                List.of(new PathNetwork.Segment(new SimPos(0, 64, 0), new SimPos(0, 64, 6)),
                        new PathNetwork.Segment(new SimPos(0, 64, 6), new SimPos(6, 64, 6))),
                List.of());
        paths.setLaidThrough(2);
        paths.setLaidThrough(0);
        assertEquals(2, paths.laidThrough(), "a road already drawn was forgotten");
        assertTrue(paths.isLaid(1), "a drawn stretch stopped counting as drawn");
        paths.setLaidThrough(99);
        assertEquals(2, paths.laidThrough(),
                "the mark ran past the end of the network");
    }

    /** The living half, so the fix cannot be "nobody ever mends anything". */
    @Test
    void aTownWithPeopleStillMendsTheRoadsItHasLaid() {
        Settlement town = new Settlement(Settlement.Id.random(), "Lively", CENTER, 512);
        town.addResident(new Person(
                Person.Id.random(), "Ada", Profession.PIONEER, CENTER));
        town.setPaths(new PathNetwork(
                List.of(new PathNetwork.Segment(new SimPos(0, 64, 0), new SimPos(0, 64, 6))),
                List.of()));
        town.paths().markOpened(0);
        town.paths().setLaidThrough(1);
        assertTrue(RoadUpkeep.mayMend(town, 0),
                "a town with people in it stopped keeping its own road");
        assertFalse(RoadUpkeep.mayMend(town, 1),
                "a stretch nobody has walked out yet was paved anyway");
    }

    /** The one thing an empty town does say, and it says it once. */
    @Test
    void theEmptyingIsAnnouncedOnceAndOnlyOnce() {
        TerrainFake ground = new TerrainFake(11);
        Settlement town = grown(ground);
        bury(town);
        for (int step = GROWTH_STEPS + 1; step <= GROWTH_STEPS + 50; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }
        long said = town.events().stream()
                .filter(event -> event.message().contains("has no one left"))
                .count();
        assertEquals(1, said, "the town should report its emptiness once, not every step");
    }
}
