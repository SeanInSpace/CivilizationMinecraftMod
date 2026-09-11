package com.civilization.neoforge.world;

import com.civilization.neoforge.CivilizationConfig;
import com.civilization.neoforge.CivilizationMod;
import com.civilization.neoforge.save.SiteLedger;
import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.Race;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.kingdom.Kingdom;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimWorld;
import com.civilization.sim.worldgen.SettlementSites;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which towns are near enough to walk to, said out loud.
 *
 * <p>The sites have always been knowable — {@code /civ sites} has printed them
 * since worldgen went in — but knowable is not the same as known. That listing
 * is a debug instrument: region coordinates, raw centers, ledger states, y
 * unresolved. It answers "is the chooser working", which is a question only the
 * person who wrote it has.
 *
 * <p>A player's question is different and much smaller: <em>where do I go?</em>
 * Millénaire answered it on the join screen — a line per village with a
 * direction and a distance — and that one message is most of why its worlds felt
 * inhabited from the first minute rather than after an hour of walking. This is
 * that message. Nearest first, at most {@link #MOST_LISTED}, inside
 * {@link #EARSHOT}.
 *
 * <p>Deliberately free of anything a chat line does not need. The formatting
 * half takes numbers and returns a string, which is why it can be tested
 * without a level, a payload or a codec — see {@code SiteDirectoryTest}.
 */
public final class SiteDirectory {

    private SiteDirectory() {
    }

    /**
     * How far out a town is worth mentioning, in blocks.
     *
     * <p>A kilometer. Two regions and a bit at the default pitch, which reaches
     * the whole of the guaranteed nine and usually a scattered site or two past
     * them. Further than this is not a direction, it is a project.
     */
    public static final int EARSHOT = 1024;

    /**
     * How many towns are named before the list is cut.
     *
     * <p>Five. The join message competes with the world loading and the death of
     * whatever the player was doing last; a wall of text is skipped, and the
     * sixth-nearest town is not the one anybody walks to first.
     */
    public static final int MOST_LISTED = 5;

    /** The eight points, in the order {@link #bearing} indexes them. */
    private static final String[] POINTS =
            {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /**
     * Which way to walk, to the nearest eighth of a turn.
     *
     * <p>{@code atan2(dx, -dz)} rather than the usual {@code atan2(dz, dx)},
     * because Minecraft's north is negative z and a compass reads clockwise from
     * it. Getting this backwards is the classic way a direction indicator ends up
     * pointing at the reflection of where it meant to.
     */
    public static String bearing(int dx, int dz) {
        double turns = Math.atan2(dx, -dz) / (2 * Math.PI);
        int at = (int) Math.floorMod(Math.round(turns * 8), 8L);
        return POINTS[at];
    }

    /**
     * One line of the directory.
     *
     * <p>Two shapes, and the difference is honest rather than cosmetic. A town
     * that has been raised has a name, because somebody named it. A site that
     * has not is a prediction — the seed says a people will build a shape there —
     * and saying "Haldstead, 800 blocks east" about a town that does not exist
     * yet would be a lie that a player walks eight hundred blocks to catch.
     */
    public static String line(String what, boolean standing, int dx, int dz) {
        long blocks = Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        return "  " + what + " — " + blocks + " blocks " + bearing(dx, dz)
                + (standing ? "" : " (not raised yet)");
    }

    /**
     * A readable scrap of an identifier.
     *
     * <p>{@code civilization:high_street} is a key, not a word. Namespace off,
     * underscores out; nothing cleverer, because the alternative is a
     * translation table for every culture and layout a datapack might add and
     * this is a chat line.
     *
     * <p>A culture id carries its race as a path segment —
     * {@code civilization:human/norman} — and only the last segment is the people's
     * own name, so the path is cut to that. The race is said separately by
     * {@link #describe}, where it can be said in English.
     */
    static String readable(String id) {
        String bare = id == null ? "" : id.substring(id.indexOf(':') + 1);
        return bare.substring(bare.lastIndexOf('/') + 1).replace('_', ' ');
    }

    /**
     * "a Norman crossroads", "an orc Warhost stronghold" — the people and the
     * shape, for a site not yet raised.
     *
     * <p><strong>Humans are not named as humans and everybody else is.</strong>
     * "A human Norman crossroads" is three words where two will do, and it
     * reads like a label on a specimen; "a Norman crossroads" is how somebody
     * would actually say it. What a player needs from this line is whether the
     * neighbors over the hill are people or not, and human is the unmarked case
     * — so the races that are worth a warning get the word and the one that is
     * not does not. A player who wants it spelled out either way has
     * {@code /civ info}, which names the race of every town outright.
     */
    static String describe(String cultureId, String layoutId) {
        String people = readable(cultureId);
        String shape = readable(layoutId);
        String capitalized = people.isEmpty()
                ? people
                : people.substring(0, 1).toUpperCase(Locale.ROOT) + people.substring(1);
        Race race = Culture.of(cultureId).race();
        String kind = race == Race.HUMAN ? "" : race.word() + " ";
        String noun = (kind + capitalized + " " + shape).trim();
        return (startsWithVowel(noun) ? "an " : "a ") + noun;
    }

    private static boolean startsWithVowel(String word) {
        return !word.isEmpty() && "AEIOUaeiou".indexOf(word.charAt(0)) >= 0;
    }

    /**
     * The towns near a position, nearest first, as chat lines.
     *
     * <p>Reads the ledger and the simulation rather than the seed alone,
     * because the two know different things. The seed knows a site is there; the
     * ledger knows whether anybody has looked; the simulation knows what the
     * town ended up being called and where it actually stands, which is not
     * where the arithmetic put it. A site the ledger has refused is left out
     * entirely — there is no town there and there never will be.
     */
    public static List<String> lines(ServerLevel level, SimPos from) {
        List<String> out = new ArrayList<>();
        for (Near near : near(level, from)) {
            out.add(line(near.what(), near.standing(),
                    near.at().x() - from.x(), near.at().z() - from.z()));
        }
        return out;
    }

    /**
     * What is near, before it is words.
     *
     * @param at       where the town is, or where the site says it will be
     * @param what     its name, or a description of what will be built there
     * @param standing whether it has actually been raised
     */
    public record Near(SimPos at, String what, boolean standing) {
    }

    /**
     * @see #lines(ServerLevel, SimPos)
     *
     * <p>Two sources, and both are needed. Every settlement that actually
     * stands is listed first, which covers the ones a player founded with a
     * charter — those have no site and no ledger entry and would otherwise be
     * missing from a list headed "settlements nearby". Then the sites the seed
     * puts nearby, minus any that a standing town already accounts for and minus
     * any the ledger has refused, because nothing is ever built on refused
     * ground. The merged list is sorted by how far it is and cut to
     * {@link #MOST_LISTED}.
     */
    public static List<Near> near(ServerLevel level, SimPos from) {
        List<Near> found = new ArrayList<>();
        long earshot = (long) EARSHOT * EARSHOT;
        SimWorld world = CivilizationMod.simulationFor(level);
        if (world != null) {
            for (Kingdom kingdom : world.kingdoms()) {
                for (Settlement settlement : kingdom.settlements()) {
                    if (settlement.center().horizontalDistanceSq(from) <= earshot) {
                        found.add(new Near(settlement.center(), settlement.name(), true));
                    }
                }
            }
        }
        if (CivilizationConfig.WORLDGEN_ENABLED.get()) {
            SiteLedger ledger = SiteLedger.get(level);
            SettlementSites.Grid grid = WorldgenSettlements.gridFor(level);
            Map<String, Integer> weights = CivilizationConfig.arrangementWeights();
            for (SettlementSites.Site site
                    : grid.near(level.getSeed(), from, EARSHOT, weights)) {
                Optional<SiteLedger.Entry> decided =
                        ledger.entry(grid.regionXOf(site), grid.regionZOf(site));
                if (decided.isPresent() && !decided.get().accepted()) {
                    continue;   // looked at, refused; nothing is ever built here
                }
                SimPos at = decided.flatMap(SiteLedger.Entry::center).orElse(site.center());
                if (alreadyListed(found, at)) {
                    continue;   // it is standing, and was named a moment ago
                }
                found.add(new Near(at, describe(site.cultureId(), site.layoutId()), false));
            }
        }
        found.sort(java.util.Comparator.comparingLong(
                town -> town.at().horizontalDistanceSq(from)));
        return found.size() <= MOST_LISTED ? found : found.subList(0, MOST_LISTED);
    }

    /**
     * Whether a standing town already covers this center.
     *
     * <p>Matched by proximity rather than by identity, because the ledger
     * deliberately stores no settlement id — see {@link SiteLedger}. The radius
     * is generous on purpose: a town's recorded center is where it was founded,
     * and nothing moves it afterwards, so anything this close is the same town.
     */
    private static boolean alreadyListed(List<Near> found, SimPos center) {
        return found.stream()
                .anyMatch(town -> town.at().horizontalDistanceSq(center) <= SAME_TOWN);
    }

    /** Squared blocks within which a center is the same town. */
    private static final long SAME_TOWN = 64L * 64L;

    /** The heading the join message goes under. */
    public static String heading(int howMany) {
        return howMany == 0
                ? "No settlement within " + EARSHOT + " blocks."
                : "Settlements within " + EARSHOT + " blocks:";
    }
}
