package com.kingdoms.neoforge.net;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.Settlement;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The surveyor's lamp on the wire, and the survey it carries.
 *
 * <p>Two things can go wrong here and neither shows up in a compiler. A stream
 * codec is a pair of functions nobody checks: a survey point is three numbers of
 * the same type and a plot is six, which is exactly the shape that transposes
 * silently and puts a depth where a facing belongs. And the survey itself is a
 * cap and a clip — a payload that grew with the town would be sent every second
 * — so what the builder leaves out matters as much as what it puts in.
 *
 * <p>None of it needs a client. The drawing does, and is checked by hand.
 */
class SurveyPayloadTest {

    /** Flat ground, so a test about reach is not also a test about hills. */
    private static final IntBinaryOperator FLAT = (x, z) -> 64;

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static SurveyPayload roundTrip(SurveyPayload sent) {
        RegistryFriendlyByteBuf buf = buffer();
        SurveyPayload.STREAM_CODEC.encode(buf, sent);
        SurveyPayload read = SurveyPayload.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(),
                "the codec read back fewer bytes than it wrote");
        return read;
    }

    private static Settlement town() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Aldenholt", new SimPos(0, 64, 0), 64);
        // Named rather than left to the culture: a lattice has no streets of its
        // own, which keeps a test about roads about roads.
        town.setLayoutId("ring");
        return town;
    }

    @Test
    void aFullSurveySurvivesTheWire() {
        // Every number deliberately different. Three shorts in a row and six in
        // a plot is precisely the shape a transposition round-trips clean on.
        SurveyPayload sent = new SurveyPayload(new BlockPos(-1234, 71, 5678),
                List.of(new SurveyPayload.Run(SurveyPayload.STREET, List.of(
                                new SurveyPayload.Vertex(1, 2, 3),
                                new SurveyPayload.Vertex(4, 5, 6))),
                        new SurveyPayload.Run(SurveyPayload.ROAD_PLANNED, List.of(
                                new SurveyPayload.Vertex(-7, -8, -9),
                                new SurveyPayload.Vertex(10, 11, 12),
                                new SurveyPayload.Vertex(13, 14, 15)))),
                List.of(new SurveyPayload.Plot(16, 17, 18, 19, 20, 21, 3,
                                "kingdoms:cottage", true),
                        new SurveyPayload.Plot(-21, -22, -23, 24, 25, 26, 1,
                                "kingdoms:market", false)));

        SurveyPayload read = roundTrip(sent);

        assertEquals(new BlockPos(-1234, 71, 5678), read.origin());
        assertEquals(sent.runs(), read.runs());
        assertEquals(sent.plots(), read.plots());
        assertEquals(sent, read);
        assertEquals(5, read.vertexCount());
    }

    @Test
    void anEmptySurveySurvivesTheWire() {
        // The packet that puts the lines out when the lamp goes away. It has to
        // arrive as recognizably empty rather than as a survey of nothing in
        // particular, because that is the whole signal.
        SurveyPayload read = roundTrip(SurveyPayload.NONE);

        assertTrue(read.isEmpty());
        assertEquals(SurveyPayload.NONE, read);
    }

    /**
     * The disconnect this could have caused.
     *
     * <p>A custom payload that throws in its encoder is not a skipped packet —
     * netty drops the connection. Offsets are shorts on the wire and a blueprint
     * id is a capped string, so both clamp where they are built and no
     * construction path can make one that will not send.
     */
    @Test
    void absurdValuesAreClampedRatherThanRefusedByTheEncoder() {
        SurveyPayload wild = new SurveyPayload(BlockPos.ZERO,
                List.of(new SurveyPayload.Run(SurveyPayload.WALL, List.of(
                        new SurveyPayload.Vertex(900_000, -900_000, 900_000),
                        new SurveyPayload.Vertex(0, 0, 0)))),
                List.of(new SurveyPayload.Plot(900_000, 0, 0, 100_000, 100_000, 100_000, 9,
                        "b".repeat(500), true)));

        SurveyPayload.Vertex far = wild.runs().getFirst().path().getFirst();
        assertEquals(Short.MAX_VALUE, far.dx());
        assertEquals(Short.MIN_VALUE, far.dy());
        SurveyPayload.Plot plot = wild.plots().getFirst();
        assertTrue(plot.blueprintId().length() < 500, "the blueprint id was not clipped");
        assertEquals(SurveyPayload.MAX_SPAN, plot.width());
        assertEquals(SurveyPayload.MAX_HEIGHT, plot.height(),
                "a height that would not fit a short reached the encoder");
        assertEquals(1, plot.facing(), "nine quarter turns is one quarter turn");
        assertEquals(wild, roundTrip(wild));
    }

    @Test
    void aPlotIsNeverFlatterThanOneCourse() {
        // The box is drawn from the floor to the top, so a height of nought
        // would be a building with no walls -- which is what a building raised
        // before heights were recorded reports. One course is the floor itself.
        SurveyPayload.Plot flat = new SurveyPayload.Plot(0, 0, 0, 9, 9, 0, 0,
                "kingdoms:cottage", true);
        SurveyPayload.Plot sunk = new SurveyPayload.Plot(0, 0, 0, 9, 9, -40, 0,
                "kingdoms:cottage", true);

        assertEquals(1, flat.height());
        assertEquals(1, sunk.height(), "a building cannot be drawn below its own floor");
    }

    @Test
    void openedRoadsAndPlannedOnesAreToldApart() {
        Settlement town = town();
        PathNetwork paths = town.paths();
        paths.add(new PathNetwork.Segment(new SimPos(0, 64, 0), new SimPos(24, 64, 0)));
        paths.add(new PathNetwork.Segment(new SimPos(0, 64, 0), new SimPos(0, 64, 24)));
        paths.markOpened(0);

        SurveyPayload survey = SurveyPayload.of(town, new SimPos(0, 64, 0), FLAT);

        assertEquals(1, count(survey, SurveyPayload.ROAD_OPENED),
                "the stretch the town has walked out");
        assertEquals(1, count(survey, SurveyPayload.ROAD_PLANNED),
                "and the one it has only drawn; the lamp must not tell the same story twice");
    }

    @Test
    void theGroundIsSampledUnderEveryPoint() {
        Settlement town = town();
        town.paths().add(new PathNetwork.Segment(new SimPos(0, 64, 0), new SimPos(20, 64, 0)));
        town.paths().markOpened(0);
        // A slope, so a line drawn at one height would sink into it.
        IntBinaryOperator hill = (x, z) -> 64 + x / 4;

        SurveyPayload survey = SurveyPayload.of(town, new SimPos(0, 64, 0), hill);
        List<SurveyPayload.Vertex> path = runOf(survey, SurveyPayload.ROAD_OPENED).path();

        assertTrue(path.size() > 2, "a twenty-block road is sampled, not just its ends");
        for (SurveyPayload.Vertex point : path) {
            // The origin's y is 64, so the offset is the rise over the town.
            assertEquals(point.dx() / 4, point.dy(),
                    "a point was drawn at a height the ground is not");
        }
    }

    @Test
    void aRoadThatLeavesTheSurveysReachIsCutRatherThanDropped() {
        Settlement town = town();
        // Far longer than the reach, and the player stands at one end. Dropping
        // any road with an end out of range blanked the high street the moment
        // you walked to the end of it.
        town.paths().add(new PathNetwork.Segment(
                new SimPos(0, 64, 0), new SimPos((int) SurveyPayload.RANGE * 3, 64, 0)));
        town.paths().markOpened(0);

        SurveyPayload survey = SurveyPayload.of(town, new SimPos(0, 64, 0), FLAT);
        List<SurveyPayload.Vertex> path = runOf(survey, SurveyPayload.ROAD_OPENED).path();

        assertFalse(path.isEmpty(), "the near end of the road is still drawn");
        for (SurveyPayload.Vertex point : path) {
            assertTrue(point.dx() <= SurveyPayload.RANGE + SurveyPayload.STEP,
                    "a point past the survey's reach travelled anyway");
        }
    }

    @Test
    void aNetworkBiggerThanTheBudgetIsCapped() {
        Settlement town = town();
        // A hundred short stretches all inside the reach: far more points than
        // the cap allows, so the cap is what decides the size of the packet
        // rather than the size of the town.
        for (int i = 0; i < 100; i++) {
            town.paths().add(new PathNetwork.Segment(
                    new SimPos(-60, 64, i - 50), new SimPos(60, 64, i - 50)));
            town.paths().markOpened(i);
        }

        SurveyPayload survey = SurveyPayload.of(town, new SimPos(0, 64, 0), FLAT);

        assertTrue(survey.vertexCount() > 0, "the cap must not blank the survey");
        assertTrue(survey.vertexCount()
                        <= SurveyPayload.MAX_PATH_VERTICES + SurveyPayload.MAX_WALL_VERTICES,
                "the survey spent " + survey.vertexCount() + " points");
        assertEquals(survey, roundTrip(survey));
    }

    @Test
    void theWallKeepsItsOwnBudget() {
        Settlement town = town();
        // The roads alone would eat a shared budget whole, and the ring is the
        // one line that says where the town stops.
        for (int i = 0; i < 100; i++) {
            town.paths().add(new PathNetwork.Segment(
                    new SimPos(-60, 64, i - 50), new SimPos(60, 64, i - 50)));
            town.paths().markOpened(i);
        }
        town.setPerimeter(new Perimeter(List.of(
                new SimPos(-40, 64, -40), new SimPos(40, 64, -40),
                new SimPos(40, 64, 40), new SimPos(-40, 64, 40)), List.of(), 0));

        SurveyPayload survey = SurveyPayload.of(town, new SimPos(0, 64, 0), FLAT);

        assertTrue(count(survey, SurveyPayload.WALL) > 0,
                "the roads spent the wall's budget");
    }

    @Test
    void plotsCarryTheirSizeFacingAndBlueprint() {
        Settlement town = town();
        Building cottage = new Building("kingdoms:cottage", new SimPos(12, 64, -8), 0L);
        cottage.setFootprint(new Footprint(64, 9, 11, 5));
        cottage.setFacing(3);
        town.addBuilding(cottage);
        // Raised before footprints were kept: nothing truthful to draw, so it is
        // left out rather than squared off at a guess.
        town.addBuilding(new Building("kingdoms:storehouse", new SimPos(-6, 64, 4), 0L));

        SurveyPayload survey = SurveyPayload.of(town, new SimPos(0, 64, 0), FLAT);

        assertEquals(1, survey.plots().size());
        SurveyPayload.Plot plot = survey.plots().getFirst();
        assertEquals(12, plot.dx());
        assertEquals(-8, plot.dz());
        assertEquals(9, plot.width());
        assertEquals(11, plot.depth(), "width and depth are not interchangeable");
        assertEquals(5, plot.height(), "the box is as tall as the building was drawn");
        assertEquals(3, plot.facing(), "the facing is what puts the tick at the door");
        assertEquals("kingdoms:cottage", plot.blueprintId());
        assertTrue(plot.finished());
        assertEquals(survey, roundTrip(survey));
    }

    @Test
    void aBuildingOutOfReachIsNotDrawn() {
        Settlement town = town();
        Building far = new Building("kingdoms:cottage",
                new SimPos((int) SurveyPayload.RANGE * 2, 64, 0), 0L);
        far.setFootprint(new Footprint(64, 9, 9, 5));
        town.addBuilding(far);

        assertTrue(SurveyPayload.of(town, new SimPos(0, 64, 0), FLAT).plots().isEmpty());
    }

    @Test
    void aTownWithNothingSurveyedReadsAsEmpty() {
        assertTrue(SurveyPayload.of(town(), new SimPos(0, 64, 0), FLAT).isEmpty(),
                "a town with no roads, no wall and no buildings has nothing to draw");
    }

    private static int count(SurveyPayload survey, int kind) {
        return (int) survey.runs().stream().filter(run -> run.kind() == kind).count();
    }

    private static SurveyPayload.Run runOf(SurveyPayload survey, int kind) {
        return survey.runs().stream().filter(run -> run.kind() == kind).findFirst()
                .orElseThrow(() -> new AssertionError("no run of kind " + kind));
    }
}
