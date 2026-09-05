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
     * How far inside its own region a site is kept, in blocks.
     *
     * <p>Not decoration: this constant <em>is</em> the spacing guarantee. See
     * {@link #MIN_SEPARATION}.
     */
    public static final int EDGE_MARGIN = 160;

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
    public static final double SPAWN_CHANCE = 0.35;

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

    /**
     * A place a town belongs, and whose it is.
     *
     * @param centre    where, with {@link #UNRESOLVED_Y} for y until the caller
     *                  looks at the ground
     * @param cultureId the {@link Culture#id()} of the people who settled it
     * @param layoutId  the arrangement they laid it out in, drawn against the
     *                  weights the world was configured with
     */
    public record Site(SimPos centre, String cultureId, String layoutId) {
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
        if (unitInterval(hash(worldSeed, regionX, regionZ, SALT_SPAWN)) >= SPAWN_CHANCE) {
            return Optional.empty();
        }
        long jitter = hash(worldSeed, regionX, regionZ, SALT_JITTER);
        // Two draws from one hash: the low half places the site on x and the
        // high half on z. Reusing the same bits for both would put every site on
        // a diagonal of its region.
        int offsetX = EDGE_MARGIN + (int) Long.remainderUnsigned(
                jitter & 0xFFFF_FFFFL, JITTER_SPAN + 1L);
        int offsetZ = EDGE_MARGIN + (int) Long.remainderUnsigned(
                jitter >>> 32, JITTER_SPAN + 1L);
        // Long arithmetic to place the region, then narrowed: a region index far
        // enough out to overflow is already millions of blocks past the world
        // border, so there is nothing there to found.
        SimPos centre = new SimPos(
                (int) ((long) regionX * REGION + offsetX),
                UNRESOLVED_Y,
                (int) ((long) regionZ * REGION + offsetZ));
        String layout = arrangementFor(worldSeed, regionX, regionZ, weights);
        return Optional.of(new Site(centre,
                peopleWhoBuild(layout, worldSeed, regionX, regionZ), layout));
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
        List<Site> found = new ArrayList<>();
        if (reach < 0) {
            return found;
        }
        int lowX = Math.floorDiv(at.x() - reach, REGION);
        int highX = Math.floorDiv(at.x() + reach, REGION);
        int lowZ = Math.floorDiv(at.z() - reach, REGION);
        int highZ = Math.floorDiv(at.z() + reach, REGION);
        long limit = (long) reach * reach;
        for (int rz = lowZ; rz <= highZ; rz++) {
            for (int rx = lowX; rx <= highX; rx++) {
                siteIn(worldSeed, rx, rz, weights)
                        .filter(site -> site.centre().horizontalDistanceSq(at) <= limit)
                        .ifPresent(found::add);
            }
        }
        found.sort(Comparator.comparingLong(site -> site.centre().horizontalDistanceSq(at)));
        return found;
    }

    /** Which region a block column belongs to. */
    public static int regionOf(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, REGION);
    }

    /**
     * The region that produced this site.
     *
     * <p>Recoverable rather than stored, because the jitter keeps every site
     * strictly inside its own region — so the region is a fact about the center
     * and cannot drift out of step with it the way a second field could.
     */
    public static int regionXOf(Site site) {
        return regionOf(site.centre().x());
    }

    /** @see #regionXOf(Site) */
    public static int regionZOf(Site site) {
        return regionOf(site.centre().z());
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
