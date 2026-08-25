package com.kingdoms.neoforge.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * What the auditor is allowed to see of the world.
 *
 * <p>The same idea as {@code WorldBridge}, one level down. That seam exists so
 * the simulation never imports Minecraft; this one exists so the auditor never
 * imports a <em>running server</em>. Everything it needs is declared here as
 * questions about a position, which means the geometry can be driven from a
 * hand-built world in a test.
 *
 * <p>Worth stating why that mattered enough to be worth doing. The auditor is
 * the only instrument this project has, and its geometry checks were reachable
 * only by founding a town, growing it, and reading a log — so a check that had
 * been reporting the same fault for weeks could not be interrogated at all,
 * only argued about. Four buildings reported as having no way in were either a
 * real defect or a false positive, and there was no way to find out which
 * without walking to them.
 *
 * <p>Deliberately narrow: eight questions and a clock. Anything wider and the
 * fake in the tests stops being cheap to write, which is the only reason a
 * seam like this ever earns its keep.
 */
public interface WorldView {

    /** Whether the chunk holding this position is loaded and can be judged. */
    boolean isLoaded(BlockPos pos);

    /**
     * Whether this ground is actually <em>running</em> — not merely readable.
     *
     * <p>These are two different questions and the difference matters. A chunk
     * at the edge of the loaded area can be read block by block while nothing in
     * it ticks: crops do not grow, dropped items never despawn, and the settlers
     * standing in it are not being asked to do anything. Every geometric check
     * here is fair on such ground, because geometry does not move. Every check
     * about a <em>process</em> is not.
     */
    boolean isTicking(BlockPos pos);

    /** Nothing here to bump into — a body could occupy this block. */
    boolean isPassable(BlockPos pos);

    /**
     * Something here to stand on: any collision at all.
     *
     * <p>Deliberately not a sturdy-face test. Paths, farmland and slabs all fail
     * that while being exactly what people walk on all day, and demanding it
     * once reported the best-connected houses in town — the ones with a track
     * laid to the door — as having no way in.
     */
    boolean isStandable(BlockPos pos);

    /** Standing water or lava, in a place a room ought to be dry. */
    boolean hasFluid(BlockPos pos);

    /** Tilled soil, ready to be planted. */
    boolean isFarmland(BlockPos pos);

    /** Something growing, at any stage. */
    boolean isCrop(BlockPos pos);

    /** A gate counts as a way in whether it is open or shut. */
    boolean isFenceGate(BlockPos pos);

    /**
     * What is at this position, named the way the registry names it.
     *
     * <p>Only for reports: when a crop vanishes the auditor says what replaced
     * it, which is how the flooded-farm cause was found after three wrong
     * theories.
     */
    String blockNameAt(BlockPos pos);

    /**
     * The y of the terrain surface at this column, foliage ignored.
     *
     * <p>What the shelf check measures a floor against: a building whose floor
     * sits well above or below the ground around it is either perched or buried.
     */
    int groundLevel(int x, int z);

    /** How many live item entities are lying about in this box. */
    int looseItemsIn(AABB box);

    /**
     * The simulation's own step count.
     *
     * <p>Not the level clock, which freezes with the server while the audit's
     * notion of "this build has not moved in a while" must not.
     */
    long stepsElapsed();
}
