package com.kingdoms.sim.worldgen;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where towns are, before anybody has been there to look.
 *
 * <p>A generated world contains no settlements, and the obvious fix — a
 * worldgen structure — is the wrong tool. These towns are 150 to 300 blocks
 * across, computed outward from a center, with roads routed against real
 * terrain. Nothing about that fits in chunk-aligned pieces placed by the
 * structure system, and a piece placed at chunk generation cannot wait for the
 * neighboring chunks it wants to route through.
 *
 * <p>So the decision is separated from the building. This class decides
 * <em>where</em>, arithmetically, for the whole infinite world at once and for
 * nothing at all in memory; something else raises a town when a player comes
 * close enough for it to matter. Ask the same question twice and you get the
 * same answer, whether or not anyone has ever visited.
 *
 * <p>The mechanism is vanilla's own trick for spacing structures without a
 * spatial query: cut the world into {@link #REGION} squares and let a hash of
 * {@code (worldSeed, regionX, regionZ)} decide, for each square independently,
 * whether it holds a site, where inside itself, and whose it is. No region ever
 * needs to know about its neighbors, which is what makes the answer cheap and
 * order-independent — and the reason spacing has to be a property of the
 * geometry rather than of a rejection pass.
 *
 * <p>Purely functional and entirely free of Minecraft, so the whole scheme is
 * testable in milliseconds: a 16x16 sweep of regions is 256 hash evaluations.
 */
public final class SettlementSites {

    /**
     * How wide a square of world holds at most one site, in blocks.
     *
     * <p>The one dial worth turning, and it trades two things against each
     * other. Smaller regions mean more towns per square kilometer and a shorter
     * walk to the nearest one; they also squeeze {@link #EDGE_MARGIN}, and the
     * margin is the only thing keeping two neighboring towns from growing into
     * each other's claims. Larger regions give a comfortable minimum separation
     * and a world where you can walk for a long time and see nothing.
     *
     * <p>512 puts sites an average of about {@code REGION / sqrt(SPAWN_CHANCE)}
     * apart — roughly 870 blocks at the chance below — which is a few minutes'
     * walk, and leaves room for a margin twice the radius of a grown town.
     *
     * <p>Must stay at least {@code 2 * EDGE_MARGIN}, or the jitter window
     * inverts and the separation guarantee below is worthless.
     */
    public static final int REGION = 512;

    /**
     * How far inside its own region a site is kept, as sixteenths of the region.
     *
     * <p>A fraction rather than a length, because the region is a dial now and
     * a fixed margin does not survive being turned. At 512 five sixteenths is
     * the 160 this was written as; at 256 it is 80, which is the honest answer —
     * halve the spacing and you halve the daylight between two towns, and
     * {@link #MIN_SEPARATION} stops covering a grown town's sprawl. That cost is
     * documented in PLAYING.md rather than hidden by clamping the margin, because
     * a margin that refuses to shrink would simply invert the jitter window and
     * put every site on its region's midline.
     */
    private static final int EDGE_MARGIN_SIXTEENTHS = 5;

    /**
     * How far inside its own region a site is kept, in blocks, at {@link #REGION}.
     *
     * <p>Not decoration: this constant <em>is</em> the spacing guarantee. See
     * {@link #MIN_SEPARATION}.
     */
    public static final int EDGE_MARGIN = REGION * EDGE_MARGIN_SIXTEENTHS / 16;

    /**
     * The closest two sites can ever be, in blocks. Exactly {@code 2 *
     * EDGE_MARGIN}.
     *
     * <p>Derived, not asserted. A site in region {@code (rx, rz)} lands at
     * {@code rx * REGION + j} for some {@code j} in
     * {@code [EDGE_MARGIN, REGION - EDGE_MARGIN]}, and likewise on z. So for two
     * regions {@code k} apart along one axis, the gap along that axis is at
     * least {@code k * REGION - (REGION - 2 * EDGE_MARGIN)}, which for
     * {@code k = 1} is {@code 2 * EDGE_MARGIN} and only grows with {@code k}.
     * Diagonal neighbors are at least {@code 2 * EDGE_MARGIN} apart on
     * <em>both</em> axes and so at least {@code sqrt(2)} times further. A region
     * holds at most one site, so there is no same-region case. The floor is
     * therefore {@code 2 * EDGE_MARGIN} exactly, reached when two side-by-side
     * regions both jitter hard against the edge they share.
     *
     * <p>Why 320 and not less: a grown town is up to about 300 blocks across, so
     * two of them centered 320 apart have twenty blocks of daylight between their
     * outer edges. Anything tighter and their claims, their fields and their
     * roads are arguing over the same ground — a fight nothing downstream is
     * equipped to settle, because neither town knows the other exists until both
     * are already standing.
     */
    public static final int MIN_SEPARATION = 2 * EDGE_MARGIN;

    /**
     * How much of each region the jitter may use, in blocks, per axis.
     *
     * <p>What is left of the region once both margins are taken out. At 192 a
     * site can sit anywhere in a 192-block square about the region's middle,
     * which is enough that the grid never reads as a grid on a map — the whole
     * reason for jittering rather than planting on the lattice.
     */
    public static final int JITTER_SPAN = REGION - 2 * EDGE_MARGIN;

    /**
     * The chance a region holds a site at all.
     *
     * <p>Without this every region holds a town and the world is a lattice of
     * them at a fixed pitch, which is both obvious to the eye and far denser
     * than anywhere anybody has lived. Empty regions are what turns a grid into
     * a scatter: at 0.35 roughly one region in three is settled, so the walk
     * between neighbors varies from the {@link #MIN_SEPARATION} floor to
     * several thousand blocks.
     */
    public static final int DEFAULT_SITE_PERCENT = 35;

    /**
     * The same number as a fraction, which is how the draw reads it.
     *
     * <p>Measured rather than asserted. Swept over 256,000 regions — forty
     * seeds by an eighty-square block — the realized share of settled regions
     * is 35.03 percent, so the number a world now sets in
     * {@code worldgen.site_chance} really is the fraction it gets. See
     * {@code SettlementSitesSpawnTest}.
     */
    public static final double SPAWN_CHANCE = DEFAULT_SITE_PERCENT / 100.0;

    /**
     * How far from the world spawn the spawn town is set down, per axis, in blocks.
     *
     * <p>140 on each axis is 198 blocks corner to corner, which is the promise:
     * a player who spawns and turns around is looking at a town, not at a
     * horizon. The lower bound two thirds of the way down keeps it from landing
     * on the spawn point itself — a town whose market square is where you
     * appear is not a discovery, it is a spawn building.
     *
     * <p>Capped at just under half a region, because the whole scheme rests on
     * at least one of {@code spawn ± leash} lying inside the spawn region on
     * each axis, which needs {@code 2 * leash < region}. At the 256 floor that
     * makes the leash 127 and the walk 180 blocks.
     */
    private static final int HOME_LEASH = 140;

    /**
     * The y a site carries until somebody resolves it.
     *
     * <p>Not a guess at the ground — a placeholder, and deliberately one that
     * cannot be mistaken for an answer. Real ground height needs the terrain,
     * which needs a level, which is exactly what this class refuses to know
     * about; the caller looks the column up and replaces this. Zero is used
     * because no generated surface sits there, so a site that reaches the world
     * with its y still unresolved is obviously wrong rather than plausibly
     * right.
     */
    public static final int UNRESOLVED_Y = 0;

    /** Distinct hash streams, so the three questions never correlate. */
    private static final long SALT_SPAWN = 0x5EED_0001L;
    private static final long SALT_JITTER = 0x5EED_0002L;
    private static final long SALT_CULTURE = 0x5EED_0003L;
    private static final long SALT_ARRANGEMENT = 0x5EED_0004L;
    private static final long SALT_HOME_X = 0x5EED_0005L;
    private static final long SALT_HOME_Z = 0x5EED_0006L;

    /**
     * A place a town belongs, and whose it is.
     *
     * @param center    where, with {@link #UNRESOLVED_Y} for y until the caller
     *                  looks at the ground
     * @param cultureId the {@link Culture#id()} of the people who settled it
     * @param layoutId  the arrangement they laid it out in, drawn against the
     *                  weights the world was configured with
     */
    public record Site(SimPos center, String cultureId, String layoutId) {
    }

    /**
     * The dials a world turns, and the one place it is anchored.
     *
     * <p>Everything above is arithmetic on constants; this is the same
     * arithmetic on a world's own numbers. It is a value rather than a read of
     * a settings table because this half of the mod is not allowed to know where
     * settings live — the caller in {@code :neoforge} builds one of these from
     * its config and hands it down, and the tests build one directly.
     *
     * <p>The spawn anchor is the interesting field. Without it the grid is what
     * it always was: a hash decides each region independently and a new world
     * may have nothing within a kilometer. With it, the region holding the world
     * spawn and its eight neighbors always hold a site, and the middle one is
     * set down {@link #HOME_LEASH} blocks from the spawn point — so a world
     * begins with a town you can see and eight more a short walk out, which is
     * what Millénaire's opening actually felt like.
     *
     * @param region      how wide a square of world holds at most one site
     * @param sitePercent how often a region holds one at all, outside the nine
     * @param spawn       the world spawn, or empty for a grid with no anchor
     *                    (which is exactly the old behavior)
     */
    public record Grid(int region, int sitePercent, Optional<SimPos> spawn) {

        /** The shipped world: 512-block regions, 35 percent, no anchor. */
        public static final Grid DEFAULT =
                new Grid(REGION, DEFAULT_SITE_PERCENT, Optional.empty());

        /**
         * Sixteen is the floor, and it is geometry rather than taste: below it
         * {@link #edgeMargin} bottoms out at one block and the window
         * {@link #heldBack} needs — a region wider than three margins — closes.
         * The config's own floor is 256.
         */
        public Grid {
            if (region < 16) {
                throw new IllegalArgumentException("region must be at least 16: " + region);
            }
        }

        /** The same grid, anchored on this world spawn. */
        public Grid anchoredAt(SimPos worldSpawn) {
            return new Grid(region, sitePercent, Optional.ofNullable(worldSpawn));
        }

        /** How far inside its own region a site is kept, in blocks. */
        public int edgeMargin() {
            return Math.max(1, region * EDGE_MARGIN_SIXTEENTHS / 16);
        }

        /**
         * The closest two sites can ever be, in blocks.
         *
         * <p>Holds across the nine anchored regions too, which is not free: the
         * middle one is placed first, near spawn and without a margin of its
         * own, and each of the eight is then held back to the far side of its
         * region until it clears this. See {@link #jitterAxis}.
         */
        public int minSeparation() {
            return 2 * edgeMargin();
        }

        /** How much of each region the jitter may use, in blocks, per axis. */
        public int jitterSpan() {
            return region - 2 * edgeMargin();
        }

        /** How far from spawn the spawn town is set down, per axis. */
        public int homeLeash() {
            return Math.min(HOME_LEASH, region / 2 - 1);
        }

        /** Which region a block column belongs to. */
        public int regionOf(int blockCoordinate) {
            return Math.floorDiv(blockCoordinate, region);
        }

        public int regionXOf(Site site) {
            return regionOf(site.center().x());
        }

        public int regionZOf(Site site) {
            return regionOf(site.center().z());
        }

        /** The region the world spawn falls in, if this grid is anchored. */
        public Optional<int[]> homeRegion() {
            return spawn.map(at -> new int[]{regionOf(at.x()), regionOf(at.z())});
        }

        /**
         * The nine regions guaranteed a site, nearest the spawn point first.
         *
         * <p>Empty for an unanchored grid. The order is the one the world start
         * wants: whatever refuses its ground, the next candidate out is already
         * the next best place for the town a player is about to look for.
         */
        public List<int[]> anchoredRegions(long worldSeed) {
            Optional<int[]> home = homeRegion();
            if (home.isEmpty()) {
                return List.of();
            }
            SimPos at = spawn.orElseThrow();
            int[] middle = home.get();
            List<int[]> all = new ArrayList<>();
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    all.add(new int[]{middle[0] + dx, middle[1] + dz});
                }
            }
            all.sort(Comparator.comparingLong(coords ->
                    siteIn(worldSeed, coords[0], coords[1], Map.of())
                            .map(site -> site.center().horizontalDistanceSq(at))
                            .orElse(Long.MAX_VALUE)));
            return all;
        }

        /** Whether this region is one of the nine a spawn anchor guarantees. */
        public boolean isAnchored(int regionX, int regionZ) {
            Optional<int[]> home = homeRegion();
            return home.isPresent()
                    && Math.abs(regionX - home.get()[0]) <= 1
                    && Math.abs(regionZ - home.get()[1]) <= 1;
        }

        /** Whether this is the region the world spawn itself falls in. */
        public boolean isHome(int regionX, int regionZ) {
            Optional<int[]> home = homeRegion();
            return home.isPresent()
                    && regionX == home.get()[0] && regionZ == home.get()[1];
        }

        /** @see SettlementSites#siteIn(long, int, int, Map) */
        public Optional<Site> siteIn(long worldSeed, int regionX, int regionZ,
                                     Map<String, Integer> weights) {
            boolean anchored = isAnchored(regionX, regionZ);
            if (!anchored && unitInterval(hash(worldSeed, regionX, regionZ, SALT_SPAWN))
                    >= sitePercent / 100.0) {
                return Optional.empty();
            }
            int x;
            int z;
            if (isHome(regionX, regionZ)) {
                SimPos at = spawn.orElseThrow();
                x = homeAxis(worldSeed, regionX, regionZ, at.x(), regionX, SALT_HOME_X);
                z = homeAxis(worldSeed, regionX, regionZ, at.z(), regionZ, SALT_HOME_Z);
            } else {
                long jitter = hash(worldSeed, regionX, regionZ, SALT_JITTER);
                // Two draws from one hash: the low half places the site on x and
                // the high half on z. Reusing the same bits for both would put
                // every site on a diagonal of its region.
                x = jitterAxis(jitter & 0xFFFF_FFFFL, regionX);
                z = jitterAxis(jitter >>> 32, regionZ);
                if (anchored) {
                    int[] home = homeRegion().orElseThrow();
                    x = heldBack(worldSeed, home, x, regionX - home[0], 0);
                    z = heldBack(worldSeed, home, z, regionZ - home[1], 1);
                }
            }
            SimPos center = new SimPos(x, UNRESOLVED_Y, z);
            String layout = arrangementFor(worldSeed, regionX, regionZ, weights);
            return Optional.of(new Site(center,
                    peopleWhoBuild(layout, worldSeed, regionX, regionZ), layout));
        }

        /** @see SettlementSites#near(long, SimPos, int, Map) */
        public List<Site> near(long worldSeed, SimPos at, int reach,
                               Map<String, Integer> weights) {
            List<Site> found = new ArrayList<>();
            if (reach < 0) {
                return found;
            }
            int lowX = Math.floorDiv(at.x() - reach, region);
            int highX = Math.floorDiv(at.x() + reach, region);
            int lowZ = Math.floorDiv(at.z() - reach, region);
            int highZ = Math.floorDiv(at.z() + reach, region);
            long limit = (long) reach * reach;
            for (int rz = lowZ; rz <= highZ; rz++) {
                for (int rx = lowX; rx <= highX; rx++) {
                    siteIn(worldSeed, rx, rz, weights)
                            .filter(site -> site.center().horizontalDistanceSq(at) <= limit)
                            .ifPresent(found::add);
                }
            }
            found.sort(Comparator.comparingLong(
                    site -> site.center().horizontalDistanceSq(at)));
            return found;
        }

        /**
         * Where the spawn town goes on one axis.
         *
         * <p>{@link #HOME_LEASH} blocks from the spawn point, on whichever side
         * still lies inside the region. At least one side always does, because
         * the leash is capped below half a region: if {@code p + d} runs past
         * the far edge then {@code p} is already more than {@code d} from the
         * near one. Where both fit, the hash chooses, so the town is not always
         * northeast of every world's spawn.
         *
         * <p>No margin is applied here, and that is the point. The margin is a
         * spacing rule between neighbors, and this site's neighbors are the
         * eight regions around it — all of which are held back from it by
         * {@link #heldBack} instead. Spending the margin here would push the
         * town back toward its region's middle and lose the one thing this
         * method exists to guarantee.
         */
        private int homeAxis(long worldSeed, int regionX, int regionZ,
                             int anchor, int regionIndex, long salt) {
            long low = (long) regionIndex * region;
            long high = low + region - 1;
            int leash = homeLeash();
            int nearest = Math.max(1, leash * 2 / 3);
            long draw = hash(worldSeed, regionX, regionZ, salt);
            int walk = nearest + (int) Long.remainderUnsigned(
                    draw >>> 1, leash - nearest + 1L);
            long plus = anchor + (long) walk;
            long minus = anchor - (long) walk;
            boolean canAdd = plus <= high;
            boolean canSubtract = minus >= low;
            if (canAdd && canSubtract) {
                return (int) ((draw & 1L) == 0 ? plus : minus);
            }
            if (canAdd) {
                return (int) plus;
            }
            if (canSubtract) {
                return (int) minus;
            }
            return (int) Math.max(low, Math.min(high, plus));   // region under 2*leash
        }

        /** The ordinary jitter: somewhere in the region's middle, margins aside. */
        private int jitterAxis(long draw, int regionIndex) {
            int margin = edgeMargin();
            int offset = margin + (int) Long.remainderUnsigned(draw, jitterSpan() + 1L);
            // Long arithmetic to place the region, then narrowed: a region index
            // far enough out to overflow is already millions of blocks past the
            // world border, so there is nothing there to found.
            return (int) ((long) regionIndex * region + offset);
        }

        /**
         * One of the eight, pushed clear of the spawn town.
         *
         * <p>The spawn town ignores its region's margins, so the guarantee that
         * two sites are never closer than {@link #minSeparation} has to be paid
         * for from this side. A neighbor west of the spawn region is capped at
         * {@code homeX - minSeparation}; one east of it is floored at
         * {@code homeX + minSeparation}; one in the same column is left alone,
         * because its separation is coming from the other axis.
         *
         * <p>The window never closes. A neighbor's own far edge sits a whole
         * region plus a margin away from the spawn region's near edge, and the
         * spawn town is at most a region from that edge, so the room left is at
         * least {@code region - 3 * edgeMargin()} — a sixteenth of a region,
         * positive for every region size this grid allows.
         */
        private int heldBack(long worldSeed, int[] home, int placed, int delta, int axis) {
            if (delta == 0) {
                return placed;
            }
            Optional<SimPos> anchor = spawn;
            if (anchor.isEmpty()) {
                return placed;
            }
            int homeSite = axis == 0
                    ? homeAxis(worldSeed, home[0], home[1], anchor.get().x(), home[0], SALT_HOME_X)
                    : homeAxis(worldSeed, home[0], home[1], anchor.get().z(), home[1], SALT_HOME_Z);
            int gap = minSeparation();
            return delta < 0
                    ? Math.min(placed, homeSite - gap)
                    : Math.max(placed, homeSite + gap);
        }
    }

    private SettlementSites() {
    }

    /**
     * The site this region holds, if it holds one.
     *
     * <p>The whole scheme in one function: three independent draws from the same
     * {@code (seed, region)} key decide whether, where, and who.
     */
    public static Optional<Site> siteIn(long worldSeed, int regionX, int regionZ) {
        return siteIn(worldSeed, regionX, regionZ, Map.of());
    }

    /**
     * The same, with the arrangements this world wants and how often it wants
     * them.
     *
     * <p>The weights are handed in rather than read, because this is the pure
     * half of the mod and a table of settings is exactly the kind of thing it
     * must not know how to find. An empty table means every arrangement a people
     * already builds in is equally likely, which is what a world with no opinion
     * should get.
     *
     * <p>A weight of zero is a refusal, not a rounding: an arrangement nobody
     * weighted is never drawn. If every weight is zero the table is treated as
     * absent, since a world where nothing can be built is not what anybody meant
     * by turning everything off.
     */
    public static Optional<Site> siteIn(long worldSeed, int regionX, int regionZ,
                                        Map<String, Integer> weights) {
        return Grid.DEFAULT.siteIn(worldSeed, regionX, regionZ, weights);
    }

    /**
     * Which arrangement this region's town is laid out in.
     *
     * <p>Drawn against the weights by the usual trick: sum them, take the hash
     * modulo the sum, and walk. Sorted by id first, because the walk depends on
     * the order and a map's order is not something to stake a world's shape on.
     */
    private static String arrangementFor(long worldSeed, int regionX, int regionZ,
                                         Map<String, Integer> weights) {
        List<String> wanted = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, Integer> entry : new TreeMap<>(weights).entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                wanted.add(entry.getKey());
                total += entry.getValue();
            }
        }
        if (wanted.isEmpty()) {
            return anyArrangement(worldSeed, regionX, regionZ);
        }
        long draw = Long.remainderUnsigned(
                hash(worldSeed, regionX, regionZ, SALT_ARRANGEMENT), total);
        for (String id : wanted) {
            draw -= weights.get(id);
            if (draw < 0) {
                return id;
            }
        }
        return wanted.get(wanted.size() - 1);   // unreachable; total is the sum
    }

    /** Any arrangement some people already builds in, for a world with no table. */
    private static String anyArrangement(long worldSeed, int regionX, int regionZ) {
        List<String> known = new ArrayList<>();
        for (Culture culture : Culture.all()) {
            if (!culture.id().equals(Culture.DEFAULT.id())) {
                known.addAll(culture.layouts());
            }
        }
        known = known.stream().distinct().sorted().toList();
        int at = (int) Long.remainderUnsigned(
                hash(worldSeed, regionX, regionZ, SALT_ARRANGEMENT), known.size());
        return known.get(at);
    }

    /**
     * A people who builds in this arrangement.
     *
     * <p>The arrangement is chosen first and the people second, which is the
     * opposite of how a settlement normally works — a town is usually laid out
     * the way its people build. Here the world has been told what it should look
     * like, so the shape leads and the culture follows it. Where several peoples
     * build the same shape the draw picks between them; where none does, the
     * arrangement is still honored and the town is simply told to use it.
     */
    private static String peopleWhoBuild(String layoutId, long worldSeed,
                                         int regionX, int regionZ) {
        // Never the sentinel. Culture.of maps every unknown and null id onto
        // kingdoms:default, so a town wearing it cannot be told from a town whose
        // people failed to load -- and it builds rings, so a layout-first draw
        // reaches it constantly if nothing stops it.
        List<String> builders = Culture.all().stream()
                .filter(culture -> !culture.id().equals(Culture.DEFAULT.id()))
                .filter(culture -> culture.layouts().contains(layoutId))
                .map(Culture::id)
                .sorted()
                .toList();
        if (builders.isEmpty()) {
            return cultureFor(worldSeed, regionX, regionZ);
        }
        int at = (int) Long.remainderUnsigned(
                hash(worldSeed, regionX, regionZ, SALT_CULTURE), builders.size());
        return builders.get(at);
    }

    /**
     * Every site whose center lies within {@code reach} blocks of {@code at},
     * nearest first.
     *
     * <p>Distance is horizontal, because y is not resolved here and a town two
     * hundred blocks up a mountain is still the town you walk to.
     */
    public static List<Site> near(long worldSeed, SimPos at, int reach) {
        return near(worldSeed, at, reach, Map.of());
    }

    /** The same, weighted. */
    public static List<Site> near(long worldSeed, SimPos at, int reach,
                                  Map<String, Integer> weights) {
        return Grid.DEFAULT.near(worldSeed, at, reach, weights);
    }

    /** Which region a block column belongs to. */
    public static int regionOf(int blockCoordinate) {
        return Grid.DEFAULT.regionOf(blockCoordinate);
    }

    /**
     * The region that produced this site.
     *
     * <p>Recoverable rather than stored, because the jitter keeps every site
     * strictly inside its own region — so the region is a fact about the center
     * and cannot drift out of step with it the way a second field could.
     */
    public static int regionXOf(Site site) {
        return regionOf(site.center().x());
    }

    /** @see #regionXOf(Site) */
    public static int regionZOf(Site site) {
        return regionOf(site.center().z());
    }

    /**
     * Which people settled this region.
     *
     * <p>Drawn from {@link Culture#all()} in id order. The sort is not tidiness:
     * {@code all()} is backed by a {@code Map.of}, whose iteration order is
     * randomized per JVM, so indexing it directly would give a different people
     * every time the game restarted — a world whose towns changed nationality on
     * relaunch. Sorting by id makes the draw a function of the arguments alone,
     * which is what the rest of this class promises.
     *
     * <p>Read fresh each call rather than cached, so a culture added by a
     * datapack is picked up rather than frozen out by whichever call happened to
     * run first. The cost is sorting a handful of strings.
     *
     * <p>{@code kingdoms:default} is excluded, and that is not tidying. It is
     * the codebase's word for <em>no culture</em>: {@code Culture.of} maps every
     * unknown id and every null to it, so a settlement stamped with it is
     * indistinguishable from one whose culture failed to load. Left in the draw
     * it took a fifth of every world — a fifth of all towns permanently
     * unreadable as a deliberate choice, and identical in play to the Normans
     * anyway, since the two entries hold the same pens and the same layout.
     *
     * <p>Note what this does <em>not</em> promise: adding a culture reshuffles
     * which people hold which region, because it changes the divisor. Sites keep
     * their places; their inhabitants do not.
     */
    private static String cultureFor(long worldSeed, int regionX, int regionZ) {
        List<String> ids = new ArrayList<>();
        for (Culture culture : Culture.all()) {
            if (!Culture.DEFAULT.id().equals(culture.id())) {
                ids.add(culture.id());
            }
        }
        if (ids.isEmpty()) {
            return Culture.DEFAULT.id();   // a world with no named people in it
        }
        ids.sort(Comparator.naturalOrder());
        long draw = hash(worldSeed, regionX, regionZ, SALT_CULTURE);
        return ids.get((int) Long.remainderUnsigned(draw, ids.size()));
    }

    /**
     * One hash stream for a region.
     *
     * <p>Mixed in sequence rather than by combining products with XOR, which is
     * the usual way this goes wrong: {@code x * A ^ z * B} maps
     * {@code (x, z)} and {@code (-x, -z)} close together and puts visible
     * structure on the diagonals. {@link #mix64} is a bijection, so folding one
     * coordinate in at a time leaves nothing for the geometry to correlate with.
     */
    private static long hash(long worldSeed, int regionX, int regionZ, long salt) {
        long h = mix64(worldSeed + salt * GOLDEN);
        h = mix64(h + regionX);
        return mix64(h + regionZ);
    }

    /** The odd 64-bit approximation of the golden ratio, as a stream separator. */
    private static final long GOLDEN = 0x9E37_79B9_7F4A_7C15L;

    /** SplitMix64's finalizer: avalanches every input bit across all 64 outputs. */
    private static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
        return z ^ (z >>> 31);
    }

    /**
     * A hash as a fraction in {@code [0, 1)}.
     *
     * <p>The top 53 bits, which is every bit a {@code double} can hold without
     * rounding — so the comparison against {@link #SPAWN_CHANCE} is exact rather
     * than nearly.
     */
    private static double unitInterval(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }
}
