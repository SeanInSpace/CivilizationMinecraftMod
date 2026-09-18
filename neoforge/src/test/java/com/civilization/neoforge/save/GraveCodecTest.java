package com.civilization.neoforge.save;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.Settlement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The town's dead, across a save.
 *
 * <p>Everything else a town puts on the ground between its houses is derived —
 * a yard's position is a function of the house it belongs to, a hedge's of the
 * street it runs along, and both of those are saved already, so the only number
 * the dressing writes down is how far along the list the work has got. A
 * graveyard cannot be derived from anything: a name is not a function of a
 * town's shape. So the dead are the one thing this work adds to the save, and
 * this is what says they survive it.
 *
 * <p>Compatibility with older worlds is not a consideration — it was waived on
 * 2026-09-11 and old saves do not load — but a field absent from a save still
 * has to read as something sensible, because "absent" is what every world
 * written before this existed says. It reads as a town that has never lost
 * anybody, which is true of a town that never had graves.
 */
class GraveCodecTest {

    private static final SimPos CENTER = new SimPos(-320, 68, 96);

    private static Settlement buried(int howMany) {
        Settlement town = new Settlement(Settlement.Id.random(), "Barrowfield", CENTER, 128);
        for (int i = 0; i < howMany; i++) {
            Person lost = new Person(Person.Id.random(), "Lost " + i,
                    i % 2 == 0 ? Profession.FARMER : Profession.GUARD, CENTER);
            town.addResident(lost);
            town.bury(lost.id(), 40L + i);
        }
        return town;
    }

    @Test
    void theDeadComeBackWithTheirNamesTheirTradesAndTheirDay() {
        Settlement town = buried(4);
        List<Settlement.Grave> went = town.dead();

        Settlement back = decode(encode(town));

        assertEquals(went, back.dead(),
                "a reload forgot who the town buried, so the stones out on the"
                        + " verge are for nobody");
        assertEquals("Lost 0, Farmer", back.dead().getFirst().epitaph(),
                "and the line cut into the first of them");
    }

    @Test
    void theOrderSurvives() {
        // Oldest first, because the row is laid in that order and a reload that
        // reversed it would shuffle every stone in the churchyard.
        Settlement back = decode(encode(buried(5)));
        List<Long> days = new ArrayList<>();
        for (Settlement.Grave grave : back.dead()) {
            days.add(grave.day());
        }
        assertEquals(List.of(40L, 41L, 42L, 43L, 44L), days,
                "the dead came back out of order, so the row is somebody else's");
    }

    @Test
    void aTownThatHasLostNobodyWritesNothingAndComesBackEmpty() {
        Settlement town = new Settlement(Settlement.Id.random(), "Lucky", CENTER, 128);
        JsonObject written = encode(town).getAsJsonObject();
        assertFalse(written.has("dead") && !written.getAsJsonArray("dead").isEmpty(),
                "a town with no dead should not be carrying a list of them");
        assertTrue(decode(written).dead().isEmpty());
    }

    @Test
    void aSaveCarryingMoreDeadThanATownKeepsStillComesBackWithADozen() {
        // The bound is applied on the way in as well as on the way out. A save
        // written by a build with a larger cap — or edited by hand — must not be
        // able to stand a hundred headstones round a village.
        Settlement town = buried(Settlement.DEAD_REMEMBERED);
        JsonObject written = encode(town).getAsJsonObject();
        var list = written.getAsJsonArray("dead");
        for (int extra = 0; extra < 20; extra++) {
            list.add(list.get(0).deepCopy());
        }
        assertEquals(Settlement.DEAD_REMEMBERED, decode(written).dead().size());
    }

    @Test
    void aTradeThisBuildDoesNotKnowReadsAsAnIdlerRatherThanRefusingTheWorld() {
        Settlement town = buried(1);
        JsonObject written = encode(town).getAsJsonObject();
        written.getAsJsonArray("dead").get(0).getAsJsonObject()
                .addProperty("profession", "FISHMONGER");

        Settlement back = decode(written);
        assertEquals(Profession.IDLER, back.dead().getFirst().profession(),
                "an epitaph reading only a name is better than a world that"
                        + " refuses to load");
        assertEquals("Lost 0", back.dead().getFirst().epitaph());
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
