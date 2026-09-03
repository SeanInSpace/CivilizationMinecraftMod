package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

import java.util.List;
import java.util.Map;

/**
 * What makes one people's town different from another's.
 *
 * <p>One culture ships today. The point of the type is that everything which
 * *should* vary by culture has somewhere to live before there are two of them —
 * so adding a second is filling in a table rather than threading a new concept
 * through the simulation.
 *
 * <p>Every field here is a plain value or a list of ids, precisely so this can
 * become a datapack entry later without the planners changing.
 *
 * @param id            the culture's identifier
 * @param pennedAnimals which beasts the animal farm keeps, one pen each, in order
 * @param layouts       the arrangements this people builds in, the one it has
 *                      always built in first; see {@link Layouts}
 * @param townNames     what this people calls its settlements
 * @param familyNames   what it calls its families
 * @param givenNames    what it calls its children
 */
public record Culture(String id, List<String> pennedAnimals, List<String> layouts,
                      List<String> townNames, List<String> familyNames,
                      List<String> givenNames) {

    /**
     * Every list copied, and the layouts never empty.
     *
     * <p>Copied because a datapack loader is where this is going and it will
     * hand over whatever it parsed into; a culture that could be edited after it
     * was defined is a table entry that does not stay put.
     *
     * <p>The order of {@code layouts} carries meaning: a settlement restored
     * from a save written before layouts were recorded takes
     * {@code layouts.get(0)}, so whatever a people built before this existed has
     * to stay at the head of its list or every town of theirs already standing
     * in somebody's world is rearranged under them.
     */
    public Culture {
        pennedAnimals = List.copyOf(pennedAnimals);
        layouts = List.copyOf(layouts);
        townNames = List.copyOf(townNames);
        familyNames = List.copyOf(familyNames);
        givenNames = List.copyOf(givenNames);
        if (layouts.isEmpty()) {
            throw new IllegalArgumentException(id + " lays a town out no way at all");
        }
    }

    /** Arrangement ids, as {@link Layouts} knows them. */
    public static final String LAYOUT_RING = "ring";
    public static final String LAYOUT_WARREN = "warren";
    public static final String LAYOUT_STRONGHOLD = "stronghold";
    public static final String LAYOUT_ORGANIC = "organic";
    public static final String LAYOUT_HIGH_STREET = "high_street";
    public static final String LAYOUT_RING_STREETS = "ring_streets";
    public static final String LAYOUT_STRONGHOLD_STREETS = "stronghold_streets";
    public static final String LAYOUT_RADIAL_CONCENTRIC = "radial_concentric";
    public static final String LAYOUT_CROSSROADS = "crossroads";
    public static final String LAYOUT_BASTIDE = "bastide";
    public static final String LAYOUT_THORP = "thorp";
    public static final String LAYOUT_CRESCENTS = "crescents";
    public static final String LAYOUT_GREEN = "green";

    /**
     * Which of this people's arrangements a town centred here is laid out in.
     *
     * <p>Chosen from the centre rather than at random, so a town is the same
     * town on every reload and in every test without anything having to be
     * written down. The two coordinates are avalanched first because a centre is
     * not a random number: a charter is planted where somebody happened to be
     * standing, and a generated town lands on a spacing grid. Reading the low
     * bits of either coordinate raw would hand a whole world one arrangement, or
     * put every town on a diagonal in the same one.
     */
    public String layoutFor(SimPos centre) {
        return layouts.get(Math.floorMod(spread(centre), layouts.size()));
    }

    /** How this people lays a town out on the ground at that centre. */
    public Layout arrangementFor(SimPos centre) {
        return Layouts.of(layoutFor(centre));
    }

    /**
     * SplitMix64's finaliser over the two coordinates.
     *
     * <p>Wanted for its avalanche rather than its speed: one block of difference
     * in x has to change the choice, or a people's second arrangement only ever
     * shows up in whole regions of a world at a time.
     *
     * <p>The third constant is there because the finaliser maps zero to zero,
     * and zero is not an ordinary coordinate: it is world spawn, and it is the
     * centre every fixture in the test suite uses. Without it the origin always
     * took the head of the list, so a people's other arrangements were unreachable
     * from the one place a player is most likely to found a town.
     */
    private static long spread(SimPos centre) {
        long h = centre.x() * 0x9E3779B97F4A7C15L
                ^ centre.z() * 0xC2B2AE3D27D4EB4FL
                ^ 0x2545F4914F6CDD1DL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /**
     * The old three-field shape, for the entries that only ever set those.
     *
     * <p>Kept so that adding names to the record did not mean editing every
     * culture and every test that builds one.
     */
    public Culture(String id, List<String> pennedAnimals, String layout) {
        this(id, pennedAnimals, List.of(layout));
    }

    /** The same, for a people who build in more than one arrangement. */
    public Culture(String id, List<String> pennedAnimals, List<String> layouts) {
        this(id, pennedAnimals, layouts, LOWLAND_TOWNS, LOWLAND_FAMILIES, LOWLAND_GIVEN);
    }

    static final List<String> LOWLAND_TOWNS = List.of(
            "Ashmarch", "Bellbrook", "Millbrook", "Stonebridge", "Fairwater",
            "Oakhollow", "Greenfield", "Whitecliff");

    static final List<String> LOWLAND_FAMILIES = List.of(
            "Baker", "Miller", "Smith", "Cooper", "Fletcher", "Mason", "Turner", "Weaver");

    static final List<String> LOWLAND_GIVEN = List.of(
            "Ada", "Bren", "Cyn", "Dov", "Esa", "Fen", "Gil", "Hana", "Ivo", "Jor");

    public static final Culture DEFAULT = new Culture(
            "kingdoms:default",
            List.of("minecraft:cow", "minecraft:sheep", "minecraft:pig", "minecraft:chicken"),
            LAYOUT_RING);

    /**
     * The lowland people, who are what every town has quietly been all along.
     *
     * <p>Settlements were already stamped {@code kingdoms:norman} and the
     * blueprint loader was already looking for {@code kingdoms:norman/house}
     * before anything defined a culture by that name — so every lookup fell
     * through to {@link #DEFAULT} and nobody noticed, because the default was
     * the only thing there was to fall through to. Naming it is what turns the
     * fallback from a coincidence into a decision.
     */
    public static final Culture NORMAN = new Culture(
            "kingdoms:norman",
            List.of("minecraft:cow", "minecraft:sheep", "minecraft:pig", "minecraft:chicken"),
            // A bastide is a Norman idea in the most literal sense: a town
            // pegged out whole by somebody with a charter, a market place left
            // open in the middle and a road round the outside marking where it
            // stops. The ring stays first -- every Norman town already standing
            // was laid out in one.
            // And a green: the village round an open middle is the archetypal
            // lowland farming settlement, which is what these people are when
            // they are not founding anything.
            List.of(LAYOUT_RING, LAYOUT_BASTIDE, LAYOUT_GREEN));

    /**
     * The hill people, who keep different beasts.
     *
     * <p>A second entry in the table, which is the whole claim the culture type
     * was making: that a second people is filling this in rather than threading
     * a new idea through the simulation. Goats and rabbits over pigs and cows —
     * the same four pens, because the animal farm's plot is reserved in the
     * catalogue and a culture cannot quietly outgrow the ground set aside for
     * it. Widening that reservation is what a fifth pen would cost.
     */
    public static final Culture HIGHLAND = new Culture(
            "kingdoms:highland",
            List.of("minecraft:goat", "minecraft:sheep", "minecraft:rabbit",
                    "minecraft:chicken"),
            // Rings are a lowland idea. A people who live where the ground will
            // not take a lattice put a house wherever there is room for one, and
            // that is what the organic scatter is -- the only arrangement here
            // whose spacing is a promise rather than a consequence.
            // A thorp is the same instinct with a track through it: yards off a
            // lane, nothing lining up, and an edge ragged enough that it does not
            // read as a rectangle cut out of a wood. It is what the organic
            // scatter would be if these people had ever agreed on a road.
            List.of(LAYOUT_ORGANIC, LAYOUT_THORP));

    /**
     * The goblins, who do not build towns so much as accumulate them.
     *
     * <p>The first entry that proves the type was worth having. Everything that
     * makes a goblin settlement a goblin settlement is filled in here — the
     * beasts, the names, and above all the arrangement. A warren grows by
     * digging in wherever the digging is good and budding a new knot off the
     * last one; it has no high street and never did.
     */
    /**
     * Townsfolk, who lay a street and build along it.
     *
     * <p>The first people here whose plan is a plan: a spine with a market
     * widening on it, a lane off, a back lane behind. Given a culture of their
     * own rather than handed to the lowlanders, because replacing what every
     * existing town is would rewrite every settlement already standing in
     * somebody's world.
     *
     * <p>They also get the compass-drawn radial town, which had been registered
     * and reachable from nothing but {@code /civ buildtest}. It belongs here
     * rather than with the vale folk: a Rundling is a shape a village
     * <em>grows</em> into round its green, and this one is a shape somebody
     * ruled — rings that do not wander and a hall set on the middle. The
     * burghers are the only people in the table who lay a plan out before they
     * build on it, so they are the only ones a drawn town is honest for.
     *
     * <p>One thing it does not do that its own javadoc claims: the plot it keeps
     * on the green does not get the hall. A town builds shelter, then food, then
     * safety, and the hall is the capstone of the last of those, so in a grown
     * town of eighty-three buildings the green took the camp post and the hall
     * stood 140 blocks out. That is no worse than the arrangements already
     * shipping — the same town under a high street put its hall at 133 — and the
     * post is the right thing to have in the middle of a green anyway.
     */
    public static final Culture BURGHER = new Culture(
            "kingdoms:burgher",
            List.of("minecraft:cow", "minecraft:sheep", "minecraft:pig", "minecraft:chicken"),
            List.of(LAYOUT_HIGH_STREET, LAYOUT_RADIAL_CONCENTRIC,
                    LAYOUT_CROSSROADS));

    /**
     * The vale folk, who build round a green.
     *
     * <p>A ring of frontage about an open middle, with lanes striking out
     * through it — a Rundling, and one of the oldest village forms there is.
     * Everybody faces the green, so the green is where everything happens; the
     * houses on the outer face of each ring look across the road at the backs of
     * the ring beyond, which is what makes the shape read as enclosure rather
     * than as concentric circles.
     *
     * <p>Deliberately a new people rather than a change to {@link #NORMAN},
     * whose towns are the old concentric lattice. Both arrangements exist and
     * {@code Layouts.streetsFirst} and {@code Layouts.lattice} swap between them,
     * so nothing already standing in anybody's world is rearranged by this
     * entry — it is a people who were not there before, not a new opinion about
     * the people who were.
     */
    public static final Culture VALE = new Culture(
            "kingdoms:vale",
            List.of("minecraft:cow", "minecraft:sheep", "minecraft:goat",
                    "minecraft:chicken"),
            // Crescents are the ring road's mannered cousin: a lane that leaves
            // the street, bows round a green and comes back to it. A people who
            // already build in circles are the only ones who would think of it.
            List.of(LAYOUT_RING_STREETS, LAYOUT_CRESCENTS),
            List.of("Ringmere", "Greenhaugh", "Hollowdean", "Roundwell",
                    "Thornring", "Elmgarth", "Wilbury", "Combe Dando"),
            List.of("Hayward", "Reeve", "Shepherd", "Orchard", "Greenway",
                    "Thatcher", "Bramble", "Fielding"),
            List.of("Alis", "Bede", "Cwen", "Dunstan", "Edith", "Frith", "Godric",
                    "Hilda", "Leofa", "Mildred"));

    public static final Culture GOBLIN = new Culture(
            "kingdoms:goblin",
            List.of("minecraft:chicken", "minecraft:pig", "minecraft:rabbit",
                    "minecraft:chicken"),
            List.of(LAYOUT_WARREN),
            List.of("Gritmaw", "Snagholt", "Murkdig", "Rotcrag", "Slugwarren",
                    "Cinderhole", "Grubfen", "Thistlemire"),
            List.of("Snag", "Grib", "Mulch", "Skarn", "Wretch", "Gnash", "Bogle", "Nix"),
            List.of("Zib", "Krek", "Nub", "Vex", "Grot", "Hix", "Snee", "Ug", "Yark", "Pib"));

    /**
     * The orcs, who lay a camp out in rows and mean it.
     *
     * <p>The other end of the same argument. Where a warren sprawls, a
     * stronghold is a grid on a fixed pitch filled from the middle outward —
     * dense, regimented, and obviously the work of somebody who counts. Same
     * simulation, same planners, same everything: only the table entry differs.
     *
     * <p>The ruled gridiron joins it, having been registered and named by
     * nobody. It is the same discipline with the roads drawn first — streets
     * ruled both ways and the blocks filled in between them — which is the one
     * arrangement in the table that could not belong to anybody else. A warren
     * has no streets on purpose and a village's bend by design; only a people
     * who count lay a carriageway straight.
     */
    public static final Culture ORC = new Culture(
            "kingdoms:orc",
            List.of("minecraft:pig", "minecraft:cow", "minecraft:goat", "minecraft:wolf"),
            List.of(LAYOUT_STRONGHOLD, LAYOUT_STRONGHOLD_STREETS),
            List.of("Karrgurd", "Dromgar", "Ironmaw", "Bloodpost", "Skullwatch",
                    "Grimhold", "Ashfang", "Warmoot"),
            List.of("Gorehand", "Skullsplit", "Ironjaw", "Blacktusk", "Redaxe",
                    "Bonebreak", "Stonefist", "Grimhide"),
            List.of("Brak", "Durg", "Ghal", "Hrok", "Kazh", "Morg", "Rurk", "Thok",
                    "Uzga", "Zharg"));

    private static final Map<String, Culture> KNOWN = Map.of(
            DEFAULT.id(), DEFAULT,
            BURGHER.id(), BURGHER,
            NORMAN.id(), NORMAN,
            HIGHLAND.id(), HIGHLAND,
            VALE.id(), VALE,
            GOBLIN.id(), GOBLIN,
            ORC.id(), ORC);

    /** Every culture that has been defined. */
    public static java.util.Collection<Culture> all() {
        return KNOWN.values();
    }

    /**
     * The named culture, or the default when nobody has defined it.
     *
     * <p>Null-guarded, and not defensively: {@code KNOWN} is a {@code Map.of},
     * which throws on a null key rather than missing it. A settlement restored
     * from a save written before cultures had names carries no id at all, so
     * the one lookup guaranteed to happen on an old world was the one that
     * would have thrown.
     */
    public static Culture of(String id) {
        return id == null ? DEFAULT : KNOWN.getOrDefault(id, DEFAULT);
    }

    /**
     * The folder this culture's blueprints live in.
     *
     * <p>Derived from the id rather than stored beside it, so the two can never
     * disagree: {@code kingdoms:highland} draws from {@code highland/}. Nothing
     * has to exist in that folder — a culture inherits every building it has
     * not drawn.
     */
    public String style() {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    /** How many pens the animal farm needs to hold this culture's beasts. */
    public int penCount() {
        return pennedAnimals.size();
    }
}
