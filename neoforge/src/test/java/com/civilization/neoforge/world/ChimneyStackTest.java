package com.civilization.neoforge.world;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildingSizes;
import com.civilization.sim.settlement.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every chimney in the mod comes out of a roof rather than standing beside one.
 *
 * <p>Playtest N13: <em>detached chimney stacks</em>. A roof is a single-block
 * shell and {@code Parts.chimney} drew a straight column up the plane of a short
 * wall. On a gable that wall is a solid triangle and the shaft is buried in it
 * until it comes out under the pot, which is what a chimney looks like. On a hip
 * the roof slopes away on all four sides, so the wall plane is the <em>lowest</em>
 * course of the roof: the shaft crossed it at the eave and then climbed five or
 * six courses of bare one-by-one column with air on every side, a stone post next
 * to a house. Every highland house, inn, hall and croft in the mod had one, and so
 * did every smithy whose people hip their roofs.
 *
 * <p><strong>What is asserted, and why it is this and not "the shaft has
 * something under it".</strong> The shaft always had something under it — it was
 * a continuous column from the floor, and the block under any cell of it was the
 * cell below. The fault was sideways. So the rule here is that no course of a
 * stack <em>at or below the roof's own top course</em> may have air on all four
 * sides: a chimney passing through a roof is touching that roof the whole way up
 * to the ridge, and only the pot standing proud of it is in open air. That is
 * exactly what was false before and true after, on every shape there is.
 *
 * <p>Level-free for the reason {@link BlueprintPlacerSizeTest} argues at length:
 * what a building draws depends on its declared size and its own geometry and on
 * nothing the ground is doing. A flat fake site is enough, and it lets this run on
 * every build instead of on somebody's afternoon.
 *
 * <p>Which columns are stacks is not decided here. {@link BlueprintPlacer#stacksAmong}
 * is the rule the mod itself uses to find a pot to put smoke over, and it is asked
 * rather than copied — a second opinion about what a chimney is would drift from
 * the first, and the half of this that matters most is that the stacks are still
 * <em>recognised</em>. A breast built one course too high stops every hearth in the
 * mod smoking, silently, and {@link #theBreastNeverSwallowsTheStackItIsThereToHold}
 * is the guard on that.
 */
class ChimneyStackTest {

    /** Ground at 64, floor at 64, as every other placer test has it. */
    private static final BlockPos BASE = new BlockPos(0, 63, 0);

    private static BlueprintPlacer.Site flatFor(Culture culture) {
        return new BlueprintPlacer.Site() {
            @Override public boolean loaded(BlockPos pos) { return true; }
            @Override public boolean unsupported(BlockPos pos) { return pos.getY() >= 64; }
            @Override public Culture culture() { return culture; }
            @Override public int groundLevel(int x, int z) { return 64; }
        };
    }

    /** One building, drawn, reduced to what is left standing at each cell. */
    private record Drawn(String what, Map<BlockPos, Block> standing, int height) {

        static Drawn of(Culture culture, String path) {
            List<BlueprintPlacer.Placement> blocks = new ArrayList<>();
            int[] dims = BlueprintPlacer.draw(flatFor(culture), blocks, path, BASE);
            Map<BlockPos, Block> standing = new HashMap<>();
            for (BlueprintPlacer.Placement placement : blocks) {
                if (placement.state().isAir()) {
                    standing.remove(placement.pos());
                } else {
                    standing.put(placement.pos(), placement.state().getBlock());
                }
            }
            return new Drawn(culture.id() + "'s " + path, standing, dims[2]);
        }

        List<BlockPos> stacks() {
            return BlueprintPlacer.stacksAmong(standing, BASE.getY());
        }

        /**
         * The shaft under a pot: down the same column while the block does not
         * change, which is what the placer's own "stacked" test reads one course of.
         */
        List<BlockPos> shaftUnder(BlockPos pot) {
            Block masonry = standing.get(pot);
            List<BlockPos> shaft = new ArrayList<>();
            for (BlockPos cell = pot; standing.get(cell) == masonry; cell = cell.below()) {
                shaft.add(cell);
            }
            return shaft;
        }

        /**
         * The roof this stack comes out of: the highest course standing in the
         * eight columns round it.
         *
         * <p>The eight neighbours and not the whole building, because a town hall
         * has a bell tower on it and a library has a lantern, and the highest
         * thing on a hall is nothing to do with the roof its kitchen chimney
         * crosses. It is also the same eight columns {@code clearsNeighbours}
         * measures, which is what makes the two halves of the assertion below
         * meet in the middle.
         */
        int roofBeside(BlockPos pot) {
            int top = BASE.getY();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    for (BlockPos cell : standing.keySet()) {
                        if (cell.getX() == pot.getX() + dx && cell.getZ() == pot.getZ() + dz) {
                            top = Math.max(top, cell.getY());
                        }
                    }
                }
            }
            return top;
        }

        boolean leansOnSomething(BlockPos cell) {
            for (Direction way : Direction.Plane.HORIZONTAL) {
                if (standing.containsKey(cell.relative(way))) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Every building this mod knows how to draw, in every people's idiom. */
    private static List<Drawn> everything() {
        List<Drawn> all = new ArrayList<>();
        for (Culture culture : Culture.all()) {
            for (BuildingType type : BuildCatalog.DEFAULT) {
                String id = type.id();
                all.add(Drawn.of(culture, id.substring(id.indexOf(':') + 1)));
            }
        }
        return all;
    }

    // --- the fault N13 reported ----------------------------------------------

    @Test
    void everyStackStandsExactlyAPotProudOfTheRoofBesideIt() {
        // The sharp half, and the one number that says "detached" without an eye.
        // BlueprintPlacer only calls a column a chimney when it clears all eight
        // of its neighbours by CHIMNEY_CLEARANCE, so a recognised stack is always
        // at least two courses proud. This is the ceiling that was missing: it may
        // be no MORE than two. A highland house used to stand its pot five courses
        // over the eave beside it and six over nothing at all.
        for (Drawn drawn : everything()) {
            for (BlockPos pot : drawn.stacks()) {
                int roof = drawn.roofBeside(pot);
                assertTrue(pot.getY() - roof <= Parts.POT_OVER_ROOF,
                        drawn.what() + " stands a chimney at " + pot.getX() + ","
                                + pot.getZ() + " " + (pot.getY() - roof) + " courses"
                                + " over the tallest thing beside it, where a pot is"
                                + " " + Parts.POT_OVER_ROOF + ". Everything between is"
                                + " bare one-by-one column, which is the stone post"
                                + " standing next to a house that playtest N13 saw on"
                                + " every hipped roof in the mod");
            }
        }
    }

    @Test
    void noCourseOfAStackBelowItsPotHasAirOnEverySide() {
        // The readable half. The shaft always had something UNDER it -- it is one
        // column from the floor up, so the block below any cell of it is the cell
        // below. The fault was sideways, and this is the sideways reading of it:
        // a chimney crossing a roof is touching that roof at every course, and only
        // the pot is in the open.
        for (Drawn drawn : everything()) {
            for (BlockPos pot : drawn.stacks()) {
                int roof = drawn.roofBeside(pot);
                for (BlockPos cell : drawn.shaftUnder(pot)) {
                    if (cell.getY() > roof) {
                        continue;   // the pot, which is supposed to be in the open
                    }
                    assertTrue(drawn.leansOnSomething(cell),
                            drawn.what() + " has a chimney at " + pot.getX() + ","
                                    + pot.getZ() + " whose course "
                                    + (cell.getY() - BASE.getY()) + " has air on all"
                                    + " four sides, under a roof beside it that reaches"
                                    + " course " + (roof - BASE.getY()));
                }
            }
        }
    }

    // --- and the two things a fix must not break -----------------------------

    @Test
    void theBreastNeverSwallowsTheStackItIsThereToHold() {
        // The breast tops out at the roof's own top course, which leaves the pot
        // exactly BlueprintPlacer.CHIMNEY_CLEARANCE above its tallest neighbour.
        // One course higher and stacksAmong stops recognising any of them --
        // silently, because a building with no stack falls back to smoking out of
        // its ridge and nothing in the game says which happened. So: the peoples
        // whose style declares a chimney still get one on the buildings that are
        // houses, and every people's smithy still gets its brick one.
        for (String people : new String[]{"civilization:human/highland",
                "civilization:human/burgher"}) {
            Culture culture = Culture.of(people);
            for (String path : new String[]{"house", "cottage", "croft", "inn",
                    "town_hall"}) {
                assertFalse(Drawn.of(culture, path).stacks().isEmpty(),
                        people + " declares a chimney and its " + path + " has none"
                                + " the placer can find, so every hearth in it is"
                                + " smokeless");
            }
        }
        for (Culture culture : Culture.all()) {
            assertFalse(Drawn.of(culture, "smith").stacks().isEmpty(),
                    culture.id() + "'s smithy has lost its brick chimney; a forge"
                            + " that does not smoke is a shed");
        }
    }

    @Test
    void holdingAStackUpAddsNoHeightToAnything() {
        // The regression the previous attempt at N13 shipped: it moved the shaft
        // inboard, which pushed the highland town hall from a declared height of
        // 17 to 19 and put a stone pillar in the middle of somebody's floor. A
        // breast is filled cells under an unchanged pot, so nothing it does can
        // reach past the height its kind declares.
        for (Culture culture : Culture.all()) {
            for (BuildingType type : BuildCatalog.DEFAULT) {
                String id = type.id();
                Drawn drawn = Drawn.of(culture, id.substring(id.indexOf(':') + 1));
                BuildingSizes.Size declared = BuildingSizes.of(id);
                if (declared == null) {
                    continue;
                }
                assertTrue(drawn.height() <= declared.height(),
                        drawn.what() + " is drawn " + drawn.height() + " courses tall"
                                + " against a declared height of " + declared.height());
            }
        }
    }
}
