package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a step of a settled town costs, on ground that is actually rough.
 *
 * <p>Written for a crash. A playtest ran {@code /civ step 800} against a
 * dedicated server holding a town of forty-seven people and the watchdog brought
 * the server down — "a single server tick took 60.00 seconds". The stack was all
 * layout and all of it under one call:
 *
 * <pre>
 * Ways.pointToSegment ← TownPlan$Street.touches ← PlannedLayout.fits
 *   ← finish ← planFor ← plotFor ← Settlement.chooseSite
 *   ← relocateIfUnsuitable ← advanceBuildQueue ← Settlement.step
 * </pre>
 *
 * <p>Eight hundred steps in sixty seconds is about seventy-five milliseconds a
 * step, which at the natural one step a second is a seventy-five millisecond
 * server hitch every second, for ever, on a town that finished settling hundreds
 * of steps earlier. The cause was not the relocation check and not the street
 * geometry: it was that {@code PlannedLayout.planFor} cached the plan it lays at
 * {@code PLAN_SIZE} and then re-grew it from scratch, street by street, on every
 * ask past that size — and an ask past that size is what a town with a moved-on
 * plot cursor makes constantly, as that method's own note explains two
 * paragraphs above the line that assumed it was rare.
 *
 * <p>So this measures it. The ceiling is deliberately far above the fixed cost
 * of a step and far below the fault: a step that has to regrow a whole layout
 * cannot come in under it on any machine, and a step that does not cannot exceed
 * it on any machine slow enough to be worth shipping to. The printed figure is
 * the real output and the number to read.
 *
 * <p>On {@link RecordedTerrain} rather than {@link TerrainFake}, for the reason
 * {@code RealTerrainRoadsTest} gives at length: the synthetic ground is too
 * smooth to make a town's siting work hard, and a measurement taken on it would
 * certify the fault rather than catch it.
 */
class GrownTownStepCostTest {

    private static final SimPos CENTER = new SimPos(16, 64, 80);

    /** Steps to grow the town before anything is timed. */
    private static final int GROW = 500;

    /** Steps timed, after the town has settled. */
    private static final int MEASURE = 200;

    /**
     * The most a settled town's step may cost, in milliseconds.
     *
     * <p>Five. The fault measured seventy-five on the reporter's machine, and a
     * step that does no layout work at all measures well under one here — so
     * there is more than an order of magnitude of daylight on either side and
     * this does not become a test about how fast the machine running it is.
     */
    private static final double MOST_MS_PER_STEP = 5.0;

    private static Settlement grow(RecordedTerrain ground) {
        Settlement town = new Settlement(Settlement.Id.random(), "Costly", CENTER, 512);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        town.setCultureId("civilization:human/vale");
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(
                    Person.Id.random(), name, Profession.PIONEER, CENTER));
        }
        for (int step = 1; step <= GROW; step++) {
            // Stocked by hand for the same reason RealTerrainRoadsTest stocks it:
            // what is measured here is the cost of a step, not the pace of a
            // claim's timber.
            town.stores().add(TownStores.WOOD, 8);
            town.stores().add(TownStores.STONE, 6);
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }
        return town;
    }

    @Test
    void aSettledTownDoesNotRegrowItsWholeLayoutEveryStep() {
        RecordedTerrain ground = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        Settlement town = grow(ground);

        long start = System.nanoTime();
        for (int step = GROW + 1; step <= GROW + MEASURE; step++) {
            town.stores().add(TownStores.WOOD, 8);
            town.stores().add(TownStores.STONE, 6);
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }
        double msPerStep = (System.nanoTime() - start) / 1_000_000.0 / MEASURE;

        System.out.printf(
                "STEP COST %d people, %d buildings, plot cursor past the settled plan:"
                        + " %.2f ms/step over %d steps%n",
                town.population(), town.buildings().size(), msPerStep, MEASURE);

        assertTrue(msPerStep < MOST_MS_PER_STEP,
                "a settled town's step took " + String.format("%.2f", msPerStep)
                        + " ms, over the " + MOST_MS_PER_STEP + " ms ceiling —"
                        + " something is re-planning the layout per step again");
    }
}
