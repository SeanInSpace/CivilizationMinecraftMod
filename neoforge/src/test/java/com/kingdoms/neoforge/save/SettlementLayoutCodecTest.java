package com.kingdoms.neoforge.save;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Settlement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town's arrangement, across a save.
 *
 * <p>A people can build in several arrangements now and picks between them by
 * hashing the town's center. That is fine for choosing one and hopeless for
 * keeping it: the day the hash changes, every town in every world would be a
 * different shape from the streets it already has on the ground. So the choice
 * is written down, and these are the two halves of what that has to mean —
 * a recorded arrangement comes back exactly, and a save that has none takes the
 * arrangement its people were building in before any of this existed.
 *
 * <p>Read and written through JSON rather than NBT. The codecs are plain
 * DataFixerUpper and care about neither, and JSON is the one an assertion can be
 * written against by hand.
 */
class SettlementLayoutCodecTest {

    private static final SimPos CENTRE = new SimPos(1_024, 72, -1_024);

    @Test
    void aTownComesBackInTheArrangementItWasBuiltIn() {
        Settlement town = new Settlement(Settlement.Id.random(), "Karrgurd", CENTRE, 256);
        town.setCultureId(Culture.ORC.id());
        town.setLayoutId(Culture.LAYOUT_STRONGHOLD_STREETS);

        Settlement back = decode(encode(town));

        assertEquals(Culture.LAYOUT_STRONGHOLD_STREETS, back.arrangement().id(),
                "the town came back laid out as something it never was");
        assertEquals(Culture.ORC.id(), back.cultureId());
    }

    @Test
    void aSaveWithNoArrangementRecordedIsNotRearranged() {
        // Every world saved before this field existed. The town on the ground is
        // a gridiron of plots and nothing in the file says so, so the only safe
        // answer is the arrangement its people were building in at the time —
        // the head of the list. Deriving one from the center instead would
        // re-plan a standing town into streets it does not have.
        Settlement town = new Settlement(Settlement.Id.random(), "Dromgar", CENTRE, 256);
        town.setCultureId(Culture.ORC.id());
        town.setLayoutId(Culture.LAYOUT_STRONGHOLD_STREETS);

        JsonObject written = encode(town).getAsJsonObject();
        assertTrue(written.has("layout"), "the layout is not being written at all");
        written.remove("layout");

        assertEquals(Culture.ORC.layouts().get(0), decode(written).arrangement().id(),
                "an old save was rearranged on load");
    }

    @Test
    void aTownIsNeverSavedWithoutAnArrangement() {
        // Nothing had asked this town how it was laid out, so nothing had settled
        // it. Saving is an asking: leaving the field out here would mean a town
        // founded and saved in the same tick came back as the head of the list
        // rather than as the arrangement it would have grown into.
        Settlement town = new Settlement(Settlement.Id.random(), "Ashfang", CENTRE, 256);
        town.setCultureId(Culture.ORC.id());

        String written = encode(town).getAsJsonObject().get("layout").getAsString();
        assertEquals(Culture.ORC.layoutFor(CENTRE), written,
                "the save recorded an arrangement the culture would not have chosen");
        assertEquals(written, decode(encode(town)).arrangement().id());
    }

    @Test
    void arrangementsSurviveAPeopleWithOnlyOne() {
        // The goblins build one way, and the point of the compatibility rule is
        // that nothing about them changed. Asserted on the written JSON as well
        // as the decoded town, because Layouts.of answers an id it does not know
        // with rings -- so a decode that returned a warren having read nothing at
        // all would look exactly like a decode that worked. That trick needs a
        // people whose one arrangement is not itself rings, which is why this is
        // the goblins and not the default folk.
        Settlement town = new Settlement(Settlement.Id.random(), "Grubhold", CENTRE, 256);
        town.setCultureId(Culture.GOBLIN.id());

        JsonObject written = encode(town).getAsJsonObject();
        assertEquals(Culture.LAYOUT_WARREN, written.get("layout").getAsString(),
                "a people who build one way did not record which way");
        assertEquals(Culture.LAYOUT_WARREN, decode(written).arrangement().id());
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
