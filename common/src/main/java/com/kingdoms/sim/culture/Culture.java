package com.kingdoms.sim.culture;

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
 * @param layout        how the settlement arranges itself; see {@link Layouts}
 * @param townNames     what this people calls its settlements
 * @param familyNames   what it calls its families
 * @param givenNames    what it calls its children
 */
public record Culture(String id, List<String> pennedAnimals, String layout,
                      List<String> townNames, List<String> familyNames,
                      List<String> givenNames) {

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

    /** How this people lays a town out on the ground. */
    public Layout arrangement() {
        return Layouts.of(layout);
    }

    /**
     * The old three-field shape, for the entries that only ever set those.
     *
     * <p>Kept so that adding names to the record did not mean editing every
     * culture and every test that builds one.
     */
    public Culture(String id, List<String> pennedAnimals, String layout) {
        this(id, pennedAnimals, layout, LOWLAND_TOWNS, LOWLAND_FAMILIES, LOWLAND_GIVEN);
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
            LAYOUT_RING);

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
            LAYOUT_ORGANIC);

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
     */
    public static final Culture BURGHER = new Culture(
            "kingdoms:burgher",
            List.of("minecraft:cow", "minecraft:sheep", "minecraft:pig", "minecraft:chicken"),
            LAYOUT_HIGH_STREET);

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
            LAYOUT_RING_STREETS,
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
            LAYOUT_WARREN,
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
     */
    public static final Culture ORC = new Culture(
            "kingdoms:orc",
            List.of("minecraft:pig", "minecraft:cow", "minecraft:goat", "minecraft:wolf"),
            LAYOUT_STRONGHOLD,
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
