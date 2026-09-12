package com.civilization.neoforge.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What {@code /civ list} actually prints, with no server in it.
 *
 * <p>Split out of {@link CivilizationCommand} because of a playtest report that
 * could not be settled by reading the command: {@code /civ list} said three
 * settlements when the log had already said six were raised. Nothing in the
 * command explains that — it walks every kingdom in the dimension and every
 * settlement in every kingdom, with no radius, no page size and no early return,
 * and a {@code LIST} line goes into the log for each one it finds. So the
 * listing was right about the moment it was asked, and the moment was the
 * problem: the nine towns around the world spawn are raised one per tick by
 * {@code WorldgenSettlements.tickAnchor} and everything past those nine is
 * raised at most one per second as somebody walks near it, so a listing typed
 * while the world is still coming up is a snapshot of a total that is still
 * climbing.
 *
 * <p>That is not fixable, and it should not be: the towns are lazy on purpose.
 * What was fixable is that the listing gave no sign of it. So the report now
 * says when the world has not finished raising its spawn towns, and says how
 * many are still to come — which turns "the command is broken" into "ask again
 * in a moment".
 *
 * <p>Here rather than in the command so it can be tested. A settlement's row is
 * a pure function of ten numbers and strings; the only reason it ever needed a
 * server was that it was written inside a method that took one.
 */
public final class SettlementListing {

    private SettlementListing() {
    }

    /** A line break in a chat report. Named so no editor can eat the escape. */
    public static final String NEWLINE = String.valueOf((char) 10);

    /**
     * One settlement, as the listing cares about it.
     *
     * @param away blocks from whoever asked, on the horizontal
     */
    public record Row(String name, String stage, int population, int x, int y, int z,
                      double away, String wall, int coin, int food) {
    }

    /**
     * The whole report: a header, every settlement nearest first, and a note
     * when the world has not finished putting towns in it.
     *
     * @param spawnTownsPending how many of the world's spawn towns are still to
     *                          be raised, or zero when they are all standing
     */
    public static String report(List<Row> rows, int spawnTownsPending) {
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingDouble(Row::away));
        StringBuilder out = new StringBuilder("=== " + sorted.size() + " settlement"
                + (sorted.size() == 1 ? "" : "s") + " in this dimension ===");
        for (Row row : sorted) {
            out.append(NEWLINE).append(line(row));
        }
        if (sorted.isEmpty()) {
            out.append(NEWLINE).append("  Nothing has been founded in this world.");
        }
        if (spawnTownsPending > 0) {
            // The one thing the old listing could not say, and the whole reason
            // a session went by believing the command had lost three towns.
            out.append(NEWLINE).append("  (the world is still raising its spawn towns — ")
                    .append(spawnTownsPending)
                    .append(spawnTownsPending == 1 ? " region" : " regions")
                    .append(" left to settle; ask again in a moment)");
        }
        return out.toString();
    }

    /** One settlement's row, in the columns the report is aligned on. */
    public static String line(Row row) {
        return String.format(
                "  %-22s %-10s pop %-4d  at %6d %4d %6d  %5.0fm away"
                        + "  wall %-9s coin %-6d food %d",
                row.name(), row.stage(), row.population(),
                row.x(), row.y(), row.z(), row.away(),
                row.wall(), row.coin(), row.food());
    }
}
