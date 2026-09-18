package com.civilization.sim.work;

import com.civilization.sim.culture.Culture;

/**
 * How a people dress the ground between their buildings.
 *
 * <p>{@link LightStyle}'s sibling, and written for the same reason: {@code common}
 * may not name a block, so what lives here is the <em>idiom</em> — a fenced kitchen
 * garden, a hedge of leaves, a paved square — and {@code FurnishingLayer} is the
 * one place that idiom becomes blocks.
 *
 * <p>What the simulation actually needs out of it is one question:
 * {@link #raises}, which says whether this people put up that kind of thing at
 * all. It is not decoration. A goblin camp with a market square and an avenue of
 * saplings is not a goblin camp, and — more to the point for the books — a work
 * that planned pieces nobody would ever raise would have a town paying for a
 * dressing plan it does not want and counting its way through stations that draw
 * nothing.
 */
public enum FurnishingStyle {

    /**
     * Norman: fenced kitchen gardens, clipped hedges, a paved square with a well
     * in it. The full vocabulary, and the one everything else is measured against.
     */
    NORMAN,

    /**
     * Highland: the same garden and the same square, and no hedges.
     *
     * <p>A hedge is a thing you plant where a thing you plant will grow into a
     * wall. These are people who build in stone on a hillside because the wind
     * takes everything else, and their boundary is a drystone course or nothing.
     */
    HIGHLAND,

    /**
     * Burgher: the whole vocabulary, masoned. Their square is the biggest thing
     * in the town after the hall, which is exactly what a people who lay a plan
     * before they build on it would do with the middle of it.
     */
    BURGHER,

    /** Vale: everything, and the orchards are theirs above anybody's. */
    VALE,

    /**
     * Orc warhost: log piles and stakes, and nothing that could be called a
     * garden.
     *
     * <p>They get crates too, because a war camp still has to put its plunder
     * somewhere, and a fire pit, because a camp has a fire. What they do not get
     * is anything that implies somebody intends to still be here next spring.
     */
    WARHOST,

    /**
     * Goblin mire: woodpiles, a fire pit, cages, and heaps of stolen crates.
     *
     * <p>The brief's own list, and the thing it leaves out is the point of it:
     * no yard, no hedge, no orchard, no square, no signpost. A goblin camp is
     * dressed with what was dragged back to it.
     */
    MIRE;

    /**
     * Whether this people raise this kind of thing.
     *
     * <p>Stated as a table rather than as a flag per piece, because the
     * interesting fact about a culture's dressing is the <em>shape of the whole
     * list</em> — that the goblins have four entries and the burghers have all of
     * them — and a reader should be able to see that in one place.
     */
    public boolean raises(Furnishings.Piece piece) {
        // The one thing nobody is exempt from. A warhost plants nothing it will
        // not be here to pick and a goblin camp is dressed with what was dragged
        // back to it — and both of them still put a stone up for their dead,
        // because burying somebody is not decoration and is not a claim about
        // next spring. A people who left their dead where they fell would be a
        // statement about them that this mod has not earned.
        if (piece == Furnishings.Piece.GRAVE) {
            return true;
        }
        return switch (this) {
            case NORMAN, BURGHER, VALE -> piece != Furnishings.Piece.CAGE
                    && piece != Furnishings.Piece.STAKES;
            case HIGHLAND -> piece != Furnishings.Piece.CAGE
                    && piece != Furnishings.Piece.STAKES
                    && piece != Furnishings.Piece.HEDGE;
            case WARHOST -> switch (piece) {
                case WOODPILE, STAKES, CRATES, FIRE_PIT -> true;
                default -> false;
            };
            case MIRE -> switch (piece) {
                case WOODPILE, CAGE, CRATES, FIRE_PIT -> true;
                default -> false;
            };
        };
    }

    /**
     * The idiom this people dress their ground in.
     *
     * <p>Keyed off {@link Culture#style()} on exactly {@link LightStyle#of}'s
     * terms: a datapack culture nobody has drawn a dressing for falls through to
     * the plainest one there is rather than to no dressing at all. An undressed
     * town is the outcome this whole work exists to prevent, so the default has
     * to be a people who dress.
     */
    public static FurnishingStyle of(String cultureStyle) {
        if (cultureStyle == null) {
            return NORMAN;
        }
        return switch (cultureStyle) {
            case "highland" -> HIGHLAND;
            case "burgher" -> BURGHER;
            case "vale" -> VALE;
            case "warhost" -> WARHOST;
            case "mire" -> MIRE;
            // "norman", "default", and anybody a datapack names.
            default -> NORMAN;
        };
    }

    /** The idiom this culture dresses its ground in. */
    public static FurnishingStyle of(Culture culture) {
        return culture == null ? NORMAN : of(culture.style());
    }
}
