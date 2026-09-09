package com.kingdoms.neoforge.save;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Field;
import com.kingdoms.sim.settlement.Settlement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field that was half grown when the game closed is half grown when it opens.
 *
 * <p>The ripeness ledger is the only record of what an unwatched farm has been
 * doing, and an unwatched farm is precisely the one nobody has been near for a
 * long time — a worldgen village can sit through whole sessions without a player
 * within ninety-six blocks of it. If the ledger were rebuilt from nothing at
 * every load, every one of those towns would restart its harvest each time the
 * player quit, and the towns that most depend on the clock would be the ones it
 * fed least.
 *
 * <p>Read and written through JSON, for the reason {@code SeededBuildingCodecTest}
 * gives: the codecs are plain DataFixerUpper and JSON is what an assertion can
 * be written against.
 */
class FieldRipenessCodecTest {

    private static Settlement townWithAFarm(int ripeHundredths) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Fieldholt", new SimPos(0, 64, 0), 128);
        Building farm = new Building("kingdoms:farm", new SimPos(20, 64, 0), 3, true);
        farm.setRipeHundredths(ripeHundredths);
        town.addBuilding(farm);
        return town;
    }

    @Test
    void aHalfGrownFieldComesBackHalfGrown() {
        Building back = decode(encode(townWithAFarm(1750))).buildings().getFirst();

        assertEquals(1750, back.ripeHundredths(),
                "the ledger is what the town has been eating off; it cannot be"
                        + " rebuilt from the blocks, because nobody has been there");
        assertEquals(17, Field.ripeBlocks(back));
    }

    @Test
    void theFractionOfABlockSurvivesTooRatherThanBeingRounded() {
        // Fifty hundredths is half a crop, which is about three steps of
        // growing. Rounded off at every save it would be three steps a session
        // quietly lost, forever, to a town that is already the slowest earner
        // in the world.
        Building back = decode(encode(townWithAFarm(50))).buildings().getFirst();

        assertEquals(50, back.ripeHundredths());
    }

    @Test
    void asaveFromBeforeFieldsWereCountedStillLoads() {
        // Every world written until now. A farm in one of them comes back with
        // an empty field and grows one, which is the honest answer: nothing was
        // recorded, so nothing is claimed.
        JsonObject written = encode(townWithAFarm(1750)).getAsJsonObject();
        JsonObject farm = written.getAsJsonArray("buildings").get(0).getAsJsonObject();
        assertTrue(farm.has("ripe"), "the ledger is not being written at all");
        farm.remove("ripe");

        assertEquals(0, decode(written).buildings().getFirst().ripeHundredths(),
                "an old save came back claiming a harvest it never had");
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
