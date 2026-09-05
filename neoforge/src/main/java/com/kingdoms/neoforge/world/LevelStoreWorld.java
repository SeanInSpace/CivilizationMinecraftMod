package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.block.StoreChestBlockEntity;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.sim.settlement.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Finds a town's chests in a real world.
 *
 * <p>All the awkwardness of doing that lives here rather than in the mirror: a
 * building's recorded origin is the middle of its floor, but the post carrying
 * the chest sits at an offset that rotation moves around, so the position has
 * to be hunted for and then remembered.
 */
public record LevelStoreWorld(ServerLevel level) implements StoreWorld {

    /** How far from a building's origin its post may sit. */
    private static final int SEARCH_RADIUS = 4;
    private static final int SEARCH_HEIGHT = 4;

    /**
     * Where each store building's chest was found, per level.
     *
     * <p>Keyed by level so two dimensions cannot hand each other a position at
     * the same coordinates, and weakly so an unloaded world does not keep its
     * own map alive. {@link #forget} still runs on server stop, because a
     * position found in one session must never be handed to the next.
     */
    private static final Map<ServerLevel, Map<BlockPos, BlockPos>> FOUND = new WeakHashMap<>();

    /** Drops every remembered chest position. Wired to the server stopping. */
    public static void forget() {
        FOUND.clear();
    }

    @Override
    public Shelves shelvesOf(Building building) {
        StoreChestBlockEntity chest = chestOf(building);
        return chest == null ? null : new ChestShelves(chest);
    }

    @Override
    public void ledgerChanged() {
        KingdomsSavedData.get(level).setDirty();
    }

    private StoreChestBlockEntity chestOf(Building building) {
        BlockPos center = new BlockPos(building.origin().x(),
                building.origin().y(), building.origin().z());
        if (!level.isLoaded(center)) {
            return null;
        }
        Map<BlockPos, BlockPos> known = FOUND.computeIfAbsent(level, l -> new HashMap<>());
        BlockPos remembered = known.get(center);
        if (remembered != null) {
            if (level.isLoaded(remembered)
                    && level.getBlockEntity(remembered) instanceof StoreChestBlockEntity chest) {
                return chest;
            }
            known.remove(center);   // torn down, or moved
        }
        BlockPos at = postNear(center);
        if (at == null) {
            return null;
        }
        known.put(center, at);
        return (StoreChestBlockEntity) level.getBlockEntity(at);
    }

    /**
     * Hunts for the post near a building's middle.
     *
     * <p>Guarded on {@code isLoaded} throughout, because
     * {@code Level.getBlockEntity} loads or even generates the chunk rather
     * than returning null for an absent one. Unguarded, every town with a store
     * dragged its chunk off disk once a second forever — in a mod whose whole
     * premise is that unwatched towns are not in the world at all.
     */
    private BlockPos postNear(BlockPos center) {
        for (int dy = 0; dy <= SEARCH_HEIGHT; dy++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos at = center.offset(dx, dy, dz);
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
