package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.InnPlanner;
import com.kingdoms.sim.settlement.MarketPlanner;
import com.kingdoms.sim.settlement.PopulationPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The VILLAGE stage's machinery, piece by piece: couples leaving the bunks,
 * the mill's grind, the carpentry's components, the inn's caravans.
 */
class VillageLifeTest {

    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(
                String blueprintId, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX = new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    private static final SimPos BUNKHOUSE = new SimPos(10, 64, 10);
    private static final SimPos COTTAGE = new SimPos(-10, 64, -10);

    private static Settlement village() {
        Settlement s = new Settlement(Settlement.Id.random(), "Bruckdorf",
                new SimPos(0, 64, 0), 128);
        s.setCatalogue(BuildCatalogue.DEFAULT);
        s.setStage(SettlementStage.VILLAGE);
        s.setFoodStock(FoodPlanner.STARTING_PROVISIONS);
        return s;
    }

    private static Person settle(Settlement s, String name, Profession trade) {
        Person person = new Person(Person.Id.random(), name, trade, s.centre());
        s.addResident(person);
        return person;
    }

    @Test
    void aCoupleMovesOutOfTheBunksTheMomentACottageStands() {
        Settlement s = village();
        s.addBuilding(new Building("kingdoms:bunkhouse", BUNKHOUSE, 0, true));
        Household party = new Household(Household.Id.random(), "Founder");
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov"}) {
            party.addMember(settle(s, name, Profession.IDLER).id());
        }
        party.setHome(BUNKHOUSE);
        s.addHousehold(party);

        s.addBuilding(new Building("kingdoms:cottage", COTTAGE, 0, true));
        PopulationPlanner.advance(s, CTX);

        Household moved = s.households().stream()
                .filter(h -> COTTAGE.equals(h.home()))
                .findFirst()
                .orElse(null);
        assertTrue(moved != null, "somebody claims the cottage the step it stands");
        assertEquals(2, moved.size(),
                "a couple founds the cottage household, not the whole party");
        assertEquals(2, party.size(),
                "the rest stay in the bunks until more cottages rise");
        assertTrue(s.isFamilyHome(moved.home()),
                "and the new home is one a family can grow in");
        assertFalse(s.isFamilyHome(party.home()),
                "which the bunkhouse never was");
    }

    @Test
    void theMillRunsOnlyWithBothStoneAndMiller() {
        Settlement s = village();
        assertFalse(FoodPlanner.millRuns(s), "no mill, no grind");

        s.addBuilding(new Building("kingdoms:mill", COTTAGE, 0, true));
        assertFalse(FoodPlanner.millRuns(s), "a mill with nobody in it grinds nothing");

        settle(s, "Mona", Profession.MILLER);
        assertTrue(FoodPlanner.millRuns(s), "stone plus miller is a working mill");
    }

    @Test
    void carpentryComponentsFinishTheSameBuildingSooner() {
        Settlement plain = village();
        Settlement helped = village();
        for (Settlement s : new Settlement[] {plain, helped}) {
            s.setStage(SettlementStage.TOWN);
            s.stores().set(TownStores.WOOD, 4096);
            s.stores().set(TownStores.STONE, 4096);
            settle(s, "Bea", Profession.BUILDER);
        }
        helped.addBuilding(new Building("kingdoms:carpentry", COTTAGE, 0, true));
        settle(helped, "Carl", Profession.CARPENTER);

        int plainSteps = stepsToFirstBuilding(plain, "kingdoms:town_hall");
        int helpedSteps = stepsToFirstBuilding(helped, "kingdoms:town_hall");

        assertTrue(helpedSteps < plainSteps,
                "pre-cut components must beat the plain crew: "
                        + helpedSteps + " vs " + plainSteps + " steps");
    }

    private static int stepsToFirstBuilding(Settlement s, String blueprintId) {
        for (int i = 1; i <= 120; i++) {
            s.step(CTX);
            if (s.countBuildings(blueprintId) > 0) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    @Test
    void theCaravanTradesSurplusBreadForIronAndHonoursTheReserve() {
        Settlement s = village();
        s.addBuilding(new Building("kingdoms:inn", COTTAGE, 0, true));
        s.setFoodStock(MarketPlanner.RESERVE_FOOD + 30);
        SimContext caravanDay = new SimContext(new QuietBridge(),
                InnPlanner.CARAVAN_PERIOD, SimSettings.SANDBOX);

        InnPlanner.advance(s, caravanDay);

        assertEquals(MarketPlanner.RESERVE_FOOD + 30 - InnPlanner.CARAVAN_FOOD,
                s.foodStock(),
                "one wagonload leaves, and only from the surplus");
        assertEquals(InnPlanner.CARAVAN_FOOD / InnPlanner.FOOD_PER_IRON,
                s.stores().get(TownStores.IRON),
                "iron comes back at the posted rate");

        s.setFoodStock(MarketPlanner.RESERVE_FOOD);
        InnPlanner.advance(s, caravanDay);
        assertEquals(MarketPlanner.RESERVE_FOOD, s.foodStock(),
                "at the reserve the wagon leaves empty — seed corn is not for sale");
    }
}
