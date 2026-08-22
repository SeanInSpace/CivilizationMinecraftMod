package com.kingdoms.neoforge;

import com.kingdoms.neoforge.block.BuildingPostBlock;
import com.kingdoms.neoforge.block.CampPostBlock;
import com.kingdoms.neoforge.block.StorehouseBlock;
import com.kingdoms.neoforge.block.LumberCampBlock;
import com.kingdoms.neoforge.block.MarketBlock;
import com.kingdoms.neoforge.block.TownHallBlock;
import com.kingdoms.neoforge.block.WarehouseBlock;
import com.kingdoms.neoforge.block.MineBlock;
import com.kingdoms.neoforge.block.QuestBoardBlock;
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
    public static final DeferredBlock<TownHallBlock> TOWN_HALL = BLOCKS.registerBlock(
            "town_hall",
            properties -> new TownHallBlock("Town Hall",
                    "the seat of the settlement, and the first thing it builds.", properties),
            KingdomsBlocks::postProperties);
    /** Cottage: a family home; where the next generation comes from. */
    public static final DeferredBlock<BuildingPostBlock> COTTAGE = BLOCKS.registerBlock(
            "cottage",
            properties -> new BuildingPostBlock("Cottage", "a family's own home — households grow here, as they never could in the bunks.", properties),
            KingdomsBlocks::postProperties);
    /** Mill: grinding the harvest gets more bread out of the grain. */
    public static final DeferredBlock<BuildingPostBlock> MILL = BLOCKS.registerBlock(
            "mill",
            properties -> new BuildingPostBlock("Mill", "the harvest is ground here; a working mill gets more bread from the same grain.", properties),
            KingdomsBlocks::postProperties);
    /** Carpentry: pre-cut components that speed every build crew. */
    public static final DeferredBlock<BuildingPostBlock> CARPENTRY = BLOCKS.registerBlock(
            "carpentry",
            properties -> new BuildingPostBlock("Carpentry", "components are cut here ahead of need; every site raises faster for it.", properties),
            KingdomsBlocks::postProperties);
    /** Inn: where the caravans call. */
    public static final DeferredBlock<BuildingPostBlock> INN = BLOCKS.registerBlock(
            "inn",
            properties -> new BuildingPostBlock("Inn", "beds for travellers and a yard for the caravans that trade the town's surplus.", properties),
            KingdomsBlocks::postProperties);
    /** Camp Post: the staked claim; see {@link CampPostBlock}. */
    public static final DeferredBlock<CampPostBlock> CAMP_POST = BLOCKS.registerBlock(
            "camp_post",
            CampPostBlock::new,
            KingdomsBlocks::postProperties);
    /** Supply Cache: the party's pooled provisions and tools, out in the open. */
    public static final DeferredBlock<BuildingPostBlock> CACHE = BLOCKS.registerBlock(
            "cache",
            properties -> new BuildingPostBlock("Supply Cache", "the party's pooled provisions and tools, out in the open.", properties),
            KingdomsBlocks::postProperties);
    /** Bunkhouse: one room the whole party sleeps in, until families raise cottages. */
    public static final DeferredBlock<BuildingPostBlock> BUNKHOUSE = BLOCKS.registerBlock(
            "bunkhouse",
            properties -> new BuildingPostBlock("Bunkhouse", "one room the whole party sleeps in, until families raise cottages.", properties),
            KingdomsBlocks::postProperties);
    /** Hearth: the open fire the camp cooks on and gathers around. */
    public static final DeferredBlock<BuildingPostBlock> HEARTH = BLOCKS.registerBlock(
            "hearth",
            properties -> new BuildingPostBlock("Hearth", "the open fire the camp cooks on and gathers around.", properties),
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
    public static final DeferredBlock<MarketBlock> MARKET = BLOCKS.registerBlock(
            "market",
            properties -> new MarketBlock("Market",
                    "families shop here; stock is carried in from the granary.", properties),
            KingdomsBlocks::postProperties);
    /** Storehouse: the town's general stores, and where timber and stone pile up. */
    public static final DeferredBlock<StorehouseBlock> STOREHOUSE = BLOCKS.registerBlock(
            "storehouse",
            properties -> new StorehouseBlock("Storehouse", "the town's stores — donations taken, timber sold; see the post itself.", properties),
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

    /** Warehouse: the town's stores; every resource it owns is counted here. */
    public static final DeferredBlock<WarehouseBlock> WAREHOUSE = BLOCKS.registerBlock(
            "warehouse",
            properties -> new WarehouseBlock("Warehouse",
                    "the town's stores; every resource it owns is counted here.", properties),
            KingdomsBlocks::postProperties);
    /** Smithy: tools, weapons and armour are made here and issued to the town. */
    public static final DeferredBlock<BuildingPostBlock> SMITH = BLOCKS.registerBlock(
            "smith",
            properties -> new BuildingPostBlock("Smithy", "tools, weapons and armour are made here and issued to the town.", properties),
            KingdomsBlocks::postProperties);
    /** Animal Farm: livestock is kept here, a pen to each kind. */
    public static final DeferredBlock<BuildingPostBlock> ANIMAL_FARM = BLOCKS.registerBlock(
            "animal_farm",
            properties -> new BuildingPostBlock("Animal Farm", "livestock is kept here, a pen to each kind.", properties),
            KingdomsBlocks::postProperties);
    /** Quest Board: what the town is asking for, and what it has seen done. */
    public static final DeferredBlock<QuestBoardBlock> QUEST_BOARD = BLOCKS.registerBlock(
            "quest_board",
            properties -> new QuestBoardBlock("Quest Board", "what the town is asking for, and what it has seen done.", properties),
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
        return List.of(WAREHOUSE, SMITH, ANIMAL_FARM, QUEST_BOARD, TOWN_HALL, HOUSE, GRANARY, FARM, MARKET, STOREHOUSE, WORKSHOP, WATCHTOWER, MINE, CAMP_POST, CACHE, BUNKHOUSE, HEARTH, COTTAGE, MILL, CARPENTRY, INN);
    }

    private KingdomsBlocks() {
    }
}
