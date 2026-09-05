package com.kingdoms.neoforge.save;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.Settlement;
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

    private static final SimPos CENTRE = new SimPos(512, 72, -512);

    private static List<SimPos> box(int half) {
        return List.of(new SimPos(CENTRE.x() - half, 72, CENTRE.z() - half),
                new SimPos(CENTRE.x() + half, 72, CENTRE.z() - half),
                new SimPos(CENTRE.x() + half, 72, CENTRE.z() + half),
                new SimPos(CENTRE.x() - half, 72, CENTRE.z() + half));
    }

    private static Settlement walled(Perimeter ring) {
        Settlement town = new Settlement(Settlement.Id.random(), "Ringmere", CENTRE, 256);
        town.setPerimeter(ring);
        return town;
    }

    @Test
    void aRingComesBackStillCarryingTheLinesItReplaced() {
        List<Perimeter.Retired> replaced = List.of(
                new Perimeter.Retired(box(30), 240),
                new Perimeter.Retired(box(45), 100));
        Perimeter ring = new Perimeter(box(60), List.of(new SimPos(CENTRE.x() + 60, 72,
                CENTRE.z())), 40, replaced);

        Perimeter back = decode(encode(walled(ring))).perimeter();

        assertEquals(replaced, back.retired(),
                "a reload forgot a wall that is still standing in the world");
        assertEquals(40, back.laid(), "and the posts it had raised with it");
        assertEquals(box(60), back.vertices());
    }

    @Test
    void aSaveFromBeforeATownCouldOutgrowItsWallStillLoads() {
        // Every world saved before re-staking existed. Those towns have one
        // wall and always had, so an absent field is not a migration — it is
        // the honest answer, and it must not be an exception either. Written
        // from a ring that does owe a demolition and then stripped, because a
        // ring owing none writes no field at all: the whole apparatus is
        // invisible in the save of a town that has never moved its wall.
        Settlement town = walled(new Perimeter(box(60), List.of(), 12,
                List.of(new Perimeter.Retired(box(30), 240))));

        JsonObject written = encode(town).getAsJsonObject();
        JsonObject ring = written.getAsJsonObject("perimeter");
        assertTrue(ring.has("retired"), "the retired lines are not being written at all");
        ring.remove("retired");

        Perimeter back = decode(written).perimeter();
        assertTrue(back.retired().isEmpty(),
                "an old save came back owing a demolition it never had");
        assertTrue(back.retiredPositions().isEmpty());
        assertEquals(12, back.laid());
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
        JsonObject ring = written.getAsJsonObject("perimeter");
        assertTrue(ring.has("staked_on"), "the wall's age is not being written at all");
        assertEquals(900L, decode(written).perimeter().stakedOn(),
                "a reloaded town forgot when it walled itself");

        // And the worlds saved before walls had an age: absent is zero, which
        // reads as a wall that has stood since this session began.
        ring.remove("staked_on");
        assertEquals(0L, decode(written).perimeter().stakedOn(),
                "an old save would not load at all for want of a field it never had");
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
