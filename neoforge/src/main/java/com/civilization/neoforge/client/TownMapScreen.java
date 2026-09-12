package com.civilization.neoforge.client;

import com.civilization.neoforge.net.SurveyPayload;
import com.civilization.neoforge.net.TownMapPayload;
import com.civilization.neoforge.net.TownMapPayload.Dot;
import com.civilization.neoforge.net.TownMapPayload.Line;
import com.civilization.neoforge.net.TownMapPayload.Note;
import com.civilization.neoforge.net.TownMapPayload.Order;
import com.civilization.neoforge.net.TownMapPayload.Overview;
import com.civilization.neoforge.net.TownMapPayload.Plot;
import com.civilization.neoforge.net.TownMapPayload.Run;
import com.civilization.neoforge.net.TownMapPayload.Vertex;
import com.civilization.neoforge.net.TownMapRequestPayload;
import com.civilization.neoforge.net.TownOverviewPayload.Distress;
import com.civilization.neoforge.trade.Currency;
import com.civilization.sim.culture.Culture;
import com.civilization.sim.person.Person;
import com.civilization.sim.settlement.Beds;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.BuildingSizes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.civilization.neoforge.client.CivilizationPanel.AMOUNT;
import static com.civilization.neoforge.client.CivilizationPanel.BORDER;
import static com.civilization.neoforge.client.CivilizationPanel.LABEL;
import static com.civilization.neoforge.client.CivilizationPanel.RULE;
import static com.civilization.neoforge.client.CivilizationPanel.STRIPE;
import static com.civilization.neoforge.client.CivilizationPanel.SUBTLE;
import static com.civilization.neoforge.client.CivilizationPanel.TITLE;

/**
 * The town, drawn and read: a plan on the left and its books on the right.
 *
 * <p>Deliberately not a Minecraft map. A map renders terrain, which is exactly
 * the information this strips away — the point is the shape of the settlement
 * and what is happening in it, so everything that is not the town is left empty.
 *
 * <p>What this replaced was a square of green rectangles: honest, and unable to
 * answer a single question. It could not say what a building was, who was in it,
 * what the build queue was waiting on, or why the granary was empty — and every
 * one of those answers lived only in {@code /civ info}, behind an operator
 * permission, as a wall of text. This is that report, laid out, for anybody who
 * can walk to a hall.
 *
 * <p><strong>It is alive.</strong> The screen asks the server for a fresh
 * reading once a second and folds it in without disturbing the pan, the zoom,
 * the tab or the selection. A settlement steps every few seconds; a plan that
 * froze when it was opened would be showing a town that no longer exists.
 *
 * <p>North is up, always. The claim sets the opening scale, so a hamlet and a
 * city both open filling the pane, and from there the wheel and the mouse decide.
 *
 * <p>Note the 26.2 shape: screens no longer draw immediately. They <em>extract</em>
 * render state, and the framework calls {@code extractBackground} before
 * {@code extractRenderState} on its own, so neither is invoked from here.
 */
public final class TownMapScreen extends Screen {

    // --- the tabs ---

    private static final int TAB_TOWN = 0;
    private static final int TAB_PEOPLE = 1;
    private static final int TAB_BUILT = 2;
    private static final int TAB_QUEUE = 3;
    private static final int TAB_HISTORY = 4;

    private static final String[] TAB_NAMES = {"Town", "People", "Built", "Queue", "History"};

    // --- the shape of the window ---

    /** Blank border between the panel and the edge of the screen. */
    private static final int MARGIN = 8;

    /** Air between the map, the side panel and the panel's own edges. */
    private static final int GAP = 6;

    private static final int FOOTER = 15;

    /** The tab strip's rows are this tall, text and underline together. */
    private static final int TAB_HEIGHT = 13;

    /** One entry in a list: two lines of text with air round them. */
    private static final int LIST_ROW = 20;

    /** A queue entry needs a third line for its bar. */
    private static final int QUEUE_ROW = 28;

    private static final int MIN_PANEL_WIDTH = 320;
    private static final int MAX_PANEL_WIDTH = 760;
    private static final int MIN_PANEL_HEIGHT = 200;
    private static final int MAX_PANEL_HEIGHT = 460;

    /** The side panel's share of the width, and the bounds it is held between. */
    private static final int SIDE_PERCENT = 30;
    private static final int MIN_SIDE = 170;
    private static final int MAX_SIDE = 240;

    // --- the palette ---

    /**
     * Colder and flatter than the shared panel ground.
     *
     * <p>Deliberately not {@link CivilizationPanel#PANEL}: this screen is mostly a
     * drawing, and the warm cast that suits a page of text reads as a stain
     * behind a plan.
     */
    private static final int PANEL = 0xF0181818;
    private static final int GROUND = 0xFF101010;

    /** The claim's edge: the border the charter draws as a ring of sparks. */
    private static final int CLAIM = 0x40FFFFFF;

    private static final int STREET_COLOR = 0x50FFFFFF;
    private static final int ROAD_COLOR = 0xFF6B5A44;
    private static final int PLANNED_ROAD_COLOR = 0x806B5A44;
    private static final int WALL_COLOR = 0xFFB0B0B0;
    private static final int TREE_COLOR = 0xFF2E7D32;
    private static final int PLAYER_COLOR = 0xFFFFFFFF;
    private static final int SELECTED = 0xFFFFE0A0;

    /** Buildings, by what they are for. The six groups the eye sorts a town into. */
    private static final int HOME_COLOR = 0xFFD9A441;
    private static final int FOOD_COLOR = 0xFF7BC96F;
    private static final int MATERIAL_COLOR = 0xFF9B7653;
    private static final int CRAFT_COLOR = 0xFF6FA8DC;
    private static final int CIVIC_COLOR = 0xFFC792EA;
    private static final int DEFENSE_COLOR = 0xFFE06C75;
    private static final int OTHER_COLOR = 0xFF8A8A8A;

    private static final int ALARM_HUNGRY = 0xFFFFE070;
    private static final int ALARM_FAILING = 0xFFFFAA55;
    private static final int ALARM_DYING = 0xFFFF6055;

    // --- how the plan is drawn ---

    /** Pixels per block, at the ends of the wheel's travel. */
    private static final double MIN_ZOOM = 0.05;
    private static final double MAX_ZOOM = 6.0;

    /** No building draws thinner than this, or a hut vanishes at town scale. */
    private static final int MIN_MARK = 2;

    /** Pixels on, pixels off, along a planned road. */
    private static final int DASH = 3;

    /** How close the cursor must come to a settler's dot to pick them out. */
    private static final int FOLK_REACH = 4;

    /** Ticks between asking the server how the town is now. */
    private static final int REFRESH_TICKS = 20;

    /**
     * The longest a single line may be walked, in pixels.
     *
     * <p>A line is drawn a pixel at a time, and at full zoom a road running out
     * of the claim is tens of thousands of pixels long with three of them on
     * screen. Segments are clipped to the pane before they are walked; this is
     * the belt to that pair of braces.
     */
    private static final int MAX_LINE_STEPS = 2048;

    // --- state ---

    private TownMapPayload town;

    /**
     * Pixels per block. Set from the claim the first time the screen is drawn.
     *
     * <p>One to begin with rather than nought, so a click that somehow lands
     * before the first frame divides by something.
     */
    private double zoom = 1.0;

    /** Where the middle of the pane is, in blocks from the town's center. */
    private double viewX;
    private double viewZ;

    private boolean fitted;

    private int tab = TAB_TOWN;
    private final int[] scroll = new int[TAB_NAMES.length];

    /** Which building is picked out, as an index into the payload's plots, or -1. */
    private int selectedPlot = -1;

    /** And which settler, as an index into its folk, or -1. */
    private int selectedFolk = -1;

    /** Whether the button that went down went down on the map. */
    private boolean draggingMap;

    /** How the People tab is ordered: by name, by trade, or by hunger. */
    private int sort;
    private static final int SORT_NAME = 0;
    private static final int SORT_TRADE = 1;
    private static final int SORT_HUNGER = 2;

    private int sinceRefresh;

    /** The tab strip's boxes, rebuilt as it is drawn so a click can be matched. */
    private final List<int[]> tabBoxes = new ArrayList<>();

    public TownMapScreen(TownMapPayload town) {
        super(Component.literal(town.town()));
        this.town = town;
    }

    /**
     * Takes a fresh reading of the same town.
     *
     * <p>Everything the player has done to the screen — where they have dragged
     * it, how far in they are, which tab and which building — is deliberately
     * untouched. Only what the town is doing changes.
     *
     * <p>The selection is an index into a list the server rebuilds every second,
     * and that list is stable for standing buildings: they travel in the
     * settlement's own order and a building is only ever added at the end.
     * Queued plots are not stable and a selection can slide along them by one
     * when an order completes, which is a flicker rather than a fault; a plot
     * that has fallen off the end is dropped.
     */
    public void update(TownMapPayload fresh) {
        this.town = fresh;
        if (selectedPlot >= fresh.plots().size()) {
            selectedPlot = -1;
        }
        if (selectedFolk >= fresh.folk().size()) {
            selectedFolk = -1;
        }
    }

    // --- geometry ---

    private int panelWidth() {
        return Math.clamp(width - 2 * MARGIN, MIN_PANEL_WIDTH, MAX_PANEL_WIDTH);
    }

    private int panelHeight() {
        return Math.clamp(height - 2 * MARGIN, MIN_PANEL_HEIGHT, MAX_PANEL_HEIGHT);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    private int bodyTop() {
        return top() + CivilizationPanel.HEADER;
    }

    private int bodyHeight() {
        return panelHeight() - CivilizationPanel.HEADER - FOOTER;
    }

    private int sideWidth() {
        return Math.clamp(panelWidth() * SIDE_PERCENT / 100, MIN_SIDE, MAX_SIDE);
    }

    private int sideX() {
        return left() + panelWidth() - GAP - sideWidth();
    }

    private int mapX() {
        return left() + GAP;
    }

    private int mapWidth() {
        return Math.max(64, sideX() - GAP - mapX());
    }

    private boolean overMap(double mouseX, double mouseY) {
        return mouseX >= mapX() && mouseX < mapX() + mapWidth()
                && mouseY >= bodyTop() && mouseY < bodyTop() + bodyHeight();
    }

    private boolean overSide(double mouseX, double mouseY) {
        return mouseX >= sideX() && mouseX < sideX() + sideWidth()
                && mouseY >= bodyTop() && mouseY < bodyTop() + bodyHeight();
    }

    /** The claim, filling the shorter side of the pane, with a little air round it. */
    private void fit() {
        int span = Math.max(16, town.claimRadius() * 2);
        zoom = Math.clamp((double) Math.min(mapWidth(), bodyHeight()) / span,
                MIN_ZOOM, MAX_ZOOM);
        viewX = 0;
        viewZ = 0;
        fitted = true;
    }

    private double screenX(double blocksEast) {
        return mapX() + mapWidth() / 2.0 + (blocksEast - viewX) * zoom;
    }

    private double screenY(double blocksSouth) {
        return bodyTop() + bodyHeight() / 2.0 + (blocksSouth - viewZ) * zoom;
    }

    // --- the live reading ---

    @Override
    public void tick() {
        if (++sinceRefresh < REFRESH_TICKS) {
            return;
        }
        sinceRefresh = 0;
        // The server answers with a reading that is not opening one, so it can
        // only ever be folded into this screen. Nothing is remembered on the
        // other end: close the screen and the asking simply stops.
        ClientPacketDistributor.sendToServer(new TownMapRequestPayload(town.origin()));
    }

    // --- input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (overMap(mouseX, mouseY)) {
            draggingMap = event.button() == 0;
            int plot = plotAt(mouseX, mouseY);
            int folk = folkAt(mouseX, mouseY);
            if (folk >= 0) {
                selectedFolk = folk;
                selectedPlot = -1;
                tab = TAB_PEOPLE;
                showFolk(folk);
            } else if (plot >= 0) {
                selectedPlot = plot;
                selectedFolk = -1;
                tab = TAB_BUILT;
                showPlot(plot);
            }
            return true;
        }
        if (overSide(mouseX, mouseY) && clickSide(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingMap = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingMap) {
            viewX -= dx / zoom;
            viewZ -= dy / zoom;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (overMap(x, y)) {
            // The block under the cursor stays under the cursor. Zooming toward
            // the middle of the pane instead makes a map you have to chase the
            // thing you are looking at across.
            double centerX = mapX() + mapWidth() / 2.0;
            double centerY = bodyTop() + bodyHeight() / 2.0;
            double blockX = viewX + (x - centerX) / zoom;
            double blockZ = viewZ + (y - centerY) / zoom;
            zoom = Math.clamp(zoom * Math.pow(1.15, scrollY), MIN_ZOOM, MAX_ZOOM);
            viewX = blockX - (x - centerX) / zoom;
            viewZ = blockZ - (y - centerY) / zoom;
            return true;
        }
        if (overSide(x, y)) {
            scroll[tab] = Math.max(0, scroll[tab] - (int) Math.signum(scrollY) * LIST_ROW);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_R) {
            fit();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            tab = (tab + 1) % TAB_NAMES.length;
            return true;
        }
        return super.keyPressed(event);
    }

    /** A click inside the side panel: a tab, a sort header, or a row. */
    private boolean clickSide(double mouseX, double mouseY) {
        for (int i = 0; i < tabBoxes.size(); i++) {
            int[] box = tabBoxes.get(i);
            if (mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3]) {
                tab = i;
                return true;
            }
        }
        int listTop = listTop();
        if (mouseY < bodyTop() + tabStripHeight()) {
            return true;   // blank ground in the tab strip; swallowed, not acted on
        }
        if (mouseY < listTop) {
            if (tab == TAB_PEOPLE) {
                sort = (sort + 1) % 3;
                return true;
            }
            return false;
        }
        int index = (int) ((mouseY - listTop + scroll[tab]) / rowHeight());
        if (index < 0) {
            return false;
        }
        if (tab == TAB_PEOPLE) {
            List<Integer> order = sortedFolk();
            if (index < order.size()) {
                selectedFolk = order.get(index);
                selectedPlot = -1;
                centerOn(town.folk().get(selectedFolk).dx(), town.folk().get(selectedFolk).dz());
                return true;
            }
        } else if (tab == TAB_BUILT) {
            List<Integer> order = sortedPlots();
            if (index < order.size()) {
                selectedPlot = order.get(index);
                selectedFolk = -1;
                centerOn(town.plots().get(selectedPlot).dx(), town.plots().get(selectedPlot).dz());
                return true;
            }
        } else if (tab == TAB_QUEUE && index < town.queue().size()) {
            Order order = town.queue().get(index);
            centerOn(order.dx(), order.dz());
            return true;
        }
        return false;
    }

    private void centerOn(int dx, int dz) {
        viewX = dx;
        viewZ = dz;
    }

    /** Scrolls the Buildings tab so a plot picked off the map is on screen. */
    private void showPlot(int plot) {
        int at = sortedPlots().indexOf(plot);
        if (at >= 0) {
            scrollTo(at);
        }
    }

    private void showFolk(int folk) {
        int at = sortedFolk().indexOf(folk);
        if (at >= 0) {
            scrollTo(at);
        }
    }

    private void scrollTo(int index) {
        int rowTop = index * rowHeight();
        int visible = bodyTop() + bodyHeight() - listTop();
        if (rowTop < scroll[tab]) {
            scroll[tab] = rowTop;
        } else if (rowTop + rowHeight() > scroll[tab] + visible) {
            scroll[tab] = Math.max(0, rowTop + rowHeight() - visible);
        }
    }

    private int rowHeight() {
        return tab == TAB_QUEUE ? QUEUE_ROW : LIST_ROW;
    }

    // --- drawing ---

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float a) {
        if (!fitted) {
            fit();
        }
        int x = left();
        int y = top();
        int w = panelWidth();
        int h = panelHeight();

        CivilizationPanel.frame(graphics, x, y, w, h, PANEL);
        CivilizationPanel.header(graphics, font, x, y, w, title,
                Component.literal(subtitle()), alarmColor(town.overview().distress().alarm()));

        drawMap(graphics);
        drawSide(graphics, mouseX, mouseY);

        CivilizationPanel.rule(graphics, x, y + h - FOOTER + 3, w);
        graphics.centeredText(font, Component.literal(
                        "drag to move   ·   wheel to zoom   ·   R to fit   ·   north is up"),
                x + w / 2, y + h - 11, SUBTLE);

        // Tooltips last and outside every scissor, or the box is clipped to the
        // pane that raised it.
        hoverTooltip(graphics, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private String subtitle() {
        Overview overview = town.overview();
        Distress distress = overview.distress();
        if (!distress.silent()) {
            return distress.word().toUpperCase(Locale.ROOT) + " — " + distress.fact();
        }
        return overview.stage() + "   ·   " + overview.folk().population() + " of "
                + overview.folk().housing() + " housed   ·   "
                + standing() + " buildings";
    }

    private static int alarmColor(int alarm) {
        return switch (alarm) {
            case Distress.ALARM_DYING -> ALARM_DYING;
            case Distress.ALARM_FAILING -> ALARM_FAILING;
            case Distress.ALARM_HUNGRY -> ALARM_HUNGRY;
            default -> SUBTLE;
        };
    }

    // --- the map pane ---

    private void drawMap(GuiGraphicsExtractor graphics) {
        int x0 = mapX();
        int y0 = bodyTop();
        int x1 = x0 + mapWidth();
        int y1 = y0 + bodyHeight();

        graphics.fill(x0, y0, x1, y1, GROUND);
        graphics.outline(x0, y0, x1 - x0, y1 - y0, BORDER);

        graphics.enableScissor(x0 + 1, y0 + 1, x1 - 1, y1 - 1);
        drawClaim(graphics);
        drawRuns(graphics, SurveyPayload.STREET);
        drawRuns(graphics, SurveyPayload.ROAD_PLANNED);
        drawRuns(graphics, SurveyPayload.ROAD_OPENED);
        drawTrees(graphics);
        drawPlots(graphics);
        drawRuns(graphics, SurveyPayload.WALL);
        drawFolk(graphics);
        drawPlayer(graphics);
        graphics.disableScissor();

        drawCompass(graphics, x0, y0, x1);
        drawScaleBar(graphics, x0, y1);
    }

    /** The claim's ring, drawn as the circle it actually is. */
    private void drawClaim(GuiGraphicsExtractor graphics) {
        int radius = town.claimRadius();
        int steps = 96;
        double lastX = screenX(radius);
        double lastY = screenY(0);
        for (int i = 1; i <= steps; i++) {
            double angle = (2 * Math.PI) * i / steps;
            double px = screenX(Math.cos(angle) * radius);
            double py = screenY(Math.sin(angle) * radius);
            line(graphics, lastX, lastY, px, py, CLAIM, 1);
            lastX = px;
            lastY = py;
        }
    }

    private void drawRuns(GuiGraphicsExtractor graphics, int kind) {
        int color = switch (kind) {
            case SurveyPayload.STREET -> STREET_COLOR;
            case SurveyPayload.ROAD_OPENED -> ROAD_COLOR;
            case SurveyPayload.ROAD_PLANNED -> PLANNED_ROAD_COLOR;
            default -> WALL_COLOR;
        };
        int thickness = kind == SurveyPayload.ROAD_OPENED || kind == SurveyPayload.WALL ? 2 : 1;
        boolean dashed = kind == SurveyPayload.ROAD_PLANNED;
        for (Run run : town.runs()) {
            if (run.kind() != kind || !run.isDrawable()) {
                continue;
            }
            List<Vertex> path = run.path();
            for (int i = 0; i < path.size() - 1; i++) {
                Vertex from = path.get(i);
                Vertex to = path.get(i + 1);
                if (dashed) {
                    dashedLine(graphics, screenX(from.dx()), screenY(from.dz()),
                            screenX(to.dx()), screenY(to.dz()), color, thickness);
                } else {
                    line(graphics, screenX(from.dx()), screenY(from.dz()),
                            screenX(to.dx()), screenY(to.dz()), color, thickness);
                }
            }
        }
    }

    private void drawTrees(GuiGraphicsExtractor graphics) {
        int size = zoom >= 1.0 ? 3 : 2;
        for (TownMapPayload.Tree tree : town.trees()) {
            int px = (int) Math.round(screenX(tree.dx()));
            int py = (int) Math.round(screenY(tree.dz()));
            graphics.fill(px - size / 2, py - size / 2,
                    px - size / 2 + size, py - size / 2 + size, TREE_COLOR);
        }
    }

    private void drawPlots(GuiGraphicsExtractor graphics) {
        List<Plot> plots = town.plots();
        for (int i = 0; i < plots.size(); i++) {
            drawPlot(graphics, plots.get(i), i == selectedPlot);
        }
    }

    private void drawPlot(GuiGraphicsExtractor graphics, Plot plot, boolean picked) {
        int[] box = boxOf(plot);
        int x0 = box[0];
        int y0 = box[1];
        int x1 = box[2];
        int y1 = box[3];

        int color = colorOf(plot);
        if (plot.finished()) {
            graphics.fill(x0, y0, x1, y1, color);
            cutNotch(graphics, plot, box, GROUND);
            if (x1 - x0 > 3 && y1 - y0 > 3) {
                graphics.outline(x0, y0, x1 - x0, y1 - y0, darker(color));
            }
            if (BuildingRole.of(plot.blueprintId()) == BuildingRole.CROP_FARM) {
                hatch(graphics, x0, y0, x1, y1, darker(color));
            }
            if (plot.condition().damage() > 0) {
                graphics.outline(x0, y0, x1 - x0, y1 - y0, ALARM_FAILING);
            }
        } else {
            // Planned but unbuilt: a wash inside a solid edge — intent, not walls.
            // The bite comes out before the edge goes on, or the edge is painted
            // over in the corner that was cut away.
            graphics.fill(x0, y0, x1, y1, (color & 0x00FFFFFF) | 0x40000000);
            cutNotch(graphics, plot, box, GROUND);
            graphics.outline(x0, y0, x1 - x0, y1 - y0, color);
        }
        drawDoor(graphics, plot, box, plot.finished() ? darker(color) : color);
        if (picked) {
            graphics.outline(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, SELECTED);
        }
    }

    /** A building's rectangle on screen, centered on its origin as the placer builds it. */
    private int[] boxOf(Plot plot) {
        double halfW = plot.width() / 2.0;
        double halfD = plot.depth() / 2.0;
        int x0 = (int) Math.round(screenX(plot.dx() - halfW));
        int x1 = (int) Math.round(screenX(plot.dx() + halfW));
        int y0 = (int) Math.round(screenY(plot.dz() - halfD));
        int y1 = (int) Math.round(screenY(plot.dz() + halfD));
        // A small building at city scale rounds to nothing; give it a floor.
        if (x1 - x0 < MIN_MARK) {
            x1 = x0 + MIN_MARK;
        }
        if (y1 - y0 < MIN_MARK) {
            y1 = y0 + MIN_MARK;
        }
        return new int[] {x0, y0, x1, y1};
    }

    /**
     * Paints the bite back out of a notched building.
     *
     * <p>The orcs' huts are octagons: a bounding box with four corners cut away
     * would be a lie about every roof in a war camp. The plot carries one corner,
     * which is what the simulation records, and painting it back to the ground
     * is cheaper and steadier than composing the remaining shape out of two
     * rectangles at a scale where both may be a pixel wide.
     */
    private void cutNotch(GuiGraphicsExtractor graphics, Plot plot, int[] box, int ground) {
        BuildingSizes.Notch notch = plot.notch();
        if (!notch.isCut()) {
            return;
        }
        double cutW = Math.min(notch.width(), plot.width()) * zoom;
        double cutD = Math.min(notch.depth(), plot.depth()) * zoom;
        if (cutW < 1 || cutD < 1) {
            return;
        }
        int x0 = notch.towardX() > 0 ? (int) Math.round(box[2] - cutW) : box[0];
        int x1 = notch.towardX() > 0 ? box[2] : (int) Math.round(box[0] + cutW);
        int y0 = notch.towardZ() > 0 ? (int) Math.round(box[3] - cutD) : box[1];
        int y1 = notch.towardZ() > 0 ? box[3] : (int) Math.round(box[1] + cutD);
        graphics.fill(x0, y0, x1, y1, ground);
    }

    /**
     * The door, as a tick along the side the building faces.
     *
     * <p>Facing counts quarter turns the way the simulation does, and the same
     * way the surveyor's lamp reads them: nought is a door on the +Z wall, which
     * on a north-up plan is the bottom edge, and each turn moves it a quarter
     * clockwise. The two surfaces must agree or a town has two front doors.
     */
    private void drawDoor(GuiGraphicsExtractor graphics, Plot plot, int[] box, int color) {
        if (box[2] - box[0] < 4 || box[3] - box[1] < 4) {
            return;   // too small to say anything the outline is not already saying
        }
        int midX = (box[0] + box[2]) / 2;
        int midY = (box[1] + box[3]) / 2;
        int reach = 3;
        switch (plot.facing()) {
            case 0 -> graphics.fill(midX - 1, box[3] - 1, midX + 2, box[3] + reach, color);
            case 1 -> graphics.fill(box[0] - reach, midY - 1, box[0] + 1, midY + 2, color);
            case 2 -> graphics.fill(midX - 1, box[1] - reach, midX + 2, box[1] + 1, color);
            default -> graphics.fill(box[2] - 1, midY - 1, box[2] + reach, midY + 2, color);
        }
    }

    /** Diagonal ruling, for a field. Clipped by a scissor rather than by arithmetic. */
    private void hatch(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        if (x1 - x0 < 6 || y1 - y0 < 6) {
            return;
        }
        graphics.enableScissor(x0, y0, x1, y1);
        int span = (x1 - x0) + (y1 - y0);
        for (int offset = 0; offset <= span; offset += 5) {
            line(graphics, x0 + offset, y0, x0 + offset - (y1 - y0), y1, color, 1);
        }
        graphics.disableScissor();
    }

    private void drawFolk(GuiGraphicsExtractor graphics) {
        List<Dot> folk = town.folk();
        for (int i = 0; i < folk.size(); i++) {
            Dot dot = folk.get(i);
            int px = (int) Math.round(screenX(dot.dx()));
            int py = (int) Math.round(screenY(dot.dz()));
            int color = tradeColor(dot.profession());
            graphics.fill(px - 1, py - 1, px + 2, py + 2, color);
            if (!dot.embodied()) {
                // Nobody is watching this one, so there is no villager standing
                // there — the dot is where the simulation keeps them. Hollow.
                graphics.fill(px, py, px + 1, py + 1, GROUND);
            }
            if (i == selectedFolk) {
                graphics.outline(px - 3, py - 3, 7, 7, SELECTED);
            }
        }
    }

    private void drawPlayer(GuiGraphicsExtractor graphics) {
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (player == null) {
            return;
        }
        double px = screenX(player.getX() - town.origin().getX());
        double py = screenY(player.getZ() - town.origin().getZ());
        // Vanilla's yaw is degrees clockwise from south, and south is +z, which
        // on a north-up plan is straight down the screen.
        double facing = Math.toRadians(player.getYRot());
        double dirX = -Math.sin(facing);
        double dirZ = Math.cos(facing);
        line(graphics, px, py, px + dirX * 8, py + dirZ * 8, PLAYER_COLOR, 1);
        int ix = (int) Math.round(px);
        int iy = (int) Math.round(py);
        graphics.fill(ix - 2, iy - 2, ix + 3, iy + 3, PLAYER_COLOR);
    }

    private void drawCompass(GuiGraphicsExtractor graphics, int x0, int y0, int x1) {
        graphics.centeredText(font, Component.literal("N"), (x0 + x1) / 2, y0 + 3, SUBTLE);
    }

    /**
     * How far a stretch of the pane is, in blocks.
     *
     * <p>The one thing a plan drawn to no fixed scale cannot do without: with the
     * wheel free, nothing else on the screen says whether the gap between two
     * houses is ten blocks or two hundred.
     */
    private void drawScaleBar(GuiGraphicsExtractor graphics, int x0, int y1) {
        int[] nice = {1, 2, 5, 10, 20, 50, 100, 200, 500, 1000};
        int blocks = nice[0];
        for (int candidate : nice) {
            if (candidate * zoom <= 80) {
                blocks = candidate;
            }
        }
        int pixels = Math.max(4, (int) Math.round(blocks * zoom));
        int barY = y1 - 8;
        int barX = x0 + 6;
        graphics.fill(barX, barY, barX + pixels, barY + 1, SUBTLE);
        graphics.fill(barX, barY - 2, barX + 1, barY + 3, SUBTLE);
        graphics.fill(barX + pixels - 1, barY - 2, barX + pixels, barY + 3, SUBTLE);
        graphics.text(font, Component.literal(blocks + "m"),
                barX + pixels + 4, barY - 4, SUBTLE, false);
    }

    // --- what is under the cursor ---

    private int plotAt(double mouseX, double mouseY) {
        List<Plot> plots = town.plots();
        // Backwards, so the thing drawn last — a queued plot over a standing one
        // — is the thing the cursor picks.
        for (int i = plots.size() - 1; i >= 0; i--) {
            Plot plot = plots.get(i);
            int[] box = boxOf(plot);
            if (mouseX < box[0] || mouseX >= box[2] || mouseY < box[1] || mouseY >= box[3]) {
                continue;
            }
            if (inNotch(plot, box, mouseX, mouseY)) {
                continue;   // the yard in the crook of an L is not the building
            }
            return i;
        }
        return -1;
    }

    private boolean inNotch(Plot plot, int[] box, double mouseX, double mouseY) {
        BuildingSizes.Notch notch = plot.notch();
        if (!notch.isCut()) {
            return false;
        }
        double cutW = Math.min(notch.width(), plot.width()) * zoom;
        double cutD = Math.min(notch.depth(), plot.depth()) * zoom;
        boolean inX = notch.towardX() > 0 ? mouseX >= box[2] - cutW : mouseX < box[0] + cutW;
        boolean inZ = notch.towardZ() > 0 ? mouseY >= box[3] - cutD : mouseY < box[1] + cutD;
        return inX && inZ;
    }

    private int folkAt(double mouseX, double mouseY) {
        List<Dot> folk = town.folk();
        int best = -1;
        double bestDistance = FOLK_REACH * FOLK_REACH;
        for (int i = 0; i < folk.size(); i++) {
            double dx = screenX(folk.get(i).dx()) - mouseX;
            double dy = screenY(folk.get(i).dz()) - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private void hoverTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!overMap(mouseX, mouseY)) {
            return;
        }
        int folk = folkAt(mouseX, mouseY);
        if (folk >= 0) {
            graphics.setComponentTooltipForNextFrame(font, folkTooltip(folk), mouseX, mouseY);
            return;
        }
        int plot = plotAt(mouseX, mouseY);
        if (plot >= 0) {
            graphics.setComponentTooltipForNextFrame(font, plotTooltip(plot), mouseX, mouseY);
        }
    }

    private List<Component> folkTooltip(int index) {
        Dot dot = town.folk().get(index);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(dot.name()).withColor(TITLE));
        lines.add(Component.literal(dot.profession()).withColor(LABEL));
        lines.add(Component.literal("Hunger " + dot.hunger() + " — " + appetite(dot.hunger()))
                .withColor(hungerColor(dot.hunger())));
        lines.add(Component.literal(dot.doing()).withColor(SUBTLE));
        if (dot.home() >= 0 && dot.home() < town.plots().size()) {
            lines.add(Component.literal("Lives in " + pretty(
                    town.plots().get(dot.home()).blueprintId())).withColor(SUBTLE));
        } else {
            lines.add(Component.literal("No roof of their own").withColor(ALARM_FAILING));
        }
        if (dot.work() >= 0 && dot.work() < town.plots().size()) {
            lines.add(Component.literal("Works at " + pretty(
                    town.plots().get(dot.work()).blueprintId())).withColor(SUBTLE));
        }
        if (!dot.embodied()) {
            lines.add(Component.literal("Nobody is watching them").withColor(SUBTLE));
        }
        return lines;
    }

    private List<Component> plotTooltip(int index) {
        Plot plot = town.plots().get(index);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(pretty(plot.blueprintId())).withColor(TITLE));
        if (!plot.finished()) {
            lines.add(Component.literal("Planned — not built yet").withColor(SUBTLE));
        } else {
            StringBuilder state = new StringBuilder();
            if (plot.condition().level() > 0) {
                state.append("level ").append(plot.condition().level()).append(", ");
            }
            state.append(plot.condition().damage() > 0
                    ? "damaged " + plot.condition().damage() : "whole");
            lines.add(Component.literal(state.toString()).withColor(
                    plot.condition().damage() > 0 ? ALARM_FAILING : LABEL));
            lines.add(Component.literal(plot.width() + " x " + plot.depth()
                    + (plot.condition().joined() ? ", on a lane" : ", NO LANE TO THE DOOR"))
                    .withColor(plot.condition().joined() ? SUBTLE : ALARM_HUNGRY));
        }

        List<String> live = namesWhere(index, true);
        if (!live.isEmpty()) {
            lines.add(Component.literal("Home to " + join(live)).withColor(LABEL));
        }
        List<String> work = namesWhere(index, false);
        if (!work.isEmpty()) {
            lines.add(Component.literal("Worked by " + join(work)).withColor(LABEL));
        }
        for (Line line : plot.stores()) {
            lines.add(Component.literal("  " + prettyWord(line.resource()) + " " + line.amount())
                    .withColor(AMOUNT));
        }
        return lines;
    }

    /** Everybody whose home — or workplace — is this plot. */
    private List<String> namesWhere(int plot, boolean home) {
        List<String> names = new ArrayList<>();
        for (Dot dot : town.folk()) {
            if ((home ? dot.home() : dot.work()) == plot && names.size() < 6) {
                names.add(dot.name());
            }
        }
        return names;
    }

    private static String join(List<String> names) {
        return String.join(", ", names);
    }

    // --- the side panel ---

    private int listTop() {
        return bodyTop() + tabStripHeight() + headerHeight() + 2;
    }

    private int headerHeight() {
        return tab == TAB_PEOPLE ? 11 : 0;
    }

    /** The tab strip wraps onto a second row on a narrow panel rather than crushing. */
    private int tabStripHeight() {
        int rows = 1;
        int used = 0;
        int available = sideWidth() - 4;
        for (String name : TAB_NAMES) {
            int wide = font.width(name) + 10;
            if (used + wide > available && used > 0) {
                rows++;
                used = 0;
            }
            used += wide;
        }
        return rows * TAB_HEIGHT;
    }

    private void drawSide(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = sideX();
        int y = bodyTop();
        int w = sideWidth();
        int h = bodyHeight();

        graphics.fill(x, y, x + w, y + h, 0x30000000);
        graphics.outline(x, y, w, h, BORDER);

        drawTabs(graphics, x, y, w, mouseX, mouseY);

        int top = listTop();
        graphics.enableScissor(x + 1, y + tabStripHeight(), x + w - 1, y + h - 1);
        switch (tab) {
            case TAB_PEOPLE -> drawPeople(graphics, x, w, top, mouseX, mouseY);
            case TAB_BUILT -> drawBuilt(graphics, x, w, top);
            case TAB_QUEUE -> drawQueue(graphics, x, w, top);
            case TAB_HISTORY -> drawHistory(graphics, x, w, top);
            default -> drawTown(graphics, x, w, top);
        }
        graphics.disableScissor();
    }

    private void drawTabs(GuiGraphicsExtractor graphics, int x, int y, int w,
                          int mouseX, int mouseY) {
        tabBoxes.clear();
        int available = w - 4;
        int used = 0;
        int row = 0;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int wide = font.width(TAB_NAMES[i]) + 10;
            if (used + wide > available && used > 0) {
                row++;
                used = 0;
            }
            int boxX = x + 2 + used;
            int boxY = y + row * TAB_HEIGHT;
            tabBoxes.add(new int[] {boxX, boxY, boxX + wide, boxY + TAB_HEIGHT});

            boolean here = i == tab;
            boolean hovered = mouseX >= boxX && mouseX < boxX + wide
                    && mouseY >= boxY && mouseY < boxY + TAB_HEIGHT;
            if (here) {
                graphics.fill(boxX, boxY, boxX + wide, boxY + TAB_HEIGHT, STRIPE);
                graphics.fill(boxX, boxY + TAB_HEIGHT - 1, boxX + wide, boxY + TAB_HEIGHT, TITLE);
            }
            graphics.text(font, Component.literal(TAB_NAMES[i]), boxX + 5, boxY + 3,
                    here ? TITLE : hovered ? AMOUNT : SUBTLE, false);
            used += wide;
        }
        graphics.fill(x + 1, y + tabStripHeight(), x + w - 1, y + tabStripHeight() + 1, RULE);
    }

    // --- the Town tab ---

    private void drawTown(GuiGraphicsExtractor graphics, int x, int w, int top) {
        Overview overview = town.overview();
        List<Component> lines = new ArrayList<>();

        Distress distress = overview.distress();
        if (!distress.silent()) {
            lines.add(Component.literal("THIS TOWN IS " + distress.word().toUpperCase(Locale.ROOT))
                    .withColor(alarmColor(distress.alarm())));
            lines.add(Component.literal(distress.fact()).withColor(LABEL));
            if (distress.showsRemedy()) {
                lines.add(Component.literal(distress.remedy()).withColor(SUBTLE));
            }
            lines.add(Component.empty());
        }

        heading(lines, "The town");
        fact(lines, "Stage", overview.stage());
        fact(lines, "People", overview.folk().population() + " of "
                + overview.folk().housing() + " housed");
        fact(lines, "Families", Integer.toString(overview.folk().families()));
        fact(lines, "Watching", overview.folk().embodied() + " embodied");
        fact(lines, "Culture", Culture.of(overview.cultureId()).race().word());
        fact(lines, "Step", Long.toString(overview.steps()));

        heading(lines, "Food");
        fact(lines, "Granary", overview.larder().granary() + " / "
                + overview.larder().granaryCapacity());
        fact(lines, "Fields", Integer.toString(overview.larder().fields()));
        fact(lines, "Market", Integer.toString(overview.larder().market()));
        fact(lines, "Pantries", Integer.toString(overview.larder().pantries()));
        // Grain is not food and nobody can eat it, so it is said separately: a
        // town starving beside full sacks is a town with no oven.
        lines.add(Component.literal("  Grain " + overview.larder().farmGrain()
                        + " on farms, " + overview.larder().bakeryGrain()
                        + (overview.larder().hasBakery() ? " at the mill" : " at NO BAKERY"))
                .withColor(overview.larder().hasBakery() ? SUBTLE : ALARM_FAILING));
        fact(lines, "Hunger", overview.folk().worstHunger() + " worst"
                + (overview.folk().starving() > 0
                        ? ", " + overview.folk().starving() + " STARVING" : ""));

        heading(lines, "Timber and stone");
        lines.add(overview.ledgers().standCounted()
                ? Component.literal("  " + overview.ledgers().trees() + " trees standing, "
                        + overview.ledgers().growing() + " coming up")
                        .withColor(overview.ledgers().standBare() ? ALARM_FAILING : SUBTLE)
                : Component.literal("  No wood counted yet").withColor(SUBTLE));
        lines.add(overview.ledgers().seamCounted()
                ? Component.literal("  " + overview.ledgers().seam() + " blocks left in the seam")
                        .withColor(overview.ledgers().seamExhausted() ? ALARM_FAILING : SUBTLE)
                : Component.literal("  No seam surveyed yet").withColor(SUBTLE));
        fact(lines, "Treasury", Currency.amount(overview.ledgers().treasury()));

        heading(lines, "The watch");
        fact(lines, "Threat", Integer.toString(overview.watch().threat()));
        lines.add(Component.literal("  Defense " + overview.watch().defense()
                        + " · " + com.civilization.sim.settlement.Garrison.watchSummary(
                                overview.watch().guards(), overview.watch().neededGuards()))
                .withColor(overview.watch().guards() < overview.watch().neededGuards()
                        ? ALARM_HUNGRY : SUBTLE));
        fact(lines, "King", overview.watch().king().isBlank()
                ? "nobody is crowned" : overview.watch().king());
        fact(lines, "Roads", overview.watch().roadRuns() + " runs, "
                + overview.watch().roadLength() + " blocks");
        fact(lines, "Joined", overview.watch().roadsJoined() + " of " + standing()
                + " buildings");

        heading(lines, "Trades");
        StringBuilder trades = new StringBuilder();
        for (TownMapPayload.Job job : overview.jobs()) {
            trades.append(job.profession()).append(" x").append(job.count()).append("   ");
        }
        lines.add(Component.literal("  " + (trades.isEmpty() ? "nobody has a trade" : trades))
                .withColor(SUBTLE));

        heading(lines, "Stores");
        if (overview.stores().isEmpty()) {
            lines.add(Component.literal("  empty").withColor(SUBTLE));
        } else {
            for (Line line : overview.stores()) {
                fact(lines, prettyWord(line.resource()), Integer.toString(line.amount()));
            }
        }

        drawLines(graphics, x, w, top, lines);
    }

    /** How many buildings actually stand, as against how many are drawn. */
    private int standing() {
        int built = 0;
        for (Plot plot : town.plots()) {
            if (plot.finished()) {
                built++;
            }
        }
        return built;
    }

    private void heading(List<Component> lines, String text) {
        if (!lines.isEmpty()) {
            lines.add(Component.empty());
        }
        lines.add(Component.literal(text).withColor(TITLE));
    }

    private void fact(List<Component> lines, String label, String value) {
        lines.add(Component.literal("  " + label + " " + value).withColor(LABEL));
    }

    /** The same, for a value that has a translated word in it. */
    private void fact(List<Component> lines, String label, Component value) {
        lines.add(Component.literal("  " + label + " ").append(value).withColor(LABEL));
    }

    /** A plain run of lines, scrolled as one. Everything the Town tab says. */
    private void drawLines(GuiGraphicsExtractor graphics, int x, int w, int top,
                           List<Component> lines) {
        int height = lines.size() * font.lineHeight + 2;
        clampScroll(height, top);
        int y = top - scroll[tab];
        for (Component line : lines) {
            graphics.text(font, line, x + 4, y, 0xFFFFFFFF, false);
            y += font.lineHeight;
        }
    }

    private void clampScroll(int contentHeight, int top) {
        int visible = bodyTop() + bodyHeight() - top;
        scroll[tab] = Math.clamp(scroll[tab], 0, Math.max(0, contentHeight - visible));
    }

    // --- the People tab ---

    private List<Integer> sortedFolk() {
        List<Dot> folk = town.folk();
        List<Integer> order = new ArrayList<>(folk.size());
        for (int i = 0; i < folk.size(); i++) {
            order.add(i);
        }
        Comparator<Integer> by = switch (sort) {
            case SORT_TRADE -> Comparator.comparing((Integer i) -> folk.get(i).profession())
                    .thenComparing(i -> folk.get(i).name());
            case SORT_HUNGER -> Comparator.comparingInt((Integer i) -> -folk.get(i).hunger())
                    .thenComparing(i -> folk.get(i).name());
            default -> Comparator.comparing(i -> folk.get(i).name());
        };
        order.sort(by);
        return order;
    }

    private void drawPeople(GuiGraphicsExtractor graphics, int x, int w, int top,
                            int mouseX, int mouseY) {
        String by = switch (sort) {
            case SORT_TRADE -> "trade";
            case SORT_HUNGER -> "hunger";
            default -> "name";
        };
        graphics.text(font, Component.literal("sorted by " + by + " — click to change"),
                x + 4, bodyTop() + tabStripHeight() + 2, SUBTLE, false);

        List<Integer> order = sortedFolk();
        clampScroll(order.size() * LIST_ROW, top);
        for (int row = 0; row < order.size(); row++) {
            int index = order.get(row);
            Dot dot = town.folk().get(index);
            int y = top + row * LIST_ROW - scroll[tab];
            if (y + LIST_ROW < top || y > bodyTop() + bodyHeight()) {
                continue;
            }
            if (index == selectedFolk) {
                graphics.fill(x + 1, y - 1, x + w - 1, y + LIST_ROW - 2, STRIPE);
            } else if (row % 2 == 1) {
                graphics.fill(x + 1, y - 1, x + w - 1, y + LIST_ROW - 2, 0x10FFFFFF);
            }
            graphics.fill(x + 2, y + 1, x + 5, y + 4, tradeColor(dot.profession()));
            graphics.text(font, Component.literal(fit(dot.name(), w - 50)),
                    x + 8, y, index == selectedFolk ? TITLE : LABEL, false);
            String hunger = Integer.toString(dot.hunger());
            graphics.text(font, Component.literal(hunger),
                    x + w - 6 - font.width(hunger), y, hungerColor(dot.hunger()), false);
            graphics.text(font, Component.literal(
                            fit(dot.profession() + " · " + dot.doing(), w - 12)),
                    x + 8, y + 9, SUBTLE, false);
        }
    }

    // --- the Buildings tab ---

    /** Grouped by what a building is for, so a town reads as its parts. */
    private List<Integer> sortedPlots() {
        List<Plot> plots = town.plots();
        List<Integer> order = new ArrayList<>(plots.size());
        for (int i = 0; i < plots.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt((Integer i) -> group(plots.get(i)))
                .thenComparing(i -> pretty(plots.get(i).blueprintId()))
                .thenComparingInt(i -> plots.get(i).finished() ? 0 : 1));
        return order;
    }

    private void drawBuilt(GuiGraphicsExtractor graphics, int x, int w, int top) {
        List<Integer> order = sortedPlots();
        clampScroll(order.size() * LIST_ROW, top);
        if (order.isEmpty()) {
            graphics.text(font, Component.literal("  Nothing stands here yet."),
                    x + 4, top, SUBTLE, false);
            return;
        }
        for (int row = 0; row < order.size(); row++) {
            int index = order.get(row);
            Plot plot = town.plots().get(index);
            int y = top + row * LIST_ROW - scroll[tab];
            if (y + LIST_ROW < top || y > bodyTop() + bodyHeight()) {
                continue;
            }
            if (index == selectedPlot) {
                graphics.fill(x + 1, y - 1, x + w - 1, y + LIST_ROW - 2, STRIPE);
            } else if (row % 2 == 1) {
                graphics.fill(x + 1, y - 1, x + w - 1, y + LIST_ROW - 2, 0x10FFFFFF);
            }
            graphics.fill(x + 2, y + 1, x + 5, y + 4, colorOf(plot));
            graphics.text(font, Component.literal(fit(pretty(plot.blueprintId()), w - 40)),
                    x + 8, y, index == selectedPlot ? TITLE : LABEL, false);
            if (plot.condition().level() > 0) {
                String level = "L" + plot.condition().level();
                graphics.text(font, Component.literal(level),
                        x + w - 6 - font.width(level), y, SUBTLE, false);
            }
            graphics.text(font, Component.literal(fit(stateOf(plot), w - 12)),
                    x + 8, y + 9, plot.finished() && plot.condition().damage() == 0
                            ? SUBTLE : ALARM_HUNGRY, false);
        }
    }

    private static String stateOf(Plot plot) {
        if (!plot.finished()) {
            return "planned";
        }
        if (plot.condition().damage() > 0) {
            return "damaged " + plot.condition().damage();
        }
        return plot.condition().joined() ? "whole, on a lane" : "whole, no lane";
    }

    // --- the Queue tab ---

    private void drawQueue(GuiGraphicsExtractor graphics, int x, int w, int top) {
        List<Order> queue = town.queue();
        clampScroll(queue.size() * QUEUE_ROW, top);
        if (queue.isEmpty()) {
            graphics.text(font, Component.literal("  Nothing is being built."),
                    x + 4, top, SUBTLE, false);
            return;
        }
        for (int row = 0; row < queue.size(); row++) {
            Order order = queue.get(row);
            int y = top + row * QUEUE_ROW - scroll[tab];
            if (y + QUEUE_ROW < top || y > bodyTop() + bodyHeight()) {
                continue;
            }
            if (row % 2 == 1) {
                graphics.fill(x + 1, y - 1, x + w - 1, y + QUEUE_ROW - 2, 0x10FFFFFF);
            }
            String name = pretty(order.blueprintId())
                    + (order.repair() ? " (repair)" : order.upgrade() ? " (upgrade)" : "");
            graphics.text(font, Component.literal(fit(name, w - 40)), x + 5, y, LABEL, false);
            String percent = Math.round(order.fraction() * 100) + "%";
            graphics.text(font, Component.literal(percent),
                    x + w - 6 - font.width(percent), y, AMOUNT, false);

            int barX = x + 5;
            int barW = w - 11;
            graphics.fill(barX, y + 10, barX + barW, y + 14, 0x40000000);
            graphics.fill(barX, y + 10, barX + (int) Math.round(barW * order.fraction()),
                    y + 14, order.waiting().isBlank() ? FOOD_COLOR : ALARM_HUNGRY);
            graphics.outline(barX, y + 10, barW, 4, RULE);

            // A watched site is built by hand or not at all, so "nought per cent
            // and staying there" is a normal state with a cause, and the cause is
            // the only thing worth printing because it is what a player can fix.
            graphics.text(font, Component.literal(order.waiting().isBlank()
                            ? fit(order.done() + " of " + order.required() + " blocks", w - 12)
                            : fit("waiting: " + order.waiting(), w - 12)),
                    x + 5, y + 16, order.waiting().isBlank() ? SUBTLE : ALARM_HUNGRY, false);
        }
    }

    // --- the History tab ---

    private void drawHistory(GuiGraphicsExtractor graphics, int x, int w, int top) {
        List<Note> events = town.events();
        clampScroll(events.size() * LIST_ROW, top);
        if (events.isEmpty()) {
            graphics.text(font, Component.literal("  Nothing has happened here yet."),
                    x + 4, top, SUBTLE, false);
            return;
        }
        // Newest first: the last thing that happened is the thing you opened this
        // for, and scrolling to the bottom of a growing list to find it is a
        // chore that gets worse the longer the town lives.
        for (int row = 0; row < events.size(); row++) {
            Note note = events.get(events.size() - 1 - row);
            int y = top + row * LIST_ROW - scroll[tab];
            if (y + LIST_ROW < top || y > bodyTop() + bodyHeight()) {
                continue;
            }
            if (row % 2 == 1) {
                graphics.fill(x + 1, y - 1, x + w - 1, y + LIST_ROW - 2, 0x10FFFFFF);
            }
            graphics.text(font, Component.literal(fit(note.message(), w - 12)),
                    x + 5, y, LABEL, false);
            long ago = town.overview().steps() - note.step();
            graphics.text(font, Component.literal("step " + note.step()
                            + (ago > 0 ? "   ·   " + ago + " steps ago" : "")),
                    x + 5, y + 9, SUBTLE, false);
        }
    }

    // --- reading a building ---

    private static final int GROUP_CIVIC = 0;
    private static final int GROUP_DEFENSE = 1;
    private static final int GROUP_HOME = 2;
    private static final int GROUP_FOOD = 3;
    private static final int GROUP_MATERIAL = 4;
    private static final int GROUP_CRAFT = 5;
    private static final int GROUP_OTHER = 6;

    /**
     * Which of the six kinds of building this is.
     *
     * <p>Asked of the simulation's own tables rather than of a byte on the wire:
     * {@link BuildingRole} and {@link Beds} both live in {@code common}, which is
     * on the client as well as the server, so there is exactly one place that
     * says what a storehouse is and this is not a second one.
     */
    private static int group(Plot plot) {
        if (plot.condition().defends()) {
            return GROUP_DEFENSE;
        }
        BuildingRole role = BuildingRole.of(plot.blueprintId());
        if (role == BuildingRole.HALL) {
            return GROUP_CIVIC;
        }
        if (Beds.isHome(plot.blueprintId())) {
            return GROUP_HOME;
        }
        return switch (role) {
            case CROP_FARM, ANIMAL_FARM, GRANARY, HEARTH, MILL -> GROUP_FOOD;
            case LUMBER_CAMP, MINE, STORE -> GROUP_MATERIAL;
            case SMITH, CARPENTRY, MARKET, INN -> GROUP_CRAFT;
            default -> GROUP_OTHER;
        };
    }

    private static int colorOf(Plot plot) {
        return switch (group(plot)) {
            case GROUP_CIVIC -> CIVIC_COLOR;
            case GROUP_DEFENSE -> DEFENSE_COLOR;
            case GROUP_HOME -> HOME_COLOR;
            case GROUP_FOOD -> FOOD_COLOR;
            case GROUP_MATERIAL -> MATERIAL_COLOR;
            case GROUP_CRAFT -> CRAFT_COLOR;
            default -> OTHER_COLOR;
        };
    }

    /**
     * A trade's color, matched leniently.
     *
     * <p>The word came off the wire and a client can be older than the server
     * that sent it. An unknown trade reads as a plain settler rather than taking
     * the screen down with it.
     */
    private static int tradeColor(String profession) {
        return switch (profession) {
            case "farmer" -> 0xFF7BC96F;
            case "builder" -> 0xFFF0A050;
            case "guard" -> 0xFFE06C75;
            case "lumberjack" -> 0xFF9B7653;
            case "miner" -> 0xFF9AA5B1;
            case "smith" -> 0xFFD0D0D0;
            case "trader" -> 0xFF6FA8DC;
            case "miller" -> 0xFFEDE3B0;
            case "carpenter" -> 0xFFC9A227;
            case "shepherd" -> 0xFFB5E0A0;
            case "king" -> 0xFFFFD700;
            case "idler" -> 0xFF707070;
            default -> 0xFFEFEFEF;
        };
    }

    /**
     * The same color at two thirds the light, for an edge against its own fill.
     *
     * <p>Derived rather than listed, so adding a seventh group means adding one
     * color rather than a matched pair somebody has to keep in step.
     */
    private static int darker(int color) {
        int alpha = color & 0xFF000000;
        int red = (color >> 16 & 0xFF) * 2 / 3;
        int green = (color >> 8 & 0xFF) * 2 / 3;
        int blue = (color & 0xFF) * 2 / 3;
        return alpha | red << 16 | green << 8 | blue;
    }

    private static int hungerColor(int hunger) {
        if (hunger >= Person.HUNGER_SEVERE) {
            return ALARM_DYING;
        }
        if (hunger >= Person.HUNGER_WEAK) {
            return ALARM_FAILING;
        }
        if (hunger >= Person.HUNGER_HUNGRY) {
            return ALARM_HUNGRY;
        }
        return SUBTLE;
    }

    private static String appetite(int hunger) {
        if (hunger >= Person.HUNGER_SEVERE) {
            return "starving";
        }
        if (hunger >= Person.HUNGER_WEAK) {
            return "too weak to work";
        }
        if (hunger >= Person.HUNGER_HUNGRY) {
            return "hungry";
        }
        return "fed";
    }

    /**
     * A blueprint id as a name: {@code civilization:orc/great_hut_l2} is "Great hut".
     *
     * <p>Written here rather than borrowed from {@code Tallies.pretty}, which
     * reads a stat key and throws on an empty string — and an empty blueprint id
     * is exactly what an unknown building off the wire looks like.
     */
    private static String pretty(String blueprintId) {
        String id = BuildPlanner.baseIdOf(blueprintId == null ? "" : blueprintId);
        int colon = id.indexOf(':');
        String path = colon < 0 ? id : id.substring(colon + 1);
        int slash = path.lastIndexOf('/');
        return prettyWord(slash < 0 ? path : path.substring(slash + 1));
    }

    private static String prettyWord(String word) {
        if (word == null || word.isEmpty()) {
            return "something";
        }
        String spaced = word.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** As much of a string as fits, with an ellipsis when it does not. */
    private String fit(String text, int pixels) {
        if (font.width(text) <= pixels) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, pixels - font.width("…"))) + "…";
    }

    // --- drawing lines ---

    /**
     * A straight line, a pixel at a time, clipped to the map pane first.
     *
     * <p>There is no line rasteriser in a GUI extractor — everything is a filled
     * rectangle — so this is the one. Clipping before walking is what keeps it
     * cheap: at full zoom a road out of the claim is tens of thousands of pixels
     * long with three of them on screen.
     */
    private void line(GuiGraphicsExtractor graphics, double x0, double y0, double x1, double y1,
                      int color, int thickness) {
        double[] clipped = clip(x0, y0, x1, y1);
        if (clipped == null) {
            return;
        }
        double ax = clipped[0];
        double ay = clipped[1];
        double bx = clipped[2];
        double by = clipped[3];
        int steps = (int) Math.min(MAX_LINE_STEPS,
                Math.ceil(Math.max(Math.abs(bx - ax), Math.abs(by - ay))));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            int px = (int) Math.round(ax + (bx - ax) * t);
            int py = (int) Math.round(ay + (by - ay) * t);
            graphics.fill(px, py, px + thickness, py + thickness, color);
        }
    }

    private void dashedLine(GuiGraphicsExtractor graphics, double x0, double y0,
                            double x1, double y1, int color, int thickness) {
        double[] clipped = clip(x0, y0, x1, y1);
        if (clipped == null) {
            return;
        }
        double ax = clipped[0];
        double ay = clipped[1];
        double bx = clipped[2];
        double by = clipped[3];
        int steps = (int) Math.min(MAX_LINE_STEPS,
                Math.ceil(Math.max(Math.abs(bx - ax), Math.abs(by - ay))));
        for (int i = 0; i <= steps; i++) {
            if ((i / DASH) % 2 == 1) {
                continue;
            }
            double t = steps == 0 ? 0 : (double) i / steps;
            int px = (int) Math.round(ax + (bx - ax) * t);
            int py = (int) Math.round(ay + (by - ay) * t);
            graphics.fill(px, py, px + thickness, py + thickness, color);
        }
    }

    /**
     * The stretch of a segment that is inside the map pane, or null for none.
     *
     * <p>Slab clipping: the parameter range is squeezed by each edge in turn, and
     * an empty range means the line misses the pane entirely.
     */
    private double[] clip(double x0, double y0, double x1, double y1) {
        double left = mapX() - 8;
        double right = mapX() + mapWidth() + 8;
        double top = bodyTop() - 8;
        double bottom = bodyTop() + bodyHeight() + 8;

        double dx = x1 - x0;
        double dy = y1 - y0;
        double enter = 0;
        double exit = 1;
        double[][] edges = {
                {-dx, x0 - left}, {dx, right - x0},
                {-dy, y0 - top}, {dy, bottom - y0}};
        for (double[] edge : edges) {
            double p = edge[0];
            double q = edge[1];
            if (p == 0) {
                if (q < 0) {
                    return null;   // parallel to this edge and outside it
                }
                continue;
            }
            double t = q / p;
            if (p < 0) {
                enter = Math.max(enter, t);
            } else {
                exit = Math.min(exit, t);
            }
        }
        if (enter > exit) {
            return null;
        }
        return new double[] {
                x0 + dx * enter, y0 + dy * enter, x0 + dx * exit, y0 + dy * exit};
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
