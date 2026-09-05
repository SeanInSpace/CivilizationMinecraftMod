package com.kingdoms.sim.platform;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Footprint;

/**
 * The one seam between the simulation and the game.
 *
 * <p>Everything the simulation needs from Minecraft is declared here in terms of
 * plain Java and {@link SimPos}. The NeoForge module implements it; a Fabric
 * module could implement it later without the simulation changing at all.
 *
 * <p>If you are tempted to import {@code net.minecraft.*} into the sim, add a
 * method here instead.
 */
public interface WorldBridge {



    /**
     * Whether any living player is within {@code radius} blocks of this position.
     *
     * <p>This is the switch between the two fidelities. Far from every player,
     * people are records, travel is a timer, and combat resolves statistically.
     * Near one, the platform layer materializes the same state as entities and
     * blocks. The radius comes from settings so the caller decides the cutoff —
     * and can use a larger one for release than for spawn (hysteresis).
     */
    boolean playerWithin(SimPos pos, double radius);

    /** Whether the chunk containing this position is currently loaded. */
    boolean isLoaded(SimPos pos);

    /**
     * The ground level at this column: the y of the first air block above the
     * terrain, ignoring foliage. Returns {@code pos.y()} unchanged when the chunk
     * is not available — callers treat the result as best-effort, and the world
     * snaps to real terrain again at materialization time.
     */
    int surfaceHeight(SimPos pos);

    /**
     * The ground height here, answered even where the world is not loaded.
     *
     * <p>{@link #surfaceHeight} deliberately returns the position's own y for an
     * unloaded column, so a plot keeps the height it was given and the world
     * snaps it properly at placement. That is right for siting one building and
     * useless for judging a route: every point of a planned street carries the
     * town center's y, so an unloaded hillside reads back as a table top. A
     * slope check written against it refused nothing at all — 155 runs of a
     * measured town climbed more than a block a step, one of them by 29.
     *
     * <p>This is the estimate instead: certain where the chunk is read, the
     * generator's own noise everywhere else. An estimate of a cliff is worth
     * more than a confident report of level ground.
     */
    default int groundHeight(SimPos pos) {
        return surfaceHeight(pos);
    }

    /**
     * Whether this ground is a river or the sea.
     *
     * <p>Split out from {@link #isSiteSuitable} because it is the one terrain
     * fact that is never a preference. A site can be steep, or wooded, or a long
     * way from a road, and a town short of room may still take it — that is what
     * "builds on poor ground rather than giving up" means. It may not take open
     * water. A building standing in a river reads as broken whatever else is
     * true of it, and the settlement always has somewhere else to go.
     *
     * <p>Default false, so a platform that cannot tell simply never refuses.
     */
    default boolean standsInWater(SimPos pos, int radius) {
        return false;
    }

    /**
     * Place a completed building into the world.
     *
     * <p>Safe to call when unobserved: implementations should record the change
     * and apply it when the chunk next loads, so that settlements which grew
     * while the player was away appear already built.
     *
     * <p>Returns where it was actually placed and how big it turned out, or
     * {@link Footprint#UNKNOWN} if nothing was placed. An unsurveyed origin carries a guess, and the caller
     * needs the real answer back — otherwise workers keep walking to a height the
     * building is not at.
     *
     * <p>{@code surveyed} says whether the origin's height was measured against
     * real terrain. When it was, the implementation must place at exactly that
     * height and not re-measure — otherwise a structure the builders had already
     * started gets stamped in a second time at a different level, and you get two
     * of it. When it was not, the height is a guess and re-measuring is the whole
     * point.
     */
    Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed,
                                   int facing);

    /**
     * Puts back the blocks a standing building is missing, and touches nothing else.
     *
     * <p><strong>Not {@link #materializeBlueprint}, and the difference is the
     * whole of this method.</strong> Materializing is the FIRST drawing of a
     * building that was finished where nobody could see it: the ground is bare,
     * so the whole blueprint goes down at once and what a player sees is a
     * building appearing where there was none. A repair is the opposite premise —
     * the building is there, somebody has knocked a hole in it, and the only
     * blocks that may be touched are the ones that differ from the plan. Running
     * the second through the first is what re-stamped an entire cottage to mend
     * its roof, twenty times in four minutes, with no builder anywhere near it.
     *
     * <p>Only ever the unwatched half of a repair. Where there are builders they
     * lay these same blocks by hand, one at a time, out of loads they fetch from
     * the stores, and nothing calls this at all — where there is a hand there is
     * no clock.
     *
     * <p>No {@code surveyed} flag, because the question cannot arise: a building
     * that can be repaired is a building that has been drawn, so its origin is
     * the height it actually stands at and re-measuring could only move it.
     *
     * <p>Default zero rather than {@code -1}: a platform with no world at all
     * mends nothing and there is no hole for it to lose track of, so a repair
     * booked against it should finish rather than stand on the books forever.
     *
     * @return how many blocks were put back, or {@code -1} where nobody could
     *         look — the same distinction {@link #solidBlocksIn} draws, and for
     *         the same reason. "I put nothing back because nothing was missing"
     *         and "I put nothing back because the ground is not there to be
     *         written to" are opposite facts, and {@code Settlement.mendInPlace}
     *         clears the building's damage on the first and must not on the
     *         second: doing so writes off a hole the town has already paid to
     *         fill, and re-baselines the census against the shell so the hole
     *         becomes the building's proper size for good.
     */
    default int repairBlueprint(String blueprintId, SimPos origin, int facing) {
        return 0;
    }

    /**
     * Whether a plot is fit to build on.
     *
     * <p>Plots are handed out by geometry alone — rings around the center — which
     * is why a settlement will otherwise cheerfully put a house in a lake or across
     * a ravine. This is the veto: too much slope, standing water, or a drop, and
     * the town takes the next plot instead.
     *
     * <p>Answers {@code true} when the chunk is not loaded and nothing can be
     * judged. A guess would be worse than deferring: the site is surveyed again for
     * real before a single block is laid.
     *
     * <p>Default {@code true} so test doubles stay small.
     */
    default boolean isSiteSuitable(SimPos plot, int radius) {
        return true;
    }

    /**
     * A plot the strict test passes outright.
     *
     * <p>Zero, so that a bare {@code fault == SITE_FAULT_NONE} reads as "this
     * ground is fine" everywhere it is written.
     */
    int SITE_FAULT_NONE = 0;

    /**
     * Ground that is not poor, but is not ground.
     *
     * <p>A sentinel rather than a large number, and it has to be one: every
     * other fault is a quantity a desperate town may weigh against another
     * quantity, and this is the one answer that is never comparable. A building
     * in a river reads as broken however sound it is, so a caller ranking
     * candidates drops these rather than sorting them.
     *
     * <p>{@code MAX_VALUE} also means arithmetic on a fault must check for it
     * before adding anything — see the tests' cruel ground, which adds its own
     * charge on top of the terrain's.
     */
    int SITE_FAULT_OPEN_WATER = Integer.MAX_VALUE;

    /**
     * How badly this ground fails, rather than whether it fails.
     *
     * <p>{@link #isSiteSuitable} is a veto, and a veto is all a settlement had.
     * When every candidate is vetoed the search has nothing left to prefer, so
     * it used to walk past all ninety-six examined plots and take an unexamined
     * one — which meant every improvement to the terrain rules bought more blind
     * placements, and a stricter water test measurably put <em>more</em> houses
     * in lakes. A town out of good ground should take the least-bad plot it
     * looked at; to do that it has to be able to say which one that was.
     *
     * <p>So this is the same judgment, scored. Zero exactly when
     * {@code isSiteSuitable} passes — implementations must keep the two in step,
     * or a settlement treats perfectly good ground as a compromise, or walks
     * onto ground the veto would have refused. Above zero it is a quantity in
     * courses: how far past what a builder will cut the ground falls, plus what
     * standing water in the plot is worth. {@link #SITE_FAULT_OPEN_WATER} for
     * the one thing that is never a preference.
     *
     * <p>Derived from the veto by default, so a bridge that has never thought
     * about degrees still answers usefully: the ground it refuses all scores the
     * same, and a caller ranking candidates falls back to the nearest of them.
     */
    default int siteFault(SimPos plot, int radius) {
        if (standsInWater(plot, radius)) {
            return SITE_FAULT_OPEN_WATER;
        }
        return isSiteSuitable(plot, radius) ? SITE_FAULT_NONE : SITE_FAULT_UNGRADED;
    }

    /**
     * What a refusal is worth when the bridge cannot say how bad it was.
     *
     * <p>Any positive number does the job — a bridge either grades every plot or
     * grades none of them, so this is never compared against a real score. It is
     * a course of fall so that a reader meeting it in a log has the right unit
     * in mind.
     */
    int SITE_FAULT_UNGRADED = 1;

    /**
     * Whether a site this refused could be made buildable by leveling it.
     *
     * <p>{@link #isSiteSuitable} says no and does not say why, and the reasons
     * are not alike: a lake cannot be filled with a barrow of earth and a
     * hummock can. Only the thing that applied the rule knows which it was, so
     * it is asked rather than guessed at — the first attempt at this had the
     * simulation infer "not wet, therefore levelable" and promptly put a house
     * in a lake, because the ground it was testing against reported water
     * through {@code isSiteSuitable} and nothing else.
     *
     * <p>False by default, deliberately. A bridge that has not thought about
     * leveling keeps exactly the behavior it had, and a fake written for some
     * other purpose cannot accidentally license a town to flatten the sea.
     */
    default boolean isSiteLevellable(SimPos plot, int radius) {
        return false;
    }

    /**
     * How wooded this ground is, from 0 to 100.
     *
     * <p>The one thing siting a lumber camp needs and the simulation cannot
     * know: it has no notion of where trees are. The work area was derived
     * *from* the camp rather than the camp from the trees, which is the wrong
     * way round — a camp put on open grass claims a circle of open grass and
     * then has nothing to fell.
     *
     * <p>Zero when nothing is loaded, which reads as "no reason to prefer this
     * spot" rather than "definitely bare". A guess would be worse: the plot is
     * surveyed again for real before a block is laid.
     *
     * <p>Default zero so test doubles stay small; real platforms should answer.
     */
    /**
     * How many solid blocks stand inside this building's footprint right now.
     *
     * <p>The measurement damage is judged from — counted rather than inferred,
     * so it does not matter whether the blocks were taken by a creeper, a fire,
     * a player with a pickaxe or another mod entirely.
     *
     * <p>Returns a negative number when the answer cannot be had: nothing loaded,
     * nobody there to look. That is not the same as zero, and callers must not
     * treat it as a building reduced to rubble — an unwatched building is not
     * decaying, it is merely unobserved.
     */
    default int solidBlocksIn(SimPos origin, Footprint plot) {
        return -1;
    }

    default int woodedness(SimPos centre, int radius) {
        return 0;
    }

    /** Structured logging that does not depend on a specific logging backend. */
    void log(String message);

    /**
     * How many hostile creatures the town's own people can actually see.
     *
     * <p>Seen, not merely present. This used to count every hostile in a box
     * thirty-two blocks deep, which meant a zombie in a cave under the town
     * hall — invisible, unreachable, and never coming up — emptied the streets
     * and kept them empty for as long as it lived. A town can only be frightened
     * of what it knows about.
     *
     * <p>Weighted, not counted. Four zombies and four creepers are not the same
     * news, so the platform scores each creature by what it is worth and reports
     * the total alongside the head count. See the platform's own danger table.
     *
     * <p>Nothing when nothing is loaded, which is exactly right: abstract
     * fidelity has no real hostiles and no real eyes, so threat there comes from
     * the raid system instead. Default provided so test doubles stay small; real
     * platforms must override.
     */
    default Sighting hostilesSeen(SimPos centre, double radius) {
        return Sighting.NONE;
    }

    /**
     * Spawn a hostile raiding party around this position — the observed-fidelity
     * half of a raid. Called only when a player is close enough to watch; entity
     * combat decides the outcome from there. Default no-op for test doubles;
     * real platforms must override.
     */
    default void spawnHostiles(int count, SimPos around) {
    }
}
