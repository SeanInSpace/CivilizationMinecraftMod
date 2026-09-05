package com.kingdoms.neoforge.world;

import com.kingdoms.sim.culture.Culture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a repair is actually a plan for: the difference between the blueprint and
 * the wall that is there.
 *
 * <p>This is the check the reported fault wanted. A cottage with its walls taken
 * off was rebuilt within seconds as one whole placement — the entire blueprint
 * stamped back down, twenty times in four minutes, with no builder involved —
 * because a repair was expressed as work on the whole structure and finished on
 * the clock. Expressed as the diff it is a dozen blocks: the crew lays those and
 * charges for those, and a cottage that has lost one wall is one wall of work.
 *
 * <p><strong>Why it can run without a world.</strong> Everything here is the
 * plan against a reading of what stands, and both arrive through seams —
 * {@link BlueprintPlacer.Site} for the drawing, which
 * {@code BlueprintPlacerSizeTest} already relies on, and
 * {@link BlueprintPlacer.Standing} for the reading. So a house with one wall
 * knocked out is a map with some cells missing from it, and the diff is
 * something that can be counted rather than something that has to be walked to.
 */
class BlueprintPlacerDiffTest {

    /** A plot on ground whose first air block is 64, which is where a floor goes. */
    private static final BlockPos BASE = new BlockPos(0, 63, 0);

    /** Level ground under everything, as the size test's fixture has it. */
    private static BlueprintPlacer.Site flat() {
        return new BlueprintPlacer.Site() {
            @Override public boolean loaded(BlockPos pos) { return true; }
            @Override public boolean unsupported(BlockPos pos) { return pos.getY() >= 64; }
            @Override public Culture culture() { return Culture.DEFAULT; }
            @Override public int groundLevel(int x, int z) { return 64; }
        };
    }

    /**
     * The cottage's plan, exactly as the shape draws it.
     *
     * <p>Deliberately not tidied. The one thing removed is a placement of air,
     * which is not a placement at all but a hole somebody has to make — that is
     * how {@code finish} reads it too, and it never reaches the step list.
     * Everything else is left in, cells written twice included, because those are
     * the entries the diff got wrong: this shape lays a course of oak planks
     * across each wall and then drops an oak log into fourteen of those cells for
     * the corner posts. A fixture that quietly kept only the last write per cell
     * would agree with the diff about a plan that has no such cells in it, which
     * is not the plan the town builds from.
     *
     * <p>The drawing order is the order the writes land in. {@code finish} sorts
     * the list before it becomes steps, but its sort is stable and every cell
     * written twice here holds two full blocks, so a repeated cell keeps the
     * order it was drawn in either way.
     */
    private static List<BlueprintPlacer.Placement> cottage() {
        List<BlueprintPlacer.Placement> drawn = new ArrayList<>();
        BlueprintPlacer.draw(flat(), drawn, "cottage", BASE);
        List<BlueprintPlacer.Placement> solid = new ArrayList<>(drawn.size());
        for (BlueprintPlacer.Placement block : drawn) {
            if (!block.state().isAir()) {
                solid.add(block);
            }
        }
        return List.copyOf(solid);
    }

    /**
     * A world holding exactly what a plan leaves behind, minus whatever was taken
     * out of it.
     *
     * <p>Built by laying the plan rather than by describing the building
     * independently, because that is the premise: a building the town raised holds
     * the blocks the town laid, and damage is the difference from there. Laid in
     * order, so a cell written twice ends up holding the second block — which is
     * the whole reason the fixture is built this way round.
     */
    private static BlueprintPlacer.Standing worldHolding(
            List<BlueprintPlacer.Placement> plan, List<BlockPos> taken) {
        Map<BlockPos, BlockState> world = new LinkedHashMap<>();
        for (BlueprintPlacer.Placement block : plan) {
            world.put(block.pos(), block.state());
        }
        for (BlockPos gone : taken) {
            world.put(gone, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        return world::get;
    }

    /** The cells a plan writes more than once, which is where the diff went wrong. */
    private static List<BlockPos> writtenTwiceIn(List<BlueprintPlacer.Placement> plan) {
        Map<BlockPos, Integer> writes = new LinkedHashMap<>();
        for (BlueprintPlacer.Placement block : plan) {
            writes.merge(block.pos(), 1, Integer::sum);
        }
        List<BlockPos> twice = new ArrayList<>();
        writes.forEach((pos, count) -> {
            if (count > 1) {
                twice.add(pos);
            }
        });
        return twice;
    }

    /**
     * Every cell of the plan standing on the far side of the house from the door.
     *
     * <p>Cells, not writes: a corner of this wall is written twice and would
     * otherwise be counted twice by everything comparing sizes below.
     */
    private static List<BlockPos> oneWallOf(List<BlueprintPlacer.Placement> plan) {
        int north = Integer.MAX_VALUE;
        for (BlueprintPlacer.Placement block : plan) {
            if (block.pos().getY() > BASE.getY()) {
                north = Math.min(north, block.pos().getZ());
            }
        }
        Set<BlockPos> wall = new LinkedHashSet<>();
        for (BlueprintPlacer.Placement block : plan) {
            if (block.pos().getZ() == north && block.pos().getY() > BASE.getY()) {
                wall.add(block.pos());
            }
        }
        return List.copyOf(wall);
    }

    @Test
    void abuildingNobodyHasTouchedOwesNothingAtAll() {
        // The property the whole mechanism rests on, and the one the first cut of
        // this got wrong. If a sound building read as owing anything, a repair
        // would go on laying blocks over blocks that were already there, which is
        // the re-stamp under another name — and it would be charged for as well,
        // and could never be finished, because a debt nothing can pay off is one
        // the crew works at until the queue times them out.
        List<BlueprintPlacer.Placement> plan = cottage();

        assertTrue(BlueprintPlacer.owedOf(plan, worldHolding(plan, List.of())).isEmpty(),
                "the cottage is exactly what the cottage was drawn as");
    }

    @Test
    void acellThePlanWritesTwiceIsOwedOnceOrNotAtAll() {
        // Why the test above is not the tautology it looks like. This shape lays a
        // course of planks along each wall and then drops an oak log into the ends
        // of it for the corner posts, so fourteen of its cells are written twice —
        // and read naively, the planks step of every one of them is owed forever,
        // because a log is standing in it the day the cottage is finished. That is
        // fourteen phantom blocks on a building whose repair threshold is nine.
        List<BlueprintPlacer.Placement> plan = cottage();
        List<BlockPos> twice = writtenTwiceIn(plan);
        assertFalse(twice.isEmpty(),
                "the shape this fixture draws still writes some cell twice; if it"
                        + " ever stops, this test is no longer testing anything");

        // Knock one of those corners out and the crew owes it exactly once: the
        // block that is meant to be standing there, which is the second write.
        BlockPos corner = twice.getFirst();
        List<BlueprintPlacer.Placement> owed =
                BlueprintPlacer.owedOf(plan, worldHolding(plan, List.of(corner)));

        assertEquals(1, owed.size(), "one hole is one block of work");
        assertEquals(corner, owed.getFirst().pos());
        assertEquals(lastWriteAt(plan, corner).state().getBlock(),
                owed.getFirst().state().getBlock(),
                "and it is the corner post that goes back, not the course it replaced");
    }

    /** What the plan leaves standing at a cell: its last write there. */
    private static BlueprintPlacer.Placement lastWriteAt(
            List<BlueprintPlacer.Placement> plan, BlockPos pos) {
        BlueprintPlacer.Placement last = null;
        for (BlueprintPlacer.Placement block : plan) {
            if (block.pos().equals(pos)) {
                last = block;
            }
        }
        return last;
    }

    @Test
    void ahouseWithOneWallOutOwesThatWallAndNothingElse() {
        List<BlueprintPlacer.Placement> plan = cottage();
        List<BlockPos> wall = oneWallOf(plan);
        assertFalse(wall.isEmpty(), "the fixture took a wall out");

        List<BlueprintPlacer.Placement> owed =
                BlueprintPlacer.owedOf(plan, worldHolding(plan, wall));

        assertEquals(wall.size(), owed.size(),
                "one wall of work, not a whole cottage of it");
        for (BlueprintPlacer.Placement block : owed) {
            assertTrue(wall.contains(block.pos()),
                    block.pos() + " is still standing and is not the crew's business");
        }
        assertTrue(owed.size() < plan.size() / 4,
                "and it really is a fraction of the building: " + owed.size()
                        + " of " + plan.size());
    }

    @Test
    void theDiffKeepsThePlansOwnOrder() {
        // A repair walks the blueprint in mason's order because that is the only
        // order that puts a floor back before the wall standing on it. Filtering
        // must not shuffle it.
        List<BlueprintPlacer.Placement> plan = cottage();
        List<BlockPos> wall = oneWallOf(plan);
        List<BlueprintPlacer.Placement> owed =
                BlueprintPlacer.owedOf(plan, worldHolding(plan, wall));

        int last = -1;
        for (BlueprintPlacer.Placement block : owed) {
            int at = plan.indexOf(block);
            assertTrue(at > last, "the diff runs down the plan, never back up it");
            last = at;
        }
    }

    @Test
    void ablockSomebodySwappedIsOwedJustAsAMissingOneIs() {
        // Not "is this cell empty". A wall course replaced with something else is
        // not what the plan says, so it is put back — and the test costs nothing
        // to keep total, because there is no list of the ways a block can stop
        // being the right block.
        List<BlueprintPlacer.Placement> plan = cottage();
        BlueprintPlacer.Placement wall = lastWriteAt(plan, oneWallOf(plan).getFirst());

        Map<BlockPos, BlockState> world = new LinkedHashMap<>();
        for (BlueprintPlacer.Placement block : plan) {
            world.put(block.pos(), block.state());
        }
        world.put(wall.pos(),
                net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState());

        List<BlueprintPlacer.Placement> owed = BlueprintPlacer.owedOf(plan, world::get);

        assertEquals(1, owed.size(), "one block is wrong and one block is owed");
        assertEquals(wall.pos(), owed.getFirst().pos());
    }

    @Test
    void groundNobodyCanSeeIsNeverOwed() {
        // Half a reading is worse than none. A repair judged across the edge of
        // the loaded area would find the far half of the building absent and set
        // about rebuilding it out of chunks that were never there to be looked at
        // — the same rule the block census keeps about damage.
        List<BlueprintPlacer.Placement> plan = cottage();

        assertTrue(BlueprintPlacer.owedOf(plan, pos -> null).isEmpty(),
                "a building nobody can see owes nothing");
    }
}
