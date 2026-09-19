package com.civilization.neoforge.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * How a town lays and takes up its own blocks, without leaving items on the floor.
 *
 * <p><strong>The fault this exists for.</strong> The playtest's server log ran a
 * continuous stream of {@code ITEMPOP} — torches, saplings, sticks, wheat seeds,
 * leather — around a town nobody was touching; eight wheat seeds appeared at one
 * column inside four seconds. Every one of the mod's own {@code destroyBlock}
 * calls already passed {@code false} for drops, so the town was never dropping
 * the block it broke. It was dropping <em>the blocks next to it</em>.
 *
 * <p>That is what a neighbour update is. {@code Block.UPDATE_NEIGHBORS} makes
 * every block touching the changed cell re-check whether it can still stand, and
 * anything that cannot — a torch on a wall the perimeter just took down, a crop
 * over farmland the excavation just dug, a sapling on ground the paving just
 * turned to dirt path — is destroyed <em>with drops</em>, because that is what
 * vanilla does when a player knocks the support out from under something. The
 * town is not a player. Its sweeps re-lay the same square of ground a hundred
 * times over the life of a settlement, and every pass shook a handful of items
 * onto the grass to lie there until they despawned or a hopper found them.
 *
 * <p><strong>It is not fixed by a flag, though a flag looks like the answer.</strong>
 * {@code Block.UPDATE_SUPPRESS_DROPS} says "destroy what can no longer stand and
 * give it to nobody", and it governs the block this call is replacing — but it
 * is masked off before the neighbour shape updates run, so it never reaches the
 * cascade that was doing the dropping. The measurement is what settled it: with
 * the flag on every one of the town's calls, the rate did not move. So the flag
 * stays because it is right about the one block it does govern, and the item is
 * refused at the door instead — see {@link #atWork}, which is open for exactly
 * the width of one block change inside one of the town's own sweeps, and
 * {@code CivilizationMod.onEntityJoin}, which is the door.
 *
 * <p>Two hundred and fifteen items a minute before, four after, on the same
 * save and the same sweeps.
 *
 * <p><strong>What still drops, and must.</strong> This is about the town's
 * <em>housekeeping</em>: paving, walling, dressing, lamps, excavation, clearing.
 * It is not about work whose whole point is the item — a lumberjack's log and a
 * farmer's harvest are credited to a store by the code that cut them, not picked
 * up off the ground, so they were never {@code ITEMPOP} to begin with. Nothing
 * here changes a single yield.
 *
 * <p><strong>And the sound stays.</strong> {@link #clear} keeps vanilla's break
 * effect, because {@code Ambience.trades} notes that outdoor work is audible by
 * accident — felling a tree and cutting a seam are heard because they go through
 * a real break — and a silent axe would be a regression paid for a tidy floor.
 */
public final class TownBlocks {

    private TownBlocks() {
    }

    /**
     * The flags the town's own work carries: the ordinary update, and no drops.
     *
     * <p>{@code UPDATE_ALL} rather than {@code UPDATE_CLIENTS}, deliberately.
     * Skipping the neighbour update would stop some of the litter and would also
     * leave a torch hanging in mid-air where its wall used to be and a crop
     * standing on nothing — the world would be quietly wrong instead of quietly
     * littered. The update has to happen; what it must not do is pay out.
     *
     * <p><strong>The flag is necessary and is not sufficient</strong>, which cost
     * a measurement to find out. {@code UPDATE_SUPPRESS_DROPS} governs the block
     * this call is replacing. It does <em>not</em> survive into the cascade:
     * {@code Level.markAndNotifyBlock} masks the flag off before it runs the
     * neighbour shape updates, so the torch that comes off the wall goes through
     * {@code Level.neighborShapeChanged} into a plain {@code destroyBlock} with
     * drops on, and no flag passed in here can reach it. That is what
     * {@link #atWork} is for. The flag stays because it is right about the one
     * block it does govern.
     */
    public static final int QUIET = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;

    /**
     * How deep in the town's own block work we currently are.
     *
     * <p>A depth rather than a flag because these calls nest — a perimeter post
     * clears the growth on its column and then lays the post, inside one sweep —
     * and a plain boolean would be switched off by the inner call returning while
     * the outer one was still working.
     *
     * <p>Server thread only, and that is not an assumption, it is where every
     * caller is: the sweeps all run out of {@code PersonEntityManager.tick},
     * which runs on the server tick. Nothing here is synchronized because
     * nothing else touches it.
     */
    private static int working;

    /**
     * Whether the town is in the middle of its own block work right now.
     *
     * <p>{@code CivilizationMod.onEntityJoin}'s question, and the whole of how
     * the litter is stopped. Vanilla will not let the town pass "drop nothing"
     * down into the neighbour cascade — see {@link #QUIET} — so instead of
     * arguing with the flags, the item is refused at the door: an item entity
     * that tries to join the world while the town is mid-sweep is the town's
     * own housekeeping shaking something loose, and it does not join.
     *
     * <p><strong>Why this is safe.</strong> The window is a single
     * {@code setBlock} deep in a sweep, not a tick and not a pass. Nothing else
     * can spawn an item inside it: a mob cannot die there, a player cannot break
     * a block there, a hopper cannot eject there. And nothing the town earns
     * arrives as an item in the first place — a lumberjack's timber and a
     * farmer's grain are credited straight to a store by the code that cut them,
     * which is why the doctrine can say the theatre touches no ledger and mean
     * it. So what this refuses is exactly the set it was written to refuse.
     */
    public static boolean atWork() {
        return working > 0;
    }

    /** Lays one block for the town, dropping nothing that it knocks loose. */
    public static boolean lay(ServerLevel level, BlockPos pos, BlockState state) {
        return lay(level, pos, state, QUIET);
    }

    /**
     * The same, for a sweep that has its own reason for its own flags.
     *
     * <p>Several of the layers lay with {@code UPDATE_CLIENTS} rather than the
     * full update — the paving does, because a dirt path does not need its
     * neighbours told, and the placer does, because a blueprint lays its own
     * blocks in its own order and does not want vanilla second-guessing the
     * shapes. Those flags are still right and are kept. What they never did was
     * stop the litter: a shape update runs whatever the neighbour bit says, so
     * every one of these calls could shake a torch or a crop loose too. Going
     * through here is what makes that quiet, whatever flags are passed.
     */
    public static boolean lay(ServerLevel level, BlockPos pos, BlockState state,
                              int flags) {
        working++;
        try {
            return level.setBlock(pos, state, flags);
        } finally {
            working--;
        }
    }

    /**
     * Takes one block up for the town, dropping neither it nor anything it was
     * holding up.
     *
     * <p>Vanilla's own {@code destroyBlock} with the drops turned off is not
     * enough and never was: its {@code dropBlock} flag governs the block being
     * broken, and the cascade it sets off underneath still pays out for
     * everything that falls with it. This is the same method with the flag on
     * the cascade as well.
     *
     * @param heard whether to play the break sound and particles — true for work
     *              a settler is visibly doing, false for a sweep nobody is
     *              swinging at
     */
    public static boolean clear(ServerLevel level, BlockPos pos, boolean heard) {
        BlockState standing = level.getBlockState(pos);
        if (standing.isAir()) {
            return false;
        }
        if (heard) {
            level.levelEvent(2001, pos, Block.getId(standing));
        }
        FluidState fluid = level.getFluidState(pos);
        working++;
        try {
            return level.setBlock(pos, fluid.createLegacyBlock(), QUIET);
        } finally {
            working--;
        }
    }

    /** The same, silent: the ordinary case for a sweep. */
    public static boolean clear(ServerLevel level, BlockPos pos) {
        return clear(level, pos, false);
    }

    /**
     * Takes up whatever is left of a felled tree's crown, so it cannot decay.
     *
     * <p>Felling takes the trunk and leaves the leaves. Vanilla then decays them
     * over the following minutes — and decaying leaves drop saplings and sticks,
     * which is most of the litter the playtest saw and the part that looked
     * least like the town's doing, because it arrived long after the axe had
     * moved on and nowhere near where the tree had been cut.
     *
     * <p>Taken down at once and quietly rather than left to rot, which is also
     * the better picture: a felled tree in this mod goes from standing to gone,
     * and a crown hanging in the air for five minutes afterwards was never
     * something anybody wanted to look at.
     *
     * <p>Bounded, and the bound is the point. It walks outward from the trunk's
     * own column through connected leaves only, within {@link #CROWN_REACH} — so
     * a canopy that touches the next tree along does not chain into felling a
     * forest's worth of leaves in one tick.
     */
    public static void clearCrown(ServerLevel level, BlockPos trunk) {
        for (int dy = 0; dy <= CROWN_REACH; dy++) {
            for (int dx = -CROWN_REACH; dx <= CROWN_REACH; dx++) {
                for (int dz = -CROWN_REACH; dz <= CROWN_REACH; dz++) {
                    BlockPos at = trunk.offset(dx, dy, dz);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(at);
                    // Natural leaves only. A leaf block a player placed is
                    // persistent and is somebody's hedge; taking that would be
                    // the town tidying up a build.
                    if (!(state.getBlock() instanceof LeavesBlock)) {
                        continue;
                    }
                    if (state.hasProperty(LeavesBlock.PERSISTENT)
                            && state.getValue(LeavesBlock.PERSISTENT)) {
                        continue;
                    }
                    working++;
                    try {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), QUIET);
                    } finally {
                        working--;
                    }
                }
            }
        }
    }

    /**
     * How far a crown reaches from the trunk that held it up: five blocks.
     *
     * <p>Wider than an oak's canopy half-span and narrower than the gap between
     * two trees the clearing would fell separately. Big enough that nothing is
     * left hanging; small enough that one felling is one tree.
     */
    private static final int CROWN_REACH = 5;
}
