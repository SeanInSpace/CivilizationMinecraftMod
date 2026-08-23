package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.block.StoreChestBlockEntity;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Resources;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.Stock;
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
 * Keeps each store's chest and that store's ledger telling the same story.
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
 * which the chest remembers, and persists, precisely so a reload cannot read
 * the whole thing as a fresh donation and credit the town twice.
 */
public final class StoreSync {

    /** How far from a building's origin its post may sit. */
    private static final int SEARCH_RADIUS = 4;
    private static final int SEARCH_HEIGHT = 4;

    /**
     * Where each store building's chest was found, by settlement and origin.
     *
     * <p>A building's recorded origin is the middle of its floor, but the post
     * carrying the chest is laid at an offset that rotation moves around, so
     * the position has to be searched for rather than computed.
     */
    private static final Map<UUID, Map<BlockPos, BlockPos>> FOUND = new HashMap<>();

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

    /** One reconciliation pass for one settlement. Cheap when there is no store. */
    public static void reconcile(ServerLevel level, Settlement settlement) {
        boolean moved = false;
        for (Building building : settlement.buildings()) {
            if (!building.isStore()) {
                continue;
            }
            StoreChestBlockEntity chest = chestOf(level, settlement, building);
            if (chest == null) {
                continue;   // not built with one, or its chunk is away
            }
            Stock stores = building.stores();
            moved |= readBackWhatChanged(stores, chest);
            if (!alreadyShowing(stores, chest)) {
                writeLedgerInto(stores, chest);
            }
        }
        if (moved) {
            // The ledgers live in the saved data, which nothing else here marks.
            // The chests save with their chunk regardless, so without this a
            // crash between now and the next simulation step would restore a
            // building that never saw a withdrawal its chest remembered.
            KingdomsSavedData.get(level).setDirty();
        }
    }

    /**
     * Applies whatever a player added or took since the last pass.
     *
     * <p>The subtraction itself lives in {@link StoreMirror}, where it can be
     * tested without a world.
     *
     * @return whether the building's ledger actually moved
     */
    private static boolean readBackWhatChanged(Stock stores, StoreChestBlockEntity chest) {
        boolean moved = false;
        for (String resource : Resources.STORED) {
            int held = countIn(chest, resource);
            int snapshot = chest.lastSynced(resource);
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
     * {@code setItem} marks the block entity dirty and walks its neighbours for
     * a comparator signal, so redrawing thirty identical stacks once a second
     * rewrote the chunk to disk on every autosave forever.
     *
     * <p>It also replaced a worse rule. The mirror used to hold still while a
     * player had the chest open, which was a way to mint items: builders spend
     * the ledger while the screen stands still, the player then drags out
     * stacks the town no longer owns, and the debit clamps at zero and swallows
     * the difference. A chest that updates under an open screen is a little
     * jarring; one that hands out timber from nothing is worse.
     */
    private static boolean alreadyShowing(Stock stores, StoreChestBlockEntity chest) {
        for (String resource : Resources.STORED) {
            if (countIn(chest, resource) != stores.get(resource)) {
                return false;
            }
        }
        return true;
    }

    /** Lays one building's goods out on its own shelves. */
    private static void writeLedgerInto(Stock stores, StoreChestBlockEntity chest) {
        clearMirrored(chest);
        int slot = 0;
        for (String resource : Resources.STORED) {
            Item item = itemFor(resource);
            if (item == null) {
                continue;   // nothing to pay this out in
            }
            int perStack = Math.min(item.getDefaultMaxStackSize(), Resources.stackSize(resource));
            int wanted = StoreMirror.showable(resource, stores.get(resource),
                    freeSlotsFrom(chest, slot));
            int laid = 0;
            while (laid < wanted && slot < chest.getContainerSize()) {
                if (!chest.getItem(slot).isEmpty()) {
                    slot++;   // something the stores do not speak for; leave it be
                    continue;
                }
                int here = Math.min(perStack, wanted - laid);
                chest.setItem(slot++, new ItemStack(item, here));
                laid += here;
            }
            // What fitted, not what the building holds. A store too small to
            // show everything must still measure withdrawals against what is
            // actually on the shelves.
            chest.setLastSynced(resource, laid);
        }
        chest.setChanged();
    }

    /** Empties a chest of everything the stores speak for, snapshot included. */
    private static void clearMirrored(StoreChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (StoreChestBlockEntity.speaksFor(chest.getItem(slot))) {
                chest.setItem(slot, ItemStack.EMPTY);
            }
        }
        for (String resource : Resources.STORED) {
            chest.setLastSynced(resource, 0);
        }
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

    /**
     * The chest belonging to one store building, if it is standing and loaded.
     *
     * <p>Guarded on {@code isLoaded}, because {@code Level.getBlockEntity} loads
     * or even generates the chunk rather than returning null for an absent one.
     * Unguarded, every town with a store dragged its chunk off disk once a
     * second forever — in a mod whose whole premise is that unwatched towns are
     * not in the world at all.
     */
    private static StoreChestBlockEntity chestOf(ServerLevel level, Settlement settlement,
                                                 Building building) {
        BlockPos centre = new BlockPos(building.origin().x(),
                building.origin().y(), building.origin().z());
        if (!level.isLoaded(centre)) {
            return null;
        }
        Map<BlockPos, BlockPos> known = FOUND.computeIfAbsent(settlement.id().value(),
                id -> new HashMap<>());
        BlockPos remembered = known.get(centre);
        if (remembered != null) {
            if (level.isLoaded(remembered)
                    && level.getBlockEntity(remembered) instanceof StoreChestBlockEntity chest) {
                return chest;
            }
            known.remove(centre);   // torn down, or moved
        }
        BlockPos at = postNear(level, centre);
        if (at == null) {
            return null;
        }
        known.put(centre, at);
        return (StoreChestBlockEntity) level.getBlockEntity(at);
    }

    private static BlockPos postNear(ServerLevel level, BlockPos centre) {
        for (int dy = 0; dy <= SEARCH_HEIGHT; dy++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos at = centre.offset(dx, dy, dz);
                    if (level.isLoaded(at)
                            && level.getBlockEntity(at) instanceof StoreChestBlockEntity) {
                        return at;
                    }
                }
            }
        }
        return null;
    }
}
