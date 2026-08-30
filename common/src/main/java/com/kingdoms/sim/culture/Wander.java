package com.kingdoms.sim.culture;

/**
 * How far a street strays from the straight line it is nominally on.
 *
 * <p>A settlement drawn with perfectly straight streets reads as a spreadsheet
 * from the air. Real roads bend, because they were a cart track before they
 * were a street and the cart went round the wet bit. The bend is small — a road
 * that wanders twenty blocks is a road nobody would follow — but it is the
 * difference between a plan and a place.
 *
 * <p><strong>This is a centreline, not decoration.</strong> The temptation is to
 * curve the drawn road and leave the plots where the arithmetic put them, which
 * looks fine in a diagram and puts houses in the carriageway on the ground: the
 * setback is measured from the street's middle, so if the middle moves and the
 * frontage does not, the gap closes. So a single function answers "where is this
 * street at this point along it", and both the road geometry and every plot that
 * fronts it are derived from that one answer. Curving the picture without
 * curving the frontage is exactly the fault that put fifteen plots in the road.
 *
 * <p>Two waves rather than one, at a deliberately irrational ratio, so the
 * street never repeats a shape along its length and never comes back to a
 * regular S-bend. Zero-mean, so a street still arrives roughly where it was
 * headed and the town does not creep sideways as it grows.
 *
 * @param amplitude how far the centreline may stray, in blocks, either way
 * @param wavelength how far along the street a full bend takes
 * @param seed which street this is, so two lanes never curve in step
 */
public record Wander(int amplitude, int wavelength, long seed) {

    /** A street laid by somebody with a straight edge. */
    public static final Wander STRAIGHT = new Wander(0, 1, 0);

    /**
     * The steepest a street may lean before its own frontage stops fitting.
     *
     * <p>Measured, not chosen. The plots along a street are pitched fourteen
     * blocks apart and have to stay twelve apart on the wider axis, so a street
     * that slides sideways as it runs uses up the gap between one plot and the
     * next. Past this the offers start fouling each other and drop to the
     * outskirts, and the town both loses its frontage and gets bigger:
     *
     * <pre>
     *   slope   frontage at 140 plots   reach
     *   0.00           100%              172
     *   0.24           100%              199
     *   0.27           100%              205
     *   0.29            96%              238
     *   0.33            82%              238
     *   0.46            69%              239
     * </pre>
     *
     * <p>The useful part is that this bounds the <em>slope</em> and not the
     * <em>amplitude</em>. A street may wander as far off line as it likes so long
     * as it takes its time getting there — thirteen blocks over a wavelength of
     * three hundred keeps every plot fronting a street, while seven blocks over
     * ninety-six costs a third of them. Bends want to be long, not small.
     */
    public static final double SAFE_SLOPE = 0.25;

    /**
     * A wander of this amplitude, spread over enough distance to stay buildable.
     *
     * <p>The way to ask for one, unless you are deliberately testing what happens
     * past the bar.
     */
    public static Wander gentle(int amplitude, long seed) {
        if (amplitude <= 0) {
            return STRAIGHT;
        }
        return new Wander(amplitude,
                (int) Math.ceil(amplitude * 2 * Math.PI / SAFE_SLOPE), seed);
    }

    public Wander {
        if (wavelength < 1) {
            throw new IllegalArgumentException("wavelength must be positive");
        }
    }

    /** The same wander, re-phased for another street of the same town. */
    public Wander forStreet(int index) {
        return new Wander(amplitude, wavelength, seed * 31 + index * 2654435761L);
    }

    /**
     * How far off the straight line the centre of the street sits, here.
     *
     * <p>Deterministic in the seed and the distance, which is what lets the road
     * and the houses along it agree without either knowing about the other.
     */
    public double offsetAt(double along) {
        if (amplitude == 0) {
            return 0;
        }
        // Phases spread across the circle by a hash of the seed, so streets in
        // one town do not all begin their first bend at the same place.
        double a = phase(seed);
        double b = phase(seed * 0x9E3779B97F4A7C15L + 1);
        double turns = 2 * Math.PI * along / wavelength;
        // The second wave is the shorter one and carries a third of the weight:
        // enough to break the regularity of a sine, not enough to read as a kink.
        double sum = 0.75 * Math.sin(turns + a) + 0.25 * Math.sin(turns * 1.618 + b);
        return amplitude * sum;
    }

    /**
     * How fast the centreline slides sideways, at the steepest point of a bend.
     *
     * <p>The number that decides whether a street's frontage fits, so it is worth
     * being able to ask for it rather than deriving it at each call site.
     *
     * <p>The principal wave's slope, not the true maximum: the shorter second
     * wave carries a quarter of the weight at 1.618 times the frequency, so the
     * steepest the centreline actually leans is about fifteen per cent more than
     * this. Reported the simple way because {@link #SAFE_SLOPE} and the table
     * beside it were measured in these same units — the bar and the reading agree,
     * which is what matters for a comparison. Do not read it as a gradient.
     */
    public double slope() {
        return amplitude * 2 * Math.PI / wavelength;
    }

    /** The offset rounded to the block the world is actually made of. */
    public int blocksAt(double along) {
        return (int) Math.round(offsetAt(along));
    }

    private static double phase(long of) {
        long mixed = of * 0xC2B2AE3D27D4EB4FL;
        mixed ^= mixed >>> 29;
        return ((mixed >>> 11) / (double) (1L << 53)) * 2 * Math.PI;
    }
}
