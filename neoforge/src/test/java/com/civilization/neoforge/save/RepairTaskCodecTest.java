package com.civilization.neoforge.save;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.BuildTask;
import com.civilization.sim.settlement.Settlement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A repair that survives being saved still knows it is a repair.
 *
 * <p>Two fields on a build task said nothing about themselves for a long while
 * and it did not matter: upgrading had been taken out of the planner, so nothing
 * produced a job booked against a building that was already standing and the
 * omission cost nothing. Repairs bring that job back, and the omission is now a
 * trap. A reloaded repair that had forgotten what it was is an ordinary build of
 * the same blueprint on the same spot — so the crew's first act is to excavate
 * the footprint, which is to say pull down the house they were sent to mend, and
 * on finishing it the town writes a second building onto the plot.
 *
 * <p>Read and written through JSON, for the reason
 * {@code SettlementLayoutCodecTest} gives: the codecs are plain
 * DataFixerUpper and JSON is the one an assertion can be written against.
 */
class RepairTaskCodecTest {

    private static final SimPos CENTER = new SimPos(0, 64, 0);
    private static final SimPos PLOT = new SimPos(12, 64, -8);

    private static Settlement mending() {
        Settlement town = new Settlement(Settlement.Id.random(), "Mendham", CENTER, 64);
        BuildTask repair = new BuildTask("civilization:cottage", PLOT, 40);
        repair.setUpgradeOf(PLOT);
        repair.setRepair(true);
        repair.setFacing(2);
        town.enqueueUrgent(repair);
        return town;
    }

    @Test
    void areloadedRepairIsStillWorkOnTheBuildingThatStandsThere() {
        BuildTask back = decode(encode(mending())).buildQueue().getFirst();

        assertTrue(back.isRepair(), "it came back as an ordinary build of the same cottage");
        assertTrue(back.isUpgrade(), "and with no idea which building it belongs to");
        assertEquals(PLOT, back.upgradeOf());
        assertEquals(2, back.facing(),
                "and the turn the wall was actually found to be standing in, which"
                        + " is what the whole diff is measured against");
    }

    @Test
    void ajobBookedAgainstNothingIsAnOrdinaryBuild() {
        // These two stay optional, and not for compatibility: most jobs in most
        // towns are ordinary builds on empty plots, and "no building underneath"
        // is what that honestly looks like. The absent plot is the statement.
        JsonObject written = encode(mending()).getAsJsonObject();
        JsonObject task = queuedTask(written);
        assertTrue(task.has("repair"), "the flag is not being written at all");
        assertTrue(task.has("upgrade_of"), "nor is the plot it is booked against");
        task.remove("repair");
        task.remove("upgrade_of");

        BuildTask back = decode(written).buildQueue().getFirst();

        assertFalse(back.isRepair(), "a plain build came back as a repair");
        assertFalse(back.isUpgrade());
    }

    @Test
    void howFarTheDiggingGotIsWrittenRatherThanRewound() {
        // The excavation count used to be optional, defaulting to a sentinel
        // that meant "a save from before digging was split out of the step
        // list", and reading it rewound the whole cursor to zero so the crew
        // laid every course again. Nothing writes such a task now; a task with
        // no dig count is a damaged record, and rewinding one silently is worse
        // than refusing it.
        JsonObject written = encode(mending()).getAsJsonObject();
        JsonObject work = queuedTask(written).getAsJsonObject("work");
        assertTrue(work.has("dig_done"), "the excavation count is not being written at all");
        work.remove("dig_done");

        assertTrue(CivilizationCodecs.SETTLEMENT.parse(JsonOps.INSTANCE, written).result().isEmpty(),
                "a task with no excavation recorded came back rewound to nothing");
    }

    /** The one queued job, at {@code works.build_queue[0]}. */
    private static JsonObject queuedTask(JsonObject town) {
        return town.getAsJsonObject("works").getAsJsonArray("build_queue")
                .get(0).getAsJsonObject();
    }

    private static JsonElement encode(Settlement town) {
        return CivilizationCodecs.SETTLEMENT.encodeStart(JsonOps.INSTANCE, town)
                .result().orElseThrow();
    }

    private static Settlement decode(JsonElement written) {
        return CivilizationCodecs.SETTLEMENT.parse(JsonOps.INSTANCE, written)
                .result().orElseThrow();
    }
}
