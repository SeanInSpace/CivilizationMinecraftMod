package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.net.SurveyPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The box somebody is about to scan, while they are still deciding.
 *
 * <p>Laying out a building to be scanned is the one part of authoring that is
 * done entirely by eye, and until now it was done entirely by arithmetic: type
 * two coordinates, scan, load the file, read the size back, discover it caught a
 * block of the hillside, try again. The whole difficulty is that a region has no
 * appearance — so it is given one. Set it, hold up the surveyor's lamp, and the
 * box is drawn round it in the same lines a building plot is, including the tick
 * that says which way the front is.
 *
 * <p>Kept in memory and per player, never saved. A scan region is a thought
 * somebody is having, not a fact about the world; carrying one across a restart
 * would mean a stale box appearing round a building that was finished three
 * sessions ago.
 */
public final class ScanRegion {

    private ScanRegion() {
    }

    /** One pending region: two corners and which way its front points. */
    public record Region(BlockPos from, BlockPos to, int facing) {

        public BlockPos min() {
            return new BlockPos(
                    Math.min(from.getX(), to.getX()),
                    Math.min(from.getY(), to.getY()),
                    Math.min(from.getZ(), to.getZ()));
        }

        public BlockPos max() {
            return new BlockPos(
                    Math.max(from.getX(), to.getX()),
                    Math.max(from.getY(), to.getY()),
                    Math.max(from.getZ(), to.getZ()));
        }

        public int width() {
            return max().getX() - min().getX() + 1;
        }

        public int depth() {
            return max().getZ() - min().getZ() + 1;
        }

        public int height() {
            return max().getY() - min().getY() + 1;
        }

        public long volume() {
            return (long) width() * height() * depth();
        }
    }

    private static final Map<UUID, Region> PENDING = new HashMap<>();

    public static void set(ServerPlayer player, Region region) {
        PENDING.put(player.getUUID(), region);
    }

    public static void clear(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    /** What this player is about to scan, or null. */
    public static Region of(ServerPlayer player) {
        return PENDING.get(player.getUUID());
    }

    /** For a world going away, so a reload does not inherit somebody's marks. */
    public static void forget() {
        PENDING.clear();
    }

    /**
     * The pending region as a box the lamp already knows how to draw, or null.
     *
     * <p>An even span grows by one rather than being drawn half a block off
     * center. The lamp's box is centered on a <em>cell</em> and reaches half its
     * span each way, which lands exactly on the block grid for an odd span and
     * half a block out for an even one. Given the choice between a box that is
     * lopsided and a box that is symmetrically one block generous, the generous
     * one is the honest guide: everything it contains really is inside the
     * region. And every building this mod places has odd spans anyway, so an
     * author laying out a real one never sees the difference.
     */
    public static SurveyPayload.Plot plotFor(ServerPlayer player, BlockPos origin) {
        Region region = of(player);
        if (region == null) {
            return null;
        }
        BlockPos min = region.min();
        int width = odd(region.width());
        int depth = odd(region.depth());
        int centerX = min.getX() + (region.width() - 1) / 2;
        int centerZ = min.getZ() + (region.depth() - 1) / 2;
        return new SurveyPayload.Plot(
                centerX - origin.getX(),
                min.getY() - origin.getY(),
                centerZ - origin.getZ(),
                width, depth, region.height(), region.facing(),
                SurveyPayload.SCAN_REGION,
                // Drawn in the unfinished color, which is what a region is: a
                // plan for something that is not there yet.
                false);
    }

    private static int odd(int span) {
        return span % 2 == 0 ? span + 1 : span;
    }
}
