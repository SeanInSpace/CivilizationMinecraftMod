package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.block.StoreChestBlockEntity;
import com.kingdoms.neoforge.save.KingdomsSavedData;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the town's chests and the town's ledger telling the same story.
 *
 * <p>The ledger is the authority, and has to be: a settlement goes on producing
 * and building while nobody is near it, and an unloaded chunk has no chest to
 * read. So the chests are the ledger made handleable rather than a second set
 * of books — while the chunks are loaded this rewrites them to match, and
 * whatever a player did in between is read back out first.
 *
 * <p><strong>How a player's hand is told apart from the town's own
 * bookkeeping.</strong> The chests are rewritten every pass, so their contents
 * on their own say nothing. What matters is the difference between what is
 * there now and what this class last put there — which each chest remembers,
 * and persists, precisely so a reload cannot read the whole thing as a fresh
 * donation and credit the town twice.
 *
 * <p><strong>Every store is one pool.</strong> A town builds a storehouse and
 * later a warehouse, and both carry a container. Mirroring into only one of
 * them was a way to make timber out of nothing: which chest got picked could
 * change across a restart, and the abandoned one kept both its stock — free for
 * the taking — and its snapshot, which then billed the ledger for goods that
 * had never been in it. So all of them are read together and the total is laid
 * out in the first, with the rest cleared of anything the stores speak for.
 */
public final class StoreSync {

    /** How far from a building's origin its post may sit. */
    private static final int SEARCH_RADIUS = 4;
    private static final int SEARCH_HEIGHT = 4;

    /**
     * Where each settlement's store chests were found.
     *
     * <p>A building's recorded origin is the middle of its floor, but the post
     * carrying the chest is laid at an offset that rotation moves around, so
     * the positions have to be searched for rather than computed.
     */
    private static final Map<UUID, List<BlockPos>> FOUND = new HashMap<>();

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
        List<StoreChestBlockEntity> chests = chestsOf(level, settlement);
        if (chests.isEmpty()) {
            return;
        }
        if (readBackWhatChanged(settlement, chests)) {
            // The ledger lives in the saved data, which nothing else here marks.
            // The chests save with their chunk regardless, so without this a
            // crash between now and the next simulation step would restore a
            // ledger that never saw the withdrawal while the chest kept it.
            KingdomsSavedData.get(level).setDirty();
        }
        if (alreadyShowing(settlement, chests)) {
            return;   // identical to what is already on the shelves
        }
        writeLedgerInto(settlement, chests);
    }

    /**
     * Applies whatever a player added or took since the last pass.
     *
     * <p>Summed over every store the town has, because they are one pool: a
     * stack moved from the warehouse to the storehouse is not a withdrawal.
     * The subtraction itself lives in {@link StoreMirror}, where it can be
     * tested without a world.
     *
     * @return whether the ledger actually moved
     */
    private static boolean readBackWhatChanged(Settlement settlement,
                                               List<StoreChestBlockEntity> chests) {
        boolean moved = false;
        for (String resource : StoreChestBlockEntity.MIRRORED) {
            int held = 0;
            int snapshot = 0;
            for (StoreChestBlockEntity chest : chests) {
                held += countIn(chest, resource);
                snapshot += chest.lastSynced(resource);
            }
            if (held != snapshot) {
                StoreMirror.reconcile(settlement.stores(), resource, held, snapshot);
                moved = true;
            }
        }
        return moved;
    }

    /**
     * Whether the shelves already show exactly what the ledger holds.
     *
     * <p>Skipping the rewrite in the common case is not only cheaper — every
     * {@code setItem} marks the block entity dirty and walks its neighbours for
     * a comparator signal, so redrawing thirty identical stacks once a second
     * rewrote the warehouse chunk to disk on every autosave forever.
     *
     * <p>It also replaced a worse rule. The mirror used to hold still while a
     * player had a chest open, which was a way to mint items: builders spend
     * the ledger while the screen stands still, the player then drags out
     * stacks the town no longer owns, and the debit clamps at zero and swallows
     * the difference. A chest that updates under an open screen is a little
     * jarring; one that hands out timber from nothing is worse.
     */
    private static boolean alreadyShowing(Settlement settlement,
                                          List<StoreChestBlockEntity> chests) {
        for (String resource : StoreChestBlockEntity.MIRRORED) {
            int held = 0;
            for (StoreChestBlockEntity chest : chests) {
                held += countIn(chest, resource);
            }
            if (held != settlement.stores().get(resource)) {
                return false;
            }
        }
        return true;
    }

    /** Lays the ledger out in the first store, and clears the rest. */
    private static void writeLedgerInto(Settlement settlement,
                                        List<StoreChestBlockEntity> chests) {
        for (StoreChestBlockEntity chest : chests) {
            clearMirrored(chest);
        }
        StoreChestBlockEntity into = chests.getFirst();
        int slot = 0;
        for (String resource : StoreChestBlockEntity.MIRRORED) {
            Item item = itemFor(resource);
            if (item == null) {
                continue;   // nothing to pay this out in
            }
            int perStack = Math.min(item.getDefaultMaxStackSize(), Resources.stackSize(resource));
            int wanted = StoreMirror.showable(resource, settlement.stores().get(resource),
                    freeSlotsFrom(into, slot));
            int laid = 0;
            while (laid < wanted && slot < into.getContainerSize()) {
                if (!into.getItem(slot).isEmpty()) {
                    slot++;   // something the stores do not speak for; leave it be
                    continue;
                }
                int here = Math.min(perStack, wanted - laid);
                into.setItem(slot++, new ItemStack(item, here));
                laid += here;
            }
            // What fitted, not what the town owns. A store too small to show
            // everything must still measure withdrawals against what is in it.
            into.setLastSynced(resource, laid);
        }
        into.setChanged();
    }

    /** Empties one chest of everything the stores speak for, snapshot included. */
    private static void clearMirrored(StoreChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (StoreChestBlockEntity.speaksFor(chest.getItem(slot))) {
                chest.setItem(slot, ItemStack.EMPTY);
            }
        }
        for (String resource : StoreChestBlockEntity.MIRRORED) {
            chest.setLastSynced(resource, 0);
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

    /** Every store chest the settlement has standing and loaded. */
    private static List<StoreChestBlockEntity> chestsOf(ServerLevel level, Settlement settlement) {
        UUID id = settlement.id().value();
        List<BlockPos> remembered = FOUND.get(id);
        if (remembered != null) {
            List<StoreChestBlockEntity> found = resolve(level, remembered);
            if (found.size() == remembered.size()) {
                return found;
            }
            FOUND.remove(id);   // one was torn down, or its chunk went away
        }
        List<BlockPos> positions = search(level, settlement);
        if (positions.isEmpty()) {
            return List.of();
        }
        FOUND.put(id, positions);
        return resolve(level, positions);
    }

    /**
     * Reads chests back from remembered positions.
     *
     * <p>Guarded on {@code isLoaded}, because {@code Level.getBlockEntity} loads
     * or even generates the chunk rather than returning null for an absent one.
     * Unguarded, every town with a chest dragged its warehouse chunk off disk
     * once a second forever — in a mod whose whole premise is that unwatched
     * towns are not in the world at all.
     */
    private static List<StoreChestBlockEntity> resolve(ServerLevel level, List<BlockPos> positions) {
        List<StoreChestBlockEntity> chests = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            if (level.isLoaded(pos)
                    && level.getBlockEntity(pos) instanceof StoreChestBlockEntity chest) {
                chests.add(chest);
            }
        }
        return chests;
    }

    /** Hunts for the post of every store building the settlement has raised. */
    private static List<BlockPos> search(ServerLevel level, Settlement settlement) {
        List<BlockPos> found = new ArrayList<>();
        for (Building building : settlement.buildings()) {
            if (!building.isMaterialized() || !isStore(building)) {
                continue;
            }
            BlockPos centre = new BlockPos(building.origin().x(),
                    building.origin().y(), building.origin().z());
            if (!level.isLoaded(centre)) {
                continue;
            }
            BlockPos at = postNear(level, centre);
            if (at != null) {
                found.add(at);
            }
        }
        return found;
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

    private static boolean isStore(Building building) {
        return building.blueprintId().contains("warehouse")
                || building.blueprintId().contains("storehouse");
    }
}
