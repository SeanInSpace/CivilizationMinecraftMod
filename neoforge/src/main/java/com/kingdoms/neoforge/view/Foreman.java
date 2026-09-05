package com.kingdoms.neoforge.view;

import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.neoforge.world.HandDig;
import com.kingdoms.neoforge.world.PerimeterLayer;
import com.kingdoms.neoforge.world.WallClearing;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.BuildLoad;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.work.PublicWorks;
import com.kingdoms.sim.work.Worksite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;

import java.util.List;

/**
 * Sends a spare builder to whatever public work needs a body next.
 *
 * <p>One loop for all of them. The wall had its own worker for about an hour,
 * and writing a second one for roads made it obvious that the third would need
 * a fourth — so what a public work has in common lives in {@link Worksite} and
 * this consumes any of them. A quarry or a bridge later is a class answering
 * three questions rather than another worker, another tick pass, and another
 * argument about who is free to do it.
 *
 * <p><strong>Priority is the order of the list, and nothing else.</strong> The
 * town offers its works most-important-first and this takes the first one with
 * a job in a loaded chunk. Deliberately crude: a settlement with a half-built
 * wall and a half-built road should finish one of them rather than alternate
 * between the two forever.
 *
 * <p>Buildings still come first, checked before this is called at all. Shelter
 * and stores before roads and walls, which is the same order the abstract clock
 * has always used.
 */
public final class Foreman {

    /** How close somebody has to be to work at a station. */
    private static final double WORK_REACH = 3.0;

    private static final double WALK_SPEED = 0.65;

    /**
     * How a builder gets a load out of the storehouse.
     *
     * <p>Handed in rather than written here, because fetching a load is the same
     * walk to the same shelves whether the block is going into a house or into
     * the wall, and the manager already owns that walk — along with the steering
     * bookkeeping that keeps a builder on their errand rather than being herded
     * off it. A second copy of it would be a second set of rules about which
     * storehouse can pay.
     */
    public interface Loader {

        /** @return true once they are loaded and can get on with it */
        boolean fetch(Settlement settlement, Person carrier, PersonEntity builder,
                      String material);
    }

    private Foreman() {
    }

    /**
     * One pass for one builder.
     *
     * @return the work they were put on, walking to it included, or null if the
     *         town had nothing for them. Which work it was matters to the caller
     *         rather than only whether there was one: the sweep that pulls down
     *         a retired wall stands aside for a crew doing it by hand, and a
     *         crew sent to the palisade instead is not that crew.
     */
    public static Worksite work(ServerLevel level, Settlement settlement, Person carrier,
                                PersonEntity builder, Loader loader) {
        List<Worksite> works = PublicWorks.of(settlement);
        for (Worksite work : works) {
            if (!work.isWorthStarting(settlement)) {
                continue;
            }
            SimPos station = work.nextStation(settlement);
            if (station == null) {
                continue;
            }
            BlockPos at = new BlockPos(station.x(), station.y(), station.z());
            if (!level.isLoaded(at)) {
                continue;   // the far side of the town; the clock has that stretch
            }
            if (work instanceof PublicWorks.DismantleWork
                    && crossOffWhatIsAlreadyDown(level, settlement, work)) {
                return work;
            }
            // Materials do not appear in a builder's hands. A fence post is a
            // plank somebody carried out of the storehouse, and it leaves the
            // town's books there -- which is what makes a wall empty a warehouse
            // rather than a number, and what stops a post going up on the
            // strength of timber across the village. A work with nothing to
            // carry (see Worksite.material) skips all of this.
            String owed = work.material();
            if (owed != null && carrier == null) {
                continue;   // an entity with no record behind it has no hands to fill
            }
            if (owed != null && !BuildLoad.canLay(owed, carrier)) {
                // Only if some storehouse actually holds it. The town-wide figure
                // that let this work start counts every shelf there is, and a
                // builder sent to shelves that turn out to be bare waits at them
                // -- which reads as work being done, and starves every work below
                // this one for as long as it lasts. A road needs nothing carried
                // and is exactly what such a builder should be doing instead.
                if (settlement.nearestStore(station, owed) == null) {
                    continue;
                }
                loader.fetch(settlement, carrier, builder, owed);
                return work;   // on the road to the stores
            }
            if (builder.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)
                    > WORK_REACH * WORK_REACH) {
                // An unmakeable route is not a slow one. Navigation throws away
                // whatever it was running before it answers no, so a station
                // nobody can path to would pin this builder here for ever -- and
                // because what this returns is what tells the away sweeps to
                // stand aside, it would pin the work itself along with them.
                if (!builder.getNavigation().moveTo(at.getX() + 0.5, at.getY(),
                        at.getZ() + 0.5, WALK_SPEED)) {
                    continue;
                }
                return work;
            }
            // Ground first. A wall used to be built straight through a wood: its
            // footing is found with a heightmap that steps over leaves and not
            // over logs, so a post whose column held a trunk was founded on top
            // of the trunk, and a canopy reaching across the line gave anything
            // outside a floor to walk in on. A tree in the way is a job before
            // it is an obstacle, and it is felled by hand at the speed an axe
            // takes -- the same order a person would do it in.
            BlockPos growth = WallClearing.inTheWay(level, at);
            if (growth != null) {
                if (builder.distanceToSqr(growth.getX() + 0.5, growth.getY() + 0.5,
                        growth.getZ() + 0.5) > WORK_REACH * WORK_REACH) {
                    builder.getNavigation().moveTo(growth.getX() + 0.5, growth.getY(),
                            growth.getZ() + 0.5, WALK_SPEED);
                    return work;
                }
                if (HandDig.strike(level, builder, growth)) {
                    // Fell the whole tree from the one block that gave, so a
                    // crown fifteen blocks up does not have to be picked at from
                    // a ladder nobody has.
                    fell(level, growth);
                }
                return work;   // still clearing; the post is not due yet
            }

            // Standing at it, and the line is clear. Charged now, with somebody
            // there ready to do the work -- never on the strength of a plan --
            // and charged once per station, when the first block of it goes in.
            // A builder who arrives to find the post half up is finishing one
            // the town has already paid for.
            boolean fresh = isUntouched(level, settlement, work);
            if (fresh && !work.pay(settlement)) {
                continue;   // cannot afford this one; see whether the next is cheaper
            }
            if (fresh && owed != null) {
                carrier.spendCarry();   // the plank goes into this post and no other
            }
            builder.getLookControl().setLookAt(
                    at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5);
            builder.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
            if (swingAt(level, settlement, work, station)) {
                work.completeOne(settlement, true);
            }
            return work;
        }
        return null;
    }

    /**
     * Walks the crew's count past retired positions with nothing of ours on them.
     *
     * <p>Two ways a town ends up with a stretch of old line already down and its
     * count still at the head of it. The away sweep pulls down whatever the crew
     * never reached and keeps no count of its own; and a settlement that
     * re-stakes a second time starts the count again at nought against a list
     * rebuilt around the new ring, so the leading stretch of it may be ground
     * that was cleared a generation ago.
     *
     * <p>Neither is worth a walk. Crossing them off where the crew stands costs
     * a look at a column, and the alternative is a builder pacing the length of
     * the town one empty position at a time. Bounded per pass, because it reads
     * the world and a retired line runs to hundreds of positions.
     *
     * @return whether anything was crossed off, in which case the crew's hands
     *         are on the old line even though nothing came out of the ground
     */
    private static boolean crossOffWhatIsAlreadyDown(ServerLevel level,
                                                     Settlement settlement, Worksite work) {
        int crossed = 0;
        while (crossed < CROSS_OFF_AT_ONCE) {
            SimPos station = work.nextStation(settlement);
            if (station == null) {
                break;
            }
            BlockPos at = new BlockPos(station.x(), station.y(), station.z());
            if (!level.isLoaded(at) || PerimeterLayer.oursStandsAt(level, station)) {
                break;   // unread ground, or a post that is a job for somebody
            }
            work.completeOne(settlement, false);
            crossed++;
        }
        return crossed > 0;
    }

    /**
     * Retired positions crossed off in one pass.
     *
     * <p>Each is a footing search and a few block reads, and this runs once per
     * settlement per pass, so sixty-four clears a few hundred stale positions in
     * a handful of seconds and is never a frame anybody notices.
     */
    private static final int CROSS_OFF_AT_ONCE = 64;

    /**
     * The platform half of a public work: what a swing at a station actually does.
     *
     * <p>{@link Worksite} says where the next job is, what it costs and how to
     * write it down, and it says all of that without knowing a block exists. This
     * is the other half of the same seam, and it is matched to the work by what
     * the work <em>is</em> rather than by its name, so the compiler is the thing
     * keeping the two lists in step.
     *
     * @return whether the station is finished — one more and the town records it
     */
    private static boolean swingAt(ServerLevel level, Settlement settlement, Worksite work,
                                   SimPos station) {
        if (work instanceof PublicWorks.WallWork) {
            return plantPost(level, settlement);
        }
        if (work instanceof PublicWorks.DismantleWork) {
            // Done either way, and this is only ever reached with something of
            // ours standing here: a position already clear was crossed off before
            // anybody was sent anywhere. A post that will not come out is one
            // somebody has since built into, and standing over it for ever helps
            // nobody.
            PerimeterLayer.pullDownOurs(level, station);
            return true;
        }
        return true;   // a road stretch is opened by being walked out
    }

    /**
     * One block of the palisade, planted by hand.
     *
     * <p>Block by block, not post by post: the wall goes up under a builder's
     * hands a course at a time, which is the whole difference between a town
     * building a wall and a wall appearing beside a town. Which block is next is
     * read off the ground rather than counted, so a post interrupted halfway is
     * resumed at the course that is missing.
     *
     * @return whether nothing more is owed at this position
     */
    private static boolean plantPost(ServerLevel level, Settlement settlement) {
        Perimeter perimeter = settlement.perimeter();
        if (perimeter == null) {
            return true;
        }
        List<PerimeterLayer.Course> plan =
                PerimeterLayer.planAt(level, perimeter, perimeter.laid());
        PerimeterLayer.Course owed = PerimeterLayer.owed(level, plan);
        if (owed == null) {
            return true;   // the post stands, or this position is a gate's opening
        }
        // A course the ground refuses is a course nothing can lay: the line runs
        // through somebody's wall just here, which is a better wall than a fence
        // and is exactly what lineIsClosed forgives. Standing here swinging at it
        // for ever is the one outcome that helps nobody.
        return !PerimeterLayer.layByHand(level, owed)
                || PerimeterLayer.owed(level, plan) == null;
    }

    /**
     * Whether no work at all has been done at this station yet.
     *
     * <p>Asked of the ground rather than of the plan, and the difference is a
     * double charge. Gates move while the wall is going up — they follow the
     * streets as those appear, every twenty steps — so a position whose lower
     * course a builder laid this minute can be a gateway the next, and its plan
     * a fence gate instead of a post. Compared against the plan, the standing
     * fence is then not "part of this station" and the town pays a second coin
     * and a second plank for the same position. Compared against the column, it
     * is what it is: work somebody has already done here.
     */
    private static boolean isUntouched(ServerLevel level, Settlement settlement,
                                       Worksite work) {
        if (!(work instanceof PublicWorks.WallWork)) {
            return true;   // nothing else has a part-done state to find
        }
        Perimeter perimeter = settlement.perimeter();
        if (perimeter == null) {
            return true;
        }
        SimPos station = perimeter.ringPositions().get(perimeter.laid());
        if (PerimeterLayer.planAt(level, perimeter, perimeter.laid()).isEmpty()) {
            return false;   // an opening costs nothing to leave open
        }
        return !PerimeterLayer.oursStandsAt(level, station);
    }

    /**
     * Brings down the whole tree the given block belongs to.
     *
     * <p>Breadth-first from the block that gave, through logs and leaves only,
     * so a felled trunk takes its own canopy with it rather than leaving a
     * crown floating over the wall it was in the way of. Bounded, because a
     * dark oak in a forest is joined to a great many of its neighbours by
     * touching leaves and a wall builder should not be made to clear a county.
     */
    private static void fell(ServerLevel level, BlockPos from) {
        java.util.Deque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        queue.add(from);
        seen.add(from);
        int taken = 0;
        while (!queue.isEmpty() && taken < MOST_OF_ONE_TREE) {
            BlockPos at = queue.poll();
            if (!level.isLoaded(at)) {
                continue;
            }
            if (!WallClearing.isGrowth(level.getBlockState(at))) {
                continue;
            }
            level.destroyBlock(at, false, null, 512);
            taken++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = at.offset(dx, dy, dz);
                        if (seen.add(next)) {
                            queue.add(next);
                        }
                    }
                }
            }
        }
    }

    /**
     * Blocks one felling will take at most.
     *
     * <p>Generous for a tree and small for a forest. Whatever is left standing
     * is simply found again on the next pass, so the cap costs a little time
     * and never leaves the wall permanently blocked.
     */
    private static final int MOST_OF_ONE_TREE = 400;
}
