package com.keystone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Item state that has to survive being dropped, stored and reloaded.
 *
 * <p>The wand remembers two corners between clicks, which is the whole of its
 * memory. Components rather than a side table keyed by player, so two people can
 * each hold a wand mid-selection without treading on each other.
 */
public final class KeystoneComponents {

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(
                    Registries.DATA_COMPONENT_TYPE, KeystoneMod.MOD_ID);

    public static final Supplier<DataComponentType<BlockPos>> CORNER_A =
            COMPONENTS.registerComponentType("corner_a", builder -> builder
                    .persistent(BlockPos.CODEC)
                    .networkSynchronized(BlockPos.STREAM_CODEC));

    public static final Supplier<DataComponentType<BlockPos>> CORNER_B =
            COMPONENTS.registerComponentType("corner_b", builder -> builder
                    .persistent(BlockPos.CODEC)
                    .networkSynchronized(BlockPos.STREAM_CODEC));

    private KeystoneComponents() {
    }
}
