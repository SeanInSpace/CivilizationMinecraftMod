package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.work.Furnishings;
import com.civilization.sim.work.LightPlanner;
import com.civilization.sim.work.PublicWorks;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town raised by {@code /civ seed town 40} lights its streets.
 *
 * <p><strong>The fault.</strong> The playtest typed {@code /civ seed town 40},
 * stood in the result for twenty minutes and read {@code lamps 0 of 131} the
 * whole time. Not a lamp, ever, in a town of forty people with a working
 * treasury — and the reason line said timber, which read like a poor town rather
 * than like a rule.
 *
 * <p><strong>What it was.</strong> A seeded town is a stage program's worth of
 * buildings with a stage's worth of people poured into them, so it starts with a
 * housing backlog it never clears: there is always a house at the head of its
 * build queue and a house is a hundred and twenty planks.
 * {@code PublicWorks.timberOwedToTheQueue} reserved the whole of that remaining
 * cost against everything below construction, and a lamp costs two. The town's
 * timber came in a few planks at a step and the house ate each one as it
 * arrived, so the stores never once reached the reserve — {@code timber 36 under
 * 82 (80 held for the build queue: civilization:house)}, unchanged at four
 * hundred steps and unchanged for ever. The reserve was right; reserving a
 * season of a job's future spending against two planks the town was holding
 * today was not. See {@code PublicWorks.MOST_OF_THE_STORES}.
 *
 * <p><strong>Why this is the honest reproduction.</strong> The settlement is
 * built through {@link Founding#seeded} with the same stage, catalog, culture
 * and count the command passes, on the recorded ground the siting tests use, and
 * then simply stepped. Nothing is handed to it: no timber is added, which is the
 * whole point — a fixture that feeds the town papers over the exact arithmetic
 * that was wrong.
 */
class SeededTownWorksTest {

    /** What {@code /civ seed town 40} passes, minus the player's standing position. */
    private static final String CULTURE = "civilization:human/norman";
    private static final int RESIDENTS = 40;

    /**
     * Long enough for the answer either way and no longer.
     *
     * <p>The town reaches the stores it needs at about two hundred steps and is
     * fully lit well before this; the build that was shipped is at zero at any
     * number you pick, because the reserve it was refused by never moves.
     */
    private static final int STEPS = 800;

    private static Settlement seeded;

    private static synchronized Settlement seededTown() {
        if (seeded == null) {
            RecordedTerrain ground = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
            SimPos site = new SimPos(16, ground.surfaceHeight(new SimPos(16, 64, 80)), 80);
            Settlement town = Founding.seeded(site, "Seeded", SettlementStage.TOWN,
                    BuildCatalog.DEFAULT, CULTURE, RESIDENTS, null, ground);
            for (int step = 1; step <= STEPS; step++) {
                town.step(new SimContext(ground, step, SimSettings.SANDBOX));
            }
            seeded = town;
        }
        return seeded;
    }

    @Test
    void aSeededTownActuallyRaisesTheLampsItPlans() {
        Settlement town = seededTown();
        int wanted = LightPlanner.wanted(town);

        assertTrue(wanted > 0, "a seeded town plans lamps at its doors and corners"
                + " whether or not it has walked a single street yet");
        assertTrue(town.lightsRaised() > 0,
                "lamps " + town.lightsRaised() + " of " + wanted + " after " + STEPS
                        + " steps. This is the playtest's line exactly, and the"
                        + " town is not poor -- it is holding a whole house's worth"
                        + " of planks against a work that wanted two");
        assertTrue(town.lightsRaised() * 2 >= wanted,
                "lamps " + town.lightsRaised() + " of " + wanted + ": a town that"
                        + " lights a token few and then stops is the same fault"
                        + " with a smaller number in front of it");
    }

    @Test
    void theReasonTheLampsWaitIsNeverTheWholeCostOfAHouse() {
        // The sharp form. Whatever else may hold a lamp up -- the dark, a crew
        // walking out to it, nothing left to raise -- it must never again be a
        // reserve larger than everything the town is standing on.
        Settlement town = seededTown();
        assertTrue(PublicWorks.timberHeldFromTheWorks(town) <= town.woodStock(),
                "the build queue is holding more timber than the town has");

        String why = LightPlanner.whyNotStarting(town);
        assertTrue(why == null || !why.startsWith("timber"),
                "the lamps are still being refused for timber: " + why);
    }

    /**
     * And the dressing behind them, which is the same fault one link down the
     * chain: see {@code PublicWorks.shareOfPasses}.
     */
    @Test
    void theDressingBehindTheLampsIsNotAtAStandstill() {
        Settlement town = seededTown();
        int lit = town.lightsRaised();
        int lamps = LightPlanner.wanted(town);
        assertTrue(PublicWorks.shareOfPasses(lit, lamps) > 0,
                "a town this lit must be handing something down; lamps "
                        + lit + " of " + lamps);
        String why = Furnishings.whyNotStarting(town);
        if (town.piecesRaised() == 0) {
            // A plan it has nothing to raise from is a fair answer and a lamp
            // count that has not got there is not; only the second is the fault.
            assertNotNull(why, "nothing raised and no reason given is the silence"
                    + " the reason line was added to end");
            assertTrue(!why.contains("passes in "),
                    "the dressing is still being told it is not its turn at "
                            + lit + " of " + lamps + " lamps: " + why);
        } else {
            assertNull(why, "a town raising its dressing has nothing to say about"
                    + " why it is not");
        }
    }
}
