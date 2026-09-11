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
    void abuildingWithNoSeededFlagWrittenIsRefused() {
        // The flag used to default to false so that every world written before
        // towns could be seeded would open. With that gone, an absent flag is a
        // damaged record, and the cost of guessing at it is a camp that never
        // gets its trees — the exact failure the flag exists to prevent, and one
        // that shows up hours later in somebody's world rather than here.
        JsonObject written = encode(seeded()).getAsJsonObject();
        JsonObject building = firstBuilding(written);
        assertTrue(building.has("seeded"), "the flag is not being written at all");
        building.remove("seeded");

        assertTrue(KingdomsCodecs.SETTLEMENT.parse(JsonOps.INSTANCE, written).result().isEmpty(),
                "a building with no debt recorded came back owing nothing");
    }

    @Test
    void areloadedSeededTownStillOwesItsStreets() {
        // The same argument as the wood, for the same towns. A village generated
        // on the far side of a flight and found three sessions later is exactly
        // the one that has not walked its roads out yet, and a debt that reset on
        // load would strand it in a field for good.
        assertTrue(decode(encode(seeded())).seededRoadsOwed(),
                "a town saved before anybody found it comes back with no streets"
                        + " and nothing saying so");
    }

    @Test
    void andATownThatHasWalkedThemDoesNotDoItTwice() {
        Settlement town = seeded();
        town.setSeededRoadsOwed(false);

        assertFalse(decode(encode(town)).seededRoadsOwed(),
                "the streets were walked out and came back unwalked");
    }

    @Test
    void atownWithNoRoadDebtWrittenIsRefused() {
        // The same argument as the wood, and the same failure: a town whose debt
        // was guessed away is a town stranded in a field for good.
        JsonObject written = encode(seeded()).getAsJsonObject();
        JsonObject charter = written.getAsJsonObject("charter");
        assertTrue(charter.has("seeded_roads_owed"), "the flag is not being written at all");
        charter.remove("seeded_roads_owed");

        assertTrue(KingdomsCodecs.SETTLEMENT.parse(JsonOps.INSTANCE, written).result().isEmpty(),
                "a town with no road debt recorded came back owing none");
    }

    /** The first building, at {@code works.buildings[0]}. */
    private static JsonObject firstBuilding(JsonObject town) {
        return town.getAsJsonObject("works").getAsJsonArray("buildings")
                .get(0).getAsJsonObject();
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
