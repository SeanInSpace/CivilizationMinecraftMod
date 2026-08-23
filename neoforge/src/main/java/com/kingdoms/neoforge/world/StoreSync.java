package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.block.StoreChestBlockEntity;
import com.kingdoms.neoforge.view.PersonEntityManager;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Resources;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.StoreMirror;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the town's chest and the town's ledger telling the same story.
 *
 * <p>The ledger is the authority, and has to be: a settlement goes on producing
 * and building while nobody is near it, and an unloaded chunk has no chest to
 * read. So the chest is the ledger made handleable rather than a second set of
 * books — while the chunk is loaded this rewrites it to match, and whatever a
 * player did to it in between is read back out first.
 *
 * <p><strong>How a player's hand is told apart from the town's own
 * bookkeeping.</strong> The chest is rewritten every pass, so its contents on
 * their own say nothing. What matters is the difference between what is there
 * now and what this class last put there — which the chest remembers, and
 * persists, precisely so a reload cannot read the whole thing as a fresh
 * donation and credit the town twice.
 *
 * <p>That one mechanism covers the awkward cases for free. Stock earned while
 * the town was unloaded shows up as a ledger that outgrew its snapshot, and is
 * simply written out. A stack taken seconds before the chunk unloaded is still
 * a difference when it loads again. Overflow works too: the snapshot records
 * what actually fitted, not what the ledger holds, so a chest too small to show
 * everything still reports withdrawals honestly.
 */
public final class StoreSync {

    /** How far from a building's origin its post may sit. */
    private static final int SEARCH_RADIUS = 4;
    private static final int SEARCH_HEIGHT = 4;

    /**
     * Where each settlement's store chest was found.
     *
     * <p>A building's recorded origin is the middle of its floor, but the post
     * carrying the chest is laid at an offset that rotation moves around, so
     * the position has to be searched for rather than computed. Once is enough.
     */
    private static final Map<UUID, BlockPos> FOUND = new HashMap<>();

    private StoreSync() {
    }

    /**
     * Drops remembered chest positions.
     *
     * <p>Wired to the server stopping, beside the other static caches. This map
     * is keyed by settlement id alone, so without it a position found in one
     * world would be handed straight to the next session that happened to load
     * a settlement with the same id.
     */
    public static void forget() {
        FOUND.clear();
    }

    /** One reconciliation pass for one settlement. Cheap when there is no chest. */
    public static void reconcile(ServerLevel level, Settlement settlement) {
        StoreChestBlockEntity chest = chestOf(level, settlement);
        if (chest == null) {
            return;
        }
        readBackWhatChanged(settlement, chest);
        if (chest.isBeingWatched()) {
            // Rewriting slots under an open screen makes stacks jump about in
            // the player's hands. Their changes were already banked above,
            // snapshot included, so there is nothing left to do until they
            // close it and the chest can be redrawn.
            return;
        }
        writeLedgerInto(settlement, chest);
    }

    /**
     * Applies whatever a player added or took since the last pass.
     *
     * <p>The subtraction itself lives in {@link StoreMirror}, where it can be
     * tested without a world — it is the part of this class that can be wrong
     * quietly, and the part where a town silently gains or loses stock.
     */
    private static void readBackWhatChanged(Settlement settlement, StoreChestBlockEntity chest) {
        for (String resource : StoreChestBlockEntity.MIRRORED) {
            // The mirror hands back the snapshot to remember, so take it. The
            // watched path used to recompute the same number by scanning every
            // slot a second time, which was both wasted work and a second
            // expression of one fact that had to be kept agreeing by hand.
            int snapshot = StoreMirror.reconcile(settlement.stores(), resource,
                    countIn(chest, resource), chest.lastSynced(resource));
            chest.setLastSynced(resource, snapshot);
        }
    }

    /** Empties the mirrored slots and lays the ledger out in them. */
    private static void writeLedgerInto(Settlement settlement, StoreChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (StoreChestBlockEntity.speaksFor(chest.getItem(slot))) {
                chest.setItem(slot, ItemStack.EMPTY);
            }
        }
        int slot = 0;
        for (String resource : StoreChestBlockEntity.MIRRORED) {
            Item item = itemFor(resource);
            if (item == null) {
                chest.setLastSynced(resource, 0);
                continue;   // nothing to pay this out in
            }
            int perStack = Math.min(item.getDefaultMaxStackSize(), Resources.stackSize(resource));
            // How much of the holding these shelves can actually show. Asking
            // StoreMirror rather than working it out again here is what keeps
            // the overflow rule the tests cover and the one that runs the same
            // rule.
            int wanted = StoreMirror.showable(resource, settlement.stores().get(resource),
                    freeSlotsFrom(chest, slot));
            int laid = 0;
            while (laid < wanted && slot < chest.getContainerSize()) {
                if (!chest.getItem(slot).isEmpty()) {
                    slot++;   // something the chest does not speak for; leave it be
                    continue;
                }
                int here = Math.min(perStack, wanted - laid);
                chest.setItem(slot++, new ItemStack(item, here));
                laid += here;
            }
            // What fitted, not what the town owns. A chest too small to show
            // everything must still measure withdrawals against what is in it.
            chest.setLastSynced(resource, laid);
        }
        chest.setChanged();
    }

    /** Empty slots from here to the end of the chest. */
    private static int freeSlotsFrom(StoreChestBlockEntity chest, int from) {
        int free = 0;
        for (int slot = from; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    private static int countIn(StoreChestBlockEntity chest, String resource) {
        int total = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty()
                    && resource.equals(Resources.resourceOf(StoreChestBlockEntity.idOf(stack)))) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static Item itemFor(String resource) {
        String id = Resources.itemFor(resource);
        return id == null ? null
                : BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(null);
    }

    /** The settlement's store chest, or null if it has none standing and loaded. */
    private static StoreChestBlockEntity chestOf(ServerLevel level, Settlement settlement) {
        UUID id = settlement.id().value();
        BlockPos remembered = FOUND.get(id);
        if (remembered != null) {
            if (level.getBlockEntity(remembered) instanceof StoreChestBlockEntity chest) {
                return chest;
            }
            FOUND.remove(id);   // torn down, or the chunk went away
        }
        if (!hasStoreBuilding(settlement)) {
            return null;   // nothing to search for; do not walk the world for it
        }
        SimPos origin = PersonEntityManager.storesPos(settlement);
        BlockPos centre = new BlockPos(origin.x(), origin.y(), origin.z());
        if (!level.isLoaded(centre)) {
            return null;
        }
        for (int dy = 0; dy <= SEARCH_HEIGHT; dy++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos at = centre.offset(dx, dy, dz);
                    if (level.getBlockEntity(at) instanceof StoreChestBlockEntity chest) {
                        FOUND.put(id, at);
                        return chest;
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasStoreBuilding(Settlement settlement) {
        for (Building building : settlement.buildings()) {
            if (building.isMaterialized()
                    && (building.blueprintId().contains("warehouse")
                            || building.blueprintId().contains("storehouse"))) {
                return true;
            }
        }
        return false;
    }
}
