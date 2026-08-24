package com.kingdoms.neoforge.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a building decides what height to sit at.
 *
 * <p>Everything about how a structure meets sloping ground is a few lines of
 * arithmetic, and its own javadoc used to end by regretting that there was
 * nowhere to pin them. There is now. Both of the failures these lines encode
 * were expensive: a floor one block proud opens its doorway at chest height,
 * and a field one block sunk sits level with the surrounding grade, which is
 * exactly where a pond beside the plot holds its water.
 */
class BlueprintPlacerFloorTest {

    private static final String HOUSE = "kingdoms:house";
    private static final String FARM = "kingdoms:farm";

    @Test
    void aStructureFloorReplacesTheTopOfTheSoil() {
        // One below the first air, so the floor course goes where the turf was
        // rather than on top of it. A block proud, and the doorway opens at
        // chest height, which is no doorway at all.
        assertEquals(63, BlueprintPlacer.floorFor(64));
        assertEquals(BlueprintPlacer.floorFor(64) + 1, 64,
                "and a person standing on that floor is at the old ground level");
    }

    @Test
    void aFieldSitsWhereAPlayerWouldTill() {
        // Not one below. A field lays its farmland below its own base, so
        // sinking the base sinks the crops to grade — where any pond at natural
        // level reaches them, which is how fields flooded from the rim while
        // the farmland underneath stayed perfectly intact.
        assertEquals(64, BlueprintPlacer.baseFor(FARM, 64));
        assertEquals(63, BlueprintPlacer.baseFor(HOUSE, 64));
        assertEquals(64, BlueprintPlacer.baseFor("kingdoms:norman/farm_l2", 64),
                "whatever level or style the field is built in");
        assertEquals(63, BlueprintPlacer.baseFor("kingdoms:animal_farm", 64),
                "and the animal farm is not a field");
    }

    @Test
    void aFlatPlotSitsAtItsOwnGround() {
        int[] flat = {64, 64, 64, 64, 64};

        assertEquals(63, BlueprintPlacer.baseAcross(HOUSE, flat));
    }

    @Test
    void aSlopeIsMetAtTheMedianRatherThanCutToTheLowest() {
        // Taking the lowest cuts the whole plot down to meet it, and every
        // building on anything but a billiard table ends up in a squared-off
        // pit of its own making. The median cuts half down and packs half up.
        int[] slope = {64, 65, 66, 67, 68};

        assertEquals(BlueprintPlacer.floorFor(66), BlueprintPlacer.baseAcross(HOUSE, slope),
                "the middle of the slope, not the bottom of it");
    }

    @Test
    void oneBoulderDoesNotDragTheFloorUp() {
        int[] lumpy = {64, 64, 64, 64, 90};

        assertEquals(BlueprintPlacer.floorFor(64), BlueprintPlacer.baseAcross(HOUSE, lumpy));
    }

    @Test
    void theFloorIsHeldDownToWhatTheUnderpinningCanReach() {
        // Half the plot a good way below the other half is real slope, not a
        // freak column, so the cap binds and the building sits low enough for
        // its foundation to reach the ground under all of it.
        int[] step = {60, 60, 80, 80, 80};

        int base = BlueprintPlacer.baseAcross(HOUSE, step);

        assertEquals(60 + BlueprintPlacer.FOUNDATION_DEPTH, base,
                "held down to what the foundation can actually stand on");
        assertTrue(base < BlueprintPlacer.floorFor(80),
                "which is lower than the median alone would have put it");
    }

    @Test
    void oneCaveMouthDoesNotSinkTheWholeBuilding() {
        // The bug this test was written for. Measuring the cap from the single
        // lowest column undid the median it had just been at pains to compute:
        // one hole in a corner dragged the building down to within three blocks
        // of the bottom of it. A house sunk eleven blocks into a hillside has
        // its doorway underground, which is what "no doorway at grade on any
        // side" looks like from the audit.
        int[] pitted = {50, 64, 64, 64, 64};

        assertEquals(BlueprintPlacer.floorFor(64), BlueprintPlacer.baseAcross(HOUSE, pitted),
                "the plot is flat ground with one hole in it, and sits at the flat");
    }

    @Test
    void aPlotSplitEvenlyBetweenTwoHeightsSitsLowEnoughToReachBoth() {
        // Half at 64 and half at 70 is a genuine step rather than an outlier,
        // so the foundation cap binds: the median alone would stand the
        // building five blocks clear of half its own plot.
        int[] even = {64, 64, 70, 70};

        assertEquals(64 + BlueprintPlacer.FOUNDATION_DEPTH,
                BlueprintPlacer.baseAcross(HOUSE, even),
                "low enough that the underpinning reaches the lower half");
    }

    @Test
    void theFloorNeverDependsOnTheOrderTheColumnsWereRead() {
        int[] ordered = {64, 65, 66, 67, 68};
        int[] jumbled = {67, 64, 68, 66, 65};

        assertEquals(BlueprintPlacer.baseAcross(HOUSE, ordered),
                BlueprintPlacer.baseAcross(HOUSE, jumbled),
                "a plot is the same plot whichever corner it was surveyed from");
    }

    @Test
    void surveyingDoesNotDisturbTheHeightsItWasGiven() {
        // It sorts a copy. Handing the caller's array back re-ordered would
        // scramble the column-to-position mapping everything downstream uses.
        int[] heights = {67, 64, 68, 66, 65};
        int[] untouched = heights.clone();

        BlueprintPlacer.baseAcross(HOUSE, heights);

        org.junit.jupiter.api.Assertions.assertArrayEquals(untouched, heights);
    }
}
