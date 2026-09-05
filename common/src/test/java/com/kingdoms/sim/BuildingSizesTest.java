package com.kingdoms.sim;

import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildingSizes;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Footprint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one thing that must stay true of a building's size: everybody agrees on it.
 *
 * <p>This is a regression suite for a fault that was invisible for the whole life
 * of the mod and cost more than any other. A building's size was a literal in the
 * method that drew it and its plot was a column in the catalog, and the two had
 * drifted to about a factor of two apart — a cottage drawn five wide standing on
 * nine blocks of reserved ground, a house drawn five standing on eleven. Nothing
 * threw, nothing measured it, and every street in the mod was laid out for
 * buildings twice the size of the ones put on it. What a player saw was villages
 * of huts scattered in fields.
 *
 * <p>The same fault had already happened once before in the other direction, and
 * {@code BlueprintPlacer.procedural} still carries the note: buildings grew two
 * blocks per level while the reserved plot did not, so a fourth-level house was
 * drawn straight through its neighbor. It was answered by deleting the growth
 * rather than by making the two numbers agree, and the note ends "the size drawn
 * here must be checked against BuildPlanner.plotSpanOf rather than assumed to
 * fit". This is that check.
 */
class BuildingSizesTest {

    @Test
    void everythingATownCanBuildHasADeclaredSize() {
        for (BuildingType type : BuildCatalogue.DEFAULT) {
            assertNotNull(BuildingSizes.of(type.id()),
                    type.id() + " is in the catalog and nothing says how big it is, so"
                            + " it would be reserved the default plot and drawn at whatever"
                            + " literal its drawing method happens to carry");
        }
    }

    @Test
    void theGroundReservedIsTheGroundTheBuildingCovers() {
        for (BuildingType type : BuildCatalogue.DEFAULT) {
            assertEquals(BuildingSizes.plotSpanOf(type.id()), type.plotSpan(),
                    type.id() + " reserves a plot that is not what its own size comes to."
                            + " These are the two numbers that drifted; they are derived"
                            + " from one table now and this is what keeps them there");
        }
    }

    @Test
    void aPlotIsAlwaysBigEnoughForTheWallsAndADoorstep() {
        for (BuildingType type : BuildCatalogue.DEFAULT) {
            BuildingSizes.Size size = BuildingSizes.of(type.id());
            int widest = Math.max(size.width(), size.depth());
            assertTrue(type.plotSpan() >= widest + 2 * BuildingSizes.APRON,
                    type.id() + " is " + size.width() + " by " + size.depth()
                            + " and holds only " + type.plotSpan() + " blocks of ground,"
                            + " so its own walls run past its plot and into the next one");
        }
    }

    @Test
    void everyBuildingIsDrawnAboutItsOwnMiddle() {
        // Both spans odd. A building with an even span has no middle column, so
        // its origin sits off center and a quarter turn moves it half a block --
        // which is the difference between a plot the overlap check can reason
        // about and one it cannot.
        for (BuildingType type : BuildCatalogue.DEFAULT) {
            BuildingSizes.Size size = BuildingSizes.of(type.id());
            assertEquals(1, size.width() % 2, type.id() + " is even across");
            assertEquals(1, size.depth() % 2, type.id() + " is even deep");
        }
        assertThrows(IllegalArgumentException.class, () -> new BuildingSizes.Size(8, 7),
                "an even span should be refused where it is written, not discovered later");
    }

    @Test
    void aLevelledBuildingIsStillTheSameBuilding() {
        // Saves made while levels were drawn still hold ids in that shape, and a
        // house_l2 that could not find its size would be reserved the default
        // plot and drawn through its neighbor.
        assertEquals(BuildingSizes.of("kingdoms:house"), BuildingSizes.of("kingdoms:house_l2"));
        assertEquals(BuildingSizes.of("kingdoms:house"),
                BuildingSizes.of("kingdoms:norman/house_l3"));
    }

    @Test
    void nothingUnknownIsGivenGround() {
        assertEquals(BuildPlanner.DEFAULT_PLOT_SPAN,
                BuildingSizes.plotSpanOf("kingdoms:something_nobody_declared"));
    }

    // --- shapes that are not rectangles --------------------------------------

    @Test
    void theCroftIsBentRoundACorner() {
        BuildingSizes.Size croft = BuildingSizes.of("kingdoms:croft");
        assertTrue(croft.notch().isCut(), "the croft is the L, and it is not one");

        int rx = croft.width() / 2;
        int rz = croft.depth() / 2;
        assertTrue(croft.covers(-rx, -rz), "the far corner of the wing is the building");
        assertTrue(croft.covers(rx, rz), "and so is the far corner of the range");
        assertFalse(croft.covers(rx, -rz),
                "the corner the notch takes is the yard, not the house");
        assertTrue(croft.covers(0, 0), "and the middle is always the building");
    }

    @Test
    void aNotchThatReachesTheFarWallIsRefused() {
        // Two wings that do not meet are two buildings, and the walls of one of
        // them would be drawn along a line that has nothing on the other side.
        assertThrows(IllegalArgumentException.class,
                () -> new BuildingSizes.Size(11, 9, new BuildingSizes.Notch(11, 4, 1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new BuildingSizes.Size(11, 9, new BuildingSizes.Notch(4, 9, 1, 1)));
    }

    @Test
    void aFootprintKnowsWhichOfItsOwnCornersIsAYard() {
        // The point of carrying the notch this far. Everything that asks whether
        // a column is inside a building -- whether somebody is indoors, whether
        // ground is spoken for, what the town map fills in -- asks Footprint.
        Footprint plain = new Footprint(64, 9, 9, 4);
        assertTrue(plain.covers(100, 200, 104, 204), "a rectangle covers its own corner");

        Footprint bent = new Footprint(64, 9, 9, 4, new BuildingSizes.Notch(3, 3, 1, -1));
        assertTrue(bent.covers(100, 200, 96, 196), "the far corner is still the building");
        assertFalse(bent.covers(100, 200, 104, 196), "and the bitten corner is not");
        assertTrue(bent.covers(100, 200, 104, 204),
                "only the one corner is taken, not both ends of the axis");
    }

    @Test
    void aFootprintFromAnOlderSaveIsSimplyARectangle() {
        // The notch is optional in the codec and absent from every save written
        // before shapes existed, so the four-argument form has to keep meaning
        // exactly what it always meant.
        Footprint old = new Footprint(64, 7, 7, 3);
        assertFalse(old.notch().isCut());
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                assertTrue(old.covers(0, 0, dx, dz),
                        "a footprint with no notch covers all of itself");
            }
        }
    }
}
