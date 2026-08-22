package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Inventory;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.RaidPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the founding kit has to be enough for.
 *
 * <p>A playtest watched a fresh town raise its hall, run out of materials,
 * bootstrap a mine, staff it with nobody, and starve to death idle. Every number
 * involved was reasonable on its own and no test anywhere weighed them against
 * each other, so nothing could say whether the day-one detour was a choice the
 * town made or a hole the kit had left it in.
 *
 * <p>These are that weighing. The rule they serve: <strong>a town's first day
 * should be survivable by arithmetic, not by luck.</strong> Every figure is
 * derived from the constants rather than written down, so retuning the kit
 * retunes the tests with it — and a retune that breaks the founding party says
 * so here rather than in somebody's world.
 *
 * <p>Deliberately about the kit and not about the spiral. What a town does when
 * it runs short is the build queue's business and changes as the queue learns
 * better habits; what the settlers walk in carrying is the charter's, and has to
 * hold whatever the queue does with it.
 *
 * <p><strong>One fidelity of two.</strong> Materials are costed here against
 * {@link BuildPlanner#WOOD_PER_WORK} and {@link BuildPlanner#STONE_PER_WORK},
 * which is what an unwatched build is charged. A build somebody is standing over
 * is billed by the platform layer instead, one unit per block actually laid, and
 * that profile leans on stone where this one leans on wood. The kit is equal
 * parts of both precisely so either way of counting is covered — but only the
 * estimate can be checked from here, because this module has never heard of a
 * block.
 */
class FoundingEconomicsTest {

    private static final String HALL = "kingdoms:town_hall";
    private static final String HOUSE = "kingdoms:house";
    private static final String FARM = "kingdoms:farm";

    private static Settlement founded() {
        return new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
    }

    private static BuildingType type(String blueprintId) {
        return BuildCatalogue.DEFAULT.stream()
                .filter(candidate -> candidate.id().equals(blueprintId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the catalogue no longer knows " + blueprintId));
    }

    /**
     * Spends a town's stores on one building the way an unwatched build does:
     * a crew's worth of work at a time, wood and stone together or not at all.
     *
     * @return true if the town could pay for every step of it
     */
    private static boolean raise(Settlement town, BuildingType building, int crew) {
        for (int done = 0; done < building.workCost(); done += crew) {
            int wood = BuildPlanner.WOOD_PER_WORK * crew;
            int stone = BuildPlanner.STONE_PER_WORK * crew;
            // The last step is charged for the whole crew even when only a
            // sliver of it is wanted, which is why an awkward crew size can cost
            // more than a tidy one. The kit has to cover the awkward ones too.
            if (!town.stores().has(TownStores.WOOD, wood)
                    || !town.stores().has(TownStores.STONE, stone)) {
                return false;
            }
            town.stores().take(TownStores.WOOD, wood);
            town.stores().take(TownStores.STONE, stone);
        }
        return true;
    }

    /**
     * The population at which a town first wants a farm at all. Below it, no
     * amount of hunger makes one worth the ground — which is the whole reason
     * the founding party has to be able to eat its way there.
     */
    private static int farmingPopulation() {
        BuildingType farm = type(FARM);
        for (int population = Math.max(1, farm.minPopulation());
                population <= SimSettings.SANDBOX.maxSettlementPopulation(); population++) {
            if (farm.desiredCount(population) > 0) {
                return population;
            }
        }
        throw new AssertionError("no town below the population cap ever wants a farm");
    }

    /**
     * Builder-steps from bare ground to a standing farm, for a lone builder.
     *
     * <p>Read off the catalogue rather than listed here, because the field is not
     * the first thing a town of that size wants: it also wants its hall, a roof
     * for every family, and a granary, and every one of those outranks the farm
     * and so is built before it. Growth is added on top — the population has to
     * arrive before a farm is wanted at all.
     */
    private static int roadToTheFirstHarvest(int farmingPopulation) {
        BuildingType farm = type(FARM);
        int building = 0;
        for (BuildingType wanted : BuildCatalogue.DEFAULT) {
            if (wanted.priority() < farm.priority()
                    || farmingPopulation < wanted.minPopulation()) {
                continue;
            }
            building += wanted.desiredCount(farmingPopulation) * wanted.workCost();
        }
        int growth = (farmingPopulation - TownStores.FOUNDING_SETTLERS)
                * SimSettings.SANDBOX.stepsPerBirth();
        return building + Math.max(0, growth);
    }

    // --- what the kit buys ---

    @Test
    void aFoundedTownStartsWithTheWholeKitInItsStores() {
        Settlement town = founded();

        assertEquals(TownStores.FOUNDING_WOOD, town.woodStock(),
                "the timber the charter promises is in the town's stores");
        assertEquals(TownStores.FOUNDING_STONE, town.stoneStock(), "and so is the stone");
        assertEquals(FoodPlanner.STARTING_PROVISIONS, town.foodStock(),
                "and the granary the settlers will fall back on once their pockets empty");
    }

    @Test
    void theKitIsMeasuredInBuilderStepsAndBuysMoreThanTheFirstTwoBuildings() {
        int woodBuys = TownStores.FOUNDING_WOOD / BuildPlanner.WOOD_PER_WORK;
        int stoneBuys = TownStores.FOUNDING_STONE / BuildPlanner.STONE_PER_WORK;
        int needed = type(HALL).workCost() + type(HOUSE).workCost();

        assertTrue(woodBuys >= needed,
                "the kit's timber runs to " + woodBuys + " builder-steps and the hall and "
                        + "first house want " + needed);
        assertTrue(stoneBuys >= needed,
                "and its stone runs to " + stoneBuys + " against the same " + needed);
    }

    @Test
    void theKitPaysForTheHallAndTheFirstHouseHoweverTheWorkIsDivided() {
        // Charged crew by crew rather than in one sum, because the crew is what
        // the last step of a build is billed for. A party that puts everybody on
        // the tools must be able to finish what a party that spares one can.
        for (int crew = 1; crew <= TownStores.FOUNDING_SETTLERS; crew++) {
            Settlement town = founded();

            assertTrue(raise(town, type(HALL), crew),
                    "a founding party building " + crew + "-handed can raise its hall out "
                            + "of the kit alone");
            assertTrue(raise(town, type(HOUSE), crew),
                    "and the kit still holds the first house after it, " + crew + "-handed");
        }
    }

    // --- what the party eats ---

    @Test
    void aSettlerCanCarryTheRationsTheCharterPacks() {
        Inventory pockets = new Inventory();

        assertEquals(TownStores.FOUNDING_PROVISIONS_EACH,
                pockets.add(Foods.PROVISION, TownStores.FOUNDING_PROVISIONS_EACH),
                "every packed loaf fits in a settler's pockets — loaves that did not "
                        + "would shrink the kit silently, on the walk out");
    }

    @Test
    void aFoundingPartyArrivesBeneathRaidersNotice() {
        // The other half of why the party is the size it is. Landing at or above
        // the gate would mean a war band on day one, met by a couple of builders
        // with no walls, no watchtower and nothing to defend but a pile of logs.
        assertTrue(TownStores.FOUNDING_SETTLERS < RaidPlanner.MIN_POPULATION_FOR_RAIDS,
                "a party of " + TownStores.FOUNDING_SETTLERS + " lands under the raid gate of "
                        + RaidPlanner.MIN_POPULATION_FOR_RAIDS + ", so its first day is spent "
                        + "building rather than dying");
    }

    @Test
    void whatTheSettlersCarryOutlastsRaisingTheHallAndTheFirstHouse() {
        // The charter's own bet, and the reason the settlers are given pockets
        // at all: until the first house stands there is no pantry to fetch from,
        // so what each of them packed is what they live on.
        int packed = TownStores.FOUNDING_PROVISIONS_EACH * Foods.nutrition(Foods.PROVISION)
                / FoodPlanner.HUNGER_PER_STEP;
        int slowest = type(HALL).workCost() + type(HOUSE).workCost();

        assertTrue(packed >= slowest,
                "packed provisions feed a settler for " + packed + " steps, and the hall and "
                        + "first house take " + slowest + " even with a single builder on them");
    }

    @Test
    void theLarderOutlastsTheRoadToTheFirstHarvest() {
        int farming = farmingPopulation();
        int road = roadToTheFirstHarvest(farming);

        // Two phases, because a settler eats what they are carrying before they
        // will walk anywhere for a loaf: the founders live out of their pockets
        // first, and only then does the whole grown town live off the granary.
        // Charging the granary at the population the town has to *reach* keeps
        // the estimate on the pessimistic side — every birth along the road is
        // another mouth, and none of them arrives with pockets of its own.
        int bread = Foods.nutrition(Foods.PROVISION);
        int packed = TownStores.FOUNDING_PROVISIONS_EACH * bread / FoodPlanner.HUNGER_PER_STEP;
        int granary = FoodPlanner.STARTING_PROVISIONS * bread
                / (farming * FoodPlanner.HUNGER_PER_STEP);
        int larder = packed + granary;

        assertTrue(larder >= road,
                "pockets and granary together feed the party for " + larder + " steps, and no "
                        + "farm is even wanted before population " + farming + " — some " + road
                        + " steps of building and growing away");
    }
}
