package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.worldgen.SettlementSites;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How far apart the towns of a world actually are, measured rather than argued.
 *
 * <p>{@code SettlementSitesTest} proves the floor — no two sites closer than
 * {@link SettlementSites#MIN_SEPARATION} — and a floor is the one number that
 * says nothing about what a world feels like to walk through. A world can honor
 * a 640-block floor and still hand every player nine towns in their first
 * kilometer, which is what this mod did: the spawn region and its eight
 * neighbors were settled whatever the dice said, so every world opened with a
 * cluster no other square kilometer of it ever produced.
 *
 * <p>So this measures the distribution instead, over 200 seeds with the spawn
 * point thrown anywhere in the world, and prints it. The numbers it prints are
 * the ones quoted in {@code CHANGELOG.md}, {@code PLAYING.md} and the comments
 * on {@link SettlementSites#REGION} — this is where they come from, and a
 * tuning pass that moves them is expected to move those too.
 */
class SettlementSpacingTest {

    /** How many worlds to measure. */
    private static final int WORLDS = 200;

    /** How far from spawn sites are gathered, in blocks. */
    private static final int SAMPLE = 8192;

    /**
     * How far out a site still counts as a subject of the nearest-neighbour
     * measurement.
     *
     * <p>Two regions inside the sample edge. A site at the very rim of the
     * sample has neighbors outside it that were never gathered, so its measured
     * nearest neighbour is too far — an edge effect that flatters the mean.
     * Sites within this radius have every possible neighbour in hand.
     */
    private static final int SUBJECT = SAMPLE - 2 * SettlementSites.REGION;

    private static final Map<String, Integer> ANY = Map.of();

    /** A world: a seed and the spawn point it throws the player at. */
    private record World(long seed, SimPos spawn) {
    }

    private static List<World> worlds() {
        Random random = new Random(8675309L);
        List<World> all = new ArrayList<>();
        for (int i = 0; i < WORLDS; i++) {
            all.add(new World(random.nextLong(),
                    new SimPos(random.nextInt(-2_000_000, 2_000_000), 64,
                            random.nextInt(-2_000_000, 2_000_000))));
        }
        return all;
    }

    private static SettlementSites.Grid gridFor(World world) {
        return SettlementSites.Grid.DEFAULT.anchoredAt(world.spawn());
    }

    @Test
    @DisplayName("the distances between towns, over two hundred worlds")
    void theDistancesBetweenTownsOverTwoHundredWorlds() {
        List<Double> nearest = new ArrayList<>();
        List<Double> starters = new ArrayList<>();
        long sites = 0;
        for (World world : worlds()) {
            SettlementSites.Grid grid = gridFor(world);
            List<SimPos> centers = new ArrayList<>();
            for (SettlementSites.Site site
                    : grid.near(world.seed(), world.spawn(), SAMPLE, ANY)) {
                centers.add(site.center());
            }
            sites += centers.size();
            assertTrue(centers.size() > 50,
                    "only " + centers.size() + " sites within " + SAMPLE
                            + " blocks — too few to measure anything");
            for (SimPos from : centers) {
                if (from.horizontalDistance(world.spawn()) > SUBJECT) {
                    continue;   // its neighbors are outside the sample
                }
                double closest = Double.MAX_VALUE;
                for (SimPos to : centers) {
                    if (to != from) {
                        closest = Math.min(closest, from.horizontalDistance(to));
                    }
                }
                nearest.add(closest);
            }
            int[] home = grid.homeRegion().orElseThrow();
            starters.add(grid.siteIn(world.seed(), home[0], home[1], ANY)
                    .orElseThrow().center().horizontalDistance(world.spawn()));
        }

        nearest.sort(null);
        starters.sort(null);
        System.out.printf(
                "%n=== town spacing over %d worlds (region %d, site_chance %d) ===%n"
                        + "sites sampled           %d (%.0f per world within %d blocks)%n"
                        + "nearest neighbour       min %.0f  median %.0f  mean %.0f  max %.0f%n"
                        + "starter town from spawn min %.0f  median %.0f  mean %.0f  max %.0f%n",
                WORLDS, SettlementSites.REGION, SettlementSites.DEFAULT_SITE_PERCENT,
                sites, (double) sites / WORLDS, SAMPLE,
                nearest.get(0), median(nearest), mean(nearest),
                nearest.get(nearest.size() - 1),
                starters.get(0), median(starters), mean(starters),
                starters.get(starters.size() - 1));

        assertTrue(nearest.get(0) >= SettlementSites.MIN_SEPARATION,
                "two towns came within " + Math.round(nearest.get(0))
                        + " blocks of each other, under the promised "
                        + SettlementSites.MIN_SEPARATION);
        // A floor nothing approaches is a coincidence rather than a guarantee;
        // over 200 worlds some pair does jitter hard against a shared edge.
        assertTrue(nearest.get(0) < SettlementSites.MIN_SEPARATION * 1.2,
                "the closest pair in " + WORLDS + " worlds was "
                        + Math.round(nearest.get(0)) + " blocks apart, nowhere near the "
                        + SettlementSites.MIN_SEPARATION + " the jitter window allows");
        // Measured at 1007; the bound is loose because this is guarding against
        // a world that has quietly gone back to being a cluster, not pinning a
        // number to three figures.
        assertTrue(median(nearest) > 900,
                "the median town has a neighbour only " + Math.round(median(nearest))
                        + " blocks off, which is not a world you can walk through");

        for (double walk : starters) {
            assertTrue(walk >= SettlementSites.STARTER_MIN_FROM_SPAWN,
                    "a starter town landed " + Math.round(walk)
                            + " blocks from the spawn point");
            assertTrue(walk <= SettlementSites.REGION,
                    "a starter town landed " + Math.round(walk)
                            + " blocks out, past its own region");
        }

        // And the walk is a fact about the seed rather than a constant. The
        // keep-out used to be a flat 256, so four worlds in five put their
        // starter at 257 or 258 and the only thing that varied was which way you
        // set off. Drawn per world from [256, 512], the distance has to spread
        // and its middle has to sit well clear of its floor.
        double shortest = starters.get(0);
        double longest = starters.get(starters.size() - 1);
        assertTrue(shortest <= SettlementSites.STARTER_MIN_FROM_SPAWN + 16,
                "the nearest starter over " + WORLDS + " worlds was "
                        + Math.round(shortest) + " blocks out, so the floor of "
                        + SettlementSites.STARTER_MIN_FROM_SPAWN + " is never reached");
        assertTrue(longest >= 480,
                "the furthest starter over " + WORLDS + " worlds was only "
                        + Math.round(longest) + " blocks out");
        assertTrue(median(starters) > shortest + 5,
                "the median starter is " + Math.round(median(starters))
                        + " blocks out against a minimum of " + Math.round(shortest)
                        + " — the walk is still the same walk in every world");
    }

    @Test
    @DisplayName("the density table PLAYING.md prints, at every dial worth turning")
    void theDensityTablePlayingPrints() {
        // Where PLAYING.md's table comes from, 512 included — the dial this
        // replaced, so the comparison is a measurement rather than a memory.
        // Twenty worlds: at 256 a sample holds sixteen times as many sites as
        // at 1024 and the pairwise sweep is 256 times the work.
        System.out.printf("%n=== worldgen.region, measured over 20 worlds ===%n"
                + "%6s %10s %10s   %s%n",
                "region", "min", "median", "sites within 4096 of spawn");
        double previous = 0;
        for (int size : new int[]{256, 512, 1024, 2048}) {
            SettlementSites.Grid grid = new SettlementSites.Grid(
                    size, SettlementSites.DEFAULT_SITE_PERCENT, Optional.empty());
            List<Double> nearest = new ArrayList<>();
            long within4096 = 0;
            for (World world : worlds().subList(0, 20)) {
                List<SimPos> centers = new ArrayList<>();
                for (SettlementSites.Site site
                        : grid.near(world.seed(), world.spawn(), SAMPLE, ANY)) {
                    centers.add(site.center());
                }
                within4096 += grid.near(world.seed(), world.spawn(), 4096, ANY).size();
                for (SimPos from : centers) {
                    if (from.horizontalDistance(world.spawn()) > SAMPLE - 2 * size) {
                        continue;
                    }
                    double closest = Double.MAX_VALUE;
                    for (SimPos to : centers) {
                        if (to != from) {
                            closest = Math.min(closest, from.horizontalDistance(to));
                        }
                    }
                    nearest.add(closest);
                }
            }
            nearest.sort(null);
            System.out.printf("%6d %10.0f %10.0f   %.0f%n",
                    size, nearest.get(0), median(nearest), within4096 / 20.0);
            assertTrue(nearest.get(0) >= grid.minSeparation(),
                    "at region " + size + " two towns came within "
                            + Math.round(nearest.get(0)) + " blocks");
            assertTrue(median(nearest) > previous,
                    "a wider region did not push the towns further apart");
            previous = median(nearest);
        }
    }

    @Test
    @DisplayName("exactly one town is raised regardless of the dice")
    void exactlyOneTownIsRaisedRegardlessOfTheDice() {
        // The point of the change. The guaranteed sites are the ones a world
        // holds with the chance turned off entirely, and there is now one of
        // them: a player meets a town in their first minutes and then walks
        // through the same world everybody else does. It used to be nine.
        for (World world : worlds()) {
            SettlementSites.Grid certain =
                    new SettlementSites.Grid(SettlementSites.REGION, 0,
                            Optional.of(world.spawn()));
            List<SettlementSites.Site> guaranteed =
                    certain.near(world.seed(), world.spawn(), SAMPLE, ANY);
            assertEquals(1, guaranteed.size(),
                    "with site_chance 0 a world held " + guaranteed.size()
                            + " sites within " + SAMPLE + " blocks of spawn");
            double walk = guaranteed.get(0).center()
                    .horizontalDistance(world.spawn());
            assertTrue(walk <= 1024,
                    "the one guaranteed town is " + Math.round(walk)
                            + " blocks out, past the kilometer the join message reads");
        }
    }

    @Test
    @DisplayName("how many towns a world start actually opens with")
    void howManyTownsAWorldStartActuallyOpensWith() {
        // Guaranteed plus lucky: the starter, and whatever the chance happened
        // to put inside the same kilometer. This is the number a player sees,
        // and it is what the join message will list.
        int[] histogram = new int[8];
        for (World world : worlds()) {
            SettlementSites.Grid grid = gridFor(world);
            int within = grid.near(world.seed(), world.spawn(), 1024, ANY).size();
            histogram[Math.min(within, histogram.length - 1)]++;
        }
        System.out.printf("towns within 1024 blocks of spawn, over %d worlds:", WORLDS);
        for (int count = 0; count < histogram.length; count++) {
            if (histogram[count] > 0) {
                System.out.printf(" %d→%d", count, histogram[count]);
            }
        }
        System.out.println();
        int towns = 0;
        for (int count = 0; count < histogram.length; count++) {
            towns += count * histogram[count];
        }
        double average = (double) towns / WORLDS;
        System.out.printf("  average %.2f%n", average);
        assertEquals(0, histogram[0], "a world started with no town at all");
        // It was nine, every time, by construction. Under two on average now,
        // and the second one is there because the dice said so rather than
        // because the world start insisted.
        assertTrue(average < 2.5, "a world opens with " + average
                + " towns inside a kilometer, which is still a cluster");
        assertEquals(0, histogram[histogram.length - 1],
                "a world opened with " + (histogram.length - 1)
                        + " or more towns in the first kilometer");
    }

    @Test
    @DisplayName("the keep-out survives every region the config allows")
    void theKeepOutSurvivesEveryRegionTheConfigAllows() {
        for (int size : new int[]{256, 512, 1024, 2048, 4096}) {
            SettlementSites.Grid plain = new SettlementSites.Grid(
                    size, SettlementSites.DEFAULT_SITE_PERCENT, Optional.empty());
            double furthest = 0;
            for (World world : worlds()) {
                SettlementSites.Grid grid = plain.anchoredAt(world.spawn());
                int[] home = grid.homeRegion().orElseThrow();
                SimPos center = grid.siteIn(world.seed(), home[0], home[1], ANY)
                        .orElseThrow().center();
                double walk = center.horizontalDistance(world.spawn());
                furthest = Math.max(furthest, walk);
                assertTrue(walk >= grid.starterMinFromSpawn(),
                        "at region " + size + " a starter landed " + Math.round(walk)
                                + " blocks out, inside its own keep-out of "
                                + grid.starterMinFromSpawn());
                assertEquals(home[0], grid.regionXOf(
                                new SettlementSites.Site(center, "", "")),
                        "a starter left its own region");
                assertEquals(home[1], grid.regionZOf(
                                new SettlementSites.Site(center, "", "")),
                        "a starter left its own region");
            }
            System.out.printf("region %d: keep-out %d, starter at most %.0f blocks out%n",
                    size, plain.anchoredAt(new SimPos(0, 64, 0)).starterMinFromSpawn(),
                    furthest);
            assertTrue(furthest <= size,
                    "at region " + size + " a starter landed " + Math.round(furthest)
                            + " blocks out, past its own region");
        }
    }

    private static double median(List<Double> sorted) {
        return sorted.get(sorted.size() / 2);
    }

    private static double mean(List<Double> all) {
        double total = 0;
        for (double one : all) {
            total += one;
        }
        return total / all.size();
    }
}
