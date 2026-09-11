package com.kingdoms.neoforge.world;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.BuildingSizes;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.PathNetwork;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a crew takes off a plot and off a road, and what it leaves standing.
 *
 * <p>Two live complaints, one fault: leaf litter lying on top of a freshly laid
 * road, and a forest canopy across a finished roof with trunks against the
 * walls. In both cases the town cleared exactly the cells it was about to write
 * in and nothing else — which is right for a course of masonry and wrong for a
 * building somebody has to be able to see.
 *
 * <p><strong>Why it can run without a world.</strong> Block tags are bound when
 * a server loads its datapacks, and a JUnit run has no server: {@code
 * Blocks.OAK_LEAVES.defaultBlockState().is(BlockTags.LEAVES)} is <em>false</em>
 * here. So {@link Overgrowth} puts the whole of its geometry behind {@link
 * Overgrowth.Sky}, which answers in {@link Overgrowth.Cover} rather than in
 * block states, and a fake sky satisfies it. Same argument {@code
 * BlueprintPlacerSizeTest} makes about a building's drawn size.
 */
class OvergrowthPlanTest {

    /** The floor line every fixture here is built around. */
    private static final int FLOOR = 64;

    /** A world you stack cell by cell, which answers the two questions. */
    private static final class FakeSky implements Overgrowth.Sky {

        private final Map<BlockPos, Overgrowth.Cover> cells = new HashMap<>();

        FakeSky put(int x, int y, int z, Overgrowth.Cover cover) {
            cells.put(new BlockPos(x, y, z), cover);
            return this;
        }

        /** Ground under everything, at the floor line, out to the given reach. */
        FakeSky ground(int reach) {
            for (int x = -reach; x <= reach; x++) {
                for (int z = -reach; z <= reach; z++) {
                    put(x, FLOOR, z, Overgrowth.Cover.KEEP);
                }
            }
            return this;
        }

        /** A trunk standing in one column, from the floor up. */
        FakeSky trunk(int x, int z, int height) {
            for (int dy = 1; dy <= height; dy++) {
                put(x, FLOOR + dy, z, Overgrowth.Cover.LOG);
            }
            return this;
        }

        /** Leaves across a square at one height. */
        FakeSky canopy(int y, int reach) {
            for (int x = -reach; x <= reach; x++) {
                for (int z = -reach; z <= reach; z++) {
                    put(x, y, z, Overgrowth.Cover.LEAF);
                }
            }
            return this;
        }

        /** Litter, grass or a flower lying on the surface of every column. */
        FakeSky litter(int reach) {
            for (int x = -reach; x <= reach; x++) {
                for (int z = -reach; z <= reach; z++) {
                    put(x, FLOOR + 1, z, Overgrowth.Cover.GROUND);
                }
            }
            return this;
        }

        @Override
        public Overgrowth.Cover at(int x, int y, int z) {
            return cells.getOrDefault(new BlockPos(x, y, z), Overgrowth.Cover.NONE);
        }

        @Override
        public int topOf(int x, int z) {
            int top = FLOOR;
            for (Map.Entry<BlockPos, Overgrowth.Cover> cell : cells.entrySet()) {
                if (cell.getKey().getX() == x && cell.getKey().getZ() == z) {
                    top = Math.max(top, cell.getKey().getY());
                }
            }
            return top + 1;
        }
    }

    /** A 7x7 building with its one-block doorstep ring: a 9x9 plot. */
    private static Footprint plot() {
        return new Footprint(FLOOR, 9, 9, 5);
    }

    private static Set<BlockPos> clearedOf(List<BlockPos> plan) {
        return new HashSet<>(plan);
    }

    /** What the crew is sent to take out. */
    private static Set<BlockPos> dug(Overgrowth.Clearing clearing) {
        return new HashSet<>(clearing.dug());
    }

    /** What is simply taken when the site opens, because nobody can reach it. */
    private static Set<BlockPos> stripped(Overgrowth.Clearing clearing) {
        return new HashSet<>(clearing.stripped());
    }

    private static Set<BlockPos> everything(Overgrowth.Clearing clearing) {
        Set<BlockPos> all = dug(clearing);
        all.addAll(clearing.stripped());
        return all;
    }

    private static Overgrowth.Clearing clear(Overgrowth.Sky sky, Footprint plot,
                                             Overgrowth.Spared spared) {
        return Overgrowth.overPlot(sky, new BlockPos(0, FLOOR, 0), plot, spared);
    }

    // --- a building's own sky ---

    @Test
    void aCanopyOverTheRoofIsPlannedAwayEveryCellOfIt() {
        // A cottage five courses high with a canopy eight blocks up: well clear of
        // anything the excavation box or the doorstep headroom ever reached, and
        // exactly what a player sees lying across the roof of a house in a wood.
        FakeSky wood = new FakeSky().ground(8).canopy(FLOOR + 8, 8);

        Overgrowth.Clearing clearing = clear(wood, plot(), Overgrowth.NOTHING_SPARED);
        Set<BlockPos> cleared = stripped(clearing);

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                assertTrue(cleared.contains(new BlockPos(dx, FLOOR + 8, dz)),
                        "the canopy over " + dx + "," + dz + " is still on the roof");
            }
        }
        assertTrue(clearing.dug().isEmpty(),
                "and nobody is sent to climb eight blocks of air for a leaf");
    }

    @Test
    void theHillsideOverAPlotIsLeftToTheExcavation() {
        // Stone standing over the plot is terrain, not canopy. Cutting a shaft to
        // the build limit through a mountain is not what clearing a site means.
        FakeSky overhang = new FakeSky().ground(6)
                .put(2, FLOOR + 9, 2, Overgrowth.Cover.KEEP);

        Set<BlockPos> cleared = everything(
                clear(overhang, plot(), Overgrowth.NOTHING_SPARED));

        assertFalse(cleared.contains(new BlockPos(2, FLOOR + 9, 2)));
    }

    @Test
    void growthAboveARoofThatIsAlreadyStandingIsStillTaken() {
        // The finishing pass runs the same plan over a building that is already
        // there, so the first thing a column sweep meets is the roof. Stopping at
        // it would mean a materialized town kept its canopy forever.
        FakeSky built = new FakeSky().ground(6);
        for (int dy = 0; dy <= 5; dy++) {
            built.put(0, FLOOR + dy, 0, Overgrowth.Cover.KEEP);
        }
        built.put(0, FLOOR + 9, 0, Overgrowth.Cover.LEAF);

        Set<BlockPos> cleared = everything(clear(built, plot(), Overgrowth.NOTHING_SPARED));

        assertTrue(cleared.contains(new BlockPos(0, FLOOR + 9, 0)),
                "the roof is stepped over, not stopped at");
        assertFalse(cleared.contains(new BlockPos(0, FLOOR + 2, 0)),
                "and the roof itself is never in the list");
    }

    @Test
    void aTrunkOverThePlotIsTheCrewsAndTheFoliageIsNot() {
        // The split that decides who takes what, and it is about reach. A log is
        // reachable however high it stands, because the excavation swaps it for
        // the stump of its own trunk and fells the tree from the ground. A leaf
        // is reachable from nowhere, so it is taken outright when the site opens
        // instead of being searched for footing three dozen times and abandoned.
        FakeSky wood = new FakeSky().ground(6)
                .put(1, FLOOR + 7, 1, Overgrowth.Cover.LOG)
                .put(1, FLOOR + 8, 1, Overgrowth.Cover.LEAF)
                .put(0, FLOOR + 1, 0, Overgrowth.Cover.GROUND);

        Overgrowth.Clearing clearing = clear(wood, plot(), Overgrowth.NOTHING_SPARED);

        assertTrue(dug(clearing).contains(new BlockPos(1, FLOOR + 7, 1)), "wood is dug");
        assertTrue(dug(clearing).contains(new BlockPos(0, FLOOR + 1, 0)),
                "and so is what is underfoot");
        assertTrue(stripped(clearing).contains(new BlockPos(1, FLOOR + 8, 1)),
                "foliage is stripped");
        assertFalse(dug(clearing).contains(new BlockPos(1, FLOOR + 8, 1)));
    }

    // --- the trunks leaning on it ---

    @Test
    void aTrunkTwoBlocksOffTheWallComesDownAndOneThreeBlocksOffDoesNot() {
        // The walls of the cottage reach three each way and its doorstep ring
        // makes the plot four. So five is two blocks off the wall — a tree with
        // branches through the eaves — and six is three off, which is a tree in
        // the yard next door and none of the town's business.
        FakeSky stand = new FakeSky().ground(10)
                .trunk(5, 0, 6).trunk(6, 0, 6);

        Set<BlockPos> cleared = everything(clear(stand, plot(), Overgrowth.NOTHING_SPARED));

        for (int dy = 1; dy <= 6; dy++) {
            assertTrue(cleared.contains(new BlockPos(5, FLOOR + dy, 0)),
                    "a trunk against the wall is felled the whole way up");
            assertFalse(cleared.contains(new BlockPos(6, FLOOR + dy, 0)),
                    "three blocks off is a tree in the yard next door");
        }
    }

    @Test
    void theBandTakesLogsAndLeavesTheCrownToDecay() {
        // Vanilla drops a leaf that is more than six blocks from any log, so the
        // crown of a felled tree comes down by itself. Taking it by hand would
        // mean every house in a wood standing in a bald ring of its own making.
        FakeSky stand = new FakeSky().ground(10)
                .trunk(5, 0, 5)
                .put(5, FLOOR + 6, 0, Overgrowth.Cover.LEAF);

        Set<BlockPos> cleared = everything(clear(stand, plot(), Overgrowth.NOTHING_SPARED));

        assertTrue(cleared.contains(new BlockPos(5, FLOOR + 5, 0)), "the trunk goes");
        assertFalse(cleared.contains(new BlockPos(5, FLOOR + 6, 0)), "its crown stays");
    }

    @Test
    void aTrunkInTheForestersStandIsNeverTouched() {
        // The one thing the band must not take. A seeded camp has a stand planted
        // for it out past the houses, and a building raised near the edge of the
        // claim would otherwise fell the very trees the camp exists to cut.
        FakeSky stand = new FakeSky().ground(10).trunk(5, 0, 6).trunk(-5, 0, 6);
        Overgrowth.Spared woodland = (x, z) -> x > 0;

        Set<BlockPos> cleared = everything(clear(stand, plot(), woodland));

        assertFalse(cleared.contains(new BlockPos(5, FLOOR + 1, 0)),
                "the camp's tree is the camp's");
        assertTrue(cleared.contains(new BlockPos(-5, FLOOR + 1, 0)),
                "the one on the other side is still leaning on the house");
    }

    @Test
    void aTrunkInTheCrookOfAnLComesDownThoughTheYardIsNotDug() {
        // The yard of an L is not the building and is never excavated, but it is
        // up against two walls: a tree growing there leans on the house exactly as
        // one outside the gable end does.
        Footprint ell = new Footprint(FLOOR, 9, 9, 5,
                new BuildingSizes.Notch(2, 2, 1, 1));
        FakeSky wood = new FakeSky().ground(8).trunk(3, 3, 4);

        Set<BlockPos> cleared = everything(clear(wood, ell, Overgrowth.NOTHING_SPARED));

        assertTrue(cleared.contains(new BlockPos(3, FLOOR + 4, 3)));
    }

    // --- what a road takes off itself ---

    @Test
    void litterOnTheStonesComesOffAndTheVergeKeepsIts() {
        // The whole of the road bug, composed the way pave() composes it: the
        // cross-section is the carriageway and everything either side of it is
        // verge. A three-wide track at a station covers nine columns; the litter
        // on those nine goes, and the litter on the tenth is what makes the
        // finished way read as a road through a meadow.
        PathNetwork.Segment track = new PathNetwork.Segment(
                new SimPos(0, FLOOR, 0), new SimPos(8, FLOOR, 0), PathNetwork.TRACK_WIDTH);
        FakeSky meadow = new FakeSky().ground(8).litter(8);

        List<SimPos> carriageway = PathLayer.crossSectionAt(track, 3);
        List<BlockPos> cleared = new ArrayList<>();
        for (SimPos column : carriageway) {
            cleared.addAll(Overgrowth.overPaving(meadow,
                    new BlockPos(column.x(), FLOOR, column.z()),
                    Overgrowth.NOTHING_SPARED));
        }
        Set<BlockPos> gone = clearedOf(cleared);

        assertEquals(9, carriageway.size(), "a three-wide way is three by three");
        for (SimPos column : carriageway) {
            assertTrue(gone.contains(new BlockPos(column.x(), FLOOR + 1, column.z())),
                    "litter still lying on the stones at " + column.x() + "," + column.z());
        }
        assertFalse(gone.contains(new BlockPos(3, FLOOR + 1, 2)),
                "the verge is verge and keeps what grows on it");
    }

    @Test
    void aTrunkInTheCarriagewayIsTakenWholeAndSoAreTheBranchesOverIt() {
        FakeSky wood = new FakeSky().ground(4).trunk(0, 0, 7).canopy(FLOOR + 8, 4);

        Set<BlockPos> gone = clearedOf(
                Overgrowth.overPaving(wood, new BlockPos(0, FLOOR, 0),
                        Overgrowth.NOTHING_SPARED));

        for (int dy = 1; dy <= 7; dy++) {
            assertTrue(gone.contains(new BlockPos(0, FLOOR + dy, 0)));
        }
        assertTrue(gone.contains(new BlockPos(0, FLOOR + 8, 0)), "and the branch above it");
    }

    @Test
    void butNotAForestersTrunkStandingInTheCarriageway() {
        // The complaint: a lane routed through the belt came out having felled
        // the stand. The trunk is the camp's whatever is laid over it; the
        // litter on the stones and the branches over them are still the road's,
        // because a road under a bough is a road through a wood and reads as
        // one. A trunk that is actually in the way means the road should never
        // have been routed there -- see PathPlanner, which now holds it off.
        // The litter goes on every column but the trunk's own, where the trunk
        // is standing in the cell the litter would have been in.
        FakeSky belt = new FakeSky().ground(4).litter(4)
                .trunk(0, 0, 6).canopy(FLOOR + 8, 4);
        Overgrowth.Spared woodland = (x, z) -> z == 0;   // a belt across the way

        Set<BlockPos> atTheTrunk = clearedOf(
                Overgrowth.overPaving(belt, new BlockPos(0, FLOOR, 0), woodland));
        Set<BlockPos> beside = clearedOf(
                Overgrowth.overPaving(belt, new BlockPos(1, FLOOR, 0), woodland));

        for (int dy = 1; dy <= 6; dy++) {
            assertFalse(atTheTrunk.contains(new BlockPos(0, FLOOR + dy, 0)),
                    "the camp's trunk was cut at course " + dy);
        }
        assertTrue(atTheTrunk.contains(new BlockPos(0, FLOOR + 8, 0)),
                "but the bough over the carriageway is the road's");
        assertTrue(beside.contains(new BlockPos(1, FLOOR + 1, 0)),
                "and so is the litter on the stones, belt or no belt");
    }

    @Test
    void andTheColumnNextToItIsStillTheRoads() {
        // The belt is a ring and a road crosses it, so the same cross-section
        // has columns on both sides of the line. Sparing is asked per column.
        FakeSky belt = new FakeSky().ground(4).trunk(0, 0, 5).trunk(2, 0, 5);
        Overgrowth.Spared woodland = (x, z) -> x == 0;

        Set<BlockPos> spared = clearedOf(
                Overgrowth.overPaving(belt, new BlockPos(0, FLOOR, 0), woodland));
        Set<BlockPos> taken = clearedOf(
                Overgrowth.overPaving(belt, new BlockPos(2, FLOOR, 0), woodland));

        assertFalse(spared.contains(new BlockPos(0, FLOOR + 1, 0)));
        assertTrue(taken.contains(new BlockPos(2, FLOOR + 1, 0)),
                "a tree outside the claim is not the forester's");
    }

    @Test
    void aRoadIsAllowedToRunUnderSomebodysFloor() {
        // The sweep stops at the first thing that is neither growth nor air. A
        // bridge, an arch or a raised walkway over a way is not the town's canopy
        // and a road that ate one would be a worse bug than the one being fixed.
        FakeSky underpass = new FakeSky().ground(4)
                .put(0, FLOOR + 1, 0, Overgrowth.Cover.GROUND)
                .put(0, FLOOR + 4, 0, Overgrowth.Cover.KEEP)
                .put(0, FLOOR + 6, 0, Overgrowth.Cover.LEAF);

        Set<BlockPos> gone = clearedOf(
                Overgrowth.overPaving(underpass, new BlockPos(0, FLOOR, 0),
                        Overgrowth.NOTHING_SPARED));

        assertTrue(gone.contains(new BlockPos(0, FLOOR + 1, 0)), "the litter underfoot goes");
        assertFalse(gone.contains(new BlockPos(0, FLOOR + 4, 0)), "the floor above stays");
        assertFalse(gone.contains(new BlockPos(0, FLOOR + 6, 0)),
                "and whatever is above that is not the road's business");
    }
}
