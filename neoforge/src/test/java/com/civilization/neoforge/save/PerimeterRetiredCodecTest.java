package com.civilization.neoforge.save;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.Perimeter;
import com.civilization.sim.settlement.Settlement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The walls a town has replaced, and the age of the one it has, across a save.
 *
 * <p>A settlement whose suburbs come to outnumber the quarter inside its wall
 * stakes a wider line and hands the old one on to be pulled down. That handover
 * is state, not a moment: the demolition walks the old ring a slice at a time
 * and half of it is usually in unloaded chunks. Lose the list on a reload and
 * the old posts stay in the ground for good, with the new wall outside them —
 * two walls, which is the one outcome re-staking exists to avoid.
 *
 * <p>The age of the standing line has to survive for a different reason: it is
 * half of what decides whether the town is allowed to move it at all.
 *
 * <p>Read and written through JSON, for the reason
 * {@code SettlementLayoutCodecTest} gives: the codecs are plain
 * DataFixerUpper and JSON is the one an assertion can be written against.
 */
class PerimeterRetiredCodecTest {

    private static final SimPos CENTER = new SimPos(512, 72, -512);

    private static List<SimPos> box(int half) {
        return List.of(new SimPos(CENTER.x() - half, 72, CENTER.z() - half),
                new SimPos(CENTER.x() + half, 72, CENTER.z() - half),
                new SimPos(CENTER.x() + half, 72, CENTER.z() + half),
                new SimPos(CENTER.x() - half, 72, CENTER.z() + half));
    }

    private static Settlement walled(Perimeter ring) {
        Settlement town = new Settlement(Settlement.Id.random(), "Ringmere", CENTER, 256);
        town.setPerimeter(ring);
        return town;
    }

    @Test
    void aRingComesBackStillCarryingTheLinesItReplaced() {
        List<Perimeter.Retired> replaced = List.of(
                new Perimeter.Retired(box(30), 240),
                new Perimeter.Retired(box(45), 100));
        Perimeter ring = new Perimeter(box(60), List.of(new SimPos(CENTER.x() + 60, 72,
                CENTER.z())), 40, replaced);

        Perimeter back = decode(encode(walled(ring))).perimeter();

        assertEquals(replaced, back.retired(),
                "a reload forgot a wall that is still standing in the world");
        assertEquals(40, back.laid(), "and the posts it had raised with it");
        assertEquals(box(60), back.vertices());
    }

    @Test
    void aTownThatHasNeverMovedItsWallStillWritesAnEmptyList() {
        // The whole apparatus used to be invisible in the save of a town that
        // has never re-staked: the field was optional and an absent one meant
        // "one wall, and always had". It is written now, empty, because an
        // absent list and an empty list are the same statement only as long as
        // somebody remembers which default was chosen.
        Settlement town = walled(new Perimeter(box(60), List.of(), 12, List.of()));

        JsonObject ring = ringOf(encode(town).getAsJsonObject());
        assertTrue(ring.has("retired"), "the retired lines are not being written at all");
        assertTrue(ring.getAsJsonArray("retired").isEmpty());

        Perimeter back = decode(encode(town)).perimeter();
        assertTrue(back.retired().isEmpty());
        assertTrue(back.retiredPositions().isEmpty());
        assertEquals(12, back.laid());
    }

    @Test
    void aRingWithNoRetiredLinesWrittenIsRefusedRatherThanAssumedSingle() {
        Settlement town = walled(new Perimeter(box(60), List.of(), 12,
                List.of(new Perimeter.Retired(box(30), 240))));

        JsonObject written = encode(town).getAsJsonObject();
        ringOf(written).remove("retired");

        assertTrue(CivilizationCodecs.SETTLEMENT.parse(JsonOps.INSTANCE, written).result().isEmpty(),
                "a ring with its demolition list missing came back owing nothing,"
                        + " which is how two walls are left standing");
    }

    @Test
    void aWallComesBackAsOldAsItWent() {
        // The wall's age is what says whether the town may move it yet, so a
        // reload that forgot it would hand every loaded town a wall of no age
        // at all and let it re-stake on the first review after loading. The
        // step counter this is measured against is a separate problem, and
        // Perimeter.ageAt is where it is dealt with; what has to survive the
        // save is the number itself.
        Settlement town = walled(new Perimeter(box(60), List.of(), 12, List.of(), 900L));

        JsonObject written = encode(town).getAsJsonObject();
        assertTrue(ringOf(written).has("staked_on"), "the wall's age is not being written at all");
        assertEquals(900L, decode(written).perimeter().stakedOn(),
                "a reloaded town forgot when it walled itself");

        // And an age that is not there at all is a refusal, not a zero. Zero is
        // a wall staked this session, which is the one reading that lets a town
        // re-stake the moment it loads.
        ringOf(written).remove("staked_on");
        assertTrue(CivilizationCodecs.SETTLEMENT.parse(JsonOps.INSTANCE, written).result().isEmpty(),
                "a wall of no recorded age came back young enough to move");
    }

    /** The standing ring, at {@code defense.perimeter}. */
    private static JsonObject ringOf(JsonObject town) {
        return town.getAsJsonObject("defense").getAsJsonObject("perimeter");
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
