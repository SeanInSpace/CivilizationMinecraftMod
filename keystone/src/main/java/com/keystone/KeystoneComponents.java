package com.keystone;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
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

    /** The blueprint this wand is lining up to place, if it is in placing mode. */
    public static final Supplier<DataComponentType<Identifier>> SELECTED =
            COMPONENTS.registerComponentType("selected", builder -> builder
                    .persistent(Identifier.CODEC)
                    .networkSynchronized(Identifier.STREAM_CODEC));

    /** Quarter turns clockwise, 0-3. Stored as an int so the codec stays trivial. */
    public static final Supplier<DataComponentType<Integer>> ROTATION =
            COMPONENTS.registerComponentType("rotation", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.INT));

    private KeystoneComponents() {
    }

    public static Rotation rotationOf(Integer quarters) {
        return quarters == null ? Rotation.NONE
                : Rotation.values()[Math.floorMod(quarters, Rotation.values().length)];
    }
}
