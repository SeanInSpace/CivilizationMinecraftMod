package com.kingdoms.neoforge.view;

import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.neoforge.world.Bridge;
import com.kingdoms.neoforge.world.HandDig;
import com.kingdoms.neoforge.world.PathLayer;
import com.kingdoms.neoforge.world.PerimeterLayer;
import com.kingdoms.neoforge.world.WallClearing;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.BuildLoad;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.settlement.PathNetwork;
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
            SimPos station = stationFor(level, settlement, work);
            if (station == null) {
                continue;
            }
            BlockPos at = new BlockPos(station.x(), station.y(), station.z());
            if (!level.isLoaded(at)) {
                // The far side of the town; the clock has that stretch. A road
                // is the exception: its stations are the crew's own place along
                // a run and only they know it, so a column nobody can see has to
                // be walked past rather than waited on. Waiting on it stops the
                // run being opened at all, and the clock -- which sees only the
                // near end, and that end loaded -- stands aside for a crew that
                // is not coming, so every later street in the town waits behind
                // it. What is walked past is laid by the mending sweep on the
                // pass after somebody loads it.
                skipColumn(settlement, work);
                continue;
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
                // Only if they are still walking. A builder who reaches the
                // shelves and fills their arms this pass can get on with the
                // work in it -- reporting them busy instead costs the town a
                // whole pass in which nobody lays anything and no second builder
                // is tried, which is the same waste the house-building loop
                // avoids by asking the same question.
                if (!loader.fetch(settlement, carrier, builder, owed)) {
                    return work;   // on the road to the stores
                }
            }
            if (builder.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)
                    > WORK_REACH * WORK_REACH) {
                // An unmakeable route is not a slow one. Navigation throws away
                // whatever it was running before it answers no, so a station
                // nobody can path to would pin this builder here for ever -- and
                // because what this returns is what tells the away sweeps to
                // stand aside, it would pin the work itself along with them.
                if (!builder.getNavigation().moveTo(at.getX() + 0.5, at.getY(),
                        at.getZ() + 0.5, WALK_SPEED)
                        || !stillWorthWalkingTo(settlement, work, station)) {
                    giveUpOn(settlement, work);
                    continue;
                }
                return work;
            }
            WALKING.remove(settlement.id());
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
            builder.getLookControl().setLookAt(
                    at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5);
            builder.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
            Swing swing = swingAt(level, settlement, work, station);
            if (fresh && owed != null && swing.worked()) {
                // Only once a block is actually in the ground. A course the
                // column refuses -- the line grazing somebody's wall, which
                // lineIsClosed forgives and counts as wall -- is not a plank
                // used, and a load emptied into positions nothing was built at
                // would have a builder walking back to the shelves for nothing.
                carrier.spendCarry();
            }
            if (swing.done()) {
                work.completeOne(settlement, swing.worked());
            }
            return work;
        }
        return null;
    }

    /**
     * What a swing at a station came to.
     *
     * <p>Two answers rather than one, because a station being finished and a
     * station having had work in it are different things. A retired position the
     * away sweep already cleared is finished the moment the crew looks at it and
     * must be crossed off, or they walk back to it for ever — and it must not be
     * paid salvage, or a town moving its wall over ground somebody else already
     * cleared makes timber out of nothing.
     */
    private record Swing(boolean done, boolean worked) {
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
            // The sweep's own signature, and it has to be: a looser test would
            // walk a crew across town to somebody's pen, take nothing out of it,
            // and credit the town the salvage of a post that was never there.
            if (!level.isLoaded(at) || PerimeterLayer.standsAsOurWall(level, station)) {
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
     * @return whether the station is finished, and whether there was work in it
     */
    private static Swing swingAt(ServerLevel level, Settlement settlement, Worksite work,
                                 SimPos station) {
        if (work instanceof PublicWorks.WallWork) {
            return plantPost(level, settlement);
        }
        if (work instanceof PublicWorks.DismantleWork) {
            // Finished either way. A post that will not come out is one somebody
            // has since built into or a lone fence that was never ours, and
            // standing over it for ever helps nobody -- but it is not a post
            // pulled up, and the town is not paid for it.
            return new Swing(true, PerimeterLayer.pullDownOurs(level, station));
        }
        if (work instanceof PublicWorks.RoadWork road) {
            return new Swing(paveOne(level, settlement, road), true);
        }
        return new Swing(true, true);
    }

    // --- roads ---

    /**
     * How far along the run each settlement's paving crew has got.
     *
     * <p>Not saved, and it does not want to be. What it indexes is the columns of
     * one stretch, and the work it records is written in the ground: a reload
     * starts the crew at the near end again and they walk a run that is already
     * paved, which costs them the walk and lays nothing, because
     * {@code PathLayer.paveAt} writes only where a road is missing. A saved
     * cursor would buy that walk back at the price of a number that could outlive
     * the run it points into.
     *
     * <p>Keyed by where the run starts as well as by the town, so a crew that
     * finishes one stretch and is handed the next begins at its near end rather
     * than partway down it.
     */
    private static final java.util.Map<Settlement.Id, Paving> PAVING =
            new java.util.HashMap<>();

    /**
     * Which run a crew is on, which of its cross-sections is next, and which
     * they had to walk past.
     *
     * <p>A crew walks a run once and then goes back for what it could not reach
     * the first time. That second visit is the whole of {@code missed} and it is
     * needed because nothing else would ever lay those columns: the mending
     * sweep only re-lays a run once a <em>quarter</em> of it has gone, quite
     * deliberately — grass creeps back over a corner of a road constantly, and a
     * layer that repaved every blade would rewrite half the town every second —
     * so three bare columns in thirty are a road with holes in it that the sweep
     * will forgive for ever.
     *
     * <p>Once. A column still out of reach on the second visit is one nothing can
     * be done about from here, and a crew laid on it in perpetuity is a town that
     * opens no more streets.
     */
    private record Paving(SimPos from, int at, List<Integer> missed) {
    }

    /** Drops what the crews remember, for a world that is closing. */
    public static void forget() {
        PAVING.clear();
        WALKING.clear();
    }

    /**
     * Where this builder is wanted, which is not always the work's own answer.
     *
     * <p>A wall or a retired line hands out one position at a time and the town's
     * own books say which — the post it has paid for, the post it has pulled up.
     * A road has no such place to keep a count: a stretch is opened or it is not,
     * and everything between those two is the crew walking. So the walking is
     * kept here, and it is the one thing about a public work the settlement does
     * not know.
     */
    private static SimPos stationFor(ServerLevel level, Settlement settlement,
                                     Worksite work) {
        if (!(work instanceof PublicWorks.RoadWork road)) {
            return work.nextStation(settlement);
        }
        int index = road.nextRun(settlement);
        if (index < 0) {
            return null;
        }
        List<SimPos> along = settlement.paths().segments().get(index).positions();
        int column = columnOf(pavingOf(settlement, along), along.size());
        return column < 0 ? null : along.get(column);
    }

    /** The crew's place on this run, started fresh if the run is not the one they were on. */
    private static Paving pavingOf(Settlement settlement, List<SimPos> along) {
        Paving paving = PAVING.get(settlement.id());
        if (paving == null || !paving.from().equals(along.getFirst())) {
            paving = new Paving(along.getFirst(), 0, List.of());
            PAVING.put(settlement.id(), paving);
        }
        return paving;
    }

    /** Which cross-section is next: along the run, then back for what was walked past. */
    private static int columnOf(Paving paving, int length) {
        if (paving.at() < length) {
            return paving.at();
        }
        return paving.missed().isEmpty() ? -1 : paving.missed().getFirst();
    }

    /**
     * Moves the crew on from the cross-section they are at.
     *
     * @param reached whether they actually worked it, as opposed to walking past
     *                a column nobody could see or get to
     */
    private static void moveOn(Settlement settlement, PublicWorks.RoadWork work,
                               List<SimPos> along, boolean reached) {
        Paving paving = pavingOf(settlement, along);
        List<Integer> missed = new java.util.ArrayList<>(paving.missed());
        if (paving.at() < along.size()) {
            if (!reached) {
                missed.add(paving.at());
            }
            paving = new Paving(paving.from(), paving.at() + 1, List.copyOf(missed));
        } else if (!missed.isEmpty()) {
            // The second visit, whether or not it got there this time. A column
            // still out of reach is one nothing can be done about from here.
            missed.removeFirst();
            paving = new Paving(paving.from(), paving.at(), List.copyOf(missed));
        }
        if (columnOf(paving, along.size()) < 0) {
            work.finishStretch(settlement);
            PAVING.remove(settlement.id());
        } else {
            PAVING.put(settlement.id(), paving);
        }
    }

    /**
     * How long a crew keeps walking toward one station before giving it up.
     *
     * <p>A refused route is not the only way a station can be out of reach, and
     * it turns out to be the rarer one: vanilla's navigation answers yes to a
     * <em>partial</em> path, running to the nearest node it can get to, so a post
     * across a river or walled inside a building is a route made and a walk
     * begun that never arrives. Nothing about that is visible from here except
     * how long it has been going on.
     *
     * <p>Two minutes at a pass a second, which is far longer than any walk across
     * a town and short enough that a work is not lost. It matters more than it
     * sounds, because what the foreman returns is what tells the away sweeps to
     * stand aside: a crew walking for ever at a retired post is a settlement
     * standing inside two walls for ever, which is the one thing the work exists
     * to prevent.
     */
    private static final int WALK_PASSES_BEFORE_GIVING_UP = 120;

    /** How long each settlement's crew has been walking to one station. */
    private static final java.util.Map<Settlement.Id, Walk> WALKING =
            new java.util.HashMap<>();

    /** Which station a crew is walking to, and for how many passes. */
    private record Walk(String work, SimPos station, int passes) {
    }

    /**
     * Whether this station is still worth walking to, or has swallowed enough.
     *
     * <p>Counted rather than measured, because how far along a walk is is not a
     * question this can ask: a builder herded off by hunger, an alarm or a bed is
     * making no progress either, and all of those end on their own.
     */
    private static boolean stillWorthWalkingTo(Settlement settlement, Worksite work,
                                               SimPos station) {
        Walk walk = WALKING.get(settlement.id());
        if (walk == null || !walk.work().equals(work.name())
                || !walk.station().equals(station)) {
            WALKING.put(settlement.id(), new Walk(work.name(), station, 1));
            return true;
        }
        WALKING.put(settlement.id(), new Walk(work.name(), station, walk.passes() + 1));
        return walk.passes() < WALK_PASSES_BEFORE_GIVING_UP;
    }

    /**
     * Writes off a station nobody can get to, so the work can go on past it.
     *
     * <p>What "written off" means differs by work and each answer is the honest
     * one. A post nobody can reach is a position of line the town has whatever is
     * standing there — the same thing the clock records when it cannot place one,
     * and what {@code /civ wall} reports as missing. A retired post nobody can
     * reach is crossed off without salvage and left to the sweep, which has no
     * legs and does not care. A column of road is walked past.
     */
    private static void giveUpOn(Settlement settlement, Worksite work) {
        WALKING.remove(settlement.id());
        if (work instanceof PublicWorks.RoadWork) {
            skipColumn(settlement, work);
        } else {
            work.completeOne(settlement, false);
        }
    }

    /**
     * Walks a paving crew's place along a run past a column they cannot work.
     *
     * <p>Ground nobody has loaded, or a column no route reaches. Neither can be
     * waited on: the crew's place along a run is theirs alone, and the clock that
     * would otherwise open the street sees only the near end of it — so a crew
     * stopped at the twentieth column of a run whose first is loaded stalls a
     * town's whole network, since every later stretch queues behind the one that
     * is never opened. Walked past instead.
     *
     * <p>Nothing for any other work. A post is a place the town's own books name,
     * so a wall or a retired line that cannot be reached is simply left to the
     * sweep that owns it.
     *
     * <p>What was walked past is not abandoned: it is written into
     * {@link Paving#missed}, and the crew comes back down the run for it once
     * before the stretch is opened.
     */
    private static void skipColumn(Settlement settlement, Worksite work) {
        if (!(work instanceof PublicWorks.RoadWork road)) {
            return;
        }
        int index = road.nextRun(settlement);
        if (index < 0 || PAVING.get(settlement.id()) == null) {
            return;
        }
        moveOn(settlement, road,
                settlement.paths().segments().get(index).positions(), false);
    }

    /**
     * One cross-section of a run, paved, and the run opened at the far end of it.
     *
     * <p>The crossing goes in first and whole. A bridge is carpentry rather than
     * a sequence of independent columns — its arch is decided by the width of the
     * water, and half an arch holds nothing up — so it is laid with the run as it
     * always has been, and the crew then paves across it like any other ground.
     *
     * <p>A run the ground refuses is opened without being paved, which is what
     * the sweep did with one before roads were work: better a gap in the network,
     * which the town routes around and a player reads as untrodden ground, than a
     * builder pacing thirty columns of cliff face laying nothing.
     *
     * @return whether this cross-section is done with
     */
    private static boolean paveOne(ServerLevel level, Settlement settlement,
                                   PublicWorks.RoadWork work) {
        int index = work.nextRun(settlement);
        if (index < 0) {
            return false;
        }
        PathNetwork.Segment run = settlement.paths().segments().get(index);
        List<SimPos> along = run.positions();
        int at = columnOf(pavingOf(settlement, along), along.size());
        if (at < 0) {
            return false;
        }
        if (at == 0) {
            if (!PathLayer.canBePaved(level, run)) {
                work.finishStretch(settlement);
                PAVING.remove(settlement.id());
                return true;
            }
            Bridge.span(level, run);
        }
        PathLayer.paveAt(level, run, at);
        moveOn(settlement, work, along, true);
        return true;
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
     * @return whether nothing more is owed at this position, and whether a block
     *         actually went into the ground for it
     */
    private static Swing plantPost(ServerLevel level, Settlement settlement) {
        Perimeter perimeter = settlement.perimeter();
        if (perimeter == null) {
            return new Swing(true, false);
        }
        List<PerimeterLayer.Course> plan =
                PerimeterLayer.planAt(level, perimeter, perimeter.laid());
        PerimeterLayer.Course owed = PerimeterLayer.owed(level, plan);
        if (owed == null) {
            // The post stands, or this position is a gate's opening. Either way
            // there was nothing to do and nothing was spent doing it.
            return new Swing(true, false);
        }
        if (!PerimeterLayer.layByHand(level, owed)) {
            // A course the column refuses is a course nothing can lay: the line
            // runs through somebody's wall just here, which is a better wall than
            // a fence and is exactly what lineIsClosed forgives. Standing here
            // swinging at it for ever is the one outcome that helps nobody.
            return new Swing(true, false);
        }
        return new Swing(PerimeterLayer.owed(level, plan) == null, true);
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
     * dark oak in a forest is joined to a great many of its neighbors by
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
