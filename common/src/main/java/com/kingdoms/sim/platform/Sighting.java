package com.kingdoms.sim.platform;

/**
 * What a town's people can see of what is coming for them.
 *
 * <p>Two numbers rather than one, because the old single count could not tell
 * the difference between four zombies and four creepers, and a town should. The
 * {@code danger} is the weighted sum — see the platform's own reckoning of what
 * each kind of creature is worth — and {@code seen} is the plain head count.
 *
 * <p>The head count survives because one rule needs it and cannot be expressed
 * in danger alone: a single creature, however nasty, must never empty the
 * streets. That is the watch's job, and a town that hides indoors every time one
 * creeper wanders past the wall is a town that never finishes a building.
 *
 * @param danger weighted total of everything in sight
 * @param seen   how many separate creatures that total came from
 */
public record Sighting(int danger, int seen) {

    /** Nothing in sight. */
    public static final Sighting NONE = new Sighting(0, 0);

    public Sighting {
        if (danger < 0 || seen < 0) {
            throw new IllegalArgumentException(
                    "a sighting cannot be negative: danger=" + danger + " seen=" + seen);
        }
    }

    /** Whether anything at all was seen. */
    public boolean any() {
        return seen > 0;
    }

    /** Whether the whole of this sighting is one creature. */
    public boolean isLone() {
        return seen == 1;
    }
}
