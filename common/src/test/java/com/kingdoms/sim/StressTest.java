package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimSettings;
import com.kingdoms.sim.world.SimWorld;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 0 stress check: the roadmap's "20 settlements, 500 people" benchmark,
 * with zero Minecraft. The whole architecture was chosen for scale — this proves
 * it with a number instead of an assumption.
 *
 * <p>The timing assertion is a deliberately generous ceiling so the test only
 * fails when something is catastrophically slow; the printed figures are the
 * real output.
 */
class StressTest {

    /** Twenty towns nobody is standing in — all of them run abstractly. */
    private static final class LoadedBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public void materializeBlueprint(String blueprintId, SimPos origin) { }
        @Override public void log(String message) { }
    }

    @Test
    void twentySettlementsStayCheap() {
        // Eight houses flat (no per-population scaling) caps each town at 32
        // people, so the run reaches a deterministic steady state instead of
        // growing exponentially for the whole benchmark.
        List<BuildingType> catalogue = List.of(
                new BuildingType("stress:hall", 10, 1, 1, 0, 100, 0),
                new BuildingType("stress:house", 10, 1, 8, 0, 80, 4),
                new BuildingType("stress:farm", 20, 4, 0, 5, 70, 0),
                new BuildingType("stress:tower", 30, 12, 0, 12, 60, 0));

        // SANDBOX (raids off) keeps the population landing on exactly 640 —
        // raid schedules hash random settlement ids and would vary per run.
        SimWorld world = new SimWorld(new LoadedBridge(), SimSettings.SANDBOX);
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Stressland", "kingdoms:stress");
        for (int i = 0; i < 20; i++) {
            Settlement s = new Settlement(
                    Settlement.Id.random(), "Town " + i, new SimPos(i * 1000, 64, 0), 64);
            s.setCatalogue(catalogue);
            s.setFoodStock(1_000_000);   // food never binds here; the benchmark isolates core cost
            for (int r = 0; r < 20; r++) {
                s.addResident(new Person(
                        Person.Id.random(), "Builder " + r, Profession.BUILDER, new SimPos(i * 1000, 64, 0)));
            }
            for (int r = 0; r < 5; r++) {
                s.addResident(new Person(
                        Person.Id.random(), "Settler " + r, Profession.IDLER, new SimPos(i * 1000, 64, 0)));
            }
            kingdom.addSettlement(s);
        }
        world.addKingdom(kingdom);

        // Warmup: towns build out and grow to their housing caps.
        for (int i = 0; i < 200; i++) {
            world.step();
        }

        int timedSteps = 1000;
        long start = System.nanoTime();
        for (int i = 0; i < timedSteps; i++) {
            world.step();
        }
        long elapsedNanos = System.nanoTime() - start;

        double perStepMicros = elapsedNanos / 1000.0 / timedSteps;
        int population = world.totalPopulation();
        int buildings = world.kingdoms().stream()
                .flatMap(k -> k.settlements().stream())
                .mapToInt(s -> s.buildings().size())
                .sum();

        System.out.printf(
                "STRESS: 20 settlements, %d people, %d buildings — %.1f us per sim step "
                        + "(amortized %.3f us per game tick at interval 100)%n",
                population, buildings, perStepMicros, perStepMicros / 100.0);

        assertEquals(640, population,
                "every town should grow to exactly its 32-person housing cap — determinism check");
        assertTrue(perStepMicros < 50_000,
                "a simulation step should stay far under 50ms, took " + perStepMicros + "us");
    }
}
