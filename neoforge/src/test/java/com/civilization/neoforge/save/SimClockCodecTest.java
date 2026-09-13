package com.civilization.neoforge.save;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.kingdom.Kingdom;
import com.civilization.sim.settlement.Perimeter;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimWorld;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The simulation's clock survives a reload, and everything measured against it
 * still reads sensibly on the other side.
 *
 * <p>{@code SimWorld.stepsElapsed} restarted at zero every session while four
 * durable things carried step numbers out of the save file:
 * {@code Perimeter.stakedOn}, {@code Building.completedOnStep},
 * {@code Settlement.firstStep} and the raid schedule. So every one of them was, on
 * the first step after a reload, a date in the future — a wall staked nine hundred
 * steps from now, a town founded nine hundred steps from now and therefore inside
 * its founding grace against raids all over again, for as long as it took the
 * session to catch up.
 *
 * <p>Written through JSON for the reason {@code SettlementLayoutCodecTest} gives:
 * these codecs are plain DataFixerUpper and JSON is the one a test can assert
 * against.
 */
class SimClockCodecTest {

    private static final SimPos CENTER = new SimPos(64, 70, -64);

    private static CivilizationSavedData saved(long steps) {
        CivilizationSavedData data = new CivilizationSavedData();
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Realm",
                "civilization:human/norman");
        Settlement town = new Settlement(Settlement.Id.random(), "Millbrook", CENTER, 128);
        town.setFirstStep(700L);
        town.setPerimeter(new Perimeter(
                List.of(new SimPos(CENTER.x() - 30, 70, CENTER.z() - 30),
                        new SimPos(CENTER.x() + 30, 70, CENTER.z() - 30),
                        new SimPos(CENTER.x() + 30, 70, CENTER.z() + 30),
                        new SimPos(CENTER.x() - 30, 70, CENTER.z() + 30)),
                List.of(), 12, List.of(), 900L));
        kingdom.restoreSettlement(town);
        data.addKingdom(kingdom);
        data.setStepsElapsed(steps);
        return data;
    }

    private static JsonElement encode(CivilizationSavedData data) {
        return CivilizationSavedData.CODEC.encodeStart(JsonOps.INSTANCE, data)
                .result().orElseThrow();
    }

    private static CivilizationSavedData decode(JsonElement written) {
        return CivilizationSavedData.CODEC.parse(JsonOps.INSTANCE, written)
                .result().orElseThrow();
    }

    @Test
    void aReloadKeepsTheStepCount() {
        JsonObject written = encode(saved(1234L)).getAsJsonObject();

        assertTrue(written.has("steps_elapsed"),
                "the simulation's clock is not being written at all");
        assertEquals(1234L, decode(written).stepsElapsed(),
                "a reloaded dimension forgot how long it had been running");
    }

    @Test
    void theWorldComesBackCountingFromWhereItStopped() {
        // The whole of the fix end to end: what the save carries is handed to the
        // world, and the world goes on from there rather than from zero.
        CivilizationSavedData reloaded = decode(encode(saved(1234L)));

        SimWorld world = new SimWorld(new NullBridge());
        reloaded.kingdoms().forEach(world::addKingdom);
        world.restoreStepsElapsed(reloaded.stepsElapsed());

        assertEquals(1234L, world.stepsElapsed());
        world.step();
        assertEquals(1235L, world.stepsElapsed(), "the clock restarted anyway");
    }

    @Test
    void everythingMeasuredAgainstTheClockNowReadsForward() {
        CivilizationSavedData reloaded = decode(encode(saved(1234L)));
        SimWorld world = new SimWorld(new NullBridge());
        reloaded.kingdoms().forEach(world::addKingdom);
        world.restoreStepsElapsed(reloaded.stepsElapsed());

        Settlement town = reloaded.kingdoms().getFirst().settlements().iterator().next();
        long now = world.stepsElapsed();

        // The wall: staked on 900, so at step 1234 it is 334 steps old. Before the
        // clock was saved this was step 0 against a stake of 900 and Perimeter.ageAt
        // read the whole thing as a restarted clock, handing the wall the age of the
        // session instead.
        assertEquals(334L, town.perimeter().ageAt(now),
                "the wall's age is still being read off a restarted clock");

        // The town: founded on 700, so 534 steps old, and long out of its founding
        // grace. It used to come back aged zero, which is inside the grace, so a
        // reloaded town could not be raided until the session caught up with it.
        assertEquals(534L, town.ageInSteps(now),
                "a reloaded town came back younger than it is");
    }

    @Test
    void aWorldWrittenBeforeTheClockWasSavedLoadsAtZero() {
        // The field is optional on purpose: a save written before this existed has
        // no count to give, and zero is what such a world used to load with anyway.
        JsonObject written = encode(saved(1234L)).getAsJsonObject();
        written.remove("steps_elapsed");

        assertEquals(0L, decode(written).stepsElapsed(),
                "an absent count should read as a world that has never stepped");
    }

    @Test
    void theClockIsNotWoundBackwards() {
        CivilizationSavedData data = saved(1234L);
        data.setStepsElapsed(12L);
        assertEquals(1234L, data.stepsElapsed(),
                "a step count that can go down makes every recorded step ambiguous");

        SimWorld world = new SimWorld(new NullBridge());
        world.restoreStepsElapsed(500L);
        assertThrows(IllegalStateException.class, () -> world.restoreStepsElapsed(900L),
                "a clock that can be wound at any time is one nothing may be"
                        + " compared against");
    }

    /** A bridge that answers nothing, because none of this asks the world anything. */
    private static final class NullBridge implements com.civilization.sim.platform.WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) {
            return false;
        }

        @Override public boolean isLoaded(SimPos pos) {
            return false;
        }

        @Override public int surfaceHeight(SimPos pos) {
            return pos.y();
        }

        @Override public com.civilization.sim.settlement.Footprint materializeBlueprint(
                String blueprintId, SimPos origin, boolean surveyed, int facing) {
            return null;
        }

        @Override public void log(String message) {
        }
    }
}
