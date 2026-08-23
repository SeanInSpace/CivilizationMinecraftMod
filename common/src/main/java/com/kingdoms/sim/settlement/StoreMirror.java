package com.kingdoms.sim.settlement;

import java.util.List;

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
     * <p>Both arguments are counts of real things and cannot be negative — the
     * container sums stack sizes, and the snapshot is clamped where it is read
     * back off disk. Guarding them again here would only suggest the invariant
     * is shakier than it is.
     *
     * @param inChest    what the container holds right now
     * @param lastWritten what this mirror put there last time
     * @return the snapshot to remember, which is simply what is there now
     */
    public static int reconcile(TownStores stores, String resource,
                                int inChest, int lastWritten) {
        int change = inChest - lastWritten;
        if (change > 0) {
            stores.add(resource, change);
        } else if (change < 0) {
            stores.takeUpTo(resource, -change);
        }
        return inChest;
    }

    /**
     * Whether a mirrored container speaks for this item.
     *
     * <p>Null is the whole reason this is a method rather than a
     * {@code contains} call at the call site. {@link Resources#resourceOf}
     * answers null for anything a town has no use for, and the mirrored list is
     * a {@code List.of}, whose {@code contains} throws on a null argument
     * rather than answering false. So a single diamond dropped into the store
     * took down the entire per-second simulation pass — paths, perimeter,
     * hunger, guards, every settlement after it — once a second, for as long
     * as the item sat there.
     *
     * <p>It is reachable by anyone: the container's own {@code canPlaceItem} is
     * consulted by hoppers but never by a player's clicks, because the chest
     * screen builds plain slots whose {@code mayPlace} is hardcoded true.
     *
     * @param itemId   a registry id, or null
     * @param mirrored the resources the container is the authority for
     */
    public static boolean mirrors(String itemId, List<String> mirrored) {
        String resource = Resources.resourceOf(itemId);
        return resource != null && mirrored.contains(resource);
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
