package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.JobPlanner;
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

        assertEquals(Optional.empty(), JobPlanner.mostNeeded(s));
    }

    @Test
    void retrainingPrefersIdlersOneCallAtATime() {
        Settlement s = settlement();
        add(s, Profession.BUILDER, 8);
        add(s, Profession.IDLER, 2);
        // Population 10: builders in surplus, one guard wanted (priority 80),
        // two farmers wanted (priority 70). Idlers go first.

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

        // A town of ten also wants one lumberjack, below farmers in priority.
        assertTrue(JobPlanner.retrainOne(s));
        assertEquals(1, JobPlanner.count(s, Profession.LUMBERJACK));

        assertFalse(JobPlanner.retrainOne(s), "fully staffed — nothing left to fix");
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
