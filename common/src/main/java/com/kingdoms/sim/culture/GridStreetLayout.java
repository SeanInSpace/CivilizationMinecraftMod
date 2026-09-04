package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

import java.util.List;

/**
 * Streets ruled both ways, and the blocks between them built on.
 *
 * <p>The streets-first answer to the stronghold, whose lattice is a square
 * spiral of plots on a pitch with no roads in it at all. The difference is not
 * cosmetic: a spiral of buildings has to have paths threaded between it
 * afterwards by whatever route is left, and a grid has the roads first and the
 * buildings in the gaps, which is what a planned town actually is. Colonial
 * grids, Roman colonies, bastides and most of Manhattan are this.
 *
 * <p>Laid with a straight edge by default, because that is the whole character
 * of the thing — a grid that wanders is a grid nobody surveyed. It will accept a
 * {@link Wander} anyway, and a slight one reads like a grid laid out by eye over
 * uneven ground, which is most medieval planned towns.
 *
 * <p>Plots stand back from both of the streets they sit between, and the
 * corners, where a plot would foul the cross street, simply go unoffered — the
 * shared machinery refuses them and the block ends up with its corners open,
 * which is what a real block does.
 */
public final class GridStreetLayout extends PlannedLayout {

    /**
     * How far apart the streets are ruled.
     *
     * <p>Two blocks over {@link #BACK_TO_BACK}, which is the floor below which
     * the two rows of houses in a block back onto each other closer than the
     * siting code allows. The forty this held was that same floor of
     * thirty-eight plus the same two, written as a literal beside a comment
     * naming a different and looser floor — twice the setback plus a
     * carriageway, thirty-four — that has never been the binding one.
     *
     * <p>The two spare are what keeps this arrangement distinguishable from the
     * bastide, which takes the floor exactly and is a tighter town for it.
     */
    private static final int BLOCK = BACK_TO_BACK + 2;

    private final String id;
    private final Wander wander;

    public GridStreetLayout() {
        this("stronghold_streets", Wander.STRAIGHT);
    }

    public GridStreetLayout(String id, Wander wander) {
        this.id = id;
        this.wander = wander;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isSameShapeEverywhere() {
        return wander.amplitude() == 0;
    }

    public Wander wander() {
        return wander;
    }

    @Override
    protected void design(SimPos centre, int wanted,
                          List<TownPlan.Street> streets, List<Offer> offers) {
        // Each block of the grid carries roughly six plots once the corners are
        // refused, so the town needs about that many blocks, and a square town
        // wants the square root of them along each side.
        int lines = Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(1, wanted) / 6.0)) + 1);
        int reach = lines * BLOCK;

        // North-south streets first, then east-west, so a street's index is
        // stable as the town grows -- a plot records the street it fronts by
        // index, and a plan that renumbered its roads would repoint every door.
        for (int i = -lines; i <= lines; i++) {
            streets.add(northSouth(centre, wanderFor(wander, centre, streets.size()),
                    i * BLOCK, -reach, reach, ROAD_HALF * 2, TownPlan.Kind.LANE));
        }
        int firstEastWest = streets.size();
        for (int i = -lines; i <= lines; i++) {
            streets.add(eastWest(centre, wanderFor(wander, centre, streets.size()),
                    i * BLOCK, -reach, reach, ROAD_HALF * 2, TownPlan.Kind.LANE));
        }

        // Frontage on the east-west streets, which is where a grid's houses
        // conventionally face. The north-south streets carry the cross traffic
        // and the backs of the blocks.
        for (int line = -lines; line <= lines; line++) {
            int street = firstEastWest + (line + lines);
            Wander how = wanderFor(wander, centre, street);
            int baseZ = line * BLOCK;
            for (int k = -lines * 3; k <= lines * 3; k++) {
                int x = HALF_PITCH + k * PITCH;
                int z = baseZ + how.blocksAt(x);
                if (Math.abs(x) > reach || Math.abs(baseZ) > reach) {
                    continue;
                }
                offers.add(new Offer(at(centre, x, z - SETBACK), street, 2));
                offers.add(new Offer(at(centre, x, z + SETBACK), street, 0));
            }
        }
    }

    private static SimPos at(SimPos centre, int dx, int dz) {
        return new SimPos(centre.x() + dx, centre.y(), centre.z() + dz);
    }
}
