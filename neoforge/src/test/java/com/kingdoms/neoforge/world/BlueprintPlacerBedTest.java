package com.kingdoms.neoforge.world;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Beds;
import com.kingdoms.sim.settlement.BuildCatalog;
import com.kingdoms.sim.settlement.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the beds the simulation sends people to are the beds the placer lays.
 *
 * <p>The two halves of this feature never speak to each other at runtime. A
 * settler is walked to a block position worked out by {@link Beds}, which reads
 * no world and loads no chunk; the block is put there by {@link BlueprintPlacer}
 * some time earlier, possibly with nobody watching. If those two ever disagree
 * the failure is silent and total — the town walks into its houses at dusk and
 * stands there, because every bed is one cell from where the simulation is
 * certain it is.
 *
 * <p>{@code Beds} is the single table both read, so they cannot disagree about
 * the arithmetic. What they can still disagree about is the <em>turn</em>: the
 * placer swings the whole plan round to face the street and the simulation
 * swings a pair of offsets, and those are two separate pieces of code doing the
 * same rotation. So every home is drawn at every one of the four facings here,
 * and the block that comes out is compared with the position that was
 * predicted — including which way it faces and which half it is, because a bed
 * whose halves disagree pops itself the moment the world touches it.
 *
 * <p>Runs without a world for the same reason {@code BlueprintPlacerSizeTest}
 * does: what a shape draws depends on the declared size and its own geometry,
 * never on the hillside, so a flat fake site is enough.
 */
class BlueprintPlacerBedTest {

    private static final BlockPos BASE = new BlockPos(0, 63, 0);

    /** Level ground under everything, as the size test's fixture has it. */
    private static BlueprintPlacer.Site flatFor(Culture culture) {
        return new BlueprintPlacer.Site() {
            @Override public boolean loaded(BlockPos pos) { return true; }
            @Override public boolean unsupported(BlockPos pos) { return pos.getY() >= 64; }
            @Override public Culture culture() { return culture; }
            @Override public int groundLevel(int x, int z) { return 64; }
        };
    }

    /** Every home in the catalog: the kinds with room for heads in them. */
    private static List<BuildingType> homes() {
        List<BuildingType> homes = new ArrayList<>();
        for (BuildingType type : BuildCatalog.DEFAULT) {
            if (type.capacity() > 0) {
                homes.add(type);
            }
        }
        return homes;
    }

    private static String pathOf(BuildingType type) {
        return type.id().substring(type.id().indexOf(':') + 1);
    }

    /**
     * The finished building, cell by cell, turned to face the way it was told to.
     *
     * <p>Last writer wins, which is what {@code finish} does with a cell written
     * twice: a shape lays its floor and then stands furniture on it, and the
     * furniture is what is there.
     */
    private static Map<BlockPos, BlockState> drawn(Culture culture, String path, int facing) {
        List<BlueprintPlacer.Placement> blocks = new ArrayList<>();
        BlueprintPlacer.draw(flatFor(culture), blocks, path, BASE);
        BlueprintPlacer.turn(blocks, BASE, BlueprintPlacer.rotationOf(facing));
        Map<BlockPos, BlockState> built = new LinkedHashMap<>();
        for (BlueprintPlacer.Placement placement : blocks) {
            built.put(placement.pos(), placement.state());
        }
        return built;
    }

    private static BlockPos at(SimPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    private static final SimPos ORIGIN = new SimPos(0, 63, 0);

    // --- the check the file exists for ---------------------------------------

    @Test
    void everyHomeHasARealBedWhereTheSimulationSaysItIs() {
        for (BuildingType home : homes()) {
            String path = pathOf(home);
            for (int facing = 0; facing < 4; facing++) {
                Map<BlockPos, BlockState> built = drawn(Culture.DEFAULT, path, facing);

                for (int index = 0; index < Beds.countIn(home.id()); index++) {
                    BlockPos foot = at(Beds.footOf(home.id(), ORIGIN, facing, index));
                    BlockPos head = at(Beds.headOf(home.id(), ORIGIN, facing, index));
                    String where = path + " facing " + facing + " bed " + index;

                    BlockState footState = built.get(foot);
                    BlockState headState = built.get(head);
                    assertNotNull(footState, where + ": nothing at all at the foot "
                            + foot.toShortString() + " the simulation walks people to");
                    assertNotNull(headState, where + ": nothing at the head "
                            + head.toShortString());

                    assertTrue(footState.getBlock() instanceof BedBlock,
                            where + ": the foot cell holds " + footState.getBlock()
                                    + " rather than a bed");
                    assertTrue(headState.getBlock() instanceof BedBlock,
                            where + ": the head cell holds " + headState.getBlock());

                    assertEquals(BedPart.FOOT, footState.getValue(BedBlock.PART),
                            where + ": the cell a sleeper stands at is not the foot");
                    assertEquals(BedPart.HEAD, headState.getValue(BedBlock.PART),
                            where + ": both halves claim to be the same half, which"
                                    + " is a bed that pops the moment it is touched");

                    // Minecraft's own rule: a bed's head is one block along its
                    // facing from its foot, and both halves agree about which way
                    // that is. Either failing is a pair of blocks vanilla will not
                    // keep.
                    Direction facingOf = footState.getValue(BedBlock.FACING);
                    assertEquals(facingOf, headState.getValue(BedBlock.FACING),
                            where + ": the two halves face different ways");
                    assertEquals(head, foot.relative(facingOf),
                            where + ": the head is not where the foot's facing"
                                    + " points, so the pair is not a bed");
                    assertFalse(footState.getValue(BedBlock.OCCUPIED),
                            where + ": a newly built bed already has somebody in it");
                }
            }
        }
    }

    @Test
    void aBedCountsAsManyAsTheHouseHolds() {
        // Counted in the drawn blocks rather than in the table, so a shape that
        // quietly stopped calling for its beds is caught here as well.
        for (BuildingType home : homes()) {
            String path = pathOf(home);
            for (int facing = 0; facing < 4; facing++) {
                long halves = drawn(Culture.DEFAULT, path, facing).values().stream()
                        .filter(state -> state.getBlock() instanceof BedBlock)
                        .count();
                assertEquals(2L * home.capacity(), halves,
                        path + " at facing " + facing + " is declared to hold "
                                + home.capacity() + " and draws " + halves / 2.0
                                + " beds");
            }
        }
    }

    @Test
    void turningTheBuildingTurnsTheBedsWithIt() {
        // The half of the turn the position arithmetic cannot see. If the placer
        // rotated positions and left the states alone -- which is exactly the
        // fault the turn's own comment warns about for stairs and doors -- every
        // bed above would still be in the right cell and every one of them would
        // be facing the way it was drawn.
        Map<BlockPos, BlockState> north = drawn(Culture.DEFAULT, "longhouse", 0);
        Map<BlockPos, BlockState> quarter = drawn(Culture.DEFAULT, "longhouse", 1);

        Direction laid = north.get(at(Beds.footOf("kingdoms:longhouse", ORIGIN, 0, 0)))
                .getValue(BedBlock.FACING);
        Direction turned = quarter.get(at(Beds.footOf("kingdoms:longhouse", ORIGIN, 1, 0)))
                .getValue(BedBlock.FACING);

        assertEquals(Direction.NORTH, laid, "a longhouse's beds head into the cold wall");
        assertEquals(Rotation.CLOCKWISE_90.rotate(laid), turned,
                "a quarter turn of the building is a quarter turn of every bed in it");
    }

    // --- the one thing a culture has a say in --------------------------------

    @Test
    void everyPeopleSleepsUnderItsOwnColorAndNoneOfThemLoseABed() {
        // The color is a culture's, and nothing else about a bed is. A people
        // whose beds moved, or who got fewer of them, would be a people whose
        // houses the simulation cannot find the beds in.
        for (Culture culture : Culture.all()) {
            for (BuildingType home : homes()) {
                String path = pathOf(home);
                Map<BlockPos, BlockState> built = drawn(culture, path, 0);
                for (int index = 0; index < Beds.countIn(home.id()); index++) {
                    BlockState foot = built.get(at(Beds.footOf(home.id(), ORIGIN, 0, index)));
                    assertNotNull(foot, culture.id() + "'s " + path + " has no bed "
                            + index + " where every other people has one");
                    assertTrue(foot.getBlock() instanceof BedBlock);
                }
            }
        }
    }

    @Test
    void aPeopleWithAnOpinionAboutColorGetsADifferentBedFromOneWithout() {
        // Not which color -- that is a table and a table is not worth pinning --
        // but that the table is consulted at all. Orcs and lowlanders sleeping
        // under identically colored wool would mean the lookup was never wired in.
        BlockState lowland = drawn(Culture.NORMAN, "cottage", 0)
                .get(at(Beds.footOf("kingdoms:cottage", ORIGIN, 0, 0)));
        BlockState orc = drawn(Culture.ORC, "cottage", 0)
                .get(at(Beds.footOf("kingdoms:cottage", ORIGIN, 0, 0)));

        assertNotNull(lowland);
        assertNotNull(orc);
        assertFalse(lowland.is(orc.getBlock()),
                "every people sleeps under the same blanket, so the culture is"
                        + " not being asked");
    }
}
