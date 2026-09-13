package com.civilization.sim.settlement;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which people sleep under which roof.
 *
 * <p>{@link BuildCatalog} says what a settlement <em>can</em> build and
 * {@link StagePlanner} says what it builds <em>next</em>, and until now neither
 * had any opinion about who was building it. That was right while every town in
 * the mod was a variation on a village. It stopped being right the moment the
 * orcs got a home of their own: a war camp whose stage program raises two
 * cottages is a war camp with two cottages in it, and a human village whose
 * catalog scan can reach a hut is a village with a hut in it.
 *
 * <h2>Why a table here rather than a column on {@link BuildingType}</h2>
 *
 * <p>A column was the first design and it is worse for a reason worth writing
 * down. Every row of the catalog would have to carry a people — forty-odd rows,
 * thirty-eight of which have no opinion at all — and the default value of that
 * column would be "anybody", which is to say the column would be blank
 * everywhere it did not matter and the one place it did matter would be easy to
 * forget. Worse, it would answer only half the question: a filter can keep
 * cottages out of a camp, but it cannot say what the camp builds <em>instead</em>,
 * so the stage program would still name a cottage and the camp would simply skip
 * that line and never house anybody.
 *
 * <p>So: one table of substitutions, and everything else derived from it.
 *
 * <ul>
 *   <li>{@link #instead} answers "what does this people build in place of that"
 *       — which is what a stage program asks, so the camp's VILLAGE program
 *       raises two huts where a village raises two cottages, with the program
 *       itself untouched.</li>
 *   <li>{@link #buildableBy} answers "may this people build that at all" —
 *       which is what the catalog scan asks. It is <em>derived</em> from the
 *       same table: a people may not build what they have a replacement for,
 *       and may not build what is somebody else's replacement. Nobody has to
 *       remember to keep two lists in step, because there is one list.</li>
 * </ul>
 *
 * <p>Keyed on the culture rather than on the race. It is the orcs' <em>warhost</em>
 * that builds huts, and an orc culture somebody adds later may well build
 * something else — the id is what a settlement carries and the id is what this
 * reads.
 */
public final class Homes {

    private Homes() {
    }

    /**
     * What the orcs of the warhost raise in place of a human home.
     *
     * <p>Both of the smaller houses become the hut, because an orc camp does not
     * have a cottage-then-house ladder: a hut is what everybody lives in, and a
     * camp that wanted a bigger one wants the chief's. Both of the larger ones
     * become the great hut, which is capped at one by its own catalog row — so a
     * camp that outgrows its huts builds its king a hall and then goes on
     * building huts, which is what a warband looks like.
     */
    private static final Map<String, String> WARHOST = Map.of(
            "civilization:cottage", "civilization:hut",
            "civilization:house", "civilization:hut",
            "civilization:longhouse", "civilization:great_hut",
            "civilization:croft", "civilization:great_hut",
            // And the hall, which is the one entry here that is not a home.
            //
            // A warhost raised a town hall beside its great hut, because the TOWN
            // program wants a hall and nothing told it the orcs already have one.
            // The great hut IS the orc hall — it is where the chief sits, it is
            // what {@code KingPlanner.SEAT} names, and it is the building at the
            // middle of the muster yard. A camp with a second, human, civic
            // building next to it is a camp with somebody else's architecture in it.
            //
            // Written as a substitution rather than as a special case in the
            // program for the reason the class javadoc gives: a substitution
            // answers both halves at once. The program's want for a hall resolves
            // to the great hut, which already stands, so the want is satisfied and
            // nothing is ordered; and buildableBy refuses a town_hall to this
            // people outright, so the catalog scan cannot reach one either.
            "civilization:town_hall", "civilization:great_hut");

    /**
     * What the mire goblins throw up in place of anything anybody else builds.
     *
     * <p>Wider than the orcs' four rows, and it has to be: a war camp is a
     * settlement with different houses in it, and a goblin camp is a different
     * kind of settlement. Nine substitutions, in three groups.
     *
     * <ul>
     *   <li><strong>Shelter.</strong> A cottage becomes a tent and everything
     *       larger becomes a hovel — including the bunkhouse, so a camp's first
     *       roof is a mud hut for three rather than a dormitory for six. There is
     *       no ladder: a hovel is what a goblin lives in, and a goblin who wants
     *       a bigger one wants the chief's.</li>
     *   <li><strong>The store.</strong> Granary, storehouse and warehouse all
     *       become the loot pile, which is capped at one by its own catalog row.
     *       So a camp has exactly one place where everything it owns is, which is
     *       what a heap of stolen goods with a fence round it actually is — and
     *       what makes burning it a raid worth making.</li>
     *   <li><strong>The seat.</strong> A town hall becomes the chieftain's hut.
     *       Nothing else does: the two big human homes go to hovels, because a
     *       camp does not raise its chief a second roof by accident.</li>
     * </ul>
     */
    private static final Map<String, String> MIRE = Map.ofEntries(
            Map.entry("civilization:cottage", "civilization:tent"),
            Map.entry("civilization:bunkhouse", "civilization:hovel"),
            Map.entry("civilization:house", "civilization:hovel"),
            Map.entry("civilization:longhouse", "civilization:hovel"),
            Map.entry("civilization:croft", "civilization:hovel"),
            Map.entry("civilization:granary", "civilization:loot_pile"),
            Map.entry("civilization:storehouse", "civilization:loot_pile"),
            Map.entry("civilization:warehouse", "civilization:loot_pile"),
            Map.entry("civilization:town_hall", "civilization:chieftain_hut"));

    /**
     * What a people will not build at all, having nothing to build instead.
     *
     * <p>The other half of the table, and the half the orcs never needed. A
     * substitution says "this people build that differently"; this says "this
     * people do not do that", and the difference is the whole of what makes a
     * goblin camp an economy rather than a village with mud walls.
     *
     * <p><strong>The field is the one that matters.</strong> Goblins do not farm,
     * ever, at any size — they forage and they steal. So the farm is refused, and
     * with it the whole apparatus a farm is the front of: a mill grinds a harvest
     * nobody cut, a granary is somebody else's word for the heap, and an animal
     * farm is a promise to feed something. Three lines of the shipped stage
     * programs name a farm, a mill and a market, and not one of them has to know
     * about goblins, because {@code StagePlanner} asks this before it asks the
     * catalog whether the thing exists.
     *
     * <p>The rest is what a camp is too small and too transient to want. A market
     * needs somebody to trade with, an inn needs travellers, a carpentry is a
     * trade rather than a scavenge, and a library in a swamp is a joke. What is
     * <em>not</em> here is deliberate: goblins keep the lumber camp, the mine, the
     * smith, the watchtower and the workshop, because stripping a wood, digging a
     * hole and beating a shiv out over a fire are exactly what scavengers do.
     */
    private static final Map<String, java.util.Set<String>> REFUSED = refused();

    private static Map<String, java.util.Set<String>> refused() {
        Map<String, java.util.Set<String>> table = new LinkedHashMap<>();
        table.put("civilization:goblin/mire", java.util.Set.of(
                "civilization:farm",
                "civilization:animal_farm",
                "civilization:mill",
                "civilization:carpentry",
                "civilization:market",
                "civilization:inn",
                "civilization:library",
                "civilization:grand_library"));
        return Map.copyOf(table);
    }

    private static final Map<String, Map<String, String>> INSTEAD = instead();

    private static Map<String, Map<String, String>> instead() {
        Map<String, Map<String, String>> table = new LinkedHashMap<>();
        table.put("civilization:orc/warhost", WARHOST);
        table.put("civilization:goblin/mire", MIRE);
        return Map.copyOf(table);
    }

    /**
     * Whether this people flatly refuse to build this, substitution or no.
     *
     * <p>Separate from {@link #buildableBy} by name so that callers who mean
     * "they have nothing like this" can say it. The farm lane in
     * {@code BuildPlanner} and the famine rescue in {@code Settlement} both ask
     * this rather than the general question, because the general answer for a
     * goblin camp and a cottage is also no and the two nos want different
     * handling — a camp builds a hovel instead of a cottage and builds
     * <em>nothing</em> instead of a field.
     */
    public static boolean refuses(String cultureId, String blueprintId) {
        if (cultureId == null || blueprintId == null) {
            return false;
        }
        return REFUSED.getOrDefault(cultureId, java.util.Set.of())
                .contains(BuildPlanner.baseIdOf(blueprintId));
    }

    /**
     * Every building that is somebody's replacement, and whose.
     *
     * <p>Derived from {@link #INSTEAD} rather than listed again, which is the
     * whole point of deriving {@link #buildableBy}: adding a people's home to
     * the table above is all it takes to keep that home out of everybody else's
     * town.
     */
    private static final Map<String, String> BELONGS_TO = belongsTo();

    private static Map<String, String> belongsTo() {
        Map<String, String> table = new LinkedHashMap<>();
        INSTEAD.forEach((culture, swaps) ->
                swaps.values().forEach(home -> table.put(home, culture)));
        return Map.copyOf(table);
    }

    /**
     * The home this people raise where the program names that one.
     *
     * <p>Answers with the id it was given whenever this people have no opinion,
     * so a caller can map every want through it without special-casing anybody.
     * Matched on the base id, so a save holding {@code civilization:cottage_l2}
     * resolves the same as a fresh {@code civilization:cottage}.
     */
    public static String instead(String cultureId, String blueprintId) {
        if (cultureId == null || blueprintId == null) {
            return blueprintId;
        }
        String swapped = INSTEAD.getOrDefault(cultureId, Map.of())
                .get(BuildPlanner.baseIdOf(blueprintId));
        return swapped == null ? blueprintId : swapped;
    }

    /**
     * Whether this people build this at all.
     *
     * <p>Two refusals and nothing else. A people does not build what it has a
     * replacement for — the orcs raise a hut, never a cottage. And nobody builds
     * somebody else's replacement — a Norman village never raises a hut, because
     * a hut is what the warhost builds instead of a house.
     *
     * <p>Three refusals now rather than two: a people also does not build what
     * {@link #refuses} says they have nothing like. Goblins never raise a field,
     * and unlike their houses there is nothing they raise instead.
     *
     * <p>Everything not in the table is buildable by everybody, which is the
     * honest default: a granary is a granary.
     */
    public static boolean buildableBy(String cultureId, String blueprintId) {
        if (blueprintId == null) {
            return true;
        }
        String base = BuildPlanner.baseIdOf(blueprintId);
        if (refuses(cultureId, base)) {
            return false;
        }
        if (cultureId != null
                && INSTEAD.getOrDefault(cultureId, Map.of()).containsKey(base)) {
            return false;
        }
        String owner = BELONGS_TO.get(base);
        return owner == null || owner.equals(cultureId);
    }

    /** Whether anybody at all has a replacement for this, which is what makes it a home. */
    public static boolean isSomebodysOwnHome(String blueprintId) {
        return blueprintId != null && BELONGS_TO.containsKey(BuildPlanner.baseIdOf(blueprintId));
    }
}
