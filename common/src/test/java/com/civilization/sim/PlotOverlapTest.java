package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.Layout;
import com.civilization.sim.culture.Layouts;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.BuildTask;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingSizes;
import com.civilization.sim.settlement.BuildingType;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import com.civilization.sim.world.SimWorld;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Footprint;
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
        BuildingType house = BuildCatalog.DEFAULT.stream()
                .filter(type -> type.id().equals("civilization:house"))
                .findFirst().orElseThrow();

        settlement.addBuilding(
                new Building("civilization:house", new SimPos(20, 64, 0), 1, true));

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
        for (String one : BuildCatalog.DEFAULT.stream().map(BuildingType::id).toList()) {
            for (String two : BuildCatalog.DEFAULT.stream()
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
        // Buildings are turned to face the center, which swaps width and depth. A
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
    void aleveledBuildingIsSizedAsWhatItGrewFrom() {
        assertEquals(
                BuildPlanner.plotSpanOf("civilization:house", BuildCatalog.DEFAULT),
                BuildPlanner.plotSpanOf("civilization:house_l2", BuildCatalog.DEFAULT),
                "the catalog span already allows for the levels, so an improvement "
                        + "never has to go looking for new ground");
    }

    @Test
    void anUrgentProducerGetsTheSameGroundRulesAsAnythingElse() {
        // The lumber camp a town orders when it runs out of timber jumps the queue.
        // It used to take the next ring slot unchecked, and landed through the hall.
        Settlement settlement = town(6);
        settlement.addBuilding(new Building("civilization:town_hall", new SimPos(12, 64, 0), 1, true));

        assertTrue(BuildPlanner.requestProducer(settlement, "wood", 1),
                "the town has no lumber camp, so one should be ordered");

        BuildTask ordered = settlement.buildQueue().getFirst();
        assertEquals("civilization:lumber_camp", ordered.blueprintId());

        int span = BuildPlanner.plotSpanOf(ordered.blueprintId(), settlement.catalog());
        // Both spans come from the catalog. This test used to hardcode the
        // hall's, and went red the day the apron shrank and every plot with it —
        // failing on a number it had copied rather than on the rule it guards.
        int hallSpan = BuildPlanner.plotSpanOf("civilization:town_hall", settlement.catalog());
        assertFalse(BuildPlanner.plotsOverlap(ordered.origin(), span,
                        new SimPos(12, 64, 0), hallSpan),
                "the camp was put at " + ordered.origin() + ", through the hall at (12, 0)");
    }

    @Test
    void stepsMayBeBuiltHardAgainstTheDoorTheyServe() {
        // Steps are a path, not a plot. Holding ground would push them away from
        // the very doorway they exist to reach.
        Settlement settlement = town(4);
        settlement.addBuilding(new Building("civilization:house", new SimPos(20, 64, 0), 1, true));
        settlement.addBuilding(
                new Building(BuildPlanner.ACCESS_STAIRS, new SimPos(20, 64, 3), 1, true));

        assertEquals(1, BuildPlanner.plotSpanOf(
                BuildPlanner.ACCESS_STAIRS, settlement.catalog()));
        assertFalse(BuildPlanner.holdsGround(BuildPlanner.ACCESS_STAIRS));
        assertTrue(settlement.isPlotFree(new SimPos(40, 64, 3), 13, null),
                "a flight of steps must not reserve a plot of its own");
    }

    @Test
    void everyArrangementHasGroundSomewhereForTheWidestBuildingThereIs() {
        // The question a thirty-three block plot asks of a plan pitched at eleven.
        //
        // The catalog's note on the library says a building wider than the plan's
        // own frontage "takes two frontages and the siting loop simply walks past
        // the offer it will not fit on" -- correct, and it was never asserted for
        // anything wider than twenty-five. A grand library claims thirty-three,
        // which is three frontages, and the failure mode if a plan cannot house it
        // is silent: the town wants it, walks all ninety-six offers, finds nothing,
        // and takes whatever the last one was. Nothing throws and nothing logs.
        //
        // Grown towns do site it on every arrangement. Measured on TerrainFake(11),
        // a settler arriving every eighth step to a hundred residents, sixteen
        // hundred steps, all thirteen arrangements plus the orc ring: fourteen for
        // fourteen, one grand library each. And it costs nothing in siting — the
        // distance out from the middle, grand library against the ordinary library
        // as the control:
        //
        //   warren 628/597   organic 226/204   thorp 269/275   orc_ring 237/209
        //   stronghold 132/148   stronghold_streets 218/192   ring 204/204
        //   high_street 155/312   radial_concentric 283/283   crossroads 324/324
        //   bastide 238/235   green 238/228   ring_streets 290/287
        //   crescents 458/440
        //
        // Within thirty blocks either way on twelve of fourteen, and on the
        // thirteenth (high street) the wider building landed nearer the middle
        // than the narrower one. Eight blocks more plot buys no exile at all.
        //
        // That run takes ten minutes, which is why it is not this test. This is
        // the cheap regression detector for it: the same walk, on bare geometry,
        // with no terrain and no clock.
        SimPos center = new SimPos(0, 64, 0);
        int grand = BuildingSizes.plotSpanOf("civilization:grand_library");
        assertEquals(33, grand, "the number this test is about");

        for (String id : Culture.all().stream()
                .flatMap(culture -> culture.layouts().stream()).distinct().toList()) {
            Layout plan = Layouts.of(id);
            List<SimPos> claimed = new ArrayList<>();
            // A town's worth of ordinary buildings first, claimed off the front of
            // the plan exactly as a settlement claims them -- because the offers
            // near the middle are the ones that will already be gone by the time
            // anybody wants this.
            for (int index = 0; index < 60; index++) {
                SimPos at = plan.plotFor(center, index);
                boolean free = claimed.stream().noneMatch(
                        held -> BuildPlanner.plotsOverlap(at, Layout.DEFAULT_SPAN,
                                held, Layout.DEFAULT_SPAN));
                if (free) {
                    claimed.add(at);
                }
            }
            assertTrue(claimed.size() >= 20,
                    id + " offers too little to say anything: " + claimed.size()
                            + " plots taken out of sixty");

            int offer = -1;
            for (int index = 0; index < BuildPlanner.PLOT_ATTEMPTS; index++) {
                SimPos at = plan.plotFor(center, 60 + index);
                boolean free = claimed.stream().noneMatch(
                        held -> BuildPlanner.plotsOverlap(at, grand,
                                held, Layout.DEFAULT_SPAN));
                if (free) {
                    offer = index;
                    break;
                }
            }
            assertTrue(offer >= 0,
                    id + " has no offer in " + BuildPlanner.PLOT_ATTEMPTS + " that a "
                            + grand + "-block plot fits on, so a grand library would be"
                            + " put wherever the search gave up");
        }
    }

    @Test
    void awholeTownIsPlannedWithoutOnePlotFoulingAnother() {
        // The real check: run a town until it has a good spread of buildings and
        // assert that no two of them share ground.
        SimWorld world = new SimWorld(new QuietBridge(), SimSettings.SANDBOX);
        var kingdom = new com.civilization.sim.kingdom.Kingdom(
                com.civilization.sim.kingdom.Kingdom.Id.random(), "Test", "civilization:human/norman");
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
                int spanA = BuildPlanner.plotSpanOf(a.blueprintId(), settlement.catalog());
                int spanB = BuildPlanner.plotSpanOf(b.blueprintId(), settlement.catalog());
                assertFalse(BuildPlanner.plotsOverlap(a.origin(), spanA, b.origin(), spanB),
                        a.blueprintId() + " at " + a.origin() + " (plot " + spanA + ") runs "
                                + "through " + b.blueprintId() + " at " + b.origin()
                                + " (plot " + spanB + ")");
            }
        }
    }
}
