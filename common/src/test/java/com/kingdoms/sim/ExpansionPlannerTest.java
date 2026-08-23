package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.ExpansionPlanner;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers how a full town daughters a new settlement. */
class ExpansionPlannerTest {

    private static final BuildingType HOUSE = new BuildingType("test:house", 5, 9999, 0, 0, 80, 4);

    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return 70; }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /** Cap of 8 so a small test settlement counts as "full". Raids off, determinism. */
    private static final SimSettings CAPPED = new SimSettings(100, 8, 96.0, 64, 50, false, 8);

    private static SimContext ctx() {
        return new SimContext(new QuietBridge(), 5, CAPPED);
    }

    /** A settlement at the cap: 8 people in 2 housed families of 4. */
    private static Settlement fullSettlement(Kingdom kingdom) {
        Settlement s = new Settlement(
                new Settlement.Id(new UUID(42L, 43L)), "Parent", new SimPos(0, 64, 0), 64);
        s.setCatalogue(List.of(HOUSE));
        // A chartered town: the hall stands and the dowry is banked, because
        // expansion gates on both.
        s.addBuilding(new Building("kingdoms:town_hall", new SimPos(0, 64, 5), 0, true));
        s.setStock(TownStores.WOOD, TownStores.FOUNDING_WOOD * 2);
        s.setStock(TownStores.STONE, TownStores.FOUNDING_STONE * 2);
        s.setStock(TownStores.FOOD, 400);
        for (int f = 0; f < 2; f++) {
            Household household = new Household(Household.Id.random(), "Family " + f);
            SimPos home = new SimPos(10 + f * 8, 64, 0);
            s.addBuilding(new Building(HOUSE.id(), home, 0, true));
            household.setHome(home);
            for (int m = 0; m < 4; m++) {
                Person person = new Person(
                        Person.Id.random(), "P" + f + m, Profession.FARMER, home);
                s.addResident(person);
                household.addMember(person.id());
            }
            s.addHousehold(household);
        }
        kingdom.addSettlement(s);
        return s;
    }

    @Test
    void noExpansionWithoutAStandingHall() {
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Realm", "kingdoms:norman");
        // The same full town, but the hall never rose: no hall, no daughters.
        Settlement parent = new Settlement(
                Settlement.Id.random(), "Hall-less", new SimPos(0, 64, 0), 64);
        parent.setCatalogue(List.of(HOUSE));
        parent.setStock(TownStores.WOOD, TownStores.FOUNDING_WOOD * 2);
        parent.setStock(TownStores.STONE, TownStores.FOUNDING_STONE * 2);
        parent.setStock(TownStores.FOOD, 400);
        for (int f = 0; f < 2; f++) {
            Household household = new Household(Household.Id.random(), "Family " + f);
            SimPos home = new SimPos(10 + f * 8, 64, 0);
            parent.addBuilding(new Building(HOUSE.id(), home, 0, true));
            household.setHome(home);
            for (int m = 0; m < 4; m++) {
                Person person = new Person(
                        Person.Id.random(), "P" + f + m, Profession.FARMER, home);
                parent.addResident(person);
                household.addMember(person.id());
            }
            parent.addHousehold(household);
        }
        kingdom.addSettlement(parent);

        ExpansionPlanner.advance(kingdom, ctx());

        assertEquals(1, kingdom.settlements().size(),
                "a full town with no hall pours its people into the climb, not outward");
    }

    @Test
    void aDaughterIsBornACampOfPioneersWithADowry() {
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Realm", "kingdoms:norman");
        Settlement parent = fullSettlement(kingdom);
        int parentWood = parent.woodStock();

        ExpansionPlanner.advance(kingdom, ctx());

        Settlement daughter = kingdom.settlements().stream()
                .filter(s -> s != parent).findFirst().orElseThrow();
        assertEquals(SettlementStage.CAMP, daughter.stage(),
                "daughters live the founding ladder from the bottom");
        assertTrue(daughter.residents().stream()
                        .allMatch(r -> r.profession() == Profession.PIONEER),
                "emigrants arrive as pioneers whatever their old papers said");
        assertEquals(TownStores.FOUNDING_WOOD, daughter.woodStock(),
                "the dowry timber arrives with them");
        assertEquals(parentWood - TownStores.FOUNDING_WOOD, parent.woodStock(),
                "and the parent actually paid it");
    }

    @Test
    void fullSettlementFoundsADaughter() {
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Realm", "kingdoms:norman");
        Settlement parent = fullSettlement(kingdom);

        ExpansionPlanner.advance(kingdom, ctx());

        assertEquals(2, kingdom.settlements().size(), "a daughter settlement should exist");
        assertEquals(8, kingdom.totalPopulation(), "emigration moves people, it does not create or lose them");
        assertTrue(parent.population() < 8, "the parent gave up its founding party");
        assertTrue(parent.events().stream().anyMatch(e -> e.message().contains("set out")),
                "history records the departure");
    }

    @Test
    void familiesEmigrateWholeAndIntact() {
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Realm", "kingdoms:norman");
        fullSettlement(kingdom);

        ExpansionPlanner.advance(kingdom, ctx());

        for (Settlement s : kingdom.settlements()) {
            int inFamilies = s.households().stream().mapToInt(Household::size).sum();
            assertEquals(s.population(), inFamilies,
                    s.name() + ": every resident must still belong to exactly one local family");
        }
        Settlement daughter = kingdom.settlements().stream()
                .filter(s -> !s.name().equals("Parent")).findFirst().orElseThrow();
        assertTrue(daughter.households().stream().noneMatch(Household::isHoused),
                "emigrants arrive unhoused and must wait for construction");
    }

    @Test
    void daughterIsPlantedFarFromTheParent() {
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Realm", "kingdoms:norman");
        Settlement parent = fullSettlement(kingdom);

        ExpansionPlanner.advance(kingdom, ctx());

        Settlement daughter = kingdom.settlements().stream()
                .filter(s -> s != parent).findFirst().orElseThrow();
        double distance = parent.centre().horizontalDistance(daughter.centre());
        assertTrue(distance >= ExpansionPlanner.DAUGHTER_DISTANCE - 1,
                "daughter should be planted on new land, was " + distance + " blocks away");
        assertEquals(70, daughter.centre().y(), "the site snaps to the terrain");
    }

    @Test
    void noExpansionWhileASiblingIsStillYoung() {
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Realm", "kingdoms:norman");
        fullSettlement(kingdom);
        Settlement hamlet = new Settlement(
                Settlement.Id.random(), "Hamlet", new SimPos(500, 64, 0), 64);
        hamlet.addResident(new Person(Person.Id.random(), "Lone", Profession.BUILDER, new SimPos(500, 64, 0)));
        kingdom.addSettlement(hamlet);

        ExpansionPlanner.advance(kingdom, ctx());

        assertEquals(2, kingdom.settlements().size(),
                "one frontier town at a time — no founding while the hamlet is young");
    }

    @Test
    void watchedFamiliesNeverTeleport() {
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Realm", "kingdoms:norman");
        Settlement parent = fullSettlement(kingdom);
        parent.residents().forEach(p -> p.setEmbodied(true));

        ExpansionPlanner.advance(kingdom, ctx());

        assertEquals(1, kingdom.settlements().size(),
                "people a player can currently see must not vanish across the map");
        assertEquals(8, parent.population());
    }

    @Test
    void expansionIsDeterministic() {
        Kingdom a = new Kingdom(new Kingdom.Id(new UUID(7L, 7L)), "Realm", "kingdoms:norman");
        Kingdom b = new Kingdom(new Kingdom.Id(new UUID(7L, 7L)), "Realm", "kingdoms:norman");
        Settlement pa = fullSettlement(a);
        Settlement pb = fullSettlement(b);

        assertEquals(ExpansionPlanner.siteFor(a, pa), ExpansionPlanner.siteFor(b, pb),
                "same ids and state must always pick the same site");
    }
}
