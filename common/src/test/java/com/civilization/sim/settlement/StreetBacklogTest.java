package com.civilization.sim.settlement;

import com.civilization.sim.RecordedTerrain;
import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town's streets go down together, whichever order it opened them in.
 *
 * <p>The report is a town you walk back into after a journey: its houses are all
 * there at once and then its streets are drawn in around you, one a second, for
 * as long as you stand in it. A hundred and seventy stretches is three minutes
 * of watching gravel appear.
 *
 * <p>The backlog that was written to prevent exactly that walked the network by
 * index from a high-water mark — {@code laidThrough} — and both halves of it
 * assumed the same thing: that a town opens every stretch of its network in
 * index order, so "how far have the stones gone down" is a number and everything
 * before it is done. That was true while a founded town opened one stretch a
 * step from the top of the list.
 *
 * <p>{@code StreetDemand} ended it. A town opens the streets it fronts, the ways
 * to its square and the rings that have filled up, which is a set with holes in
 * it — and against a mark, the first hole is a wall. The sweep stopped there,
 * every opened stretch beyond it read as un-laid forever, and the only thing
 * that ever drew them was the one-a-second round-robin sitting behind the
 * backlog. The backlog was doing the opposite of its job.
 *
 * <p>So the record is per stretch and the walk is over what is opened, in the
 * order it was opened. This grows a real town on the recorded hillside, checks
 * that its opened set genuinely has holes in it, and counts the passes each rule
 * needs to get the whole town's gravel down.
 */
class StreetBacklogTest {

    /** Where every survey in this project puts its town. */
    private static final SimPos CENTER = new SimPos(16, 64, 80);

    /** Long enough for this ground to carry a town of some size. */
    private static final int STEPS = 500;

    /**
     * How much of a waiting network goes down in one pass.
     *
     * <p>{@code PersonEntityManager.PAVE_AT_ONCE}, restated because that class is
     * the platform's and this module cannot see it. What is measured here is the
     * shape of the walk rather than the exact bound, and the assertions are
     * written so that moving the number moves both figures together.
     */
    private static final int PAVE_AT_ONCE = 64;

    /** A stop, so a rule that cannot finish reports a number instead of hanging. */
    private static final int GIVE_UP_AFTER = 5000;

    private static RecordedTerrain ground;

    private static synchronized RecordedTerrain ground() {
        if (ground == null) {
            ground = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        }
        return ground;
    }

    /** A town grown on the hillside until it has a network worth drawing. */
    private static Settlement grown() {
        Settlement town = new Settlement(Settlement.Id.random(), "Millbrook", CENTER, 512);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        town.setCultureId(Culture.NORMAN.id());
        town.setLayoutId("ring_streets");
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(
                    Person.Id.random(), name, Profession.PIONEER, CENTER));
        }
        for (int step = 1; step <= STEPS; step++) {
            town.stores().add(TownStores.WOOD, 8);
            town.stores().add(TownStores.STONE, 6);
            town.step(new SimContext(ground(), step, SimSettings.SANDBOX));
        }
        return town;
    }

    /**
     * Whether the town's opened stretches run in one unbroken block from zero.
     *
     * <p>The invariant the mark was built on. If this ever comes back true the
     * fixture has stopped exhibiting the fault and the measurement below is
     * measuring nothing.
     */
    private static boolean openedIsAPrefix(PathNetwork network) {
        for (int i = 0; i < network.segments().size(); i++) {
            if (!network.isOpened(i)) {
                for (int j = i + 1; j < network.segments().size(); j++) {
                    if (network.isOpened(j)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return true;
    }

    /** The first stretch the town has not opened, which is where the mark stuck. */
    private static int firstHole(PathNetwork network) {
        for (int i = 0; i < network.segments().size(); i++) {
            if (!network.isOpened(i)) {
                return i;
            }
        }
        return network.segments().size();
    }

    /**
     * Passes until every opened stretch has been drawn, under the rule as it is.
     *
     * <p>The sweep's own loop, less the world: take what the backlog offers,
     * strike each one off, repeat. Nothing here can stall, because nothing the
     * backlog hands back is an index it has to wait on.
     */
    private static int passesUnderTheBacklog(Settlement town) {
        int passes = 0;
        while (passes < GIVE_UP_AFTER) {
            List<Integer> owed = RoadUpkeep.backlog(town, PAVE_AT_ONCE);
            if (owed.isEmpty()) {
                return passes;
            }
            passes++;
            for (int index : owed) {
                town.paths().markLaid(index);
            }
        }
        return passes;
    }

    /**
     * Passes under the rule as it was: a high-water mark, then the round-robin.
     *
     * <p>A restatement of the old sweep rather than the old sweep itself, which
     * is deliberate — the fault is fixed, so the only way to keep its
     * measurement honest is to write down what it did. It did this: walk from
     * the mark, skipping stretches the ground refused, and stop at the first
     * index nobody has opened; move the mark to wherever the walk stopped; and
     * if that walk laid nothing, mend exactly one stretch, the next one round.
     *
     * <p>Read against the town's opened set without touching it, so the two
     * measurements are of one town rather than of two.
     */
    private static int passesUnderAHighWaterMark(PathNetwork network) {
        int count = network.segments().size();
        Set<Integer> drawn = new HashSet<>();
        int owed = network.openedCount();
        int mark = 0;
        int cursor = 0;
        int passes = 0;
        while (drawn.size() < owed && passes < GIVE_UP_AFTER) {
            passes++;
            int done = 0;
            int i = mark;
            for (; i < count && done < PAVE_AT_ONCE; i++) {
                if (network.isUnwalkable(i)) {
                    continue;   // a stair, not a street: nobody will ever open it
                }
                if (!network.isOpened(i)) {
                    break;      // "the network is opened in order; wait for this one"
                }
                drawn.add(i);
                done++;
            }
            mark = Math.max(mark, Math.min(i, count));
            if (done > 0) {
                continue;
            }
            // The round-robin behind the backlog: one stretch a sweep, and a
            // sweep is a second. It draws nothing the mark knows about, which is
            // why the mark never moves again.
            int index = Math.floorMod(cursor++, count);
            if (network.isOpened(index)) {
                drawn.add(index);
            }
        }
        return passes;
    }

    /**
     * The fault, stated as the question the sweep asks.
     *
     * <p>Not a count and not a timing: the backlog, asked once, has to offer a
     * stretch from beyond the first hole in the opened set. Under the mark it
     * could not — that was the whole of it.
     */
    @Test
    void theBacklogReachesPastTheFirstStreetTheTownNeverOpened() {
        Settlement town = grown();
        PathNetwork network = town.paths();

        assertTrue(network.openedCount() > 0, "a town with no streets measures nothing");
        assertTrue(!openedIsAPrefix(network),
                "this ground grew a town that opened every street in index order, so"
                        + " the fault cannot show here — the fixture needs a bigger town");

        int hole = firstHole(network);
        List<Integer> owed = RoadUpkeep.backlog(town, PAVE_AT_ONCE);
        List<Integer> beyond = new ArrayList<>();
        for (int index : owed) {
            if (index > hole) {
                beyond.add(index);
            }
        }
        assertTrue(!beyond.isEmpty(),
                "the backlog stopped at stretch " + hole + ", the first one the town"
                        + " never opened, and offered nothing beyond it — so everything"
                        + " past it waits on the one-a-second sweep");
    }

    /** Every opened stretch is offered exactly once, and nothing else ever is. */
    @Test
    void theBacklogOffersTheOpenedStretchesAndOnlyThem() {
        Settlement town = grown();
        PathNetwork network = town.paths();

        Set<Integer> offered = new HashSet<>();
        int passes = 0;
        while (passes < GIVE_UP_AFTER) {
            List<Integer> owed = RoadUpkeep.backlog(town, PAVE_AT_ONCE);
            if (owed.isEmpty()) {
                break;
            }
            passes++;
            for (int index : owed) {
                assertTrue(network.isOpened(index),
                        "stretch " + index + " was offered for paving and nobody has"
                                + " walked it out");
                assertTrue(offered.add(index),
                        "stretch " + index + " was offered twice, so the town repaves"
                                + " a road it has already drawn");
                town.paths().markLaid(index);
            }
        }
        assertEquals(network.openedCount(), offered.size(),
                "the backlog did not offer every street the town has opened");
        assertTrue(RoadUpkeep.backlog(town, PAVE_AT_ONCE).isEmpty(),
                "a town whose streets are all drawn is still owed gravel");
    }

    /**
     * The measurement, and it is the report.
     *
     * <p>A town's whole network goes down in the few passes the per-pass bound
     * allows, rather than in one pass a second for as long as it takes the
     * round-robin to walk the list.
     */
    @Test
    void awholeTownsStreetsGoDownInAHandfulOfPassesInsteadOfOneASecond() {
        Settlement town = grown();
        PathNetwork network = town.paths();
        int opened = network.openedCount();

        int before = passesUnderAHighWaterMark(network);
        int after = passesUnderTheBacklog(town);

        int ideal = (opened + PAVE_AT_ONCE - 1) / PAVE_AT_ONCE;
        System.out.println("PAVING " + opened + " opened of " + network.segments().size()
                + " stretches (first hole at " + firstHole(network) + "): "
                + before + " passes before, " + after + " after");

        assertEquals(ideal, after,
                "a town's opened streets should go down " + PAVE_AT_ONCE + " a pass,"
                        + " so " + opened + " of them is " + ideal + " passes");
        assertTrue(after * 4 < before,
                "the backlog is no faster than the one-a-second sweep it replaces:"
                        + " " + before + " passes before, " + after + " after");
    }
}
