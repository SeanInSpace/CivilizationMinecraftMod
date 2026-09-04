package com.kingdoms.sim;

import com.kingdoms.sim.culture.Layout;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a town does when nothing it looked at will do.
 *
 * <p>The give-up path is where every siting improvement went to die. A search
 * that refused all {@link BuildPlanner#PLOT_ATTEMPTS} candidates used to walk
 * past every one of them and take the next ring slot <em>unexamined</em> — so
 * the sharper the terrain rules got, the more often the search exhausted itself
 * and the more buildings were placed with no check at all. Measured in a world:
 * requiring two water tests instead of one, which refuses strictly more ground,
 * left fourteen buildings in water against eight and none.
 *
 * <p>None of the existing fixtures can show that. {@code TerrainFake} refuses
 * about two columns in five and the recorded ground about three in five, and at
 * those rates ninety-six candidates in a row are never all bad — a town of
 * eighty on either of them reaches the give-up path so rarely that it stands on
 * no faulted ground at all. Real ground refuses in <em>families</em>: a lake, a
 * hillside, a quarter nobody has loaded. So the ground here is refused in
 * families too, and the search really does run out.
 */
class LeastBadSiteTest {

    private static final SimPos CENTRE = new SimPos(0, 64, 0);

    /**
     * Long enough to run out of good ground, short enough to run.
     *
     * <p>Five hundred, against the seven hundred the layout fitness suite
     * grows. Nothing here needs a bigger town — the middle fills by about step
     * two hundred and everything after that is the case under test — and seven
     * hundred cost two minutes where five hundred costs one second, because
     * every plot the town considers is weighed against every building already
     * standing.
     */
    private static final int STEPS = 500;

    private static final BuildingType HOUSE =
            new BuildingType("test:house", 20, 1, 1, 0, 80, 4);

    /**
     * Ground with a grudge against every plot, and a different grudge for each.
     *
     * <p>Nothing here passes, which is the case the whole of this class is
     * about. What varies is by how much, so that "the least bad of them" is a
     * question with one right answer that can be asserted rather than a
     * tie-break in disguise.
     */
    private static final class GradedGround implements WorldBridge {
        private final Layout plan;
        private final Map<Long, Integer> faults = new HashMap<>();
        private final Set<Long> wet = new HashSet<>();
        private final Set<Long> asked = new HashSet<>();
        private final int ordinary = 5;

        GradedGround(Layout plan) {
            this.plan = plan;
        }

        private static long key(SimPos at) {
            return ((long) at.x() << 32) ^ (at.z() & 0xffffffffL);
        }

        void fault(int plotIndex, int fault) {
            faults.put(key(plan.plotFor(CENTRE, plotIndex)), fault);
        }

        void flood(int plotIndex) {
            wet.add(key(plan.plotFor(CENTRE, plotIndex)));
        }

        void drain(int plotIndex) {
            wet.remove(key(plan.plotFor(CENTRE, plotIndex)));
        }

        boolean wasExamined(SimPos at) {
            return asked.contains(key(at));
        }

        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public void log(String message) { }

        @Override
        public Footprint materializeBlueprint(String id, SimPos origin, boolean surveyed,
                                              int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }

        @Override
        public boolean standsInWater(SimPos pos, int radius) {
            return wet.contains(key(pos));
        }

        @Override
        public int siteFault(SimPos plot, int radius) {
            asked.add(key(plot));
            if (wet.contains(key(plot))) {
                return SITE_FAULT_OPEN_WATER;
            }
            return faults.getOrDefault(key(plot), ordinary);
        }

        @Override
        public boolean isSiteSuitable(SimPos plot, int radius) {
            return siteFault(plot, radius) == SITE_FAULT_NONE;
        }
    }

    private static Settlement town() {
        Settlement s = new Settlement(Settlement.Id.random(), "Lastditch", CENTRE, 256);
        s.setCatalogue(List.of(HOUSE));
        s.addResident(new Person(
                Person.Id.random(), "Builder", Profession.BUILDER, CENTRE));
        return s;
    }

    private static SimPos sited(Settlement s) {
        assertEquals(1, s.buildQueue().size(), "the town has to build something");
        return s.buildQueue().getFirst().origin();
    }

    /** Plots are compared by their column; the height is read off the world. */
    private static void assertPlot(Settlement s, int expectedIndex, SimPos actual,
                                   String why) {
        SimPos want = s.arrangement().plotFor(CENTRE, expectedIndex);
        assertEquals(want.x() + "," + want.z(), actual.x() + "," + actual.z(), why);
    }

    @Test
    void aTownOutOfGoodGroundTakesTheLeastBadPlotItLookedAt() {
        Settlement s = town();
        GradedGround ground = new GradedGround(s.arrangement());
        ground.fault(0, 9);     // the slot the old give-up path handed back
        ground.fault(37, 1);    // the best of a bad lot, well inside the search

        s.step(new SimContext(ground, 0, SimSettings.SANDBOX));

        assertPlot(s, 37, sited(s),
                "the town took a plot it had refused without comparing it with the "
                        + "ninety-five others it had also refused");
    }

    @Test
    void itIsNeverGroundTheTownDidNotLookAt() {
        // The fault itself, stated as the invariant it broke. A blind
        // plotFor(nextPlotIndex + n) satisfies "somewhere to build" and nothing
        // else, and that is how a farm, a lumber camp and a watchtower came to
        // stand in a river at y=54, 55 and 62 on ground the rules had never
        // been shown.
        Settlement s = town();
        GradedGround ground = new GradedGround(s.arrangement());
        ground.fault(50, 2);

        s.step(new SimContext(ground, 0, SimSettings.SANDBOX));

        assertTrue(ground.wasExamined(sited(s)),
                "the town built at " + sited(s) + ", which it never judged");
    }

    @Test
    void aRiverIsNeverTheLeastBadAnything() {
        // Every candidate refused and all but one of them under water. Water is
        // not a quantity to be weighed against the one dry plot's own faults --
        // a building in a river reads as broken however sound it is -- so the
        // dry plot wins however many wet ones the town walked past.
        Settlement s = town();
        GradedGround ground = new GradedGround(s.arrangement());
        for (int index = 0; index < BuildPlanner.PLOT_ATTEMPTS; index++) {
            ground.flood(index);
        }
        ground.drain(64);
        ground.fault(64, 2);

        s.step(new SimContext(ground, 0, SimSettings.SANDBOX));

        assertPlot(s, 64, sited(s), "the town chose water over dry ground");
    }

    @Test
    void aWiderSearchAlsoTakesTheLeastBadOfWhatItSees() {
        // Nothing in the first ninety-six is even dry, so there is no examined
        // candidate to fall back on and the search widens. It used to take the
        // first dry slot it stumbled on out there; it now grades a dozen of
        // them and takes the best.
        //
        // A dozen, not the whole window: both are planted inside the first
        // twelve dry slots on purpose, because grading all hundred and
        // twenty-eight would cost more than the building and the search is
        // bounded for the same reason the ordinary one is.
        Settlement s = town();
        GradedGround ground = new GradedGround(s.arrangement());
        for (int index = 0; index < BuildPlanner.PLOT_ATTEMPTS; index++) {
            ground.flood(index);
        }
        ground.fault(96, 7);
        ground.fault(104, 2);

        s.step(new SimContext(ground, 0, SimSettings.SANDBOX));

        assertPlot(s, 104, sited(s),
                "the widened search took the first dry slot rather than the best one");
    }

    @Test
    void groundThePlanWouldAcceptIsStillTakenAtOnce() {
        // The other side of it. Ranking refusals must not turn into weighing up
        // good ground against bad: a plot that passes is taken where it is
        // found, and the whole of this machinery stays out of the way.
        Settlement s = town();
        GradedGround ground = new GradedGround(s.arrangement());
        ground.fault(0, 0);

        s.step(new SimContext(ground, 0, SimSettings.SANDBOX));

        assertPlot(s, 0, sited(s), "sound ground was passed over");
    }

    @Test
    void aScoreOfZeroAndAVetoOfNoNeverDisagree() {
        // The contract the whole of the above rests on, asserted across two
        // grounds and eight thousand plots. If a bridge ever scores sound
        // ground above zero, every search on it starts treating good ground as
        // a compromise and falls back on the least-bad machinery it should
        // never reach; if it scores refused ground at zero, the veto is
        // bypassed altogether. Neither shows up as anything but a slightly
        // odd-looking town.
        TerrainFake fake = new TerrainFake(11);
        RecordedTerrain recorded = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        int reach = BuildPlanner.PLOT_PROBE_RADIUS;
        for (int x = -200; x <= 200; x += 5) {
            for (int z = -200; z <= 200; z += 5) {
                SimPos at = new SimPos(x, 72, z);
                assertEquals(fake.isSiteSuitable(at, reach),
                        fake.siteFault(at, reach) == WorldBridge.SITE_FAULT_NONE,
                        "the sandbox ground grades " + at + " differently from how "
                                + "it judges it");
                assertEquals(recorded.isSiteSuitable(at, reach),
                        recorded.siteFault(at, reach) == WorldBridge.SITE_FAULT_NONE,
                        "the recorded ground grades " + at + " differently from how "
                                + "it judges it");
            }
        }
    }

    // --- what it is worth to a whole town -----------------------------------

    /**
     * A town that fills its good ground and has to keep building.
     *
     * <p>{@code TerrainFake} within a stone's throw of the middle, and refused
     * beyond it — which is what a town on an island, a shelf or a river bend
     * runs into, and what no other fixture in this suite reproduces. The fault
     * out there is deliberately <em>not</em> a function of distance: if poorer
     * ground were always further out, taking the least bad would be the same
     * thing as taking the nearest, and the measurement below would be measuring
     * the tie-break.
     */
    private static final class HemmedIn implements WorldBridge {
        private final TerrainFake ground = new TerrainFake(11);

        /** How far the sound ground reaches, in blocks from the middle. */
        private static final int SHORE = 48;

        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return ground.surfaceHeight(pos); }
        @Override public void log(String message) { }

        @Override
        public Footprint materializeBlueprint(String id, SimPos origin, boolean surveyed,
                                              int facing) {
            return ground.materializeBlueprint(id, origin, surveyed, facing);
        }

        @Override
        public boolean standsInWater(SimPos pos, int radius) {
            return ground.standsInWater(pos, radius);
        }

        @Override
        public int woodedness(SimPos centre, int radius) {
            return ground.woodedness(centre, radius);
        }

        @Override
        public int siteFault(SimPos plot, int radius) {
            int fault = ground.siteFault(plot, radius);
            if (fault == SITE_FAULT_OPEN_WATER || withinTheShore(plot)) {
                return fault;
            }
            return fault + 1 + Math.floorMod(plot.x() * 37 + plot.z() * 11, 9);
        }

        private boolean withinTheShore(SimPos plot) {
            return Math.abs(plot.x()) <= SHORE && Math.abs(plot.z()) <= SHORE;
        }

        @Override
        public boolean isSiteSuitable(SimPos plot, int radius) {
            return siteFault(plot, radius) == SITE_FAULT_NONE;
        }
    }

    /**
     * What a hemmed-in town builds, and how bad the ground it settles for is.
     *
     * <p>Grown the way {@code LayoutFitnessTest} grows one — six pioneers, five
     * hundred steps — on ground sound for forty-eight blocks and refused past
     * it. Every building after the middle fills up is sited by a give-up path,
     * so these are the numbers those paths produce. Off the good ground the
     * fault runs from one to nine, so the least bad a town can hope for is one.
     *
     * <pre>
     *                                     buildings   faulted   courses   worst
     *   before                                    9         0         0       0
     *   a site no better than the one it
     *     replaces no longer replaces it         45        35       119       8
     *   and the least bad of what was
     *     examined                               45        34        55       3
     * </pre>
     *
     * <p>The first row is not a town that built on good ground; it is a town
     * that could not build. Every site it chose was refused by the ground, the
     * relocation check moved it the next step, and a task that keeps being
     * replaced never starts — so it finished nine buildings on the middle
     * ground and nothing else in five hundred steps, with its plot cursor six
     * hundred and ninety-three slots out.
     *
     * <p>The second row is that town able to build again, and it is where the
     * doctrine bites: forty-five buildings, thirty-five of them on ground the
     * settlement had refused. That is correct — a town out of room builds on
     * poor ground — and it is also the whole of what "poor ground" used to
     * mean, which is whatever the walk happened to stop at: a hundred and
     * nineteen courses of fault, and one building on ground eight courses past
     * what a builder will cut.
     *
     * <p>The third row is the same forty-five buildings, on less than half the
     * fault, with nothing worse than three. Nobody gave anything up for it: the
     * search had already looked at every one of those plots and was throwing
     * the answers away.
     */
    private static final int HEMMED_IN_COURSES_CEILING = 80;

    /**
     * And no single building on ground worse than this.
     *
     * <p>Three measured, five allowed, eight without the ranking. The gap
     * between five and eight is what makes this a test rather than a record.
     */
    private static final int HEMMED_IN_WORST_CEILING = 5;

    /** Forty-five measured. Nine is what it managed before, and is a failure. */
    private static final int MIN_BUILDINGS = 20;

    @Test
    void aTownThatRunsOutOfGoodGroundSettlesForTheLeastBadOfIt() {
        HemmedIn ground = new HemmedIn();
        Settlement town = new Settlement(Settlement.Id.random(), "Hemmed", CENTRE, 512);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        town.setCultureId("kingdoms:vale");
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(
                    Person.Id.random(), name, Profession.PIONEER, CENTRE));
        }
        for (int step = 1; step <= STEPS; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }

        int standing = 0;
        int faulted = 0;
        int courses = 0;
        int worst = 0;
        for (Building b : town.buildings()) {
            if (!BuildPlanner.holdsGround(b.blueprintId())) {
                continue;
            }
            standing++;
            int fault = ground.siteFault(b.origin(), BuildPlanner.PLOT_PROBE_RADIUS);
            if (fault == WorldBridge.SITE_FAULT_OPEN_WATER) {
                courses += 1000;   // must never happen; make it impossible to miss
                continue;
            }
            if (fault > 0) {
                faulted++;
                courses += fault;
                worst = Math.max(worst, fault);
            }
        }
        System.out.println("hemmed in: " + standing + " buildings, " + faulted
                + " on faulted ground, " + courses + " courses, worst " + worst);

        // The floor, and it is not a fixture check. Nine is what this town
        // managed before any of this: it chose a site, the ground refused it,
        // the relocation check moved it, and it did that again every step for
        // five hundred steps without laying a block. A town out of good ground
        // builds on poor ground -- it does not stop.
        assertTrue(standing >= MIN_BUILDINGS,
                "only " + standing + " buildings in " + STEPS + " steps: a town out "
                        + "of good ground is supposed to build on poor ground, not "
                        + "to stop building");
        assertTrue(courses <= HEMMED_IN_COURSES_CEILING,
                courses + " courses of fault accepted across the town, past the "
                        + HEMMED_IN_COURSES_CEILING + " allowed");
        assertTrue(worst <= HEMMED_IN_WORST_CEILING,
                "one building stands on ground scoring " + worst
                        + ", past the " + HEMMED_IN_WORST_CEILING + " allowed");
    }
}
