package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers what a settlement decides to build, and where it puts it. */
class BuildPlannerTest {

    //                                              id            work  minPop  base  per  priority  capacity
    private static final BuildingType HALL  = new BuildingType("test:hall",  10,     1,    1,   0,      100,       0);
    private static final BuildingType HOUSE = new BuildingType("test:house",  5,     1,    0,   2,       80,       4);
    private static final BuildingType TOWER = new BuildingType("test:tower", 20,    12,    0,  12,       60,       0);

    private static final List<BuildingType> CATALOGUE = List.of(HALL, HOUSE, TOWER);

    /** An unwatched town: the chunk is not loaded, so building runs on the clock. */
    private static final class LoadedBridge implements WorldBridge {
        Integer surfaceOverride = null;
        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return surfaceOverride != null ? surfaceOverride : pos.y(); }
        @Override public void materializeBlueprint(String blueprintId, SimPos origin) { }
        @Override public void log(String message) { }
    }

    private static Settlement settlement(int population) {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 16);
        s.setCatalogue(CATALOGUE);
        for (int i = 0; i < population; i++) {
            s.addResident(new Person(
                    Person.Id.random(), "Person " + i, Profession.BUILDER, new SimPos(0, 64, 0)));
        }
        return s;
    }

    // --- how many are wanted ---

    @Test
    void desiredCountScalesWithPopulation() {
        assertEquals(5, HOUSE.desiredCount(10), "one house per two residents");
        assertEquals(0, HOUSE.desiredCount(1), "integer division floors");
        assertEquals(1, HALL.desiredCount(100), "flat counts ignore population");
    }

    // --- what gets chosen ---

    @Test
    void townHallComesFirst() {
        Optional<BuildingType> choice = BuildPlanner.chooseNext(settlement(4), CATALOGUE);

        assertTrue(choice.isPresent());
        assertEquals(HALL, choice.get(), "highest priority shortfall wins");
    }

    @Test
    void housesFollowOnceTheHallExists() {
        Settlement s = settlement(4);
        s.addBuilding(new Building(HALL.id(), new SimPos(12, 64, 0), 0));

        Optional<BuildingType> choice = BuildPlanner.chooseNext(s, CATALOGUE);

        assertTrue(choice.isPresent());
        assertEquals(HOUSE, choice.get());
    }

    @Test
    void minimumPopulationGatesBuildings() {
        Settlement s = settlement(4);
        s.addBuilding(new Building(HALL.id(), new SimPos(12, 64, 0), 0));
        s.addBuilding(new Building(HOUSE.id(), new SimPos(0, 64, 12), 0));
        s.addBuilding(new Building(HOUSE.id(), new SimPos(-12, 64, 0), 0));

        // Population 4 satisfies hall (1/1) and houses (2/2); tower needs 12 residents.
        assertTrue(BuildPlanner.chooseNext(s, CATALOGUE).isEmpty(),
                "a settlement below the population gate should want nothing");
    }

    @Test
    void nothingIsWantedWhenSatisfied() {
        Settlement s = settlement(2);
        s.addBuilding(new Building(HALL.id(), new SimPos(12, 64, 0), 0));
        s.addBuilding(new Building(HOUSE.id(), new SimPos(0, 64, 12), 0));

        assertTrue(BuildPlanner.chooseNext(s, CATALOGUE).isEmpty());
    }

    @Test
    void shortfallCountsWhatIsMissing() {
        Settlement s = settlement(10);
        s.addBuilding(new Building(HOUSE.id(), new SimPos(12, 64, 0), 0));

        assertEquals(4, BuildPlanner.shortfall(s, HOUSE, 10), "wants 5, has 1");
    }

    // --- where it goes ---

    @Test
    void plotsAreDistinctAndSpiralOutward() {
        SimPos centre = new SimPos(0, 64, 0);
        Set<SimPos> seen = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            assertTrue(seen.add(BuildPlanner.plotFor(centre, i)), "plot " + i + " reused an earlier position");
        }

        double firstRing = centre.horizontalDistance(BuildPlanner.plotFor(centre, 0));
        double secondRing = centre.horizontalDistance(BuildPlanner.plotFor(centre, 8));
        assertTrue(secondRing > firstRing, "the ninth plot should sit further out");
    }

    @Test
    void outerRingsPackDenselyInsteadOfFormingSpokes() {
        SimPos centre = new SimPos(0, 64, 0);

        // Ring 0 holds 8 plots, ring 1 (radius 22) holds 13, ring 2 (radius 32)
        // holds 20 — indices 21 and 22 are neighbours inside ring 2.
        SimPos a = BuildPlanner.plotFor(centre, 21);
        SimPos b = BuildPlanner.plotFor(centre, 22);
        double gap = a.horizontalDistance(b);
        assertTrue(gap <= BuildPlanner.TARGET_PLOT_SPACING + 3,
                "neighbours in an outer ring should stay ~" + BuildPlanner.TARGET_PLOT_SPACING
                        + " apart, not drift into spokes (gap " + gap + ")");

        // The constant-8 layout put a plot at angle 0 of every ring. With
        // circumference packing plus stagger, consecutive rings' first plots no
        // longer share a ray from the centre.
        SimPos ring1First = BuildPlanner.plotFor(centre, 8);
        SimPos ring2First = BuildPlanner.plotFor(centre, 21);
        assertTrue(ring1First.z() != 0 || ring2First.z() != 0,
                "ring starts must not all line up on the same axis");
    }

    @Test
    void plotsStayAtSettlementHeight() {
        SimPos centre = new SimPos(0, 72, 0);
        assertEquals(72, BuildPlanner.plotFor(centre, 5).y());
    }

    // --- end to end through step() ---

    @Test
    void settlementQueuesItsOwnBuildWithoutBeingTold() {
        Settlement s = settlement(4);
        assertTrue(s.buildQueue().isEmpty());

        s.step(new SimContext(new LoadedBridge(), 0));

        assertEquals(1, s.buildQueue().size(), "the settlement should decide for itself");
        assertEquals(HALL.id(), s.buildQueue().getFirst().blueprintId());
    }

    @Test
    void settlementFinishesOneProjectBeforeStartingAnother() {
        Settlement s = settlement(4);
        SimContext ctx = new SimContext(new LoadedBridge(), 0);

        s.step(ctx);
        assertEquals(1, s.buildQueue().size());

        s.step(ctx);
        assertEquals(1, s.buildQueue().size(), "must not queue a second project while busy");
        assertEquals(HALL.id(), s.buildQueue().getFirst().blueprintId());
    }

    @Test
    void settlementProgressesFromHallToHouses() {
        Settlement s = settlement(4);
        SimContext ctx = new SimContext(new LoadedBridge(), 0);

        // 4 builders, hall costs 10 work: 3 steps to finish it. Planning happens at
        // the start of a step, so the next project is only chosen on the 4th.
        for (int i = 0; i < 4; i++) {
            s.step(ctx);
        }

        assertEquals(1, s.countBuildings(HALL.id()), "hall should be finished");
        assertEquals(HOUSE.id(), s.buildQueue().getFirst().blueprintId(),
                "and the settlement should have moved on to housing");
    }

    @Test
    void territoryExpandsToCoverNewBuildings() {
        // Claim deliberately tighter than the first ring of plots (radius 12), so
        // the settlement is forced to expand to build at all.
        Settlement s = new Settlement(Settlement.Id.random(), "Cramped", new SimPos(0, 64, 0), 5);
        s.setCatalogue(CATALOGUE);
        s.addResident(new Person(Person.Id.random(), "Builder", Profession.BUILDER, new SimPos(0, 64, 0)));

        s.step(new SimContext(new LoadedBridge(), 0));

        assertTrue(s.claimRadius() > 5, "claim should grow to enclose the new plot");
        assertTrue(s.contains(s.buildQueue().getFirst().origin()));
    }

    @Test
    void territoryDoesNotShrinkForNearbyPlots() {
        Settlement s = settlement(4);
        assertEquals(16, s.claimRadius());

        s.step(new SimContext(new LoadedBridge(), 0));

        assertEquals(16, s.claimRadius(), "a plot already inside the claim should not change it");
    }

    @Test
    void plotsSnapToTerrainHeight() {
        LoadedBridge bridge = new LoadedBridge();
        bridge.surfaceOverride = 80;
        Settlement s = settlement(1);

        s.step(new SimContext(bridge, 0));

        assertEquals(80, s.buildQueue().getFirst().origin().y(),
                "the plot should sit on the terrain, not at the settlement's height");
    }

    @Test
    void emptySettlementBuildsNothing() {
        Settlement s = settlement(0);

        s.step(new SimContext(new LoadedBridge(), 0));

        assertTrue(s.buildQueue().isEmpty(), "no residents means no population gate is met");
        assertFalse(s.buildings().size() > 0);
    }
}
