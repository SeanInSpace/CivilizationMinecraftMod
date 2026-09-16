package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Field;
import com.civilization.sim.settlement.FoodPlanner;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.HaulPlanner;
import com.civilization.sim.settlement.Herd;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import com.civilization.sim.world.YieldPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pens hold beasts, and the beasts are worth something.
 *
 * <p>Until this file existed the animal farm was scenery: fences, a byre and a
 * shepherd standing in the middle of it producing nothing whatever, while
 * {@code ShepherdWorker} quietly summoned a cow out of the air whenever a pen
 * looked thin. {@link Herd} is the ledger that replaced both halves of that, and
 * what is checked here is the arithmetic a town runs on where nobody is
 * watching — the half a player never sees and therefore the half that has to be
 * provable without one.
 */
class HerdTest {

    private static final String COW = "minecraft:cow";
    private static final String SHEEP = "minecraft:sheep";
    private static final String CHICKEN = "minecraft:chicken";

    /** Nobody watching, nothing wild to pick. */
    private static final class Alone implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int forageableNear(SimPos center, int radius) { return 0; }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 9, 17, 4);
        }
        @Override public void log(String message) { }
    }

    private static SimSettings shipped() {
        return SimSettings.SANDBOX.withYields(YieldPolicy.DEFAULTS);
    }

    private static SimContext ctx(int step) {
        return new SimContext(new Alone(), step, shipped());
    }

    private static Settlement town(String name) {
        Settlement town = new Settlement(
                Settlement.Id.random(), name, new SimPos(0, 64, 0), 256);
        town.setStage(SettlementStage.VILLAGE);
        town.setFoodStock(0);
        return town;
    }

    private static Building raise(Settlement town, String blueprintId, int x) {
        Building building = new Building(blueprintId, new SimPos(x, 64, 0), 0, true);
        town.addBuilding(building);
        return building;
    }

    private static Person hand(Settlement town, String name, Profession trade) {
        Person person = new Person(Person.Id.random(), name, trade, town.center());
        town.addResident(person);
        return person;
    }

    /** A compound with a shepherd on it and feed on its shelf. */
    private static Building compound(Settlement town) {
        Building pens = raise(town, "civilization:animal_farm", 30);
        Herd.stock(pens, Culture.DEFAULT);
        pens.stores().set(TownStores.GRAIN, Herd.FEED_STOCK);
        hand(town, "Cuthbert", Profession.SHEPHERD);
        return pens;
    }

    /** One step of everything the food chain does, errands included. */
    private static void chain(Settlement town, int steps, int from) {
        for (int step = from; step < from + steps; step++) {
            FoodPlanner.advance(town, ctx(step));
            HaulPlanner.advance(town, ctx(step));
        }
    }

    // --- what a compound arrives with ---

    @Test
    void aCompoundArrivesWithABreedingPairOfEachKindAndNoMore() {
        Settlement town = town("Byre");
        Building pens = raise(town, "civilization:animal_farm", 30);

        Herd.stock(pens, Culture.DEFAULT);

        for (String kind : Herd.speciesOf(Culture.DEFAULT)) {
            assertEquals(Herd.STARTER_HEAD, Herd.head(pens, kind),
                    kind + " arrives as a breeding pair; one of anything is a dead end");
        }
        assertEquals(Herd.STARTER_HEAD * Herd.speciesOf(Culture.DEFAULT).size(),
                Herd.totalHead(town));
    }

    @Test
    void stockingIsDoneOnceAndAnEmptiedPenStaysEmptied() {
        Settlement town = town("Once");
        Building pens = compound(town);
        pens.stores().set(Herd.headKey(COW), 0);

        Herd.stock(pens, Culture.DEFAULT);

        assertEquals(0, Herd.head(pens, COW),
                "a herd somebody culled to nothing is not quietly restocked by the"
                        + " bookkeeping — a shepherd has to go and bring more in");
    }

    @Test
    void aCompoundBuiltRatherThanSeededIsStockedOnItsFirstStep() {
        Settlement town = town("Raised");
        Building pens = raise(town, "civilization:animal_farm", 30);
        hand(town, "Cuthbert", Profession.SHEPHERD);
        assertFalse(Herd.isStocked(pens), "the premise");

        FoodPlanner.advance(town, ctx(1));

        assertEquals(Herd.STARTER_HEAD, Herd.head(pens, COW),
                "a town that builds a compound gets beasts in it without anybody"
                        + " having to seed the world");
    }

    // --- breeding ---

    @Test
    void aFedPairBreedsAtTheVanillaPaceAndTheGrainIsSpentOnIt() {
        Settlement town = town("Calving");
        Building pens = compound(town);
        int feedBefore = pens.stores().get(TownStores.GRAIN);

        // One pair, one shepherd, four kinds to get round. BREEDING_TICKS at the
        // shipped interval is what a calf costs a pair that is fed every step,
        // and this shepherd feeds two pens of the four on any given step — so
        // four times the ticks is comfortably one calf and nowhere near a herd.
        int steps = 4 * Herd.BREEDING_TICKS / shipped().simIntervalTicks();
        for (int step = 1; step <= steps; step++) {
            Herd.advance(town, ctx(step), false);
        }

        assertTrue(Herd.head(pens, COW) > Herd.STARTER_HEAD,
                "two fed cows and fifty minutes is a calf");
        assertTrue(pens.stores().get(TownStores.GRAIN) < feedBefore,
                "and it was paid for in grain off the compound's own shelf");
    }

    @Test
    void nothingBreedsWithNothingToFeedThem() {
        Settlement town = town("Bare");
        Building pens = compound(town);
        pens.stores().set(TownStores.GRAIN, 0);

        for (int step = 1; step <= 2000; step++) {
            Herd.advance(town, ctx(step), false);
        }

        assertEquals(Herd.STARTER_HEAD, Herd.head(pens, COW),
                "vanilla animals breed when somebody feeds them and never"
                        + " otherwise, and a compound with empty sacks is a"
                        + " compound whose herd stands exactly still");
    }

    @Test
    void nothingBreedsWithNobodyToFeedThem() {
        Settlement town = town("Unmanned");
        Building pens = raise(town, "civilization:animal_farm", 30);
        Herd.stock(pens, Culture.DEFAULT);
        pens.stores().set(TownStores.GRAIN, 512);

        for (int step = 1; step <= 2000; step++) {
            Herd.advance(town, ctx(step), false);
        }

        assertEquals(Herd.STARTER_HEAD, Herd.head(pens, COW),
                "a full trough is not a shepherd");
    }

    @Test
    void theHerdStopsAtWhatThePensWillHold() {
        Settlement town = town("Brimming");
        Building pens = compound(town);
        // Feed enough that grain is never the limit, and take the cull off the
        // table by leaving the meat safe full: what is being measured is the
        // ceiling, not what a shepherd does about reaching it.
        pens.stores().set(TownStores.MEAT, Herd.FARM_MEAT_CAP);

        for (int step = 1; step <= 20_000; step++) {
            pens.stores().set(TownStores.GRAIN, Herd.FEED_STOCK);
            Herd.advance(town, ctx(step), false);
        }

        int cap = Herd.capacity(Culture.DEFAULT, COW);
        assertEquals(Herd.HEAD_PER_PEN, cap, "one pen of cattle at two blocks a head");
        assertTrue(Herd.head(pens, COW) <= cap,
                "a pen fills and then stops; nothing in vanilla stops it, so this has to");
    }

    @Test
    void twoPensOfOneKindHoldTwiceAsMany() {
        // The mire goblins keep fowl in two of their four pens. A cap read off
        // the species rather than off the pens would have penned twice the birds
        // in half the ground.
        assertEquals(2, Herd.pensFor(Culture.GOBLIN, CHICKEN));
        assertEquals(2 * Herd.HEAD_PER_PEN, Herd.capacity(Culture.GOBLIN, CHICKEN));
        assertEquals(Herd.HEAD_PER_PEN, Herd.capacity(Culture.GOBLIN, "minecraft:pig"));
    }

    // --- the cull ---

    @Test
    void aFullPenIsCulledAndTheMeatLandsOnTheCompoundsShelf() {
        Settlement town = town("Slaughter");
        Building pens = compound(town);
        pens.stores().set(Herd.headKey(COW), Herd.capacity(Culture.DEFAULT, COW));

        assertTrue(Herd.wantsCulling(pens, Culture.DEFAULT, COW, false), "the premise");
        Herd.advance(town, ctx(1), false);

        assertEquals(Herd.capacity(Culture.DEFAULT, COW) - 1, Herd.head(pens, COW));
        assertEquals(Herd.yieldOf(COW).meatPerCull(), pens.stores().get(TownStores.MEAT),
                "one cow's worth of beef, and the table's number is what vanilla"
                        + " drops on average — the watched shepherd gathers the real"
                        + " drops instead, which is why the two cannot pay differently");
        assertEquals(Herd.yieldOf(COW).clipPerCull(), pens.stores().get(TownStores.LEATHER));
    }

    @Test
    void theCullNeverTakesTheLastPair() {
        Settlement town = town("Last");
        Building pens = compound(town);
        pens.stores().set(Herd.headKey(COW), Herd.BREEDING_PAIR);

        // Starving, which is the one thing that would otherwise open the pens.
        for (int step = 1; step <= 200; step++) {
            Herd.advance(town, ctx(step), true);
        }

        assertEquals(Herd.BREEDING_PAIR, Herd.head(pens, COW),
                "a town that ate its way down to one cow can never breed its way"
                        + " back, so the answer to the famine here is no");
    }

    @Test
    void aHungryTownEatsIntoItsHerdRatherThanWaitingForThePensToFill() {
        Settlement town = town("Lean");
        Building pens = compound(town);
        pens.stores().set(Herd.headKey(COW), 6);

        Herd.advance(town, ctx(1), true);

        assertEquals(5, Herd.head(pens, COW),
                "a herd is a larder, and a starving town opens it");
    }

    @Test
    void woolComesOffACulledSheepAndIsKept() {
        Settlement town = town("Fleece");
        Building pens = compound(town);
        pens.stores().set(Herd.headKey(SHEEP), Herd.capacity(Culture.DEFAULT, SHEEP));

        Herd.advance(town, ctx(1), false);

        assertEquals(1, pens.stores().get(TownStores.WOOL),
                "an unshorn sheep drops its fleece when it dies, so that is where"
                        + " the town's wool comes from");
        assertEquals(1, Herd.clipStock(town, TownStores.WOOL),
                "and the town's total is derived from the shelf it is sitting on");
    }

    @Test
    void aGoatIsNeverCulledBecauseVanillaGivesNothingForOne() {
        Settlement town = town("Highland");
        town.setCultureId(Culture.HIGHLAND.id());
        Building pens = raise(town, "civilization:animal_farm", 30);
        Herd.stock(pens, Culture.HIGHLAND);
        pens.stores().set(TownStores.GRAIN, Herd.FEED_STOCK);
        hand(town, "Mairi", Profession.SHEPHERD);
        pens.stores().set(Herd.headKey("minecraft:goat"),
                Herd.capacity(Culture.HIGHLAND, "minecraft:goat"));

        Herd.advance(town, ctx(1), true);

        assertEquals(Herd.capacity(Culture.HIGHLAND, "minecraft:goat"),
                Herd.head(pens, "minecraft:goat"),
                "killing a goat drops nothing at all in this game, so a ledger"
                        + " that paid mutton for one would be minting it");
        assertEquals(0, pens.stores().get(TownStores.MEAT));
    }

    // --- the meat reaching a mouth ---

    @Test
    void rawMeatIsNotFoodUntilSomebodyCooksIt() {
        Settlement town = town("Raw");
        Building pens = compound(town);
        pens.stores().set(TownStores.MEAT, 20);

        assertEquals(0, FoodPlanner.totalFood(town),
                "a town with a full meat safe and no fire owns no food whatever,"
                        + " exactly as a town with full grain sacks and no oven does");
    }

    @Test
    void meatCarriedToTheFireBecomesSomethingTheTownCanEat() {
        Settlement town = town("Roast");
        Building pens = compound(town);
        raise(town, "civilization:granary", -20);
        pens.stores().set(TownStores.MEAT, 24);

        chain(town, 200, 1);

        assertTrue(town.foodStock() > 0,
                "the shepherd carries the joint to the fire and the fire turns it"
                        + " into a meal; that is the whole of the trade's yield");
        assertTrue(FoodPlanner.totalFood(town) > 0);
    }

    @Test
    void theOvenRoastsNoFasterThanItBakes() {
        Settlement town = town("Hearth");
        Building oven = raise(town, "civilization:granary", -20);
        oven.stores().set(TownStores.MEAT, 100);

        Herd.roast(town);

        assertEquals(Herd.ROAST_PER_STEP * Herd.MEALS_PER_JOINT, town.foodStock());
        assertEquals(100 - Herd.ROAST_PER_STEP, oven.stores().get(TownStores.MEAT));
    }

    // --- the long run ---

    @Test
    void fiveHundredStepsUnwatchedNeitherStarvesTheTownNorFillsItWithCattle() {
        Settlement town = town("Steady");
        raise(town, "civilization:farm", 20);
        raise(town, "civilization:granary", -20);
        Building pens = compound(town);
        hand(town, "Ada", Profession.FARMER);
        hand(town, "Bruno", Profession.FARMER);
        town.setFoodStock(40);

        chain(town, 500, 1);

        assertTrue(FoodPlanner.totalFood(town) > 0,
                "a town with fields, a fire and a herd does not starve in five"
                        + " hundred steps of nobody looking at it");
        for (String kind : Herd.speciesOf(Culture.DEFAULT)) {
            int head = Herd.head(pens, kind);
            assertTrue(head <= Herd.capacity(Culture.DEFAULT, kind),
                    kind + " overflowed its pen: " + head + " head in ground for "
                            + Herd.capacity(Culture.DEFAULT, kind));
            assertTrue(head >= Herd.BREEDING_PAIR,
                    kind + " was eaten down to " + head + "; the breeding pair is"
                            + " supposed to be untouchable");
        }
        assertTrue(pens.stores().get(TownStores.MEAT) <= Herd.FARM_MEAT_CAP,
                "and the meat safe does not grow without bound either");
    }

    @Test
    void thePensGetTheirFeedWithoutRobbingTheOven() {
        Settlement town = town("Shared");
        Building field = raise(town, "civilization:farm", 20);
        Building oven = raise(town, "civilization:granary", -20);
        Building pens = compound(town);
        pens.stores().set(TownStores.GRAIN, 0);
        field.stores().set(TownStores.GRAIN, FoodPlanner.FARM_GRAIN_CAP);
        hand(town, "Ada", Profession.FARMER);

        chain(town, 120, 1);

        assertTrue(pens.stores().get(TownStores.GRAIN) > 0,
                "somebody walked the feed out to the pens");
        assertTrue(oven.stores().get(TownStores.GRAIN) > 0 || town.foodStock() > 0,
                "and the oven was not left empty to pay for it");
    }

    // --- the drawing, which the ledger is a ledger of ---

    @Test
    void theCompoundsGeometryIsOneNumberAndNotThree() {
        // The placer, the view layer's bounding boxes and the ledger's capacity
        // all read these. They used to be written down separately, which is how
        // the fences and the beasts come to disagree about where a pen is.
        int pens = Herd.penCount(Culture.DEFAULT);

        assertEquals(4, pens, "the default people keep four kinds and the plot fits four");
        assertEquals(Herd.compoundSize().depth(), Herd.compoundDepth(pens),
                "four pens fill the depth the catalog reserved exactly");
        assertEquals(-(Herd.compoundSize().depth() / 2) + 1, Herd.penFirstRow(pens, 0),
                "the first pen starts just inside the leading fence");
        assertEquals(Herd.penFirstRow(pens, 0) + Herd.PEN_DEPTH + 1,
                Herd.penFirstRow(pens, 1),
                "and every pen after it is a divider further on");
        assertEquals((Herd.compoundSize().width() - 2) * Herd.PEN_DEPTH
                        / Herd.BLOCKS_PER_HEAD, Herd.HEAD_PER_PEN);
    }

    @Test
    void acultureThatOutgrewItsPlotLosesTheLastKindRatherThanGettingAPenThatIsNotThere() {
        for (Culture people : Culture.all()) {
            assertTrue(Herd.kindsOf(people).size() <= Herd.penCount(people),
                    people.id() + " keeps more kinds than its compound has pens");
            assertEquals(Herd.penCount(people), Herd.kindsOf(people).size(),
                    people.id() + " has a pen with nothing in it");
        }
    }

    @Test
    void theLedgerKeepsItsBookkeepingOutOfWhatAPanelShows() {
        Settlement town = town("Tidy");
        Building pens = compound(town);
        Herd.feed(pens, Culture.DEFAULT, COW, Herd.TURNS_PER_PAIR, ctx(1));

        assertTrue(Herd.isHeadKey(Herd.headKey(COW)));
        assertEquals("cow", Herd.headWord(Herd.headKey(COW)));
        assertFalse(Herd.isInternal(Herd.headKey(COW)),
                "the head count is the one line a player wants to read");
        for (String word : pens.stores().all().keySet()) {
            assertFalse(word.startsWith("unborn:") && !Herd.isInternal(word),
                    "a fraction of a calf must not be listed as livestock");
        }
    }

    @Test
    void theFieldAndThePensAreStillToldApart() {
        // The bug this whole role table exists for: "animal_farm" ends in
        // "farm", and for most of this mod's life the compound was handed a
        // seventy-one-block ripeness ledger it has no wheat for.
        Settlement town = town("Apart");
        Building pens = compound(town);

        chain(town, 50, 1);

        assertEquals(0, pens.ripeHundredths(),
                "nothing ripens in a sheep pen");
        assertEquals(0, FoodPlanner.farmGrain(town),
                "and the sheaves on the compound's shelf are feed rather than"
                        + " harvest: the food chain counts the fields' grain and"
                        + " never the pens'");
        assertTrue(pens.stores().get(TownStores.GRAIN) > 0, "which is there all the same");
    }
}
