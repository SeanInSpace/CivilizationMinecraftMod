package com.civilization.sim.geom;

/**
 * The one hash the mod picks with.
 *
 * <p>SplitMix64's finalizer, which is what everything here that wants a
 * deterministic but well-spread choice — a face, a greeting, an inn's name, a
 * smith's rhythm — reaches for, and which had been typed out by hand in each
 * place that wanted it. Six copies of three magic constants is six places a
 * typo can weaken one caller's spread without anybody seeing which copy was the
 * canonical one. The arithmetic is unchanged; only its address is.
 */
public final class Mix {

    private Mix() {
    }

    /** SplitMix64's finalizer: every bit of the input reaches every bit of the output. */
    public static long finish(long h) {
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /**
     * A seed stirred and finished.
     *
     * <p>Multiplied by the golden ratio and salted before the finalizer, for the
     * reason every caller gives: ids allotted in sequence share their high bits,
     * and finishing them raw would hand a whole town one answer.
     */
    public static long of(long seed) {
        return finish(seed * 0x9E3779B97F4A7C15L ^ 0x2545F4914F6CDD1DL);
    }
}
