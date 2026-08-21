package com.kingdoms.sim.settlement;

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
 * building is centred on its origin in both — so it runs from
 * {@code -width/2} to {@code +width/2}. {@code height} counts upward from the
 * floor.
 *
 * @param y      the height the floor actually ended up at
 * @param width  full span east-west
 * @param depth  full span north-south
 * @param height courses from the floor to the roof
 */
public record Footprint(int y, int width, int depth, int height) {

    /** What is known about a building whose plan has never been built. */
    public static final Footprint UNKNOWN = new Footprint(Integer.MIN_VALUE, 0, 0, 0);

    public boolean isKnown() {
        return width > 0 && depth > 0;
    }

    /** Whether a column falls inside this building's footprint. */
    public boolean covers(int originX, int originZ, int x, int z) {
        int rx = width / 2;
        int rz = depth / 2;
        return Math.abs(x - originX) <= rx && Math.abs(z - originZ) <= rz;
    }
}
