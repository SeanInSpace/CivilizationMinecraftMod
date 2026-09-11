package com.kingdoms.neoforge.net;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.client.SurveyLines;
import com.kingdoms.sim.culture.TownPlan;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * What the surveyor's lamp is looking at: one settlement's plan, as lines.
 *
 * <p>The lamp used to draw itself out of server particles, a mark every quarter
 * block along every edge, on the settlement manager's beat. That beat is a pass
 * every twenty ticks and the survey only redrew on every fourth pass, so the
 * whole picture was repainted once every four seconds and spent most of that
 * time fading. It read as a flicker rather than a drawing, which is the one
 * thing an instrument for reading shape must not do.
 *
 * <p>So the shape travels instead of the paint. The server sends this once a
 * second — and at once when the lamp is picked up or put away — and the client
 * draws it every frame, which is what makes the lines constant.
 *
 * <p><strong>Coordinates are offsets from {@link #origin}</strong>, which is the
 * settlement's center, and each one is a short. A survey is a few hundred points
 * and a full world coordinate is five bytes of varint once it goes negative;
 * six bytes a point, measured from the town, is a third of that and cannot be
 * wrong about a town smaller than a region. {@link Vertex} clamps rather than
 * throws, because a payload whose encoder throws is not a skipped packet — netty
 * drops the connection.
 *
 * <p>The ground height travels too, one per point, because the server is the
 * one that knows it. A client that guessed would draw a street sunk into the
 * rise it climbs.
 */
public record SurveyPayload(BlockPos origin, List<Run> runs, List<Plot> plots)
        implements CustomPacketPayload {

    /** A street the plan drew, whether or not anybody has walked it. */
    public static final int STREET = 0;

    /** A stretch of road the town has opened: somewhere you can actually go. */
    public static final int ROAD_OPENED = 1;

    /** A stretch the network holds but has not opened. Drawn dashed, and meant to be. */
    public static final int ROAD_PLANNED = 2;

    /** The wall's ring, closed or not. */
    public static final int WALL = 3;

    /**
     * One line, as the run of points it passes through.
     *
     * <p>A polyline rather than a pile of segments so a street that bends stays
     * one line with one kind: the client joins consecutive points, and the dash
     * pattern on a planned road runs along the whole street rather than
     * restarting at every joint.
     */
    public record Run(int kind, List<Vertex> path) {

        public Run {
            path = List.copyOf(path);
        }

        public boolean isDrawable() {
            return path.size() >= 2;
        }
    }

    /** One point on a line, as an offset from the survey's origin. */
    public record Vertex(int dx, int dy, int dz) {

        public Vertex {
            dx = clamp(dx);
            dy = clamp(dy);
            dz = clamp(dz);
        }

        private static int clamp(int value) {
            return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
        }
    }

    /**
     * One building's room, as a box centered on its origin.
     *
     * <p>A rectangle on the floor was the first answer and it is the wrong shape
     * to read from anywhere but straight overhead: a plan drawn flat tells you
     * where a building's ground is and nothing about the building, so two houses
     * and a tower all looked the same from the hill the lamp is carried up. The
     * box says how much air the plan has spoken for as well as how much ground,
     * which is the question you are standing on the hill to ask.
     *
     * <p>{@code height} is courses from the floor to the roof, one short on the
     * wire like the offsets. {@code facing} is the quarter turns the building was
     * given, which is what tells the client which side the door is on — the lamp
     * draws a short tick there, along the bottom edge, so a plot reads as a
     * building rather than a crate.
     */
    public record Plot(int dx, int dy, int dz, int width, int depth, int height,
                       int facing, String blueprintId, boolean finished) {

        public Plot {
            dx = Vertex.clamp(dx);
            dy = Vertex.clamp(dy);
            dz = Vertex.clamp(dz);
            width = Math.max(1, Math.min(MAX_SPAN, width));
            depth = Math.max(1, Math.min(MAX_SPAN, depth));
            height = Math.max(1, Math.min(MAX_HEIGHT, height));
            facing = Math.floorMod(facing, 4);
            blueprintId = clip(blueprintId);
        }
    }

    public static final Type<SurveyPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "survey"));

    /** Nothing to draw. Sent the moment the lamp goes away, so the lines go with it. */
    public static final SurveyPayload NONE =
            new SurveyPayload(BlockPos.ZERO, List.of(), List.of());

    /**
     * The blueprint id a pending scan region is drawn under.
     *
     * <p>Not a building, and deliberately shaped like one anyway. The lamp
     * already draws a box with a tick on the side a thing faces, which is
     * exactly the instrument somebody laying out a scan wants: it is the same
     * question — how much room does this take and which way round is it — asked
     * about a region instead of about a plot. A second kind of line on the wire
     * would have been a second renderer, a second budget and a second thing to
     * keep in step, for a picture that is already drawn.
     */
    public static final String SCAN_REGION = "kingdoms:scan_region";

    /** An empty survey measured from somewhere in particular. */
    public static SurveyPayload empty(BlockPos origin) {
        return new SurveyPayload(origin, List.of(), List.of());
    }

    /**
     * The same survey with one more box in it.
     *
     * <p>Added past the {@link #MAX_PLOTS} cap on purpose: the cap exists so a
     * city's worth of buildings cannot grow the packet without limit, and this
     * is one box that the person holding the lamp asked for by name. Losing it
     * behind ninety-six houses would make the frame useless in exactly the place
     * somebody is most likely to be authoring a building.
     */
    public SurveyPayload with(Plot extra) {
        List<Plot> both = new ArrayList<>(plots.size() + 1);
        both.addAll(plots);
        both.add(extra);
        return new SurveyPayload(origin, runs, both);
    }

    /** How far from the player a survey reaches, in blocks. */
    public static final double RANGE = 128.0;

    /** Blocks between ground samples along a line. */
    public static final int STEP = 2;

    /**
     * How many points the streets and the roads may spend between them.
     *
     * <p>Capped rather than trusted. A city's network is hundreds of stretches
     * and a survey is sent every second; without a ceiling the packet grows with
     * the town and the lamp gets slower exactly where it is most worth holding.
     */
    public static final int MAX_PATH_VERTICES = 900;

    /**
     * And how many the wall may spend, kept separate on purpose.
     *
     * <p>A shared budget would be spent by the streets before the ring was
     * reached, so a town with a lot of road would draw no wall at all — and the
     * ring is the one line that tells you where the town stops.
     */
    public static final int MAX_WALL_VERTICES = 300;

    /** How many buildings' plots are drawn, nearest first. */
    public static final int MAX_PLOTS = 96;

    /** The widest plot rectangle the wire will carry. */
    public static final int MAX_SPAN = 256;

    /**
     * The tallest plot box the wire will carry.
     *
     * <p>Four times the tallest thing a town builds, which is the point: it is a
     * clamp against a nonsense number reaching the encoder, not a judgement about
     * how high a building may be. A short would carry twenty times this.
     */
    public static final int MAX_HEIGHT = 256;

    /**
     * How tall a queued building is drawn before anybody has measured one: six.
     *
     * <p>Width and depth have a declared size to fall back on and height has
     * none — a blueprint's height is what {@code BlueprintPlacer} measures off
     * the placements once the thing is drawn, because a roof that rises with the
     * depth and a chimney that has to clear the ridge are not in the catalog. Six
     * is what an ordinary cottage comes out at: three courses of wall and its
     * roof. The box is redrawn at the true height on the step the building is
     * raised, so this is only ever what an order looks like while it is waiting.
     */
    public static final int PLANNED_HEIGHT = 6;

    private static final int MAX_ID = 96;

    private static final StreamCodec<io.netty.buffer.ByteBuf, Integer> SHORT_INT =
            ByteBufCodecs.SHORT.map(Short::intValue, Integer::shortValue);

    private static final StreamCodec<RegistryFriendlyByteBuf, Vertex> VERTEX_CODEC =
            StreamCodec.composite(
                    SHORT_INT, Vertex::dx,
                    SHORT_INT, Vertex::dy,
                    SHORT_INT, Vertex::dz,
                    Vertex::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Run> RUN_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Run::kind,
                    VERTEX_CODEC.apply(ByteBufCodecs.list()), Run::path,
                    Run::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Plot> PLOT_CODEC =
            StreamCodec.composite(
                    SHORT_INT, Plot::dx,
                    SHORT_INT, Plot::dy,
                    SHORT_INT, Plot::dz,
                    ByteBufCodecs.VAR_INT, Plot::width,
                    ByteBufCodecs.VAR_INT, Plot::depth,
                    SHORT_INT, Plot::height,
                    ByteBufCodecs.VAR_INT, Plot::facing,
                    ByteBufCodecs.stringUtf8(MAX_ID), Plot::blueprintId,
                    ByteBufCodecs.BOOL, Plot::finished,
                    Plot::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, SurveyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SurveyPayload::origin,
                    RUN_CODEC.apply(ByteBufCodecs.list()), SurveyPayload::runs,
                    PLOT_CODEC.apply(ByteBufCodecs.list()), SurveyPayload::plots,
                    SurveyPayload::new);

    public SurveyPayload {
        runs = List.copyOf(runs);
        plots = List.copyOf(plots);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Whether there is anything here to draw. An empty survey puts the lines out. */
    public boolean isEmpty() {
        return runs.isEmpty() && plots.isEmpty();
    }

    /** Every point in the survey, which is what the cap is a cap on. */
    public int vertexCount() {
        int total = 0;
        for (Run run : runs) {
            total += run.path().size();
        }
        return total;
    }

    /**
     * Reads a settlement's survey for somebody standing at {@code eye}.
     *
     * <p>Pure but for the ground: {@code groundAt} is asked for the surface
     * height of a column and is the only thing here that needs a world. That is
     * what lets this be tested with a hillside made of arithmetic.
     *
     * <p>Everything is clipped to {@link #RANGE} of the eye rather than to the
     * town, and a line that leaves the circle and comes back is two runs. The
     * alternative — dropping any street with one end out of range — blanked the
     * high street the moment you walked to one end of it.
     */
    public static SurveyPayload of(Settlement settlement, SimPos eye, IntBinaryOperator groundAt) {
        SimPos center = settlement.center();
        BlockPos origin = new BlockPos(center.x(), center.y(), center.z());
        List<Run> runs = new ArrayList<>();

        Budget paths = new Budget(MAX_PATH_VERTICES);
        for (TownPlan.Street street : settlement.arrangement().fullPlan(center).streets()) {
            trace(runs, STREET, street.path(), origin, eye, groundAt, paths);
        }
        PathNetwork network = settlement.paths();
        List<PathNetwork.Segment> stretches = network.segments();
        for (int i = 0; i < stretches.size(); i++) {
            PathNetwork.Segment stretch = stretches.get(i);
            int kind = network.isOpened(i) ? ROAD_OPENED : ROAD_PLANNED;
            trace(runs, kind, List.of(stretch.from(), stretch.to()),
                    origin, eye, groundAt, paths);
        }

        Perimeter wall = settlement.perimeter();
        if (wall != null && wall.vertices().size() >= 2) {
            List<SimPos> ring = new ArrayList<>(wall.vertices());
            ring.add(ring.get(0));   // a ring is a line that comes back
            trace(runs, WALL, ring, origin, eye, groundAt, new Budget(MAX_WALL_VERTICES));
        }

        return new SurveyPayload(origin, runs, plots(settlement, eye, origin));
    }

    /**
     * The buildings, standing and queued, nearest first.
     *
     * <p>Nearest first because the list is capped, and the cap has to fall on
     * the far side of town rather than on whichever building happens to have
     * been raised last.
     *
     * <p>A building whose size was never recorded is left out rather than drawn
     * at a guessed span. Measuring one is a world read and belongs to the caller
     * that has a world; by the time a survey is taken the question is only
     * whether the answer is known.
     */
    private static List<Plot> plots(Settlement settlement, SimPos eye, BlockPos origin) {
        record Near(Plot plot, double distance) {
        }
        List<Near> found = new ArrayList<>();
        for (Building building : settlement.buildings()) {
            Footprint footprint = building.footprint();
            if (!footprint.isKnown() || !within(eye, building.origin())) {
                continue;
            }
            found.add(new Near(plot(origin, building.origin(), footprint.width(),
                    footprint.depth(), footprint.height(), building.facing(),
                    building.blueprintId(), true),
                    distance(eye, building.origin())));
        }
        for (BuildTask task : settlement.buildQueue()) {
            if (!BuildPlanner.holdsGround(task.blueprintId()) || !within(eye, task.origin())) {
                continue;   // a repair flight is an errand, not a plot
            }
            Footprint footprint = task.footprint();
            int span = BuildPlanner.plotSpanOf(task.blueprintId(), settlement.catalog());
            int width = footprint.isKnown() ? footprint.width() : span;
            int depth = footprint.isKnown() ? footprint.depth() : span;
            // A footprint is known by its ground, so a task that has one may
            // still carry no height: an order is measured when it is drawn.
            int height = footprint.height() > 0 ? footprint.height() : PLANNED_HEIGHT;
            found.add(new Near(plot(origin, task.origin(), width, depth, height,
                    task.facing(), task.blueprintId(), false),
                    distance(eye, task.origin())));
        }
        found.sort(Comparator.comparingDouble(Near::distance));
        List<Plot> plots = new ArrayList<>(Math.min(found.size(), MAX_PLOTS));
        for (int i = 0; i < found.size() && i < MAX_PLOTS; i++) {
            plots.add(found.get(i).plot());
        }
        return plots;
    }

    private static Plot plot(BlockPos origin, SimPos at, int width, int depth, int height,
                             int facing, String blueprintId, boolean finished) {
        return new Plot(at.x() - origin.getX(), at.y() - origin.getY(), at.z() - origin.getZ(),
                width, depth, height, facing, blueprintId, finished);
    }

    /**
     * Walks a polyline, sampling the ground every {@link #STEP} blocks, and files
     * away whichever stretches of it fall inside the survey's reach.
     */
    private static void trace(List<Run> runs, int kind, List<SimPos> path, BlockPos origin,
                              SimPos eye, IntBinaryOperator groundAt, Budget budget) {
        if (path.size() < 2 || budget.spent()) {
            return;
        }
        List<Vertex> open = new ArrayList<>();
        for (int leg = 0; leg < path.size() - 1; leg++) {
            SimPos from = path.get(leg);
            SimPos to = path.get(leg + 1);
            double run = Math.hypot(to.x() - from.x(), to.z() - from.z());
            int steps = Math.max(1, (int) Math.ceil(run / STEP));
            // The last point of a leg is the first of the next, so it is taken
            // once: every leg but the final one stops a step short.
            int last = leg == path.size() - 2 ? steps : steps - 1;
            for (int i = 0; i <= last; i++) {
                int x = (int) Math.round(from.x() + (to.x() - from.x()) * (double) i / steps);
                int z = (int) Math.round(from.z() + (to.z() - from.z()) * (double) i / steps);
                if (!within(eye, x, z)) {
                    open = close(runs, kind, open, budget);
                    continue;
                }
                if (budget.spent()) {
                    close(runs, kind, open, budget);
                    return;
                }
                open.add(new Vertex(x - origin.getX(),
                        groundAt.applyAsInt(x, z) - origin.getY(), z - origin.getZ()));
                budget.spend();
            }
        }
        close(runs, kind, open, budget);
    }

    private static List<Vertex> close(List<Run> runs, int kind, List<Vertex> open, Budget budget) {
        if (open.size() >= 2) {
            runs.add(new Run(kind, open));
        } else {
            budget.refund(open.size());   // a lone point draws nothing; do not charge for it
        }
        return new ArrayList<>();
    }

    private static boolean within(SimPos eye, SimPos at) {
        return within(eye, at.x(), at.z());
    }

    private static boolean within(SimPos eye, int x, int z) {
        double dx = eye.x() - x;
        double dz = eye.z() - z;
        return dx * dx + dz * dz <= RANGE * RANGE;
    }

    private static double distance(SimPos eye, SimPos at) {
        return Math.hypot(eye.x() - at.x(), eye.z() - at.z());
    }

    private static String clip(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_ID ? text : text.substring(0, MAX_ID);
    }

    /** How many points are left to spend. */
    private static final class Budget {

        private int left;

        private Budget(int left) {
            this.left = left;
        }

        private boolean spent() {
            return left <= 0;
        }

        private void spend() {
            left--;
        }

        private void refund(int points) {
            left += points;
        }
    }

    /**
     * Hands the survey to the client's renderer.
     *
     * <p>Named through a client class the way the screens are, and for the same
     * reason: a handler that said {@code Minecraft} out loud would load it on a
     * dedicated server the moment this class was verified. Nothing on the server
     * side ever calls this — it is a client-bound payload — and nothing on the
     * server side names {@link SurveyLines}.
     */
    public static void handle(SurveyPayload payload, IPayloadContext context) {
        SurveyLines.accept(payload);
    }
}
