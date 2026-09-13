package com.civilization.sim.work;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.settlement.TownStores;

/**
 * How a people light their streets.
 *
 * <p>An enum rather than a pair of block names, because {@code common} may not
 * name a block and should not want to: what a culture has is an <em>idiom</em> —
 * a lantern on a masoned post, a torch on a spruce one — and the blocks that
 * come to are the platform's business. {@code LightLayer} is the one place that
 * translation lives, the same way {@code PerimeterLayer} owns what a palisade
 * post is made of.
 *
 * <p>What is here instead is the part the simulation has to know: what a light
 * costs the town, and how tall it stands. Both are read by the clock and by the
 * builder, so both halves of the work charge the same price.
 *
 * <p><strong>There is no lights ledger.</strong> It was considered and rejected.
 * A town already counts timber and iron, a torch is a stick and a lump of coal,
 * and a lantern is iron nuggets round a torch — so a torch is charged as timber
 * and a lantern as iron, and no new column has to be added to every store, every
 * codec and every report to say the same thing less clearly. The coal is not
 * charged, and that is the one honest omission: nothing in this simulation has
 * ever counted coal, and inventing a ledger for it to price a torch would be the
 * tail wagging the dog.
 */
public enum LightStyle {

    /** Norman: a lantern on an oak fence post. */
    OAK_LANTERN(TownStores.WOOD, TownStores.IRON, 2),

    /**
     * Highland: a torch on a spruce post.
     *
     * <p>A post of spruce fence with a stripped log for its head, and the log is
     * not decoration. A torch will not stand on a fence — the top of a post is not
     * a face that supports one, every torch placed on one pops straight back off,
     * and {@code PerimeterLayer} spent an entire wall's drawing budget rediscovering
     * that. So a torch style gets something solid to sit on.
     */
    SPRUCE_TORCH(TownStores.WOOD, TownStores.WOOD, 2),

    /**
     * Burgher: a lantern on a stone-brick post.
     *
     * <p>It was to be hung under a chain, and it cannot be. A hanging lantern
     * attaches to the block <em>above</em> it and a chain has to hang from
     * something in its turn, so "a lamp under a chain" needs a ceiling — an arch,
     * an overhang, a bracket reaching out from a wall. A standard in the middle of
     * a street has none of those, and building it one would mean a block over the
     * carriageway held up by nothing. So the chain is gone and the stone is not:
     * what makes a burgher lamp a burgher lamp is that the town masons its street
     * furniture while everybody else nails theirs together.
     */
    STONE_LANTERN(TownStores.STONE, TownStores.IRON, 2),

    /**
     * Vale: a torch on an oak post. The plainest of them, and the cheapest.
     *
     * <p>Also what any people nobody has written a lamp for gets, because the one
     * outcome this work exists to prevent is an unlit town.
     */
    FENCE_TORCH(TownStores.WOOD, TownStores.WOOD, 2),

    /**
     * Orc warhost: a soul lantern on a spruce post.
     *
     * <p>The skull that was to go on top is not there, for the same reason the
     * burgher's chain is not: nothing in the game will hold a skull up on a lamp,
     * and a skull below the flame is just the post. The soul lantern is the mark
     * instead — a warhost lights its roads blue, which nobody else does, and it
     * costs the town exactly what an ordinary lantern costs.
     */
    SPRUCE_SOUL_LANTERN(TownStores.WOOD, TownStores.IRON, 2),

    /**
     * Goblin mire: a torch lashed to a stick.
     *
     * <p>The cheapest light a settlement can raise, which is the whole character
     * of it: a goblin street is lit because somebody jammed a burning stick in
     * the mud, not because anybody planned a lamp standard.
     */
    STICK_TORCH(TownStores.WOOD, TownStores.WOOD, 2);

    private final String post;
    private final String light;
    private final int height;

    LightStyle(String post, String light, int height) {
        this.post = post;
        this.light = light;
        this.height = height;
    }

    /** What the standard is made of — a plank, or a block of stone. */
    public String postResource() {
        return post;
    }

    /** What the lamp itself is made of — timber for a torch, iron for a lantern. */
    public String lightResource() {
        return light;
    }

    /** How far above the ground the flame sits, which is also how tall the post is. */
    public int height() {
        return height;
    }

    /** Whether this is a hung lantern rather than an open flame. */
    public boolean isLantern() {
        return TownStores.IRON.equals(light);
    }

    /**
     * The idiom this people light their streets in.
     *
     * <p>Keyed off {@link Culture#style()} and not off the whole id, for exactly
     * the reason the blueprint folder is: a datapack that writes
     * {@code civilization:human/burgher} gets burgher lamps without saying so
     * twice, and a culture nobody has written a style for falls through to the
     * plainest lamp there is rather than to no lamp at all. An unlit town is the
     * one outcome this whole work exists to prevent, so the default has to be a
     * light.
     */
    public static LightStyle of(String cultureStyle) {
        if (cultureStyle == null) {
            return FENCE_TORCH;
        }
        return switch (cultureStyle) {
            case "norman" -> OAK_LANTERN;
            case "highland" -> SPRUCE_TORCH;
            case "burgher" -> STONE_LANTERN;
            case "warhost" -> SPRUCE_SOUL_LANTERN;
            case "mire" -> STICK_TORCH;
            // "vale", "default", and anybody a datapack names that nobody has
            // drawn a lamp for.
            default -> FENCE_TORCH;
        };
    }

    /** The idiom this culture lights its streets in. */
    public static LightStyle of(Culture culture) {
        return culture == null ? FENCE_TORCH : of(culture.style());
    }
}
