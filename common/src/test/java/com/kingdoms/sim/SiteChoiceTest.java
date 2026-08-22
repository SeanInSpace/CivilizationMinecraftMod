package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers a town refusing to build in a lake, and still building when hemmed in. */
class SiteChoiceTest {

    private static final BuildingType HOUSE =
            new BuildingType("test:house", 20, 1, 1, 0, 80, 4);

    /** Refuses whichever plots the test names, accepts the rest. */
    private static final class PickyBridge implements WorldBridge {
        final Set<SimPos> refused = new HashSet<>();
        final List<SimPos> drawn = new ArrayList<>();
        boolean refuseEverything;
        /** Whether the town is being watched. Off, plots are judged blind. */
        boolean chunksLoaded;
        int asked;
        int narrowestReachAsked = Integer.MAX_VALUE;

        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return chunksLoaded; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin, boolean surveyed, int facing) {
            drawn.add(origin);
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }

        @Override
        public boolean isSiteSuitable(SimPos plot, int radius) {
            asked++;
            narrowestReachAsked = Math.min(narrowestReachAsked, radius);
            return !refuseEverything && !refused.contains(plot);
        }
    }

    private static Settlement town() {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 256);
        s.setCatalogue(List.of(HOUSE));
        s.addResident(new Person(
                Person.Id.random(), "Builder", Profession.BUILDER, new SimPos(0, 64, 0)));
        return s;
    }

    private static SimContext ctx(WorldBridge bridge, long step) {
        return new SimContext(bridge, step, SimSettings.SANDBOX);
    }

    @Test
    void thefirstPlotIsPutToTheWorldBeforeItIsTaken() {
        Settlement s = town();
        PickyBridge bridge = new PickyBridge();

        s.step(ctx(bridge, 0));

        assertTrue(bridge.asked > 0, "a plot is judged, not simply assumed");
        assertEquals(1, s.buildQueue().size());
    }

    @Test
    void aRefusedPlotIsSkippedAndNeverOfferedAgain() {
        Settlement s = town();
        PickyBridge bridge = new PickyBridge();
        SimPos flooded = BuildPlanner.plotFor(s.centre(), 0);
        bridge.refused.add(flooded);

        s.step(ctx(bridge, 0));

        assertEquals(1, s.buildQueue().size());
        assertFalse(s.buildQueue().getFirst().origin().equals(flooded),
                "the town does not put a house in the lake it was just shown");
    }

    @Test
    void aTownHemmedInEverywhereStillBuilds() {
        Settlement s = town();
        PickyBridge bridge = new PickyBridge();
        bridge.refuseEverything = true;

        s.step(ctx(bridge, 0));

        assertEquals(1, s.buildQueue().size(),
                "an island town must still be able to build something");
        assertTrue(bridge.asked <= BuildPlanner.PLOT_ATTEMPTS + 1,
                "and it must not search forever looking for perfect ground");
    }

    @Test
    void groundIsJudgedAcrossAReachAndNotAtASingleColumn() {
        Settlement s = town();
        PickyBridge bridge = new PickyBridge();

        s.step(ctx(bridge, 0));

        assertTrue(bridge.narrowestReachAsked >= BuildPlanner.PLOT_PROBE_RADIUS,
                "a building covers far more ground than the block it is planted on, "
                        + "so the world is handed a reach to judge and not one column");
    }

    @Test
    void aLakeAcrossTheNearPlotsIsWalkedPastRatherThanBuiltIn() {
        Settlement s = town();
        PickyBridge bridge = new PickyBridge();
        // A lake is wider than a plot. The first ring holds eight of them, and a
        // town beside real water finds several in a row unfit, not just one.
        for (int index = 0; index < 8; index++) {
            bridge.refused.add(BuildPlanner.plotFor(s.centre(), index));
        }

        s.step(ctx(bridge, 0));

        assertEquals(1, s.buildQueue().size());
        assertFalse(bridge.refused.contains(s.buildQueue().getFirst().origin()),
                "the town keeps walking until it is clear of the water, "
                        + "rather than stopping at the first plot past the one it was shown");
    }

    @Test
    void groundThatTurnsOutWetOnceSeenMovesTheBuildingBeforeItIsDrawn() {
        Settlement s = town();
        PickyBridge bridge = new PickyBridge();

        s.step(ctx(bridge, 0));   // laid out blind: nothing is loaded, so nothing is known
        SimPos planned = s.buildQueue().getFirst().origin();

        // The chunks arrive and the ground under the site turns out to be water.
        // This is the gate that matters — an unwatched town chooses almost every
        // plot it will ever use before anybody can see the ground it sits on.
        bridge.chunksLoaded = true;
        bridge.refused.add(planned);
        s.step(ctx(bridge, 1));

        assertFalse(s.buildQueue().isEmpty(), "the town still means to build it");
        assertFalse(planned.equals(s.buildQueue().getFirst().origin()),
                "the site moves off the water instead of the building going up in it");
    }

    @Test
    void nothingIsEverDrawnOnGroundTheWorldCallsWet() {
        Settlement s = town();
        PickyBridge bridge = new PickyBridge();
        bridge.chunksLoaded = true;
        for (int index = 0; index < 6; index++) {
            bridge.refused.add(BuildPlanner.plotFor(s.centre(), index));
        }

        for (long step = 0; step < 80; step++) {
            s.step(ctx(bridge, step));
        }

        assertFalse(bridge.drawn.isEmpty(), "the town did build, so this proves something");
        for (SimPos origin : bridge.drawn) {
            assertFalse(bridge.refused.contains(origin),
                    "a building was drawn at " + origin + ", which stands in water");
        }
    }
}
