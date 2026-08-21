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
        // A 7x7 hall on a plot cleared two blocks past its walls is an 11x11 plot.
        // The recorded size is the plot, because the cleared shelf is part of what
        // the town has taken for that building — the map and the lamp draw it.
        Footprint plot = new Footprint(64, 7 + 4, 7 + 4, 5);

        assertTrue(plot.covers(100, 100, 105, 100), "five east is still the hall's ground");
        assertFalse(plot.covers(100, 100, 106, 100), "six east belongs to nobody");
    }

    @Test
    void widthAndDepthAreTheFullSpanNotARadius() {
        // A 7x7 hall centred on its origin reaches three blocks each way.
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
