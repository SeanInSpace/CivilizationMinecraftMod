package com.kingdoms.sim.person;

import com.kingdoms.sim.settlement.Stock;

/**
 * What a builder has in hand, and the rule that a block has to come out of it.
 *
 * <p>Watched building is paid for in carried loads rather than in ledger
 * entries: a builder lays a block because they are holding one, not because the
 * town owns one somewhere across the village. Both halves of that live here so
 * the rule can be stated once and tested without a world — the view layer
 * supplies the walking and the swinging and asks these two questions.
 *
 * <p>Only the watched path uses this. An unwatched town has no hands to fill,
 * and requiring a carried load of a settlement nobody is looking at would be
 * meaningless; it goes on paying per work unit out of the pooled ledger. See
 * {@code Settlement.payForProgress}.
 */
public final class BuildLoad {

    private BuildLoad() {
    }

    /**
     * How much a builder shoulders in one trip to the stores.
     *
     * <p>Sixteen is a good fraction of a wall: enough that a builder is not
     * walking back to the warehouse every other block, and short enough that an
     * empty store is felt within seconds rather than after a whole course has
     * gone up out of one armful.
     */
    public static final int LOAD_SIZE = 16;

    /**
     * Whether this builder may lay the step in front of them.
     *
     * <p>{@code owed} is null when the step costs nothing at all — a course of
     * something the town does not make, or one of the bootstrap producers, which
     * are exempt from materials everywhere else too. A step nobody is charged
     * for is a step nobody has to be holding, or a watched town out of stone
     * could never raise the mine that fixes it.
     *
     * <p>Everything else has to be in hand. Owning it somewhere is not enough:
     * that was the whole complaint, that the carried load was decorative and the
     * block came out of the town's books whatever the builder was holding.
     */
    public static boolean canLay(String owed, Person carrier) {
        return owed == null || (carrier != null && carrier.carries(owed));
    }

    /**
     * Loads a builder up at the shelves they are standing at.
     *
     * <p>Anything they were still carrying goes back on those shelves, rather
     * than being overwritten — which is what {@code setCarry} on its own does,
     * and it quietly destroyed the remainder every time a course changed
     * material. On an ordinary house that is every few blocks.
     *
     * <p>Only once there is something to swap it for. Shelves that turn out to
     * be bare leave a builder holding what they already had: tipping a good load
     * of timber out at an empty stone store, on the strength of a trip that
     * failed, would be the same waste by a longer route.
     *
     * <p>The stock leaves the ledger here, at pickup, and not again at the wall.
     * That is what makes a load in transit genuinely out of the stores, and it
     * is the reason the laying path must never charge for a carried block.
     *
     * @return how much was drawn, zero if the shelves had nothing
     */
    public static int pickUp(Stock from, Person carrier, String material) {
        if (carrier == null) {
            return 0;
        }
        int drawn = from.takeUpTo(material, LOAD_SIZE);
        if (drawn <= 0) {
            return 0;
        }
        putBack(from, carrier);
        carrier.setCarry(material, drawn);
        return drawn;
    }

    /**
     * Returns an unspent load to a holder, and leaves the hands empty.
     *
     * <p>Which holder is the caller's business, and the two callers mean
     * different things by it: the shelves a builder has walked to, which is the
     * honest destination because they carried the load there on foot; and the
     * town's own pool, when a builder is released and there is no longer anybody
     * to carry anything anywhere. Nothing is created either way — the stock came
     * out of the same town it is going back into.
     */
    public static void putBack(Stock to, Person carrier) {
        if (carrier == null) {
            return;
        }
        String held = carrier.carriedMaterial();
        if (held == null || carrier.carriedLoad() <= 0) {
            return;
        }
        to.add(held, carrier.carriedLoad());
        carrier.setCarry(null, 0);
    }
}
