package com.kingdoms.sim;

import com.kingdoms.sim.culture.Layout;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingSizes;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import com.kingdoms.sim.world.SimWorld;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Footprint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two buildings must never be given the same ground.
 *
 * <p>This went unnoticed for a long time because nothing enforced it and nothing
 * showed it: the town map drew the plots, and they were plainly sitting inside
 * one another. What made it destructive rather than merely untidy is that raising
 * a building excavates its plot first — so a plot laid over a standing granary
 * did not squeeze in beside it, it demolished it.
 */
class PlotOverlapTest {

    /** Nothing is loaded and nobody is watching, so the town builds on the clock. */
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

    private static Settlement town(int population) {
        Settlement settlement = new Settlement(
                Settlement.Id.random(), "Test", new SimPos(0, 64, 0), 128);
        for (int i = 0; i < population; i++) {
            settlement.addResident(new Person(Person.Id.random(), "P" + i,
                    i % 3 == 0 ? Profession.BUILDER : Profession.IDLER, new SimPos(0, 64, 0)));
        }
        return settlement;
    }

    @Test
    void aplotIsRefusedWhereABuildingAlreadyStands() {
        Settlement settlement = town(4);
        BuildingType house = BuildCatalogue.DEFAULT.stream()
                .filter(type -> type.id().equals("kingdoms:house"))
                .findFirst().orElseThrow();

        settlement.addBuilding(
                new Building("kingdoms:house", new SimPos(20, 64, 0), 1, true));

        assertFalse(settlement.isPlotFree(new SimPos(20, 64, 0), house.plotSpan(), null),
                "the very same spot is plainly not free");
        assertFalse(settlement.isPlotFree(new SimPos(28, 64, 0), house.plotSpan(), null),
                "two 13-wide plots eight apart still run through each other");
        assertTrue(settlement.isPlotFree(new SimPos(40, 64, 0), house.plotSpan(), null),
                "far enough away is fine");
    }

    @Test
    void theClosestTwoBuildingsMayStandIsTwoDoorstepsTouching() {
        // The whole of what a plot gap of nought means, asserted on the sizes
        // rather than on the constant. A span is a building's walls plus the
        // apron cleared round them, so the closest the overlap check allows two
        // of anything to stand leaves one apron each between the walls and
        // nothing else -- a cottage beside a cottage and a library beside a hall
        // alike. It came out at three before, on every pair, for a bare block
        // that belonged to neither building.
        //
        // Measured against the LONGER side, because a plot is a square: a
        // building is turned to face its street and a plot that only fitted at
        // one bearing is not a plot. So a building that is not square stands
        // further off across its narrow way, and that is the square claim rather
        // than slack in this rule.
        //
        // Every kind in the table, including the two that are not square and the
        // two that declare room to spare -- a rule stated for a hand-picked list
        // is a rule that holds for the list.
        for (String one : BuildCatalogue.DEFAULT.stream().map(BuildingType::id).toList()) {
            for (String two : BuildCatalogue.DEFAULT.stream()
                    .map(BuildingType::id).toList()) {
                BuildingSizes.Size a = BuildingSizes.of(one);
                BuildingSizes.Size b = BuildingSizes.of(two);
                if (a == null || b == null) {
                    continue;   // sized by the fallback, so there is nothing to check
                }
                int spanA = BuildingSizes.plotSpanOf(one);
                int spanB = BuildingSizes.plotSpanOf(two);

                int closest = 0;
                while (BuildPlanner.plotsOverlap(new SimPos(0, 64, 0), spanA,
                        new SimPos(closest, 64, 0), spanB)) {
                    closest++;
                }
                // Walls face each other across the axis that separates them, so
                // the clear blocks between them are the gap less each half-side
                // and the block the far origin stands on.
                int between = closest
                        - Math.max(a.width(), a.depth()) / 2
                        - Math.max(b.width(), b.depth()) / 2 - 1;
                // Plus whatever room a kind declared beyond its own walls, as the
                // reach reads it -- halved, because a reach is half a span.
                int wanted = 2 * BuildingSizes.APRON
                        + (spanA / 2 - a.span() / 2) + (spanB / 2 - b.span() / 2);
                assertEquals(wanted, between,
                        one + " beside " + two + " leaves " + between
                                + " blocks of bare ground between their walls, wanting "
                                + wanted);
            }
        }
    }

    @Test
    void thePlansSeparationIsWhatTwoOrdinaryPlotsActuallyNeed() {
        // Two halves of one rule that cannot see each other. A layout may not
        // import a planner, so Layout.MIN_PLOT_SEPARATION is a number that has to
        // agree with BuildPlanner.plotsOverlap by hand -- and the last time they
        // disagreed a third of every warren's plots were thrown away for months.
        // So the agreement is asserted rather than commented.
        SimPos here = new SimPos(0, 64, 0);
        int span = Layout.DEFAULT_SPAN;
        assertFalse(BuildPlanner.plotsOverlap(here, span,
                        new SimPos(Layout.MIN_PLOT_SEPARATION, 64, 0), span),
                "the plan offers frontage at its separation and the siting code "
                        + "refuses it there, so every second offer is wasted");
        assertTrue(BuildPlanner.plotsOverlap(here, span,
                        new SimPos(Layout.MIN_PLOT_SEPARATION - 1, 64, 0), span),
                "the separation is looser than it needs to be, which is bare grass "
                        + "between every pair of walls in every town");
    }

    @Test
    void overlapIsSquareSoATurnedBuildingStillFits() {
        // Buildings are turned to face the centre, which swaps width and depth. A
        // plot that only fitted at one rotation would be a plot that fails as soon
        // as the building is placed on the other side of town.
        SimPos a = new SimPos(0, 64, 0);
        SimPos eastward = new SimPos(16, 64, 0);
        SimPos northward = new SimPos(0, 64, 16);

        assertEquals(
                BuildPlanner.plotsOverlap(a, 17, eastward, 17),
                BuildPlanner.plotsOverlap(a, 17, northward, 17),
                "a plot must foul the same at any bearing");
    }

    @Test
    void alevelledBuildingIsSizedAsWhatItGrewFrom() {
        assertEquals(
                BuildPlanner.plotSpanOf("kingdoms:house", BuildCatalogue.DEFAULT),
                BuildPlanner.plotSpanOf("kingdoms:house_l2", BuildCatalogue.DEFAULT),
                "the catalogue span already allows for the levels, so an improvement "
                        + "never has to go looking for new ground");
    }

    @Test
    void anUrgentProducerGetsTheSameGroundRulesAsAnythingElse() {
        // The lumber camp a town orders when it runs out of timber jumps the queue.
        // It used to take the next ring slot unchecked, and landed through the hall.
        Settlement settlement = town(6);
        settlement.addBuilding(new Building("kingdoms:town_hall", new SimPos(12, 64, 0), 1, true));

        assertTrue(BuildPlanner.requestProducer(settlement, "wood", 1),
                "the town has no lumber camp, so one should be ordered");

        BuildTask ordered = settlement.buildQueue().getFirst();
        assertEquals("kingdoms:lumber_camp", ordered.blueprintId());

        int span = BuildPlanner.plotSpanOf(ordered.blueprintId(), settlement.catalogue());
        // Both spans come from the catalogue. This test used to hardcode the
        // hall's, and went red the day the apron shrank and every plot with it —
        // failing on a number it had copied rather than on the rule it guards.
        int hallSpan = BuildPlanner.plotSpanOf("kingdoms:town_hall", settlement.catalogue());
        assertFalse(BuildPlanner.plotsOverlap(ordered.origin(), span,
                        new SimPos(12, 64, 0), hallSpan),
                "the camp was put at " + ordered.origin() + ", through the hall at (12, 0)");
    }

    @Test
    void stepsMayBeBuiltHardAgainstTheDoorTheyServe() {
        // Steps are a path, not a plot. Holding ground would push them away from
        // the very doorway they exist to reach.
        Settlement settlement = town(4);
        settlement.addBuilding(new Building("kingdoms:house", new SimPos(20, 64, 0), 1, true));
        settlement.addBuilding(
                new Building(BuildPlanner.ACCESS_STAIRS, new SimPos(20, 64, 3), 1, true));

        assertEquals(1, BuildPlanner.plotSpanOf(
                BuildPlanner.ACCESS_STAIRS, settlement.catalogue()));
        assertFalse(BuildPlanner.holdsGround(BuildPlanner.ACCESS_STAIRS));
        assertTrue(settlement.isPlotFree(new SimPos(40, 64, 3), 13, null),
                "a flight of steps must not reserve a plot of its own");
    }

    @Test
    void awholeTownIsPlannedWithoutOnePlotFoulingAnother() {
        // The real check: run a town until it has a good spread of buildings and
        // assert that no two of them share ground.
        SimWorld world = new SimWorld(new QuietBridge(), SimSettings.SANDBOX);
        var kingdom = new com.kingdoms.sim.kingdom.Kingdom(
                com.kingdoms.sim.kingdom.Kingdom.Id.random(), "Test", "kingdoms:norman");
        Settlement settlement = town(30);
        settlement.stores().add("wood", 100_000);
        settlement.stores().add("stone", 100_000);
        settlement.stores().add("food", 100_000);
        kingdom.addSettlement(settlement);
        world.addKingdom(kingdom);

        for (int step = 0; step < 400; step++) {
            world.step();
        }

        List<Building> raised = new ArrayList<>(settlement.buildings());
        assertTrue(raised.size() >= 8, "expected a town worth checking, got " + raised.size());

        for (int i = 0; i < raised.size(); i++) {
            for (int j = i + 1; j < raised.size(); j++) {
                Building a = raised.get(i);
                Building b = raised.get(j);
                int spanA = BuildPlanner.plotSpanOf(a.blueprintId(), settlement.catalogue());
                int spanB = BuildPlanner.plotSpanOf(b.blueprintId(), settlement.catalogue());
                assertFalse(BuildPlanner.plotsOverlap(a.origin(), spanA, b.origin(), spanB),
                        a.blueprintId() + " at " + a.origin() + " (plot " + spanA + ") runs "
                                + "through " + b.blueprintId() + " at " + b.origin()
                                + " (plot " + spanB + ")");
            }
        }
    }
}
