package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.worldgen.SettlementSites;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The promise a new world makes: there is a town over there. One town.
 *
 * <p>The unanchored grid is a scatter, and a scatter is allowed to be empty —
 * a third of regions hold a site and a player can spawn in the two thirds that
 * do not, with nothing inside a kilometer. That is the right world to walk
 * through and the wrong world to start in, which is what Millénaire understood
 * and this suite pins down.
 *
 * <p>The promise used to be nine towns, and nine was the mod showing off: a
 * cluster in the first kilometer that no other kilometer of the world ever
 * produced. It is one now, and the other eight regions take their chances like
 * everywhere else — see {@code SettlementSpacingTest}, which measures what that
 * did to the world.
 *
 * <p>What is checked is a guarantee, not a tendency, so everything here sweeps
 * a thousand seeds with the spawn point thrown anywhere in the world rather
 * than at a convenient origin. The awkward cases are exactly the ones a real
 * world produces: a spawn point sitting on a region boundary, or in a region's
 * corner, where the naive fix — clamp the spawn point into the jitter window —
 * puts the town on top of the player.
 */
class SettlementSitesSpawnTest {

    /**
     * How far a starter town may be from the spawn point.
     *
     * <p>Its own region and no further, which is geometry: the site lands in a
     * jitter window that stops a margin short of the region's edge, and the
     * spawn point is inside the same region, so the furthest the two can be is
     * {@code (region - margin) * sqrt(2)} — 996 blocks at the shipped region.
     */
    private static final double SIGHT = SettlementSites.REGION;

    private static final Map<String, Integer> ANY = Map.of();

    /** A thousand worlds, each with its spawn thrown somewhere different. */
    private static List<long[]> worlds() {
        Random random = new Random(8675309L);
        List<long[]> all = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            all.add(new long[]{
                    random.nextLong(),
                    random.nextInt(-2_000_000, 2_000_000),
                    random.nextInt(-2_000_000, 2_000_000)});
        }
        // And the cases a random sweep will not reliably hit: the spawn point
        // exactly on a region corner, on each edge, and dead in the middle.
        // The corner is the one the old approach could not serve.
        for (int[] offset : new int[][]{
                {0, 0}, {0, 1023}, {1023, 0}, {1023, 1023}, {512, 512}}) {
            all.add(new long[]{4242L, offset[0], offset[1]});
        }
        return all;
    }

    private static SettlementSites.Grid gridAt(long spawnX, long spawnZ) {
        return SettlementSites.Grid.DEFAULT.anchoredAt(
                new SimPos((int) spawnX, 64, (int) spawnZ));
    }

    @Test
    @DisplayName("the region a world spawns in always holds a site")
    void theRegionAWorldSpawnsInAlwaysHoldsASite() {
        for (long[] world : worlds()) {
            SettlementSites.Grid grid = gridAt(world[1], world[2]);
            int[] home = grid.homeRegion().orElseThrow();
            Optional<SettlementSites.Site> site =
                    grid.siteIn(world[0], home[0], home[1], ANY);
            assertTrue(site.isPresent(),
                    "no site in the spawn region for seed " + world[0]
                            + " spawning at " + world[1] + ", " + world[2]);
        }
    }

    @Test
    @DisplayName("the starter town is a walk away, not a neighbour and not a horizon")
    void theStarterTownIsAWalkAway() {
        double furthest = 0;
        double nearest = Double.MAX_VALUE;
        for (long[] world : worlds()) {
            SimPos at = new SimPos((int) world[1], 64, (int) world[2]);
            SettlementSites.Grid grid = SettlementSites.Grid.DEFAULT.anchoredAt(at);
            int[] home = grid.homeRegion().orElseThrow();
            SimPos center = grid.siteIn(world[0], home[0], home[1], ANY)
                    .orElseThrow().center();
            double walk = center.horizontalDistance(at);
            furthest = Math.max(furthest, walk);
            nearest = Math.min(nearest, walk);
            assertTrue(walk <= SIGHT,
                    "spawn town " + Math.round(walk) + " blocks out for seed "
                            + world[0] + " spawning at " + at);
        }
        System.out.printf("starter town: %.0f to %.0f blocks from the spawn point%n",
                nearest, furthest);
        // Not on top of the player either. A town whose square is where you
        // appear is a spawn building, not a discovery -- and a grown one would
        // have swallowed the spawn point outright.
        assertTrue(nearest >= SettlementSites.STARTER_MIN_FROM_SPAWN,
                "a starter town landed " + nearest + " blocks away");
    }

    @Test
    @DisplayName("nobody under arms holds the region a world spawns in")
    void nobodyUnderArmsHoldsTheSpawnRegion() {
        // The flag was already there and already set. Grid.siteIn has passed
        // mustBeFriendly = starter to both halves of the draw since the starter
        // site was written, and the comment beside it says a first settlement
        // that shoots at you on sight is a death screen rather than an
        // introduction. It filtered on Culture.isHostile, which means *goblin* —
        // so it kept the mire goblins out and let the orc warhost straight
        // through, and a playtest on seed 20260919 drew
        // `civilization:orc/warhost orc_ring` for the very region the player
        // spawns in. Only the ground refusing it kept a warhost off the
        // wayfinder.
        //
        // Swept over a thousand seeds with the spawn thrown anywhere, and over
        // both tables: the weighted draw a world actually runs, and the empty
        // one that means "no preference at all" and takes the other branch.
        for (long[] world : worlds()) {
            SimPos at = new SimPos((int) world[1], 64, (int) world[2]);
            SettlementSites.Grid grid = SettlementSites.Grid.DEFAULT.anchoredAt(at);
            int[] home = grid.homeRegion().orElseThrow();
            for (Map<String, Integer> table : List.of(ANY, everyArrangement())) {
                SettlementSites.Site site =
                        grid.siteIn(world[0], home[0], home[1], table).orElseThrow();
                Culture people = Culture.of(site.cultureId());
                assertFalse(people.underArms(),
                        "seed " + world[0] + " spawning at " + at
                                + " drew " + site.cultureId() + " " + site.layoutId()
                                + " for its own spawn region");
                assertFalse(people.isHostile(),
                        "seed " + world[0] + " drew a hostile people for its spawn region");
            }
        }
    }

    @Test
    @DisplayName("every arrangement a people builds is still drawn away from spawn")
    void everyArrangementIsStillReachableAwayFromSpawn() {
        // The other half of the same rule: the filter is on the spawn region and
        // on nothing else. A world that never produced a war camp or a goblin
        // camp anywhere would be a worse fix than the fault.
        Set<String> seen = new java.util.HashSet<>();
        SettlementSites.Grid grid = SettlementSites.Grid.DEFAULT;
        for (int rz = -30; rz <= 30; rz++) {
            for (int rx = -30; rx <= 30; rx++) {
                grid.siteIn(8675309L, rx, rz, everyArrangement())
                        .ifPresent(site -> seen.add(site.cultureId()));
            }
        }
        assertTrue(seen.contains(Culture.ORC.id()),
                "no orc warhost anywhere in a 61-region sweep: " + seen);
        assertTrue(seen.contains(Culture.GOBLIN.id()),
                "no mire goblins anywhere in a 61-region sweep: " + seen);
    }

    /** Every arrangement anybody builds in, evenly weighted. */
    private static Map<String, Integer> everyArrangement() {
        Map<String, Integer> weights = new java.util.LinkedHashMap<>();
        for (Culture culture : Culture.all()) {
            for (String layout : culture.layouts()) {
                weights.put(layout, 1);
            }
        }
        return weights;
    }

    @Test
    @DisplayName("the spawn region is the only one a world is promised")
    void theSpawnRegionIsTheOnlyOneAWorldIsPromised() {
        // With the chance turned off entirely, a world holds exactly one site:
        // the starter. Everything else in the five-by-five block around it is
        // whatever the dice said, which is the whole of this change.
        for (long[] world : worlds()) {
            SimPos at = new SimPos((int) world[1], 64, (int) world[2]);
            SettlementSites.Grid certain = new SettlementSites.Grid(
                    SettlementSites.REGION, 0, Optional.of(at));
            int[] home = certain.homeRegion().orElseThrow();
            int held = 0;
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    if (certain.siteIn(world[0], home[0] + dx, home[1] + dz, ANY)
                            .isPresent()) {
                        held++;
                    }
                }
            }
            assertEquals(1, held, "a world with site_chance 0 held " + held
                    + " sites around its spawn point");
            assertTrue(certain.siteIn(world[0], home[0], home[1], ANY).isPresent(),
                    "the one site a world is promised is not in the spawn region");
        }
    }

    @Test
    @DisplayName("anchoring never brings two sites closer than the separation floor")
    void anchoringNeverBringsTwoSitesCloserThanTheSeparationFloor() {
        checkSeparation(SettlementSites.Grid.DEFAULT);
    }

    @Test
    @DisplayName("a 256-block region keeps its own separation floor")
    void a256BlockRegionKeepsItsOwnSeparationFloor() {
        SettlementSites.Grid dense = new SettlementSites.Grid(
                256, SettlementSites.DEFAULT_SITE_PERCENT, Optional.empty());
        assertEquals(80, dense.edgeMargin());
        assertEquals(160, dense.minSeparation());
        checkSeparation(dense);
    }

    /**
     * Every pair of sites in the five-by-five block around spawn, at every seed.
     *
     * <p>Five rather than three, because the interesting failure is not the
     * starter against its own region — it is the starter, pushed off the spawn
     * point by its keep-out, landing on top of a neighbour nobody was thinking
     * about.
     */
    private void checkSeparation(SettlementSites.Grid unanchored) {
        long floorSq = (long) unanchored.minSeparation() * unanchored.minSeparation();
        for (long[] world : worlds()) {
            SettlementSites.Grid grid = unanchored.anchoredAt(
                    new SimPos((int) world[1], 64, (int) world[2]));
            int[] home = grid.homeRegion().orElseThrow();
            List<SimPos> centers = new ArrayList<>();
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    grid.siteIn(world[0], home[0] + dx, home[1] + dz, ANY)
                            .ifPresent(site -> centers.add(site.center()));
                }
            }
            for (int i = 0; i < centers.size(); i++) {
                for (int j = i + 1; j < centers.size(); j++) {
                    long apart = centers.get(i).horizontalDistanceSq(centers.get(j));
                    assertTrue(apart >= floorSq,
                            "two sites " + Math.round(Math.sqrt(apart))
                                    + " blocks apart at region " + unanchored.region()
                                    + ", seed " + world[0] + ": "
                                    + centers.get(i) + " and " + centers.get(j));
                }
            }
        }
    }

    @Test
    @DisplayName("every site stays inside the region that produced it")
    void everySiteStaysInsideTheRegionThatProducedIt() {
        for (int size : new int[]{256, 512, 1024, 2048}) {
            SettlementSites.Grid plain = new SettlementSites.Grid(
                    size, SettlementSites.DEFAULT_SITE_PERCENT, Optional.empty());
            for (long[] world : worlds().subList(0, 200)) {
                SettlementSites.Grid grid = plain.anchoredAt(
                        new SimPos((int) world[1], 64, (int) world[2]));
                int[] home = grid.homeRegion().orElseThrow();
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int rx = home[0] + dx;
                        int rz = home[1] + dz;
                        grid.siteIn(world[0], rx, rz, ANY).ifPresent(site -> {
                            assertEquals(rx, grid.regionXOf(site),
                                    "site " + site.center() + " left its own region");
                            assertEquals(rz, grid.regionZOf(site),
                                    "site " + site.center() + " left its own region");
                        });
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("the region size is the pitch of the grid")
    void theRegionSizeIsThePitchOfTheGrid() {
        // Halving the region should roughly quadruple how many sites a fixed
        // square of world holds, since the same fraction of four times as many
        // regions is settled.
        SimPos here = new SimPos(0, 64, 0);
        int wide = new SettlementSites.Grid(512, SettlementSites.DEFAULT_SITE_PERCENT,
                Optional.empty()).near(4242L, here, 4096, ANY).size();
        int tight = new SettlementSites.Grid(256, SettlementSites.DEFAULT_SITE_PERCENT,
                Optional.empty()).near(4242L, here, 4096, ANY).size();
        System.out.printf("within 4096 blocks: %d sites at region 512, %d at 256%n",
                wide, tight);
        assertTrue(tight > wide * 3,
                "halving the region gave " + tight + " sites against " + wide);
    }

    @Test
    @DisplayName("the measured site chance is the number the config exposes")
    void theMeasuredSiteChanceIsTheNumberTheConfigExposes() {
        for (int percent : new int[]{0, 10, 35, 60, 100}) {
            SettlementSites.Grid grid =
                    new SettlementSites.Grid(512, percent, Optional.empty());
            int held = 0;
            int looked = 0;
            for (long seed = 0; seed < 40; seed++) {
                for (int rz = -40; rz < 40; rz++) {
                    for (int rx = -40; rx < 40; rx++) {
                        looked++;
                        if (grid.siteIn(seed, rx, rz, ANY).isPresent()) {
                            held++;
                        }
                    }
                }
            }
            double measured = 100.0 * held / looked;
            System.out.printf("site_chance %d -> %.2f%% of %d regions settled%n",
                    percent, measured, looked);
            assertTrue(Math.abs(measured - percent) < 0.5,
                    "site_chance " + percent + " measured " + measured + "%");
        }
    }

    @Test
    @DisplayName("an unanchored grid is the grid the mod always had")
    void anUnanchoredGridIsTheGridTheModAlwaysHad() {
        for (int rz = -20; rz < 20; rz++) {
            for (int rx = -20; rx < 20; rx++) {
                assertEquals(SettlementSites.siteIn(4242L, rx, rz),
                        SettlementSites.Grid.DEFAULT.siteIn(4242L, rx, rz, ANY),
                        "region " + rx + "," + rz + " moved");
            }
        }
        assertEquals(SettlementSites.EDGE_MARGIN,
                SettlementSites.Grid.DEFAULT.edgeMargin());
        assertEquals(SettlementSites.MIN_SEPARATION,
                SettlementSites.Grid.DEFAULT.minSeparation());
        assertEquals(SettlementSites.JITTER_SPAN,
                SettlementSites.Grid.DEFAULT.jitterSpan());
    }

    @Test
    @DisplayName("anchoring leaves the rest of the world exactly where it was")
    void anchoringLeavesTheRestOfTheWorldExactlyWhereItWas() {
        SettlementSites.Grid grid = gridAt(0, 0);
        int[] home = grid.homeRegion().orElseThrow();
        for (int rz = -20; rz < 20; rz++) {
            for (int rx = -20; rx < 20; rx++) {
                if (Math.abs(rx - home[0]) <= 1 && Math.abs(rz - home[1]) <= 1) {
                    continue;   // the nine are the whole point; they may move
                }
                assertEquals(SettlementSites.siteIn(4242L, rx, rz),
                        grid.siteIn(4242L, rx, rz, ANY),
                        "anchoring moved region " + rx + "," + rz);
            }
        }
    }
}
