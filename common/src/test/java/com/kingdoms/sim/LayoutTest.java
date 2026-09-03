package com.kingdoms.sim;

import com.kingdoms.sim.culture.BastideLayout;
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
                Layouts.ORGANIC, Layouts.HIGH_STREET, Layouts.BASTIDE)) {
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
            return new RadialStreetLayout(r.id(), r.wander());
        }
        if (like instanceof GridStreetLayout g) {
            return new GridStreetLayout(g.id(), g.wander());
        }
        if (like instanceof BastideLayout b) {
            return new BastideLayout(b.id());
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
        TownPlan plan = Layouts.of(Culture.LAYOUT_RADIAL_CONCENTRIC).planFor(CENTRE, MANY);
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
        TownPlan plan = Layouts.of(Culture.LAYOUT_RADIAL_CONCENTRIC).planFor(CENTRE, MANY);
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
        TownPlan plan = Layouts.of(Culture.LAYOUT_RADIAL_CONCENTRIC).planFor(CENTRE, MANY);
        int adrift = 0;
        for (TownPlan.Plot plot : plan.plots()) {
            if (!plot.frontsAStreet()) {
                adrift++;
            }
        }
        assertEquals(1, adrift, "only the hall on the green should front nothing");
    }

    // --- the founder's town ---

    /** How far the place reaches from the middle of the town, either way. */
    private static final int PLACE_HALF = 19;

    /**
     * The market place is genuinely open ground, and everything looks at it.
     *
     * <p>Both halves matter and only the first is obvious. A square with nothing
     * facing it is a gap in the plan rather than a place, and the way to end up
     * with one is to leave the block out and let the neighbouring blocks go on
     * fronting the streets they always fronted — which turns their backs on it
     * from two sides out of four. So the eight nearest plots in the town are
     * asserted to be a ring round the square with their doors on it.
     *
     * <p><strong>The door is compared against {@link Layout#facingToward}, not
     * against a compass written out here.</strong> The first draft of this test
     * spelled the four quarter turns as a table of steps — {@code 0} is north,
     * {@code 1} is east — and that is a rule with two definitions, which this
     * codebase has already paid for once. {@code Layout.facingToward} and
     * {@code BuildPlanner.facingToward} are today exactly a half turn apart
     * (Layout answers 2 where BuildPlanner answers 0), so a hand-written compass
     * necessarily agrees with one and certifies the other as broken. Asking
     * instead whether a house on the square is turned the way anything told to
     * look at the middle of town would be turned is true under either
     * convention, and stays true on the day somebody makes the two agree.
     */
    @Test
    void aBastideLeavesItsPlaceOpenAndFacesTheTownAtIt() {
        TownPlan plan = new BastideLayout("bastide_place").planFor(CENTRE, MANY);
        for (TownPlan.Plot plot : plan.plots()) {
            assertFalse(Math.abs(plot.at().x() - CENTRE.x()) <= PLACE_HALF
                            && Math.abs(plot.at().z() - CENTRE.z()) <= PLACE_HALF,
                    "a building at " + plot.at() + " stands in the market place");
        }

        // Offers are taken nearest-first, so the ring round the square is the
        // first thing the town builds -- which is the right order for a market
        // town and the reason the place is worth having at all.
        Set<SimPos> ring = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            TownPlan.Plot plot = plan.plot(i);
            ring.add(plot.at());
            assertTrue(plot.frontsAStreet(),
                    "the plot at " + plot.at() + " stands on the square fronting nothing");
            assertEquals(Layout.facingToward(plot.at(), CENTRE), plot.facing(),
                    "the plot at " + plot.at() + " stands on the square with its back to it");
        }
        assertEquals(8, ring.size(), "the square should be ringed two to a side");
        assertEquals(4, sidesFaced(ring), "the square wants frontage on all four sides");
    }

    /** How many of the four sides of the square carry frontage. */
    private static int sidesFaced(Set<SimPos> ring) {
        boolean north = false;
        boolean south = false;
        boolean east = false;
        boolean west = false;
        for (SimPos at : ring) {
            int dx = at.x() - CENTRE.x();
            int dz = at.z() - CENTRE.z();
            if (Math.abs(dz) > Math.abs(dx)) {
                north |= dz < 0;
                south |= dz > 0;
            } else {
                west |= dx < 0;
                east |= dx > 0;
            }
        }
        return (north ? 1 : 0) + (south ? 1 : 0) + (east ? 1 : 0) + (west ? 1 : 0);
    }

    /**
     * The circuit is a real street, and the town's edge is where it says.
     *
     * <p>The trap here is R6, and it is the expensive one: a road that offers no
     * frontage costs twice, because it refuses every plot it passes and gives
     * nothing back. Six bare lanes held a ring town to 62% frontage. A circuit is
     * the longest road in the town and by far the easiest one to leave bare, so
     * this asserts it carries houses on every one of its four runs.
     *
     * <p>Measured on the whole plan rather than on a town of a hundred and twenty:
     * the circuit is pegged out at foundation for the town the founder hoped for,
     * and the houses reach it as the place fills. That is what a bastide is —
     * Monpazier's walls were built round more town than ever turned up.
     */
    @Test
    void aBastideIsBoundedByACircuitWithHousesOnIt() {
        TownPlan plan = new BastideLayout("bastide_circuit").fullPlan(CENTRE);
        TownPlan.Street circuit = null;
        int spines = 0;
        for (TownPlan.Street street : plan.streets()) {
            if (street.kind() == TownPlan.Kind.SPINE) {
                circuit = street;
                spines++;
            }
        }
        assertEquals(1, spines, "a bastide has exactly one road round the outside");
        assertEquals(circuit.from(), circuit.to(), "the circuit does not close");

        int reach = Math.max(Math.abs(circuit.from().x() - CENTRE.x()),
                Math.abs(circuit.from().z() - CENTRE.z()));
        for (SimPos point : circuit.path()) {
            assertEquals(reach, Math.max(Math.abs(point.x() - CENTRE.x()),
                            Math.abs(point.z() - CENTRE.z())),
                    "the circuit wanders off its rectangle at " + point);
        }

        int index = plan.streets().indexOf(circuit);
        boolean[] run = new boolean[4];
        int fronting = 0;
        for (TownPlan.Plot plot : plan.plots()) {
            if (plot.street() != index) {
                continue;
            }
            fronting++;
            int dx = plot.at().x() - CENTRE.x();
            int dz = plot.at().z() - CENTRE.z();
            run[Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 0 : 1) : (dz > 0 ? 2 : 3)] = true;
        }
        // Sixteen rather than the sixty-eight the rim actually offers. A square
        // grid steps in whole blocks, so the plan pegs eighty-one blocks to hold
        // a town of two hundred and fifty-six that wants sixty-five of them, and
        // the plots that never get taken are the furthest out -- the corners of
        // the rim. What the circuit must not be is bare, and that is the bar.
        assertTrue(fronting >= 16,
                "only " + fronting + " houses front the circuit; it is a fence");
        for (boolean side : run) {
            assertTrue(side, "one whole run of the circuit carries no frontage at all");
        }

        // And nothing is built outside it, or the boundary means nothing.
        for (TownPlan.Plot plot : plan.plots()) {
            assertTrue(Math.max(Math.abs(plot.at().x() - CENTRE.x()),
                            Math.abs(plot.at().z() - CENTRE.z())) < reach,
                    "a building at " + plot.at() + " stands outside the circuit");
        }
    }

    /**
     * A bastide is not the stronghold with the serial numbers filed off.
     *
     * <p>Two grids is one grid too many unless they are different towns, and
     * "it looks different" is not something anybody can check twice. So the same
     * measure the three original arrangements are held to: fewer than four shared
     * positions in the first forty.
     *
     * <p>The structural half is the more useful one. The stronghold rules its
     * streets through the middle of the town, so the centre of an orc grid is a
     * crossroads. The bastide offsets the whole grid by half a block, so the
     * centre is the middle of a block and that block is the market. Same idea,
     * opposite decision about the one square in the town that anybody looks at.
     */
    @Test
    void aBastideIsNotTheStronghold() {
        assertTrue(overlap(plotSet(new BastideLayout("bastide_apart")),
                        plotSet(new GridStreetLayout("grid_apart", Wander.STRAIGHT))) < 4,
                "the bastide and the stronghold are the same town");

        TownPlan bastide = new BastideLayout("bastide_middle").planFor(CENTRE, MANY);
        TownPlan grid = new GridStreetLayout("grid_middle", Wander.STRAIGHT)
                .planFor(CENTRE, MANY);
        assertTrue(onARoad(grid, CENTRE),
                "the stronghold used to put a crossroads in the middle; if it no "
                        + "longer does, the two arrangements have converged");
        assertFalse(onARoad(bastide, CENTRE),
                "the middle of a bastide is its market place, not a junction");
    }

    /** Whether any street of this plan runs over this point. */
    private static boolean onARoad(TownPlan plan, SimPos at) {
        for (TownPlan.Street street : plan.streets()) {
            if (street.touches(at, 0)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The town, drawn, because every siting fault so far was found by looking.
     *
     * <p>Printed at three sizes and beside the stronghold, all in one window and
     * at one scale, so the claim that these are different towns can be checked by
     * a person in about four seconds. The assertions are the bars underneath the
     * picture: frontage, an empty place, and a reach that neither huddles nor
     * sprawls.
     */
    @Test
    void aBastideDrawsAsARectangleWithAHoleInTheMiddle() {
        StringBuilder drawn = new StringBuilder();
        for (int wanted : new int[] {64, 140}) {
            TownPlan plan = new BastideLayout("bastide_map_" + wanted)
                    .planFor(CENTRE, wanted);
            assertTrue(plan.frontagePercent() >= 95,
                    "bastide at " + wanted + " fronted only "
                            + plan.frontagePercent() + "%");
            drawn.append(caption("bastide", wanted, plan)).append(map(plan));
        }

        TownPlan full = new BastideLayout("bastide_map_full").fullPlan(CENTRE);
        drawn.append(caption("bastide", full.size(), full)).append(map(full));

        TownPlan grid = new GridStreetLayout("grid_map", Wander.STRAIGHT)
                .planFor(CENTRE, 140);
        drawn.append(caption("stronghold_streets", 140, grid)).append(map(grid));
        System.out.println(drawn);

        TownPlan town = new BastideLayout("bastide_reach").planFor(CENTRE, 140);
        double reach = furthest(town);
        assertTrue(reach >= 120 && reach <= 250,
                "a bastide of 140 reached " + Math.round(reach)
                        + " blocks, outside the 120..250 a town holds together in");
    }

    private static String caption(String id, int wanted, TownPlan plan) {
        return String.format("%n== %s @ %d plots: %d%% fronting, %d streets, "
                        + "furthest %d blocks ==%n",
                id, wanted, plan.frontagePercent(), plan.streets().size(),
                Math.round(furthest(plan)));
    }

    private static double furthest(TownPlan plan) {
        double out = 0;
        for (TownPlan.Plot plot : plan.plots()) {
            out = Math.max(out, plan.centre().horizontalDistance(plot.at()));
        }
        return out;
    }

    /** How wide a window every map is drawn in, so they can be compared. */
    private static final int WINDOW = 180;

    /** How many blocks one character of a map stands for. */
    private static final int CELL = 5;

    /** A plan from above: {@code #} a building, {@code .} a street. */
    private static String map(TownPlan plan) {
        int half = WINDOW / CELL;
        int side = 2 * half + 1;
        char[][] grid = new char[side][side];
        for (char[] row : grid) {
            java.util.Arrays.fill(row, ' ');
        }
        for (TownPlan.Street street : plan.streets()) {
            List<SimPos> path = street.path();
            for (int i = 1; i < path.size(); i++) {
                SimPos a = path.get(i - 1);
                SimPos b = path.get(i);
                int steps = Math.max(1, (int) Math.hypot(b.x() - a.x(), b.z() - a.z()));
                for (int t = 0; t <= steps; t++) {
                    ink(grid, plan.centre(), half,
                            a.x() + (b.x() - a.x()) * t / steps,
                            a.z() + (b.z() - a.z()) * t / steps, '.');
                }
            }
        }
        for (TownPlan.Plot plot : plan.plots()) {
            ink(grid, plan.centre(), half, plot.at().x(), plot.at().z(), '#');
        }
        StringBuilder out = new StringBuilder();
        for (char[] row : grid) {
            out.append(new String(row).replaceAll("\\s+$", "")).append('\n');
        }
        return out.toString();
    }

    private static void ink(char[][] grid, SimPos centre, int half, int x, int z, char mark) {
        int col = Math.floorDiv(x - centre.x() + CELL / 2, CELL) + half;
        int row = Math.floorDiv(z - centre.z() + CELL / 2, CELL) + half;
        if (row >= 0 && row < grid.length && col >= 0 && col < grid.length) {
            grid[row][col] = mark;
        }
    }
}
