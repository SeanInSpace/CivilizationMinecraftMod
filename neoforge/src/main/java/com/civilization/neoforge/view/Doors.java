package com.civilization.neoforge.view;

import com.civilization.neoforge.entity.PersonEntity;
import com.civilization.neoforge.world.TownBlocks;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Household;
import com.civilization.sim.person.Person;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * The town shuts its doors when it goes to bed.
 *
 * <p><strong>This is theatre and is worth nothing.</strong> A shut door is not a
 * wall: it keeps nothing out that the mod models, stops no raider, saves no
 * torch and touches no store, no ledger and no yield. It is here for one reason,
 * which is that a street of houses standing wide open at midnight reads as a
 * street nobody lives in. Run it or do not run it and the save comes out
 * identical.
 *
 * <p><strong>What vanilla already does, checked rather than assumed.</strong>
 * {@code PersonEntity} carries {@code new OpenDoorGoal(this, true)}, and the
 * standing belief was that it closes behind everybody except the last settler
 * in, whose door nothing would ever shut. Reading the goal says otherwise. Its
 * {@code canUse} needs {@code mob.horizontalCollision} — somebody has to walk
 * <em>into</em> a shut door for it to fire at all — and from there it is closed
 * on either of two events: {@code DoorInteractGoal.tick} sets {@code passed} the
 * instant the body crosses the plane of the door, which fails
 * {@code canContinueToUse} and runs {@code stop}, which closes it; and failing
 * that, {@code forgetTime} counts twenty ticks down from {@code start} and
 * closes it anyway. The last one in is no different from the first. So that half
 * of the claim is simply wrong, and there was nothing there to fix.
 *
 * <p><strong>The hole that is real</strong> is the goal never getting to run its
 * {@code stop} at all: a body that is despawned by the embodiment planner, or
 * killed, between opening a door and passing through it leaves that door open
 * for ever, because a removed entity's goals are dropped rather than stopped.
 * Nothing in the mod ever shut one again. This does, along with anything a
 * player left swinging — and it is idempotent, so it costs a handful of block
 * reads a night and nothing else.
 *
 * <p><strong>The door is read off the plan, never scanned for.</strong>
 * {@link Building#doorstep} is where the simulation says somebody stands to walk
 * in — found in the file for an authored building, arithmetic off the facing for
 * a drawn one — so the leaf is the doorstep and the two cells inward from it,
 * and that is the whole of the search. See {@link #leavesOf}, which is pure and
 * is where the reading is tested.
 *
 * <p>Worth saying plainly: the buildings this mod <em>draws</em> cut a doorway
 * and hang nothing in it — see {@code Parts}, where a doorway is a gap — so the
 * doors this shuts are the ones authored blueprints carry and the ones players
 * hang themselves. A village of drawn cottages has nothing to close and this
 * finds nothing to do, which is correct and is not a failure.
 */
final class Doors {

    /**
     * How far in from the doorstep a leaf may be hung: two cells.
     *
     * <p>The doorstep is one step outside the wall, so the wall plane itself is
     * one in and the first cell of the floor is two. A door is in one of those
     * three, and anything further in is a room rather than a doorway.
     */
    static final int LEAF_REACH = 2;

    /**
     * How far above and below the plan's floor line a leaf is looked for.
     *
     * <p>The simulation's y for a building is its floor and the world's is
     * whatever the ground turned out to be once it was leveled, and the two
     * disagree by a course either way often enough that a probe fixed on one of
     * them would find half the doors in a hilly town. Two up and one down covers
     * both halves of a leaf standing on either answer.
     */
    private static final int BELOW = 1;

    private static final int ABOVE = 2;

    /**
     * How near their own home a settler counts as being in it: 3 blocks.
     *
     * <p>Not a footprint test, deliberately. By the time this runs the curfew has
     * already walked everybody to their own bed or, failing that, to their own
     * doorstep — see {@code PersonEntityManager.dailyRoutine}, whose walk home
     * arrives at {@code BED_REACH} — so "at home" and "inside" are the same
     * answer to within a pace, and a settler standing on his own doorstep at
     * midnight is somebody the house may shut its door behind. If he walks out
     * again the goal opens it for him.
     */
    private static final double HOME_REACH = 3.0;

    /**
     * Passes between sweeps: 4, so about every four seconds.
     *
     * <p>Far finer than the thing being decided — the town has a whole night to
     * get its doors shut — and the point of the beat is that a sweep is block
     * reads against a level, which is the one cost here worth having an opinion
     * about.
     */
    private static final int BEAT_PASSES = 4;

    private final ServerLevel level;

    private final Function<UUID, PersonEntity> viewOf;

    /** The world day each town was last found with every door shut. */
    private final Map<Settlement.Id, Long> settledOn = new HashMap<>();

    private int beat;

    Doors(ServerLevel level, Function<UUID, PersonEntity> viewOf) {
        this.level = Objects.requireNonNull(level, "level");
        this.viewOf = Objects.requireNonNull(viewOf, "viewOf");
    }

    // --- the reading ------------------------------------------------------------

    /**
     * Every cell a building's door could be hanging in, read off its plan.
     *
     * <p>The doorstep first, then inward toward the origin: a door hung on the
     * outer face of a porch, the wall plane where every ordinary one is, and the
     * first cell of the floor inside it. Three cells and no search — which is
     * the whole point of this being derived from {@link Building#doorstep}
     * rather than from a box of block reads round the house.
     *
     * <p>Pure, and tested without a level. What the sweep adds to it is the
     * height window and the question of whether there is actually a door there.
     */
    static List<SimPos> leavesOf(Building building) {
        if (building == null) {
            return List.of();
        }
        SimPos step = building.doorstep();
        int[] in = inward(building.facing());
        List<SimPos> cells = new ArrayList<>();
        for (int deep = 0; deep <= LEAF_REACH; deep++) {
            cells.add(new SimPos(step.x() + in[0] * deep, step.y(),
                    step.z() + in[1] * deep));
        }
        return List.copyOf(cells);
    }

    /**
     * The step from the doorstep back into the building, per facing.
     *
     * <p>{@code Building.facing}'s convention, read straight off
     * {@link Building#doorstep}: facing 0 puts the doorstep at {@code +z}, so
     * inward is {@code -z}, and so round. An authored building's doorstep is a
     * recorded offset that the same turn has already been applied to, so the
     * axis is the same fact for both kinds and this needs no second case.
     */
    static int[] inward(int facing) {
        return switch (Math.floorMod(facing, 4)) {
            case 1 -> new int[] {1, 0};
            case 2 -> new int[] {0, 1};
            case 3 -> new int[] {-1, 0};
            default -> new int[] {0, -1};
        };
    }

    // --- the pass ----------------------------------------------------------------

    /**
     * One town's doors, shut for the night.
     *
     * <p>Only at bedtime, only on the beat, and only once a night per town: the
     * sweep stops looking at a settlement it has found entirely shut until the
     * clock turns over, so the ordinary cost of this on a quiet night is one
     * map lookup.
     *
     * @param bedtime what {@code PersonEntityManager.isBedtime} says: dark out
     *                <em>and</em> past dusk, so a thunderstorm at noon does not
     *                have the town bolting its doors
     * @param day     the world day, so "shut already tonight" can lapse at dawn
     */
    void shutForTheNight(Settlement settlement, boolean bedtime, long day) {
        if (!bedtime) {
            settledOn.remove(settlement.id());
            return;
        }
        if (++beat % BEAT_PASSES != 0) {
            return;
        }
        Long done = settledOn.get(settlement.id());
        if (done != null && done == day) {
            return;
        }
        boolean all = true;
        for (Household household : settlement.households()) {
            if (!household.isHoused()) {
                continue;
            }
            Building home = buildingAt(settlement, household.home());
            if (home == null) {
                continue;
            }
            if (someoneStillOut(settlement, household)) {
                all = false;   // the house is waiting up for somebody
                continue;
            }
            all &= shut(home);
        }
        // A lit building is shut on the hour rather than on who is in it. The
        // hearth and the inn are the town's rooms and not anybody's house, so
        // "everybody who lives here is in" is a question with no answer for
        // them; what there is instead is closing time, which is what bedtime is.
        for (BuildingRole role : LIT) {
            all &= shut(settlement.buildingWithRole(role));
        }
        if (all) {
            settledOn.put(settlement.id(), day);
        }
    }

    /** The town's own rooms, which keep hours rather than residents. */
    private static final List<BuildingRole> LIT =
            List.of(BuildingRole.HEARTH, BuildingRole.INN);

    /** Drops what is remembered about a level that is going away. */
    void forget() {
        settledOn.clear();
    }

    // --- who is in ----------------------------------------------------------------

    /**
     * Whether anybody who lives here is still out of doors.
     *
     * <p>A person with no body is in by definition: an unembodied settler is a
     * record in a ledger, not somebody standing in the road, and a house that
     * waited up for one would never shut its door at all in a town nobody is
     * watching closely. Everybody else is in if they are asleep or standing at
     * their own home; see {@link #HOME_REACH}.
     */
    private boolean someoneStillOut(Settlement settlement, Household household) {
        SimPos home = household.home();
        for (Person.Id id : household.members()) {
            Person person = settlement.resident(id);
            if (person == null || !person.isEmbodied()) {
                continue;
            }
            PersonEntity view = viewOf.apply(id.value());
            if (view == null || view.isRemoved() || view.isSleeping()) {
                continue;
            }
            double dx = view.getX() - (home.x() + 0.5);
            double dz = view.getZ() - (home.z() + 0.5);
            if (dx * dx + dz * dz > HOME_REACH * HOME_REACH) {
                return true;
            }
        }
        return false;
    }

    // --- the blocks ------------------------------------------------------------------

    /**
     * Shuts whatever leaves this building has open.
     *
     * @return whether the building is now settled — every leaf found shut, and
     *         every cell of its doorway readable. An unloaded doorway answers
     *         no, so the town is looked at again rather than written off as done
     *         on the strength of chunks nobody has read.
     */
    private boolean shut(Building building) {
        if (building == null) {
            return true;
        }
        boolean settled = true;
        for (SimPos cell : leavesOf(building)) {
            for (int dy = -BELOW; dy <= ABOVE; dy++) {
                BlockPos at = new BlockPos(cell.x(), cell.y() + dy, cell.z());
                if (!level.isLoaded(at)) {
                    settled = false;
                    continue;
                }
                close(at);
            }
        }
        return settled;
    }

    /**
     * One leaf, shut, both halves of it.
     *
     * <p>Both halves by hand rather than through {@code DoorBlock.setOpen},
     * which writes the one block it was handed with the shape update suppressed
     * and leaves the other half of the same door standing open — invisible on a
     * door a player swings, because the half they clicked is the half the game
     * then updates, and very visible on a door nobody is touching. The sound is
     * the door's own: a spruce door in a mire village should not close like an
     * oak one.
     */
    private void close(BlockPos at) {
        BlockState state = level.getBlockState(at);
        if (!(state.getBlock() instanceof DoorBlock door)
                || !state.getValue(DoorBlock.OPEN)) {
            return;
        }
        BlockPos foot = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                ? at.below() : at;
        boolean swung = false;
        for (BlockPos half : new BlockPos[] {foot, foot.above()}) {
            BlockState leaf = level.getBlockState(half);
            if (leaf.getBlock() == door && leaf.getValue(DoorBlock.OPEN)) {
                TownBlocks.lay(level, half,
                        leaf.setValue(DoorBlock.OPEN, false), Block.UPDATE_ALL);
                swung = true;
            }
        }
        if (swung) {
            level.playSound(null, foot, door.type().doorClose(), SoundSource.BLOCKS,
                    1.0F, 1.0F);
        }
    }

    /** The building standing at this origin, or null. */
    private static Building buildingAt(Settlement settlement, SimPos origin) {
        for (Building building : settlement.buildings()) {
            if (building.origin().equals(origin)) {
                return building;
            }
        }
        return null;
    }
}
