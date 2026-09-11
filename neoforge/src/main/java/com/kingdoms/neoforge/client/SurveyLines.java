package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.net.SurveyPayload;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.util.List;

/**
 * The surveyor's lamp, drawn.
 *
 * <p>Client only. The server sends a {@link SurveyPayload} once a second while
 * the lamp is in hand; this holds the last one and lays it out again every
 * frame, which is the whole difference between a survey you can read and the
 * field of fading sparks this replaced.
 *
 * <p><strong>The lines do not test depth.</strong> A survey drawn a block above
 * the ground and depth-tested disappears behind the first rise between you and
 * the town, and the thing a player picks the lamp up to do is stand back on a
 * hill and look at the whole plan — which is precisely the view that hides most
 * of it. The alternative was honest about occlusion and useless for its one job,
 * so the lines are drawn over everything and read as an overlay, which is what
 * they are. The height above ground stays: it keeps a street off the grass it
 * crosses rather than z-fighting with it, and it is what makes a line follow the
 * lie of the land instead of floating.
 *
 * <p>Everything the survey carries is relative to its origin and the camera
 * subtracts itself, because the render pose handed to a custom submit is the
 * identity — world coordinates would be doubles the size of the world and
 * floats by the time they reached the buffer.
 */
public final class SurveyLines {

    /**
     * How long a survey stays up without a fresh one, in milliseconds.
     *
     * <p>The server clears the lines by sending an empty survey when the lamp is
     * put away, and that is the ordinary path. This is for the other ones: the
     * packet lost, the player teleported out of range, the server that stops
     * talking. Two seconds is twice the send interval, so an ordinary hiccup
     * does not blink the survey out.
     */
    public static final long HOLD_MILLIS = 2000L;

    /** How high above the sampled ground a line is drawn. */
    private static final double LIFT = 1.0;

    /** A street the plan drew: present, but not a thing you can walk yet. */
    private static final int STREET_COLOR = 0xB0FFFFFF;

    /** A road the town has opened. */
    private static final int ROAD_COLOR = 0xFFFFFFFF;

    /** The wall's ring, told apart from the roads by being gray rather than white. */
    private static final int WALL_COLOR = 0xFFC8C8C8;

    /** A building's plot. */
    private static final int PLOT_COLOR = 0xE0FFFFFF;

    /** A plot that is queued rather than standing, drawn fainter. */
    private static final int PLANNED_PLOT_COLOR = 0x80FFFFFF;

    private static final float STREET_WIDTH = 2.0F;
    private static final float ROAD_WIDTH = 3.0F;
    private static final float WALL_WIDTH = 3.0F;
    private static final float PLOT_WIDTH = 1.5F;

    /** How far past the wall a door's tick sticks out. */
    private static final double DOOR_TICK = 1.5;

    /**
     * Lines that ignore the depth buffer.
     *
     * <p>A copy of vanilla's line pipeline with the depth test turned off and
     * the depth write with it — the shaders, the vertex format and the topology
     * all come from {@code LINES_SNIPPET}, so this needs no shader of its own.
     * There is no stock render type for a see-through line: the two vanilla has
     * both test depth, and the one that skips the <em>write</em> still hides
     * behind a hill.
     */
    public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "pipeline/survey_lines"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    private static final RenderType SURVEY_LINES = RenderType.create(
            "kingdoms_survey_lines", RenderSetup.builder(PIPELINE).createRenderSetup());

    /** The latest survey, or null when there is nothing to draw. */
    private static volatile SurveyPayload survey;

    /** When it arrived, so a survey nobody is refreshing goes out on its own. */
    private static volatile long arrivedAt;

    private SurveyLines() {
    }

    /** Called from the payload handler on the client thread. */
    public static void accept(SurveyPayload payload) {
        survey = payload == null || payload.isEmpty() ? null : payload;
        arrivedAt = System.currentTimeMillis();
    }

    /** Drops whatever is held. For leaving a world, where the lines are about a plan that is gone. */
    public static void forget() {
        survey = null;
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PIPELINE);
    }

    /**
     * Submits the survey's geometry for the frame.
     *
     * <p>26.2 draws the level from a collected list of submits rather than by
     * writing into a buffer source mid-pass, so this is the custom-geometry
     * event rather than a render stage: the stage events carry no buffer and
     * their pose stack is a null placeholder, which leaves nothing to draw into.
     */
    public static void render(SubmitCustomGeometryEvent event) {
        SurveyPayload showing = survey;
        if (showing == null || System.currentTimeMillis() - arrivedAt > HOLD_MILLIS) {
            return;
        }
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        if (!camera.initialized) {
            return;
        }
        Vec3 eye = camera.pos;
        Matrix4fc modelView = RenderSystem.getModelViewMatrixCopy();
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), SURVEY_LINES,
                (pose, buffer) -> draw(pose, buffer, showing, eye, modelView));
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer buffer, SurveyPayload showing,
                             Vec3 eye, Matrix4fc modelView) {
        BlockPos origin = showing.origin();
        Pen pen = new Pen(pose, buffer, eye, modelView);
        for (SurveyPayload.Run run : showing.runs()) {
            if (!run.isDrawable()) {
                continue;
            }
            int color = switch (run.kind()) {
                case SurveyPayload.STREET -> STREET_COLOR;
                case SurveyPayload.WALL -> WALL_COLOR;
                default -> ROAD_COLOR;
            };
            float width = switch (run.kind()) {
                case SurveyPayload.STREET -> STREET_WIDTH;
                case SurveyPayload.WALL -> WALL_WIDTH;
                default -> ROAD_WIDTH;
            };
            // A stretch the town has drawn but never opened is drawn as a dash:
            // every other piece, and the pieces are already the survey's own
            // sampling step apart. Dimming it instead would have read as
            // distance rather than as intent.
            boolean dashed = run.kind() == SurveyPayload.ROAD_PLANNED;
            List<SurveyPayload.Vertex> path = run.path();
            for (int i = 0; i < path.size() - 1; i++) {
                if (dashed && (i & 1) == 1) {
                    continue;
                }
                SurveyPayload.Vertex from = path.get(i);
                SurveyPayload.Vertex to = path.get(i + 1);
                pen.line(origin.getX() + from.dx() + 0.5, origin.getY() + from.dy() + LIFT,
                        origin.getZ() + from.dz() + 0.5,
                        origin.getX() + to.dx() + 0.5, origin.getY() + to.dy() + LIFT,
                        origin.getZ() + to.dz() + 0.5,
                        color, width);
            }
        }
        for (SurveyPayload.Plot plot : showing.plots()) {
            plot(pen, origin, plot);
        }
    }

    /**
     * One building's room: the box it holds, and a tick at its door.
     *
     * <p>A box rather than the rectangle this drew before, and the difference is
     * the whole point of carrying a height on the wire. Four lines on the grass
     * say a piece of ground is spoken for and say it only to somebody directly
     * above them; from the hill the lamp is actually carried up to, a flat plan
     * of a village is a scatter of foreshortened slivers with no way to tell a
     * cottage from the hall. Twelve edges stand the plan up: you read the height
     * of what is going there, you can see a plot behind another one, and a queued
     * order reads as the volume it has claimed rather than as a mark on the
     * floor.
     *
     * <p>The tick stays on the bottom edge, where a door is. It is what makes a
     * box a building: the same twelve lines with a mark on one side at ground
     * level say which way the place faces, which is the question the lamp exists
     * to answer about a street.
     *
     * <p>The floor course is lifted clear of the ground the way every other line
     * here is, and the top is not — it is the true roof line. A building one
     * course tall therefore draws as the flat rectangle it is, which is right.
     */
    private static void plot(Pen pen, BlockPos origin, SurveyPayload.Plot plot) {
        double x = origin.getX() + plot.dx() + 0.5;
        double y = origin.getY() + plot.dy() + LIFT;
        double top = origin.getY() + plot.dy() + Math.max(plot.height(), LIFT);
        double z = origin.getZ() + plot.dz() + 0.5;
        double rx = plot.width() / 2.0;
        double rz = plot.depth() / 2.0;
        int color = plot.finished() ? PLOT_COLOR : PLANNED_PLOT_COLOR;

        for (double course : new double[] {y, top}) {
            pen.line(x - rx, course, z - rz, x + rx, course, z - rz, color, PLOT_WIDTH);
            pen.line(x + rx, course, z - rz, x + rx, course, z + rz, color, PLOT_WIDTH);
            pen.line(x + rx, course, z + rz, x - rx, course, z + rz, color, PLOT_WIDTH);
            pen.line(x - rx, course, z + rz, x - rx, course, z - rz, color, PLOT_WIDTH);
        }
        // The four corner posts, which are what make the two squares one box
        // rather than two plots at different heights.
        pen.line(x - rx, y, z - rz, x - rx, top, z - rz, color, PLOT_WIDTH);
        pen.line(x + rx, y, z - rz, x + rx, top, z - rz, color, PLOT_WIDTH);
        pen.line(x + rx, y, z + rz, x + rx, top, z + rz, color, PLOT_WIDTH);
        pen.line(x - rx, y, z + rz, x - rx, top, z + rz, color, PLOT_WIDTH);

        // Facing counts quarter turns the way the simulation does: nought is a
        // door on the +Z wall, and each turn moves it a quarter clockwise.
        switch (plot.facing()) {
            case 1 -> pen.line(x - rx, y, z, x - rx - DOOR_TICK, y, z, color, ROAD_WIDTH);
            case 2 -> pen.line(x, y, z - rz, x, y, z - rz - DOOR_TICK, color, ROAD_WIDTH);
            case 3 -> pen.line(x + rx, y, z, x + rx + DOOR_TICK, y, z, color, ROAD_WIDTH);
            default -> pen.line(x, y, z + rz, x, y, z + rz + DOOR_TICK, color, ROAD_WIDTH);
        }
    }

    /**
     * Writes line vertices, camera-relative, with the near plane respected.
     *
     * <p>The near-plane clip is vanilla's, from the renderer that draws debug
     * gizmos, and it is not optional for lines that ignore depth: a segment with
     * one end behind the eye projects to nonsense and smears across the screen.
     * Both ends behind, and there is nothing to draw at all.
     */
    private record Pen(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Matrix4fc modelView) {

        /** How far in front of the eye a vertex has to be to be drawn. */
        private static final float NEAR = -0.05F;

        private void line(double x1, double y1, double z1, double x2, double y2, double z2,
                          int color, float width) {
            Vector4f from = new Vector4f(
                    (float) (x1 - eye.x()), (float) (y1 - eye.y()), (float) (z1 - eye.z()), 1.0F);
            Vector4f to = new Vector4f(
                    (float) (x2 - eye.x()), (float) (y2 - eye.y()), (float) (z2 - eye.z()), 1.0F);
            Vector4f fromView = from.mul(modelView, new Vector4f());
            Vector4f toView = to.mul(modelView, new Vector4f());
            boolean fromBehind = fromView.z > NEAR;
            boolean toBehind = toView.z > NEAR;
            if (fromBehind && toBehind) {
                return;
            }
            if (fromBehind || toBehind) {
                float span = toView.z - fromView.z;
                if (Math.abs(span) < 1.0E-9F) {
                    return;
                }
                float cut = Mth.clamp((NEAR - fromView.z) / span, 0.0F, 1.0F);
                Vector4f meeting = from.lerp(to, cut, new Vector4f());
                if (fromBehind) {
                    from.set(meeting);
                } else {
                    to.set(meeting);
                }
            }
            float dx = to.x - from.x;
            float dy = to.y - from.y;
            float dz = to.z - from.z;
            buffer.addVertex(pose, from.x, from.y, from.z)
                    .setNormal(pose, dx, dy, dz)
                    .setColor(color)
                    .setLineWidth(width);
            buffer.addVertex(pose, to.x, to.y, to.z)
                    .setNormal(pose, dx, dy, dz)
                    .setColor(color)
                    .setLineWidth(width);
        }
    }
}
