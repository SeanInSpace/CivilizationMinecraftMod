package com.kingdoms.sim;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Ground recorded out of a real world, for tests that need real ground.
 *
 * <p>Every fake terrain in this suite is smooth. {@code TerrainFake} is three
 * sine waves whose steepest gradient is about half a block per block, so
 * adjacent columns almost never differ by more than one — which means a rule
 * that refuses ground climbing more than a block a step <em>cannot fire in the
 * suite at all</em>. Three separate fixes for roads on unclimbable slopes
 * measured perfectly clean here and changed nothing in a world, because there
 * were no unclimbable slopes here to measure.
 *
 * <p>So this is a height field captured from the world the reports came from,
 * by {@code /civ terrain}, with full chunk generation forced so it is the real
 * jagged {@code OCEAN_FLOOR} rather than the oracle's smoothed guess. It is
 * data, not Minecraft: the simulation module still imports nothing from the
 * game.
 *
 * <p>Recorded at a grain, so a lookup rounds down to the nearest recorded
 * column. That loses sub-grain detail and keeps every step the rules care
 * about, which is the trade the grain was chosen for.
 */
public final class HeightField {

    private final int originX;
    private final int originZ;
    private final int grain;
    private final int cellsX;
    private final int cellsZ;
    private final int seaLevel;
    private final int[] heights;
    private final boolean[] wet;

    private HeightField(int originX, int originZ, int grain, int cellsX, int cellsZ,
                        int seaLevel, int[] heights, boolean[] wet) {
        this.originX = originX;
        this.originZ = originZ;
        this.grain = grain;
        this.cellsX = cellsX;
        this.cellsZ = cellsZ;
        this.seaLevel = seaLevel;
        this.heights = heights;
        this.wet = wet;
    }

    /**
     * Reads a field written by {@code /civ terrain}.
     *
     * <p>Format, deliberately plain text so it can be looked at: a header of
     * {@code origin x z}, {@code grain n}, {@code size nx nz} and {@code sea n},
     * then one row per z of space-separated heights, each with a trailing
     * {@code ~} where the column is under water.
     */
    public static HeightField load(String resource) {
        try (InputStream in = HeightField.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("no recorded terrain at " + resource);
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            int originX = 0;
            int originZ = 0;
            int grain = 1;
            int cellsX = 0;
            int cellsZ = 0;
            int sea = 63;
            List<String> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                switch (parts[0]) {
                    case "origin" -> {
                        originX = Integer.parseInt(parts[1]);
                        originZ = Integer.parseInt(parts[2]);
                    }
                    case "grain" -> grain = Integer.parseInt(parts[1]);
                    case "size" -> {
                        cellsX = Integer.parseInt(parts[1]);
                        cellsZ = Integer.parseInt(parts[2]);
                    }
                    case "sea" -> sea = Integer.parseInt(parts[1]);
                    default -> rows.add(line.trim());
                }
            }
            if (rows.size() != cellsZ) {
                throw new IllegalStateException(
                        resource + " says " + cellsZ + " rows and has " + rows.size());
            }
            int[] heights = new int[cellsX * cellsZ];
            boolean[] wet = new boolean[cellsX * cellsZ];
            for (int iz = 0; iz < cellsZ; iz++) {
                String[] cells = rows.get(iz).split("\\s+");
                if (cells.length != cellsX) {
                    throw new IllegalStateException(
                            resource + " row " + iz + " has " + cells.length
                                    + " cells, not " + cellsX);
                }
                for (int ix = 0; ix < cellsX; ix++) {
                    String cell = cells[ix];
                    boolean under = cell.endsWith("~");
                    heights[iz * cellsX + ix] = Integer.parseInt(
                            under ? cell.substring(0, cell.length() - 1) : cell);
                    wet[iz * cellsX + ix] = under;
                }
            }
            return new HeightField(originX, originZ, grain, cellsX, cellsZ, sea,
                    heights, wet);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    private int index(int x, int z) {
        int ix = Math.floorDiv(x - originX, grain);
        int iz = Math.floorDiv(z - originZ, grain);
        // Clamped rather than refused. A town may reach past what was recorded,
        // and an edge that repeats is a far smaller lie than a hole.
        ix = Math.max(0, Math.min(cellsX - 1, ix));
        iz = Math.max(0, Math.min(cellsZ - 1, iz));
        return iz * cellsX + ix;
    }

    public int heightAt(int x, int z) {
        return heights[index(x, z)];
    }

    public boolean wetAt(int x, int z) {
        return wet[index(x, z)];
    }

    public int seaLevel() {
        return seaLevel;
    }

    /** Whether a column falls outside what was actually recorded. */
    public boolean covers(int x, int z) {
        int ix = Math.floorDiv(x - originX, grain);
        int iz = Math.floorDiv(z - originZ, grain);
        return ix >= 0 && ix < cellsX && iz >= 0 && iz < cellsZ;
    }

    /**
     * The steepest step between neighbouring recorded columns.
     *
     * <p>The number this whole fixture exists for. If a recorded field reports
     * one, it is as smooth as the sine waves it was meant to replace and it is
     * not worth testing against.
     */
    public int steepestStep() {
        int worst = 0;
        for (int iz = 0; iz < cellsZ; iz++) {
            for (int ix = 0; ix < cellsX; ix++) {
                int here = heights[iz * cellsX + ix];
                if (ix + 1 < cellsX) {
                    worst = Math.max(worst, Math.abs(heights[iz * cellsX + ix + 1] - here));
                }
                if (iz + 1 < cellsZ) {
                    worst = Math.max(worst,
                            Math.abs(heights[(iz + 1) * cellsX + ix] - here));
                }
            }
        }
        return worst;
    }
}
