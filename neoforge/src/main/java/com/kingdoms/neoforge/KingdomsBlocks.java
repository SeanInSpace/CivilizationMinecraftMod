package com.kingdoms.neoforge;

import com.kingdoms.neoforge.block.LumberCampBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block registration. */
public final class KingdomsBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(KingdomsMod.MOD_ID);

    /** The lumber camp's control post; see {@link LumberCampBlock}. */
    public static final DeferredBlock<LumberCampBlock> LUMBER_CAMP = BLOCKS.registerBlock(
            "lumber_camp",
            LumberCampBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava());

    private KingdomsBlocks() {
    }
}
