package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The settlement's defensive ring: an ordered loop of vertices, the gates cut
 * into it, and how much of it has been raised so far.
 *
 * <p>This is the interface the whole perimeter subsystem works through, and it
 * is deliberately shape-agnostic: v1 stakes an axis-aligned rectangle around
 * the buildings ({@link PerimeterPlanner}), and the concave α-shape wall the
 * GOALS describe replaces only the staking — the walk order, the gates, the
 * laying cursor and the patrol all read the same loop either way.
 *
 * <p>The vertices double as the sentry's patrol nodes; the positions along the
 * loop are what the palisade layer stamps into the world.
 *
 * <p>A ring is very nearly for ever. A town whose suburbs have come to
 * outnumber the quarter inside the line re-stakes once, a generation later at
 * the earliest, and the wider ring carries the line it replaced in
 * {@link #retired()} until the world has taken its posts down — a settlement
 * has one wall at a time.
 */
public final class Perimeter {

    /** Blocks of clear opening cut around each gate's centre. */
    public static final int GATE_HALF_WIDTH = 1;

    /**
     * A line the town has replaced: where it ran, and how much of it stood.
     *
     * <p>The raised count is the half that keeps the demolition honest. Only
     * the first {@code laid} positions of a ring ever had a post put in them,
     * so those are the only ones there is anything to pull down at — and the
     * rest is ordinary ground that may since have had somebody's fence, field
     * or bridge built on it. A demolition that swept the whole loop would take
     * those away, having never put anything there.
     */
    public record Retired(List<SimPos> vertices, int laid) {
        public Retired {
            vertices = List.copyOf(vertices);
            laid = Math.max(0, laid);
        }
    }

    private final List<SimPos> vertices;
    private List<SimPos> gates;
    private int laid;
    private List<Retired> retired;

    /** Worked out once: a ring's shape never changes after it is staked. */
    private List<SimPos> retiredPositions;

    /**
     * The same, for the standing line, and for a stronger reason than tidiness.
     *
     * <p>{@link #ringPositions} is asked far more often than anything else here:
     * {@code Settlement.standsOnTheWall} walks the whole ring for every plot
     * {@code isPlotFree} is offered, which is hundreds of walks for one siting
     * decision, and {@code length()} and {@code closed()} walk it again every
     * step. The vertices are copied at construction and never replaced, so the
     * answer cannot go stale.
     */
    private List<SimPos> positions;

    /**
     * The step this line was staked on — how old the wall is.
     *
     * <p>Saved, unlike the scratch note it replaced, because it is the thing
     * that stops a wall being moved twice in one generation and a server
     * restart is not a generation. Zero is what every world saved before walls
     * had an age reads, and that is the honest answer for them: those towns
     * have stood inside the same line for as long as anybody has been counting.
     *
     * <p>Read through {@link #ageAt(long)} rather than subtracted directly. The
     * step it is compared against is not saved, and the difference matters.
     */
    private final long stakedOn;

    /** The step this line was staked on; zero for a wall of unremembered age. */
    public long stakedOn() {
        return stakedOn;
    }

    /**
     * How long this line has stood, as of this step.
     *
     * <p>Not simply {@code step - stakedOn()}, because {@code SimWorld} does not
     * save its step counter: it restarts at zero every time the server comes up,
     * while the stake step it is compared against comes out of the save file. A
     * wall staked at step nine hundred in the last session therefore reads as
     * staked nine hundred steps from <em>now</em>, and taken literally that is a
     * wall which can never be moved again on any world that has ever been
     * reloaded — a rule that quietly stops working, which is the worst kind.
     *
     * <p>So a stake in the future is read as what it is, a clock that restarted,
     * and the wall's honest age is then the age of the session: it was standing
     * when the session began, and nobody can say for how long before that. The
     * cooldown runs from the reload, which errs towards leaving the wall where
     * it is. Should the world clock ever start being saved, this degrades to the
     * plain subtraction on its own.
     */
    public long ageAt(long step) {
        return stakedOn > step ? step : step - stakedOn;
    }

    public Perimeter(List<SimPos> vertices, List<SimPos> gates, int laid) {
        this(vertices, gates, laid, List.of(), 0L);
    }

    public Perimeter(List<SimPos> vertices, List<SimPos> gates, int laid,
                     List<Retired> retired) {
        this(vertices, gates, laid, retired, 0L);
    }

    /**
     * A ring that replaced others, carrying them until they are pulled down.
     *
     * @param retired  the lines this ring supersedes, oldest first
     * @param stakedOn the step it was staked on, which is what its age is
     *                 measured from
     */
    public Perimeter(List<SimPos> vertices, List<SimPos> gates, int laid,
                     List<Retired> retired, long stakedOn) {
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("a perimeter is a loop, not a line");
        }
        this.vertices = List.copyOf(vertices);
        this.gates = List.copyOf(gates);
        this.laid = Math.max(0, laid);
        this.retired = List.copyOf(retired);
        this.stakedOn = Math.max(0L, stakedOn);
    }

    /** The loop's corners in walk order. Patrol nodes, verbatim. */
    public List<SimPos> vertices() {
        return vertices;
    }

    /** Openings in the ring — where the ways in and out are. */
    public List<SimPos> gates() {
        return gates;
    }

    /**
     * Moves the gates, which is only allowed while the wall is still going up.
     *
     * <p>A ring is staked before most of the town's roads exist, so the gates
     * it starts with are a guess. They are re-sited as the streets appear and
     * fixed the moment the wall closes — after that the posts are in the ground
     * and a gate is where it is.
     */
    public void setGates(List<SimPos> gates) {
        if (!closed()) {
            this.gates = List.copyOf(gates);
        }
    }

    /** Ring positions raised so far, counted along {@link #ringPositions()}. */
    public int laid() {
        return laid;
    }

    public void setLaid(int laid) {
        this.laid = Math.max(0, Math.min(laid, length()));
    }

    /** Whether the ring is complete. */
    public boolean closed() {
        return laid >= length();
    }

    /** Total positions on the loop, gates included (they are laid as openings). */
    public int length() {
        return ringPositions().size();
    }

    /**
     * Every position on the loop, in walk order, corner to corner.
     *
     * <p><strong>These are the posts, and they have to stand where the line was
     * staked.</strong> Everything that keeps the wall off a building reasons
     * about the straight stretch between two vertices — the keepouts
     * {@code Hull.concave} refuses to dig across, {@code PerimeterPlanner}'s
     * push-out and relaxation, the invariant {@code HullSimplicityTest} asserts
     * — and none of them ever looks at a post. So a walk that wanders off the
     * stretch it belongs to puts posts on ground nobody checked, and every one
     * of those rules is enforcing a line the town does not build.
     *
     * <p>It used to wander a long way. A leg was walked the whole of its x run
     * at the starting z and then the whole of its z run at the finishing x — an
     * L, which is exact for the axis-aligned rectangle v1 staked and no
     * further out than a block for any stretch that is nearly axis-aligned. The
     * concave hull stakes neither. On the sixteen-by-eight leg of a measured
     * ring the corner of that L stood <strong>seven blocks</strong> off the
     * straight line, which was seven blocks into the hearth: the checks looked
     * where the line ran and the posts went in through somebody's wall.
     * Measured across a hundred and seventeen grown towns, that accounted for
     * 689 of the 1247 buildings with a wall through them.
     *
     * <p>So the legs are walked along the line instead: still one block a step
     * and still axis-aligned, so a rectangle comes out exactly as it did and the
     * palisade stays a closed run nothing can squeeze through, and still
     * {@code |dx| + |dz|} posts to a leg, so a ring costs the same timber and
     * coin it always did. Only the order of the two runs changes — interleaved
     * by the leg's own gradient rather than one after the other — which keeps
     * every post within a block of the stretch it was staked on.
     *
     * <p><strong>A world saved before this does not migrate, and cannot from
     * here.</strong> {@link #laid} is an index into this walk and the columns it
     * names have moved, so a ring caught half-raised on an old save has its
     * built stretch recorded at posts nobody planted and its real posts — the
     * misplaced ones included — left standing with nothing pointing at them.
     * {@link Perimeter.Retired} carries the same wound. Nothing here can find
     * those columns again: the walk that produced them is gone, and putting it
     * back for old lines would only be right until the first line raised under
     * this one. A migration wants a save version and the old walk kept beside
     * this one to read it with, which is a decision about the save format
     * rather than about the wall. See GOALS.
     */
    public List<SimPos> ringPositions() {
        if (positions == null) {
            positions = List.copyOf(walk(vertices));
        }
        return positions;
    }

    private static List<SimPos> walk(List<SimPos> loop) {
        List<SimPos> ring = new ArrayList<>();
        for (int i = 0; i < loop.size(); i++) {
            SimPos from = loop.get(i);
            SimPos to = loop.get((i + 1) % loop.size());
            int runX = Math.abs(to.x() - from.x());
            int runZ = Math.abs(to.z() - from.z());
            int stepX = Integer.signum(to.x() - from.x());
            int stepZ = Integer.signum(to.z() - from.z());
            int x = from.x();
            int z = from.z();
            int goneX = 0;
            int goneZ = 0;
            while (goneX < runX || goneZ < runZ) {
                ring.add(new SimPos(x, from.y(), z));
                // Whichever of the two steps leaves the walk nearer the line.
                // The comparison is the two candidate half-steps cross-
                // multiplied against the leg's gradient, so it is exact and
                // never divides by a run that may be nought. Widened to long
                // because it is the one multiplication on this path: a plot can
                // be sited arbitrarily far out -- groundBeyondEverything moves
                // outward every time it is reached -- and an int product wraps
                // at a leg of some forty-six thousand blocks, which would step
                // the wrong axis and put posts where nothing checked, the exact
                // fault this walk exists to remove.
                if (goneZ >= runZ || (goneX < runX
                        && (2L * goneX + 1) * runZ < (2L * goneZ + 1) * runX)) {
                    x += stepX;
                    goneX++;
                } else {
                    z += stepZ;
                    goneZ++;
                }
            }
        }
        return ring;
    }

    /**
     * The lines this ring replaced, which are still standing in the world.
     *
     * <p>A town does not keep two walls. When a settlement outgrows its
     * palisade the wider ring supersedes the old one rather than joining it —
     * an inner fence line through the middle of a town is exactly the partition
     * that the concave hull work was done to remove, and it would be no better
     * for having been a wall once. So the old line travels with the new one
     * until whatever draws the world has pulled its posts down.
     *
     * <p>The list cannot run away with itself. A ring is replaced only when the
     * suburbs outnumber the town inside it, only when it has been paid for to
     * the last post, and only after {@code PerimeterPlanner.RESTAKE_COOLDOWN} —
     * so a settlement has at most one re-staking per age of the town however
     * long it lives. Measured over fourteen hundred steps on the rough seed:
     * twelve of the thirteen arrangements the mod builds in moved their line
     * exactly once and so carry exactly one retired loop, and the thirteenth,
     * whose first ring never closed, never moved it at all. The rule this
     * replaced retired four to seven loops in the same run.
     */
    public List<Retired> retired() {
        return retired;
    }

    /**
     * Every position a retired line actually raised that the standing ring is
     * not itself built on.
     *
     * <p>Two exclusions, and both are the difference between a demolition and
     * vandalism. The unraised tail of an old ring is ground the wall never
     * touched, so there is nothing of its there to take down and anything that
     * <em>is</em> there is somebody else's. And a superseded ring can share
     * ground with the one that replaced it: pulling down a post the drawing
     * puts straight back is the treadmill that has halted this wall twice
     * already.
     */
    public List<SimPos> retiredPositions() {
        if (retired.isEmpty()) {
            return List.of();
        }
        if (retiredPositions == null) {
            java.util.Set<Long> seen = new java.util.HashSet<>();
            for (SimPos post : ringPositions()) {
                seen.add(column(post));
            }
            List<SimPos> out = new ArrayList<>();
            for (Retired line : retired) {
                List<SimPos> walked = walk(line.vertices());
                int raised = Math.min(line.laid(), walked.size());
                for (int i = 0; i < raised; i++) {
                    SimPos post = walked.get(i);
                    if (seen.add(column(post))) {
                        out.add(post);   // and never twice, where two old lines met
                    }
                }
            }
            retiredPositions = List.copyOf(out);
        }
        return retiredPositions;
    }

    private static long column(SimPos at) {
        return ((long) at.x() << 32) ^ (at.z() & 0xffffffffL);
    }

    /** The old lines are down; stop carrying them. */
    public void forgetRetired() {
        retired = List.of();
        retiredPositions = List.of();
    }

    /** Whether this ring position is inside a gate's opening. */
    public boolean isGateway(SimPos pos) {
        for (SimPos gate : gates) {
            if (Math.abs(pos.x() - gate.x()) <= GATE_HALF_WIDTH
                    && Math.abs(pos.z() - gate.z()) <= GATE_HALF_WIDTH) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Perimeter other
                && vertices.equals(other.vertices)
                && gates.equals(other.gates)
                && laid == other.laid
                && stakedOn == other.stakedOn
                && retired.equals(other.retired);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vertices, gates, laid, stakedOn, retired);
    }
}
