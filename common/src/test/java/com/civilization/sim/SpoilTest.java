package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.HaulTask;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Pockets;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.HaulPlanner;
import com.civilization.sim.settlement.LumberPlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.Stand;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.settlement.WorkArea;
import com.civilization.sim.work.Spoil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a broken block is worth, and how it gets to the shelves.
 *
 * <p>The rule under test is one sentence with no exceptions: <em>every block a
 * citizen breaks yields its material to that citizen, and what a citizen carries
 * goes into the town's supplies.</em>
 *
 * <p>It replaces an older rule that said the opposite — that wood cleared off a
 * building site was spoil, like the earth from under a floor, and that a town
 * had to raise a lumber camp before it owned any timber at all. That was wrong
 * on the plain facts of what a player watches happen: six oaks come down to
 * stand a cottage on their stumps, and six oaks' worth of timber does not stop
 * existing because of what the felling was <em>for</em>. The camp is what makes
 * timber renewable. It was never what makes a felled tree real.
 *
 * <p>Which {@code Spoil.Kind} a given block state is belongs to the platform
 * layer, and is tested there against the real registry; what a kind is worth,
 * what a pair of hands holds and how a load gets walked somewhere are all here,
 * where they can be asked without a world.
 */
class SpoilTest {

    private static Settlement town() {
        return new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
    }

    private static Building storehouseAt(int x, int z) {
        return new Building("civilization:storehouse", new SimPos(x, 64, z), 1, true);
    }

    private static Person digger(Settlement town, SimPos at) {
        Person person = new Person(Person.Id.random(), "Dora", Profession.BUILDER, at);
        town.addResident(person);
        return person;
    }

    // --- the table ---

    @Test
    void everyCategoryIsWorthWhatTheTownKeepsItAs() {
        assertEquals(TownStores.WOOD, Spoil.Kind.TIMBER.resource());
        assertEquals(TownStores.STONE, Spoil.Kind.ROCK.resource());
        assertEquals(TownStores.EARTH, Spoil.Kind.SOIL.resource());
        assertEquals(TownStores.IRON, Spoil.Kind.ORE.resource());
        assertNull(Spoil.Kind.NOTHING.resource(), "nothing is worth nothing");

        for (Spoil.Kind kind : Spoil.Kind.values()) {
            assertEquals(kind != Spoil.Kind.NOTHING, kind.isSomething());
            assertEquals(kind == Spoil.Kind.NOTHING ? 0 : 1, kind.perBlock(),
                    "one block is one unit throughout, which is the only arithmetic"
                            + " a player can do while watching");
        }
    }

    @Test
    void aLogIsTimberWhateverTreeItCameOff() {
        for (String log : List.of("oak_log", "spruce_log", "stripped_birch_log",
                "acacia_wood", "stripped_dark_oak_wood", "warped_stem", "crimson_hyphae")) {
            assertEquals(Spoil.Kind.TIMBER, Spoil.ofBlockName(log), log + " is not timber");
        }
    }

    @Test
    void stoneAndItsKinAreStone() {
        for (String rock : List.of("stone", "cobblestone", "deepslate", "cobbled_deepslate",
                "andesite", "diorite", "granite", "tuff")) {
            assertEquals(Spoil.Kind.ROCK, Spoil.ofBlockName(rock), rock + " is not stone");
        }
    }

    @Test
    void theGroundIsEarth() {
        for (String soil : List.of("dirt", "coarse_dirt", "rooted_dirt", "grass_block",
                "podzol", "mud", "sand", "gravel", "clay", "snow_block")) {
            assertEquals(Spoil.Kind.SOIL, Spoil.ofBlockName(soil), soil + " is not earth");
        }
    }

    @Test
    void ironOreIsIronAndEveryOtherVeinIsTheRockItWasIn() {
        // The town keeps one metal. A copper vein is still a pickaxe's worth of
        // stone, which is what actually comes up, so it is stone rather than
        // nothing -- otherwise a miner cutting through a seam of it works for
        // free.
        assertEquals(Spoil.Kind.ORE, Spoil.ofBlockName("iron_ore"));
        assertEquals(Spoil.Kind.ORE, Spoil.ofBlockName("deepslate_iron_ore"));
        for (String other : List.of("copper_ore", "gold_ore", "coal_ore",
                "deepslate_redstone_ore")) {
            assertEquals(Spoil.Kind.ROCK, Spoil.ofBlockName(other), other + " paid the wrong metal");
        }
    }

    @Test
    void aCrownAndACropAndAPaneAreWorthNothing() {
        // The cut-off, and it has to be a short list rather than a broad one.
        // Charging the world's whole variety into a ledger with four columns
        // only makes the ledger lie in more places -- and a town that gained
        // stone from every furnace it pulled down would be a town mining
        // furniture.
        for (String nothing : List.of("oak_leaves", "wheat", "glass", "furnace",
                "torch", "chest", "oak_planks", "poppy", "", "not_a_block")) {
            assertEquals(Spoil.Kind.NOTHING, Spoil.ofBlockName(nothing),
                    "'" + nothing + "' paid the town something");
        }
        assertEquals(Spoil.Kind.NOTHING, Spoil.ofBlockName(null));
    }

    // --- what a list of broken blocks comes to ---

    @Test
    void theUnwatchedCreditIsTheSumOfWhatWouldHaveBeenDug() {
        // The clock's half of the rule. Nobody is holding anything when a
        // building is drawn in an unloaded chunk, so the yield of every block
        // the drawing clears is summed and credited where the hole was.
        List<Spoil.Kind> cleared = List.of(
                Spoil.Kind.TIMBER, Spoil.Kind.TIMBER, Spoil.Kind.TIMBER,
                Spoil.Kind.SOIL, Spoil.Kind.SOIL,
                Spoil.Kind.NOTHING, Spoil.Kind.NOTHING);

        Map<String, Integer> tally = Spoil.tally(cleared);

        assertEquals(3, tally.get(TownStores.WOOD), "three trunks, three timber");
        assertEquals(2, tally.get(TownStores.EARTH));
        assertEquals(2, tally.size(), "and the leaves added a column of nothing");
    }

    // --- the pockets ---

    @Test
    void pocketsHoldAnArmfulAcrossEverythingInThem() {
        Pockets pockets = new Pockets();

        assertEquals(10, pockets.put(TownStores.EARTH, 10));
        assertEquals(6, pockets.put(TownStores.WOOD, 10),
                "the capacity is a pair of hands, not a shelf per material");
        assertTrue(pockets.isFull());
        assertEquals(Pockets.CAPACITY, pockets.total());
        assertEquals(0, pockets.put(TownStores.STONE, 1), "full is full");
    }

    @Test
    void whatWentInFirstIsWalkedAwayFirst() {
        // The delivery order. A load on a back is one load of one thing, so a
        // digger with earth and timber makes two trips -- and the log they have
        // been carrying since the first tree does not keep riding around because
        // they have since picked up some dirt.
        Pockets pockets = new Pockets();
        pockets.put(TownStores.WOOD, 3);
        pockets.put(TownStores.EARTH, 4);

        assertEquals(TownStores.WOOD, pockets.first());
        assertEquals(3, pockets.takeAll(TownStores.WOOD));
        assertEquals(TownStores.EARTH, pockets.first());
        assertEquals(4, pockets.takeAll(TownStores.EARTH));
        assertNull(pockets.first());
        assertTrue(pockets.isEmpty());
    }

    // --- the walk ---

    @Test
    void aDiggerWalksTheirOwnDiggingsToTheNearestStore() {
        Settlement town = town();
        Building near = storehouseAt(8, 0);
        Building far = storehouseAt(-200, 0);
        town.addBuilding(near);
        town.addBuilding(far);
        Person dora = digger(town, new SimPos(10, 64, 0));
        dora.pockets().put(TownStores.EARTH, 9);

        assertTrue(Spoil.startDelivery(town, dora), "nobody was sent");
        HaulTask errand = dora.haul();
        assertNotNull(errand);
        assertTrue(errand.isDug(), "the load came off their own back, not off a shelf");
        assertTrue(errand.isLoaded(),
                "there is no first leg: they were holding it when the errand began");
        assertEquals(near.origin(), errand.target(),
                "sent across the village to the far storehouse");
        assertTrue(dora.pockets().isEmpty(), "and it is off their books until they arrive");

        HaulPlanner.advance(town, null);

        assertNull(dora.haul(), "the errand outlived its delivery");
        assertEquals(9, near.stores().get(TownStores.EARTH), "the earth is in the store");
        assertEquals(0, far.stores().get(TownStores.EARTH));
    }

    @Test
    void aCampWithNoStoreYetPilesItOnTheGroundRatherThanLosingIt() {
        // A camp on its first morning has nowhere to put anything, and a rule
        // that waited for shelves would throw away exactly the timber that pays
        // for the first storehouse.
        Settlement town = town();
        Person dora = digger(town, new SimPos(30, 64, 30));
        dora.pockets().put(TownStores.EARTH, 5);
        int lying = town.loosePile().get(TownStores.EARTH);

        assertTrue(Spoil.startDelivery(town, dora));
        HaulPlanner.advance(town, null);

        assertEquals(lying + 5, town.loosePile().get(TownStores.EARTH),
                "set down where they stood");
    }

    @Test
    void twoMaterialsAreTwoTripsAndTheTownGetsBoth() {
        Settlement town = town();
        Building store = storehouseAt(4, 4);
        town.addBuilding(store);
        town.putAwayLoosePile();
        Person dora = digger(town, new SimPos(6, 64, 6));
        dora.pockets().put(TownStores.EARTH, 7);
        dora.pockets().put(TownStores.STONE, 2);

        assertTrue(Spoil.startDelivery(town, dora));
        assertEquals(TownStores.EARTH, dora.haul().resource(), "oldest first");
        HaulPlanner.advance(town, null);

        assertFalse(dora.pockets().isEmpty(), "the stone is still on their back");
        assertTrue(Spoil.startDelivery(town, dora), "and it wants a second trip");
        HaulPlanner.advance(town, null);

        assertEquals(7, store.stores().get(TownStores.EARTH));
        assertEquals(TownStores.FOUNDING_STONE + 2, store.stores().get(TownStores.STONE));
        assertTrue(dora.pockets().isEmpty());
        assertNull(dora.haul());
    }

    @Test
    void aFullStoreRefusesTheLastOfItRatherThanHoldingMoreThanItCan() {
        // Dug timber lands under exactly the ceiling a lumberjack's timber lands
        // under. A town's capacity cannot depend on which end of the axe the
        // felling was for -- and a founding party arrives holding more wood than
        // a storeless town can keep, so this is the case a real town meets on
        // its first afternoon in a forest.
        Settlement town = town();
        int ceiling = LumberPlanner.woodCapacity(town);
        town.setStock(TownStores.WOOD, ceiling);
        Person dora = digger(town, town.center());
        dora.pockets().put(TownStores.WOOD, 4);

        assertTrue(Spoil.startDelivery(town, dora));
        HaulPlanner.advance(town, null);

        assertEquals(ceiling, town.stores().get(TownStores.WOOD),
                "the town held more timber than it has anywhere to keep");
    }

    @Test
    void aHungryDiggerPutsTheirLoadDownRatherThanCarryingItAround() {
        // The errand is an ordinary haul, so it obeys the ordinary rule about
        // somebody too weak to carry -- and what they were holding goes back on
        // the ground where they dug it rather than being conjured away.
        Settlement town = town();
        Person dora = digger(town, new SimPos(20, 64, 20));
        dora.pockets().put(TownStores.EARTH, 6);
        Spoil.startDelivery(town, dora);
        int lying = town.loosePile().get(TownStores.EARTH);

        dora.setHunger(Person.HUNGER_SEVERE);
        HaulPlanner.advance(town, null);

        assertNull(dora.haul(), "the errand is given up");
        assertEquals(lying + 6, town.loosePile().get(TownStores.EARTH),
                "and the earth is where they were digging, not nowhere");
    }

    @Test
    void onlyTheCampsOwnTreesComeOffTheCampsBooks() {
        // Decision four, and both halves of it matter. A trunk taken out of the
        // camp's wood is one fewer trunk standing there whoever felled it and
        // whatever they felled it for -- leave it on the ledger and the clock
        // pays the town for the same tree again the moment everybody walks away.
        // A tree on a house plot in the middle of the village was never the
        // forester's, so clearing it must not make the camp any poorer: the town
        // gets the timber and the stand is untouched.
        Settlement town = town();
        Building camp = new Building("civilization:lumber_camp", new SimPos(80, 64, 0), 1, true);
        town.addBuilding(camp);
        town.setLumberArea(new WorkArea(new SimPos(80, 64, 0), 24));
        camp.setStandThousandths(20 * Stand.PER_LOG);

        assertEquals(1, Stand.fellInArea(town, new SimPos(84, 64, 4)),
                "a trunk in the camp's wood stayed on the camp's books");
        assertEquals(19, Stand.logs(camp));

        assertEquals(0, Stand.fellInArea(town, town.center()),
                "a tree in the village was charged to the forester");
        assertEquals(19, Stand.logs(camp));
    }

    @Test
    void nobodyIsSentAnywhereWithEmptyPockets() {
        Settlement town = town();
        Person dora = digger(town, town.center());

        assertFalse(Spoil.startDelivery(town, dora));
        assertNull(dora.haul());
    }
}
