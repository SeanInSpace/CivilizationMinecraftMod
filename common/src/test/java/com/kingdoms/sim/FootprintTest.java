package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the dimensions a building keeps, which is what anything drawing it needs. */
class FootprintTest {

    @Test
    void abuildingStartsNotKnowingItsOwnSize() {
        Building building = new Building("kingdoms:house", new SimPos(0, 64, 0), 1, false);
        assertFalse(building.footprint().isKnown(),
                "nothing is known until its plan has actually been built");
    }

    @Test
    void anUnbuiltFootprintIsNeverDrawn() {
        assertFalse(Footprint.UNKNOWN.isKnown());
        assertFalse(new Footprint(64, 0, 0, 0).isKnown(), "zero span is not a building");
    }

    @Test
    void aplotIsWiderThanTheBuildingItHolds() {
        // A 7x7 hall with one block of doorstep round it is a 9x9 plot. The
        // recorded size is the plot, because that step is part of what the town has
        // taken for that building — the map and the lamp draw it.
        //
        // It used to be two blocks each side. Two blocks of ground taken on every
        // side of every building is what a player saw as a scraped-flat pad round
        // each hut, and it held the next building that much further off.
        Footprint plot = new Footprint(64, 7 + 2, 7 + 2, 5);

        assertTrue(plot.covers(100, 100, 104, 100), "four east is still the hall's doorstep");
        assertFalse(plot.covers(100, 100, 105, 100), "five east belongs to nobody");
    }

    @Test
    void thewallsCanBeRecoveredFromTheRecordedPlot() {
        // Everything that wants the building rather than its plot takes the margin
        // back off the recorded span: the audit's shelf, fluid and doorway checks,
        // and the farmer scanning their own field. A margin that did not survive
        // that round trip is exactly how the farmer came to scan a ring of fence
        // instead of soil, and it went unnoticed because the two numbers agreed by
        // coincidence rather than by reference.
        int margin = 1;   // BlueprintPlacer.APRON_MARGIN, which common cannot see
        for (int walls : new int[]{1, 3, 4, 5, 7, 9, 11, 13}) {
            Footprint plot = new Footprint(64, walls + 2 * margin, walls + 2 * margin, 4);
            assertEquals(walls / 2, plot.width() / 2 - margin,
                    "a " + walls + "-wide building must come back out of its own plot");
        }
    }

    @Test
    void widthAndDepthAreTheFullSpanNotARadius() {
        // A 7x7 hall centered on its origin reaches three blocks each way.
        Footprint hall = new Footprint(64, 7, 7, 5);

        assertTrue(hall.covers(100, 100, 103, 100), "three east is inside");
        assertTrue(hall.covers(100, 100, 97, 103), "and three north-west");
        assertFalse(hall.covers(100, 100, 104, 100), "four east is outside");
        assertFalse(hall.covers(100, 100, 100, 104));
    }

    @Test
    void anEvenSpanRoundsTheSameWayThePlacerDoes() {
        // The placer builds from -width/2 to +width/2 with integer division, so an
        // even span is lopsided in exactly this way. Drawing has to agree with it.
        Footprint even = new Footprint(64, 4, 4, 3);
        assertTrue(even.covers(0, 0, 2, 0));
        assertFalse(even.covers(0, 0, 3, 0));
    }

    @Test
    void afootprintSurvivesBeingSetAndRead() {
        Building building = new Building("kingdoms:town_hall", new SimPos(8, 70, 8), 3, true);
        building.setFootprint(new Footprint(70, 7, 7, 6));

        assertTrue(building.footprint().isKnown());
        assertEquals(7, building.footprint().width());
        assertEquals(6, building.footprint().height());

        building.setFootprint(null);
        assertFalse(building.footprint().isKnown(), "null falls back rather than exploding");
    }
}
