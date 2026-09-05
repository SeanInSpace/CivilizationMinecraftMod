package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.worldgen.SettlementSites;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The site chooser, checked on the four things that can go wrong with it.
 *
 * <p>Nothing here needs a world, which is the point of the class being pure: a
 * 16x16 sweep of regions is 256 hash evaluations and runs in about as long as
 * it takes to allocate the list.
 *
 * <p>The separation sweep is the one that earns its keep. The minimum distance
 * between two sites is a consequence of the jitter window and nothing enforces
 * it at runtime — no rejection pass, no neighbor query — so if
 * {@code EDGE_MARGIN} and {@code REGION} ever drift out of step with
 * {@code MIN_SEPARATION}, the only thing that notices is this.
 */
class SettlementSitesTest {

    private static final long SEED = 8675309L;

    /** Every site in a square block of regions, as one list. */
    private static List<SettlementSites.Site> sweep(long seed, int regions) {
        List<SettlementSites.Site> found = new ArrayList<>();
        for (int rz = 0; rz < regions; rz++) {
            for (int rx = 0; rx < regions; rx++) {
                SettlementSites.siteIn(seed, rx, rz).ifPresent(found::add);
            }
        }
        return found;
    }

    @Test
    void theSameSeedAndRegionAlwaysGiveTheSameSite() {
        for (int rx = -4; rx <= 4; rx++) {
            for (int rz = -4; rz <= 4; rz++) {
                Optional<SettlementSites.Site> first = SettlementSites.siteIn(SEED, rx, rz);
                Optional<SettlementSites.Site> again = SettlementSites.siteIn(SEED, rx, rz);
                assertEquals(first, again,
                        "region (" + rx + ", " + rz + ") changed its mind between calls");
            }
        }
    }

    @Test
    void differentSeedsGiveDifferentWorlds() {
        // Not "every region differs" -- two seeds agreeing about one region is
        // ordinary. What would be wrong is the seed making no difference at all,
        // which is what happens when it is dropped on the floor somewhere in the
        // mixing.
        Set<String> shapes = new HashSet<>();
        for (long seed = 1; seed <= 40; seed++) {
            StringBuilder shape = new StringBuilder();
            for (int rx = 0; rx < 8; rx++) {
                for (int rz = 0; rz < 8; rz++) {
                    shape.append(SettlementSites.siteIn(seed, rx, rz)
                            .map(site -> site.center().toString() + site.cultureId())
                            .orElse("-"));
                }
            }
            shapes.add(shape.toString());
        }
        assertEquals(40, shapes.size(), "different seeds produced the same world");
    }

    @Test
    void aSiteStaysInsideItsOwnRegion() {
        // The whole separation argument rests on this and on nothing else.
        for (int rx = -3; rx <= 3; rx++) {
            for (int rz = -3; rz <= 3; rz++) {
                int fx = rx;
                int fz = rz;
                SettlementSites.siteIn(SEED, rx, rz).ifPresent(site -> {
                    long lowX = (long) fx * SettlementSites.REGION + SettlementSites.EDGE_MARGIN;
                    long highX = (long) (fx + 1) * SettlementSites.REGION
                            - SettlementSites.EDGE_MARGIN;
                    long lowZ = (long) fz * SettlementSites.REGION + SettlementSites.EDGE_MARGIN;
                    long highZ = (long) (fz + 1) * SettlementSites.REGION
                            - SettlementSites.EDGE_MARGIN;
                    assertTrue(site.center().x() >= lowX && site.center().x() <= highX,
                            site.center() + " strayed outside region x " + fx);
                    assertTrue(site.center().z() >= lowZ && site.center().z() <= highZ,
                            site.center() + " strayed outside region z " + fz);
                    assertEquals(fx, SettlementSites.regionXOf(site));
                    assertEquals(fz, SettlementSites.regionZOf(site));
                });
            }
        }
    }

    @Test
    void noTwoSitesAreCloserThanTheDocumentedMinimum() {
        List<SettlementSites.Site> found = sweep(SEED, 16);
        assertTrue(found.size() > 50, "a 16x16 sweep found only " + found.size() + " sites");
        long floor = (long) SettlementSites.MIN_SEPARATION * SettlementSites.MIN_SEPARATION;
        for (int i = 0; i < found.size(); i++) {
            for (int j = i + 1; j < found.size(); j++) {
                SimPos a = found.get(i).center();
                SimPos b = found.get(j).center();
                assertTrue(a.horizontalDistanceSq(b) >= floor,
                        a + " and " + b + " are " + Math.round(a.horizontalDistance(b))
                                + " apart, under the promised "
                                + SettlementSites.MIN_SEPARATION);
            }
        }
    }

    @Test
    void theMinimumIsTightRatherThanGenerous() {
        // A separation floor nothing ever approaches is not a guarantee, it is a
        // coincidence -- and would hide MIN_SEPARATION having been written down
        // far below what the jitter window actually allows, which is the way
        // this promise rots without any test going red. Over 576 regions two
        // neighbors do jitter towards the edge they share: measured at 340
        // blocks against a floor of 320.
        long closest = Long.MAX_VALUE;
        List<SettlementSites.Site> found = sweep(SEED, 24);
        for (int i = 0; i < found.size(); i++) {
            for (int j = i + 1; j < found.size(); j++) {
                closest = Math.min(closest,
                        found.get(i).center().horizontalDistanceSq(found.get(j).center()));
            }
        }
        double closestBlocks = Math.sqrt(closest);
        assertTrue(closestBlocks < SettlementSites.MIN_SEPARATION * 1.5,
                "the closest pair in a 24x24 sweep was " + Math.round(closestBlocks)
                        + " apart, nowhere near the " + SettlementSites.MIN_SEPARATION
                        + " the jitter window allows");
    }

    @Test
    void notEveryRegionHoldsATown() {
        int regions = 32 * 32;
        int held = sweep(SEED, 32).size();
        assertTrue(held > 0 && held < regions,
                held + " of " + regions + " regions settled — the spawn chance is not doing anything");
        double rate = (double) held / regions;
        // Wide bounds on purpose: this is checking the chance is applied at all
        // and roughly in the right place, not re-deriving the binomial.
        assertTrue(Math.abs(rate - SettlementSites.SPAWN_CHANCE) < 0.08,
                "settled " + rate + " of regions against a chance of "
                        + SettlementSites.SPAWN_CHANCE);
    }

    @Test
    void theCultureSpreadIsNotDegenerate() {
        Map<String, Integer> histogram = new HashMap<>();
        for (SettlementSites.Site site : sweep(SEED, 24)) {
            histogram.merge(site.cultureId(), 1, Integer::sum);
        }
        // Everybody but the sentinel. kingdoms:default is what an unreadable
        // culture becomes, so a town wearing it cannot be told from a broken
        // one; the chooser leaves it out and this is what says so.
        int peoples = Culture.all().size() - 1;
        assertFalse(histogram.containsKey(Culture.DEFAULT.id()),
                "the no-culture sentinel was handed out as if it were a people");
        assertEquals(peoples, histogram.size(),
                "only " + histogram.size() + " of " + peoples + " cultures ever settled: "
                        + histogram);
        int total = histogram.values().stream().mapToInt(Integer::intValue).sum();
        for (Map.Entry<String, Integer> entry : histogram.entrySet()) {
            double share = (double) entry.getValue() / total;
            assertTrue(share > 0.5 / peoples && share < 2.0 / peoples,
                    entry.getKey() + " holds " + share + " of all sites, against a fair share of "
                            + (1.0 / peoples) + ": " + histogram);
        }
    }

    @Test
    void theSameSeedAndRegionGiveTheSameSiteInEveryJvm() {
        // Written down rather than derived, because the fault it guards against
        // is invisible to a run that only compares itself. Culture.all() is a
        // Map.of, and Map.of iterates in an order that is randomized per JVM: a
        // chooser reading it in that order is perfectly self-consistent within
        // one launch and gives a different people every time the game restarts.
        // A fixed expectation turns that into a test that passes today and fails
        // tomorrow, which is the only shape of evidence available for it.
        //
        // These change if REGION, EDGE_MARGIN or SPAWN_CHANCE are tuned, and are
        // meant to: a tuning pass moves every town in every world, and being
        // told so by a red test is the point.
        // The position is pinned and the people is not. Which people settles a
        // region stopped being a free draw when arrangements got weights: the
        // shape is drawn first and the people is then chosen from those who
        // build in it, so a table that asks for greens gets whoever builds a
        // green. That is the intended behavior and would make a pinned culture
        // a test of the table rather than of the grid.
        assertEquals(new SimPos(-275, SettlementSites.UNRESOLVED_Y, 224),
                SettlementSites.siteIn(SEED, -1, 0).orElseThrow().center());
        assertEquals(new SimPos(-1265, SettlementSites.UNRESOLVED_Y, -1357),
                SettlementSites.siteIn(SEED, -3, -3).orElseThrow().center());
        assertTrue(SettlementSites.siteIn(SEED, 0, 0).isEmpty(),
                "region (0, 0) has always been empty under this seed");
    }

    @Test
    void nearReturnsExactlyTheSitesWithinReach() {
        SimPos at = new SimPos(1234, 0, -5678);
        int reach = 1500;
        List<SettlementSites.Site> near = SettlementSites.near(SEED, at, reach);

        for (SettlementSites.Site site : near) {
            assertTrue(site.center().horizontalDistance(at) <= reach,
                    site.center() + " is outside a reach of " + reach);
        }

        // And nothing within reach was missed: sweep a box comfortably wider
        // than the reach and check every site the long way round.
        int slack = reach + 2 * SettlementSites.REGION;
        Set<SimPos> expected = new HashSet<>();
        for (int rx = SettlementSites.regionOf(at.x() - slack);
             rx <= SettlementSites.regionOf(at.x() + slack); rx++) {
            for (int rz = SettlementSites.regionOf(at.z() - slack);
                 rz <= SettlementSites.regionOf(at.z() + slack); rz++) {
                SettlementSites.siteIn(SEED, rx, rz)
                        .filter(site -> site.center().horizontalDistance(at) <= reach)
                        .ifPresent(site -> expected.add(site.center()));
            }
        }
        Set<SimPos> got = new HashSet<>();
        near.forEach(site -> got.add(site.center()));
        assertEquals(expected, got);
        assertTrue(expected.size() >= 2,
                "the fixture found only " + expected.size()
                        + " sites, which is too few to be checking anything");
    }

    @Test
    void nearHandsBackTheNearestFirst() {
        SimPos at = new SimPos(-90210, 0, 42);
        List<SettlementSites.Site> near = SettlementSites.near(SEED, at, 3000);
        long previous = -1;
        for (SettlementSites.Site site : near) {
            long distance = site.center().horizontalDistanceSq(at);
            assertTrue(distance >= previous, "sites came back out of order");
            previous = distance;
        }
    }

    @Test
    void aSiteCarriesNoOpinionAboutHeight() {
        // Every site in a sweep, not one region: written first as
        // siteIn(0, 0).ifPresent(...), where region (0, 0) is empty under this
        // seed, so the assertion never ran and the y contract was guarded by a
        // test that could not fail.
        List<SettlementSites.Site> found = sweep(SEED, 8);
        assertFalse(found.isEmpty());
        for (SettlementSites.Site site : found) {
            assertEquals(SettlementSites.UNRESOLVED_Y, site.center().y(),
                    "the chooser cannot know the ground and must not pretend to");
        }
    }

    @Test
    void theConstantsAgreeWithEachOther() {
        assertEquals(SettlementSites.REGION - 2 * SettlementSites.EDGE_MARGIN,
                SettlementSites.JITTER_SPAN);
        assertEquals(2 * SettlementSites.EDGE_MARGIN, SettlementSites.MIN_SEPARATION);
        assertTrue(SettlementSites.JITTER_SPAN > 0,
                "the margin has eaten the region; every site would sit on the lattice");
    }

    // --- which shape of town a world wants ---

    /** Every region in a wide sweep, under a given table. */
    private static List<SettlementSites.Site> sweep(Map<String, Integer> weights) {
        List<SettlementSites.Site> found = new ArrayList<>();
        for (int rx = -8; rx < 8; rx++) {
            for (int rz = -8; rz < 8; rz++) {
                SettlementSites.siteIn(SEED, rx, rz, weights).ifPresent(found::add);
            }
        }
        return found;
    }

    /**
     * A table of one arrangement gets a world of one arrangement.
     *
     * <p>Which is what the mod ships with, so this is the shipped world.
     */
    @Test
    void aWorldThatAsksForOneShapeGetsOnlyThatShape() {
        List<SettlementSites.Site> sites = sweep(Map.of(Culture.LAYOUT_GREEN, 100));
        assertFalse(sites.isEmpty(), "the sweep should find towns at all");
        for (SettlementSites.Site site : sites) {
            assertEquals(Culture.LAYOUT_GREEN, site.layoutId(),
                    "a table naming one arrangement should draw nothing else");
        }
    }

    /**
     * A weight of nought is a refusal.
     *
     * <p>Written down because the obvious implementation -- divide by the total
     * and round -- gives a zero-weighted entry a small share rather than none,
     * and a world that has switched an arrangement off should never see it.
     */
    @Test
    void anArrangementWeightedNoughtNeverAppears() {
        Map<String, Integer> table = new LinkedHashMap<>();
        table.put(Culture.LAYOUT_GREEN, 1);
        table.put(Culture.LAYOUT_CROSSROADS, 0);
        table.put(Culture.LAYOUT_BASTIDE, 0);
        for (SettlementSites.Site site : sweep(table)) {
            assertEquals(Culture.LAYOUT_GREEN, site.layoutId(),
                    "nought means never, not seldom");
        }
    }

    /**
     * An empty table is no preference, not a ban.
     *
     * <p>The config is empty until it loads, and a world that raised no towns
     * during that window would raise none for the regions the player happened to
     * be standing in at the time -- permanently, because the ledger would have
     * written them down as settled.
     */
    @Test
    void aWorldWithNoTableStillBuildsSomething() {
        List<SettlementSites.Site> sites = sweep(Map.of());
        assertFalse(sites.isEmpty(), "no table should not mean no towns");
        for (SettlementSites.Site site : sites) {
            assertFalse(site.layoutId().isBlank(), "every site names an arrangement");
        }
    }

    /**
     * Two tables place towns in the same places.
     *
     * <p>The shape a world wants must not move its towns: whether and where are
     * drawn from their own hashes, and only which is weighted. Otherwise editing
     * the table in an existing world would strand every settlement already
     * standing.
     */
    @Test
    void theTableChangesTheShapeAndNotThePlaces() {
        List<SimPos> green = sweep(Map.of(Culture.LAYOUT_GREEN, 100)).stream()
                .map(SettlementSites.Site::center).toList();
        List<SimPos> bastide = sweep(Map.of(Culture.LAYOUT_BASTIDE, 100)).stream()
                .map(SettlementSites.Site::center).toList();
        assertEquals(green, bastide, "the table must not move a single town");
    }

    /** Whoever settles a site builds in the shape it was drawn for. */
    @Test
    void thePeopleOfASiteBuildTheShapeItWasDrawnFor() {
        for (SettlementSites.Site site : sweep(Map.of(Culture.LAYOUT_GREEN, 100))) {
            assertTrue(Culture.of(site.cultureId()).layouts().contains(site.layoutId()),
                    site.cultureId() + " does not build a " + site.layoutId());
        }
    }
}
