package com.kingdoms.sim.settlement;

/**
 * The arithmetic behind keeping a container and the ledger in step.
 *
 * <p>It lives here, away from the container it serves, because it is the part
 * that can be got wrong quietly. Everything else about mirroring a chest is
 * moving item stacks around and is obvious when broken; this is the part where
 * a town silently gains or loses stock, and it is the part that can be tested
 * without a game.
 *
 * <p>The whole idea is one subtraction. A mirrored container is rewritten from
 * the ledger every pass, so what is in it says nothing on its own — only the
 * difference between what is in it <em>now</em> and what was last written
 * <em>there</em> distinguishes a player's hand from the town's own bookkeeping.
 */
public final class StoreMirror {

    private StoreMirror() {
    }

    /**
     * Folds whatever changed in the container back into the ledger.
     *
     * <p>Anything above the snapshot is a donation; anything below it, a
     * withdrawal. Taking is a partial spend rather than all-or-nothing on
     * purpose: the stock is already gone from the container, so refusing to
     * debit the ledger would be minting it.
     *
     * @param inChest    what the container holds right now
     * @param lastWritten what this mirror put there last time
     * @return the snapshot to remember, which is simply what is there now
     */
    public static int reconcile(TownStores stores, String resource,
                                int inChest, int lastWritten) {
        int change = inChest - Math.max(0, lastWritten);
        if (change > 0) {
            stores.add(resource, change);
        } else if (change < 0) {
            stores.takeUpTo(resource, -change);
        }
        return Math.max(0, inChest);
    }

    /**
     * How much of a holding a container with this much room can actually show.
     *
     * <p>A store may outgrow its shelves. When it does the mirror has to record
     * what <em>fitted</em> rather than what the town owns, or the next pass
     * reads the shortfall as though somebody had carried it off.
     */
    public static int showable(String resource, int held, int freeSlots) {
        if (held <= 0 || freeSlots <= 0) {
            return 0;
        }
        return Math.min(held, freeSlots * Resources.stackSize(resource));
    }
}
