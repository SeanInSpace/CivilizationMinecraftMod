package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.FoodPlanner;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.HaulPlanner;
import com.civilization.sim.settlement.LumberPlanner;
import com.civilization.sim.settlement.MinePlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import com.civilization.sim.world.YieldPolicy;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one rule the whole two-fidelity design exists to keep: <strong>a watched
 * town and an unwatched one produce identical books.</strong>
 *
 * <p>It was not kept. The playtest of 2026-09-19 ran the same town for the same
 * three hundred steps with the only difference being where the player stood, and
 * got a granary of 449 with him six hundred blocks away and a granary of 144 —
 * falling, on its way to nothing — with him standing in the square. The town died
 * at step 1213 with sixty people in it. A player who walks to the world's one
 * guaranteed town and stays there watched it starve, and walking away was the
 * cure.
 *
 * <p>The cause was a rule that read well: <em>where there is a hand there is no
 * clock</em>. Under a player's eye the clock stopped crediting anything and the
 * hands were supposed to do all of it — cut every sheaf, fell every log, cut
 * every block of stone. But hands are not a second implementation of the ledger.
 * They are bodies, and a body can be out of reach, unspawned, walking, asleep,
 * boxed in by terrain, or simply not there at all because no game tick has passed
 * — which is exactly what {@code /civ step 800} does, advancing the ledger eight
 * hundred steps while the crew gets six seconds. Every one of those is a step the
 * town ate through and did not earn.
 *
 * <p>So the rule is now stated the other way round, and it is the only way it can
 * be stated and still be true: <strong>the ledger decides the step's yield, and
 * the hands spend that same budget where anybody can see them.</strong> A real
 * swing, a real axe, a real pick is booked against the step's allowance and the
 * clock credits only the remainder. Where the hands keep up the clock adds
 * nothing and every sheaf in the granary was cut by somebody; where they cannot,
 * the books still balance. Neither fidelity can outproduce the other, in either
 * direction, which is what "identical books" means.
 *
 * <p>This is the measurement. Two towns, identical down to their UUIDs, differing
 * in one bit — whether {@code playerWithin} says yes — stepped three hundred times
 * through the four ledgers a town lives on.
 */
class WatchedBooksTest {

    /**
     * One ground, one bit.
     *
     * <p>Deliberately a single class with a flag rather than the two separate
     * bridges this file's neighbours use. The experiment is only worth anything
     * if the two worlds are the same world; two hand-written doubles that were
     * meant to agree are two doubles that will eventually stop agreeing, and the
     * divergence would be read as the fault under test.
     */
    private static final class Ground implements WorldBridge {
        private final boolean anybodyHere;

        Ground(boolean anybodyHere) {
            this.anybodyHere = anybodyHere;
        }

        @Override public boolean playerWithin(SimPos pos, double radius) {
            return anybodyHere;
        }

        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int forageableNear(SimPos center, int radius) { return 0; }
        @Override public int countTreesNear(SimPos center, int radius) { return 40; }
        @Override public int countStoneBelow(SimPos c, int radius, int depth) { return 4000; }

        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 11, 11, 3);
        }

        @Override public void log(String message) { }
    }

    private static SimSettings shipped() {
        return SimSettings.SANDBOX.withYields(YieldPolicy.DEFAULTS);
    }

    /**
     * The same town twice, to the byte.
     *
     * <p>Fixed UUIDs rather than random ones: several of the planners key a
     * decision off a person's identity — who takes an errand, who is retrained —
     * and two towns whose people are differently named are two different
     * experiments.
     */
    private static Settlement town() {
        Settlement town = new Settlement(
                new Settlement.Id(UUID.fromString("0000c1f1-0000-4000-8000-000000000001")),
                "Ashmarch", new SimPos(0, 64, 0), 128);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.VILLAGE);
        town.setFoodStock(FoodPlanner.STARTING_PROVISIONS);

        // The food chain, end to end: somewhere to cut, somewhere to bake,
        // somewhere to put the bread. A town missing any of them starves in both
        // fidelities alike and would prove nothing.
        town.addBuilding(new Building("civilization:farm", new SimPos(40, 64, 0), 0, true));
        town.addBuilding(new Building("civilization:farm", new SimPos(40, 64, 32), 0, true));
        town.addBuilding(new Building("civilization:granary", new SimPos(0, 64, 12), 0, true));
        town.addBuilding(new Building("civilization:mill", new SimPos(0, 64, -12), 0, true));
        town.addBuilding(new Building("civilization:lumber_camp",
                new SimPos(-40, 64, 0), 0, true));
        town.addBuilding(new Building("civilization:mine", new SimPos(0, 64, -40), 0, true));

        String[] names = {"Ada", "Bruno", "Cass", "Dov", "Elsa", "Finn", "Gwen", "Hal"};
        Profession[] trades = {
            Profession.FARMER, Profession.FARMER, Profession.FARMER, Profession.MILLER,
            Profession.LUMBERJACK, Profession.LUMBERJACK, Profession.MINER, Profession.MINER,
        };
        for (int i = 0; i < names.length; i++) {
            town.addResident(new Person(
                    new Person.Id(new UUID(PERSON_SEED, i)), names[i], trades[i],
                    town.center()));
        }
        town.setStock(TownStores.WOOD, 0);
        town.setStock(TownStores.STONE, 0);
        return town;
    }

    /** A fixed high word for the people's UUIDs, so both towns get the same eight. */
    private static final long PERSON_SEED = 0x00C1_0000_4000_0000L;

    /**
     * The four ledgers a town lives on, as one line.
     *
     * <p>One string rather than four assertions on purpose: when this fails, what
     * is wanted is both towns' whole position side by side, because the
     * interesting failures are the ones where three books match and the fourth
     * does not.
     */
    private static String books(Settlement town) {
        return "grain " + (FoodPlanner.farmGrain(town) + FoodPlanner.bakeryGrain(town))
                + ", bread " + FoodPlanner.totalFood(town)
                + ", timber " + town.stores().get(TownStores.WOOD)
                + ", stone " + town.stores().get(TownStores.STONE);
    }

    /**
     * Everything a step does to the four ledgers, in the order
     * {@code Settlement.step} does it.
     *
     * <p>The production planners and the errands that move what they produce, and
     * not the build queue: a watched town's <em>construction</em> is hand-work on
     * purpose and always was — a building that popped into existence in front of
     * a player is the fault the whole-claim rule was written to fix, and it stays
     * fixed. What must not differ is what the town earns.
     */
    private static void produce(Settlement town, WorldBridge ground, int steps) {
        SimSettings settings = shipped();
        for (int step = 1; step <= steps; step++) {
            SimContext ctx = new SimContext(ground, step, settings);
            FoodPlanner.advance(town, ctx);
            HaulPlanner.advance(town, ctx);
            LumberPlanner.advance(town, ctx);
            MinePlanner.advance(town, ctx);
        }
    }

    /** Three hundred steps, the playtest's own run length. */
    private static final int RUN = 300;

    @Test
    void aWatchedTownAndAnUnwatchedOneKeepTheSameBooks() {
        Settlement alone = town();
        Settlement watched = town();

        produce(alone, new Ground(false), RUN);
        produce(watched, new Ground(true), RUN);

        assertEquals(books(alone), books(watched),
                "three hundred steps, one town, and the only difference is whether"
                        + " somebody was standing in it. The playtest got a granary"
                        + " of 449 alone and 144 and falling while watched;"
                        + " identical books is the whole of the doctrine.");
    }

    /**
     * And the books are not identically empty, which is the way this test would
     * otherwise pass while the town starved in both fidelities at once.
     */
    @Test
    void andTheyAreTheBooksOfATownThatIsActuallyEarning() {
        Settlement watched = town();

        produce(watched, new Ground(true), RUN);

        assertTrue(FoodPlanner.totalFood(watched) > FoodPlanner.STARTING_PROVISIONS,
                "a watched town eats for three hundred steps and must have baked"
                        + " more than it set out with — it had " + books(watched));
        assertTrue(watched.stores().get(TownStores.WOOD) > 0,
                "and felled something — " + books(watched));
        assertTrue(watched.stores().get(TownStores.STONE) > 0,
                "and cut some stone — " + books(watched));
    }

    /**
     * The playtest's own measurement, reproduced: the granary at every hundredth
     * step, watched against alone.
     *
     * <p>Kept as a test rather than as a note in the changelog because it is the
     * number this whole change is answerable to. The playtest read 0 → 157 → 329
     * → 449 with the player six hundred blocks away and 467 → 360 → 250 → 144
     * with him standing in the square; the second column is a town three hundred
     * steps into starving to death, and it did, at step 1213.
     */
    @Test
    void theGranaryRisesWithSomebodyStandingInTheTown() {
        Settlement alone = town();
        Settlement watched = town();
        StringBuilder trail = new StringBuilder();

        for (int hundred = 1; hundred <= 3; hundred++) {
            produce(alone, new Ground(false), 100);
            produce(watched, new Ground(true), 100);
            trail.append("\n  step ").append(hundred * 100)
                    .append(": alone [").append(books(alone))
                    .append("], watched [").append(books(watched)).append(']');
        }

        assertEquals(books(alone), books(watched),
                "the two columns the playtest could not make agree." + trail);
        assertTrue(FoodPlanner.totalFood(watched) > FoodPlanner.STARTING_PROVISIONS,
                "the granary of a watched town climbs, and used to fall to nothing."
                        + trail);
    }

    /**
     * The same run under {@code /civ step}, which is the fault from its other end.
     *
     * <p>A burst passes no game ticks: fifty steps land inside one tick and the
     * crew never moves between them. So a burst is precisely a watched town whose
     * hands did nothing at all, and if the clock does not cover what the hands did
     * not do, {@code /civ step 800} is a command that starves whatever it steps —
     * which is what it did, killing run A's entire population of sixty inside the
     * one command.
     *
     * <p>Nothing in {@code StepBurst} needs to know this. A step in which no hand
     * moved is already the case the reconciliation covers, so the burst inherits
     * the fix instead of carrying a second copy of it.
     */
    @Test
    void aBurstOfStepsEarnsWhatTheSameStepsEarnUnwatched() {
        Settlement alone = town();
        Settlement burst = town();

        produce(alone, new Ground(false), 800);
        // No ticks pass under a burst, so no hand ever swings: exactly a watched
        // world in which nothing embodied does anything.
        produce(burst, new Ground(true), 800);

        assertEquals(books(alone), books(burst),
                "eight hundred steps of eating must come with eight hundred steps"
                        + " of earning, whoever is looking");
    }
}
