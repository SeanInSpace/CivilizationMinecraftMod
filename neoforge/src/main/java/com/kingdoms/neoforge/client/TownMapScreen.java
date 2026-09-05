package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.net.TownMapPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import static com.kingdoms.neoforge.client.KingdomsPanel.BORDER;
import static com.kingdoms.neoforge.client.KingdomsPanel.SUBTLE;
import static com.kingdoms.neoforge.client.KingdomsPanel.TITLE;

/**
 * A plan of the town: blank ground, and every building picked out in green.
 *
 * <p>Deliberately not a Minecraft map. A map renders terrain, which is exactly
 * the information this is trying to strip away — the point is to see the shape
 * of the settlement, so everything that is not a building is left empty.
 *
 * <p>North is up and the scale is fixed by the town's own claim, so a hamlet and
 * a city both fill the same square and can be read the same way.
 */
public final class TownMapScreen extends Screen {

    private static final int HEADER = 30;
    private static final int PADDING = 20;

    /** Blank border between the panel and the edge of the screen. */
    private static final int MARGIN = 16;

    /** Gutter down each side of the map inside the panel. */
    private static final int GUTTER = 20;

    /**
     * Bounds on how big the plan is drawn.
     *
     * <p>The map used to be a fixed 216 pixels, which is a different thing
     * entirely depending on the GUI scale: at 3x it filled the screen nicely and
     * at 1x it was a postage stamp in the middle of a very large window. Both
     * bounds are generous — this is a screen whose whole job is the drawing on it.
     */
    private static final int MIN_MAP = 128;
    private static final int MAX_MAP = 640;

    /**
     * Colder and flatter than the shared panel ground.
     *
     * <p>Deliberately not {@link KingdomsPanel#PANEL}: this screen is mostly a
     * drawing of terrain, and the warm cast that suits a page of text reads as a
     * stain behind a green plan.
     */
    private static final int PANEL = 0xF0181818;
    private static final int GROUND = 0xFF101010;
    private static final int CLAIM = 0x30FFFFFF;
    private static final int BUILDING = 0xFF4CD07A;
    private static final int BUILDING_EDGE = 0xFF2E8F52;

    /** Planned but unbuilt: a faint wash inside a solid edge — intent, not walls. */
    private static final int PLANNED_FILL = 0x40348A52;

    /** The roads: dim earth, drawn under the buildings so they read as ground. */
    private static final int ROAD = 0xFF6B5A44;
    private static final int PLAYER = 0xFFFFFFFF;


    /** No building ever draws thinner than this, or a hut vanishes at town scale. */
    private static final int MIN_MARK = 2;

    /** Roads never draw thinner than this, or the network vanishes at town scale. */
    private static final int ROAD_WIDTH = 1;

    private final TownMapPayload town;

    public TownMapScreen(TownMapPayload town) {
        super(Component.literal(town.town()));
        this.town = town;
    }

    /**
     * How big the plan is drawn, for the window it has been given.
     *
     * <p>Measured off the screen rather than fixed, so the map is the same size
     * relative to the window at every GUI scale instead of shrinking to a stamp
     * as the scale goes down.
     */
    private int mapSize() {
        int across = width - 2 * (MARGIN + GUTTER);
        int down = height - 2 * MARGIN - HEADER - PADDING;
        return Math.clamp(Math.min(across, down), MIN_MAP, MAX_MAP);
    }

    private int panelWidth() {
        return mapSize() + 2 * GUTTER;
    }

    private int panelHeight() {
        return HEADER + mapSize() + PADDING;
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    /** Blocks per pixel. The claim fills the square, whatever size the town is. */
    private double scale() {
        int span = Math.max(16, town.claimRadius() * 2);
        return (double) mapSize() / span;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int size = mapSize();
        int panelWidth = panelWidth();
        int x = left();
        int y = top();
        int h = panelHeight();

        KingdomsPanel.frame(graphics, x, y, panelWidth, h, PANEL);

        graphics.centeredText(font, title, x + panelWidth / 2, y + 10, TITLE);

        int mapX = x + (panelWidth - size) / 2;
        int mapY = y + HEADER;
        graphics.fill(mapX, mapY, mapX + size, mapY + size, GROUND);
        graphics.outline(mapX, mapY, size, size, BORDER);

        drawClaim(graphics, mapX, mapY);
        // Under the buildings: a road passing a plot should disappear behind it,
        // the way it does on the ground.
        drawRoads(graphics, mapX, mapY);
        drawBuildings(graphics, mapX, mapY);
        drawPlayer(graphics, mapX, mapY);

        int built = 0;
        for (TownMapPayload.Mark mark : town.marks()) {
            if (mark.finished()) {
                built++;
            }
        }
        int planned = town.marks().size() - built;
        graphics.centeredText(font,
                Component.literal(built + " buildings"
                        + (planned > 0 ? "   ·   " + planned + " planned" : "")
                        + (town.roads().isEmpty() ? ""
                                : "   ·   " + town.roads().size() + " roads")
                        + "   ·   north is up"),
                x + panelWidth / 2, y + h - 14, SUBTLE);

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    /** A faint square for the town's borders, so the plan has an edge to read against. */
    private void drawClaim(GuiGraphicsExtractor graphics, int mapX, int mapY) {
        graphics.outline(mapX + 1, mapY + 1, mapSize() - 2, mapSize() - 2, CLAIM);
    }

    /**
     * The road network. Every segment is axis-aligned, so each one draws as a
     * thin filled rectangle — no line rasteriser needed.
     */
    private void drawRoads(GuiGraphicsExtractor graphics, int mapX, int mapY) {
        double scale = scale();
        int size = mapSize();
        for (TownMapPayload.Road road : town.roads()) {
            int x0 = mapX + toPixel(Math.min(road.x1(), road.x2()) - town.centreX(), scale, size);
            int x1 = mapX + toPixel(Math.max(road.x1(), road.x2()) - town.centreX(), scale, size);
            int z0 = mapY + toPixel(Math.min(road.z1(), road.z2()) - town.centreZ(), scale, size);
            int z1 = mapY + toPixel(Math.max(road.z1(), road.z2()) - town.centreZ(), scale, size);

            // A road is a block or three wide, which is under a pixel on a city
            // plan. Draw it thin rather than not at all.
            if (x1 - x0 < ROAD_WIDTH) {
                x1 = x0 + ROAD_WIDTH;
            }
            if (z1 - z0 < ROAD_WIDTH) {
                z1 = z0 + ROAD_WIDTH;
            }
            if (x1 <= mapX || z1 <= mapY || x0 >= mapX + size || z0 >= mapY + size) {
                continue;
            }
            graphics.fill(Math.max(x0, mapX), Math.max(z0, mapY),
                    Math.min(x1, mapX + size), Math.min(z1, mapY + size), ROAD);
        }
    }

    private void drawBuildings(GuiGraphicsExtractor graphics, int mapX, int mapY) {
        double scale = scale();
        int size = mapSize();
        for (TownMapPayload.Mark mark : town.marks()) {
            // Centered on its origin, exactly as the placer builds it.
            double halfW = mark.width() / 2.0;
            double halfD = mark.depth() / 2.0;

            int x0 = mapX + toPixel(mark.x() - halfW - town.centreX(), scale, size);
            int x1 = mapX + toPixel(mark.x() + halfW - town.centreX(), scale, size);
            int z0 = mapY + toPixel(mark.z() - halfD - town.centreZ(), scale, size);
            int z1 = mapY + toPixel(mark.z() + halfD - town.centreZ(), scale, size);

            // A small building at city scale rounds to nothing; give it a floor.
            if (x1 - x0 < MIN_MARK) {
                x1 = x0 + MIN_MARK;
            }
            if (z1 - z0 < MIN_MARK) {
                z1 = z0 + MIN_MARK;
            }
            if (x1 <= mapX || z1 <= mapY || x0 >= mapX + size || z0 >= mapY + size) {
                continue;   // built outside the claim it started with
            }

            if (mark.finished()) {
                graphics.fill(x0, z0, x1, z1, BUILDING);
                if (x1 - x0 > 3 && z1 - z0 > 3) {
                    graphics.outline(x0, z0, x1 - x0, z1 - z0, BUILDING_EDGE);
                }
            } else {
                graphics.fill(x0, z0, x1, z1, PLANNED_FILL);
                graphics.outline(x0, z0, x1 - x0, z1 - z0, BUILDING_EDGE);
            }
        }
    }

    private void drawPlayer(GuiGraphicsExtractor graphics, int mapX, int mapY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        double scale = scale();
        int size = mapSize();
        int px = mapX + toPixel(player.getX() - town.centreX(), scale, size);
        int pz = mapY + toPixel(player.getZ() - town.centreZ(), scale, size);
        if (px < mapX || pz < mapY || px >= mapX + size || pz >= mapY + size) {
            return;   // off the plan entirely
        }
        graphics.fill(px - 1, pz - 1, px + 2, pz + 2, PLAYER);
    }

    /** World offset from the town center to a pixel offset from the map's middle. */
    private static int toPixel(double offsetBlocks, double scale, int size) {
        return (int) Math.round(size / 2.0 + offsetBlocks * scale);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
