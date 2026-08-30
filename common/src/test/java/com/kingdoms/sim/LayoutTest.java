package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.Layout;
import com.kingdoms.sim.culture.Layouts;
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
                Layouts.ORGANIC, Layouts.HIGH_STREET)) {
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
            // holding: it is what makes rings rings. ORGANIC is deliberately not
            // — its throws are seeded from the town's own centre, so two villages
            // of the same people are not the same village twice. What the rule is
            // actually for is that a layout must build around the centre it is
            // HANDED rather than one it remembers, and that is asserted for all
            // of them below.
            if (!layout.id().equals("organic")) {
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
}
