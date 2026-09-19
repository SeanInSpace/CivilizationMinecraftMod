package com.civilization.sim.culture;

import com.civilization.sim.geom.Mix;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which face a settler wears, decided from the person rather than the body.
 *
 * <p>Every human in the mod wore Steve, which is the one thing that made a town
 * read as a diorama rather than as a place: thirty people with one face are
 * thirty copies of a figure. The game already ships nine default player skins —
 * alex, ari, efe, kai, makena, noor, steve, sunny, zuri — and they are free,
 * shipped, and exactly the sheet the settler model already expects.
 *
 * <p><strong>Here rather than beside the renderer</strong> for the reason the
 * whole {@code common} half exists: the choice is arithmetic over a person's id
 * and a table of indices, and nothing about it needs a level, a client or a
 * texture to exist. The renderer's table ({@code PersonSkins}) says what index
 * <em>n</em> looks like; this says which <em>n</em> a person gets, and the two
 * halves are tested on their own sides.
 *
 * <p><strong>Culture biases, the person decides.</strong> Each human people
 * draws from a subset of six of the nine, so a Norman town and a burgher town do
 * not look like the same crowd shuffled; but which of the six a settler gets is
 * a function of their {@code Person.Id} alone, so a face survives a re-embody,
 * a reload and a walk out of the observed radius and back. That is the whole
 * requirement — the culture bias is only a flourish on top of it.
 *
 * <p>The subsets are here and not a column of {@link Culture} deliberately. A
 * culture's table is what a people <em>does</em> — its beasts, its arrangements,
 * its names — and a list of vanilla texture names is a fact about the art on
 * disk. Keeping them apart is also what lets a race with no culture at all (a
 * raider spawned out of the trees) be asked the same question.
 */
public final class Faces {

    /**
     * Vanilla's nine default player skins, in the order {@code DefaultPlayerSkin}
     * lists them.
     *
     * <p>Names rather than paths, because the path is the renderer's business:
     * these are the wide (Steve-build) sheets at
     * {@code minecraft:textures/entity/player/wide/<name>.png}, and the settler
     * model is the wide one, so the slim half of vanilla's table is not ours to
     * use. An index into this list is what travels on the wire.
     */
    public static final List<String> HUMAN_FACES = List.of(
            "alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri");

    /**
     * How many faces a non-human race is asked for.
     *
     * <p>Two: plain and war-painted, which is what the orc art is. Not a per-race
     * count on purpose — a race with one sheet reduces into what it has on the
     * renderer's side, so a second goblin sheet starts splitting goblins without
     * anything here changing. This is the number that was
     * {@code PersonEntity.SKINS_PER_RACE}.
     */
    public static final int VARIANTS_PER_RACE = 2;

    /**
     * Which six of the nine each human people wears.
     *
     * <p>Six rather than nine so a people has a look, and overlapping rather than
     * partitioned so that a face is not a badge: every one of the nine is worn by
     * at least two of the four, and the union is all nine. Three would have been
     * a uniform and nine would have been no culture at all.
     *
     * <p>A culture with no row — the sentinel, and anything a datapack invents
     * later — gets the whole nine. An unnamed town has never been a people, and
     * refusing to draw one would be worse than drawing it out of everything.
     */
    private static final Map<String, List<Integer>> PALETTES = Map.of(
            // Lowlanders: the people every town quietly was.
            Culture.NORMAN.id(), List.of(0, 2, 3, 6, 7, 8),
            Culture.HIGHLAND.id(), List.of(1, 2, 3, 4, 5, 6),
            Culture.BURGHER.id(), List.of(0, 1, 4, 5, 7, 8),
            Culture.VALE.id(), List.of(0, 2, 4, 5, 6, 8));

    /** Every face this people draws from, as indices into {@link #HUMAN_FACES}. */
    public static List<Integer> paletteOf(Culture culture) {
        if (culture == null) {
            return allNine();
        }
        List<Integer> palette = PALETTES.get(culture.id());
        return palette == null ? allNine() : palette;
    }

    private static List<Integer> allNine() {
        return java.util.stream.IntStream.range(0, HUMAN_FACES.size()).boxed().toList();
    }

    /**
     * The face index this person wears, for their race and their people.
     *
     * <p>Keyed on the {@code Person.Id}'s UUID and nothing else, which is the
     * point: a body is a disposable view with a fresh entity uuid every time the
     * player walks back into town, so a face hashed off the body would be
     * reshuffled every time you turned your back. This one is fixed for a life.
     *
     * <p>{@code floorMod} rather than {@code %} because a UUID's hash is as often
     * negative as not, and a negative index would put half a town in no face at
     * all. The hash is avalanched first: a {@code UUID.hashCode} is the two
     * halves xored and folded, which is fine for a table of two and visibly
     * lumpy across nine.
     *
     * <p>A null id — a raider who stands for nobody — is face zero rather than a
     * throw, for the same reason {@code Culture.of} falls back.
     */
    public static int faceFor(Race race, Culture culture, UUID personId) {
        if (personId == null) {
            return 0;
        }
        long spread = Mix.finish(personId.getMostSignificantBits()
                ^ Long.rotateLeft(personId.getLeastSignificantBits(), 32));
        if (race != Race.HUMAN) {
            return (int) Math.floorMod(spread, VARIANTS_PER_RACE);
        }
        List<Integer> palette = paletteOf(culture);
        return palette.get((int) Math.floorMod(spread, palette.size()));
    }

    /** The same question for somebody with no town behind them. */
    public static int faceFor(Race race, UUID personId) {
        return faceFor(race, null, personId);
    }

    private Faces() {
    }
}
