package com.civilization.sim.work;

import com.civilization.sim.geom.Hull;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.PathNetwork;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.WorkArea;
import com.civilization.sim.world.SimContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The wood a town takes down inside its own streets.
 *
 * <p>The measurement that produced this said it plainly: <em>the forest inside
 * the ring roads stood untouched</em>. A burgher town of seventeen had its houses,
 * its lanes and its ring road laid through standing timber, and the trees between
 * them were a canopy — which is a roof, which is darkness at noon, which is a
 * spawner in the middle of a village. Four spiders and seven zombies came out of
 * it. No amount of street lighting fixes a wood, because a lamp on a verge does
 * not light the ground under a crown thirty blocks away.
 *
 * <p>So a town clears the wood inside its built area as it grows, as a public
 * work, at the same priority as the roads. Trunks and their crowns only: the
 * ground cover stays, because a village on scraped dirt reads as a building site
 * and the grass under a lamp is not spawnable anyway. The crowns are not touched
 * at all — a felled trunk's leaves decay by vanilla's own rule, which is what a
 * player expects to see and what {@code Felling} has always relied on.
 *
 * <p><strong>The forester's belt is spared, and it is spared by construction.</strong>
 * A lumber camp's stand lies <em>outside</em> the claim — that is what
 * {@code Overgrowth.beltOf} means by asking whether a column is inside the village
 * — and the built area is inside the streets, which are inside the claim. The two
 * cannot overlap. {@link #sparesTheBelt} asserts it rather than trusting it,
 * because "cannot overlap" is a sentence about today's siting rules and the
 * forester's ledger is being rewritten in the next worktree along.
 */
public final class InteriorClearing {

    private InteriorClearing() {
    }

    /**
     * How much ground one cell of the work covers: eight blocks square.
     *
     * <p>A cell rather than a tree, because the simulation does not know where
     * trees are and must not pretend to. What it can say is <em>which ground is
     * to be cleared</em>, in units somebody can walk to; the platform finds
     * whatever is standing in the cell and brings it down. Eight matches the
     * lighting's spacing, so a cleared town and a lit town are described on the
     * same grid.
     */
    public static final int CELL = 8;

    /**
     * Cells the clock clears in one step: one.
     *
     * <p>Deliberately slow. A wood inside a grown town's streets is a few hundred
     * cells, so a step a cell is a few hundred steps — twenty-five minutes of
     * real time — which is a town clearing its ground over a season rather than
     * a village that flattens a forest between two glances. It is also the cap
     * the brief asked for, and the reason it asked is that an uncapped sweep over
     * a big wood is a stutter.
     */
    public static final int CELLS_PER_STEP = 1;

    /**
     * How far past the hull of the streets the clearing reaches.
     *
     * <p>Nothing. The built area is the ground the town has actually taken, and a
     * town that cleared a margin outside its own streets would be a town that
     * scrapes a ring of bare earth round itself every time it opens a lane — the
     * look the plot margins were shrunk to avoid. The trees just outside the
     * outermost street are the wood the village stands in, and they are supposed
     * to be there.
     */
    public static final int MARGIN = 0;

    /**
     * The outline of the built area: the hull of the columns its opened streets
     * cover.
     *
     * <p>The brief asked for the innermost closed loop of streets on a ring
     * arrangement and the hull of the opened streets otherwise. It is the hull for
     * all of them, and the reason is what the innermost loop actually is: on the
     * measured town — a {@code radial_concentric} burgher plan — the innermost
     * loop is the ring round the market place, thirty blocks across, and clearing
     * inside it would have cleared the market square and left every tree that
     * killed anybody standing. The hull of the opened streets <em>is</em> the
     * outermost ring on a ring town, so taking it uniformly clears the wood inside
     * the ring roads, which is the thing the measurement asked for, and it needs
     * no special case for the grid and linear arrangements.
     *
     * <p>Convex rather than concave. A concave hull traces the fingers of a
     * branching network and would refuse to clear the wood in the crook between
     * two lanes, which is exactly where a canopy closes over.
     */
    public static List<SimPos> outline(Settlement settlement) {
        PathNetwork paths = settlement.paths();
        if (paths == null) {
            return List.of();
        }
        List<SimPos> ends = new ArrayList<>();
        List<PathNetwork.Segment> runs = paths.segments();
        for (int i = 0; i < runs.size(); i++) {
            if (!paths.isOpened(i) || paths.isUnwalkable(i)) {
                continue;
            }
            ends.add(runs.get(i).from());
            ends.add(runs.get(i).to());
        }
        return ends.size() < 3 ? List.of() : Hull.convex(ends);
    }

    /**
     * Every cell of ground the town has to clear, in the order it clears them.
     *
     * <p>Center-outward, for the reason {@code LightPlanner} sorts the same way:
     * the count of cells cleared is an index into this list, so the list wants to
     * grow at its end. A town's streets reach further out as it grows, so the cells
     * they enclose are mostly appended.
     *
     * <p><strong>Mostly, and the exception is stated rather than wished away.</strong>
     * A band of new ground on one side of the hull is nearer the middle than the far
     * corners of the old hull, so a lopsided growth inserts cells rather than
     * appending them and the cleared count stops naming quite the same ground. What
     * catches it is {@code Woodcut.draw}, which walks the whole cleared prefix
     * continuously and fells whatever it finds standing there — so an inserted cell
     * is cleared on the sweep that reaches it rather than being lost. The
     * alternative was persisting a set of cell coordinates instead of one number,
     * which is a save-format cost and a growing one, for a work whose stations are
     * free, idempotent and revisited anyway. See {@code InteriorClearingTest}, which
     * states both halves of this.
     *
     * <p>Three exclusions, and each of them is a thing that must never be felled.
     * A cell inside somebody's footprint is their house. A cell in the forester's
     * belt is the town's own timber supply — see {@link #sparesTheBelt}. And a
     * cell outside the hull is not the built area at all; it is the wood the
     * village stands in.
     */
    public static List<SimPos> cells(Settlement settlement) {
        List<SimPos> hull = outline(settlement);
        if (hull.isEmpty()) {
            return List.of();
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (SimPos corner : hull) {
            minX = Math.min(minX, corner.x());
            maxX = Math.max(maxX, corner.x());
            minZ = Math.min(minZ, corner.z());
            maxZ = Math.max(maxZ, corner.z());
        }
        SimPos center = settlement.center();
        List<SimPos> cells = new ArrayList<>();
        // Snapped to a fixed world grid rather than laid out from the hull's own
        // corner, so a cell keeps its identity when the hull grows. A grid pinned
        // to a moving corner would renumber every cell in the town the first time
        // a lane opened to the west.
        int fromX = Math.floorDiv(minX - MARGIN, CELL) * CELL;
        int fromZ = Math.floorDiv(minZ - MARGIN, CELL) * CELL;
        for (int x = fromX; x <= maxX + MARGIN; x += CELL) {
            for (int z = fromZ; z <= maxZ + MARGIN; z += CELL) {
                SimPos at = new SimPos(x + CELL / 2, center.y(), z + CELL / 2);
                if (!Hull.contains(hull, at)) {
                    continue;
                }
                if (insideAPlot(settlement, at) || inTheBelt(settlement, at)) {
                    continue;
                }
                cells.add(at);
            }
        }
        cells.sort(Comparator
                .comparingLong((SimPos at) -> at.horizontalDistanceSq(center))
                .thenComparingInt(SimPos::x)
                .thenComparingInt(SimPos::z));
        return List.copyOf(cells);
    }

    /** The next cell to clear, or null when the ground inside the streets is open. */
    public static SimPos next(Settlement settlement) {
        List<SimPos> all = cells(settlement);
        int done = settlement.interiorCleared();
        return done >= 0 && done < all.size() ? all.get(done) : null;
    }

    /** Whether the town has cleared all the ground its streets enclose. */
    public static boolean isClear(Settlement settlement) {
        return settlement.interiorCleared() >= cells(settlement).size();
    }

    /**
     * Whether no cell of the clearing stands in the forester's belt.
     *
     * <p>An assertion rather than a filter — {@link #cells} already filters — and
     * it is here so a test can state it about a whole town rather than about the
     * one cell that happened to be checked. If this ever comes back false, either
     * a lumber camp has been sited inside a claim or the built area has grown out
     * past one, and both of those are somebody else's bug that this work would
     * otherwise turn into a felled stand.
     */
    public static boolean sparesTheBelt(Settlement settlement) {
        for (SimPos cell : cells(settlement)) {
            if (inTheBelt(settlement, cell)) {
                return false;
            }
        }
        return true;
    }

    /** Whether any cell of the clearing stands on a plot. */
    public static boolean sparesThePlots(Settlement settlement) {
        for (SimPos cell : cells(settlement)) {
            if (insideAPlot(settlement, cell)) {
                return false;
            }
        }
        return true;
    }

    private static boolean insideAPlot(Settlement settlement, SimPos at) {
        for (Building building : settlement.buildings()) {
            if (!building.footprint().isKnown()) {
                continue;
            }
            // The whole cell, not only its middle: a cell whose corner overlaps
            // a wall is a cell a crew would fell a wall in.
            for (int dx = -CELL / 2; dx <= CELL / 2; dx += CELL / 2) {
                for (int dz = -CELL / 2; dz <= CELL / 2; dz += CELL / 2) {
                    if (building.footprint().covers(building.origin().x(),
                            building.origin().z(), at.x() + dx, at.z() + dz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean inTheBelt(Settlement settlement, SimPos at) {
        WorkArea stand = settlement.lumberArea();
        if (stand == null) {
            return false;
        }
        // The cell's furthest corner, so a stand grazed at the edge still spares
        // the whole cell. Erring toward leaving trees standing is the right way
        // round: an uncleared cell is a dark patch, and a cleared stand is a town
        // with no timber.
        double reach = stand.radius() + CELL;
        return at.horizontalDistance(
                new SimPos(stand.center().x(), at.y(), stand.center().z())) <= reach;
    }

    // --- the clock ---

    /**
     * One step of clearing for a town nobody is looking at.
     *
     * <p>The same two refusals as the roads and the lighting: hands are on it, or
     * somebody can see the cell. A wood that fells itself in front of a player is
     * the one thing this must never look like.
     *
     * <p><strong>The clock takes no spoil.</strong> It cannot: it does not know
     * how many logs are standing in the cell, and a figure invented for them would
     * be a town making timber out of arithmetic. What it writes down is that the
     * cell is cleared, and {@code Woodcut} brings the trunks down and credits the
     * real logs on the first pass where the ground is loaded — the same split
     * {@code PerimeterLayer} has always used between a wall a town has and a wall
     * a player can see.
     */
    public static void advance(Settlement settlement, SimContext ctx) {
        if (!settlement.hasLivingResidents()) {
            return;
        }
        // Nobody fells a tree in the dark. Same parity argument as the lighting's:
        // a watched town's crew is indoors, so a clock that worked through the
        // night would clear an unwatched town's ground faster than a watched one's.
        if (com.civilization.sim.person.Curfew.idlesUnwatchedWork(
                ctx.bridge().dayTime(), com.civilization.sim.person.Curfew.LEAD_TICKS)) {
            return;
        }
        for (int done = 0; done < CELLS_PER_STEP; done++) {
            SimPos cell = next(settlement);
            if (cell == null) {
                return;
            }
            if (PublicWorks.leaveItToTheCrew(settlement, ctx.bridge(),
                    new PublicWorks.ClearingWork())) {
                return;
            }
            if (ctx.bridge().playerWithin(cell, ctx.settings().observedRadius())) {
                return;
            }
            settlement.setInteriorCleared(settlement.interiorCleared() + 1);
        }
    }
}
