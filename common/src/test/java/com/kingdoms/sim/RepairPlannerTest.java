package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.RepairPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A building notices when it has been knocked about, and gets fixed when it
 * matters.
 *
 * <p>Damage is measured by counting, not by watching for explosions: the
 * building is counted when first seen whole and counted again afterwards, and
 * the shortfall is the damage. That is why none of these tests mention a
 * creeper — it does not matter what took the blocks.
 */
class RepairPlannerTest {

    private static final BuildingType COTTAGE =
            new BuildingType("test:cottage", 100, 0, 0, 0, 80, 4);

    /** Reports whatever census it is told to. */
    private static final class QuarryBridge implements WorldBridge {
        int standing = 200;

        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin, boolean s, int f) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
        @Override public int solidBlocksIn(SimPos origin, Footprint plot) { return standing; }
    }

    private static Settlement town() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);
        town.setCatalogue(List.of(COTTAGE));
        Building cottage = new Building(COTTAGE.id(), new SimPos(10, 64, 10), 1, true);
        cottage.setFootprint(new Footprint(64, 5, 5, 4));
        town.addBuilding(cottage);
        return town;
    }

    private static Building only(Settlement town) {
        return town.buildings().getFirst();
    }

    private static void look(Settlement town, QuarryBridge bridge, int step) {
        RepairPlanner.advance(town, new SimContext(bridge, step, SimSettings.SANDBOX));
    }

    // --- the baseline ---

    @Test
    void theFirstLookEstablishesWhatWholeLooksLike() {
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();

        look(town, bridge, 0);

        assertEquals(200, only(town).soundCensus(), "counted as it stands, not as drawn");
        assertEquals(0, only(town).damage(), "and nothing is wrong with it yet");
    }

    @Test
    void aBuildingNobodyCanSeeIsNotDecaying() {
        // The distinction the whole feature turns on: "I cannot count it" must
        // never read as "there is nothing left of it".
        Settlement town = town();
        QuarryBridge blind = new QuarryBridge();
        blind.standing = -1;

        look(town, blind, 0);

        assertFalse(only(town).hasCensus(), "no baseline was taken");
        assertEquals(0, only(town).damage(), "and certainly no damage was invented");
    }

    @Test
    void anUnmeasuredBuildingIsLeftAlone() {
        Settlement town = town();
        only(town).setFootprint(Footprint.UNKNOWN);
        QuarryBridge bridge = new QuarryBridge();

        look(town, bridge, 0);

        assertFalse(only(town).hasCensus(), "there is no volume to count inside");
    }

    // --- damage ---

    @Test
    void blocksGoneAreDamageRecorded() {
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);

        bridge.standing = 180;   // ten percent of it gone
        look(town, bridge, 1);

        assertEquals(10, only(town).damage());
        assertTrue(only(town).isDamaged(), "ten percent is worth recording");
        assertFalse(only(town).needsRepair(), "but not worth a builder's afternoon");
    }

    @Test
    void aBlockOrTwoIsNotDamage() {
        // A census taken through a doorway somebody is standing in comes back
        // light. A building that flickered between damaged and sound every step
        // would fill the log with nothing.
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);

        bridge.standing = 197;
        look(town, bridge, 1);

        assertFalse(only(town).isDamaged(), "under the noise floor");
        assertEquals(0, only(town).damage());
    }

    @Test
    void severeDamageBooksTheWork() {
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);

        bridge.standing = 120;   // forty percent gone
        look(town, bridge, 1);

        assertTrue(only(town).needsRepair());
        assertEquals(1, town.buildQueue().size(), "a repair is booked");
        assertEquals(new SimPos(10, 64, 10), town.buildQueue().getFirst().upgradeOf(),
                "as work on the building that stands there, not a second cottage");
        assertEquals(COTTAGE.id(), town.buildQueue().getFirst().blueprintId(),
                "and to the level it already had");
    }

    @Test
    void theWorkIsPricedByWhatIsMissing() {
        assertEquals(40, RepairPlanner.repairWork(COTTAGE, 40),
                "two fifths of a hundred-unit building");
        assertTrue(RepairPlanner.repairWork(COTTAGE, 40)
                        < RepairPlanner.repairWork(COTTAGE, 90),
                "a wall gone costs less than a building flattened");
        assertTrue(RepairPlanner.repairWork(COTTAGE, 0) >= 1,
                "no repair is free");
    }

    @Test
    void oneRepairIsBookedNotOnePerStep() {
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);
        bridge.standing = 120;

        for (int step = 1; step <= 10; step++) {
            look(town, bridge, step);
        }

        assertEquals(1, town.buildQueue().size(),
                "the town books the job once, not every time it looks at the hole");
    }

    // --- getting better ---

    @Test
    void aBuildingPutRightIsCalledWholeAgain() {
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);
        bridge.standing = 120;
        look(town, bridge, 1);
        assertTrue(only(town).needsRepair());

        bridge.standing = 200;
        look(town, bridge, 2);

        assertEquals(0, only(town).damage());
        assertFalse(only(town).isDamaged());
    }

    @Test
    void aBuildingMadeBiggerIsNotPermanentlySound() {
        // An upgrade adds blocks. Keeping the old baseline would mean the new,
        // larger building could lose everything it gained before anybody noticed.
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);

        bridge.standing = 300;
        look(town, bridge, 1);

        assertEquals(300, only(town).soundCensus(), "the higher figure is the new truth");
    }

    @Test
    void aBuildingThatIsAllButGoneIsNotOverAHundredPercent() {
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);

        bridge.standing = 0;
        look(town, bridge, 1);

        assertEquals(100, only(town).damage(), "flattened is flattened");
    }
}
