package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The arrangements a people can lay a town out in.
 *
 * <p>Three, and they are meant to look nothing like one another from the air.
 * That is the point: the claim that a second culture is "a table entry rather
 * than new code" is only worth making if the table can express a town that is
 * genuinely planned differently, not the same rings with different timber.
 *
 * <ul>
 *   <li>{@link #RING} — concentric rings around a centre. Orderly, evenly
 *       spaced, growing outward. A village that expects to still be there in a
 *       hundred years.</li>
 *   <li>{@link #WARREN} — tight knots of buildings with open ground between the
 *       knots. Nothing lines up. Grows by budding a new clump off the last one
 *       rather than by widening a circle.</li>
 *   <li>{@link #STRONGHOLD} — a square grid filled in a spiral from the middle.
 *       Regimented, dense, and unmistakably laid out by somebody who thinks in
 *       rows.</li>
 * </ul>
 */
public final class Layouts {

    private Layouts() {
    }

    /**
     * Concentric rings — what every town has always done.
     *
     * <p>Lifted out of {@code BuildPlanner} unchanged, down to the half-slot
     * stagger on alternate rings. That stagger is not decoration: a constant
     * eight plots per ring produced an eight-legged star, the same angles
     * repeated at every radius, and packing by circumference is what fills the
     * ground near the town before stepping outward.
     */
    public static final Layout RING = new Layout() {
        static final int MIN_SLOTS_PER_RING = 8;
        static final int FIRST_RING_RADIUS = 12;
        static final int RING_SPACING = 16;
        static final int TARGET_PLOT_SPACING = 16;

        @Override
        public String id() {
            return "ring";
        }

        int slotsInRing(int ring) {
            int radius = FIRST_RING_RADIUS + ring * RING_SPACING;
            int byCircumference = (int) Math.floor(2 * Math.PI * radius / TARGET_PLOT_SPACING);
            return Math.max(MIN_SLOTS_PER_RING, byCircumference);
        }

        @Override
        public SimPos plotFor(SimPos centre, int index) {
            int ring = 0;
            int slot = Math.max(0, index);
            while (slot >= slotsInRing(ring)) {
                slot -= slotsInRing(ring);
                ring++;
            }
            int slots = slotsInRing(ring);
            int radius = FIRST_RING_RADIUS + ring * RING_SPACING;
            double slice = 2 * Math.PI / slots;
            double angle = slot * slice + (ring % 2) * slice / 2;
            return new SimPos(
                    centre.x() + (int) Math.round(radius * Math.cos(angle)),
                    centre.y(),
                    centre.z() + (int) Math.round(radius * Math.sin(angle)));
        }
    };

    /**
     * Knots of buildings with open ground between them.
     *
     * <p>Six plots to a clump, packed at the closest separation the siting code
     * will tolerate, and the clumps themselves flung out along a widening spiral
     * far enough apart that the gaps read as gaps. The effect from above is a
     * scatter of tight little settlements that happen to share a name, which is
     * about right for a people who dig in wherever the digging is good rather
     * than laying out a high street.
     *
     * <p>The clump angle advances by a turn that is deliberately not a neat
     * fraction of a circle, so clumps never line up into spokes — the same
     * mistake the ring layout made once and had to be taught out of.
     */
    public static final Layout WARREN = new Layout() {
        static final int PER_CLUMP = 6;

        /**
         * How far a hut sits from the middle of its own knot.
         *
         * <p>Was thirteen, on the reasoning that six around a circle of
         * thirteen puts neighbours thirteen apart and so clears
         * {@link Layout#MIN_PLOT_SEPARATION} with a block to spare. Both halves
         * of that were true and it was still wrong: the separation is measured
         * on the wider axis, not as a distance, and those neighbours sit six
         * across and eleven deep. Eleven is inside the box, so one hut of every
         * pair was refused.
         *
         * <p>What that cost, measured against the same seed and the same nine
         * hundred steps: <strong>26 people to the ring layout's 96</strong>, on
         * forty buildings to its hundred and thirteen, sprawling nearly twice as
         * far because the plot cursor ran outward hunting for ground it kept
         * being refused.
         */
        static final int CLUMP_RADIUS = 16;

        /**
         * How far the second knot sits from the first.
         *
         * <p>Has to clear two knot radii plus a plot separation, or huts on the
         * facing edges of neighbouring knots overlap — which is exactly what the
         * first draft of this did, putting plots 2 and 6 ten blocks apart. The
         * worst pair under the old numbers was between knots rather than inside
         * one, so widening the knot alone never fixed it; these three constants
         * are solved together.
         *
         * <p>Solved for two things, not one. The tightest set that merely clears
         * the box pulls the knots in until huts in neighbouring knots sit closer
         * than huts in the same one — at which point there are no knots, just a
         * scatter, and the layout has been repaired into meaninglessness. So the
         * search also keeps the nearest hut in another knot further off than the
         * nearest hut at home, which is the only thing that makes a knot legible
         * from above.
         *
         * <p>And solved over the town that <em>exists</em>. The first attempt
         * minimised the spread of three hundred plots, which bought a tight tail
         * by pushing the first three knots further out — and no warren has ever
         * reached the tail. A measured town of twenty-nine buildings uses knots
         * nought to four and nothing beyond, so every knot it had was further
         * from home than before and the town came out <em>smaller</em>: fifteen
         * people where the broken geometry managed twenty-six. Correct by the
         * invariant, worse by the outcome. These numbers are solved for the
         * first thirty plots instead, and span 96 blocks against the original's
         * 104.
         */
        static final int FIRST_CLUMP_OUT = 52;

        static final int CLUMP_SPREAD = 20;

        /** Two fifths of a turn: never repeats a spoke, and looks unplanned. */
        static final double CLUMP_TURN = 2.399963;

        @Override
        public String id() {
            return "warren";
        }

        @Override
        public int claimMargin() {
            return 16;   // the clumps sprawl; the claim has to cover the outliers
        }

        @Override
        public SimPos plotFor(SimPos centre, int index) {
            int at = Math.max(0, index);
            int clump = at / PER_CLUMP;
            int within = at % PER_CLUMP;

            // The first clump sits on the town centre itself, so a young warren
            // is one dense knot rather than a ring of huts around nothing.
            int clumpX = centre.x();
            int clumpZ = centre.z();
            if (clump > 0) {
                double out = FIRST_CLUMP_OUT + (clump - 1) * CLUMP_SPREAD / 2.0;
                double angle = clump * CLUMP_TURN;
                clumpX += (int) Math.round(out * Math.cos(angle));
                clumpZ += (int) Math.round(out * Math.sin(angle));
            }

            double slice = 2 * Math.PI / PER_CLUMP;
            // Each clump turned a little against the last, so two neighbouring
            // knots never present the same face to each other.
            double angle = within * slice + clump * slice / 3.0;
            return new SimPos(
                    clumpX + (int) Math.round(CLUMP_RADIUS * Math.cos(angle)),
                    centre.y(),
                    clumpZ + (int) Math.round(CLUMP_RADIUS * Math.sin(angle)));
        }
    };

    /**
     * A square grid, filled outward from the middle.
     *
     * <p>Rows and columns on a fixed pitch, taken in a square spiral so the
     * middle fills before the edges. Nothing is staggered and nothing is
     * curved. A town laid out by somebody who counts.
     *
     * <p>The centre cell itself is skipped — that is where the hall goes, and a
     * layout that offered it as an ordinary plot would have the town build a
     * hut on its own square.
     */
    public static final Layout STRONGHOLD = new Layout() {
        static final int PITCH = 18;

        @Override
        public String id() {
            return "stronghold";
        }

        @Override
        public SimPos plotFor(SimPos centre, int index) {
            // Walk the square spiral, counting only the cells that are offered.
            int x = 0;
            int z = 0;
            int dx = 1;
            int dz = 0;
            int legLength = 1;
            int stepsOnLeg = 0;
            int legsDone = 0;
            int offered = -1;
            // Bounded by construction: every iteration takes one step, and the
            // spiral reaches every cell eventually.
            for (int guard = 0; guard < 1_000_000; guard++) {
                if (!(x == 0 && z == 0)) {
                    offered++;
                    if (offered == Math.max(0, index)) {
                        return new SimPos(centre.x() + x * PITCH, centre.y(),
                                centre.z() + z * PITCH);
                    }
                }
                x += dx;
                z += dz;
                stepsOnLeg++;
                if (stepsOnLeg == legLength) {
                    stepsOnLeg = 0;
                    int turnX = -dz;
                    dz = dx;
                    dx = turnX;
                    legsDone++;
                    if (legsDone % 2 == 0) {
                        legLength++;
                    }
                }
            }
            throw new IllegalStateException("stronghold spiral never reached index " + index);
        }
    };

    /**
     * Dart-thrown plots with a guaranteed gap: blue noise.
     *
     * <p>Every other arrangement here is a <em>lattice</em> — rings, knots on a
     * spiral, a square grid — and each one has had the same fault, which is that
     * its spacing is a consequence of its arithmetic rather than a promise. The
     * ring's innermost course cannot hold its own plots. The warren put six huts
     * on a circle whose neighbours fell inside the overlap box, and a third of
     * every goblin town was thrown away for years because of it. Both were
     * "fixed" by choosing better constants, which is a repair that lasts exactly
     * until somebody chooses a different constant.
     *
     * <p>This one cannot have that fault. A position is only returned once it has
     * been <em>checked</em> against every position already given out, so
     * {@link Layout#MIN_PLOT_SEPARATION} is the algorithm rather than a number
     * somebody has to get right. The overlap check downstream can still refuse a
     * plot for the ground it sits on; it can no longer refuse one for sitting on
     * its neighbour.
     *
     * <p>The three rules are kept the way the others keep them. <b>Deterministic:</b>
     * the dart throws come from a hash of the town's own centre, so the same town
     * is the same town on every reload and in every test. <b>Injective:</b> two
     * plots a clear twelve apart are not the same plot. <b>Roomy:</b> by
     * construction, above.
     *
     * <p>It fills outward, because a town does: the radius each dart may land in
     * grows with the square root of how many plots have been handed out, which
     * keeps the density even instead of piling the middle or racing for the edge.
     */
    public static final Layout ORGANIC = new Layout() {

        /** Nothing is offered inside this: the hall and its yard live here. */
        static final int HEART = 15;

        /** Darts thrown at one radius before the town is allowed to reach further. */
        static final int THROWS = 48;

        /** Towns whose sequences are kept. More than a handful is a server's worth. */
        static final int TOWNS_REMEMBERED = 8;

        @Override
        public String id() {
            return "organic";
        }

        @Override
        public SimPos plotFor(SimPos centre, int index) {
            List<SimPos> seq = sequenceFor(centre, Math.max(0, index) + 1);
            return seq.get(Math.max(0, index));
        }

        /**
         * The town's plots in order, generated as far as asked and remembered.
         *
         * <p>Kept because the sequence is defined by everything before it: plot
         * four hundred is only knowable by having placed the three hundred and
         * ninety-nine before it. Recomputing that for every candidate a
         * settlement weighs would be the same work a hundred times over.
         */
        private List<SimPos> sequenceFor(SimPos centre, int wanted) {
            String key = centre.x() + ":" + centre.z();
            synchronized (REMEMBERED) {
                List<SimPos> seq = REMEMBERED.get(key);
                if (seq == null) {
                    seq = new ArrayList<>();
                    REMEMBERED.put(key, seq);
                    if (REMEMBERED.size() > TOWNS_REMEMBERED) {
                        Iterator<String> it = REMEMBERED.keySet().iterator();
                        it.next();
                        it.remove();
                    }
                }
                extend(centre, seq, wanted);
                return seq;
            }
        }

        /**
         * Bridson's method: grow outward from what is already placed.
         *
         * <p>The first draft threw darts uniformly into a disc that widened with
         * the count, and it sprawled — measured against rings on the same seed
         * and the same population, 435 blocks of spread against 268, and a ring
         * wall half again as long. The reason is the whole difference between
         * rejection sampling and Poisson-disk: once the middle is full, a
         * uniform dart lands there and fails, over and over, until the code
         * widens the disc to find room. The town ends up hollow and huge.
         *
         * <p>So candidates are thrown into the ring between one and two
         * separations of a plot already placed. Every throw is next to somebody,
         * so the town packs instead of spreading, and a plot that runs out of
         * room around it retires rather than pushing the whole town outward.
         */
        private void extend(SimPos centre, List<SimPos> seq, int wanted) {
            long seed = (long) centre.x() * 0x9E3779B97F4A7C15L
                    ^ (long) centre.z() * 0xC2B2AE3D27D4EB4FL;
            for (SimPos placed : seq) {
                seed ^= (long) placed.x() * 31 + placed.z();   // resume where we left off
            }
            List<Integer> active = new ArrayList<>();
            for (int i = 0; i < seq.size(); i++) {
                active.add(i);
            }
            if (seq.isEmpty()) {
                seq.add(new SimPos(centre.x() + HEART, centre.y(), centre.z()));
                active.add(0);
            }
            // Bounded, because the alternative is a hang. The first version
            // re-added the last plot whenever the active list emptied, threw its
            // darts, failed, removed it, and re-added the same plot again --
            // forever, if that plot happened to be hemmed in. A layout that can
            // spin is worse than one that spreads: this one takes the honest way
            // out and starts a fresh knot beyond everything placed so far.
            int stuck = 0;
            while (seq.size() < wanted) {
                if (active.isEmpty()) {
                    if (++stuck > RESTARTS) {
                        seq.add(beyond(centre, seq));
                        active.add(seq.size() - 1);
                        stuck = 0;
                        continue;
                    }
                    // Everybody is hemmed in. Try again from the newest plot,
                    // whose darts are thrown fresh each time.
                    active.add(seq.size() - 1);
                }
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                int pick = (int) Math.floorMod(seed >>> 17, active.size());
                SimPos from = seq.get(active.get(pick));
                SimPos found = null;
                for (int attempt = 0; attempt < THROWS && found == null; attempt++) {
                    seed = seed * 6364136223846793005L + 1442695040888963407L;
                    double angle = ((seed >>> 11) / (double) (1L << 53)) * Math.PI * 2;
                    seed = seed * 6364136223846793005L + 1442695040888963407L;
                    double away = MIN_SEP + ((seed >>> 11) / (double) (1L << 53)) * MIN_SEP;
                    SimPos dart = new SimPos(
                            from.x() + (int) Math.round(away * Math.cos(angle)),
                            centre.y(),
                            from.z() + (int) Math.round(away * Math.sin(angle)));
                    if (Math.max(Math.abs(dart.x() - centre.x()),
                                 Math.abs(dart.z() - centre.z())) < HEART) {
                        continue;   // the hall's own ground
                    }
                    boolean clear = true;
                    for (SimPos taken : seq) {
                        if (!Layout.farEnoughApart(dart, taken)) {
                            clear = false;
                            break;
                        }
                    }
                    if (clear) {
                        found = dart;
                    }
                }
                if (found == null) {
                    active.remove(pick);   // no room left around this one
                    continue;
                }
                seq.add(found);
                active.add(seq.size() - 1);
            }
        }

        /** A plot's own width, which is the radius the scatter packs to. */
        static final int MIN_SEP = Layout.MIN_PLOT_SEPARATION;

        /** How often a hemmed-in scatter retries before it starts somewhere new. */
        static final int RESTARTS = 8;

        /**
         * Ground beyond everything placed, for a scatter that has run out of room.
         *
         * <p>Clear by construction: past the furthest plot by a whole separation,
         * so it cannot foul anything however tightly the rest is packed. The town
         * takes a step outward, which is what a real one does when the good
         * ground by the green is gone.
         */
        private SimPos beyond(SimPos centre, List<SimPos> seq) {
            int furthest = HEART;
            for (SimPos placed : seq) {
                furthest = Math.max(furthest, Math.max(
                        Math.abs(placed.x() - centre.x()),
                        Math.abs(placed.z() - centre.z())));
            }
            int out = furthest + MIN_SEP;
            // Turned by the count so successive restarts do not stack up in a line.
            double angle = seq.size() * 2.399963;
            return new SimPos(
                    centre.x() + (int) Math.round(out * Math.cos(angle)),
                    centre.y(),
                    centre.z() + (int) Math.round(out * Math.sin(angle)));
        }

        private final Map<String, List<SimPos>> REMEMBERED = new LinkedHashMap<>();
    };

    /** A town laid along a street, with the street known first. */
    public static final Layout HIGH_STREET = new StreetLayout();

    private static final Map<String, Layout> KNOWN = new LinkedHashMap<>();

    static {
        for (Layout layout : new Layout[]{RING, WARREN, STRONGHOLD, ORGANIC, HIGH_STREET}) {
            KNOWN.put(layout.id(), layout);
        }
    }

    /**
     * The named arrangement, or rings when nobody has said.
     *
     * <p>Null-tolerant on purpose: a settlement restored from a save written
     * before layouts existed carries no name at all, and the one lookup
     * guaranteed to happen on an old world is the one that would otherwise
     * throw.
     */
    public static Layout of(String id) {
        Layout found = id == null ? null : KNOWN.get(id);
        return found != null ? found : RING;
    }

    /** Every arrangement that has been defined. */
    public static java.util.Collection<Layout> all() {
        return KNOWN.values();
    }
}
