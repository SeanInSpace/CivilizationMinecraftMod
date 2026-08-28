package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

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

    private static final Map<String, Layout> KNOWN = new LinkedHashMap<>();

    static {
        for (Layout layout : new Layout[]{RING, WARREN, STRONGHOLD}) {
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
