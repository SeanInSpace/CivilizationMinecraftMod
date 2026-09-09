package com.kingdoms.neoforge.save;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Seam;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.Stand;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A half-felled claim and a half-dug mine are the same when the game reopens.
 *
 * <p>{@code FieldRipenessCodecTest}'s argument, twice more. A camp's stand and a
 * mine's seam are the only record of what an unwatched town has been working,
 * and an unwatched town is exactly the one nobody has been near for a long time.
 * Rebuilt from nothing at every load, a mine would be inexhaustible and a felled
 * wood would stand up again every time the player quit.
 *
 * <p>The sentinel matters as much as the number. Both fields default to
 * {@link Stand#UNCOUNTED} rather than to zero, because every save ever written
 * before today was written by a world that had never counted a tree — and "no
 * count" must not load as "felled bare" or "cut out".
 */
class StandAndSeamCodecTest {

    private static Settlement townWithTrades(int standThousandths, int growing, int seam) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Timberton", new SimPos(0, 64, 0), 128);
        Building camp = new Building("kingdoms:lumber_camp", new SimPos(20, 64, 0), 3, true);
        camp.setStandThousandths(standThousandths);
        camp.setGrowingThousandths(growing);
        town.addBuilding(camp);
        Building mine = new Building("kingdoms:mine", new SimPos(-20, 64, 0), 4, true);
        mine.setStoneSeam(seam);
        town.addBuilding(mine);
        return town;
    }

    @Test
    void aHalfFelledClaimComesBackHalfFelled() {
        Settlement back = decode(encode(townWithTrades(31_500, 12_000, 1_337)));
        Building camp = back.buildings().getFirst();

        assertEquals(31_500, camp.standThousandths());
        assertEquals(5, Stand.trees(camp), "five trees and a half");
        assertEquals(2, Stand.growing(camp), "with two more coming up");
        assertEquals(1_337, Seam.remaining(back.buildings().get(1)));
    }

    @Test
    void theFractionOfALogSurvivesRatherThanBeingRounded() {
        // A quarter of a log is a step or two of a regrown claim's whole income.
        // Rounded off at every save it is a step or two a session lost, forever,
        // to the town that has least.
        Building camp = decode(encode(townWithTrades(250, 250, 0))).buildings().getFirst();

        assertEquals(250, camp.standThousandths());
        assertEquals(250, camp.growingThousandths());
    }

    @Test
    void anExhaustedMineStaysExhausted() {
        Building mine = decode(encode(townWithTrades(0, 0, 0))).buildings().get(1);

        assertTrue(Seam.isCounted(mine));
        assertTrue(Seam.isExhausted(mine),
                "a mine dug out before the save is a mine dug out after it");
    }

    @Test
    void aSaveFromBeforeAnyOfThisStillLoadsAsUncounted() {
        JsonObject written = encode(townWithTrades(31_500, 6_000, 900)).getAsJsonObject();
        JsonObject camp = written.getAsJsonArray("buildings").get(0).getAsJsonObject();
        JsonObject mine = written.getAsJsonArray("buildings").get(1).getAsJsonObject();
        assertTrue(camp.has("stand") && camp.has("growing") && mine.has("seam"),
                "the ledgers are not being written at all");
        camp.remove("stand");
        camp.remove("growing");
        mine.remove("seam");

        Settlement back = decode(written);
        assertTrue(!Stand.isCounted(back.buildings().getFirst()),
                "an old camp wakes up not knowing what it stands in, and finds"
                        + " out the day somebody loads its ground");
        assertTrue(!Seam.isCounted(back.buildings().get(1)),
                "and an old mine wakes up unsurveyed rather than spent");
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
