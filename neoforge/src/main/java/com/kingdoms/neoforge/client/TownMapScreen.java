package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.net.TownMapPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

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

    private static final int PANEL_WIDTH = 256;
    private static final int HEADER = 30;
    private static final int MAP_SIZE = 216;
    private static final int PADDING = 20;

    private static final int PANEL = 0xF0181818;
    private static final int BORDER = 0xFF6A6A6A;
    private static final int GROUND = 0xFF101010;
    private static final int CLAIM = 0x30FFFFFF;
    private static final int BUILDING = 0xFF4CD07A;
    private static final int BUILDING_EDGE = 0xFF2E8F52;
    private static final int PLAYER = 0xFFFFFFFF;
    private static final int TITLE = 0xFFFFE0A0;
    private static final int SUBTLE = 0xFF9A9A9A;

    /** No building ever draws thinner than this, or a hut vanishes at town scale. */
    private static final int MIN_MARK = 2;

    private final TownMapPayload town;

    public TownMapScreen(TownMapPayload town) {
        super(Component.literal(town.town()));
        this.town = town;
    }

    private int panelHeight() {
        return HEADER + MAP_SIZE + PADDING;
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    /** Blocks per pixel. The claim fills the square, whatever size the town is. */
    private double scale() {
        int span = Math.max(16, town.claimRadius() * 2);
        return (double) MAP_SIZE / span;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = left();
        int y = top();
        int h = panelHeight();

        graphics.fill(x, y, x + PANEL_WIDTH, y + h, PANEL);
        graphics.outline(x, y, PANEL_WIDTH, h, BORDER);

        graphics.centeredText(font, title, x + PANEL_WIDTH / 2, y + 10, TITLE);

        int mapX = x + (PANEL_WIDTH - MAP_SIZE) / 2;
        int mapY = y + HEADER;
        graphics.fill(mapX, mapY, mapX + MAP_SIZE, mapY + MAP_SIZE, GROUND);
        graphics.outline(mapX, mapY, MAP_SIZE, MAP_SIZE, BORDER);

        drawClaim(graphics, mapX, mapY);
        drawBuildings(graphics, mapX, mapY);
        drawPlayer(graphics, mapX, mapY);

        graphics.centeredText(font,
                Component.literal(town.marks().size() + " buildings   ·   north is up"),
                x + PANEL_WIDTH / 2, y + h - 14, SUBTLE);

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    /** A faint square for the town's borders, so the plan has an edge to read against. */
    private void drawClaim(GuiGraphicsExtractor graphics, int mapX, int mapY) {
        graphics.outline(mapX + 1, mapY + 1, MAP_SIZE - 2, MAP_SIZE - 2, CLAIM);
    }

    private void drawBuildings(GuiGraphicsExtractor graphics, int mapX, int mapY) {
        double scale = scale();
        for (TownMapPayload.Mark mark : town.marks()) {
            // Centred on its origin, exactly as the placer builds it.
            double halfW = mark.width() / 2.0;
            double halfD = mark.depth() / 2.0;

            int x0 = mapX + toPixel(mark.x() - halfW - town.centreX(), scale);
            int x1 = mapX + toPixel(mark.x() + halfW - town.centreX(), scale);
            int z0 = mapY + toPixel(mark.z() - halfD - town.centreZ(), scale);
            int z1 = mapY + toPixel(mark.z() + halfD - town.centreZ(), scale);

            // A small building at city scale rounds to nothing; give it a floor.
            if (x1 - x0 < MIN_MARK) {
                x1 = x0 + MIN_MARK;
            }
            if (z1 - z0 < MIN_MARK) {
                z1 = z0 + MIN_MARK;
            }
            if (x1 <= mapX || z1 <= mapY || x0 >= mapX + MAP_SIZE || z0 >= mapY + MAP_SIZE) {
                continue;   // built outside the claim it started with
            }

            graphics.fill(x0, z0, x1, z1, BUILDING);
            if (x1 - x0 > 3 && z1 - z0 > 3) {
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
        int px = mapX + toPixel(player.getX() - town.centreX(), scale);
        int pz = mapY + toPixel(player.getZ() - town.centreZ(), scale);
        if (px < mapX || pz < mapY || px >= mapX + MAP_SIZE || pz >= mapY + MAP_SIZE) {
            return;   // off the plan entirely
        }
        graphics.fill(px - 1, pz - 1, px + 2, pz + 2, PLAYER);
    }

    /** World offset from the town centre to a pixel offset from the map's middle. */
    private static int toPixel(double offsetBlocks, double scale) {
        return (int) Math.round(MAP_SIZE / 2.0 + offsetBlocks * scale);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
