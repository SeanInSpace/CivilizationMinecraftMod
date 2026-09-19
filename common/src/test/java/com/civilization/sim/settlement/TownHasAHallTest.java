package com.civilization.sim.settlement;

import com.civilization.sim.RecordedTerrain;
import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A town that says it is a town has a hall, or is building one first.
 *
 * <p>The report is one line of {@code /civ info}: Millbrook, the spawn town of
 * seed 8675309, reading <em>TOWN</em> with no {@code civilization:town_hall} in
 * its building list. FOUNDING.md reads a stage off a census — "a hall means
 * TOWN; count backward from there" — and PLAYING.md says the hall is the last
 * thing built. A town with the title and no hall is the settlement saying
 * something untrue about itself.
 *
 * <p>Both halves were working as written, which is why it lasted. The hall is
 * the TOWN program's headline build, so the stage is what <em>lets</em> it be
 * ordered and every TOWN is briefly a TOWN without one. What made "briefly" into
 * "indefinitely" is where the program was asked: after the build queue had been
 * found empty. A growing town's queue is not empty, and a village the world
 * wrote down is a TOWN on its very first step — so Millbrook held the title
 * with its hall neither standing nor ordered, behind a farm, for as long as it
 * had anything else to do.
 *
 * <p>So the seat is ordered as soon as the stage is held and the town has
 * streets to set it back from, and it goes to the front of the queue rather
 * than the back of it. These pin the invariant rather than the mechanism:
 * whenever a settlement reads TOWN, its seat stands or is work in hand.
 */
class TownHasAHallTest {

    /** Where every survey in this project puts its town. */
    private static final SimPos CENTER = new SimPos(16, 64, 80);

    private static final String HALL = "civilization:town_hall";

    private static RecordedTerrain ground;

    private static synchronized RecordedTerrain ground() {
        if (ground == null) {
            ground = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        }
        return ground;
    }

    /** A village the world wrote down, exactly as {@code WorldgenSettlements} does. */
    private static Settlement worldgenVillage() {
        SimPos site = new SimPos(CENTER.x(), ground().surfaceHeight(CENTER), CENTER.z());
        return Founding.seeded(site, "Millbrook", SettlementStage.VILLAGE,
                BuildCatalog.DEFAULT, Culture.NORMAN.id());
    }

    /**
     * Whether the town's seat stands or is work in hand.
     *
     * <p>In hand rather than at the head, and the difference is one rule:
     * {@code planFarmAhead} is allowed to put a field in front of whatever is
     * building, because a town that stops to finish its hall while its people go
     * hungry is a town that starves for a monument. Everything else queues
     * behind the seat. What this refuses is the thing that was reported — a
     * settlement wearing the title with the hall nowhere at all.
     */
    private static void holdsOrIsRaisingItsSeat(Settlement town, String seat, String when) {
        if (town.countBuildings(seat) > 0) {
            return;
        }
        for (BuildTask ordered : town.buildQueue()) {
            if (BuildPlanner.baseIdOf(ordered.blueprintId()).equals(seat)) {
                return;
            }
        }
        fail("a " + town.stage() + " with no " + seat + " standing and none"
                + " ordered either, " + when + " (queue: "
                + town.buildQueue().stream().map(BuildTask::blueprintId).toList() + ")");
    }

    /**
     * And that it goes to the front of the queue rather than the back of it.
     *
     * <p>Front, not head: a town does not knock off the job in hand to start its
     * hall, so the seat is ordered next after whatever was already being built.
     * What it is never behind is anything ordered <em>after</em> it, which is
     * what "the queue drained first" used to mean in practice — a hall waiting
     * behind a row of cottages the catalog wanted while it waited.
     */
    private static void isAtTheFrontOfTheQueue(Settlement town, String seat, String when) {
        int at = -1;
        for (int i = 0; i < town.buildQueue().size(); i++) {
            if (BuildPlanner.baseIdOf(town.buildQueue().get(i).blueprintId()).equals(seat)) {
                at = i;
                break;
            }
        }
        assertTrue(at >= 0 && at <= 1,
                "a " + town.stage() + " has its " + seat + " at place " + at
                        + " of its queue " + when + " (queue: "
                        + town.buildQueue().stream().map(BuildTask::blueprintId).toList()
                        + ")");
    }

    /**
     * The fault, on the town it was reported on.
     *
     * <p>The spawn town is seeded at VILLAGE and is a TOWN one step later, which
     * is what {@code StagePlanner.readyToAdvance} says a finished village is.
     * It orders its hall on the step after that, and not before: its whole road
     * network is walked out in one pass on its first step, and a hall sited
     * before there is a street to set it back from is a hall standing in the way
     * of the streets. Two steps, therefore — and it used to be thirteen, with
     * the hall waiting for the town's ordinary wants to run dry.
     */
    @Test
    void thevillageTheWorldWroteDownIsATownThatIsRaisingItsHall() {
        Settlement town = worldgenVillage();
        assertEquals(0, town.countBuildings(HALL),
                "a seeded village already has a hall, so this measures nothing");

        town.step(new SimContext(ground(), 1, SimSettings.SANDBOX));
        assertEquals(SettlementStage.TOWN, town.stage(),
                "a finished village stopped graduating, so the fault has moved");
        town.step(new SimContext(ground(), 2, SimSettings.SANDBOX));

        holdsOrIsRaisingItsSeat(town, HALL, "on its second step as a town");
        isAtTheFrontOfTheQueue(town, HALL, "on its second step as a town");
    }

    /** And it goes on being true, every step, until the hall actually stands. */
    @Test
    void andGoesOnBeingTrueUntilTheHallStands() {
        Settlement town = worldgenVillage();
        for (int step = 1; step <= 60; step++) {
            town.step(new SimContext(ground(), step, SimSettings.SANDBOX));
            if (step > 1 && town.stage() == SettlementStage.TOWN) {
                holdsOrIsRaisingItsSeat(town, HALL, "on step " + step);
            }
        }
    }

    /**
     * A town that climbed the ladder honestly, which is the other way in.
     *
     * <p>Grown from a camp on the recorded hillside rather than seeded, so the
     * rule is held against a settlement that earned every rung. It reaches TOWN
     * around two hundred steps in with a queue of its own wants already running,
     * and the hall has to go to the front of it.
     */
    @Test
    void andATownThatClimbedTheLadderPutsItsHallBeforeItsHouses() {
        Settlement town = new Settlement(Settlement.Id.random(), "Grown", CENTER, 512);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        town.setCultureId(Culture.NORMAN.id());
        town.setLayoutId("ring_streets");
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(
                    Person.Id.random(), name, Profession.PIONEER, CENTER));
        }

        boolean everATown = false;
        for (int step = 1; step <= 400; step++) {
            town.stores().add(TownStores.WOOD, 8);
            town.stores().add(TownStores.STONE, 6);
            town.step(new SimContext(ground(), step, SimSettings.SANDBOX));
            if (town.stage() != SettlementStage.TOWN) {
                continue;
            }
            if (!everATown) {
                everATown = true;
                // The very step the charter lands: the hall goes to the front of
                // a queue that has a town's worth of ordinary wants already in it.
                isAtTheFrontOfTheQueue(town, HALL, "on the step it was chartered, " + step);
            }
            holdsOrIsRaisingItsSeat(town, HALL, "on step " + step);
        }
        assertTrue(everATown,
                "this ground never grew a town at all, so nothing was measured");
        assertTrue(town.countBuildings(HALL) > 0,
                "a town four hundred steps old with materials to hand never"
                        + " finished its hall");
    }
}
