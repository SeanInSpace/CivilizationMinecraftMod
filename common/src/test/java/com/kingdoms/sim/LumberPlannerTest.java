package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.WorkArea;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the timber trade's simulation half: where the camp works, and how much it can store. */
class LumberPlannerTest {

    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX = new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    private static Settlement settlement() {
        return new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 256);
    }

    @Test
    void aTownWithNoCampClaimsNoWoodland() {
        Settlement s = settlement();

        LumberPlanner.advance(s, CTX);

        assertNull(s.lumberArea(), "nothing to work until a camp is built");
    }

    @Test
    void buildingTheCampClaimsTheWoodAroundIt() {
        Settlement s = settlement();
        SimPos campSite = new SimPos(30, 64, -10);
        s.addBuilding(new Building("kingdoms:lumber_camp", campSite, 0, true));

        LumberPlanner.advance(s, CTX);

        assertEquals(new WorkArea(campSite, LumberPlanner.DEFAULT_RADIUS), s.lumberArea());
        assertTrue(s.events().stream().anyMatch(e -> e.message().contains("lumber camp")),
                "and the town records it");
    }

    @Test
    void aClaimAlreadySetIsLeftAlone() {
        Settlement s = settlement();
        s.addBuilding(new Building("kingdoms:lumber_camp", new SimPos(30, 64, 0), 0, true));
        WorkArea chosen = new WorkArea(new SimPos(-200, 70, 140), 16);
        s.setLumberArea(chosen);

        LumberPlanner.advance(s, CTX);

        assertEquals(chosen, s.lumberArea(),
                "a player who pointed the camp somewhere must not be overridden");
    }

    @Test
    void theWorkAreaBoundsWhereTreesMayBeFelled() {
        WorkArea area = new WorkArea(new SimPos(0, 64, 0), 24);

        assertTrue(area.contains(new SimPos(20, 64, 0)));
        assertFalse(area.contains(new SimPos(40, 64, 0)), "trees outside the claim are safe");
    }

    @Test
    void storehousesDeepenTheTimberStores() {
        Settlement s = settlement();
        assertEquals(LumberPlanner.BASE_WOOD_STORAGE, LumberPlanner.woodCapacity(s));

        s.addBuilding(new Building("kingdoms:storehouse", new SimPos(10, 64, 0), 0, true));

        assertEquals(LumberPlanner.BASE_WOOD_STORAGE + LumberPlanner.WOOD_PER_STOREHOUSE,
                LumberPlanner.woodCapacity(s));
    }

    @Test
    void fullStoresStopTheFelling() {
        Settlement s = settlement();
        assertTrue(LumberPlanner.wantsMoreTimber(s),
                "a new town wants timber — its founding stock is well under capacity");

        s.setWoodStock(LumberPlanner.woodCapacity(s));

        assertFalse(LumberPlanner.wantsMoreTimber(s),
                "a full store means lumberjacks stop rather than clear-cut");
    }

    @Test
    void theRadiusStaysWithinItsLimits() {
        assertEquals(LumberPlanner.MIN_RADIUS, LumberPlanner.clampRadius(1));
        assertEquals(LumberPlanner.MAX_RADIUS, LumberPlanner.clampRadius(9999));
        assertEquals(24, LumberPlanner.clampRadius(24));
    }
}
