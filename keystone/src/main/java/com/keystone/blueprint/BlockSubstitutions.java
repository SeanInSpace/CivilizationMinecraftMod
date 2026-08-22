package com.keystone.blueprint;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What to put where a foreign block used to be.
 *
 * <p>A Structurize {@code .blueprint} is authored against a modpack, and the
 * packs worth importing lean on it heavily. Measured over a 240-file
 * MineColonies schematic set: {@code domum_ornamentum:shingle} appears in 977
 * palettes, {@code structurize:blocksubstitution} fills fifteen thousand cells,
 * and Biomes O' Plenty, Quark and half a dozen others turn up throughout. Import
 * without a policy for those and every roof in the pack disappears.
 *
 * <p>Three layers, in order:
 *
 * <ol>
 *   <li><strong>Structurize's own markers</strong>, which are not blocks at all
 *       but instructions — "leave whatever is here", "put ground here". Getting
 *       these wrong is the difference between a hut sitting on the land and a
 *       hut with a slab of cobble stamped under it.</li>
 *   <li><strong>Named blocks</strong> the survey proved common enough to be
 *       worth a considered answer.</li>
 *   <li><strong>A suffix heuristic</strong> for everything else. This is the
 *       layer that earns its keep: {@code biomesoplenty:hellbark_stairs},
 *       {@code quark:crimson_ladder} and {@code naturesaura:ancient_stairs} are
 *       all handled without ever being named, because a mod block called
 *       {@code *_stairs} is a stairs block.</li>
 * </ol>
 *
 * <p>Substitution keeps the original block state's <em>properties</em> — see
 * {@link StructurizeNbt} — so a substituted stair keeps its facing and its half,
 * and a roof made of somebody else's shingles still slopes the right way.
 */
public final class BlockSubstitutions {

    /** Returned when a cell should be left alone rather than filled. */
    public static final String SKIP = null;

    /**
     * What an unrecognised block becomes when nothing else matches.
     *
     * <p>Solid rather than empty, deliberately. An unknown block is more often
     * part of a wall than a decoration, and a building with a hole in it reads
     * as broken while one with a plain patch reads as plain.
     */
    public static final String DEFAULT = "minecraft:oak_planks";

    private static final Map<String, String> NAMED = new LinkedHashMap<>();
    private static final Map<String, String> BY_SUFFIX = new LinkedHashMap<>();

    static {
        // --- Structurize's instruction blocks ---
        // "Leave the world as it is." Overwhelmingly the ground a building
        // stands on, which is why it must not become a block: stamping these
        // would bury the structure in its own foundation slab.
        NAMED.put("structurize:blocksubstitution", SKIP);
        NAMED.put("structurize:blocktagsubstitution", SKIP);
        // "Whatever the ground here is." Dirt is the honest neutral answer.
        NAMED.put("structurize:blocksolidsubstitution", "minecraft:dirt");
        NAMED.put("structurize:blockfluidsubstitution", "minecraft:water");

        // --- MineColonies fixtures ---
        // Hut blocks name which building this is to MineColonies. They mean
        // nothing here, and a consuming mod places its own marker anyway.
        NAMED.put("minecolonies:blockwaypoint", SKIP);
        NAMED.put("minecolonies:blockminecoloniesrack", "minecraft:barrel");
        NAMED.put("minecolonies:barrel_block", "minecraft:composter");
        NAMED.put("minecolonies:composted_dirt", "minecraft:dirt");
        NAMED.put("minecolonies:blockminecoloniesnamedgrave", "minecraft:cobblestone_wall");

        // --- Domum Ornamentum, the decoration mod MineColonies packs ship with ---
        // Shingles are roofing, and they carry stair properties, so mapping them
        // to stairs keeps the pitch of every roof in the pack.
        NAMED.put("domum_ornamentum:shingle", "minecraft:cobblestone_stairs");
        NAMED.put("domum_ornamentum:shingle_slab", "minecraft:cobblestone_slab");
        NAMED.put("domum_ornamentum:vanilla_stairs_compat", "minecraft:oak_stairs");
        NAMED.put("domum_ornamentum:double_crossed", "minecraft:oak_fence");

        // --- The suffix heuristic ---
        // Order matters: the longer, more specific ending has to be tested
        // first, or every fence gate becomes a fence and every trapdoor a door.
        BY_SUFFIX.put("_fence_gate", "minecraft:oak_fence_gate");
        BY_SUFFIX.put("_pressure_plate", "minecraft:oak_pressure_plate");
        BY_SUFFIX.put("_glass_pane", "minecraft:glass_pane");
        BY_SUFFIX.put("_trapdoor", "minecraft:oak_trapdoor");
        BY_SUFFIX.put("_wall_torch", "minecraft:wall_torch");
        BY_SUFFIX.put("_stairs", "minecraft:oak_stairs");
        BY_SUFFIX.put("_slab", "minecraft:oak_slab");
        BY_SUFFIX.put("_planks", "minecraft:oak_planks");
        BY_SUFFIX.put("_fence", "minecraft:oak_fence");
        BY_SUFFIX.put("_door", "minecraft:oak_door");
        BY_SUFFIX.put("_leaves", "minecraft:oak_leaves");
        BY_SUFFIX.put("_sapling", "minecraft:oak_sapling");
        BY_SUFFIX.put("_carpet", "minecraft:white_carpet");
        BY_SUFFIX.put("_bookshelf", "minecraft:bookshelf");
        BY_SUFFIX.put("_ladder", "minecraft:ladder");
        BY_SUFFIX.put("_torch", "minecraft:torch");
        BY_SUFFIX.put("_lantern", "minecraft:lantern");
        BY_SUFFIX.put("_button", "minecraft:oak_button");
        BY_SUFFIX.put("_wall", "minecraft:cobblestone_wall");
        BY_SUFFIX.put("_bricks", "minecraft:bricks");
        BY_SUFFIX.put("_glass", "minecraft:glass");
        BY_SUFFIX.put("_log", "minecraft:oak_log");
        BY_SUFFIX.put("_wood", "minecraft:oak_wood");
        BY_SUFFIX.put("_sand", "minecraft:sand");
        BY_SUFFIX.put("_dirt", "minecraft:dirt");
        BY_SUFFIX.put("_stone", "minecraft:stone");
    }

    private BlockSubstitutions() {
    }

    /**
     * The vanilla block id to stand in for a foreign one.
     *
     * <p>Pure string work, with no registry involved, which is what makes the
     * whole policy unit-testable without a running game.
     *
     * @return a block id, or {@link #SKIP} (null) to leave the cell empty
     */
    public static String substituteFor(String foreignId) {
        String id = foreignId.toLowerCase(Locale.ROOT);
        if (NAMED.containsKey(id)) {
            return NAMED.get(id);
        }
        String path = id.substring(id.indexOf(':') + 1);
        for (Map.Entry<String, String> rule : BY_SUFFIX.entrySet()) {
            if (path.endsWith(rule.getKey())) {
                return rule.getValue();
            }
        }
        return DEFAULT;
    }

    /** Whether this id is one the policy names outright, rather than guesses at. */
    public static boolean isKnown(String foreignId) {
        return NAMED.containsKey(foreignId.toLowerCase(Locale.ROOT));
    }
}
