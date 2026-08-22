package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.JobPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Settlement;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers who a settlement decides should do what. */
class JobPlannerTest {

    private static Settlement settlement() {
        return new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);
    }

    private static void add(Settlement s, Profession profession, int count) {
        for (int i = 0; i < count; i++) {
            s.addResident(new Person(
                    Person.Id.random(), profession + " " + i, profession, new SimPos(0, 64, 0)));
        }
    }

    @Test
    void desiredCountsScaleWithPopulation() {
        JobPlanner.ProfessionNeed guards = new JobPlanner.ProfessionNeed(Profession.GUARD, 0, 8, 80);

        assertEquals(0, guards.desiredCount(7));
        assertEquals(1, guards.desiredCount(8));
        assertEquals(2, guards.desiredCount(16));
    }

    @Test
    void buildersComeFirstWhenEverythingIsShort() {
        Settlement s = settlement();
        add(s, Profession.IDLER, 10);

        assertEquals(Optional.of(Profession.BUILDER), JobPlanner.mostNeeded(s),
                "construction gates everything, so builders take priority");
    }

    @Test
    void fullyStaffedSettlementNeedsNothing() {
        Settlement s = settlement();
        add(s, Profession.BUILDER, 2);  // wants 1 + 5/5 = 2
        add(s, Profession.FARMER, 1);   // wants 5/5 = 1
        add(s, Profession.GUARD, 2);    // wants 5/8 = 0 — surplus is fine

        assertFalse(s.isStarving(), "provisioned, so the staffing table has the last word");
        assertEquals(Optional.empty(), JobPlanner.mostNeeded(s));
    }

    @Test
    void retrainingPrefersIdlersOneCallAtATime() {
        Settlement s = settlement();
        add(s, Profession.BUILDER, 8);
        add(s, Profession.IDLER, 2);
        // Population 10: builders in surplus, one guard wanted (priority 80),
        // two farmers wanted (priority 70). Idlers go first.
        // A founding party's provisions are still in the granary, so nothing
        // here is the crisis lane speaking.
        assertFalse(s.isStarving(), "a provisioned town retrains by the table alone");

        assertTrue(JobPlanner.retrainOne(s));
        assertEquals(1, JobPlanner.count(s, Profession.GUARD), "guard outranks farmer");
        assertEquals(8, JobPlanner.count(s, Profession.BUILDER), "idlers are taken before surplus workers");

        assertTrue(JobPlanner.retrainOne(s));
        assertEquals(1, JobPlanner.count(s, Profession.FARMER));

        // Idlers gone, but the builders (want 3, have 8) carry a surplus of five —
        // one takes up the remaining farmer shortfall.
        assertTrue(JobPlanner.retrainOne(s));
        assertEquals(2, JobPlanner.count(s, Profession.FARMER));
        assertEquals(7, JobPlanner.count(s, Profession.BUILDER));

        assertFalse(JobPlanner.retrainOne(s),
                "fully staffed — and with no lumber camp, no lumberjack is wanted");

        // Build the camp and the need switches on: somewhere to work is what makes
        // the job worth filling, which is why a four-person town does not spend one
        // of its people on it before then.
        s.addBuilding(new Building("kingdoms:lumber_camp", new SimPos(6, 64, 6), 1, true));

        // One always, plus one per ten residents: a town of ten wants two.
        assertTrue(JobPlanner.retrainOne(s));
        assertEquals(1, JobPlanner.count(s, Profession.LUMBERJACK));
        assertTrue(JobPlanner.retrainOne(s));
        assertEquals(2, JobPlanner.count(s, Profession.LUMBERJACK));

        assertFalse(JobPlanner.retrainOne(s), "and now there is nothing left to fix");
    }

    @Test
    void surplusWorkersTakeUpNeededTrades() {
        // The playtest town: all farmers, no idlers, and defenseless. Farmers are
        // wildly over their desired count, so they retrain rather than the town
        // staying guardless forever.
        Settlement s = settlement();
        add(s, Profession.FARMER, 16);

        assertTrue(JobPlanner.retrainOne(s));
        assertEquals(15, JobPlanner.count(s, Profession.FARMER));
        assertEquals(1, JobPlanner.count(s, Profession.BUILDER), "builders are the top shortfall");
    }

    // --- when the town is starving ---

    @Test
    void afoundingPartyPutsSomebodyInTheFieldsRatherThanStarve() {
        // The charter that died: four settlers, two of them idle, and a staffing
        // table that wants no farmers at all below five residents. Nought plus
        // four-fifths is nought, so the shortfall is nought, so a farmer was
        // unreachable at any priority and the idlers stayed idle until everyone
        // was dead.
        Settlement s = settlement();
        add(s, Profession.BUILDER, 2);
        add(s, Profession.IDLER, 2);
        s.setFoodStock(0);

        assertEquals(Optional.empty(), JobPlanner.mostNeeded(s),
                "the table itself still wants nobody — that is the whole problem");
        assertTrue(s.isStarving());

        assertTrue(JobPlanner.retrainOne(s), "and the town staffs its fields anyway");
        assertEquals(1, JobPlanner.count(s, Profession.FARMER));
        assertEquals(1, JobPlanner.count(s, Profession.IDLER), "an idler went, not a builder");
    }

    @Test
    void hungerIsNoBarToTakingUpTheHoe() {
        // Everyone is past the point where they would normally stop working.
        // Somebody still has to farm, because nobody else is coming.
        Settlement s = settlement();
        add(s, Profession.GUARD, 3);
        s.setFoodStock(0);
        s.residents().forEach(p -> p.setHunger(Person.HUNGER_SEVERE));

        assertTrue(JobPlanner.retrainOne(s));
        assertEquals(1, JobPlanner.count(s, Profession.FARMER),
                "the weakness gate is exactly what has to give here");
    }

    @Test
    void theLastBuilderIsLeftToBuildTheFarm() {
        Settlement s = settlement();
        add(s, Profession.BUILDER, 1);
        s.setFoodStock(0);

        assertFalse(JobPlanner.retrainOne(s),
                "taking the only builder would leave nobody to raise the field they need");
        assertEquals(1, JobPlanner.count(s, Profession.BUILDER));
    }

    @Test
    void onceSomebodyFarmsTheCrisisLaneStandsDown() {
        Settlement s = settlement();
        add(s, Profession.FARMER, 1);
        add(s, Profession.BUILDER, 3);
        s.setFoodStock(0);

        assertFalse(JobPlanner.retrainOne(s),
                "one field hand is the whole ask; the table decides the rest");
        assertEquals(1, JobPlanner.count(s, Profession.FARMER));
    }

    @Test
    void staffedProfessionsAreNeverDrained() {
        Settlement s = settlement();
        add(s, Profession.BUILDER, 4);   // pop 16: wants exactly 1 + 16/5 = 4
        add(s, Profession.FARMER, 12);   // wants 3 — surplus of 9

        assertTrue(JobPlanner.retrainOne(s));

        assertEquals(4, JobPlanner.count(s, Profession.BUILDER),
                "a profession at its desired count must never be the donor");
        assertEquals(11, JobPlanner.count(s, Profession.FARMER), "the surplus farmer retrains instead");
        assertEquals(1, JobPlanner.count(s, Profession.GUARD));
    }
}
