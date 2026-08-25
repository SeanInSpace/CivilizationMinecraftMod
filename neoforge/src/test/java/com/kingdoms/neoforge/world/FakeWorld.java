package com.kingdoms.neoforge.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

/**
 * A world you can build by hand, for asking the auditor what it thinks of it.
 *
 * <p>Only two kinds of block exist here — something and nothing — because that
 * is all {@link WorldView} asks about, plus a handful of flags for the few
 * cases where the auditor wants to know what <em>sort</em> of something. That
 * narrowness is the whole reason the seam was worth cutting: a fake that had to
 * model real block states would cost more to trust than the code it tests.
 */
final class FakeWorld implements WorldView {

    private final Set<BlockPos> solid = new HashSet<>();
    private final Set<BlockPos> gates = new HashSet<>();
    private final Set<BlockPos> fluid = new HashSet<>();
    private final Set<BlockPos> farmland = new HashSet<>();
    private final Set<BlockPos> crops = new HashSet<>();
    private final Set<BlockPos> unloaded = new HashSet<>();

    private int ground;
    private int looseItems;
    private long step;

    FakeWorld(int ground) {
        this.ground = ground;
    }

    // --- building the world ---

    FakeWorld solid(int x, int y, int z) {
        solid.add(new BlockPos(x, y, z));
        return this;
    }

    /** A flat plain of solid ground at this height, out to the given reach. */
    FakeWorld plain(int y, int reach) {
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                solid(x, y, z);
            }
        }
        return this;
    }

    /** Walls two blocks high around the rectangle at these half-extents. */
    FakeWorld walls(int half, int floor) {
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if (Math.abs(dx) == half || Math.abs(dz) == half) {
                    solid(dx, floor + 1, dz);
                    solid(dx, floor + 2, dz);
                }
            }
        }
        return this;
    }

    /** Knocks a person-sized hole in the wall at this column. */
    FakeWorld doorway(int x, int z, int floor) {
        solid.remove(new BlockPos(x, floor + 1, z));
        solid.remove(new BlockPos(x, floor + 2, z));
        return this;
    }

    FakeWorld empty(int x, int y, int z) {
        solid.remove(new BlockPos(x, y, z));
        return this;
    }

    FakeWorld gate(int x, int y, int z) {
        gates.add(new BlockPos(x, y, z));
        return this;
    }

    FakeWorld fluid(int x, int y, int z) {
        fluid.add(new BlockPos(x, y, z));
        return this;
    }

    FakeWorld farmland(int x, int y, int z) {
        farmland.add(new BlockPos(x, y, z));
        return this;
    }

    FakeWorld crop(int x, int y, int z) {
        crops.add(new BlockPos(x, y, z));
        return this;
    }

    FakeWorld unloaded(int x, int y, int z) {
        unloaded.add(new BlockPos(x, y, z));
        return this;
    }

    FakeWorld groundAt(int y) {
        this.ground = y;
        return this;
    }

    FakeWorld looseItems(int count) {
        this.looseItems = count;
        return this;
    }

    FakeWorld atStep(long step) {
        this.step = step;
        return this;
    }

    // --- what the auditor is allowed to ask ---

    /**
     * Whether this fake's ground is running. True by default: a test that has
     * gone to the trouble of laying out a field means it to be judged.
     */
    public boolean ticking = true;

    @Override
    public boolean isTicking(BlockPos pos) {
        return ticking;
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return !unloaded.contains(pos);
    }

    @Override
    public boolean isPassable(BlockPos pos) {
        return !solid.contains(pos);
    }

    @Override
    public boolean isStandable(BlockPos pos) {
        return solid.contains(pos);
    }

    @Override
    public boolean hasFluid(BlockPos pos) {
        return fluid.contains(pos);
    }

    @Override
    public boolean isFarmland(BlockPos pos) {
        return farmland.contains(pos);
    }

    @Override
    public boolean isCrop(BlockPos pos) {
        return crops.contains(pos);
    }

    @Override
    public boolean isFenceGate(BlockPos pos) {
        return gates.contains(pos);
    }

    @Override
    public String blockNameAt(BlockPos pos) {
        if (fluid.contains(pos)) {
            return "test:water";
        }
        return solid.contains(pos) ? "test:stone" : "test:air";
    }

    @Override
    public int groundLevel(int x, int z) {
        return ground;
    }

    @Override
    public int looseItemsIn(AABB box) {
        return looseItems;
    }

    @Override
    public long stepsElapsed() {
        return step;
    }
}
