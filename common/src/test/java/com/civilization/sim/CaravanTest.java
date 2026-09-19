package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.kingdom.Kingdom;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Caravan;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.InnPlanner;
import com.civilization.sim.settlement.MarketPlanner;
import com.civilization.sim.settlement.PathNetwork;
import com.civilization.sim.settlement.Perimeter;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementEvent;
import com.civilization.sim.settlement.TownEdge;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import com.civilization.sim.world.SimWorld;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wagon the inn trades with, and the promise that watching it changes nothing.
 *
 * <p>The inn has always traded — bread out, iron in, every forty-eight steps —
 * and the whole of it was a line in the event log. The visit is what a player can
 * now stand in the street and watch: a trader walking up the road from the town's
 * edge with two pack animals behind it. The body is platform work and needs a
 * level, so what is stated here is the half that decides anything: <em>when</em>
 * a wagon is on the ground, <em>where</em> it comes in, and — the one that
 * matters — that asking any of it leaves the books exactly as they were.
 */
class CaravanTest {

    private static final SimPos CENTER = new SimPos(0, 64, 0);

    private static class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 5, 5, 4);
        }
        @Override public void log(String message) { }
    }

    /**
     * A town with an inn on it, a ring of opened street round it, and bread on
     * the shelf — which is everything {@code InnPlanner} looks at.
     */
    private static Settlement innTown(String name) {
        Settlement town = new Settlement(Settlement.Id.random(), name, CENTER, 128);
        town.setCultureId("civilization:human/norman");
        PathNetwork paths = new PathNetwork();
        List<SimPos> corners = List.of(
                new SimPos(-48, 64, -48), new SimPos(48, 64, -48),
                new SimPos(48, 64, 48), new SimPos(-48, 64, 48));
        for (int i = 0; i < corners.size(); i++) {
            paths.add(new PathNetwork.Segment(
                    corners.get(i), corners.get((i + 1) % corners.size()), 8));
        }
        for (int i = 0; i < paths.segments().size(); i++) {
            paths.markOpened(i);
        }
        town.setPaths(paths);
        Building inn = new Building("civilization:inn", new SimPos(12, 64, 0), 1, true);
        inn.setFootprint(new Footprint(64, 9, 9, 5));
        town.addBuilding(inn);
        town.addResident(new Person(
                Person.Id.random(), "Hosteler", Profession.TRADER, CENTER));
        town.stores().add(TownStores.FOOD, MarketPlanner.RESERVE_FOOD + 400);
        return town;
    }

    private static List<String> logOf(Settlement town) {
        List<String> lines = new ArrayList<>();
        for (SettlementEvent event : town.events()) {
            lines.add(event.step() + ": " + event.message());
        }
        return lines;
    }

    // --- the doctrine --------------------------------------------------------

    /**
     * The visit is worth nothing, stated by running the same town twice.
     *
     * <p>One town has every question the view layer would ask asked of it on
     * every step — is a wagon due, where does it come in, where does it stand,
     * what is it called — and the other is left alone. Both are stepped through
     * the inn's clock the same number of times. If the two ledgers ever differ
     * then something in the theatre has taken a decision, and a town a player
     * watched would grow differently from one they did not, which is the one
     * asymmetry this mod never allows.
     */
    @Test
    void watchingTheCaravanChangesNeitherTheStoresNorTheLog() {
        Settlement watched = innTown("Watched");
        Settlement alone = innTown("Watched");
        SimWorld world = new SimWorld(new TerrainFake(1));
        Kingdom realm = new Kingdom(Kingdom.Id.random(), "Realm", watched.cultureId());
        realm.addSettlement(watched);
        world.addKingdom(realm);

        for (long step = 1; step <= InnPlanner.CARAVAN_PERIOD * 5; step++) {
            SimContext ctx = new SimContext(new QuietBridge(), step, SimSettings.SANDBOX);
            InnPlanner.advance(watched, ctx);
            // Everything the theatre ever asks, on every step, including the
            // steps where the answer is "no wagon".
            if (Caravan.isVisiting(step) && Caravan.callsAt(watched)) {
                assertNotNull(Caravan.entersAt(watched));
                assertNotNull(Caravan.standsAt(watched));
                assertNotNull(Caravan.nameFor(world, watched));
                Caravan.arrivedOn(step);
            }
            InnPlanner.advance(alone, ctx);
        }

        assertEquals(alone.stores().get(TownStores.FOOD),
                watched.stores().get(TownStores.FOOD),
                "the watched town's granary moved: the visit has taken a decision");
        assertEquals(alone.stores().get(TownStores.IRON),
                watched.stores().get(TownStores.IRON),
                "the watched town's iron moved: a body that only exists while"
                        + " somebody is looking has been paid for something");
        assertEquals(logOf(alone), logOf(watched),
                "the two towns tell different histories, so the caravan a player"
                        + " watched is a caravan the other town never had");
    }

    // --- the clock -----------------------------------------------------------

    @Test
    void theWagonIsOnTheGroundOnlyAroundATrade() {
        assertFalse(Caravan.isVisiting(0),
                "the first wagon calls on the first trade, not on the first step");
        assertFalse(Caravan.isVisiting(InnPlanner.CARAVAN_PERIOD - 1));
        assertTrue(Caravan.isVisiting(InnPlanner.CARAVAN_PERIOD),
                "the trade books on this step and the wagon is what booked it");
        assertTrue(Caravan.isVisiting(
                InnPlanner.CARAVAN_PERIOD + Caravan.VISIT_STEPS - 1));
        assertFalse(Caravan.isVisiting(InnPlanner.CARAVAN_PERIOD + Caravan.VISIT_STEPS),
                "the wagon has left, and the inn yard is empty again");
        assertTrue(Caravan.isVisiting(InnPlanner.CARAVAN_PERIOD * 4),
                "and the next one calls on the next trade");
    }

    /**
     * Two visits never overlap, which is what makes "one caravan to a town" a
     * fact about the clock rather than a cap somebody has to remember to apply.
     */
    @Test
    void oneWagonHasAlwaysLeftBeforeTheNextIsDue() {
        assertTrue(Caravan.VISIT_STEPS < InnPlanner.CARAVAN_PERIOD,
                "a visit outlasting the gap between trades is a wagon that never"
                        + " leaves");
        for (long step = 0; step < InnPlanner.CARAVAN_PERIOD * 4; step++) {
            if (Caravan.isVisiting(step)) {
                assertEquals(0, Caravan.arrivedOn(step) % InnPlanner.CARAVAN_PERIOD,
                        "a wagon on the ground at step " + step + " arrived on a"
                                + " step the inn never traded on");
            }
        }
    }

    // --- who gets one --------------------------------------------------------

    @Test
    void aTownWithNoInnGetsNoWagon() {
        Settlement town = innTown("Innless");
        Settlement bare = new Settlement(
                Settlement.Id.random(), "Bare", CENTER, 128);
        bare.setCultureId(town.cultureId());
        bare.setPaths(town.paths());
        assertFalse(Caravan.callsAt(bare),
                "the inn is what a wagon stops at, and it is also what the"
                        + " planner requires before it books anything");
    }

    @Test
    void aRoadlessTownGetsNoWagon() {
        Settlement camp = new Settlement(Settlement.Id.random(), "Camp", CENTER, 128);
        camp.setCultureId("civilization:human/norman");
        Building inn = new Building("civilization:inn", new SimPos(8, 64, 0), 1, true);
        inn.setFootprint(new Footprint(64, 9, 9, 5));
        camp.addBuilding(inn);
        assertNull(TownEdge.of(camp),
                "a settlement with no opened street has no way in");
        assertFalse(Caravan.callsAt(camp),
                "a wagon put down at a road-less camp is a trader standing in a"
                        + " field with two llamas in the trees behind him");
    }

    @Test
    void theWagonStandsOnTheInnsDoorstep() {
        Settlement town = innTown("Millbrook");
        Building inn = town.buildings().getFirst();
        assertEquals(inn.doorstep(), Caravan.standsAt(town),
                "the inn is where InnPlanner unloads the wagon, so it had better"
                        + " be where the wagon is — and on the step rather than"
                        + " in the middle of the taproom");
    }

    @Test
    void aTownWithNoNeighborTradesWithTheRoad() {
        Settlement town = innTown("Lonely");
        SimWorld world = new SimWorld(new TerrainFake(1));
        Kingdom realm = new Kingdom(Kingdom.Id.random(), "Realm", town.cultureId());
        realm.addSettlement(town);
        world.addKingdom(realm);
        assertEquals("Caravan from " + Caravan.THE_ROAD, Caravan.nameFor(world, town),
                "naming a neighbour that does not exist would be the mod making"
                        + " up a fact about the world");
    }

    @Test
    void aTownWithANeighborSaysWhoSentTheWagon() {
        Settlement town = innTown("Millbrook");
        Settlement neighbor = innTown("Ashford");
        SimWorld world = new SimWorld(new TerrainFake(1));
        Kingdom realm = new Kingdom(Kingdom.Id.random(), "Realm", town.cultureId());
        realm.addSettlement(town);
        realm.addSettlement(neighbor);
        world.addKingdom(realm);
        neighbor.addResident(new Person(
                Person.Id.random(), "Somebody", Profession.IDLER, neighbor.center()));
        assertEquals("Caravan from Ashford", Caravan.nameFor(world, town));
    }

    // --- the way in ----------------------------------------------------------

    @Test
    void theWayInIsAGateWhenTheWallHasOne() {
        Settlement town = innTown("Walled");
        SimPos gate = new SimPos(48, 64, 12);
        town.setPerimeter(new Perimeter(
                List.of(new SimPos(-52, 64, -52), new SimPos(52, 64, -52),
                        new SimPos(52, 64, 52), new SimPos(-52, 64, 52)),
                List.of(gate), 0));

        SimPos way = TownEdge.of(town);
        assertNotNull(way);
        assertTrue(way.horizontalDistance(gate) <= 4,
                "the way in is at " + way + " and the gate the town cut in its own"
                        + " wall is at " + gate + ": a town that opened a gate"
                        + " and then had everybody walk round it made that gate a"
                        + " lie");
        assertTrue(TownEdge.onAnOpenedStreet(town, way),
                "the gate column itself is wall line, not road — an arrival put"
                        + " down there has to find the street on its own, and the"
                        + " first ten blocks are what a pathfinder is worst at");
    }

    @Test
    void theWayInIsTheFarEndOfTheLongestStreetWhenThereIsNoWall() {
        Settlement town = new Settlement(Settlement.Id.random(), "Open", CENTER, 160);
        town.setCultureId("civilization:human/norman");
        PathNetwork paths = new PathNetwork();
        // A short lane and a long high street. The wagon comes in off the long one.
        paths.add(new PathNetwork.Segment(CENTER, new SimPos(0, 64, 12), 3));
        paths.add(new PathNetwork.Segment(CENTER, new SimPos(96, 64, 0), 8));
        paths.markOpened(0);
        paths.markOpened(1);
        town.setPaths(paths);

        SimPos way = TownEdge.of(town);
        assertEquals(new SimPos(96, 64, 0), way,
                "the longest road out of a village is the road to somewhere else,"
                        + " and the end a stranger arrives at is the far one");
        assertTrue(TownEdge.onAnOpenedStreet(town, way));
    }

    /**
     * The second playtest's N4, in the shape it was measured in: a sprawling
     * town whose inn stood at (261, 64, 511) and whose caravan arrived, on time
     * and exactly as planned, two hundred and seventy-seven blocks away at the
     * far end of the longest opened street. A player standing in the inn yard
     * sat through two whole visit windows and saw nothing.
     */
    @Test
    void theWagonDoesNotCallAtAnInnAndArriveAcrossTheTown() {
        Settlement town = new Settlement(Settlement.Id.random(), "Sprawl", CENTER, 512);
        town.setCultureId("civilization:human/norman");
        PathNetwork paths = new PathNetwork();
        // The long high street of a town four hundred blocks across, and an
        // ordinary lane off the middle of it.
        paths.add(new PathNetwork.Segment(CENTER, new SimPos(0, 64, 280), 8));
        paths.add(new PathNetwork.Segment(CENTER, new SimPos(0, 64, -80), 8));
        paths.markOpened(0);
        paths.markOpened(1);
        town.setPaths(paths);
        Building inn = new Building("civilization:inn", new SimPos(12, 64, 0), 1, true);
        inn.setFootprint(new Footprint(64, 9, 9, 5));
        town.addBuilding(inn);

        SimPos way = TownEdge.of(town);
        assertNotNull(way);
        assertEquals(new SimPos(0, 64, -80), way,
                "the longest street of a sprawling town is by construction the"
                        + " one running furthest from everything, so choosing it"
                        + " puts the wagon where nobody is");
        double walk = TownEdge.walkToTheInn(town, way);
        assertTrue(walk <= TownEdge.WALK_TO_THE_INN,
                "the walk from the arrival to the inn it is calling at is " + walk
                        + " blocks, over the " + TownEdge.WALK_TO_THE_INN
                        + " a player told a caravan has called will make");
        assertTrue(TownEdge.onAnOpenedStreet(town, way),
                "and it is still on a road, which is the invariant of the class");
    }

    @Test
    void andTheGateItComesInBySeesTheInnToo() {
        Settlement town = innTown("Gated");
        // Two gates on a ring of street: one across the town from the inn at
        // (12, 64, 0), one beside it. The old rule took whichever gate happened
        // to graze a lane first.
        SimPos near = new SimPos(48, 64, 0);
        SimPos far = new SimPos(-48, 64, 0);
        town.setPerimeter(new Perimeter(
                List.of(new SimPos(-52, 64, -52), new SimPos(52, 64, -52),
                        new SimPos(52, 64, 52), new SimPos(-52, 64, 52)),
                List.of(far, near), 0));

        SimPos way = TownEdge.of(town);
        assertNotNull(way);
        assertTrue(way.horizontalDistance(near) < way.horizontalDistance(far),
                "of two gates a town cut, the one it brings a wagon in by is the"
                        + " one whose walk reaches the inn: the way in was " + way);
        assertTrue(TownEdge.walkToTheInn(town, way) <= TownEdge.WALK_TO_THE_INN);
        assertTrue(TownEdge.onAnOpenedStreet(town, way));
    }

    @Test
    void aTownTooBigForAnyOfThisStillGetsItsWagon() {
        Settlement town = new Settlement(Settlement.Id.random(), "Vast", CENTER, 512);
        town.setCultureId("civilization:human/norman");
        PathNetwork paths = new PathNetwork();
        // Every way in is a hike. The cap gives way rather than the caravan.
        paths.add(new PathNetwork.Segment(CENTER, new SimPos(0, 64, 400), 8));
        paths.add(new PathNetwork.Segment(CENTER, new SimPos(0, 64, -300), 8));
        paths.markOpened(0);
        paths.markOpened(1);
        town.setPaths(paths);
        Building inn = new Building("civilization:inn", new SimPos(12, 64, 0), 1, true);
        inn.setFootprint(new Footprint(64, 9, 9, 5));
        town.addBuilding(inn);

        SimPos way = TownEdge.of(town);
        assertEquals(new SimPos(0, 64, -300), way,
                "with nothing inside the cap the shortest walk of a bad set wins,"
                        + " because a town with opened streets is a town a wagon"
                        + " can reach and refusing one would stop caravans calling"
                        + " on exactly the towns this was written for");
    }

    @Test
    void anUnopenedStreetIsNotAWayIn() {
        Settlement town = new Settlement(Settlement.Id.random(), "Planned", CENTER, 160);
        town.setCultureId("civilization:human/norman");
        PathNetwork paths = new PathNetwork();
        paths.add(new PathNetwork.Segment(CENTER, new SimPos(96, 64, 0), 8));
        town.setPaths(paths);   // routed, never opened
        assertNull(TownEdge.of(town),
                "a street that has been drawn on the plan and never cut is not a"
                        + " road anybody can walk in on");

        paths.markOpened(0);
        paths.markUnwalkable(0);
        assertNull(TownEdge.of(town),
                "and neither is one the town gave up on");
    }

    @Test
    void theWayInIsTheSameEveryTimeItIsAsked() {
        Settlement town = innTown("Steady");
        SimPos once = TownEdge.of(town);
        SimPos twice = TownEdge.of(town);
        assertEquals(once, twice,
                "an entry point that moved between two passes would have a wagon"
                        + " arriving at one gate and leaving by another, which"
                        + " reads as two wagons");
    }
}
