package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalog;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Founding;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.work.PublicWorks;
import com.kingdoms.sim.work.Worksite;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town you founded and stood in has to end up with roads.
 *
 * <p>The founded path is the one every player takes and it was the one path that
 * paved almost nothing. A founded camp is watched by definition — you are
 * standing in it — so its streets are the crew's to walk out and never the
 * clock's. And the crew was only ever offered a public work with the build queue
 * empty, while a settlement's stage program orders the next building on the same
 * step the last one is struck off. So the only moment anybody could be sent to a
 * street was the single step between one building finishing and the next being
 * ordered: the town planned two stretches for every building it raised and
 * walked out about one, and the backlog grew for as long as the town did.
 *
 * <p>What fixes it is a hand, not a clock — see {@code PublicWorks.availableTo}.
 * A party of four keeps three on the bunkhouse and walks the fourth out to the
 * road. The last pair of hands in a settlement still belongs at the site, which
 * is the one thing about "shelter and stores first" that did not change.
 */
class FoundedRoadsTest {

    /** Flat, loaded, and with somebody standing in the middle of everything. */
    private static final class Watched implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return 64; }
        @Override public int groundHeight(SimPos pos) { return 64; }
        @Override public Footprint materializeBlueprint(
                String id, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(64, 5, 5, 4);
        }
        @Override public void log(String message) { }
    }

    /**
     * What the view layer does for a town somebody is standing in.
     *
     * <p>Both crews, because the whole subject is how a town divides itself
     * between them: the builders lay their share of the head of the queue, and
     * whoever is spared goes to whatever public work the town names — the street,
     * or the ring above it.
     */
    private static void hands(Settlement town) {
        if (!town.buildQueue().isEmpty()) {
            BuildTask head = town.buildQueue().getFirst();
            if (head.siteY() == BuildTask.UNSET_SITE_Y) {
                head.setSiteY(head.origin().y());
                head.setPlan(head.requiredWork(), head.requiredWork());
            }
            head.recordStepDone(4);
        }
        Worksite handed = PublicWorks.handsAreOn(town, new Watched());
        if (handed instanceof PublicWorks.RoadWork) {
            handed.finishStretch(town);
        } else if (handed != null && handed.pay(town)) {
            handed.completeOne(town, true);
        }
    }

    /** Stretches planned, opened, and refused as too steep to walk. */
    private record Network(int planned, int opened, int unwalkable) {

        /** Stretches that are the crew's to walk and have not been walked. */
        int outstanding() {
            return planned - opened - unwalkable;
        }

        @Override
        public String toString() {
            return planned + " planned, " + opened + " opened, "
                    + outstanding() + " untrodden";
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

    /**
     * A town opens one stretch a step, so it is allowed to be a little behind.
     *
     * <p>Two, for the pair a newly raised building plans at once. Any more than
     * that is not a lag, it is a backlog.
     */
    private static final int ALLOWED_LAG = 2;

    /** A charter party, standing up, with somebody watching them. */
    private static Settlement chartered() {
        Settlement town = Founding.party(new SimPos(0, 64, 0), "Newholt");
        town.setCatalog(BuildCatalog.DEFAULT);
        for (Person settler : town.residents()) {
            settler.setEmbodied(true);   // you are here, so they are standing up
        }
        return town;
    }

    @Test
    void aFoundedCampWalksOutEveryRoadItPlans() {
        // Fifty steps is a camp and a homestead: the whole of the founding the
        // charter describes, and the whole of what the complaint was about.
        Settlement town = chartered();
        WorldBridge bridge = new Watched();
        for (int step = 1; step <= 50; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
            hands(town);
        }
        Network roads = networkOf(town);

        assertTrue(town.buildings().size() >= 2,
                "fifty steps in, a camp has raised something to join up");
        assertTrue(roads.planned() > 0,
                "and the roads between what it raised are planned");
        assertTrue(roads.outstanding() <= ALLOWED_LAG,
                "a founded camp must walk out the roads it plans, and this one has "
                        + roads);
        for (Building standing : town.buildings()) {
            assertTrue(town.paths().hasJoined(standing.origin()),
                    "every standing building is on the network, and "
                            + standing.blueprintId() + " is not");
        }
    }

    @Test
    void andTheBacklogNeverGrowsWhileTheTownHasAHandToSpare() {
        // The shape of the old fault, over the long run. It was never "no road
        // was ever opened" -- it was that a growing town planned two stretches
        // for every one it walked, so the gap widened with every building. Two
        // hundred steps is where that showed: seventeen of twenty-seven.
        //
        // Measured only on the steps the town actually had somebody to send. A
        // settlement down to its last builder keeps them at the site by design,
        // and its streets waiting is the stated rule rather than this bug.
        Settlement town = chartered();
        WorldBridge bridge = new Watched();
        int worst = 0;
        String when = "never";
        for (int step = 1; step <= 200; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
            hands(town);
            if (PublicWorks.workingHands(town) <= PublicWorks.HANDS_KEPT_ON_BUILDINGS) {
                continue;
            }
            Network roads = networkOf(town);
            if (roads.outstanding() > worst) {
                worst = roads.outstanding();
                when = "step " + step + ": " + roads;
            }
        }

        assertTrue(worst <= ALLOWED_LAG,
                "a town with a hand to spare must not accumulate untrodden road; "
                        + "the worst it got was " + when);
    }

    // --- the rule underneath it ---

    /** A camp with a house on the go, hands to spare, and a street waiting. */
    private static Settlement busyCampOf(int settlers) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Newholt", new SimPos(0, 64, 0), 128);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        for (int i = 0; i < settlers; i++) {
            Person settler = new Person(Person.Id.random(), "Settler " + i,
                    Profession.PIONEER, town.center());
            settler.setEmbodied(true);
            town.addResident(settler);
        }
        town.paths().add(new PathNetwork.Segment(
                new SimPos(0, 64, 0), new SimPos(8, 64, 0)));
        town.enqueueBuild(new BuildTask("kingdoms:bunkhouse", new SimPos(20, 64, 20), 40));
        return town;
    }

    @Test
    void aPartyOfFourSparesOneOfThemForTheStreet() {
        Settlement town = busyCampOf(4);

        assertTrue(PublicWorks.handsAreOn(town, new Watched())
                        instanceof PublicWorks.RoadWork,
                "three on the bunkhouse and one on the road, which is how a camp"
                        + " ends up with both");
    }

    @Test
    void aTownWithOnePairOfHandsStillPutsThemOnTheHouse() {
        // The boundary, and it does not move: shelter and stores before roads
        // and walls is a rule about the last pair of hands.
        assertNull(PublicWorks.handsAreOn(busyCampOf(1), new Watched()),
                "the only builder in the settlement belongs at the site");
    }

    @Test
    void theWallStillWaitsForTheBuildQueue() {
        // Only the roads interleave. A post is a plank the build queue is owed;
        // a track is somebody's afternoon and costs the town nothing else.
        for (Worksite work : PublicWorks.availableTo(busyCampOf(4))) {
            assertFalse(work instanceof PublicWorks.WallWork,
                    "a town raising a bunkhouse does not spend its timber on a fence");
        }
    }
}
