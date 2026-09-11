package com.civilization.sim.settlement;

/**
 * How much room a building takes up, and where its floor sits.
 *
 * <p>The simulation has never needed a building's size before — it only ever
 * cared where things were, not how big. Anything that has to draw a building,
 * check whether a point is inside one, or keep two of them from overlapping
 * needs this, so the platform layer reports it back once the plan is built and
 * the settlement keeps it.
 *
 * <p>{@code width} and {@code depth} are the full span, not a radius, and the
 * building is centered on its origin in both — so it runs from
 * {@code -width/2} to {@code +width/2}. {@code height} counts upward from the
 * floor.
 *
 * <p>The {@code notch} is what makes this a shape rather than a box. A building
 * with a corner cut out of it reports the bounding box it was drawn in and the
 * bite that was taken from it, so everything asking "is this column inside that
 * building" gets the right answer for the yard in the crook of an L. Absent for
 * everything rectangular, which is nearly everything, and absent from every save
 * written before shapes existed.
 *
 * @param y      the height the floor actually ended up at
 * @param width  full span east-west
 * @param depth  full span north-south
 * @param height courses from the floor to the roof
 * @param notch  the corner cut away, or {@link BuildingSizes.Notch#NONE}
 */
public record Footprint(int y, int width, int depth, int height,
                        BuildingSizes.Notch notch) {

    public Footprint(int y, int width, int depth, int height) {
        this(y, width, depth, height, BuildingSizes.Notch.NONE);
    }

    public Footprint {
        if (notch == null) {
            notch = BuildingSizes.Notch.NONE;
        }
    }

    /** What is known about a building whose plan has never been built. */
    public static final Footprint UNKNOWN = new Footprint(Integer.MIN_VALUE, 0, 0, 0);

    public boolean isKnown() {
        return width > 0 && depth > 0;
    }

    /**
     * Whether a column falls inside this building's footprint.
     *
     * <p>The bite is taken off here rather than only where the blocks are laid,
     * because the callers of this are the ones that would otherwise be wrong
     * about a yard: whether a person is indoors, whether ground is spoken for,
     * what the town map fills in.
     */
    public boolean covers(int originX, int originZ, int x, int z) {
        int rx = width / 2;
        int rz = depth / 2;
        int dx = x - originX;
        int dz = z - originZ;
        if (Math.abs(dx) > rx || Math.abs(dz) > rz) {
            return false;
        }
        return !inNotch(dx, dz);
    }

    /**
     * Whether a column stands close enough to this plot that a tree in it leans
     * on the building: outside the plot, and within {@code clearance} of it.
     *
     * <p>The one band the town is allowed to touch beyond its own ground, and it
     * is deliberately narrow. A trunk two blocks off a wall is a tree growing
     * through the eaves; a trunk three blocks off is a tree in somebody's yard,
     * and felling that would make every building in a wood clear a ring around
     * itself — which is the scraped-pad look the plot margin was shrunk to avoid.
     *
     * <p>Measured as a square ring rather than a circle, because the plot is a
     * box and a round band round a square reads as neither. Asked cell by cell
     * against {@link #covers} rather than by arithmetic on the half-spans, so the
     * yard in the crook of an L is handled for free: those columns are outside
     * the plot and up against two walls, so a tree standing in the yard is in the
     * band exactly as a tree standing outside the gable end is.
     *
     * @param clearance how far past the plot the band reaches; zero for no band
     */
    public boolean inClearanceBand(int originX, int originZ, int x, int z, int clearance) {
        if (clearance <= 0 || covers(originX, originZ, x, z)) {
            return false;
        }
        for (int dx = -clearance; dx <= clearance; dx++) {
            for (int dz = -clearance; dz <= clearance; dz++) {
                if (covers(originX, originZ, x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether this cell of the bounding box is the corner that was cut away. */
    public boolean inNotch(int dx, int dz) {
        if (!notch.isCut()) {
            return false;
        }
        int rx = width / 2;
        int rz = depth / 2;
        boolean inX = notch.towardX() > 0
                ? dx > rx - notch.width() : dx < -rx + notch.width();
        boolean inZ = notch.towardZ() > 0
                ? dz > rz - notch.depth() : dz < -rz + notch.depth();
        return inX && inZ;
    }
}
