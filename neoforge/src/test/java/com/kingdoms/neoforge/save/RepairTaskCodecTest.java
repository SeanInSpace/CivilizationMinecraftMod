package com.kingdoms.neoforge.save;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Settlement;
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

    private static final SimPos CENTRE = new SimPos(0, 64, 0);
    private static final SimPos PLOT = new SimPos(12, 64, -8);

    private static Settlement mending() {
        Settlement town = new Settlement(Settlement.Id.random(), "Mendham", CENTRE, 64);
        BuildTask repair = new BuildTask("kingdoms:cottage", PLOT, 40);
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
    void asaveFromBeforeRepairsExistedStillLoads() {
        // Every world written until now. None of them holds a job booked against
        // a standing building, so both fields simply being absent is the honest
        // answer rather than a migration — and it must not be an exception
        // either.
        JsonObject written = encode(mending()).getAsJsonObject();
        JsonObject task = written.getAsJsonArray("build_queue").get(0).getAsJsonObject();
        assertTrue(task.has("repair"), "the flag is not being written at all");
        assertTrue(task.has("upgrade_of"), "nor is the plot it is booked against");
        task.remove("repair");
        task.remove("upgrade_of");

        BuildTask back = decode(written).buildQueue().getFirst();

        assertFalse(back.isRepair(), "an old save came back as a repair it never was");
        assertFalse(back.isUpgrade());
    }

    private static JsonElement encode(Settlement town) {
        return KingdomsCodecs.SETTLEMENT.encodeStart(JsonOps.INSTANCE, town)
                .result().orElseThrow();
    }

    private static Settlement decode(JsonElement written) {
        return KingdomsCodecs.SETTLEMENT.parse(JsonOps.INSTANCE, written)
                .result().orElseThrow();
    }
}
