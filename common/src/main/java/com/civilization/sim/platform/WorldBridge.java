package com.civilization.sim.platform;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Footprint;

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
     * The same, reporting what the ground it cleared was worth.
     *
     * <p>Drawing a building unwatched clears the same trees, ground cover and
     * hillside a crew would have dug by hand, and that material belongs to the
     * town either way — a plot is not worth less timber because nobody stood and
     * watched it come off. So the clearing is counted as it happens and handed
     * back through {@code spoil}, one call per resource.
     *
     * <p>Default: the four-argument placement and no accounting, for a platform
     * with nothing to count. See {@link #countsSpoil}.
     */
    default Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed,
                                           int facing,
                                           java.util.function.ObjIntConsumer<String> spoil) {
        return materializeBlueprint(blueprintId, origin, surveyed, facing);
    }

    /**
     * What a hand-authored file put in a building that the tables cannot say.
     *
     * <p>Null for a building the code drew, which is nearly all of them, and null
     * for a platform that has never heard of a blueprint file. See
     * {@link Building.Authored} for what it is and why the simulation has to be
     * told rather than allowed to look: a drawn building's beds and door are in a
     * table both halves read, and somebody else's building's are wherever they
     * built them.
     *
     * <p>Asked once per building, at the moment its plan is real — when the
     * builders finish it, or when an unwatched one is finally drawn. Answering
     * null on ground nobody has loaded is correct and expected; the question is
     * asked again on the step the building becomes visible.
     */
    default Building.Authored authoredFacts(String blueprintId, SimPos origin, int facing) {
        return null;
    }

    /**
     * Whether this platform can say what clearing a site actually yielded.
     *
     * <p>The simulation keeps an estimate for the ones that cannot — a course of
     * earth over the footprint, which is what pays to level the next awkward plot
     * — and must not credit it on top of a real count, or a town is paid twice
     * for the same hole. Default false: a bridge that has never heard of a block
     * keeps exactly the behavior it had.
     */
    default boolean countsSpoil() {
        return false;
    }

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
     * Grows trees on ground a seeded town's forester is supposed to have been
     * working.
     *
     * <p>Grown trees, not saplings, and that is the whole point: the camp has
     * been standing since before the first step, so its wood has had years to
     * come up. A seeded town handed saplings is a town with no timber for as
     * long as it takes them to grow, which is the fault this exists to fix.
     *
     * <p>The positions are candidates in the order the simulation prefers them —
     * see {@code ForesterStand.candidates} — and there are deliberately more of
     * them than are wanted. The platform walks the list, skips every square the
     * ground refuses (water, stone, a cliff, a canopy already there), and stops
     * once it has planted {@code wanted}. It never levels, fills or clears
     * anything to make a square usable: a camp on a lakeshore simply gets a
     * smaller stand, which is what a camp on a lakeshore would have.
     *
     * <p>Default zero, so a platform with no world plants nothing and says so.
     *
     * @param spots  candidate trunk positions, nearest the camp first
     * @param wanted how many trees to stop at
     * @return how many actually went in
     */
    default int plantGrownTrees(java.util.List<SimPos> spots, int wanted) {
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
    default boolean isSiteLevelable(SimPos plot, int radius) {
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

    /**
     * Make a field's crops say what the simulation says is standing in it.
     *
     * <p>Called once, on the step a farm goes from nobody-near-it to somebody-
     * standing-in-it. While a field is unwatched, {@code Field}'s ledger is the
     * only record of how much has ripened; the blocks themselves are wherever
     * they were left, and vanilla has been growing them or not depending on
     * whether the chunk happened to stay loaded. The two disagree, and the
     * ledger wins — it is what fed the town while nobody was looking.
     *
     * <p>So: set exactly {@code ripeBlocks} of the field's crops to mature and
     * the rest back to young, in whatever stable order the platform likes. From
     * that moment the real hands take over and the ledger only mirrors them.
     *
     * <p>No-op by default. A bridge with no world behind it has no crops to
     * disagree with, and every test double is one.
     */
    default void setFieldRipeness(SimPos farmOrigin, Footprint plot, int ripeBlocks) {
    }

    default int woodedness(SimPos center, int radius) {
        return 0;
    }

    /**
     * How many trees are actually standing inside a lumber camp's claim.
     *
     * <p>The measurement a camp's {@code Stand} is sized from, and the reason
     * timber is no longer a percentage of anything: a camp raised in a forest
     * has a woodland to fell and a camp raised on a meadow does not, and the
     * simulation cannot tell the difference on its own. Counted rather than
     * inferred, so it does not matter whether the trees grew there, were planted
     * by a seeded town's forester, or were left standing by a player who cleared
     * the rest.
     *
     * <p>A trunk, not a log: whole trees, however tall each one happens to be.
     * Sampled rather than scanned and read only out of chunks that are already
     * loaded — nothing here loads one — so a claim half of which is out of range
     * reads as the half that could be seen, scaled.
     *
     * <p>Zero when nothing is loaded and zero by default, which is honest for a
     * bridge with no world behind it. Callers must not treat that as "the wood
     * has been felled": see {@code Stand.UNCOUNTED}, and ask only when
     * {@link #isLoaded} says the ground can answer.
     */
    default int countTreesNear(SimPos center, int radius) {
        return 0;
    }

    /**
     * How much stone is actually under a mine head, in blocks.
     *
     * <p>The measurement a mine's {@code Seam} is sized from. A mine sunk into a
     * mountainside is worth a great deal and a mine sunk into a superflat is
     * worth almost nothing, and the mine that ran on a yield table could not
     * tell the two apart. Counted down to {@code depth} below the head, because
     * that is as far as {@code MinerWorker} will ever cut.
     *
     * <p>Sampled rather than scanned, loaded chunks only. Zero by default so
     * test doubles stay small; a real platform answers, and answers
     * {@code Seam.UNSURVEYED} rather than zero where it cannot read the ground,
     * because a mine that nobody could survey is a mine of unknown worth rather
     * than an empty one.
     */
    default int countStoneBelow(SimPos center, int radius, int depth) {
        return 0;
    }

    /**
     * How many meals' worth of wild food is actually growing around here.
     *
     * <p>The counterpart to {@link #woodedness}, and it exists for the same
     * reason: the simulation has no idea what is on the ground. Foraging used
     * to be a headcount divided by three, which meant a camp pitched on bare
     * superflat and a camp in a berry-thick taiga ate exactly the same, and a
     * party in the middle of a desert lived on sand indefinitely. If it is a
     * superflat world, what are they foraging?
     *
     * <p>So this is the answer: a count, in whole meals, of the edible things
     * a forager could reach within {@code radius} of the camp — berry bushes,
     * mushrooms, wild wheat and beetroot and carrots and potatoes nobody
     * planted, melons and pumpkins, and apples under oak leaves at the rate
     * apples actually fall. Grass yields seeds at a rate barely worth counting.
     * Cactus is not food.
     *
     * <p>A standing quantity, not a rate: it is what is <em>there</em>, and the
     * caller is expected to remember what it has already taken and let it grow
     * back. Sampled rather than scanned, and only from chunks that happen to be
     * loaded — nothing here loads one.
     *
     * <p>Zero when nothing is loaded and zero by default, which is the honest
     * reading for a bridge with no world behind it: a fake forages nothing
     * unless it says otherwise.
     */
    default int forageableNear(SimPos center, int radius) {
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
    default Sighting hostilesSeen(SimPos center, double radius) {
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

    /**
     * The same, for a raid that came out of a settlement somebody could walk to.
     *
     * <p>{@link #spawnHostiles} spawns zombies, which is the honest picture of a
     * raid whose only existence is a hash of a step number. A goblin camp is not
     * that: it has a name, a position, a chieftain and a heap of stolen goods, and
     * a player who watches its raid arrive should see goblins.
     *
     * <p>So the raider's own name is handed over and the platform spawns whatever
     * it stands for. Defaulting to {@link #spawnHostiles} rather than to nothing is
     * deliberate: a platform that has not learned about goblins yet still puts a
     * fight in front of the player, which is very much better than a raid that is
     * logged and invisible.
     *
     * @param cultureId whose party this is, as {@code Culture} names it, so the
     *                  platform knows what body to give it
     */
    default void spawnRaiders(int count, SimPos around, String cultureId) {
        spawnHostiles(count, around);
    }

    /**
     * Which players are standing near this point, by id.
     *
     * <p>{@link #playerWithin} answers whether <em>anybody</em> is about, which
     * is the fidelity switch and deliberately anonymous — the simulation does
     * not care who is watching a town grow. This asks the other question: a
     * quest given to one person is finished by that person walking to the mark,
     * and "somebody is there" is not that.
     *
     * <p>Empty by default, and that is an honest answer rather than a stub: a
     * platform with no notion of players has nobody standing anywhere, and a
     * quest that waits for an arrival simply waits.
     */
    default java.util.List<java.util.UUID> playersWithin(SimPos pos, double radius) {
        return java.util.List.of();
    }
}
