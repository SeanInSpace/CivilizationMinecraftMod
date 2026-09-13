package com.civilization.neoforge.net;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Household;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.BuildTask;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.BuildingSizes;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.PathNetwork;
import com.civilization.sim.settlement.Perimeter;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.TownStores;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The town map on the wire, and the reading it carries.
 *
 * <p>Three things can go wrong here and none of them shows up in a compiler.
 *
 * <p>A stream codec is a pair of functions nobody checks. This payload is ten
 * nested records of same-typed numbers — a plot is a width and a depth and a
 * facing, and a settler is two offsets and two indices — which is exactly the
 * shape that transposes silently and puts a depth where a facing belongs. So
 * every round trip here uses numbers that are all different from each other.
 *
 * <p>A cap that is not enforced is a packet that grows with the town, and this
 * one is sent every second. So what the builder leaves out matters as much as
 * what it puts in.
 *
 * <p>And an encoder that throws is not a skipped packet — netty drops the
 * connection. So nothing that can be built may be unsendable.
 *
 * <p>None of it needs a client. The drawing does, and is checked by hand.
 */
class TownMapPayloadTest {

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static TownMapPayload roundTrip(TownMapPayload sent) {
        RegistryFriendlyByteBuf buf = buffer();
        TownMapPayload.STREAM_CODEC.encode(buf, sent);
        TownMapPayload read = TownMapPayload.STREAM_CODEC.decode(buf);
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

    private static Building cottage(String id, int x, int z, int facing) {
        Building building = new Building(id, new SimPos(x, 64, z), 0L);
        building.setFootprint(new Footprint(64, 9, 11, 5));
        building.setFacing(facing);
        return building;
    }

    // --- the wire ---

    @Test
    void aFullReadingSurvivesTheWire() {
        // Every number deliberately different from every other. A plot is three
        // spans and a facing and a settler is two offsets and two indices, which
        // is precisely the shape a transposition round-trips clean on.
        TownMapPayload.Overview overview = new TownMapPayload.Overview(
                "village", "civilization:norman", 4321L,
                new TownOverviewPayload.Distress(
                        TownOverviewPayload.Distress.ALARM_FAILING, "fact", "remedy"),
                new TownMapPayload.Folk(11, 12, 13, 14, 15, 16, 17, 18),
                new TownMapPayload.Larder(21, 22, 23, 24, 25, 26, 27, true),
                new TownMapPayload.Ledgers(31, 32, true, false, 35, true, false, 38),
                new TownMapPayload.Watch(41, 42, 43, 44, 45, "Karrgurd", 46, 47, 48),
                List.of(new TownMapPayload.Line("food", 51),
                        new TownMapPayload.Line("stone", 52)),
                List.of(new TownMapPayload.Job("farmer", 61),
                        new TownMapPayload.Job("guard", 62)));

        TownMapPayload sent = new TownMapPayload("Aldenholt",
                new BlockPos(-1234, 71, 5678), 96, true, overview,
                List.of(new TownMapPayload.Run(SurveyPayload.STREET, List.of(
                                new TownMapPayload.Vertex(1, 2),
                                new TownMapPayload.Vertex(3, 4))),
                        new TownMapPayload.Run(SurveyPayload.ROAD_PLANNED, List.of(
                                new TownMapPayload.Vertex(-5, -6),
                                new TownMapPayload.Vertex(7, 8),
                                new TownMapPayload.Vertex(9, 10)))),
                List.of(new TownMapPayload.Plot(71, 72, 73, 74,
                                new BuildingSizes.Notch(3, 4, 1, -1), 3, "civilization:cottage",
                                new TownMapPayload.Condition(2, 6, true, false), true,
                                List.of(new TownMapPayload.Line("wood", 77))),
                        new TownMapPayload.Plot(-81, -82, 83, 84,
                                BuildingSizes.Notch.NONE, 1, "civilization:market",
                                new TownMapPayload.Condition(0, 0, false, true), false,
                                List.of())),
                List.of(new TownMapPayload.Dot(91, 92, "Aelfric", "farmer", 93, true, 0, 1,
                        "carrying 3 food to 4,5")),
                List.of(new TownMapPayload.Tree(101, 102), new TownMapPayload.Tree(-103, 104)),
                List.of(new TownMapPayload.Order("civilization:granary", 111, 112, 113, 114,
                        "no timber", true, false)),
                List.of(new TownMapPayload.Note(121L, "Raid of 2 repelled")));

        TownMapPayload read = roundTrip(sent);

        assertEquals(new BlockPos(-1234, 71, 5678), read.origin());
        assertEquals(sent.overview(), read.overview());
        assertEquals(sent.runs(), read.runs());
        assertEquals(sent.plots(), read.plots());
        assertEquals(sent.folk(), read.folk());
        assertEquals(sent.trees(), read.trees());
        assertEquals(sent.queue(), read.queue());
        assertEquals(sent.events(), read.events());
        assertEquals(sent, read);
        assertEquals(5, read.vertexCount());
    }

    @Test
    void theCornerCutOutOfABuildingSurvivesTheWire() {
        // The orcs' huts are octagons, and the quarter a notch points at is two
        // bits on the wire. Getting either of them the wrong way round turns a
        // roundhouse into a building with a hole in the wrong corner.
        for (int towardX : new int[] {1, -1}) {
            for (int towardZ : new int[] {1, -1}) {
                TownMapPayload.Plot plot = new TownMapPayload.Plot(0, 0, 9, 9,
                        new BuildingSizes.Notch(2, 3, towardX, towardZ), 0,
                        "civilization:hut", new TownMapPayload.Condition(0, 0, false, true),
                        true, List.of());
                TownMapPayload read = roundTrip(reading(List.of(plot)));
                assertEquals(plot.notch(), read.plots().getFirst().notch(),
                        "a notch toward " + towardX + "," + towardZ + " came back wrong");
            }
        }
    }

    @Test
    void theRequestSurvivesTheWire() {
        RegistryFriendlyByteBuf buf = buffer();
        TownMapRequestPayload sent = new TownMapRequestPayload(new BlockPos(-900, 70, 1200));
        TownMapRequestPayload.STREAM_CODEC.encode(buf, sent);
        TownMapRequestPayload read = TownMapRequestPayload.STREAM_CODEC.decode(buf);

        assertEquals(0, buf.readableBytes());
        assertEquals(sent, read);
    }

    // --- the caps ---

    @Test
    void absurdValuesAreClampedRatherThanRefusedByTheEncoder() {
        TownMapPayload.Plot wild = new TownMapPayload.Plot(900_000, -900_000,
                100_000, 100_000, new BuildingSizes.Notch(9_000, 9_000, 1, 1), 9,
                "b".repeat(500), new TownMapPayload.Condition(0, 0, false, false), true,
                List.of());

        assertEquals(Short.MAX_VALUE, wild.dx());
        assertEquals(Short.MIN_VALUE, wild.dz());
        assertEquals(TownMapPayload.MAX_SPAN, wild.width());
        assertEquals(1, wild.facing(), "nine quarter turns is one quarter turn");
        assertTrue(wild.blueprintId().length() < 500, "the blueprint id was not clipped");
        assertTrue(wild.notch().width() <= wild.width(),
                "a bite wider than the loaf would draw a hole where the building is");

        TownMapPayload.Dot loud = new TownMapPayload.Dot(700_000, 0, "n".repeat(200),
                "p".repeat(200), 5_000, true, 0, 0, "d".repeat(500));
        assertEquals(Short.MAX_VALUE, loud.dx());
        assertEquals(Person.HUNGER_MAX, loud.hunger());
        assertTrue(loud.name().length() < 200);
        assertTrue(loud.doing().length() < 500);

        assertEquals(wild, roundTrip(reading(List.of(wild))).plots().getFirst());
    }

    @Test
    void everyListIsCapped() {
        List<TownMapPayload.Plot> plots = new ArrayList<>();
        for (int i = 0; i < TownMapPayload.MAX_PLOTS * 2; i++) {
            plots.add(new TownMapPayload.Plot(i, i, 5, 5, BuildingSizes.Notch.NONE, 0,
                    "civilization:cottage", new TownMapPayload.Condition(0, 0, false, true),
                    true, List.of()));
        }
        List<TownMapPayload.Dot> folk = new ArrayList<>();
        for (int i = 0; i < TownMapPayload.MAX_FOLK * 2; i++) {
            folk.add(new TownMapPayload.Dot(i, i, "n" + i, "farmer", 0, true, -1, -1, ""));
        }
        List<TownMapPayload.Note> events = new ArrayList<>();
        for (int i = 0; i < TownMapPayload.MAX_EVENTS * 3; i++) {
            events.add(new TownMapPayload.Note(i, "something happened"));
        }

        TownMapPayload capped = new TownMapPayload("Aldenholt", BlockPos.ZERO, 64, true,
                emptyOverview(), List.of(), plots, folk, List.of(), List.of(), events);

        assertEquals(TownMapPayload.MAX_PLOTS, capped.plots().size());
        assertEquals(TownMapPayload.MAX_FOLK, capped.folk().size());
        assertEquals(TownMapPayload.MAX_EVENTS, capped.events().size());
        // And the decoder's own ceilings agree with the builder's, or a payload
        // this side is happy to write is one the far side refuses to read.
        assertEquals(capped, roundTrip(capped));
    }

    // --- reading a settlement ---

    @Test
    void aSettlementReadsIntoAPlan() {
        Settlement town = town();
        town.setStage(SettlementStage.VILLAGE);
        town.addBuilding(cottage("civilization:cottage", 12, -8, 3));
        town.addBuilding(cottage("civilization:granary", -20, 6, 1));
        town.setStock(TownStores.WOOD, 140);
        town.paths().add(new PathNetwork.Segment(new SimPos(0, 64, 0), new SimPos(24, 64, 0)));
        town.paths().add(new PathNetwork.Segment(new SimPos(0, 64, 0), new SimPos(0, 64, 24)));
        town.paths().markOpened(0);
        town.setPerimeter(new Perimeter(List.of(
                new SimPos(-40, 64, -40), new SimPos(40, 64, -40),
                new SimPos(40, 64, 40), new SimPos(-40, 64, 40)), List.of(), 0));
        town.logEvent(7L, "The granary was raised");

        TownMapPayload read = TownMapPayload.of(town, 99L, true);

        assertEquals("Aldenholt", read.town());
        assertEquals(new BlockPos(0, 64, 0), read.origin());
        assertTrue(read.opening());
        assertEquals(2, read.plots().size());
        assertEquals(12, read.plots().getFirst().dx());
        assertEquals(-8, read.plots().getFirst().dz());
        assertEquals(9, read.plots().getFirst().width());
        assertEquals(11, read.plots().getFirst().depth(),
                "width and depth are not interchangeable");
        assertEquals(3, read.plots().getFirst().facing(),
                "the facing is what puts the tick at the door");
        assertEquals("village", read.overview().stage());
        assertEquals(99L, read.overview().steps());
        assertEquals(1, count(read, SurveyPayload.ROAD_OPENED),
                "the stretch the town has walked out");
        assertEquals(1, count(read, SurveyPayload.ROAD_PLANNED),
                "and the one it has only drawn; the map must not tell the same story twice");
        assertTrue(count(read, SurveyPayload.WALL) > 0, "the ring says where the town stops");
        assertEquals(1, read.events().size());
        assertEquals(7L, read.events().getFirst().step());
        assertTrue(read.overview().stores().stream()
                        .anyMatch(line -> TownStores.WOOD.equals(line.resource())
                                && line.amount() == 140),
                "the ledger travels with the plan");
        assertEquals(read, roundTrip(read));
    }

    @Test
    void aBuildingNobodyMeasuredIsStillDrawn() {
        // The surveyor's lamp leaves one out, because it is drawing the exact
        // ground a plot has taken and has nothing truthful to say. A town's own
        // map is a different document: a building missing from it reads as a
        // building that is not there.
        Settlement town = town();
        town.addBuilding(new Building("civilization:storehouse", new SimPos(-6, 64, 4), 0L));

        TownMapPayload read = TownMapPayload.of(town, 0L, true);

        assertEquals(1, read.plots().size());
        assertTrue(read.plots().getFirst().width() > 0,
                "an unmeasured building is drawn at its declared span, not at nothing");
    }

    @Test
    void aSettlerPointsAtTheRoofTheySleepUnder() {
        Settlement town = town();
        town.addBuilding(cottage("civilization:granary", -20, 6, 0));
        town.addBuilding(cottage("civilization:cottage", 12, -8, 0));

        Person aelfric = new Person(Person.Id.random(), "Aelfric",
                Profession.FARMER, new SimPos(10, 64, -6));
        town.addResident(aelfric);
        Household family = new Household(Household.Id.random(), "Aelfric");
        family.addMember(aelfric.id());
        family.setHome(new SimPos(12, 64, -8));
        town.addHousehold(family);

        TownMapPayload read = TownMapPayload.of(town, 0L, true);

        assertEquals(1, read.folk().size());
        TownMapPayload.Dot dot = read.folk().getFirst();
        assertEquals("Aelfric", dot.name());
        assertEquals("farmer", dot.profession());
        assertEquals(10, dot.dx());
        assertEquals(-6, dot.dz());
        assertEquals(1, dot.home(), "the cottage is the second plot, not the granary");
        assertEquals("civilization:cottage", read.plots().get(dot.home()).blueprintId());
    }

    @Test
    void aSettlerWithNoRoofSaysSo() {
        Settlement town = town();
        town.addBuilding(cottage("civilization:cottage", 12, -8, 0));
        town.addResident(new Person(Person.Id.random(), "Wulf",
                Profession.PIONEER, new SimPos(1, 64, 2)));

        assertEquals(-1, TownMapPayload.of(town, 0L, true).folk().getFirst().home(),
                "a settler in nobody's house must not be filed under somebody's");
    }

    @Test
    void atradeIsPointedAtTheBuildingItIsPlied() {
        Settlement town = town();
        town.addBuilding(cottage("civilization:cottage", 40, 40, 0));
        town.addBuilding(cottage("civilization:farm", 12, -8, 0));
        town.addResident(new Person(Person.Id.random(), "Aelfric",
                Profession.FARMER, new SimPos(10, 64, -6)));

        TownMapPayload.Dot dot = TownMapPayload.of(town, 0L, true).folk().getFirst();

        assertEquals(1, dot.work());
        assertEquals(BuildingRole.CROP_FARM,
                BuildingRole.of(TownMapPayload.of(town, 0L, true)
                        .plots().get(dot.work()).blueprintId()));
    }

    @Test
    void queuedWorkCarriesItsReason() {
        Settlement town = town();
        BuildTask task = new BuildTask("civilization:granary", new SimPos(18, 64, 18), 400);
        task.addProgress(120);
        task.setWaitingOnHands("nobody is building");
        town.enqueueBuild(task);

        TownMapPayload read = TownMapPayload.of(town, 0L, true);

        assertEquals(1, read.queue().size());
        TownMapPayload.Order order = read.queue().getFirst();
        assertEquals("civilization:granary", order.blueprintId());
        assertEquals(120, order.done());
        assertEquals(400, order.required());
        assertEquals("nobody is building", order.waiting());
        assertEquals(0.3, order.fraction(), 0.0001);
        // And the same order is a plot on the plan, drawn hollow.
        assertEquals(1, read.plots().size());
        assertFalse(read.plots().getFirst().finished());
    }

    @Test
    void aBuildingsConditionTravelsWithIt() {
        Settlement town = town();
        Building hurt = cottage("civilization:cottage", 12, -8, 0);
        hurt.setLevel(2);
        hurt.setDamage(17);
        town.addBuilding(hurt);
        town.paths().markJoined(hurt.origin());

        TownMapPayload.Condition condition =
                TownMapPayload.of(town, 0L, true).plots().getFirst().condition();

        assertEquals(2, condition.level());
        assertEquals(17, condition.damage());
        assertTrue(condition.joined(), "a lane reaches this door and the map should say so");
    }

    @Test
    void aTownWithNothingInItStillReads() {
        // The screen opens on a camp that has just been founded as readily as on
        // a city, and a payload that threw on an empty larder would take the one
        // moment a player most wants to look at it.
        TownMapPayload read = TownMapPayload.of(town(), 0L, true);

        assertTrue(read.plots().isEmpty());
        assertTrue(read.folk().isEmpty());
        assertTrue(read.queue().isEmpty());
        assertEquals(read, roundTrip(read));
    }

    @Test
    void theWallKeepsItsOwnBudget() {
        Settlement town = town();
        // The roads alone would eat a shared budget whole, and the ring is the
        // one line that tells you where the town stops.
        for (int i = 0; i < 900; i++) {
            town.paths().add(new PathNetwork.Segment(
                    new SimPos(-60, 64, i - 450), new SimPos(60, 64, i - 450)));
            town.paths().markOpened(i);
        }
        town.setPerimeter(new Perimeter(List.of(
                new SimPos(-40, 64, -40), new SimPos(40, 64, -40),
                new SimPos(40, 64, 40), new SimPos(-40, 64, 40)), List.of(), 0));

        TownMapPayload read = TownMapPayload.of(town, 0L, true);

        assertTrue(count(read, SurveyPayload.WALL) > 0, "the roads spent the wall's budget");
        assertTrue(read.vertexCount() <= TownMapPayload.MAX_PATH_VERTICES
                        + TownMapPayload.MAX_WALL_VERTICES,
                "the plan spent " + read.vertexCount() + " points");
        assertEquals(read, roundTrip(read));
    }

    @Test
    void aReadingThatIsNotOpeningOneSaysSo() {
        // The flag the client leans on so a reply arriving a tick after escape
        // does not put the map back up.
        assertFalse(TownMapPayload.of(town(), 0L, false).opening());
        assertNotEquals(TownMapPayload.of(town(), 0L, false),
                TownMapPayload.of(town(), 0L, true));
    }

    // --- helpers ---

    private static TownMapPayload reading(List<TownMapPayload.Plot> plots) {
        return new TownMapPayload("Aldenholt", BlockPos.ZERO, 64, true, emptyOverview(),
                List.of(), plots, List.of(), List.of(), List.of(), List.of());
    }

    private static TownMapPayload.Overview emptyOverview() {
        return new TownMapPayload.Overview("camp", "civilization:norman", 0L,
                TownOverviewPayload.Distress.STEADY,
                new TownMapPayload.Folk(0, 0, 0, 0, 0, 0, 0, 0),
                new TownMapPayload.Larder(0, 0, 0, 0, 0, 0, 0, false),
                new TownMapPayload.Ledgers(0, 0, false, false, 0, false, false, 0),
                new TownMapPayload.Watch(0, 0, 0, 0, 0, "", 0, 0, 0),
                List.of(), List.of());
    }

    private static int count(TownMapPayload map, int kind) {
        return (int) map.runs().stream().filter(run -> run.kind() == kind).count();
    }
}
