package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.MinePlanner;
import com.kingdoms.sim.settlement.Seam;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seam's own arithmetic: what is under a mine, and what happens when it
 * runs out.
 *
 * <p>The timber trade's twin, minus the regrowth — which is the interesting
 * half. A camp felled bare puts its own saplings back; a mine cut out is a mine
 * cut out, and a town that wants more stone after that has to find more ground.
 */
class SeamTest {

    /** Nobody anywhere, on ground that can be read. */
    private static final class Alone implements WorldBridge {
        int stone = 2000;
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int countStoneBelow(SimPos c, int radius, int depth) { return stone; }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /** The same ground with somebody standing on it. */
    private static final class Standing implements WorldBridge {
        int stone = 2000;
        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int countStoneBelow(SimPos c, int radius, int depth) { return stone; }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimSettings SHIPPED = SimSettings.SANDBOX;

    private static Settlement mineTown(int miners) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Stonebank", new SimPos(0, 64, 0), 128);
        town.addBuilding(new Building("kingdoms:mine", new SimPos(12, 64, 0), 0, true));
        for (int i = 0; i < miners; i++) {
            town.addResident(new Person(Person.Id.random(), "Pick " + i,
                    Profession.MINER, town.center()));
        }
        town.setStock(TownStores.STONE, 0);
        town.setStock(TownStores.IRON, 0);
        return town;
    }

    private static Building only(Settlement town) {
        return town.buildings().getFirst();
    }

    // --- the ledger ---

    @Test
    void cuttingTakesNoMoreThanIsThere() {
        Building mine = only(mineTown(0));
        Seam.recount(mine, 10);

        assertEquals(4, Seam.cut(mine, 4));
        assertEquals(6, Seam.cut(mine, 99), "and the last of it is the last of it");
        assertEquals(0, Seam.remaining(mine));
        assertTrue(Seam.isExhausted(mine));
    }

    @Test
    void anUncountedSeamIsNotAnExhaustedOne() {
        Building mine = only(mineTown(0));

        assertTrue(!Seam.isCounted(mine));
        assertTrue(!Seam.isExhausted(mine),
                "a mine nobody has ever walked to is of unknown worth, not spent");
    }

    // --- the two fidelities ---

    @Test
    void anUnwatchedMineCutsAtItsMinersOwnPace() {
        Settlement town = mineTown(2);

        MinePlanner.advance(town, new SimContext(new Alone(), 1, SHIPPED));

        assertEquals(2 * Seam.STONE_PER_MINER_PER_STEP,
                town.stores().get(TownStores.STONE));
        assertEquals(2000 - 2 * Seam.STONE_PER_MINER_PER_STEP, Seam.remaining(only(town)),
                "block for block out of the workings");
    }

    @Test
    void theOreComesUpWithTheRockAtTheRateItAlwaysHas() {
        Settlement town = mineTown(1);
        Alone world = new Alone();

        for (int step = 1; step <= 10; step++) {
            MinePlanner.advance(town, new SimContext(world, step, SHIPPED));
        }

        assertEquals(10 * Seam.STONE_PER_MINER_PER_STEP,
                town.stores().get(TownStores.STONE));
        assertEquals(10, town.stores().get(TownStores.IRON),
                "one ingot's worth per " + Seam.STONE_PER_IRON + " blocks, which is"
                        + " the ratio the mine ran at before the seam existed");
    }

    @Test
    void aWatchedMineIsLeftToItsPicks() {
        Settlement town = mineTown(2);

        for (int step = 1; step <= 20; step++) {
            MinePlanner.advance(town, new SimContext(new Standing(), step, SHIPPED));
        }

        assertEquals(0, town.stores().get(TownStores.STONE));
        assertEquals(2000, Seam.remaining(only(town)), "and the rock is all still there");
    }

    @Test
    void aRealDigSpendsTheSameBlockTheClockWouldHave() {
        Building mine = only(mineTown(0));
        Seam.recount(mine, 100);

        Seam.cut(mine, 1);

        assertEquals(99, Seam.remaining(mine));
    }

    @Test
    void walkingUpToAMineCountsWhatIsActuallyDownThere() {
        Settlement town = mineTown(1);
        Standing arriving = new Standing();
        arriving.stone = 40;
        Seam.recount(only(town), 5000);

        MinePlanner.advance(town, new SimContext(arriving, 1, SHIPPED));

        assertEquals(40, Seam.remaining(only(town)),
                "the rock is its own truth, and the count on arrival wins");
    }

    // --- running out ---

    @Test
    void aMineThatIsCutOutStopsAndSaysSo() {
        Settlement town = mineTown(1);
        Alone world = new Alone();
        MinePlanner.advance(town, new SimContext(world, 1, SHIPPED));
        Seam.recount(only(town), Seam.STONE_PER_MINER_PER_STEP);

        MinePlanner.advance(town, new SimContext(world, 2, SHIPPED));
        int held = town.stores().get(TownStores.STONE);
        for (int step = 3; step <= 20; step++) {
            MinePlanner.advance(town, new SimContext(world, step, SHIPPED));
        }

        assertTrue(Seam.isExhausted(only(town)), "the last of the rock is up");
        assertEquals(held, town.stores().get(TownStores.STONE),
                "and a mine with nothing left in it cuts nothing more, forever —"
                        + " there is no regrowth down here");
        assertTrue(town.events().stream().anyMatch(e -> e.message().contains("cut out")),
                "and the town's own history says why the stone stopped");
    }
}
