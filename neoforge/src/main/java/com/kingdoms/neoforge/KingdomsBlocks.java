package com.kingdoms.neoforge;

import com.kingdoms.neoforge.block.BuildingPostBlock;
import com.kingdoms.neoforge.block.LumberCampBlock;
import com.kingdoms.neoforge.block.MineBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

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


    /** Town Hall: the seat of the settlement, and the first thing it builds. */
    public static final DeferredBlock<BuildingPostBlock> TOWN_HALL = BLOCKS.registerBlock(
            "town_hall",
            properties -> new BuildingPostBlock("Town Hall", "the seat of the settlement, and the first thing it builds.", properties),
            KingdomsBlocks::postProperties);
    /** Dwelling: a family lives here; housing is what lets the town grow. */
    public static final DeferredBlock<BuildingPostBlock> HOUSE = BLOCKS.registerBlock(
            "house",
            properties -> new BuildingPostBlock("Dwelling", "a family lives here; housing is what lets the town grow.", properties),
            KingdomsBlocks::postProperties);
    /** Granary: harvest is carried here from the fields and kept. */
    public static final DeferredBlock<BuildingPostBlock> GRANARY = BLOCKS.registerBlock(
            "granary",
            properties -> new BuildingPostBlock("Granary", "harvest is carried here from the fields and kept.", properties),
            KingdomsBlocks::postProperties);
    /** Farm: wheat is grown here, the first link in the food chain. */
    public static final DeferredBlock<BuildingPostBlock> FARM = BLOCKS.registerBlock(
            "farm",
            properties -> new BuildingPostBlock("Farm", "wheat is grown here, the first link in the food chain.", properties),
            KingdomsBlocks::postProperties);
    /** Market: families shop here; stock is carried in from the granary. */
    public static final DeferredBlock<BuildingPostBlock> MARKET = BLOCKS.registerBlock(
            "market",
            properties -> new BuildingPostBlock("Market", "families shop here; stock is carried in from the granary.", properties),
            KingdomsBlocks::postProperties);
    /** Storehouse: the town's general stores, and where timber and stone pile up. */
    public static final DeferredBlock<BuildingPostBlock> STOREHOUSE = BLOCKS.registerBlock(
            "storehouse",
            properties -> new BuildingPostBlock("Storehouse", "the town's general stores, and where timber and stone pile up.", properties),
            KingdomsBlocks::postProperties);
    /** Workshop: craft work, and the reason a town wants a surplus at all. */
    public static final DeferredBlock<BuildingPostBlock> WORKSHOP = BLOCKS.registerBlock(
            "workshop",
            properties -> new BuildingPostBlock("Workshop", "craft work, and the reason a town wants a surplus at all.", properties),
            KingdomsBlocks::postProperties);
    /** Watchtower: the garrison's post; it adds to the town's defense. */
    public static final DeferredBlock<BuildingPostBlock> WATCHTOWER = BLOCKS.registerBlock(
            "watchtower",
            properties -> new BuildingPostBlock("Watchtower", "the garrison's post; it adds to the town's defense.", properties),
            KingdomsBlocks::postProperties);
    /** Stone Mine: stone is cut here, and a town that digs needs it. */
    public static final DeferredBlock<MineBlock> MINE = BLOCKS.registerBlock(
            "mine",
            properties -> new MineBlock("Stone Mine",
                    "stone is cut here, and a town that digs needs it.", properties),
            KingdomsBlocks::postProperties);

    /**
     * Shared footing for every post: sturdy enough not to be knocked out by
     * accident, cheap enough to break if you want the building gone.
     */
    private static BlockBehaviour.Properties postProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5F)
                .sound(SoundType.WOOD);
    }

    /** Every post the mod registers, in build order. */
    public static List<DeferredBlock<? extends BuildingPostBlock>> posts() {
        return List.of(TOWN_HALL, HOUSE, GRANARY, FARM, MARKET, STOREHOUSE, WORKSHOP, WATCHTOWER, MINE);
    }

    private KingdomsBlocks() {
    }
}
