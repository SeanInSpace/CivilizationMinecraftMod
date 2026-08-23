package com.kingdoms.neoforge;

import com.kingdoms.neoforge.block.StoreChestBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block entities. Until the town's stores became real items there were none at
 * all — every building in the mod was a plain block with a number behind it.
 */
public final class KingdomsBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, KingdomsMod.MOD_ID);

    /**
     * The container behind the buildings that hold the town's stock.
     *
     * <p>Attached to the warehouse and the storehouse because those are what
     * {@code PersonEntityManager.storesPos} already resolves to when a builder
     * goes for materials — the address the town has always used for its stores,
     * now with something actually in it.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StoreChestBlockEntity>>
            STORE_CHEST = BLOCK_ENTITIES.register("store_chest",
                    () -> new BlockEntityType<>(
                            StoreChestBlockEntity::new,
                            KingdomsBlocks.WAREHOUSE.get(),
                            KingdomsBlocks.STOREHOUSE.get()));

    private KingdomsBlockEntities() {
    }
}
