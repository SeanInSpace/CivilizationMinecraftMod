package com.kingdoms.neoforge.world;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.settlement.BuildingSizes;
import com.kingdoms.sim.settlement.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How many crop blocks a farm has, asked of the thing that lays them.
 *
 * <p>{@link Field#CROP_BLOCKS} is the number the whole food economy is
 * proportional to: an unwatched town's harvest is that many blocks ripening over
 * the ripening time, and nothing else. It lives in {@code common}, which has
 * never seen a block in its life, so it is a written-down count of what
 * {@link BlueprintPlacer#farm} draws — and a written-down count of somebody
 * else's drawing is a number that drifts the first time the drawing changes.
 *
 * <p>So this counts them. Same seam and same reasoning as
 * {@link BlueprintPlacerSizeTest}: the placement list a builder returns is
 * decided by the blueprint and not by the ground, so a flat fake site is enough
 * and no {@code ServerLevel} is needed. Widen the field, take the water channel
 * out, move the post — any of those changes the count, this fails, and the
 * constant gets changed on purpose rather than silently going wrong.
 */
class BlueprintPlacerFieldTest {

    private static final BlockPos BASE = new BlockPos(0, 63, 0);

    private static BlueprintPlacer.Site flat() {
        return new BlueprintPlacer.Site() {
            @Override public boolean loaded(BlockPos pos) { return true; }
            @Override public boolean unsupported(BlockPos pos) { return pos.getY() >= 64; }
            @Override public Culture culture() { return Culture.DEFAULT; }
            @Override public int groundLevel(int x, int z) { return 64; }
        };
    }

    /**
     * What the farm actually ends up made of, one entry per position.
     *
     * <p>Last placement wins, because that is what laying blocks into a world
     * does. It matters here: the farm post is drawn on top of a cell that has
     * already had wheat put on it, and counting the raw list rather than the
     * result would say seventy-two.
     */
    private static Map<BlockPos, BlockState> drawnFarm() {
        List<BlueprintPlacer.Placement> laid = new ArrayList<>();
        BlueprintPlacer.draw(flat(), laid, "farm", BASE);
        Map<BlockPos, BlockState> world = new LinkedHashMap<>();
        for (BlueprintPlacer.Placement p : laid) {
            world.put(p.pos(), p.state());
        }
        return world;
    }

    @Test
    void theFieldHasExactlyTheCropBlocksTheSimulationCountsOn() {
        long wheat = drawnFarm().values().stream()
                .filter(state -> state.is(Blocks.WHEAT))
                .count();

        assertEquals(Field.CROP_BLOCKS, wheat,
                "the placer lays " + wheat + " wheat blocks and Field.CROP_BLOCKS"
                        + " says " + Field.CROP_BLOCKS + ". That constant is what an"
                        + " unwatched town's whole harvest is proportional to, so the"
                        + " two drifting apart is a town quietly fed the wrong amount");
    }

    @Test
    void everyCropBlockHasFarmlandUnderIt() {
        // The premise the count rests on. A wheat block laid on anything else is
        // a crop that pops off its soil, and a field that is smaller than the
        // ledger thinks it is.
        Map<BlockPos, BlockState> world = drawnFarm();
        for (Map.Entry<BlockPos, BlockState> cell : world.entrySet()) {
            if (!cell.getValue().is(Blocks.WHEAT)) {
                continue;
            }
            BlockState under = world.get(cell.getKey().below());
            assertTrue(under != null && under.is(Blocks.FARMLAND),
                    "wheat at " + cell.getKey() + " stands on "
                            + (under == null ? "nothing the placer laid" : under));
        }
    }

    @Test
    void theFieldFitsInsideThePlotTheCatalogReservesForIt() {
        // Field's javadoc says eleven by eleven and counts its blocks off that.
        // If the plot ever changed size the count would be wrong before anybody
        // looked at the drawing.
        BuildingSizes.Size declared = BuildingSizes.of("kingdoms:farm");

        assertEquals(11, declared.width(), "the count in Field assumes an 11-wide field");
        assertEquals(11, declared.depth());
    }
}
