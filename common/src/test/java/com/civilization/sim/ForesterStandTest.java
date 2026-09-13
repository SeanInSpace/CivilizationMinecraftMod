package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.ForesterStand;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.settlement.LumberPlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.settlement.WorkArea;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
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

    /**
     * The candidate squares of the claim a camp actually settles on.
     *
     * <p>Not of the first belt, and the difference is now the ordinary case. The
     * belt is held off the ground the town's own plan has reserved, and on a
     * closely plotted arrangement — a bastide, a green — the annulus immediately
     * outside the houses is very nearly all reserved. {@code ForesterStand.raise}
     * answers that by looking further out, up to {@link ForesterStand#BELTS_OUT}
     * belts, which is exactly what that bound is for; so the claim worth asking
     * about is the one it stops at rather than the one it starts from.
     */
    private static List<SimPos> stand() {
        Settlement town = seeded();
        town.step(new SimContext(new Planted(), 1, SimSettings.SANDBOX));
        WorkArea settled = town.lumberArea();
        assertNotNull(settled, "the camp never staked a claim at all");
        return ForesterStand.candidates(town, settled);
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
        private final List<SimPos> refused = new ArrayList<>();
        private int calls;

        /** One column the ground will not take, for the relocation paths. */
        Planted refuse(SimPos where) {
            refused.add(where);
            return this;
        }

        @Override
        public boolean isSiteSuitable(SimPos plot, int radius) {
            for (SimPos no : refused) {
                if (no.x() == plot.x() && no.z() == plot.z()) {
                    return false;
                }
            }
            return true;
        }

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

    // --- the roads that come afterwards ---

    @Test
    void theStandIsTheDozenNearestSquaresAndIsAnsweredWithoutStoringIt() {
        // The camp keeps a count of its trees and never kept their squares. It
        // does not have to: the stand is a function of the camp, the claim and
        // what is standing, all three of which the settlement already knows.
        Settlement town = seeded();
        town.setLumberArea(ForesterStand.woodlandFor(campOf(town).origin(),
                town.center(), town.claimRadius()));
        List<SimPos> standing = ForesterStand.stand(town);

        assertFalse(standing.isEmpty(), "a camp with a belt has a stand in it");
        assertTrue(standing.size() <= ForesterStand.TREES_WANTED,
                "a stand is a dozen trees, not the whole belt: " + standing.size());
        assertEquals(standing, ForesterStand.candidates(town, town.lumberArea())
                        .subList(0, standing.size()),
                "the squares planted are the nearest of the squares offered");
    }

    @Test
    void aTownWithNoCampHasNoStandToKeepARoadOffOf() {
        assertTrue(ForesterStand.stand(Founding.party(SITE, "Testburg")).isEmpty(),
                "four people in a field have no forester and no belt");
    }

    @Test
    void noRoadASeededTownOpensIsLaidThroughTheStand() {
        // The fault in one line: the town goes on building after the stand is
        // planted, and the streets and lanes it walks out afterwards used to be
        // routed straight through the belt. What the paving then did to those
        // trunks is Overgrowth's half of the same bug.
        Settlement town = seeded();
        Planted world = new Planted();
        for (int step = 1; step <= 12; step++) {
            town.step(new SimContext(world, step, SimSettings.SANDBOX));
        }
        List<SimPos> standing = ForesterStand.stand(town);
        assertFalse(standing.isEmpty(), "nothing to test if the camp got no wood");

        List<com.civilization.sim.settlement.PathNetwork.Segment> runs =
                town.paths().segments();
        assertFalse(runs.isEmpty(), "a seeded village walks its roads out at once");
        for (int i = 0; i < runs.size(); i++) {
            if (!town.paths().isOpened(i)) {
                continue;
            }
            int half = runs.get(i).paveHalf();
            for (SimPos at : runs.get(i).positions()) {
                for (SimPos trunk : standing) {
                    assertFalse(Math.abs(at.x() - trunk.x()) <= half
                                    && Math.abs(at.z() - trunk.z()) <= half,
                            "an opened road gravels the stand at " + trunk);
                }
            }
        }
    }

    // --- the ground the plan has spoken for ---

    /**
     * A town is nineteen buildings inside a plan of two hundred and fifty-six, so
     * a belt chosen against what is <em>standing</em> is a belt planted squarely on
     * the ground the town has not reached yet. Every one of those plots is cleared
     * in its turn, and the camp watches its wood be built on: Millbrook went from
     * 57 trees to 4 in 218 steps that way.
     */
    @Test
    void noTreeStandsOnGroundTheTownsOwnPlanHasReserved() {
        // Every arrangement, because the reserved ground is shaped by the
        // arrangement and the one a culture happens to hash to proves nothing
        // about the other fourteen.
        for (Culture culture : Culture.all()) {
            for (String layout : culture.layouts()) {
                Settlement town = Founding.seeded(SITE, "Seedholt",
                        SettlementStage.VILLAGE, BuildCatalog.DEFAULT, culture.id());
                town.setLayoutId(layout);
                if (town.buildingWithRole(BuildingRole.LUMBER_CAMP) == null) {
                    continue;
                }
                town.step(new SimContext(new Planted(), 1, SimSettings.SANDBOX));
                List<SimPos> standing = ForesterStand.stand(town);
                assertFalse(standing.isEmpty(),
                        layout + " settled its claim and got no stand at all");

                com.civilization.sim.culture.TownPlan plan = town.arrangement()
                        .planFor(town.center(), Founding.PLOTS_ENOUGH_FOR_ANY_PROGRAM);
                for (SimPos trunk : standing) {
                    for (com.civilization.sim.culture.TownPlan.Plot plot : plan.plots()) {
                        int reach = plot.span() / 2;
                        assertFalse(Math.abs(trunk.x() - plot.at().x()) <= reach
                                        && Math.abs(trunk.z() - plot.at().z()) <= reach,
                                layout + ": " + trunk + " stands on plot " + plot.at()
                                        + ", which the town has not built on yet and will");
                    }
                    for (com.civilization.sim.culture.TownPlan.Street street
                            : plan.streets()) {
                        assertFalse(street.touches(trunk, 0.5), layout + ": " + trunk
                                + " stands in a street the plan has drawn, and the "
                                + "road is built before the trees are missed");
                    }
                }
            }
        }
    }

    /**
     * The belt is staked around the camp, so it has to move when the camp does.
     *
     * <p>A camp relocated on arrival used to leave its claim behind at the plot it
     * had left — and {@code ForesterStand.raise} would not put it right, because a
     * claim that is not centered on the camp is read as one a player pointed
     * somewhere else deliberately and is never overruled. So the camp stood in one
     * wood and counted, felled and replanted in another.
     */
    @Test
    void aCampThatRelocatesTakesItsBeltWithIt() {
        Settlement town = seeded();
        SimPos was = campOf(town).origin();
        town.setLumberArea(ForesterStand.woodlandFor(was, town.center(),
                town.claimRadius()));
        assertEquals(was, town.lumberArea().center(),
                "the belt starts out staked around the camp");

        // The one column the world refuses, which is what makes a building move
        // on the step it would otherwise have been drawn.
        Planted world = new Planted();
        world.refuse(was);
        for (int step = 1; step <= 4; step++) {
            town.step(new SimContext(world, step, SimSettings.SANDBOX));
        }

        SimPos now = campOf(town).origin();
        assertFalse(now.x() == was.x() && now.z() == was.z(),
                "the camp did not move, so nothing here is being tested");
        assertEquals(now.x(), town.lumberArea().center().x(),
                "a camp that moved is working the wood around where it moved to, "
                        + "not the wood it left: belt at " + town.lumberArea().center()
                        + ", camp at " + now);
        assertEquals(now.z(), town.lumberArea().center().z(),
                "a camp that moved is working the wood around where it moved to");
    }

    // --- the town building itself out ---

    /**
     * The measured fault, end to end: 57 trees standing became 4 in 218 steps
     * while the town cleared its own plots, and the camp's timber swung from 1072
     * to 1 because the clock was reading a stand it believed had been felled.
     *
     * <p>The wood store is held full on purpose, so the forester has no reason to
     * fell and every tree the ledger loses is a tree somebody else took. What the
     * town is doing meanwhile is raising buildings, which means clearing plots.
     */
    @Test
    void theStandSurvivesTheTownBuildingItselfOut() {
        Settlement town = seeded();
        Planted world = new Planted();
        town.step(new SimContext(world, 1, SimSettings.SANDBOX));
        Building camp = campOf(town);
        int planted = com.civilization.sim.settlement.Stand.trees(camp);
        assertTrue(planted > 0, "nothing to test if the camp was never given a stand");

        int before = town.buildings().size();
        for (int step = 2; step <= 300; step++) {
            // Held above the ceiling, so wantsMoreTimber is false and the
            // forester's own axe is never the explanation for a missing tree.
            town.stores().add(TownStores.WOOD, 4096);
            town.stores().add(TownStores.STONE, 512);
            town.step(new SimContext(world, step, SimSettings.SANDBOX));
            assertTrue(com.civilization.sim.settlement.Stand.trees(camp) >= planted,
                    "the stand fell to " + com.civilization.sim.settlement.Stand.trees(camp)
                            + " of " + planted + " by step " + step
                            + " with nobody felling it");
        }
        assertTrue(town.buildings().size() > before,
                "the town raised nothing, so no plot was ever cleared and this "
                        + "asserts nothing: " + town.buildings().size());
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
