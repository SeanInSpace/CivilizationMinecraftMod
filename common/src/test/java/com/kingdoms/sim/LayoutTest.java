package com.kingdoms.sim;

import com.kingdoms.sim.culture.CrossroadsLayout;
import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.Layout;
import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.culture.GridStreetLayout;
import com.kingdoms.sim.culture.RadialStreetLayout;
import com.kingdoms.sim.culture.StreetLayout;
import com.kingdoms.sim.culture.TownPlan;
import com.kingdoms.sim.culture.Wander;
import com.kingdoms.sim.geom.SimPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a people arranges a town, and the three rules every arrangement keeps.
 *
 * <p>These are not style rules. The siting code assumes all three, and a layout
 * that breaks one produces a town that cannot build: it re-plans itself on
 * reload, or hands the same ground out twice, or proposes plots so close
 * together that the overlap check rejects every one of them and nothing is ever
 * raised. So they are checked for every arrangement that exists rather than for
 * the one somebody happened to be working on.
 */
class LayoutTest {

    private static final SimPos CENTRE = new SimPos(0, 64, 0);

    /** Enough plots to cover several rings, several clumps and several grid legs. */
    private static final int MANY = 120;

    // --- the three rules, for every layout there is ---

    @Test
    void everyLayoutIsDeterministic() {
        // The whole suite leans on replayability, and a town that re-planned
        // itself on reload would rebuild its own streets somewhere else.
        for (Layout layout : Layouts.all()) {
            for (int i = 0; i < MANY; i++) {
                assertEquals(layout.plotFor(CENTRE, i), layout.plotFor(CENTRE, i),
                        layout.id() + " gave two answers for plot " + i);
            }
        }
    }

    @Test
    void everyLayoutHandsOutEachPieceOfGroundOnce() {
        // An index is spent when a plot is taken. A layout that repeated itself
        // would have the town demolish a building to raise one.
        for (Layout layout : Layouts.all()) {
            Set<SimPos> seen = new HashSet<>();
            for (int i = 0; i < MANY; i++) {
                assertTrue(seen.add(layout.plotFor(CENTRE, i)),
                        layout.id() + " reused a position at plot " + i);
            }
        }
    }

    @Test
    void theNewArrangementsLeaveRoomToBuild() {
        // Closer than this and a candidate fails the overlap check and is thrown
        // away. RING is excluded and measured separately, below, because it has
        // never kept this rule and moving it now would shift the first ring of
        // every town that already exists.
        //
        // Measured with Layout.farEnoughApart, which is the whole point of that
        // predicate existing: this test used to compare straight-line distance
        // while the siting code refused on a box, so WARREN passed it for
        // months while a third of its plots were being thrown away in play.
        // A test in the wrong units is worse than no test -- it certifies the
        // fault.
        for (Layout layout : List.of(Layouts.WARREN, Layouts.STRONGHOLD,
                Layouts.ORGANIC, Layouts.HIGH_STREET, Layouts.CROSSROADS)) {
            SimPos[] plots = new SimPos[MANY];
            for (int i = 0; i < MANY; i++) {
                plots[i] = layout.plotFor(CENTRE, i);
            }
            for (int a = 0; a < MANY; a++) {
                for (int b = a + 1; b < MANY; b++) {
                    assertTrue(Layout.farEnoughApart(plots[a], plots[b]),
                            layout.id() + " put plots " + a + " and " + b
                                    + " at " + plots[a] + " and " + plots[b]
                                    + " — only " + Math.max(
                                            Math.abs(plots[a].x() - plots[b].x()),
                                            Math.abs(plots[a].z() - plots[b].z()))
                                    + " apart on the wider axis");
                }
            }
        }
    }

    @Test
    void everyLayoutBuildsAroundTheCentreItIsGiven() {
        SimPos elsewhere = new SimPos(3000, 70, -1500);
        for (Layout layout : Layouts.all()) {
            SimPos here = layout.plotFor(CENTRE, 3);
            SimPos there = layout.plotFor(elsewhere, 3);
            assertEquals(elsewhere.y(), there.y(),
                    layout.id() + " invented a height; that is the survey's job");

            // A lattice is the same shape wherever it is put, and that is worth
            // holding: it is what makes rings rings. Some arrangements are
            // deliberately not — their throws or their bends are seeded from the
            // town's own centre, so two villages of the same people are not the
            // same village twice. The layout says which it is, rather than this
            // test naming the exceptions: as a list of ids it was really the rule
            // "every layout except the ones that break it", and it would have
            // silently covered up the next one. What the rule is actually for is
            // that a layout must build around the centre it is HANDED rather than
            // one it remembers, and that is asserted for all of them below.
            if (layout.isSameShapeEverywhere()) {
                assertEquals(there.x() - elsewhere.x(), here.x() - CENTRE.x(),
                        layout.id() + " is not the same shape somewhere else");
                assertEquals(there.z() - elsewhere.z(), here.z() - CENTRE.z(),
                        layout.id() + " is not the same shape somewhere else");
            }

            assertTrue(near(here, CENTRE) && near(there, elsewhere),
                    layout.id() + " put a plot nowhere near the centre it was given");
        }
    }

    /** Whether a plot is close enough to be part of that centre's town at all. */
    private static boolean near(SimPos plot, SimPos centre) {
        return Math.max(Math.abs(plot.x() - centre.x()),
                        Math.abs(plot.z() - centre.z())) < 400;
    }

    @Test
    void twoOrganicTownsAreNotTheSameTownTwice() {
        // The property the exemption above is buying. A scatter that repeated
        // itself would be a lattice with extra steps.
        SimPos elsewhere = new SimPos(3000, 70, -1500);
        int same = 0;
        for (int i = 0; i < MANY; i++) {
            SimPos a = Layouts.ORGANIC.plotFor(CENTRE, i);
            SimPos b = Layouts.ORGANIC.plotFor(elsewhere, i);
            if (a.x() - CENTRE.x() == b.x() - elsewhere.x()
                    && a.z() - CENTRE.z() == b.z() - elsewhere.z()) {
                same++;
            }
        }
        assertTrue(same < MANY / 4,
                "two organic towns came out " + same + " plots identical of " + MANY);
    }

    @Test
    void noPlanPutsAPlotInTheRoad() {
        // A plan that lays houses on its own streets is worse than one with no
        // streets at all: the path layer paves through the building and the only
        // report is a player wondering why the road goes through a front room.
        // Fifteen of a hundred and forty did, on the first plan that had streets
        // to check against -- which is a fault that had nowhere to be seen until
        // the plan became a thing that could be looked at.
        // Walked a segment at a time, because a street bends. Checking only the
        // two ends of a wandering road tests a straight line the road is not on.
        for (Layout layout : Layouts.all()) {
            TownPlan plan = layout.planFor(CENTRE, 140);
            for (TownPlan.Plot plot : plan.plots()) {
                for (TownPlan.Street street : plan.streets()) {
                    assertFalse(street.touches(plot.at(), plot.span() / 2.0),
                            layout.id() + " put a plot at " + plot.at()
                                    + " standing on a " + street.kind() + " street");
                }
            }
        }
    }

    @Test
    void aStreetsFrontageFollowsItsBends() {
        // The trap in curving a street is curving only the picture. The setback
        // is measured from the centreline, so if the drawn road bends and the
        // frontage does not, the gap closes and the house ends up on the kerb --
        // which is exactly the fault this layout has already had once, from a
        // different cause. So: every plot that fronts a street must actually be
        // near that street, however much the street wanders.
        StreetLayout bendy = new StreetLayout("bendy", new Wander(9, 80, 4242L));
        TownPlan plan = bendy.planFor(CENTRE, 120);
        int fronting = 0;
        for (TownPlan.Plot plot : plan.plots()) {
            TownPlan.Street street = plan.streetOf(plot);
            if (street == null) {
                continue;
            }
            fronting++;
            double nearest = Double.MAX_VALUE;
            List<SimPos> path = street.path();
            for (int i = 1; i < path.size(); i++) {
                nearest = Math.min(nearest, distanceToSegment(
                        plot.at(), path.get(i - 1), path.get(i)));
            }
            // The bar is the deepest setback the plan uses, not the ordinary
            // one: the market widening deliberately stands SETBACK + MARKET_EXTRA
            // back -- twenty-five blocks -- because a market is a place with room
            // in it. Plus the slack a bend introduces between the point the
            // offset was read at and the nearest point on the road.
            assertTrue(nearest <= 34,
                    "a plot at " + plot.at() + " claims to front a " + street.kind()
                            + " street it stands " + Math.round(nearest) + " blocks from");
        }
        assertTrue(fronting >= 60, "only " + fronting + " plots fronted anything");
    }

    @Test
    void aPlannedTownActuallyFrontsItsStreets() {
        // The point of drawing streets first is that the buildings stand on
        // them. A plan that lays roads and then puts its houses in the outskirts
        // has the cost of streets and none of the benefit -- and it fails
        // quietly, because the streets are still there in the picture and the
        // town still reaches its plot count. Every collapse this layout has had
        // showed up here first and nowhere else:
        //
        //   ring roads pitched like a straight street        20%
        //   ring faces spaced on the centreline              25%
        //   plot square expanded into a square, not a disc   25%
        //   spokes that refuse frontage and offer none       62%
        //   with frontage on the spokes                      72%
        //   asking the design again when too few survive    100%
        //
        // The bar is a floor against collapse, not a target. Every planned
        // arrangement measures 100% today, at every size, because a plan that
        // comes up short now asks its design for more frontage instead of
        // trusting an estimate of its own capacity.
        for (Layout layout : Layouts.all()) {
            if (!Layouts.isStreetsFirst(layout)) {
                continue;
            }
            for (int wanted : new int[] {24, 60, 140}) {
                TownPlan plan = fresh(layout).planFor(CENTRE, wanted);
                assertTrue(plan.frontagePercent() >= 95,
                        layout.id() + " at " + wanted + " plots fronted only "
                                + plan.frontagePercent() + "% of them on its own streets");
            }
        }
    }

    /**
     * A layout of the same kind with an empty plan cache.
     *
     * <p>Planned layouts remember plans by the town's x and z, and every test in
     * this JVM shares the one instance the registry holds. Asking the shared one
     * for a town of twenty-four at the origin returns whatever the largest town
     * any other test grew there was — which is how a measurement of the plan at
     * three sizes came back as the same three-hundred-plot answer three times.
     */
    private static Layout fresh(Layout like) {
        if (like instanceof StreetLayout s) {
            return new StreetLayout(s.id(), s.wander());
        }
        if (like instanceof RadialStreetLayout r) {
            // With the hall, which the two-argument constructor drops. Rebuilt
            // without it, radial_concentric came back as a plain ring_streets
            // wearing the other one's name, so every rule that runs through here
            // was certifying an arrangement nobody ships.
            return new RadialStreetLayout(r.id(), r.wander(), r.hallOnTheGreen());
        }
        if (like instanceof GridStreetLayout g) {
            return new GridStreetLayout(g.id(), g.wander());
        }
        if (like instanceof CrossroadsLayout c) {
            return new CrossroadsLayout(c.id(), c.wander());
        }
        return like;
    }

    @Test
    void everyStreetsFirstArrangementCanBeSwappedBackForItsLattice() {
        // Streets-first changes the shape of every settlement in a world, which
        // is not a decision to make on anybody's behalf permanently. Both
        // directions exist and both round-trip, so a culture can name either and
        // a world that liked its old towns keeps them.
        for (Layout lattice : List.of(Layouts.RING, Layouts.STRONGHOLD)) {
            Layout streets = Layouts.streetsFirst(lattice);
            assertNotEquals(lattice.id(), streets.id(),
                    lattice.id() + " has no streets-first counterpart");
            assertTrue(Layouts.isStreetsFirst(streets),
                    streets.id() + " does not actually draw its streets first");
            assertFalse(Layouts.isStreetsFirst(lattice),
                    lattice.id() + " was supposed to be the lattice");
            assertSame(lattice, Layouts.lattice(streets),
                    "could not get back from " + streets.id());
            assertTrue(streets.planFor(CENTRE, 60).frontagePercent() > 0,
                    streets.id() + " draws streets nothing fronts");
        }

        // The ones with no counterpart answer with themselves, so a caller can
        // swap a whole table over without special-casing them.
        for (Layout alone : List.of(Layouts.WARREN, Layouts.ORGANIC)) {
            assertSame(alone, Layouts.streetsFirst(alone),
                    alone.id() + " invented streets it has no business having");
            assertSame(alone, Layouts.lattice(alone));
        }
    }

    @Test
    void noArrangementBendsItsStreetsTooSteeplyToBuildOn() {
        // The failure this catches is quiet and expensive: a street that leans
        // faster than its plot pitch pushes each plot sideways into the next, the
        // offers foul each other, and the town keeps its shape while losing its
        // frontage to the outskirts. At a slope of 0.46 a hundred and forty plots
        // dropped from every one fronting a street to sixty-nine per cent, and
        // the town got forty per cent wider doing it. Nothing about that reads as
        // a bug from the outside -- it just looks like a worse town.
        for (Layout layout : Layouts.all()) {
            if (layout instanceof StreetLayout streets) {
                assertTrue(streets.wander().slope() <= Wander.SAFE_SLOPE + 1e-9,
                        layout.id() + " bends at " + streets.wander().slope()
                                + ", past the " + Wander.SAFE_SLOPE + " its frontage survives");
            }
        }
    }

    @Test
    void aBendingStreetIsStillTheSameStreetOnReload() {
        // Determinism is the first of the three rules and the wander is the
        // newest thing that could break it: a town whose streets re-rolled on
        // load would rebuild its own roads somewhere else every session.
        StreetLayout a = new StreetLayout("bendy", new Wander(9, 80, 4242L));
        StreetLayout b = new StreetLayout("bendy", new Wander(9, 80, 4242L));
        assertEquals(a.planFor(CENTRE, 80).streets(), b.planFor(CENTRE, 80).streets(),
                "the same town laid different streets on a second run");
        assertEquals(a.planFor(CENTRE, 80).plots(), b.planFor(CENTRE, 80).plots(),
                "the same town laid different plots on a second run");
    }

    @Test
    void twoTownsDoNotBendAlike() {
        // A wander seeded only by the layout would give every settlement on the
        // map the identical kink in the identical place, which reads worse than
        // a straight road because it reads as a repeated asset.
        StreetLayout bendy = new StreetLayout("bendy", new Wander(9, 80, 4242L));
        List<SimPos> here = bendy.planFor(CENTRE, 60).streets().get(0).path();
        List<SimPos> there = bendy.planFor(new SimPos(2048, 72, -1024), 60)
                .streets().get(0).path();
        int same = 0;
        for (int i = 0; i < Math.min(here.size(), there.size()); i++) {
            // Compared as offsets from each town's own centre, or every point
            // differs for the trivial reason that the towns are far apart.
            if (here.get(i).x() - CENTRE.x() == there.get(i).x() - 2048) {
                same++;
            }
        }
        assertTrue(same < Math.min(here.size(), there.size()),
                "two towns bent their spine identically at every one of "
                        + same + " points");
    }

    /** How far a point lies from a segment, for the frontage check above. */
    private static double distanceToSegment(SimPos p, SimPos a, SimPos b) {
        double vx = b.x() - a.x();
        double vz = b.z() - a.z();
        double len = vx * vx + vz * vz;
        if (len == 0) {
            return Math.hypot(p.x() - a.x(), p.z() - a.z());
        }
        double t = ((p.x() - a.x()) * vx + (p.z() - a.z()) * vz) / len;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(p.x() - (a.x() + t * vx), p.z() - (a.z() + t * vz));
    }

    // --- and that they are actually different from each other ---

    @Test
    void theThreeArrangementsAreGenuinelyDifferentTowns() {
        // The claim the whole type exists to make. If a warren and a village
        // came out the same shape, culture would be decoration.
        Set<SimPos> ring = plotSet(Layouts.RING);
        Set<SimPos> warren = plotSet(Layouts.WARREN);
        Set<SimPos> stronghold = plotSet(Layouts.STRONGHOLD);

        assertTrue(overlap(ring, warren) < 4, "rings and warrens are not the same town");
        assertTrue(overlap(ring, stronghold) < 4, "rings and grids are not the same town");
        assertTrue(overlap(warren, stronghold) < 4, "warrens and grids are not the same town");
    }

    @Test
    void theVillagesInnermostRingIsTooTightForItsOwnPlots() {
        // A defect, recorded rather than hidden, and not introduced here: with
        // eight slots at a radius of twelve, neighbouring plots sit four across
        // and eight deep, and a pair that close on BOTH axes fouls. So the
        // innermost ring of every human town cannot hold two neighbouring
        // buildings, and the overlap check quietly refuses one of each pair.
        //
        // Harmless enough to have gone unnoticed -- an index is only spent when
        // a plot is actually taken, so the town shrugs and tries the next one --
        // but it means the first ring never holds what the arithmetic says it
        // holds. Fixing it moves the first ring of every existing town, which is
        // not a thing to do quietly on the way past.
        SimPos first = Layouts.RING.plotFor(CENTRE, 0);
        SimPos second = Layouts.RING.plotFor(CENTRE, 1);
        assertFalse(Layout.farEnoughApart(first, second),
                "if this ever passes, the first ring was widened and this note is stale");
        assertEquals(8, Math.max(Math.abs(first.x() - second.x()),
                        Math.abs(first.z() - second.z())),
                "and it is eight on the wider axis, not nothing");
    }

    @Test
    void aWarrenKeepsItsKnotsRatherThanBecomingAScatter() {
        // Separation alone is not enough to describe this layout, and solving
        // for separation alone very nearly destroyed it. Pulling the knots
        // together until every plot cleared the overlap box left huts in
        // NEIGHBOURING knots sitting closer than huts in the same knot -- which
        // passes every rule and is no longer a warren. It is a scatter with the
        // same plot count.
        //
        // So the thing the layout exists to be is asserted too: from above, a
        // knot has to read as a knot.
        int perClump = 6;
        double within = Double.MAX_VALUE;
        double between = Double.MAX_VALUE;
        SimPos[] plots = new SimPos[60];
        for (int i = 0; i < plots.length; i++) {
            plots[i] = Layouts.WARREN.plotFor(CENTRE, i);
        }
        for (int a = 0; a < plots.length; a++) {
            for (int b = a + 1; b < plots.length; b++) {
                double gap = plots[a].horizontalDistance(plots[b]);
                if (a / perClump == b / perClump) {
                    within = Math.min(within, gap);
                } else {
                    between = Math.min(between, gap);
                }
            }
        }
        assertTrue(between > within,
                "huts in different knots are " + Math.round(between)
                        + " apart and huts in the same knot " + Math.round(within)
                        + " — the knots have dissolved into a scatter");
    }

    @Test
    void aWarrenIsNotBuiltInRingsAtAll() {
        // The structural difference, measured rather than eyeballed. A village
        // puts every plot on one of a handful of radii -- that is what a ring
        // is. A warren's knots sit wherever the last one budded, so its plots
        // are scattered across many different distances from the centre.
        assertTrue(distinctRadii(Layouts.WARREN) > 3 * distinctRadii(Layouts.RING),
                "a warren has no rings to speak of; a village is nothing but rings");
    }

    @Test
    void aStrongholdIsLaidOutInRowsAndAVillageIsNot() {
        // Everything in a grid shares a column with something else. That is what
        // makes it read as regimented from the air.
        assertTrue(sharesColumn(Layouts.STRONGHOLD) > sharesColumn(Layouts.RING),
                "a stronghold lines up and a village deliberately does not");
    }

    // --- how a culture picks one ---

    @Test
    void aCulturePicksItsOwnArrangement() {
        assertSame(Layouts.RING, Culture.NORMAN.arrangement());
        assertSame(Layouts.WARREN, Culture.GOBLIN.arrangement());
        assertSame(Layouts.STRONGHOLD, Culture.ORC.arrangement());
    }

    @Test
    void anUnknownArrangementFallsBackToRingsRatherThanThrowing() {
        // The one lookup guaranteed to happen on a world saved before layouts
        // existed is the one that carries no name at all.
        assertSame(Layouts.RING, Layouts.of(null));
        assertSame(Layouts.RING, Layouts.of("nothing_by_that_name"));
    }

    @Test
    void everyDefinedCultureNamesAnArrangementThatExists() {
        // A culture whose layout id is a typo would silently become a village.
        for (Culture culture : Culture.all()) {
            assertSame(Layouts.of(culture.layout()), culture.arrangement(),
                    culture.id() + " names an arrangement nothing provides");
            assertEquals(culture.layout(), culture.arrangement().id(),
                    culture.id() + " asks for one arrangement and gets another");
        }
    }

    @Test
    void thePeoplesDoNotShareEachOthersNames() {
        assertNotEquals(Culture.NORMAN.familyNames(), Culture.GOBLIN.familyNames());
        assertNotEquals(Culture.GOBLIN.givenNames(), Culture.ORC.givenNames());
        for (Culture culture : Culture.all()) {
            assertTrue(!culture.townNames().isEmpty(), culture.id() + " has nowhere to live");
            assertTrue(!culture.familyNames().isEmpty(), culture.id() + " has no families");
            assertTrue(!culture.givenNames().isEmpty(), culture.id() + " has no children");
        }
    }

    // --- helpers ---

    private static Set<SimPos> plotSet(Layout layout) {
        Set<SimPos> plots = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            plots.add(layout.plotFor(CENTRE, i));
        }
        return plots;
    }

    private static int overlap(Set<SimPos> a, Set<SimPos> b) {
        int shared = 0;
        for (SimPos pos : a) {
            if (b.contains(pos)) {
                shared++;
            }
        }
        return shared;
    }

    /** How many different distances from the centre the first forty plots sit at. */
    private static int distinctRadii(Layout layout) {
        Set<Long> radii = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            radii.add(Math.round(CENTRE.horizontalDistance(layout.plotFor(CENTRE, i))));
        }
        return radii.size();
    }

    /** How many of the first forty plots share an x with another plot. */
    private static int sharesColumn(Layout layout) {
        int[] xs = new int[40];
        for (int i = 0; i < 40; i++) {
            xs[i] = layout.plotFor(CENTRE, i).x();
        }
        int lined = 0;
        for (int a = 0; a < xs.length; a++) {
            for (int b = 0; b < xs.length; b++) {
                if (a != b && xs[a] == xs[b]) {
                    lined++;
                    break;
                }
            }
        }
        return lined;
    }

    // --- the compass-drawn town ---

    /**
     * The radial-concentric arrangement draws true circles, not bent ones.
     *
     * <p>It exists precisely because {@code ring_streets} bends its rings by up
     * to nine blocks to look grown rather than drawn. If somebody gives this one
     * a wander -- by copying the other's constructor call, which is the obvious
     * way to make the mistake -- the two arrangements become the same
     * arrangement and the reason for having both quietly disappears.
     */
    @Test
    void radialConcentricRingsAreTrueCircles() {
        TownPlan plan = fresh(Layouts.of(Culture.LAYOUT_RADIAL_CONCENTRIC))
                .planFor(CENTRE, MANY);
        for (TownPlan.Street street : plan.streets()) {
            if (street.kind() != TownPlan.Kind.LANE) {
                continue;   // spokes are straight lines, not rings
            }
            double first = CENTRE.horizontalDistance(street.path().get(0));
            for (SimPos point : street.path()) {
                assertEquals(first, CENTRE.horizontalDistance(point), 1.5,
                        "a ring road should hold its radius all the way round");
            }
        }
    }

    /**
     * Something stands in the middle of the green.
     *
     * <p>A town drawn round a centre with nothing at the centre reads as a
     * roundabout. The hall goes on the green because the plan offers the middle
     * and offers are taken nearest-first, so this is really a test that the
     * offer survives the sort and the fits check -- both of which have refused
     * it during development, and both silently.
     */
    @Test
    void radialConcentricPutsSomethingInTheMiddle() {
        TownPlan plan = fresh(Layouts.of(Culture.LAYOUT_RADIAL_CONCENTRIC))
                .planFor(CENTRE, MANY);
        assertEquals(CENTRE.x(), plan.plots().get(0).at().x(),
                "the first plot should be the middle of the green");
        assertEquals(CENTRE.z(), plan.plots().get(0).at().z(),
                "the first plot should be the middle of the green");
        assertFalse(plan.plots().get(0).frontsAStreet(),
                "the plot on the green fronts nothing, and must say so");
    }

    /**
     * Everything else does front a street.
     *
     * <p>One unfronted plot is the price of having a middle. Two would mean the
     * ring faces had stopped being offered, which is the failure this layout's
     * whole spacing arithmetic exists to prevent and which does not announce
     * itself -- the town simply grows outward hunting for room.
     */
    @Test
    void radialConcentricFrontsEverythingElse() {
        TownPlan plan = fresh(Layouts.of(Culture.LAYOUT_RADIAL_CONCENTRIC))
                .planFor(CENTRE, MANY);
        int adrift = 0;
        for (TownPlan.Plot plot : plan.plots()) {
            if (!plot.frontsAStreet()) {
                adrift++;
            }
        }
        assertEquals(1, adrift, "only the hall on the green should front nothing");
    }

    // --- the town where two roads met ---

    /**
     * Half-width of the open ground the crossroads leaves at its crossing.
     *
     * <p>Restated here rather than read off the layout, deliberately: this is the
     * bar the arrangement is being held to, and a test that reads its expectation
     * out of the thing under test asserts only that the code agrees with itself.
     */
    private static final int MARKET_HALF = 20;

    /**
     * How far from its own spine anything in a crossroads town may stand.
     *
     * <p>The ribs hold two plots a side, the outermost of them forty blocks out,
     * so no plot is ever further than forty from one spine or the other. Six
     * blocks of slack on that, and no more: a rib lengthened to hold a third plot
     * puts it fifty-four out, and that is precisely the change this is here to
     * catch. It was measured — at fifty-four the arms and the ribs come out 2.3
     * to one and the town draws as a square with a hole in it; at forty they are
     * 4.2 to one and it draws as a cross.
     */
    private static final int RIB_BAND = 46;

    /**
     * Nothing stands on the market square.
     *
     * <p>The one part of this plan that exists by not being built on, which makes
     * it the one part nothing else would notice the loss of. Every other
     * invariant here is satisfied by a town that quietly fills its own middle in:
     * the plots still front streets, still clear each other, still keep out of
     * the road. Measured on the building rather than on its centre, because a
     * market with the corners of four houses in it is not open ground.
     */
    @Test
    void aCrossroadsLeavesItsMarketSquareEmpty() {
        TownPlan plan = fresh(Layouts.CROSSROADS).planFor(CENTRE, 140);
        for (TownPlan.Plot plot : plan.plots()) {
            double clear = Math.max(Math.abs(plot.at().x() - CENTRE.x()),
                    Math.abs(plot.at().z() - CENTRE.z())) - plot.span() / 2.0;
            assertTrue(clear >= MARKET_HALF,
                    "a building at " + plot.at() + " stands "
                            + Math.round(MARKET_HALF - clear) + " blocks into the market");
        }
    }

    /**
     * It is a cross with ribs, and it is not a grid.
     *
     * <p>The whole identity of this arrangement, and the one thing about it that
     * could be lost without a single other test going red. Let the ribs run on
     * until they meet the ribs of the other spine and the four quarters between
     * the arms close up: the frontage would be as good, the plots as far apart,
     * the roads as clear — and the town would be {@code stronghold_streets} with
     * a hole in the middle, which is an arrangement that already exists.
     *
     * <p>So: every plot is near one spine or the other, and the town runs far
     * further along the spines than it ever reaches across them. A grid fails the
     * first line on its very first corner block.
     */
    @Test
    void aCrossroadsIsACrossAndNotAGrid() {
        CrossroadsLayout town = (CrossroadsLayout) fresh(Layouts.CROSSROADS);
        // Measured from the middle of the town rather than from the spine, which
        // is the same thing only while the spines are straight. A bend carries
        // the whole arm off the axis with it, so the bar has to make room for it
        // or a wandering crossroads goes red for its wander and not for its shape.
        int band = RIB_BAND + town.wander().amplitude();
        TownPlan plan = town.planFor(CENTRE, 140);
        int along = 0;
        int across = 0;
        for (TownPlan.Plot plot : plan.plots()) {
            int dx = Math.abs(plot.at().x() - CENTRE.x());
            int dz = Math.abs(plot.at().z() - CENTRE.z());
            assertTrue(Math.min(dx, dz) <= band,
                    "a plot at " + plot.at() + " stands " + Math.min(dx, dz)
                            + " blocks from the nearer spine — the quarters between"
                            + " the arms have filled in and this is a grid");
            along = Math.max(along, Math.max(dx, dz));
            across = Math.max(across, Math.min(dx, dz));
        }
        assertTrue(along >= 2 * across,
                "the arms reach " + along + " blocks and the ribs " + across
                        + " — that is a blob, not a cross");
    }

    /**
     * The town at two sizes, drawn, because a shape is not a number.
     *
     * <p>Every fault this package has had was found by a person looking at a
     * picture, and the pictures cost a five-minute round trip through the game
     * each. A plan is a flat drawing; it can be printed. The assertions below are
     * the parts of the picture that can be stated — that the middle is open, that
     * the arms are long and the ribs short, that the ground between the arms is
     * empty — and the map itself is for the parts that cannot.
     */
    @Test
    void aCrossroadsMapReadsAsACross() {
        for (int wanted : new int[] {64, 140}) {
            TownPlan plan = fresh(Layouts.CROSSROADS).planFor(CENTRE, wanted);
            double furthest = 0;
            int along = 0;
            int across = 0;
            for (TownPlan.Plot plot : plan.plots()) {
                furthest = Math.max(furthest, CENTRE.horizontalDistance(plot.at()));
                int dx = Math.abs(plot.at().x() - CENTRE.x());
                int dz = Math.abs(plot.at().z() - CENTRE.z());
                along = Math.max(along, Math.max(dx, dz));
                across = Math.max(across, Math.min(dx, dz));
            }
            // The arm and rib reaches printed alongside the picture, because they
            // are the numbers this arrangement's constants are documented in and
            // a recorded measurement nobody can re-read goes stale silently.
            System.out.println("crossroads at " + wanted + " plots: "
                    + plan.size() + " plots, " + plan.streets().size() + " streets, "
                    + plan.frontagePercent() + "% fronting, furthest "
                    + Math.round(furthest) + " blocks, arms " + along
                    + " and ribs " + across + " (" + Math.round(10.0 * along / across)
                    / 10.0 + " to one)");
            System.out.println(mapOf(plan, CENTRE, 8));

            assertEquals(wanted, plan.size(), "the plan came up short");
            assertTrue(plan.frontagePercent() >= 95,
                    "only " + plan.frontagePercent() + "% of it fronted a street");
            assertTrue(furthest < 250,
                    "the town sprawled to " + Math.round(furthest) + " blocks");
        }
    }

    /**
     * A plan drawn as text: {@code #} a building, {@code .} a street, space open.
     *
     * <p>Bounded by the plots rather than by the streets, so the picture is of
     * the town rather than of the roads it will one day fill: the plan is always
     * laid at its full size, so a town of sixty-four has the roads of a town of
     * two hundred and fifty-six drawn through it and would otherwise be a speck
     * in the middle of a cross.
     */
    private static String mapOf(TownPlan plan, SimPos centre, int scale) {
        int reach = scale;
        for (TownPlan.Plot plot : plan.plots()) {
            reach = Math.max(reach, Math.max(Math.abs(plot.at().x() - centre.x()),
                    Math.abs(plot.at().z() - centre.z())));
        }
        // Cells counted out from the middle rather than from a corner, so the
        // rounding is symmetric. Counted from a corner, a cell boundary falls
        // wherever the bounding box happens to start and one arm of a perfectly
        // symmetric town comes out a cell longer than the other -- which reads as
        // a fault in the layout and is a fault in the picture.
        int half = reach / scale + 1;
        int cells = 2 * half + 1;
        char[][] grid = new char[cells][cells];
        for (char[] row : grid) {
            java.util.Arrays.fill(row, ' ');
        }

        for (TownPlan.Street street : plan.streets()) {
            List<SimPos> path = street.path();
            for (int i = 1; i < path.size(); i++) {
                SimPos a = path.get(i - 1);
                SimPos b = path.get(i);
                int steps = (int) Math.ceil(Math.hypot(b.x() - a.x(), b.z() - a.z()));
                for (int s = 0; s <= steps; s++) {
                    double t = steps == 0 ? 0 : (double) s / steps;
                    mark(grid, centre, scale, half,
                            (int) Math.round(a.x() + t * (b.x() - a.x())),
                            (int) Math.round(a.z() + t * (b.z() - a.z())), '.');
                }
            }
        }
        for (TownPlan.Plot plot : plan.plots()) {
            mark(grid, centre, scale, half, plot.at().x(), plot.at().z(), '#');
        }

        // Two characters to a cell, because a terminal's characters are about
        // twice as tall as they are wide and a town drawn one to a cell comes out
        // squashed -- which matters when the whole question the picture answers
        // is what shape the town is.
        StringBuilder drawn = new StringBuilder();
        for (char[] row : grid) {
            StringBuilder line = new StringBuilder();
            for (char cell : row) {
                line.append(cell).append(cell == '#' ? '#' : cell == '.' ? '.' : ' ');
            }
            drawn.append(line.toString().replaceAll("\\s+$", "")).append('\n');
        }
        return drawn.toString();
    }

    /** One block of the world put into its cell, buildings winning over roads. */
    private static void mark(char[][] grid, SimPos centre, int scale, int half,
                             int x, int z, char what) {
        int col = Math.floorDiv(x - centre.x() + scale / 2, scale) + half;
        int row = Math.floorDiv(z - centre.z() + scale / 2, scale) + half;
        if (row < 0 || row >= grid.length || col < 0 || col >= grid.length) {
            return;
        }
        if (grid[row][col] != '#') {
            grid[row][col] = what;
        }
    }
}
