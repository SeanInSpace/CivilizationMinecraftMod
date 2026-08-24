package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.MinePlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town nobody is watching must not be the only town that works.
 *
 * <p>Being watched suppressed the abstract yield outright — and that is only
 * right while the real hands are actually cutting. A camp whose trees are all
 * felled has nothing left to swing at, and a mine on flat grassland has no
 * exposed stone until a shaft is sunk, so standing in your own town stopped its
 * timber and its stone dead for as long as you were there to watch it fail.
 *
 * <p>The rule is now the one the food economy already used: the clock stands
 * aside only while real work is recent.
 */
class WatchedProductionTest {

    /** A world where somebody is always standing in the town. */
    private static final class Watched implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static SimContext at(long step) {
        return new SimContext(new Watched(), step, SimSettings.SANDBOX);
    }

    private static Settlement townWith(String blueprintId, Profession trade) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
        town.addBuilding(new Building(blueprintId, new SimPos(12, 64, 0), 1, true));
        for (int i = 0; i < 3; i++) {
            town.addResident(new Person(
                    Person.Id.random(), "Hand " + i, trade, town.centre()));
        }
        town.setStock(TownStores.WOOD, 0);
        town.setStock(TownStores.STONE, 0);
        return town;
    }

    private static Building only(Settlement town) {
        return town.buildings().getFirst();
    }

    @Test
    void aWatchedCampWithNothingToFellStillEarnsItsTimber() {
        Settlement town = townWith("kingdoms:lumber_camp", Profession.LUMBERJACK);

        LumberPlanner.advance(town, at(100));

        assertTrue(town.stores().get(TownStores.WOOD) > 0,
                "nobody has cut a real log, so the clock must still be counting");
    }

    @Test
    void aWatchedCampWhoseAxesAreSwingingIsLeftToItsOwnWork() {
        Settlement town = townWith("kingdoms:lumber_camp", Profession.LUMBERJACK);
        only(town).touchRealHarvest(100);

        LumberPlanner.advance(town, at(100));

        assertEquals(0, town.stores().get(TownStores.WOOD),
                "real axes are cutting real logs; counting them twice is the other bug");
    }

    @Test
    void theClockTakesOverAgainOnceTheRealAxesFallSilent() {
        Settlement town = townWith("kingdoms:lumber_camp", Profession.LUMBERJACK);
        only(town).touchRealHarvest(100);

        // Long enough after the last real cut that the crew has plainly stopped.
        LumberPlanner.advance(town, at(100 + LumberPlanner.WATCHED_WORK_GRACE_STEPS + 1));

        assertTrue(town.stores().get(TownStores.WOOD) > 0,
                "a crew that has downed tools must not take the town's income with it");
    }

    @Test
    void aWatchedMineWithNoExposedStoneStillEarnsIt() {
        Settlement town = townWith("kingdoms:mine", Profession.MINER);

        MinePlanner.advance(town, at(100));

        assertTrue(town.stores().get(TownStores.STONE) > 0,
                "flat grassland has no face to swing at until a shaft is sunk");
    }

    @Test
    void aWatchedMineThatIsCuttingIsLeftAlone() {
        Settlement town = townWith("kingdoms:mine", Profession.MINER);
        only(town).touchRealHarvest(100);

        MinePlanner.advance(town, at(100));

        assertEquals(0, town.stores().get(TownStores.STONE),
                "real picks are cutting real stone");
    }
}
