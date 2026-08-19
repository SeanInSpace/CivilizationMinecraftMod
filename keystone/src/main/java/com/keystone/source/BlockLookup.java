package com.keystone.source;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/**
 * The block lookup a structure palette needs to turn names back into states.
 *
 * <p>Trivial, but every source needs it and the route is not obvious: the
 * registry access hanging off the level doubles as a {@code HolderGetter}.
 */
final class BlockLookup {

    private BlockLookup() {
    }

    static HolderGetter<Block> of(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(Registries.BLOCK);
    }
}
