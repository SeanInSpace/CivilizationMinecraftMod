package com.kingdoms.neoforge.save;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.BuildCatalog;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingRole;
import com.kingdoms.sim.settlement.Founding;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A seeded town that was saved before anybody walked up to it still owes its
 * forester a wood.
 *
 * <p>World generation writes a town the moment its chunk is generated, and the
 * player may not come within sight of it for a hundred hours of play — through
 * any number of saves and reloads. The debt that says "this camp has never had
 * its trees planted" is only paid at the first drawing, so if it does not
 * survive a save, the towns that most need it are exactly the ones that lose it:
 * every one a player did not happen to walk into during the session it was
 * generated in.
 *
 * <p>Read and written through JSON, for the reason
 * {@code SettlementLayoutCodecTest} gives: the codecs are plain DataFixerUpper
 * and JSON is the one an assertion can be written against.
 */
class SeededBuildingCodecTest {

    private static final SimPos SITE = new SimPos(0, 72, 0);

    private static Settlement seeded() {
        return Founding.seeded(SITE, "Seedholt", SettlementStage.VILLAGE,
                BuildCatalog.DEFAULT, Culture.NORMAN.id());
    }

    @Test
    void areloadedSeededCampIsStillOwedItsWood() {
        Settlement back = decode(encode(seeded()));

        Building camp = back.buildingWithRole(BuildingRole.LUMBER_CAMP);
        assertNotNull(camp, "the seeded village lost its lumber camp in the save");
        assertTrue(camp.isSeeded(),
                "a town saved before a player found it comes back on bare ground");
    }

    @Test
    void acampThatHasAlreadyHadItsWoodDoesNotGetASecond() {
        Settlement town = seeded();
        Building camp = town.buildingWithRole(BuildingRole.LUMBER_CAMP);
        camp.setSeeded(false);

        Building back = decode(encode(town)).buildingWithRole(BuildingRole.LUMBER_CAMP);
        assertFalse(back.isSeeded(), "the debt was paid and came back unpaid");
    }

    @Test
    void asaveFromBeforeTownsWereSeededStillLoads() {
        // Every world written until now. None of them holds a building that was
        // written into existence rather than built, so the field simply being
        // absent is the honest answer rather than a migration — and it must not
        // be an exception either.
        JsonObject written = encode(seeded()).getAsJsonObject();
        JsonObject building = written.getAsJsonArray("buildings").get(0).getAsJsonObject();
        assertTrue(building.has("seeded"), "the flag is not being written at all");
        building.remove("seeded");

        Building back = decode(written).buildings().getFirst();
        assertFalse(back.isSeeded(), "an old save came back owed a wood it never lacked");
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
