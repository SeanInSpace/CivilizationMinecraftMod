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

import org.junit.jupiter.api.Test;

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
        boolean refuseEverything;
        int asked;

        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin, boolean surveyed) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }

        @Override
        public boolean isSiteSuitable(SimPos plot, int radius) {
            asked++;
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

    @Test
    void thefirstPlotIsPutToTheWorldBeforeItIsTaken() {
        Settlement s = town();
        PickyBridge bridge = new PickyBridge();

        s.step(new SimContext(bridge, 0));

        assertTrue(bridge.asked > 0, "a plot is judged, not simply assumed");
        assertEquals(1, s.buildQueue().size());
    }

    @Test
    void aRefusedPlotIsSkippedAndNeverOfferedAgain() {
        Settlement s = town();
        PickyBridge bridge = new PickyBridge();
        SimPos flooded = BuildPlanner.plotFor(s.centre(), 0);
        bridge.refused.add(flooded);

        s.step(new SimContext(bridge, 0));

        assertEquals(1, s.buildQueue().size());
        assertFalse(s.buildQueue().getFirst().origin().equals(flooded),
                "the town does not put a house in the lake it was just shown");
    }

    @Test
    void aTownHemmedInEverywhereStillBuilds() {
        Settlement s = town();
        PickyBridge bridge = new PickyBridge();
        bridge.refuseEverything = true;

        s.step(new SimContext(bridge, 0));

        assertEquals(1, s.buildQueue().size(),
                "an island town must still be able to build something");
        assertTrue(bridge.asked <= BuildPlanner.PLOT_ATTEMPTS + 1,
                "and it must not search forever looking for perfect ground");
    }
}
