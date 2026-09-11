package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.Layout;
import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalog;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingRole;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.ForesterStand;
import com.kingdoms.sim.settlement.Founding;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.work.PublicWorks;
import com.kingdoms.sim.work.Worksite;
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
 * A town the world put there has to be a town when you find it.
 *
 * <p>Two reports, one shape. A village raised by world generation stood on bare
 * ground with no roads, and its lumber camp stood in a field with nothing to
 * fell. Both had a fix written for them the same week — one for the roads a
 * <em>founded</em> town walks out, one for the wood a <em>seeded</em> camp is
 * owed — and neither reached the path a generated town actually takes.
 *
 * <p>The roads, because {@code Founding.seeded} stands the whole of a village's
 * program and leaves its path network empty. A generated town is watched from
 * its first step by definition — it is raised because a player came near it — so
 * the clock will never open a stretch for it, and its crew opens exactly one a
 * step. A village that had supposedly stood for a generation therefore had
 * nought planned and nought opened at the moment you walked up to it, and then
 * unrolled its streets in front of you one at a time for several minutes.
 *
 * <p>The wood, because a camp is drawn on the step its own chunk arrives, which
 * is the far edge of what a player can see, and the stand it is owed lies
 * further out again in chunks nobody has loaded. The platform plants nothing on
 * ground it cannot read, and the debt that says "this camp has never had its
 * trees" was struck off a line before the planting was attempted. Every
 * generated town lost its wood at exactly the moment it was meant to get one.
 *
 * <p>The fixture is the recorded hillside rather than the sine waves, because
 * the sine waves cannot refuse a road for steepness and a road test on them
 * certifies whatever it is given.
 */
class WorldgenTownTest {

    /**
     * The recorded ground, with somebody standing in the middle of the town and
     * a horizon they cannot see past.
     *
     * <p>Both halves matter. Watched, because a generated town is watched from
     * step zero; and bounded, because the whole of the tree fault is that the
     * wood a camp is owed lies outside the loaded ground on the step the camp is
     * drawn.
     */
    private static final class Watched implements WorldBridge {
        private final RecordedTerrain ground = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        /** What the platform was asked to plant, per call. */
        final List<Integer> plantings = new ArrayList<>();
        /** Where the player is, or null for a world that is loaded everywhere. */
        SimPos player;
        /** How far a server answers for chunks around them. */
        int horizon = Integer.MAX_VALUE;

        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }

        @Override public boolean isLoaded(SimPos pos) {
            return player == null || player.horizontalDistance(pos) <= horizon;
        }

        @Override public int surfaceHeight(SimPos pos) { return ground.surfaceHeight(pos); }
        @Override public boolean standsInWater(SimPos p, int r) { return ground.standsInWater(p, r); }
        @Override public boolean isSiteSuitable(SimPos p, int r) { return ground.isSiteSuitable(p, r); }
        @Override public int siteFault(SimPos p, int r) { return ground.siteFault(p, r); }
        @Override public boolean isSiteLevelable(SimPos p, int r) { return ground.isSiteLevelable(p, r); }
        @Override public int woodedness(SimPos c, int r) { return ground.woodedness(c, r); }

        @Override public Footprint materializeBlueprint(
                String id, SimPos origin, boolean surveyed, int facing) {
            return ground.materializeBlueprint(id, origin, surveyed, facing);
        }

        /** Plants only where the ground can actually be read, as the world does. */
        @Override public int plantGrownTrees(List<SimPos> spots, int wanted) {
            int planted = 0;
            for (SimPos spot : spots) {
                if (planted >= wanted) {
                    break;
                }
                if (isLoaded(spot)) {
                    planted++;
                }
            }
            plantings.add(planted);
            return planted;
        }

        @Override public void log(String message) { }
    }

    /** Where every survey in this project puts its town. */
    private static final SimPos SITE = new SimPos(16, 64, 80);

    /** Long enough for a discovered village to have grown half as much again. */
    private static final int STEPS = 300;

    /**
     * A town raised the way {@code WorldgenSettlements} raises one: seeded at
     * VILLAGE, told its arrangement afterwards, and standing up because you are
     * there to see it.
     */
    private static Settlement found(Watched bridge, String layoutId) {
        SimPos site = new SimPos(SITE.x(), bridge.surfaceHeight(SITE), SITE.z());
        Settlement town = Founding.seeded(site, "Wayside", SettlementStage.VILLAGE,
                BuildCatalog.DEFAULT, Culture.NORMAN.id());
        town.setLayoutId(layoutId);
        for (Person resident : town.residents()) {
            resident.setEmbodied(true);   // you are standing in it, so they are standing up
        }
        return town;
    }

    /**
     * What the view layer does for a town somebody is standing in: the head of
     * the build queue is laid by the crew, and whoever is spared goes to
     * whatever public work the town names.
     */
    private static void hands(Settlement town, WorldBridge bridge) {
        if (!town.buildQueue().isEmpty()) {
            BuildTask head = town.buildQueue().getFirst();
            if (head.siteY() == BuildTask.UNSET_SITE_Y) {
                head.setSiteY(head.origin().y());
                head.setPlan(head.requiredWork(), head.requiredWork());
            }
            head.recordStepDone(4);
        }
        Worksite handed = PublicWorks.handsAreOn(town, bridge);
        if (handed instanceof PublicWorks.RoadWork) {
            handed.finishStretch(town);
        } else if (handed != null && handed.pay(town)) {
            handed.completeOne(town, true);
        }
    }

    /** Stretches planned, opened, and refused as too steep to walk. */
    private record Network(int planned, int opened, int unwalkable) {

        int outstanding() {
            return planned - opened - unwalkable;
        }

        @Override
        public String toString() {
            return planned + " planned, " + opened + " opened, " + unwalkable
                    + " too steep, " + outstanding() + " untrodden";
        }
    }

    private static Network networkOf(Settlement town) {
        PathNetwork paths = town.paths();
        int opened = 0;
        int unwalkable = 0;
        for (int i = 0; i < paths.segments().size(); i++) {
            if (paths.isOpened(i)) {
                opened++;
            } else if (paths.isUnwalkable(i)) {
                unwalkable++;
            }
        }
        return new Network(paths.segments().size(), opened, unwalkable);
    }

    // --- the roads ---

    @Test
    void avillageYouFindAlreadyHasItsStreets() {
        // Before a single step. This is the moment the complaint is about: you
        // fly up to a town that has supposedly stood for a generation and it is
        // fourteen buildings in a field.
        Settlement town = found(new Watched(), Layouts.RING.id());
        town.step(new SimContext(new Watched(), 1, SimSettings.SANDBOX));

        Network roads = networkOf(town);
        assertTrue(roads.planned() > 0,
                "a town the world put there arrives with its roads planned");
        assertEquals(0, roads.outstanding(),
                "and with them walked out, not waiting on a crew: " + roads);
        for (Building standing : town.buildings()) {
            assertTrue(town.paths().hasJoined(standing.origin()),
                    "every standing building is on the network, and "
                            + standing.blueprintId() + " is not");
        }
    }

    @Test
    void andEveryArrangementDoes() {
        // The arrangement is a world setting, so any of them can be the one a
        // player walks into. The street-planning layouts and the ones that only
        // ever join doors both have to arrive finished.
        for (Layout layout : Layouts.all()) {
            Watched bridge = new Watched();
            Settlement town = found(bridge, layout.id());
            town.step(new SimContext(bridge, 1, SimSettings.SANDBOX));

            Network roads = networkOf(town);
            assertTrue(roads.planned() > 0,
                    layout.id() + " arrived with no roads planned at all");
            assertEquals(0, roads.outstanding(),
                    layout.id() + " arrived with roads nobody had walked: " + roads);
            for (Building standing : town.buildings()) {
                assertTrue(town.paths().hasJoined(standing.origin()),
                        layout.id() + " left " + standing.blueprintId() + " off the network");
            }
        }
    }

    @Test
    void andWhatItBuildsAfterwardsIsStillWalkedOutByHands() {
        // The other half of the rule, and the one the fix could easily have
        // broken. Arriving finished is a fact about what the world wrote down;
        // it is not a licence for the town to go on conjuring streets in front
        // of somebody. Anything raised after you find it is the crew's.
        Watched bridge = new Watched();
        Settlement town = found(bridge, Layouts.RING.id());
        int standing = town.buildings().size();
        for (int step = 1; step <= STEPS; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
            hands(town, bridge);
        }
        assertFalse(town.seededRoadsOwed(), "the debt is paid once and struck off");
        assertTrue(town.buildings().size() > standing,
                "a discovered village goes on building, which is the point of finding one");

        // And now the town has nobody it can send. Every road it plans from here
        // has to wait for a pair of hands, because somebody is standing in it.
        for (Person resident : town.residents()) {
            resident.setEmbodied(false);
        }
        int openedBefore = networkOf(town).opened();
        for (int step = STEPS + 1; step <= STEPS + 40; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }

        Network roads = networkOf(town);
        assertEquals(openedBefore, roads.opened(),
                "nothing may open itself in front of a player: " + roads);
        assertTrue(roads.outstanding() > 0,
                "and the town went on planning roads it now cannot walk: " + roads);
    }

    @Test
    void andItKeepsThemAsItGrows() {
        // Three hundred steps of being lived in. A village that arrives finished
        // and then falls behind for ever is the old fault wearing a new hat.
        Watched bridge = new Watched();
        Settlement town = found(bridge, Layouts.RING.id());
        for (int step = 1; step <= STEPS; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
            hands(town, bridge);
        }

        Network roads = networkOf(town);
        assertTrue(roads.outstanding() <= ALLOWED_LAG,
                "a village with hands to spare must not accumulate untrodden road: "
                        + roads);
        for (Building standing : town.buildings()) {
            assertTrue(town.paths().hasJoined(standing.origin()),
                    "every standing building is on the network, and "
                            + standing.blueprintId() + " is not");
        }
    }

    /** A town opens one stretch a step, so it is allowed to be a little behind. */
    private static final int ALLOWED_LAG = 2;

    // --- the wood ---

    /**
     * How far a server answers for chunks. Ten chunks of view is what a
     * single-player world runs at; the exact number does not matter, only that
     * there is a horizon at all.
     */
    private static final int HORIZON = 160;

    @Test
    void aforesterYouFindHasAWoodToWork() {
        // The player is approaching, standing a horizon away from the lumber
        // camp -- which is exactly where they are on the step the camp's own
        // chunk arrives and the camp is drawn. The stand the camp is owed lies
        // further out again, so this is the arrangement under which the wood was
        // silently lost.
        Watched bridge = new Watched();
        Settlement town = found(bridge, Layouts.RING.id());
        Building camp = town.buildingWithRole(BuildingRole.LUMBER_CAMP);
        assertNotNull(camp, "a seeded village with no lumber camp cannot test one");
        bridge.horizon = HORIZON;
        bridge.player = camp.origin().offset(HORIZON, 0, 0);

        for (int step = 1; step <= 20; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }
        assertTrue(camp.isSeeded(),
                "the camp was drawn on ground whose wood nobody can see yet, and it"
                        + " is still owed one");
        assertEquals(List.of(), bridge.plantings,
                "nothing may be planted on ground the world cannot answer for");

        // And now somebody walks in.
        bridge.player = town.center();
        for (int step = 21; step <= 40; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }

        assertFalse(camp.isSeeded(), "the debt is paid once the ground can be seen");
        assertEquals(1, bridge.plantings.size(),
                "the stand is planted exactly once, and was " + bridge.plantings);
        assertTrue(bridge.plantings.getFirst() > 0,
                "a forester you find has a wood, and this one was handed "
                        + bridge.plantings.getFirst() + " trees");
    }

    @Test
    void andTheStandIsTheWholeOneRatherThanWhateverWasLoaded() {
        // Not merely "some trees". The camp is asked with every square of its
        // belt readable, so the dozen that go in are the dozen nearest the camp
        // rather than whichever half of them happened to be inside the horizon
        // on the step the roof went on.
        Watched bridge = new Watched();
        Settlement town = found(bridge, Layouts.RING.id());
        Building camp = town.buildingWithRole(BuildingRole.LUMBER_CAMP);
        assertNotNull(camp, "a seeded village with no lumber camp cannot test one");
        bridge.horizon = HORIZON;
        // Standing where the camp is drawn: near enough to see the camp's own
        // square, not near enough to see the far side of the belt it works.
        bridge.player = camp.origin().offset(HORIZON - 8, 0, 0);
        for (int step = 1; step <= 20; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }
        bridge.player = town.center();
        for (int step = 21; step <= 40; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }

        assertEquals(1, bridge.plantings.size(), "planted once: " + bridge.plantings);
        assertEquals(ForesterStand.TREES_WANTED, bridge.plantings.getFirst(),
                "the whole stand goes in, not the part of it that happened to be"
                        + " loaded on the step the roof went on");
    }

    @Test
    void andACampNobodyEverVisitsIsStillOwedIts() {
        // The debt is not spent by being asked. A town generated at the far end
        // of a flight and never returned to comes back owed its wood, which is
        // what makes it worth saving.
        Watched bridge = new Watched();
        Settlement town = found(bridge, Layouts.RING.id());
        Building camp = town.buildingWithRole(BuildingRole.LUMBER_CAMP);
        assertNotNull(camp, "a seeded village with no lumber camp cannot test one");
        bridge.horizon = HORIZON;
        bridge.player = town.center().offset(4000, 0, 0);

        for (int step = 1; step <= STEPS; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }

        assertTrue(camp.isSeeded(), "nobody has been near it, so nothing is settled");
        assertEquals(List.of(), bridge.plantings, "and nothing was planted in the dark");
    }
}
