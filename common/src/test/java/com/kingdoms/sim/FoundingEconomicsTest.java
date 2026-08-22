package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.ExpansionPlanner;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Inventory;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.MinePlanner;
import com.kingdoms.sim.settlement.RaidPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.StagePlanner;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * that profile leans on stone where this one leans on wood. The kit carries stone
 * well past what the estimate asks for precisely so either way of counting is
 * covered — but only the estimate can be checked from here, because this module
 * has never heard of a block.
 */
class FoundingEconomicsTest {

    private static final String HALL = "kingdoms:town_hall";
    private static final String HOUSE = "kingdoms:house";
    private static final String FARM = "kingdoms:farm";

    /**
     * The stages a founding party has to get through on the kit alone.
     *
     * <p>It stops at FORTIFIED because that is where the settlement stops living
     * out of its packs: the stage's first build is the lumber camp, free, and
     * from there the town fells its own timber. Everything after is paid for by a
     * town that has an income.
     */
    private static final List<SettlementStage> FOUNDING_STAGES = List.of(
            SettlementStage.CAMP, SettlementStage.HOMESTEAD, SettlementStage.FORTIFIED);

    /**
     * How much of the kit must still be in the stores when the founding stands.
     *
     * <p>A cushion against the arithmetic, not against the ground. The estimate
     * below prices the programme exactly; what it cannot price is the unplanned
     * work a real site adds, and the biggest of those is a flight of access steps
     * — {@code BuildPlanner.requestAccessStairs} orders at least four work, which
     * a crew of four is billed sixteen timber for. Three percent of the timber
     * bill is thirteen. This margin does not cover one flight and is not pretending
     * to.
     *
     * <p>It cannot, because the kit is squeezed from above by
     * {@code LumberPlanner.BASE_WOOD_STORAGE} — see
     * {@link #theKitFitsInTheStoresAFreshTownActuallyHas} — and the programme
     * already spends 464 of the 512 a town can hold. What covers a bad site is
     * not the kit at all: it is the lumber camp FORTIFIED raises free, after
     * which the town fells its own overrun. The stone, which has no such rescue,
     * carries a far fatter reserve for exactly that reason. Widening this one
     * means widening the ceiling first.
     */
    private static final int RESERVE_PERCENT = 3;

    /**
     * The largest crew that can ever be on a founding site.
     *
     * <p>Not simply the charter's party: {@code ExpansionPlanner} sends daughters
     * out up to {@link ExpansionPlanner#FOUNDING_PARTY_MAX} strong, seeded at CAMP
     * with every emigrant a pioneer — and {@code Settlement.laboursAs} counts every
     * pioneer as a builder below VILLAGE, on the same kit. The rounding waste does
     * not climb steadily with the crew, so the sizes in between have to be priced
     * rather than reasoned about.
     */
    private static final int LARGEST_FOUNDING_CREW =
            Math.max(TownStores.FOUNDING_SETTLERS, ExpansionPlanner.FOUNDING_PARTY_MAX);

    private static Settlement founded() {
        return new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
    }

    /**
     * Every building the founding stages will raise, in the order they raise it.
     *
     * <p>Asked of {@link StagePlanner} one building at a time rather than listed
     * here, because the programme table is private and a copy of it in this file
     * is a copy that goes quietly stale the first time somebody adds a smokehouse
     * to the homestead. The settlement is a paper one: it is handed each building
     * the moment the planner names it, so the next question gets a truthful
     * answer.
     */
    private static List<BuildingType> foundingProgram() {
        Settlement paper = founded();
        List<BuildingType> program = new ArrayList<>();
        for (SettlementStage stage : FOUNDING_STAGES) {
            paper.setStage(stage);
            // Bounded so a programme that somehow never satisfies itself fails
            // this suite rather than hanging the build.
            for (int guard = 0; guard < 64 && !StagePlanner.programComplete(paper); guard++) {
                Optional<BuildingType> next = StagePlanner.nextProgramWant(paper);
                if (next.isEmpty()) {
                    break;
                }
                program.add(next.get());
                paper.addBuilding(new Building(next.get().id(), paper.centre(), 0));
            }
            assertTrue(StagePlanner.programComplete(paper),
                    "the " + stage.pretty() + " program can be satisfied by building what it "
                            + "asks for — a program that never completes is a founding that "
                            + "never advances");
        }
        assertTrue(program.size() > 1,
                "the founding stages want more than one building between them; a program this "
                        + "test cannot read is a kit this test cannot weigh");
        return program;
    }

    /**
     * The entries a founding party actually pays for.
     *
     * <p>The producers are exempt — {@code Settlement.isFreeToBuild} waives the
     * materials on anything {@link BuildPlanner#PRODUCER_OF} names, so the farm
     * and the lumber camp cost the kit nothing. Filtered by that map rather than
     * by name, so a producer added later drops out of the bill here too.
     */
    private static List<BuildingType> billed(List<BuildingType> program) {
        return program.stream()
                .filter(type -> !isFree(type))
                .toList();
    }

    private static boolean isFree(BuildingType type) {
        return BuildPlanner.PRODUCER_OF.containsValue(type.id());
    }

    /**
     * Builder-steps the stores are charged for raising these, with this many
     * hands on every site.
     *
     * <p>Not simply the sum of the work: {@code payForProgress} charges the whole
     * crew for every step including the last partial one, so a building of
     * twenty-five work costs a crew of four twenty-eight. The rounding is the
     * point — it is the difference between the kit fitting on paper and the party
     * running dry three buildings in.
     */
    private static int chargedWork(List<BuildingType> program, int crew) {
        int charged = 0;
        for (BuildingType type : billed(program)) {
            int steps = (type.workCost() + crew - 1) / crew;
            charged += steps * crew;
        }
        return charged;
    }

    private static String readable(BuildingType type) {
        return type.id().substring(type.id().indexOf(':') + 1).replace('_', ' ');
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
        if (isFree(building)) {
            // A producer is waived by isFreeToBuild, and pretending otherwise
            // would have the kit paying twice for the two buildings that exist
            // precisely so a town with nothing can still build something.
            return true;
        }
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

    // --- what the kit has to buy: the founding program ---

    /**
     * <strong>The realistic fidelity, not the ideal one.</strong> Every crew size
     * a founding party can put on a site is priced, from a lone builder up to
     * {@link #LARGEST_FOUNDING_CREW}, and the kit has to cover the dearest of
     * them — which is the charter's four, because the whole crew is charged for
     * the last partial step of every building. Costing the ideal lone builder
     * instead would have let the kit off eleven builder-steps of rounding it will
     * certainly be billed for: below VILLAGE every pioneer labours as a builder,
     * so four hands on a site is what actually happens, not a corner case.
     */
    @Test
    void theKitCoversEveryPaidBuildingOfTheFoundingProgram() {
        List<BuildingType> program = foundingProgram();

        for (int crew = 1; crew <= LARGEST_FOUNDING_CREW; crew++) {
            int work = chargedWork(program, crew);
            int wood = work * BuildPlanner.WOOD_PER_WORK;
            int stone = work * BuildPlanner.STONE_PER_WORK;
            int woodWanted = wood + wood * RESERVE_PERCENT / 100;
            int stoneWanted = stone + stone * RESERVE_PERCENT / 100;

            assertTrue(TownStores.FOUNDING_WOOD >= woodWanted,
                    "the kit carries " + TownStores.FOUNDING_WOOD + " timber and a party "
                            + "building " + crew + "-handed is billed " + wood + " for the "
                            + "camp, homestead and fortified programs — " + woodWanted
                            + " with the " + RESERVE_PERCENT + "% left over for rough ground");
            assertTrue(TownStores.FOUNDING_STONE >= stoneWanted,
                    "the kit carries " + TownStores.FOUNDING_STONE + " stone against the same "
                            + "program's " + stone + " — " + stoneWanted + " with the reserve");
        }
    }

    @Test
    void aFoundingPartyRaisesItsWholeProgramWithoutSendingAnybodyDownAMine() {
        // Spent building by building rather than summed, because that is how the
        // party finds out. The playtest this test is written from ran dry partway
        // through the granary: the kit was equal parts timber and stone while
        // building burns two to one, so the last log went with a third of the
        // stone untouched — and a town out of stone goes and starts a mine, which
        // is exactly the detour that was watched and is what this forbids.
        List<BuildingType> program = foundingProgram();

        for (int crew = 1; crew <= LARGEST_FOUNDING_CREW; crew++) {
            Settlement town = founded();
            for (BuildingType wanted : program) {
                assertTrue(raise(town, wanted, crew),
                        "a party building " + crew + "-handed pays for the " + readable(wanted)
                                + " out of the kit alone; it ran dry with " + town.woodStock()
                                + " timber and " + town.stoneStock() + " stone left");
            }
        }
    }

    @Test
    void theKitFitsInTheStoresAFreshTownActuallyHas() {
        // The ceiling on the other side of the kit, and the one that caught this
        // retune out. A town with no storehouse holds BASE_WOOD_STORAGE and no
        // more, so a party arriving at that figure arrives with its stores already
        // full: LumberPlanner.wantsMoreTimber goes false on day one and the lumber
        // camp FORTIFIED builds for exactly this purpose fells into a store with no
        // room in it. The kit has to sit under the ceiling, not on it.
        Settlement town = founded();
        int timberRoom = LumberPlanner.woodCapacity(town);
        int stoneRoom = MinePlanner.stoneCapacity(town);

        assertTrue(TownStores.FOUNDING_WOOD < timberRoom,
                "the kit's " + TownStores.FOUNDING_WOOD + " timber leaves room under a fresh "
                        + "town's ceiling of " + timberRoom + ", so its first lumberjack has "
                        + "somewhere to put what they fell");
        assertTrue(TownStores.FOUNDING_STONE < stoneRoom,
                "and its " + TownStores.FOUNDING_STONE + " stone the same, under " + stoneRoom);
    }

    @Test
    void theKitCarriesMoreTimberThanStoneBecauseBuildingBurnsMoreTimber() {
        // The shape of the mistake, guarded separately from its size. The kit was
        // 256 of each, set when a town's first act was a hall and a house, and
        // survived the founding rework unexamined — so any future retune that
        // drifts back toward equal parts is starving the party of the resource it
        // spends twice as fast, however generous the total looks.
        assertTrue(BuildPlanner.WOOD_PER_WORK > BuildPlanner.STONE_PER_WORK,
                "building still burns timber faster than stone, which is the premise this "
                        + "whole test rests on");
        assertTrue(TownStores.FOUNDING_WOOD > TownStores.FOUNDING_STONE,
                "the kit's " + TownStores.FOUNDING_WOOD + " timber outweighs its "
                        + TownStores.FOUNDING_STONE + " stone, as the "
                        + BuildPlanner.WOOD_PER_WORK + ":" + BuildPlanner.STONE_PER_WORK
                        + " burn rate demands");
    }

    // --- what the party eats ---

    @Test
    void whatTheSettlersPackedOutlastsTheRoadToTheProgramsFirstFarm() {
        // Why the provisions were left alone when the materials were retuned.
        // The old road to a field ran through the hall, three houses and a
        // granary — some two hundred builder-steps — and the larder was sized for
        // it. The stage programs put the farm fourth, so the road is a fraction of
        // what it was and the loaves in one settler's pockets cover it several
        // times over. Priced for a lone builder, the slowest a party can possibly
        // work, because a settler eats by the step whoever else is on the tools.
        List<BuildingType> program = foundingProgram();
        int road = 0;
        for (BuildingType wanted : program) {
            road += wanted.workCost();
            if (wanted.id().equals(FARM)) {
                break;
            }
        }

        int packed = TownStores.FOUNDING_PROVISIONS_EACH * Foods.nutrition(Foods.PROVISION)
                / FoodPlanner.HUNGER_PER_STEP;

        assertTrue(packed >= road,
                "packed provisions feed a settler for " + packed + " steps and the program "
                        + "reaches its first field in " + road + " even single-handed, so the "
                        + "party eats out of its pockets until the harvest without the kit "
                        + "needing another loaf");
    }

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
