package com.kingdoms.neoforge.world;

import com.keystone.api.LoadedBlueprint;
import com.keystone.blueprint.Blueprint;
import com.keystone.blueprint.BlueprintNbt;
import com.keystone.blueprint.Transforms;
import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Beds;
import com.kingdoms.sim.settlement.BlueprintCheck;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingSizes;
import com.kingdoms.sim.settlement.Field;
import com.kingdoms.sim.settlement.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a building out of a file is as legible to the town as one the code drew.
 *
 * <p>The drawn buildings get away with something an authored one cannot. A
 * settler finds their bed because {@link Beds} is one table the placer lays from
 * and the simulation reads from, so the two cannot disagree; a worker finds the
 * door because every drawing puts one in the middle of the south wall. Neither
 * is true of somebody else's cottage. So its beds and its door are <em>found</em>
 * in the file, once, and written on the building's own record — and everything
 * downstream goes on asking the simulation rather than the world, which is the
 * rule that makes an unwatched town possible at all.
 *
 * <p>Runs without a world, the way {@code BlueprintPlacerBedTest} does and for
 * the same reason: what is in a file depends on the file, never on the hillside.
 */
class AuthoredBuildingTest {

    private static final SimPos ORIGIN = new SimPos(100, 64, -40);

    /** The example cottage, authored facing {@code authoredAs}, turned to stand at {@code facing}. */
    private static AuthoredReading.Reading reading(int authoredAs, int facing) {
        Blueprint file = ExampleCottage.blueprint(authoredAs);
        LoadedBlueprint loaded = new LoadedBlueprint(Transforms.apply(file,
                Transforms.between(authoredAs, facing), Mirror.NONE));
        assertEquals(facing, loaded.facing(),
                "a structure turned to face a street faces it");
        return AuthoredReading.read("kingdoms:cottage", loaded, facing);
    }

    /** A standing cottage with the file's facts on it, as a town would hold it. */
    private static Building cottage(int facing, Building.Authored facts) {
        Building building = new Building("kingdoms:cottage", ORIGIN, 1, true);
        building.setFacing(facing);
        building.setFootprint(new Footprint(ORIGIN.y(),
                ExampleCottage.WIDTH + 2 * BuildingSizes.APRON,
                ExampleCottage.DEPTH + 2 * BuildingSizes.APRON,
                ExampleCottage.HEIGHT));
        building.setAuthored(facts);
        return building;
    }

    // --- the file is read at all ---------------------------------------------

    @Test
    void theCottageInTheFilePassesTheCheck() {
        List<BlueprintCheck.Finding> findings =
                BlueprintCheck.of(reading(0, 0).survey());

        assertTrue(BlueprintCheck.passes(findings),
                "a file laid out to agree with the drawn cottage has nothing"
                        + " standing against it: " + findings);
    }

    @Test
    void itsBedsPostAndDoorwayAreAllFound() {
        BlueprintCheck.Survey survey = reading(0, 0).survey();

        assertEquals(3, survey.beds(), "three beds, counted once each rather than per half");
        assertTrue(survey.post(), "the author's own post, so none is added");
        assertTrue(survey.door(), "the hole cut in the south wall");
        assertEquals(ExampleCottage.WIDTH, survey.width());
        assertEquals(ExampleCottage.DEPTH, survey.depth());
        assertEquals(ExampleCottage.HEIGHT, survey.height());
    }

    // --- the beds are the file's, at every facing -----------------------------

    @Test
    void everyBedIsWhereTheFilePutItAtEveryFacing() {
        for (int facing = 0; facing < 4; facing++) {
            // Authored facing south and turned, and authored facing east and
            // turned back: both must arrive at the same building, because the
            // stated front is the thing that makes them the same building.
            for (int authoredAs = 0; authoredAs < 4; authoredAs++) {
                Building.Authored facts = reading(authoredAs, facing).facts();
                Building standing = cottage(facing, facts);
                String where = "authored " + authoredAs + ", standing " + facing;

                assertEquals(3, Beds.countIn(standing), where + ": three beds");
                for (int index = 0; index < 3; index++) {
                    SimPos foot = Beds.footOf(standing, index);
                    SimPos head = Beds.headOf(standing, index);
                    assertNotNull(foot, where + ": bed " + index + " has a foot");
                    assertNotNull(head, where + ": bed " + index + " has a head");
                    assertEquals(ORIGIN.y() + Beds.FLOOR_COURSE, foot.y(),
                            where + ": furniture stands on the floor");
                    assertEquals(1, Math.abs(foot.x() - head.x()) + Math.abs(foot.z() - head.z()),
                            where + ": a bed's two halves lie next to each other");
                }
            }
        }
    }

    @Test
    void theFilesBedsAreTheSameCellsTheDrawnCottageUses() {
        // The example is laid out to agree with the drawn cottage, so the two
        // sets of cells must be the same set. This is what makes a failure
        // anywhere else in this file about the loading rather than about the
        // example's design.
        Building authored = cottage(0, reading(0, 0).facts());

        List<SimPos> fromFile = new ArrayList<>();
        List<SimPos> fromTable = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            fromFile.add(Beds.footOf(authored, index));
            fromTable.add(Beds.footOf("kingdoms:cottage", ORIGIN, 0, index));
        }
        fromFile.sort(AuthoredBuildingTest::byPosition);
        fromTable.sort(AuthoredBuildingTest::byPosition);

        assertEquals(fromTable, fromFile);
    }

    @Test
    void aDrawnBuildingGoesOnReadingTheTable() {
        // The other half of the rule: nothing about this feature may change what
        // a building the code drew does. An un-authored cottage has no facts on
        // it at all, and Beds falls straight through to the table.
        Building drawn = new Building("kingdoms:cottage", ORIGIN, 1, true);

        assertFalse(drawn.isAuthored());
        assertEquals(Beds.countIn("kingdoms:cottage"), Beds.countIn(drawn));
        assertEquals(Beds.footOf("kingdoms:cottage", ORIGIN, 0, 1), Beds.footOf(drawn, 1));
    }

    // --- the door -------------------------------------------------------------

    @Test
    void theDoorstepIsTheOneTheDrawnCottageWouldHave() {
        // Same building, same door, so the same block to stand on. If these ever
        // differ, an authored building gets its paths run to a different cell
        // from the one workers walk to, and only one of the two is right.
        Building authored = cottage(0, reading(0, 0).facts());
        Building drawn = new Building("kingdoms:cottage", ORIGIN, 1, true);
        drawn.setFootprint(authored.footprint());

        assertEquals(drawn.doorstep(), authored.doorstep());
    }

    @Test
    void theDoorstepFollowsTheBuildingRoundEveryQuarter() {
        for (int facing = 0; facing < 4; facing++) {
            Building.Authored facts = reading(0, facing).facts();
            assertNotNull(facts.doorstep(),
                    "facing " + facing + ": the doorway was found, rather than"
                            + " the building quietly falling back to the drawn arithmetic");
            Building authored = cottage(facing, facts);
            Building drawn = new Building("kingdoms:cottage", ORIGIN, 1, true);
            drawn.setFacing(facing);
            drawn.setFootprint(authored.footprint());

            assertEquals(drawn.doorstep(), authored.doorstep(),
                    "facing " + facing + ": the door is on the street side");
        }
    }

    @Test
    void aBuildingWhoseHeightIsCorrectedTakesItsFurnitureWithIt() {
        // Positions are kept as offsets rather than as world coordinates for
        // exactly this: a building planned in an unloaded chunk carries an
        // estimated height, and placement writes the real one back.
        Building authored = cottage(0, reading(0, 0).facts());
        SimPos before = Beds.footOf(authored, 0);

        authored.setOriginY(ORIGIN.y() + 7);

        assertEquals(before.y() + 7, Beds.footOf(authored, 0).y());
    }

    // --- the file that must not be placed ------------------------------------

    @Test
    void aCottageFileDrawnNineAcrossIsARefusedSizeMismatch() {
        // The fault the whole check exists for. Nine by nine is a perfectly
        // reasonable thing to build, and the plan reserved ground for seven: the
        // walls would go through whatever is on the next plot, and the town
        // would go on believing the neighbour was standing.
        BlueprintCheck.Survey oversize = AuthoredReading.read("kingdoms:cottage",
                new LoadedBlueprint(box(9, 5, 9)), 0).survey();

        assertEquals(9, oversize.width());
        List<BlueprintCheck.Finding> findings = BlueprintCheck.of(oversize);
        assertFalse(BlueprintCheck.passes(findings), "refused: " + findings);
        assertTrue(findings.stream().anyMatch(f -> f.message().startsWith("SIZE MISMATCH")),
                "and said so by name, because that is the line somebody greps the log for");
    }

    @Test
    void aFileIsMeasuredInItsOwnFrameRatherThanTheOneItIsTurnedTo() {
        // Nine across and seven back, standing at a quarter turn. Compared
        // against the size table with its axes still swapped it would read as
        // seven by nine, and a building genuinely too wide would pass.
        BlueprintCheck.Survey turned = AuthoredReading.read("kingdoms:cottage",
                new LoadedBlueprint(Transforms.apply(box(9, 5, 7),
                        Rotation.CLOCKWISE_90, Mirror.NONE)), 1).survey();

        assertEquals(9, turned.width(), "nine across is nine across whichever way it is standing");
        assertEquals(7, turned.depth());
    }

    // --- which file a town asks for -------------------------------------------

    @Test
    void aTownAsksForItsOwnCulturesFileBeforeThePlainOne() {
        // The mechanism by which an authored building is chosen at all. A norman
        // town building a cottage asks for norman/cottage first and plain
        // cottage after, so dropping one file into the world folder replaces
        // that culture's cottages and nobody else's -- and a culture that has
        // drawn nothing inherits the common building rather than a blank.
        List<Identifier> asked = BlueprintPlacer.styleCandidates(
                Identifier.parse("kingdoms:cottage"), Culture.DEFAULT);

        assertEquals(2, asked.size(), "the styled name, then the plain one: " + asked);
        assertTrue(asked.get(0).getPath().endsWith("/cottage"),
                "the culture's own first: " + asked);
        assertEquals("cottage", asked.get(1).getPath(),
                "and the common one as a fallback");
    }

    @Test
    void aNameThatAlreadySaysItsStyleIsLeftAlone() {
        // A datapack asking for a particular style outright must get it rather
        // than have the local culture imposed on top of it.
        List<Identifier> asked = BlueprintPlacer.styleCandidates(
                Identifier.parse("kingdoms:mire/cottage"), Culture.DEFAULT);

        assertEquals("mire/cottage", asked.get(0).getPath());
        assertEquals("cottage", asked.get(1).getPath());
    }

    // --- the field ------------------------------------------------------------

    @Test
    void aFarmsLedgerCountsItsOwnCropsRatherThanTheDrawnFarms() {
        Building farm = new Building("kingdoms:farm", ORIGIN, 1, true);
        farm.setAuthored(new Building.Authored(List.of(), List.of(), null, 30));

        assertEquals(30, Field.cropBlocksOf(farm),
                "a smaller field feeds fewer people, which is the whole of what"
                        + " a smaller field means");
        assertEquals(Field.CROP_BLOCKS,
                Field.cropBlocksOf(new Building("kingdoms:farm", ORIGIN, 1, true)),
                "and a drawn farm is still the drawn farm");
    }

    // --- the file goes to disk and comes back the same ------------------------

    @Test
    void theWholeFileSurvivesTheFormatItIsStoredIn() {
        // The loader's own round trip, on a structure with real block states in
        // it rather than the empty ones keystone's own tests can use without a
        // registry. What comes back has to be the same building, or every
        // authored cottage changes the first time the game is restarted.
        Blueprint written = ExampleCottage.blueprint(2);

        Blueprint back = BlueprintNbt.read(BlueprintNbt.write(written),
                BuiltInRegistries.BLOCK);

        assertEquals(written.size(), back.size());
        assertEquals(written.anchor(), back.anchor());
        assertEquals(2, back.facing());
        assertEquals(written.blocks().size(), back.blocks().size());

        BlueprintCheck.Survey survey = AuthoredReading.read("kingdoms:cottage",
                new LoadedBlueprint(Transforms.apply(back,
                        Transforms.between(2, 0), Mirror.NONE)), 0).survey();
        assertEquals(3, survey.beds(), "beds keep both their halves and their heading");
        assertTrue(survey.post(), "the author's post survives as a block state");
        assertTrue(survey.door(), "and the hole they cut is still a hole");
        assertTrue(BlueprintCheck.passes(BlueprintCheck.of(survey)));
    }

    // --- helpers --------------------------------------------------------------

    /** A solid box of the given size, anchored at the middle of its floor. */
    private static Blueprint box(int width, int height, int depth) {
        List<Blueprint.BlueprintBlock> blocks = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    blocks.add(new Blueprint.BlueprintBlock(new BlockPos(x, y, z),
                            Blocks.COBBLESTONE.defaultBlockState(), null));
                }
            }
        }
        return new Blueprint(new Vec3i(width, height, depth), blocks,
                new BlockPos(width / 2, 0, depth / 2), Blueprint.Meta.NONE);
    }

    private static int byPosition(SimPos a, SimPos b) {
        int byX = Integer.compare(a.x(), b.x());
        if (byX != 0) {
            return byX;
        }
        int byY = Integer.compare(a.y(), b.y());
        return byY != 0 ? byY : Integer.compare(a.z(), b.z());
    }
}
