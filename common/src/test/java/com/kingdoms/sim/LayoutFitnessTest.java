package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.Layout;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingSizes;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a finished town has to be true of, whatever built it.
 *
 * <p>These are <strong>fitness functions</strong>, not example tests. They do
 * not check that a particular layout produces a particular plot; they grow a
 * whole town on real ground and then ask whether the result is a town anybody
 * would want to walk into. Every layout has to pass, so a new arrangement is
 * held to the same bar as the old ones without anybody writing it new tests.
 *
 * <p><strong>Why this exists.</strong> Every siting fault this project has had
 * was found by launching Minecraft, growing a town, flying to it and looking:
 * a quarter of the buildings standing in water, a warren that lost three
 * quarters of its people, producers that had never consulted the ground in any
 * version. Each took a five-minute round trip to see and none of them turned a
 * test red. A generative system needs its quality asserted, or the only
 * regression detector is a person with a screenshot.
 *
 * <p>The bars are deliberately loose. They are there to catch a <em>collapse</em>
 * — a layout that drowns its town, or scatters it beyond walking distance, or
 * puts two buildings on one plot — not to pin down a number somebody will have
 * to keep updating. A test that fails when a town gets slightly worse is a test
 * that gets deleted.
 */
class LayoutFitnessTest {

    private static final int STEPS = 700;
    private static final SimPos CENTRE = new SimPos(0, 72, 0);

    /**
     * Every arrangement a culture can ask for, by the id the culture names.
     *
     * <p>Gathered from the table rather than listed, because the list had gone
     * stale twice over: ring_streets is what every vale town is built in and had
     * never been grown here at all, and the two arrangements a people had just
     * been given would have gone the same way. A fitness suite that has to be
     * remembered is a fitness suite that misses the thing nobody remembered.
     */
    private static List<String> layouts() {
        Set<String> named = new LinkedHashSet<>();
        for (Culture culture : Culture.all()) {
            named.addAll(culture.layouts());
        }
        return List.copyOf(named);
    }

    /**
     * Grown once per arrangement and shared by every rule below.
     *
     * <p>Seven hundred steps of a whole settlement is not cheap, and the first
     * draft of this grew one per assertion — five layouts by four rules, twenty
     * towns, and a test suite nobody would run twice. The rules ask different
     * questions of the same town, which is what a fixture is for.
     */
    private static final Map<String, Settlement> GROWN = new HashMap<>();

    private static synchronized Settlement town(String layout, TerrainFake ground) {
        return GROWN.computeIfAbsent(layout, id -> grow(id, ground));
    }

    private static Settlement grow(String layout, TerrainFake ground) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Fitness", CENTRE, 512);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        for (Culture culture : Culture.all()) {
            if (culture.layouts().contains(layout)) {
                town.setCultureId(culture.id());
                break;
            }
        }
        // Named outright, after the culture, because a people builds in several
        // arrangements now and picks between them by where the town stands.
        // Leaving that to the hash would grow whichever town this centre happens
        // to choose and quietly stop testing the rest.
        town.setLayoutId(layout);
        assertEquals(layout, town.arrangement().id(),
                "the fixture did not actually select " + layout);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(
                    Person.Id.random(), name, Profession.PIONEER, CENTRE));
        }
        for (int step = 1; step <= STEPS; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }
        return town;
    }

    /** A building's plot claim, read the way the planners read it. */
    private static int span(Settlement town, Building building) {
        return BuildPlanner.plotSpanOf(building.blueprintId(), town.catalogue());
    }

    private static List<Building> onGround(Settlement town) {
        List<Building> holding = new ArrayList<>();
        for (Building b : town.buildings()) {
            if (BuildPlanner.holdsGround(b.blueprintId())) {
                holding.add(b);
            }
        }
        return holding;
    }

    @Test
    void noLayoutStandsItsTownInTheWater() {
        // The one rule that is never a preference. A building in a river reads as
        // broken however sound it is, and the town always had somewhere else to
        // go -- ninety-six candidates and it only needed one.
        TerrainFake ground = new TerrainFake(11);
        for (String layout : layouts()) {
            Settlement town = town(layout, ground);
            List<Building> wet = new ArrayList<>();
            for (Building b : onGround(town)) {
                if (ground.wetAt(b.origin().x(), b.origin().z())) {
                    wet.add(b);
                }
            }
            assertTrue(wet.isEmpty(),
                    layout + " put " + wet.size() + " of " + onGround(town).size()
                            + " buildings in the water, first at "
                            + (wet.isEmpty() ? "-" : wet.get(0).origin().toString()));
        }
    }

    @Test
    void noLayoutBuildsTwoThingsOnOnePlot() {
        // Cheap to assert, and it has been broken before: a lumber camp was once
        // ordered through the side of the town hall because the urgent path did
        // not check.
        TerrainFake ground = new TerrainFake(11);
        for (String layout : layouts()) {
            Settlement town = town(layout, ground);
            List<Building> holding = onGround(town);
            for (int a = 0; a < holding.size(); a++) {
                for (int b = a + 1; b < holding.size(); b++) {
                    Building one = holding.get(a);
                    Building two = holding.get(b);
                    assertTrue(!BuildPlanner.plotsOverlap(
                                    one.origin(), span(town, one),
                                    two.origin(), span(town, two)),
                            layout + " overlapped " + one.blueprintId() + " at "
                                    + one.origin() + " with " + two.blueprintId()
                                    + " at " + two.origin());
                }
            }
        }
    }

    @Test
    void noLayoutScattersItsTownBeyondWalking() {
        // A settlement is a place people walk across. Food is hauled to a
        // granary, roads are laid between doorsteps, and a town whose buildings
        // are further apart than its people will walk is a set of hamlets that
        // share a name -- which is exactly what a warren was, at 26 people to
        // the ring layout's 96, before its geometry was solved.
        TerrainFake ground = new TerrainFake(11);
        for (String layout : layouts()) {
            Settlement town = town(layout, ground);
            double furthest = 0;
            for (Building b : onGround(town)) {
                furthest = Math.max(furthest, Math.hypot(
                        b.origin().x() - CENTRE.x(), b.origin().z() - CENTRE.z()));
            }
            int limit = sprawlLimitFor(layout);
            assertTrue(furthest < limit,
                    layout + " spread to " + Math.round(furthest)
                            + " blocks, past the " + limit + " it is allowed");
        }
    }

    @Test
    void noLayoutLeavesAFieldBetweenNeighbouringWalls() {
        // What a street of buildings looks like, which nothing was watching.
        //
        // Every other rule here catches a town that has fallen apart. This one
        // catches a town that never came together: buildings a plot apart on
        // ground that would take them at a doorstep, because the plan offered its
        // frontage more coarsely than the siting code demanded and the siting
        // code demanded more than the buildings did. Nothing about it reads as a
        // fault from the inside -- every plot is legal, every door faces its
        // street, the town simply looks like huts in a field.
        //
        // Measured on this fixture before the spacing was derived and after, as
        // the median over every building of the clear blocks to its nearest
        // neighbour's wall, and the tightest of them:
        //
        //   layout                median   tightest
        //   ring                    5  5      3  2
        //   warren                  5  5      3  2
        //   stronghold              9  3      5  2
        //   organic                 5  4      3  2
        //   high_street             5  3      3  2
        //   ring_streets            8  8      3  2
        //   stronghold_streets      7  4      4  2
        //   radial_concentric       9  9      4  2
        //   crossroads              5  4      3  2
        //   bastide                 6  4      3  2
        //   thorp                   5  4      3  2
        //   crescents               7  7      3  2
        //   green                   5  4      3  2
        //
        // Every arrangement now touches the floor of two somewhere, which is two
        // doorsteps meeting and is what the overlap check allows at its tightest.
        //
        // The bar is a floor against a collapse back to the old numbers, not a
        // target. The three circular arrangements barely move, and that is a known
        // and stated cost rather than an oversight: an arc spaced evenly along
        // itself has to clear the wider axis on the diagonal, so it stands root
        // two too wide at the cardinal points. Spacing an arc by the wider axis
        // instead would fix it, and it is a change to how offers are generated
        // rather than to a constant.
        TerrainFake ground = new TerrainFake(11);
        for (String layout : layouts()) {
            Settlement town = town(layout, ground);
            List<Integer> nearest = new ArrayList<>();
            List<Building> holding = onGround(town);
            for (Building a : holding) {
                int closest = Integer.MAX_VALUE;
                for (Building b : holding) {
                    if (a != b) {
                        closest = Math.min(closest, wallGap(a, b));
                    }
                }
                if (closest != Integer.MAX_VALUE) {
                    nearest.add(closest);
                }
            }
            assertTrue(nearest.size() >= MIN_BUILDINGS,
                    layout + " built too little to say anything about its spacing");
            java.util.Collections.sort(nearest);
            int median = nearest.get(nearest.size() / 2);
            assertTrue(median <= CROWDING_LIMIT,
                    layout + " left a median " + median + " blocks between a wall and"
                            + " its nearest neighbour's, past the " + CROWDING_LIMIT
                            + " a town reads as a town at");
        }
    }

    /**
     * The clear blocks between two buildings' walls, on the axis that parts them.
     *
     * <p>The same measure the overlap check uses, in the same metric: two boxes
     * are kept apart on one axis and may overlap freely on the other, so what
     * anybody standing between them sees is the gap on the axis that separates
     * them. Read off {@link BuildingSizes} and the way the building was turned,
     * because a plot span is a claim and this is about walls.
     */
    private static int wallGap(Building a, Building b) {
        int[] one = halfWalls(a);
        int[] two = halfWalls(b);
        int alongX = Math.abs(a.origin().x() - b.origin().x()) - one[0] - two[0] - 1;
        int alongZ = Math.abs(a.origin().z() - b.origin().z()) - one[1] - two[1] - 1;
        return Math.max(alongX, alongZ);
    }

    /** How far a building's walls reach either side of its origin, as it stands. */
    private static int[] halfWalls(Building of) {
        BuildingSizes.Size size = BuildingSizes.of(of.blueprintId());
        int width = size == null ? Layout.DEFAULT_SPAN - 2 : size.width();
        int depth = size == null ? Layout.DEFAULT_SPAN - 2 : size.depth();
        boolean turned = of.facing() % 2 != 0;   // a quarter turn swaps the two
        return new int[] {(turned ? depth : width) / 2, (turned ? width : depth) / 2};
    }

    /**
     * How much bare ground may stand between neighbouring walls before a town
     * stops reading as one.
     *
     * <p>Ten, against a measured worst of nine and a median of four across the
     * thirteen arrangements. Loose on purpose, like every bar in this file: it is
     * here to catch a plan that has gone back to offering frontage at a pitch
     * nothing can close up, not to pin a number somebody has to keep right.
     */
    private static final int CROWDING_LIMIT = 10;

    @Test
    void everyLayoutActuallyGrowsATown() {
        // The floor under all of it. A layout that refuses everything passes
        // every rule above by building nothing, and this is what stops that
        // reading as success.
        TerrainFake ground = new TerrainFake(11);
        for (String layout : layouts()) {
            Settlement town = town(layout, ground);
            assertTrue(town.population() >= MIN_POPULATION,
                    layout + " reached only " + town.population() + " people");
            assertTrue(onGround(town).size() >= MIN_BUILDINGS,
                    layout + " raised only " + onGround(town).size() + " buildings");
        }
    }

    /**
     * How far a town may spread before it stops being one.
     *
     * <p>Generous on purpose. Measured towns run to about 190 blocks and the
     * warren, before its geometry was fixed, ran to 476 while holding a quarter
     * of the people. This sits between: it catches a collapse and lets ordinary
     * variation past.
     */
    private static final int SPRAWL_LIMIT = 340;

    /**
     * What a warren is allowed, which is more, and is not approval.
     *
     * <p>The warren sprawls by construction: knots of six flung along a spiral,
     * with open ground between them by design. On this terrain it reaches 430
     * where every other arrangement holds inside 340. That is the open goal
     * about the void between knots exceeding what anybody will walk, and it is
     * recorded here as a <em>ceiling</em> rather than waived — the number is
     * what it measures today, so the layout cannot quietly get worse while the
     * goal waits. Lower it when the goal is done; do not raise it.
     *
     * <p><strong>It was raised once, from 420, and this is the reason.</strong>
     * Not the warren getting worse: the buildings did not fit in it any more.
     * A knot is a hexagon of radius sixteen, which puts neighbouring huts about
     * fourteen apart on the wider axis, and that was solved when the biggest
     * thing in a town claimed eleven. Buildings now claim between five and
     * twenty-five, because they are drawn at the size the catalogue reserves
     * for them rather than at a fifth of it — so anything past a house is
     * refused by every slot in the knot and the plot cursor walks outward until
     * it finds ground. Measured furthest-out on this seed: two farms at 416,
     * both of which claimed fifteen before any of this and still do.
     *
     * <p>Widening the knot is not the answer and was checked: a radius that
     * held a span-fifteen building is nineteen, and
     * {@code Layouts.WARREN}'s three constants are solved together against the
     * first thirty plots — pushing them out makes the warren <em>bigger</em>,
     * which is the thing this number exists to watch. The real answer is the
     * open goal it already names.
     */
    private static final int WARREN_SPRAWL_CEILING = 460;

    /**
     * What crescents is allowed, which is more, and is not approval either.
     *
     * <p>The sprawliest of the arrangements that draw streets, and it was
     * already within nine blocks of the shared limit before anything here
     * changed: 331 of 340, where the other ten sit between 157 and 253. Its
     * lanes hang off the spine in a chain rather than round a middle, so the
     * plan itself reaches further per plot than any other — plot two hundred
     * stands 295 blocks out where the green's stands 174 — and a town that has
     * had a third of its plots refused for ground is well down that chain.
     *
     * <p>It went over at 358 when buildings started being drawn at the size the
     * catalogue reserves for them, which is more work apiece and so a bigger
     * town for the same number of steps. Measured in three parts, on the same
     * seed and the same seven hundred steps:
     *
     * <pre>
     *   331   before any of it
     *   359   buildings drawn at their declared size
     *   401   and brought up to the kerb, whole-distance
     *   358   and brought in only as far as the rank allows
     * </pre>
     *
     * <p>So the kerb costs nothing once it is asked of the rank rather than of
     * the plot, and the twenty-seven that remain are the buildings being bigger,
     * which is the change and not a side effect of it. Recorded as a ceiling for
     * the same reason the warren's is: the number is what it measures today, so
     * the layout cannot quietly get worse. Lower it when the chain is shortened;
     * do not raise it.
     *
     * <p><strong>345 now</strong>, thirteen better, from the plot separation
     * coming down to what two plots of the default span actually need. Left at
     * 380 rather than pulled down to what that measures, and the reason is worth
     * recording: this arrangement's spread is a <em>cliff</em>, not a slope. The
     * same change with the crescents' rank gap two blocks tighter measured 433,
     * and six tighter 390 — not because the lanes are closer but because a
     * station that loses one plot leaves the plan short of its count, and a short
     * plan is re-laid at twice the size with a third rank nested at every station.
     * A ceiling set on the sunny side of a cliff is a ceiling that goes red for
     * reasons nobody can read. The note is on {@code CrescentLayout.RANK_GAP}.
     */
    private static final int CRESCENTS_SPRAWL_CEILING = 380;

    private static int sprawlLimitFor(String layout) {
        if (Culture.LAYOUT_WARREN.equals(layout)) {
            return WARREN_SPRAWL_CEILING;
        }
        if (Culture.LAYOUT_CRESCENTS.equals(layout)) {
            return CRESCENTS_SPRAWL_CEILING;
        }
        return SPRAWL_LIMIT;
    }

    private static final int MIN_POPULATION = 20;
    private static final int MIN_BUILDINGS = 12;
}
