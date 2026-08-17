package com.kingdoms.neoforge.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * Turns a blueprint id into actual blocks.
 *
 * <p>Two paths, checked in order:
 * <ol>
 *   <li><strong>A structure template</strong> — if a datapack provides
 *       {@code data/<ns>/structure/<path>.nbt} matching the blueprint id, it is
 *       placed verbatim. This is the route to hand-built, per-culture
 *       architecture: author a building in-game with structure blocks, drop the
 *       file in, and no code changes.</li>
 *   <li><strong>Procedural fallback</strong> — a small hand-coded structure per
 *       building type, so the mod reads as a village out of the box without any
 *       template authoring.</li>
 * </ol>
 *
 * <p>Every placement first lays a foundation (fill below) and clears headroom, so
 * buildings sit sanely on slopes.
 */
public final class BlueprintPlacer {

    private BlueprintPlacer() {
    }

    public static void place(ServerLevel level, String blueprintId, BlockPos base) {
        Identifier id = Identifier.parse(blueprintId);

        Optional<StructureTemplate> template = level.getStructureManager().get(id);
        if (template.isPresent()) {
            StructureTemplate t = template.get();
            prepareSite(level, base, t.getSize().getX(), t.getSize().getZ(), t.getSize().getY());
            // Seeded from position: repeat placements are identical.
            long seed = base.asLong();
            t.placeInWorld(level, base, base, new StructurePlaceSettings(), RandomSource.create(seed), 2);
            return;
        }

        switch (id.getPath()) {
            case "town_hall" -> hall(level, base);
            case "house" -> house(level, base);
            case "farm" -> farm(level, base);
            case "watchtower" -> watchtower(level, base);
            case "storehouse" -> storehouse(level, base);
            case "workshop" -> workshop(level, base);
            default -> marker(level, base);
        }
    }

    // --- procedural buildings ---

    private static void house(ServerLevel level, BlockPos base) {
        cabin(level, base, 5, 5, 3, Blocks.OAK_PLANKS, Blocks.OAK_LOG);
    }

    private static void hall(ServerLevel level, BlockPos base) {
        cabin(level, base, 7, 7, 4, Blocks.STONE_BRICKS, Blocks.SPRUCE_LOG);
        set(level, base.offset(0, 5, 0), Blocks.GOLD_BLOCK);   // something to aspire to
    }

    private static void storehouse(ServerLevel level, BlockPos base) {
        cabin(level, base, 5, 5, 3, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG);
        set(level, base.offset(-1, 1, -1), Blocks.BARREL);
        set(level, base.offset(1, 1, -1), Blocks.BARREL);
        set(level, base.offset(-1, 2, -1), Blocks.BARREL);
    }

    private static void workshop(ServerLevel level, BlockPos base) {
        cabin(level, base, 5, 5, 3, Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG);
        set(level, base.offset(-1, 1, -1), Blocks.CRAFTING_TABLE);
        set(level, base.offset(1, 1, -1), Blocks.SMITHING_TABLE);
        set(level, base.offset(0, 1, -1), Blocks.FURNACE);
    }

    /** Fenced field: tilled rows around a water channel, first crops already in. */
    private static void farm(ServerLevel level, BlockPos base) {
        int r = 3;
        prepareSite(level, base, 2 * r + 1, 2 * r + 1, 3);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                BlockPos ground = base.offset(dx, -1, dz);
                if (edge) {
                    set(level, ground, Blocks.GRASS_BLOCK);
                    set(level, base.offset(dx, 0, dz), Blocks.OAK_FENCE);
                } else if (dz == 0) {
                    set(level, ground, Blocks.WATER);
                } else {
                    set(level, ground, Blocks.FARMLAND);
                    set(level, base.offset(dx, 0, dz), Blocks.WHEAT);
                }
            }
        }
        set(level, base.offset(0, 0, r), Blocks.OAK_FENCE_GATE);
    }

    private static void watchtower(ServerLevel level, BlockPos base) {
        prepareSite(level, base, 3, 3, 9);
        for (int y = 0; y < 7; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean shell = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                    if (y == 0 || y == 6) {
                        set(level, base.offset(dx, y, dz), Blocks.COBBLESTONE);
                    } else if (shell && !(dz == 1 && dx == 0 && y <= 2)) {   // door gap south
                        set(level, base.offset(dx, y, dz), Blocks.COBBLESTONE);
                    }
                }
            }
        }
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                set(level, base.offset(dx, 7, dz), Blocks.COBBLESTONE_WALL);
            }
        }
        set(level, base.offset(0, 7, 0), Blocks.LANTERN);
    }

    /** The old placeholder, kept for unknown blueprint ids. */
    private static void marker(ServerLevel level, BlockPos base) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                set(level, base.offset(dx, 0, dz), Blocks.STONE_BRICKS);
            }
        }
        set(level, base.offset(0, 1, 0), Blocks.GOLD_BLOCK);
    }

    // --- shared construction ---

    /**
     * A rectangular building: plank floor, log corners, walled sides with a south
     * door gap and a window per wall, flat roof with a log rim, and a lantern.
     */
    private static void cabin(ServerLevel level, BlockPos base, int width, int depth, int wallHeight,
                              Block wall, Block frame) {
        int rx = width / 2;
        int rz = depth / 2;
        prepareSite(level, base, width, depth, wallHeight + 2);

        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                set(level, base.offset(dx, 0, dz), wall);                    // floor
                set(level, base.offset(dx, wallHeight + 1, dz), wall);       // roof
            }
        }
        for (int y = 1; y <= wallHeight; y++) {
            for (int dx = -rx; dx <= rx; dx++) {
                for (int dz = -rz; dz <= rz; dz++) {
                    boolean edgeX = Math.abs(dx) == rx;
                    boolean edgeZ = Math.abs(dz) == rz;
                    if (!edgeX && !edgeZ) {
                        continue;
                    }
                    BlockPos p = base.offset(dx, y, dz);
                    if (edgeX && edgeZ) {
                        set(level, p, frame);                                // corners
                    } else if (dz == rz && dx == 0 && y <= 2) {
                        // south door gap
                    } else if (y == 2 && (dx == 0 || dz == 0)) {
                        set(level, p, Blocks.GLASS);                         // windows
                    } else {
                        set(level, p, wall);
                    }
                }
            }
        }
        for (int dx = -rx; dx <= rx; dx++) {                                 // roof rim
            set(level, base.offset(dx, wallHeight + 1, -rz), frame);
            set(level, base.offset(dx, wallHeight + 1, rz), frame);
        }
        set(level, base.offset(0, 1, 0), Blocks.LANTERN);
    }

    /** Foundation below the footprint, clear air above it. Buildings sit sanely on slopes. */
    private static void prepareSite(ServerLevel level, BlockPos base, int width, int depth, int height) {
        int rx = width / 2;
        int rz = depth / 2;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos below = base.offset(dx, -dy, dz);
                    if (level.getBlockState(below).isAir() || !level.getFluidState(below).isEmpty()) {
                        set(level, below, Blocks.COBBLESTONE);
                    }
                }
                for (int dy = 0; dy <= height; dy++) {
                    set(level, base.offset(dx, dy, dz), Blocks.AIR);
                }
            }
        }
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlockAndUpdate(pos, block.defaultBlockState());
    }
}
