package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalog;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingRole;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.ForesterStand;
import com.kingdoms.sim.settlement.Founding;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.settlement.WorkArea;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wood a seeded town's forester has supposedly been working.
 *
 * <p>The fault this pins is a whole class of quiet nonsense: a town written into
 * the world complete, with a lumber camp, a lumberjack, and not one tree inside
 * the claim. Nobody noticed for as long as the abstract clock was conjuring
 * timber on the side; the moment a world turns that off, a seeded town has no
 * wood at all and never will.
 *
 * <p>What is testable without a world is the layout — how wide the claim has to
 * be, and which squares of it a tree may stand on. Which species grows in which
 * biome, and whether a given square is lake or cliff, is world work and lives in
 * {@code Woodland}; there is a manual check for it in the changelog entry.
 */
class ForesterStandTest {

    private static final SimPos SITE = new SimPos(0, 72, 0);
    private static final String CULTURE = Culture.NORMAN.id();

    private static Settlement seeded() {
        return Founding.seeded(SITE, "Seedholt", SettlementStage.VILLAGE,
                BuildCatalog.DEFAULT, CULTURE);
    }

    private static Building campOf(Settlement town) {
        Building camp = town.buildingWithRole(BuildingRole.LUMBER_CAMP);
        assertNotNull(camp, "a seeded village that has no lumber camp cannot test one");
        return camp;
    }

    // --- the flag ---

    @Test
    void aSeededBuildingSaysSo() {
        for (Building standing : seeded().buildings()) {
            assertTrue(standing.isSeeded(),
                    standing.blueprintId() + " was written into the world, not built");
        }
    }

    @Test
    void aBuildingSomebodyRaisedDoesNot() {
        // The whole of the distinction: a founded camp gets the country it
        // walked into, and its lumberjacks plant saplings and wait like anyone.
        Building built = new Building("lumber_camp", SITE, 40L);
        assertFalse(built.isSeeded(), "the builders stood here; nothing is owed");
    }

    // --- the seed box ---

    @Test
    void aSeededCampKeepsSaplingsOnHand() {
        Settlement town = seeded();
        assertEquals(ForesterStand.SAPLINGS_ON_HAND, town.saplingStock(),
                "a working camp has a box of seed in it, and the town has to be "
                        + "able to see it or the forester will never replant");
        assertTrue(town.storeNear(campOf(town).origin())
                        .has(TownStores.SAPLINGS, ForesterStand.SAPLINGS_ON_HAND),
                "and they are on the shelves nearest the camp, not across the town");
    }

    @Test
    void aCharteredPartyIsGivenNoSaplings() {
        assertEquals(0, Founding.party(SITE, "Testburg").saplingStock(),
                "four people in a field have what the charter gave them");
    }

    // --- the claim ---

    @Test
    void theWoodlandReachesPastTheHouses() {
        Settlement town = seeded();
        WorkArea wood = ForesterStand.woodlandFor(
                campOf(town).origin(), town.center(), town.claimRadius());

        int outward = (int) Math.round(
                campOf(town).origin().horizontalDistance(town.center())) + wood.radius();
        assertTrue(outward >= town.claimRadius() + ForesterStand.BELT,
                "a claim that lies wholly inside the village is a claim the "
                        + "forester will never replant in: reached " + outward
                        + " against a village of " + town.claimRadius());
    }

    @Test
    void theWoodlandIsNoWiderThanReachingPastTheHousesTakes() {
        Settlement town = seeded();
        WorkArea wood = ForesterStand.woodlandFor(
                campOf(town).origin(), town.center(), town.claimRadius());

        int fromTheCamp = (int) Math.floor(
                campOf(town).origin().horizontalDistance(town.center()));
        assertTrue(wood.radius() <= Math.max(LumberPlanner.DEFAULT_RADIUS,
                        town.claimRadius() - fromTheCamp + ForesterStand.BELT),
                "the reach past the houses is the whole reason to widen a claim, "
                        + "so nothing may be widened past it");
        assertTrue(wood.radius() >= LumberPlanner.DEFAULT_RADIUS,
                "and never smaller than the claim an ordinary camp takes");
        assertEquals(campOf(town).origin(), wood.center(),
                "a camp works the ground around itself");
    }

    @Test
    void aVillageThatHasOutgrownItsCampIsStillReachedPast() {
        // The fault that took this file's third fix. The claim used to stop at
        // what a player can dial up on the camp block, which is sixty-four
        // blocks. A village laid out in rings never outgrows that and every
        // other arrangement does — a crossroads town's arms, a warren's knots —
        // and a camp whose whole claim lies inside the village has nowhere its
        // forester is allowed to plant, so the stand was never laid at all.
        Settlement town = seeded();
        SimPos camp = campOf(town).origin();
        int outgrown = 3 * LumberPlanner.MAX_RADIUS;
        town.setClaimRadius(outgrown);
        WorkArea wood = ForesterStand.woodlandFor(camp, town.center(), outgrown);

        int outward = (int) Math.round(camp.horizontalDistance(town.center()))
                + wood.radius();
        assertTrue(outward >= outgrown + ForesterStand.BELT,
                "a claim of " + wood.radius() + " reaches " + outward
                        + " and the village is " + outgrown + " across: every "
                        + "square of that claim is somebody's doorstep");
        assertFalse(ForesterStand.candidates(town, wood).isEmpty(),
                "and so there is somewhere to put a tree");
    }

    // --- the stand ---

    private static List<SimPos> stand() {
        Settlement town = seeded();
        return ForesterStand.candidates(town, ForesterStand.woodlandFor(
                campOf(town).origin(), town.center(), town.claimRadius()));
    }

    @Test
    void thereAreMoreCandidatesThanTreesWanted() {
        // Deliberately more, because only the world knows which of them are
        // lake, cliff or bare stone. Too few and a camp on rough ground gets
        // nothing rather than a smaller stand.
        assertTrue(stand().size() > ForesterStand.TREES_WANTED,
                "a dozen trees wanted and only " + stand().size()
                        + " squares offered leaves the ground no say");
    }

    @Test
    void noTreeStandsInTheVillage() {
        Settlement town = seeded();
        long reach = town.claimRadius();
        for (SimPos spot : stand()) {
            assertTrue(spot.horizontalDistanceSq(town.center()) > reach * reach,
                    spot + " is inside the village, where a tree blocks the paths "
                            + "and the forester is forbidden to replant");
        }
    }

    @Test
    void noTreeStandsOnAPlotOrARoad() {
        Settlement town = seeded();
        for (SimPos spot : stand()) {
            assertTrue(town.isPlotFree(spot, ForesterStand.SPACING, null),
                    spot + " fouls a plot, a way or the wall");
        }
    }

    @Test
    void trunksAreSpacedSoTwoCanopiesDoNotGrowThroughEachOther() {
        List<SimPos> spots = stand();
        for (int i = 0; i < spots.size(); i++) {
            for (int j = i + 1; j < spots.size(); j++) {
                long apart = spots.get(i).horizontalDistanceSq(spots.get(j));
                assertTrue(apart >= (long) ForesterStand.SPACING * ForesterStand.SPACING,
                        spots.get(i) + " and " + spots.get(j) + " are a hedge, not a stand");
            }
        }
    }

    @Test
    void theNearestGroundIsOfferedFirst() {
        Settlement town = seeded();
        SimPos camp = campOf(town).origin();
        List<SimPos> spots = stand();
        for (int i = 1; i < spots.size(); i++) {
            assertTrue(spots.get(i - 1).horizontalDistanceSq(camp)
                            <= spots.get(i).horizontalDistanceSq(camp),
                    "a camp that can only plant a handful should plant the handful "
                            + "nearest the people who work it");
        }
    }

    @Test
    void noTreeStandsOnTheCampItself() {
        Settlement town = seeded();
        assertFalse(stand().contains(campOf(town).origin()),
                "the camp is standing there");
    }

    // --- the once ---

    /** A world that is entirely loaded, entirely flat, and counts its trees. */
    private static final class Planted implements WorldBridge {
        private final List<SimPos> offered = new ArrayList<>();
        private int calls;

        @Override
        public boolean playerWithin(SimPos pos, double radius) {
            return false;
        }

        @Override
        public boolean isLoaded(SimPos pos) {
            return true;
        }

        @Override
        public int surfaceHeight(SimPos pos) {
            return SITE.y();
        }

        @Override
        public Footprint materializeBlueprint(String blueprintId, SimPos origin,
                                              boolean surveyed, int facing) {
            return new Footprint(origin.y(), 5, 5, 4);
        }

        @Override
        public int plantGrownTrees(List<SimPos> spots, int wanted) {
            calls++;
            offered.addAll(spots);
            return Math.min(wanted, spots.size());
        }

        @Override
        public void log(String message) {
        }
    }

    @Test
    void theStandIsPlantedOnceAndTheDebtIsCleared() {
        Settlement town = seeded();
        Planted world = new Planted();

        for (int step = 1; step <= 5; step++) {
            town.step(new SimContext(world, step, SimSettings.SANDBOX));
        }

        assertEquals(1, world.calls,
                "a camp whose wood has been planted is thereafter an ordinary camp");
        for (Building standing : town.buildings()) {
            assertFalse(standing.isSeeded(),
                    standing.blueprintId() + " is still owed a world it already got");
        }
    }

    @Test
    void aTownThatWasFoundedIsPlantedNothing() {
        Settlement town = Founding.party(SITE, "Testburg");
        Planted world = new Planted();

        for (int step = 1; step <= 5; step++) {
            town.step(new SimContext(world, step, SimSettings.SANDBOX));
        }

        assertEquals(0, world.calls,
                "the four pioneers get the country they walked into");
    }
}
