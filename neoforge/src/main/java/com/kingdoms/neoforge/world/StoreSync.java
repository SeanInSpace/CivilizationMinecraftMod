package com.kingdoms.neoforge.world;

import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Resources;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.Stock;
import com.kingdoms.sim.settlement.StoreMirror;
import net.minecraft.server.level.ServerLevel;

/**
 * Keeps each store's shelves and that store's ledger telling the same story.
 *
 * <p>The ledger is the authority, and has to be: a settlement goes on producing
 * and building while nobody is near it, and an unloaded chunk has no chest to
 * read. So a chest is one building's goods made handleable rather than a second
 * set of books — while the chunk is loaded this rewrites it to match, and
 * whatever a player did in between is read back out first.
 *
 * <p><strong>One chest, one ledger.</strong> This used to reconcile every store
 * in the town against a single town-wide figure, which is what made two of them
 * a way to mint timber: both were entitled to show all of it, and whichever one
 * went unread kept its stock for the taking while its snapshot billed the town
 * for goods that were never in it. Now a building holds its own goods and its
 * own chest shows them, so the sum that used to have to be got right is not
 * computed here at all.
 *
 * <p><strong>How a player's hand is told apart from the town's own
 * bookkeeping.</strong> The chest is rewritten whenever it disagrees with its
 * building, so its contents on their own say nothing. What matters is the
 * difference between what is there now and what this class last put there —
 * which the shelves remember, and persist, precisely so a reload cannot read
 * the whole thing as a fresh donation and credit the town twice.
 *
 * <p>Everything here is slots and counts. Finding a chest in a world and
 * turning items into resources are {@link LevelStoreWorld}'s and
 * {@link ChestShelves}' business, which is what lets the reconciliation be
 * tested against shelves that are two arrays.
 */
public final class StoreSync {

    private StoreSync() {
    }

    /** Drops remembered chest positions. Wired to the server stopping. */
    public static void forget() {
        LevelStoreWorld.forget();
    }

    /** Convenience for callers holding a live level. */
    public static void reconcile(ServerLevel level, Settlement settlement) {
        reconcile(new LevelStoreWorld(level), settlement);
    }

    /** One reconciliation pass for one settlement. Cheap when there is no store. */
    public static void reconcile(StoreWorld world, Settlement settlement) {
        boolean moved = false;
        for (Building building : settlement.buildings()) {
            if (!building.isStore()) {
                continue;
            }
            Shelves shelves = world.shelvesOf(building);
            if (shelves == null) {
                continue;   // not built with one, or its chunk is away
            }
            Stock stores = building.stores();
            moved |= readBackWhatChanged(stores, shelves);
            if (!alreadyShowing(stores, shelves)) {
                writeLedgerInto(stores, shelves);
            }
        }
        if (moved) {
            world.ledgerChanged();
        }
    }

    /**
     * Applies whatever a player added or took since the last pass.
     *
     * <p>The subtraction itself lives in {@link StoreMirror}, where it can be
     * tested without any of this.
     *
     * @return whether the building's ledger actually moved
     */
    private static boolean readBackWhatChanged(Stock stores, Shelves shelves) {
        boolean moved = false;
        for (String resource : Resources.STORED) {
            int held = countIn(shelves, resource);
            int snapshot = shelves.lastSynced(resource);
            if (held != snapshot) {
                StoreMirror.reconcile(stores, resource, held, snapshot);
                moved = true;
            }
        }
        return moved;
    }

    /**
     * Whether the shelves already show exactly what this building holds.
     *
     * <p>Skipping the rewrite in the common case is not only cheaper — every
     * write marks the block entity dirty and walks its neighbors for a
     * comparator signal, so redrawing thirty identical stacks once a second
     * rewrote the chunk to disk on every autosave forever.
     *
     * <p>It also replaced a worse rule. The mirror used to hold still while a
     * player had the chest open, which was a way to mint items: builders spend
     * the ledger while the screen stands still, the player then drags out
     * stacks the town no longer owns, and the debit clamps at zero and swallows
     * the difference. A chest that updates under an open screen is a little
     * jarring; one that hands out timber from nothing is worse.
     */
    private static boolean alreadyShowing(Stock stores, Shelves shelves) {
        for (String resource : Resources.STORED) {
            if (countIn(shelves, resource) != stores.get(resource)) {
                return false;
            }
        }
        return true;
    }

    /** Lays one building's goods out on its own shelves. */
    private static void writeLedgerInto(Stock stores, Shelves shelves) {
        clearMirrored(shelves);
        int slot = 0;
        for (String resource : Resources.STORED) {
            int perStack = shelves.perSlot(resource);
            if (perStack <= 0) {
                continue;   // nothing to pay this out in
            }
            int wanted = StoreMirror.showable(resource, stores.get(resource),
                    freeSlotsFrom(shelves, slot));
            int laid = 0;
            while (laid < wanted && slot < shelves.slots()) {
                if (!shelves.isEmpty(slot)) {
                    slot++;   // something the stores do not speak for; leave it be
                    continue;
                }
                int here = Math.min(perStack, wanted - laid);
                shelves.lay(slot++, resource, here);
                laid += here;
            }
            // What fitted, not what the building holds. A store too small to
            // show everything must still measure withdrawals against what is
            // actually on the shelves.
            shelves.setLastSynced(resource, laid);
        }
        shelves.done();
    }

    /** Empties the shelves of everything the stores speak for, snapshot included. */
    private static void clearMirrored(Shelves shelves) {
        for (int slot = 0; slot < shelves.slots(); slot++) {
            if (shelves.resourceAt(slot) != null) {
                shelves.empty(slot);
            }
        }
        for (String resource : Resources.STORED) {
            shelves.setLastSynced(resource, 0);
        }
    }

    /** Empty slots from here to the end. */
    private static int freeSlotsFrom(Shelves shelves, int from) {
        int free = 0;
        for (int slot = from; slot < shelves.slots(); slot++) {
            if (shelves.isEmpty(slot)) {
                free++;
            }
        }
        return free;
    }

    private static int countIn(Shelves shelves, String resource) {
        int total = 0;
        for (int slot = 0; slot < shelves.slots(); slot++) {
            if (resource.equals(shelves.resourceAt(slot))) {
                total += shelves.amountAt(slot);
            }
        }
        return total;
    }
}
