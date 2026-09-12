package com.civilization.neoforge.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@code /civ list} prints, asked without a server.
 *
 * <p>Written for a playtest report that said the listing printed three
 * settlements when six had been raised. The command has no radius and no page
 * size — it walks every kingdom in the dimension — so the count it printed was
 * true of the moment it was asked, and the moment was the point: the world
 * raises its spawn towns one per tick and everything past them one per second.
 * The claim under test is that the report says so rather than leaving the reader
 * to conclude the command drops towns.
 */
class SettlementListingTest {

    private static SettlementListing.Row town(String name, double away) {
        return new SettlementListing.Row(name, "Village", 12, 100, 64, -200,
                away, "none", 40, 300);
    }

    @Test
    void everySettlementIsListedNearestFirst() {
        String report = SettlementListing.report(List.of(
                town("Thornring", 900), town("Millbrook", 100), town("Stonebridge", 400)), 0);

        assertTrue(report.contains("=== 3 settlements in this dimension ==="));
        assertTrue(report.indexOf("Millbrook") < report.indexOf("Stonebridge"),
                "nearest first");
        assertTrue(report.indexOf("Stonebridge") < report.indexOf("Thornring"));
    }

    @Test
    void everyRowCarriesItsDistance() {
        String report = SettlementListing.report(List.of(town("Millbrook", 137.4)), 0);

        assertTrue(report.contains("137m away"),
                "the distance column is the reason the thing is sorted: " + report);
    }

    @Test
    void oneSettlementIsNotSettlements() {
        assertTrue(SettlementListing.report(List.of(town("Millbrook", 10)), 0)
                .contains("=== 1 settlement in this dimension ==="));
    }

    @Test
    void anEmptyWorldSaysSoRatherThanPrintingAHeaderAndNothing() {
        String report = SettlementListing.report(List.of(), 0);

        assertTrue(report.contains("Nothing has been founded in this world."));
    }

    @Test
    void aWorldStillRaisingItsTownsSaysHowManyAreLeft() {
        String report = SettlementListing.report(List.of(town("Millbrook", 10)), 6);

        assertTrue(report.contains("6 regions left to settle"),
                "the answer to \"where are the other six\": " + report);
        assertTrue(report.contains("ask again in a moment"));
    }

    @Test
    void andSaysNothingOfTheSortWhenTheyAreAllStanding() {
        String report = SettlementListing.report(List.of(town("Millbrook", 10)), 0);

        assertFalse(report.contains("left to settle"),
                "a settled world must not apologize for being finished");
    }

    @Test
    void oneRegionIsNotRegions() {
        assertTrue(SettlementListing.report(List.of(), 1).contains("1 region left"));
    }

    @Test
    void aRowIsOneLine() {
        assertEquals(1, SettlementListing.line(town("Millbrook", 10)).split("\n").length);
    }
}
