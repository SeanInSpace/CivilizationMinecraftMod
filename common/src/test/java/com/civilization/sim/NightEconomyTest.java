package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Curfew;
import com.civilization.sim.person.NightRest;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.FoodPlanner;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.Tallies;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import com.civilization.sim.world.SimWorld;
import com.civilization.sim.world.YieldPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What it costs an unwatched town to keep the same hours a watched one does.
 *
 * <p>This is a measurement rather than an assertion, and it exists because the
 * doctrine and the arithmetic pull in opposite directions. A watched town has
 * always sent its people indoors at dusk — {@code dailyRoutine} does it, and has
 * for as long as there have been beds — while the clock that runs a town nobody is
 * standing in worked straight through the night. So a watched town was quietly
 * losing to an unwatched one on every trade with a body attached to it, which is a
 * reason to put the game down and go away rather than to play. Closing that gap
 * means idling the clock for {@link Curfew#LEAD_TICKS} plus the whole night, which
 * is 13,500 ticks of 24,000 — <strong>fifty-six per cent of every day</strong> —
 * and halving a town's timber is its own kind of bug.
 *
 * <p>So the run below is the number, taken on the same ground
 * {@link UnwatchedTradesTest} uses, over a thousand steps, with and without the
 * clock keeping hours. What the measurement decided is in the changelog; what it
 * measured is here, and re-running it is how the next person checks.
 */
class NightEconomyTest {

    /** Steps in a Minecraft day at the shipped interval: 240. */
    private static final long STEPS_PER_DAY = NightRest.DAY / SimWorld.SIM_INTERVAL_TICKS;

    /**
     * Ground with a wood and a seam on it that nobody ever visits.
     *
     * <p>The same fixture {@link UnwatchedTradesTest} runs on, with a clock bolted
     * on: {@code dayTime} advances one simulation interval per step, which is what
     * a real server does, so a thousand steps is four days and a sixth.
     */
    private static final class TimedWoods implements WorldBridge {
        long day;
        final boolean keepsHours;

        TimedWoods(boolean keepsHours) {
            this.keepsHours = keepsHours;
        }

        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int forageableNear(SimPos center, int radius) { return 0; }
        @Override public int countTreesNear(SimPos center, int radius) { return 12; }
        @Override public int countStoneBelow(SimPos c, int r, int depth) { return 2000; }

        /**
         * Dawn for ever when the clock is not keeping hours.
         *
         * <p>Which is exactly how the rest of the test suite sees the world —
         * {@code WorldBridge.dayTime} answers nought by default — so "before" here
         * is the shipped behavior and not an approximation of it.
         */
        @Override public long dayTime() {
            return keepsHours ? day : 0L;
        }

        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 11, 11, 3);
        }
        @Override public void log(String message) { }
    }

    /** What a thousand steps came to. */
    private record Run(int population, int buildings, int wood, int stone, int iron,
                       int tools, int food, int felled, int farms) {

        @Override
        public String toString() {
            return "pop " + population + ", buildings " + buildings
                    + ", timber " + wood + ", stone " + stone + ", iron " + iron
                    + ", tools " + tools + ", food " + food
                    + ", logs felled " + felled + ", fields " + farms;
        }
    }

    private static Settlement campOfFour() {
        Settlement camp = new Settlement(
                Settlement.Id.random(), "Curfewton", new SimPos(0, 64, 0), 128);
        camp.setCatalog(BuildCatalog.DEFAULT);
        camp.setStage(SettlementStage.CAMP);
        camp.setFoodStock(FoodPlanner.STARTING_PROVISIONS);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov"}) {
            camp.addResident(new Person(Person.Id.random(), name, Profession.PIONEER,
                    new SimPos(0, 64, 0)));
        }
        return camp;
    }

    private static Run thousandSteps(boolean keepsHours) {
        Settlement camp = campOfFour();
        SimSettings settings = SimSettings.SANDBOX.withYields(YieldPolicy.DEFAULTS);
        TimedWoods world = new TimedWoods(keepsHours);
        for (int step = 1; step <= 1000; step++) {
            world.day = (long) step * SimWorld.SIM_INTERVAL_TICKS;
            camp.step(new SimContext(world, step, settings));
        }
        return new Run(camp.population(), camp.buildings().size(), camp.woodStock(),
                camp.stoneStock(),
                camp.stores().get(com.civilization.sim.settlement.TownStores.IRON),
                camp.stores().get(com.civilization.sim.settlement.TownStores.TOOLS),
                camp.foodStock(), camp.tallies().get(Tallies.TREES_FELLED),
                camp.countBuildings("civilization:farm"));
    }

    /**
     * The measurement, and the one thing it is allowed to assert: the town lives.
     *
     * <p>A curfew that starves towns is worse than the nights it was meant to fix,
     * so what is checked is survival and growth rather than a particular figure —
     * the figures move whenever anything about the trades does, and a test that
     * pinned them would be a test somebody deletes. The numbers themselves are
     * printed into the failure message, so a run that does break this reports the
     * whole census rather than one boolean.
     */
    @Test
    void aTownThatKeepsHoursStillGrows() {
        Run before = thousandSteps(false);
        Run after = thousandSteps(true);

        String report = "\n  1000 unwatched steps (" + STEPS_PER_DAY + " steps a day,"
                + " so 4.2 days)\n"
                + "  working round the clock: " + before + "\n"
                + "  keeping the curfew:      " + after + "\n"
                + "  the curfew idles " + (Curfew.LEAD_TICKS + (NightRest.DAY - NightRest.DUSK))
                + " of " + NightRest.DAY + " ticks, which is "
                + (100 * (Curfew.LEAD_TICKS + NightRest.DAY - NightRest.DUSK) / NightRest.DAY)
                + "% of every day\n";

        assertTrue(after.population() > 4,
                "a town that keeps hours still takes in people." + report);
        assertTrue(after.buildings() >= before.buildings() / 2,
                "and still builds, rather than stalling on the first thing it cannot"
                        + " pay for." + report);
        assertTrue(after.farms() >= before.farms(),
                "and has as many fields dug as the town that never slept — a field is"
                        + " earth rather than timber, so the curfew must not cost one."
                        + report);
        assertTrue(after.food() > 0,
                "and is not starving, which is the one outcome that would send this"
                        + " whole change back." + report);
        // Printed on success too, because the point of the run is the number.
        System.out.println(report);
    }

    /**
     * That the idling actually idles something.
     *
     * <p>Worth its own test, because the obvious failure of the whole change is that
     * it does nothing: {@code WorldBridge.dayTime} answers nought by default, which
     * reads as permanent dawn, so a wiring mistake would leave the clock working
     * through the night and every other test in this suite would still pass.
     */
    @Test
    void theCurfewCostsTheUnwatchedTownSomething() {
        Run before = thousandSteps(false);
        Run after = thousandSteps(true);
        assertTrue(after.felled() < before.felled(),
                "a town that stops felling at dusk has to cut less timber than one"
                        + " that never stops: " + after.felled() + " against "
                        + before.felled());
    }
}
