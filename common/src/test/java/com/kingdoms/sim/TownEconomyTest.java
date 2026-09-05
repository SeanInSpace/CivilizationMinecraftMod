package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.MarketPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SmithPlanner;
import com.kingdoms.sim.settlement.Tallies;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the forge, the market's hours, and the counters the quest board reads. */
class TownEconomyTest {

    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX =
            new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    private static Settlement townWithSmithy(int smiths) {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
        s.addBuilding(new Building("kingdoms:smith", new SimPos(4, 64, 4), 1, true));
        for (int i = 0; i < smiths; i++) {
            s.addResident(new Person(
                    Person.Id.random(), "Smith " + i, Profession.SMITH, s.center()));
        }
        return s;
    }

    // --- the forge ---

    @Test
    void theForgeTurnsIronIntoTools() {
        Settlement s = townWithSmithy(1);
        s.setStock(TownStores.IRON, 10);
        s.setStock(TownStores.WOOD, 10);

        SmithPlanner.advance(s, CTX);

        assertEquals(1, s.stores().get(TownStores.TOOLS));
        assertEquals(8, s.stores().get(TownStores.IRON), "iron is spent");
        assertEquals(9, s.stores().get(TownStores.WOOD), "and timber burns as fuel");
    }

    @Test
    void noIronMeansNoForging() {
        Settlement s = townWithSmithy(2);
        s.setStock(TownStores.WOOD, 100);

        SmithPlanner.advance(s, CTX);

        assertEquals(0, s.stores().get(TownStores.TOOLS),
                "a forge with nothing to work is just a warm room");
    }

    @Test
    void aTownWithNoSmithyMakesNothing() {
        Settlement s = new Settlement(Settlement.Id.random(), "Bare", new SimPos(0, 64, 0), 128);
        s.addResident(new Person(Person.Id.random(), "Hopeful", Profession.SMITH, s.center()));
        s.setStock(TownStores.IRON, 100);
        s.setStock(TownStores.WOOD, 100);

        SmithPlanner.advance(s, CTX);

        assertEquals(0, s.stores().get(TownStores.TOOLS), "somebody has to build the forge first");
    }

    @Test
    void toolsComeBeforeWeaponsWhichComeBeforeArmor() {
        Settlement s = townWithSmithy(1);
        s.setStock(TownStores.IRON, 1000);
        s.setStock(TownStores.WOOD, 1000);
        s.setStock(TownStores.TOOLS, SmithPlanner.MAX_TOOLS);

        SmithPlanner.advance(s, CTX);
        assertEquals(1, s.stores().get(TownStores.WEAPONS), "tools stocked, so weapons next");

        s.setStock(TownStores.WEAPONS, SmithPlanner.MAX_WEAPONS);
        SmithPlanner.advance(s, CTX);
        assertEquals(1, s.stores().get(TownStores.ARMOR));
    }

    @Test
    void aToolIsIssuedOnlyIfTheTownHasOne() {
        Settlement s = townWithSmithy(0);
        Person worker = new Person(Person.Id.random(), "Digger", Profession.BUILDER, s.center());
        s.addResident(worker);

        assertFalse(SmithPlanner.issueTool(s, worker), "an empty rack equips nobody");
        assertFalse(worker.hasTool());

        s.setStock(TownStores.TOOLS, 1);
        assertTrue(SmithPlanner.issueTool(s, worker));
        assertTrue(worker.hasTool());
        assertEquals(0, s.stores().get(TownStores.TOOLS), "and the tool leaves the rack");

        assertFalse(SmithPlanner.issueTool(s, worker), "nobody needs two");
    }

    // --- market hours ---

    @Test
    void theMarketKeepsHours() {
        assertFalse(MarketPlanner.isOpen(0), "not at dawn");
        assertTrue(MarketPlanner.isOpen(6000), "open at noon");
        assertFalse(MarketPlanner.isOpen(13000), "shut by dusk");
        assertFalse(MarketPlanner.isOpen(18000), "and firmly shut at midnight");
    }

    @Test
    void hoursWrapAroundTheDay() {
        assertTrue(MarketPlanner.isOpen(24000 + 6000), "the second day is a day too");
        assertEquals(0, MarketPlanner.untilOpen(6000), "already open");
        assertTrue(MarketPlanner.untilOpen(18000) > 0, "midnight has a wait");
        assertTrue(MarketPlanner.untilOpen(18000) < 24000, "and it is less than a whole day");
    }

    @Test
    void theSeedCornIsNotForSale() {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
        s.setStock(TownStores.FOOD, MarketPlanner.RESERVE_FOOD);

        assertEquals(0, MarketPlanner.foodForSale(s));
        assertEquals(0, MarketPlanner.sellFood(s, 10),
                "a town cannot be bought into starvation at any price");
        assertEquals(MarketPlanner.RESERVE_FOOD, s.stores().get(TownStores.FOOD));
    }

    @Test
    void sellingHandsOverFoodAndCountsTheTrade() {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
        s.setStock(TownStores.FOOD, MarketPlanner.RESERVE_FOOD + 100);

        int sold = MarketPlanner.sellFood(s, 2);

        assertEquals(2 * MarketPlanner.FOOD_PER_EMERALD, sold);
        assertEquals(MarketPlanner.RESERVE_FOOD + 100 - sold, s.stores().get(TownStores.FOOD));
        assertEquals(sold, s.tallies().get(Tallies.GOODS_TRADED));
    }

    // --- counters ---

    @Test
    void anyStatCanBeCountedWithoutBeingDeclared() {
        Tallies tallies = new Tallies();
        assertEquals(0, tallies.get("hideouts_cleared"), "unknown stats read as none");

        tallies.record("hideouts_cleared");
        tallies.record("hideouts_cleared", 2);
        assertEquals(3, tallies.get("hideouts_cleared"));

        tallies.record("a_thing_nobody_wrote_code_for", 7);
        assertEquals(7, tallies.get("a_thing_nobody_wrote_code_for"),
                "the board can show a counter this mod has never heard of");
    }

    @Test
    void statNamesReadAsProse() {
        assertEquals("Mobs slain", Tallies.pretty(Tallies.MOBS_SLAIN));
        assertEquals("Hideouts cleared", Tallies.pretty(Tallies.HIDEOUTS_CLEARED));
    }

    // --- culture ---

    @Test
    void aTownKeepsItsCulturesBeasts() {
        Culture culture = Culture.of("kingdoms:default");
        assertTrue(culture.penCount() > 0);
        assertEquals(culture.penCount(), culture.pennedAnimals().size(),
                "a pen for each kind, which is what keeps them apart");
        assertEquals(Culture.DEFAULT, Culture.of("nobody:defined-this"),
                "an unknown culture falls back rather than failing");
    }
}
