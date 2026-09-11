package com.civilization.neoforge.net;

import com.civilization.neoforge.CivilizationMod;
import com.civilization.neoforge.client.CivilizationScreens;
import com.civilization.sim.culture.TownPlan;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Appetite;
import com.civilization.sim.person.HaulTask;
import com.civilization.sim.person.Household;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.BuildTask;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.BuildingSizes;
import com.civilization.sim.settlement.Field;
import com.civilization.sim.settlement.FoodPlanner;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.ForesterStand;
import com.civilization.sim.settlement.Garrison;
import com.civilization.sim.settlement.JobPlanner;
import com.civilization.sim.settlement.KingPlanner;
import com.civilization.sim.settlement.PathNetwork;
import com.civilization.sim.settlement.Perimeter;
import com.civilization.sim.settlement.PopulationPlanner;
import com.civilization.sim.settlement.RaidPlanner;
import com.civilization.sim.settlement.Seam;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementEvent;
import com.civilization.sim.settlement.Stand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The whole town, as one reading: its plan, its people, its books.
 *
 * <p>This used to be a plan and nothing else — a list of rectangles and a list
 * of roads, sent once when a map was opened and never again. That was a picture
 * of a town rather than a way of reading one: it could not say what a building
 * was, who was in it, what the queue was waiting on, or why the granary was
 * empty, and every one of those answers existed only in {@code /civ info}, which
 * is a wall of text behind an operator permission.
 *
 * <p>So the payload carries what the report carries. The screen lays it out.
 *
 * <p><strong>Coordinates are offsets from {@link #origin}</strong> — the
 * settlement's center — and every one is a short, exactly as
 * {@link SurveyPayload} does it and for the same reason: a town is a few
 * thousand points, a world coordinate is five bytes of varint once it goes
 * negative, and a payload sent once a second should not grow with how far from
 * spawn the town happens to be. Every offset clamps rather than throws, because
 * a payload whose encoder throws is not a skipped packet — netty drops the
 * connection.
 *
 * <p>Every list is capped. The screen asks for a fresh reading every second
 * while it is open, so the size of the packet has to be decided by this file
 * rather than by how big the town got.
 *
 * <p>The line kinds are {@link SurveyPayload}'s, deliberately: the lamp and the
 * map draw the same four things and a town whose planned roads were dashed on
 * one surface and solid on the other would be two different towns.
 */
public record TownMapPayload(String town, BlockPos origin, int claimRadius, boolean opening,
                             Overview overview, List<Run> runs, List<Plot> plots,
                             List<Dot> folk, List<Tree> trees, List<Order> queue,
                             List<Note> events)
        implements CustomPacketPayload {

    // --- the pieces ---

    /** One point on a line, as an offset from the map's origin. Flat: no height. */
    public record Vertex(int dx, int dz) {

        public Vertex {
            dx = clamp(dx);
            dz = clamp(dz);
        }

        static int clamp(int value) {
            return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
        }
    }

    /**
     * One line of the plan, as the run of points it passes through.
     *
     * <p>A polyline rather than a pile of segments, so a street that bends stays
     * one line: the dashes on a planned road run along the whole street instead
     * of restarting at every joint, and the wall's ring closes.
     *
     * <p>{@code kind} is one of {@link SurveyPayload#STREET},
     * {@link SurveyPayload#ROAD_OPENED}, {@link SurveyPayload#ROAD_PLANNED} or
     * {@link SurveyPayload#WALL}.
     */
    public record Run(int kind, List<Vertex> path) {

        public Run {
            path = List.copyOf(path);
        }

        public boolean isDrawable() {
            return path.size() >= 2;
        }
    }

    /** One row of a ledger: a resource and how much of it there is. */
    public record Line(String resource, int amount) {

        public Line {
            resource = clip(resource, MAX_WORD);
        }
    }

    /** How many of a trade the town has. */
    public record Job(String profession, int count) {

        public Job {
            profession = clip(profession, MAX_WORD);
        }
    }

    /**
     * What state a standing building is in.
     *
     * <p>Bundled rather than spread across {@link Plot} because a plot is
     * already at the arity the wire's composer allows, and because these four
     * are one question: how good a building is this, right now.
     *
     * @param level    the upgrade level it has been raised to
     * @param damage   how badly a raid or a creeper hurt it, zero when whole
     * @param defends  whether the catalog gives it a defense bonus
     * @param joined   whether a lane reaches its door
     */
    public record Condition(int level, int damage, boolean defends, boolean joined) {
    }

    /**
     * One building, standing or queued, as a shape on the plan.
     *
     * <p>The {@code notch} travels because a building with a corner cut out of
     * it is the one shape a bounding box lies about, and the orcs' round huts
     * are all notch. {@code facing} is what puts the door tick on the right
     * side.
     *
     * <p>{@code blueprintId} rather than a role byte: {@link BuildingRole} and
     * {@code Beds} both live in the simulation, which is on the client too, so
     * the client can classify a building for itself and there is exactly one
     * table that says what a storehouse is.
     */
    public record Plot(int dx, int dz, int width, int depth, BuildingSizes.Notch notch,
                       int facing, String blueprintId, Condition condition,
                       boolean finished, List<Line> stores) {

        public Plot {
            dx = Vertex.clamp(dx);
            dz = Vertex.clamp(dz);
            width = Math.max(1, Math.min(MAX_SPAN, width));
            depth = Math.max(1, Math.min(MAX_SPAN, depth));
            notch = normalize(notch, width, depth);
            facing = Math.floorMod(facing, 4);
            blueprintId = clip(blueprintId, MAX_ID);
            stores = stores.size() <= MAX_STORE_LINES
                    ? List.copyOf(stores) : List.copyOf(stores.subList(0, MAX_STORE_LINES));
        }

        /**
         * A bite no bigger than the loaf.
         *
         * <p>A notch wider than the building it is cut from would draw a hole
         * where the building is, and the sizes it is read off are a catalog a
         * datapack can write.
         */
        private static BuildingSizes.Notch normalize(BuildingSizes.Notch notch,
                                                     int width, int depth) {
            if (notch == null || !notch.isCut()) {
                return BuildingSizes.Notch.NONE;
            }
            return new BuildingSizes.Notch(
                    Math.min(notch.width(), width), Math.min(notch.depth(), depth),
                    notch.towardX() > 0 ? 1 : -1, notch.towardZ() > 0 ? 1 : -1);
        }
    }

    /**
     * One settler, where they are and what they are doing.
     *
     * <p>{@code home} and {@code work} are indices into {@link #plots}, or -1.
     * Indices rather than positions because the screen's two questions run both
     * ways — who lives in this building, and where does this person live — and
     * an index answers both without either side matching coordinates.
     *
     * <p>{@code embodied} is whether there is a villager standing there. An
     * unembodied settler is at the position the simulation keeps for them,
     * which is the truth about somebody nobody is watching.
     */
    public record Dot(int dx, int dz, String name, String profession, int hunger,
                      boolean embodied, int home, int work, String doing) {

        public Dot {
            dx = Vertex.clamp(dx);
            dz = Vertex.clamp(dz);
            name = clip(name, MAX_WORD);
            profession = clip(profession, MAX_WORD);
            hunger = Math.max(0, Math.min(Person.HUNGER_MAX, hunger));
            doing = clip(doing, MAX_NOTE);
        }
    }

    /** One tree standing in the forester's stand. */
    public record Tree(int dx, int dz) {

        public Tree {
            dx = Vertex.clamp(dx);
            dz = Vertex.clamp(dz);
        }
    }

    /**
     * One order on the build queue, and why it is not finished.
     *
     * <p>{@code waiting} is the build's own account of what it is short of —
     * the same sentence {@code /civ info} prints — and blank when nothing is
     * wrong but the work.
     */
    public record Order(String blueprintId, int dx, int dz, int done, int required,
                        String waiting, boolean repair, boolean upgrade) {

        public Order {
            blueprintId = clip(blueprintId, MAX_ID);
            dx = Vertex.clamp(dx);
            dz = Vertex.clamp(dz);
            done = Math.max(0, done);
            required = Math.max(1, required);
            waiting = clip(waiting, MAX_NOTE);
        }

        /** Nought to one. Clamped, because a build can be granted more than it needs. */
        public double fraction() {
            return Math.min(1.0, (double) done / required);
        }
    }

    /** One line of the town's history. */
    public record Note(long step, String message) {

        public Note {
            message = clip(message, MAX_NOTE);
        }
    }

    // --- the overview, which is what /civ info prints ---

    /** The people: how many, how housed, how fed, how equipped. */
    public record Folk(int population, int housing, int embodied, int families,
                       int worstHunger, int starving, int hungry, int equipped) {
    }

    /**
     * The larder, in the four places food actually sits, and the grain besides.
     *
     * <p>Grain gets its own fields because nobody can eat it: a town starving
     * beside full sacks is a town with no oven, and that has to be readable
     * rather than mysterious.
     */
    public record Larder(int granary, int granaryCapacity, int fields, int market,
                         int pantries, int farmGrain, int bakeryGrain, boolean hasBakery) {
    }

    /**
     * What the trades are standing on: the trees left, the rock left, the coin.
     *
     * <p>{@code counted} is not the same as {@code zero}. A camp whose trees
     * nobody has counted and a camp that has felled its last one are different
     * towns, and the report must not say the same thing about both.
     */
    public record Ledgers(int trees, int growing, boolean standCounted, boolean standBare,
                          int seam, boolean seamCounted, boolean seamExhausted, int treasury) {
    }

    /** The watch, against what it is watching for. */
    public record Watch(int threat, int defense, int guards, int neededGuards, String king,
                        int roadRuns, int roadLength, int roadsJoined) {

        public Watch {
            king = clip(king, MAX_WORD);
        }
    }

    /** Everything the town knows about itself that is not a shape. */
    public record Overview(String stage, String cultureId, long steps,
                           TownOverviewPayload.Distress distress, Folk folk, Larder larder,
                           Ledgers ledgers, Watch watch, List<Line> stores, List<Job> jobs) {

        public Overview {
            stage = clip(stage, MAX_WORD);
            cultureId = clip(cultureId, MAX_ID);
            stores = capped(stores, MAX_STORE_LINES);
            jobs = capped(jobs, MAX_JOBS);
        }
    }

    // --- the wire ---

    public static final Type<TownMapPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CivilizationMod.MOD_ID, "town_map"));

    /** Generous for a town name, short enough not to be a payload attack. */
    private static final int MAX_NAME = 96;
    private static final int MAX_ID = 96;
    private static final int MAX_WORD = 32;
    private static final int MAX_NOTE = 96;

    /**
     * How many buildings are drawn.
     *
     * <p>Twice {@link SurveyPayload#MAX_PLOTS}, because the lamp draws what is
     * within 128 blocks of somebody standing in a field and this draws a whole
     * town. A city of two hundred buildings is past anything the simulation
     * raises today; the cap is here so the packet's size is this file's
     * decision rather than the town's.
     */
    public static final int MAX_PLOTS = 192;

    /** How many settlers are drawn, nearest the center first. */
    public static final int MAX_FOLK = 160;

    /** How many of the forester's trees are drawn. */
    public static final int MAX_TREES = 128;

    /** How many orders of the build queue travel. */
    public static final int MAX_ORDERS = 32;

    /** How much history travels: the last fifty lines, which is a session's worth. */
    public static final int MAX_EVENTS = 50;

    /** How many rows any one ledger may have. */
    public static final int MAX_STORE_LINES = 24;

    /** How many trades may be counted. One per {@link Profession}, and no more. */
    public static final int MAX_JOBS = 32;

    /** The widest building the wire will carry. */
    public static final int MAX_SPAN = 256;

    /**
     * How many points the streets and the roads may spend between them.
     *
     * <p>Larger than the lamp's budget because nothing is sampled here: a flat
     * map draws straight between two corners, so a point is a corner rather
     * than every second block of one. A street plan is tens of lines and the
     * road network hundreds of short stretches, which is what this is sized for.
     */
    public static final int MAX_PATH_VERTICES = 1600;

    /**
     * And how many the wall may spend, kept separate on purpose.
     *
     * <p>A shared budget would be spent by the roads before the ring was
     * reached, so a town with a lot of road would draw no wall — and the ring is
     * the one line that says where the town stops.
     */
    public static final int MAX_WALL_VERTICES = 400;

    /** How many runs may travel, so a pathological plan cannot make a list of one-liners. */
    private static final int MAX_RUNS = 512;

    private static final StreamCodec<io.netty.buffer.ByteBuf, Integer> SHORT_INT =
            ByteBufCodecs.SHORT.map(Short::intValue, Integer::shortValue);

    private static final StreamCodec<RegistryFriendlyByteBuf, Vertex> VERTEX_CODEC =
            StreamCodec.composite(
                    SHORT_INT, Vertex::dx,
                    SHORT_INT, Vertex::dz,
                    Vertex::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Run> RUN_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Run::kind,
                    VERTEX_CODEC.apply(ByteBufCodecs.list(MAX_PATH_VERTICES)), Run::path,
                    Run::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Line> LINE_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_WORD), Line::resource,
                    ByteBufCodecs.VAR_INT, Line::amount,
                    Line::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Job> JOB_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_WORD), Job::profession,
                    ByteBufCodecs.VAR_INT, Job::count,
                    Job::new);

    /**
     * The bite, as two spans and two quarters.
     *
     * <p>{@code towardX} and {@code towardZ} are 1 or -1 and nothing else, so
     * they travel as a bit each rather than as a varint that spends five bytes
     * saying minus one.
     */
    private static final StreamCodec<RegistryFriendlyByteBuf, BuildingSizes.Notch> NOTCH_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BuildingSizes.Notch::width,
                    ByteBufCodecs.VAR_INT, BuildingSizes.Notch::depth,
                    ByteBufCodecs.BOOL, notch -> notch.towardX() > 0,
                    ByteBufCodecs.BOOL, notch -> notch.towardZ() > 0,
                    (width, depth, plusX, plusZ) -> new BuildingSizes.Notch(
                            width, depth, plusX ? 1 : -1, plusZ ? 1 : -1));

    private static final StreamCodec<RegistryFriendlyByteBuf, Condition> CONDITION_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Condition::level,
                    ByteBufCodecs.VAR_INT, Condition::damage,
                    ByteBufCodecs.BOOL, Condition::defends,
                    ByteBufCodecs.BOOL, Condition::joined,
                    Condition::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Plot> PLOT_CODEC =
            StreamCodec.composite(
                    SHORT_INT, Plot::dx,
                    SHORT_INT, Plot::dz,
                    ByteBufCodecs.VAR_INT, Plot::width,
                    ByteBufCodecs.VAR_INT, Plot::depth,
                    NOTCH_CODEC, Plot::notch,
                    ByteBufCodecs.VAR_INT, Plot::facing,
                    ByteBufCodecs.stringUtf8(MAX_ID), Plot::blueprintId,
                    CONDITION_CODEC, Plot::condition,
                    ByteBufCodecs.BOOL, Plot::finished,
                    LINE_CODEC.apply(ByteBufCodecs.list(MAX_STORE_LINES)), Plot::stores,
                    Plot::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Dot> DOT_CODEC =
            StreamCodec.composite(
                    SHORT_INT, Dot::dx,
                    SHORT_INT, Dot::dz,
                    ByteBufCodecs.stringUtf8(MAX_WORD), Dot::name,
                    ByteBufCodecs.stringUtf8(MAX_WORD), Dot::profession,
                    ByteBufCodecs.VAR_INT, Dot::hunger,
                    ByteBufCodecs.BOOL, Dot::embodied,
                    ByteBufCodecs.VAR_INT, Dot::home,
                    ByteBufCodecs.VAR_INT, Dot::work,
                    ByteBufCodecs.stringUtf8(MAX_NOTE), Dot::doing,
                    Dot::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Tree> TREE_CODEC =
            StreamCodec.composite(
                    SHORT_INT, Tree::dx,
                    SHORT_INT, Tree::dz,
                    Tree::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Order> ORDER_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_ID), Order::blueprintId,
                    SHORT_INT, Order::dx,
                    SHORT_INT, Order::dz,
                    ByteBufCodecs.VAR_INT, Order::done,
                    ByteBufCodecs.VAR_INT, Order::required,
                    ByteBufCodecs.stringUtf8(MAX_NOTE), Order::waiting,
                    ByteBufCodecs.BOOL, Order::repair,
                    ByteBufCodecs.BOOL, Order::upgrade,
                    Order::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Note> NOTE_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, Note::step,
                    ByteBufCodecs.stringUtf8(MAX_NOTE), Note::message,
                    Note::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Folk> FOLK_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Folk::population,
                    ByteBufCodecs.VAR_INT, Folk::housing,
                    ByteBufCodecs.VAR_INT, Folk::embodied,
                    ByteBufCodecs.VAR_INT, Folk::families,
                    ByteBufCodecs.VAR_INT, Folk::worstHunger,
                    ByteBufCodecs.VAR_INT, Folk::starving,
                    ByteBufCodecs.VAR_INT, Folk::hungry,
                    ByteBufCodecs.VAR_INT, Folk::equipped,
                    Folk::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Larder> LARDER_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Larder::granary,
                    ByteBufCodecs.VAR_INT, Larder::granaryCapacity,
                    ByteBufCodecs.VAR_INT, Larder::fields,
                    ByteBufCodecs.VAR_INT, Larder::market,
                    ByteBufCodecs.VAR_INT, Larder::pantries,
                    ByteBufCodecs.VAR_INT, Larder::farmGrain,
                    ByteBufCodecs.VAR_INT, Larder::bakeryGrain,
                    ByteBufCodecs.BOOL, Larder::hasBakery,
                    Larder::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Ledgers> LEDGERS_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Ledgers::trees,
                    ByteBufCodecs.VAR_INT, Ledgers::growing,
                    ByteBufCodecs.BOOL, Ledgers::standCounted,
                    ByteBufCodecs.BOOL, Ledgers::standBare,
                    ByteBufCodecs.VAR_INT, Ledgers::seam,
                    ByteBufCodecs.BOOL, Ledgers::seamCounted,
                    ByteBufCodecs.BOOL, Ledgers::seamExhausted,
                    ByteBufCodecs.VAR_INT, Ledgers::treasury,
                    Ledgers::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Watch> WATCH_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Watch::threat,
                    ByteBufCodecs.VAR_INT, Watch::defense,
                    ByteBufCodecs.VAR_INT, Watch::guards,
                    ByteBufCodecs.VAR_INT, Watch::neededGuards,
                    ByteBufCodecs.stringUtf8(MAX_WORD), Watch::king,
                    ByteBufCodecs.VAR_INT, Watch::roadRuns,
                    ByteBufCodecs.VAR_INT, Watch::roadLength,
                    ByteBufCodecs.VAR_INT, Watch::roadsJoined,
                    Watch::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Overview> OVERVIEW_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_WORD), Overview::stage,
                    ByteBufCodecs.stringUtf8(MAX_ID), Overview::cultureId,
                    ByteBufCodecs.VAR_LONG, Overview::steps,
                    TownOverviewPayload.DISTRESS_CODEC, Overview::distress,
                    FOLK_CODEC, Overview::folk,
                    LARDER_CODEC, Overview::larder,
                    LEDGERS_CODEC, Overview::ledgers,
                    WATCH_CODEC, Overview::watch,
                    LINE_CODEC.apply(ByteBufCodecs.list(MAX_STORE_LINES)), Overview::stores,
                    JOB_CODEC.apply(ByteBufCodecs.list(MAX_JOBS)), Overview::jobs,
                    Overview::new);

    // Order here is load-bearing: the terminal ::new is the canonical record
    // constructor, so each getter must sit where its component does.
    public static final StreamCodec<RegistryFriendlyByteBuf, TownMapPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_NAME), TownMapPayload::town,
                    BlockPos.STREAM_CODEC, TownMapPayload::origin,
                    ByteBufCodecs.VAR_INT, TownMapPayload::claimRadius,
                    ByteBufCodecs.BOOL, TownMapPayload::opening,
                    OVERVIEW_CODEC, TownMapPayload::overview,
                    RUN_CODEC.apply(ByteBufCodecs.list(MAX_RUNS)), TownMapPayload::runs,
                    PLOT_CODEC.apply(ByteBufCodecs.list(MAX_PLOTS)), TownMapPayload::plots,
                    DOT_CODEC.apply(ByteBufCodecs.list(MAX_FOLK)), TownMapPayload::folk,
                    TREE_CODEC.apply(ByteBufCodecs.list(MAX_TREES)), TownMapPayload::trees,
                    ORDER_CODEC.apply(ByteBufCodecs.list(MAX_ORDERS)), TownMapPayload::queue,
                    NOTE_CODEC.apply(ByteBufCodecs.list(MAX_EVENTS)), TownMapPayload::events,
                    TownMapPayload::new);

    public TownMapPayload {
        town = clip(town, MAX_NAME);
        claimRadius = Math.max(16, claimRadius);
        runs = capped(runs, MAX_RUNS);
        plots = capped(plots, MAX_PLOTS);
        folk = capped(folk, MAX_FOLK);
        trees = capped(trees, MAX_TREES);
        queue = capped(queue, MAX_ORDERS);
        events = capped(events, MAX_EVENTS);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Every point in the plan, which is what the budgets are budgets on. */
    public int vertexCount() {
        int total = 0;
        for (Run run : runs) {
            total += run.path().size();
        }
        return total;
    }

    // --- reading a settlement ---

    /**
     * Reads a whole settlement.
     *
     * <p>Pure: it takes a settlement and nothing else, so it is testable without
     * a world, a level or a player. Everything that would need one — measuring a
     * building nobody has surveyed, asking who has not moved in fifteen seconds
     * — is deliberately not here. Those belong to the surfaces that have a
     * world: the lamp measures, and {@code /civ info} reports the idle watch.
     *
     * @param steps   how many steps the simulation has run, which is the clock
     *                the town's history is written against and the only thing
     *                here a settlement does not know about itself
     * @param opening whether this reading should put a screen up, or only refresh
     *                one already on screen
     */
    public static TownMapPayload of(Settlement settlement, long steps, boolean opening) {
        SimPos center = settlement.center();
        BlockPos origin = new BlockPos(center.x(), center.y(), center.z());

        List<Plot> plots = plots(settlement, origin);
        Map<SimPos, Integer> byOrigin = new HashMap<>();
        List<Building> standing = settlement.buildings();
        for (int i = 0; i < standing.size() && i < plots.size(); i++) {
            byOrigin.putIfAbsent(standing.get(i).origin(), i);
        }

        return new TownMapPayload(settlement.name(), origin, settlement.claimRadius(),
                opening, overview(settlement, steps), runs(settlement, origin), plots,
                folk(settlement, origin, byOrigin), trees(settlement, origin),
                queue(settlement, origin), events(settlement));
    }

    /**
     * The plan's lines: the streets it drew, the roads it opened, and the ring.
     *
     * <p>Nothing is sampled. The lamp walks a line taking the ground's height
     * every two blocks because it draws over terrain; a map looks straight down,
     * so the corners are the whole of the line.
     */
    private static List<Run> runs(Settlement settlement, BlockPos origin) {
        List<Run> runs = new ArrayList<>();

        // The ring first, and that is load-bearing rather than tidy. Two ceilings
        // fall on this list — a budget of points and a ceiling on how many runs
        // it may hold — and the second one is spent by whichever lines are traced
        // first. A town with more stretches of road than the wire carries would
        // otherwise draw no wall at all, which is the one line that says where
        // the town stops.
        Perimeter wall = settlement.perimeter();
        if (wall != null && wall.vertices().size() >= 2) {
            List<SimPos> ring = new ArrayList<>(wall.vertices());
            ring.add(ring.get(0));   // a ring is a line that comes back
            trace(runs, SurveyPayload.WALL, ring, origin, new Budget(MAX_WALL_VERTICES));
        }

        Budget paths = new Budget(MAX_PATH_VERTICES);
        for (TownPlan.Street street : settlement.arrangement()
                .fullPlan(settlement.center()).streets()) {
            trace(runs, SurveyPayload.STREET, street.path(), origin, paths);
        }

        PathNetwork network = settlement.paths();
        List<PathNetwork.Segment> stretches = network.segments();
        for (int i = 0; i < stretches.size(); i++) {
            PathNetwork.Segment stretch = stretches.get(i);
            trace(runs, network.isOpened(i) ? SurveyPayload.ROAD_OPENED
                            : SurveyPayload.ROAD_PLANNED,
                    List.of(stretch.from(), stretch.to()), origin, paths);
        }
        return runs;
    }

    private static void trace(List<Run> runs, int kind, List<SimPos> path, BlockPos origin,
                              Budget budget) {
        if (path.size() < 2 || budget.spent() || runs.size() >= MAX_RUNS) {
            return;
        }
        List<Vertex> points = new ArrayList<>(path.size());
        for (SimPos at : path) {
            if (budget.spent()) {
                break;
            }
            points.add(new Vertex(at.x() - origin.getX(), at.z() - origin.getZ()));
            budget.spend();
        }
        if (points.size() >= 2) {
            runs.add(new Run(kind, points));
        } else {
            budget.refund(points.size());   // a lone point draws nothing; do not charge for it
        }
    }

    /**
     * The buildings, standing first and then queued.
     *
     * <p>Standing first and in the settlement's own order, because the indices
     * {@link Dot} carries are indices into this list and a settler's home has to
     * be findable. A building whose size was never recorded is drawn at the span
     * its blueprint declares rather than left out: the lamp leaves one out
     * because it is drawing the exact ground a plot has taken, and this is
     * drawing a town — a building missing from the town's own map reads as a
     * building that is not there.
     */
    private static List<Plot> plots(Settlement settlement, BlockPos origin) {
        List<Plot> plots = new ArrayList<>();
        for (Building building : settlement.buildings()) {
            if (plots.size() >= MAX_PLOTS) {
                break;
            }
            Footprint footprint = building.footprint();
            int span = BuildPlanner.plotSpanOf(building.blueprintId(), settlement.catalog());
            int width = footprint.isKnown() ? footprint.width() : span;
            int depth = footprint.isKnown() ? footprint.depth() : span;
            plots.add(new Plot(building.origin().x() - origin.getX(),
                    building.origin().z() - origin.getZ(), width, depth,
                    footprint.notch(), building.facing(), building.blueprintId(),
                    new Condition(building.level(), building.damage(),
                            RaidPlanner.defenseBonusOf(settlement, building.blueprintId()) > 0,
                            settlement.paths().hasJoined(building.origin())),
                    true, storesOf(building)));
        }
        for (BuildTask task : settlement.buildQueue()) {
            if (plots.size() >= MAX_PLOTS) {
                break;
            }
            if (!BuildPlanner.holdsGround(task.blueprintId())) {
                continue;   // a repair flight is an errand, not a plot worth drawing
            }
            Footprint footprint = task.footprint();
            int span = BuildPlanner.plotSpanOf(task.blueprintId(), settlement.catalog());
            int width = footprint.isKnown() ? footprint.width() : span;
            int depth = footprint.isKnown() ? footprint.depth() : span;
            plots.add(new Plot(task.origin().x() - origin.getX(),
                    task.origin().z() - origin.getZ(), width, depth,
                    footprint.notch(), task.facing(), task.blueprintId(),
                    new Condition(0, 0, false, false), false, List.of()));
        }
        return plots;
    }

    private static List<Line> storesOf(Building building) {
        if (!building.hasStores()) {
            return List.of();
        }
        List<Line> lines = new ArrayList<>();
        building.stores().all().forEach((resource, amount) -> {
            if (lines.size() < MAX_STORE_LINES) {
                lines.add(new Line(resource, amount));
            }
        });
        return lines;
    }

    /**
     * The settlers, and enough about each to answer a tooltip.
     *
     * <p>Everybody, in the settlement's own order, up to the cap. Not sorted by
     * anything: the screen sorts, and a list whose order moved every second
     * would make the People tab jump under the cursor.
     */
    private static List<Dot> folk(Settlement settlement, BlockPos origin,
                                  Map<SimPos, Integer> byOrigin) {
        List<Dot> folk = new ArrayList<>();
        for (Person person : settlement.residents()) {
            if (folk.size() >= MAX_FOLK) {
                break;
            }
            SimPos at = person.position();
            folk.add(new Dot(at.x() - origin.getX(), at.z() - origin.getZ(),
                    person.name(), person.profession().name().toLowerCase(Locale.ROOT),
                    person.hunger(), person.isEmbodied(),
                    indexOf(byOrigin, homeOf(settlement, person)),
                    indexOf(byOrigin, workplaceOf(settlement, person)),
                    doing(person)));
        }
        return folk;
    }

    private static int indexOf(Map<SimPos, Integer> byOrigin, SimPos at) {
        Integer found = at == null ? null : byOrigin.get(at);
        return found == null ? -1 : found;
    }

    /** The roof this settler sleeps under, by way of the family they belong to. */
    private static SimPos homeOf(Settlement settlement, Person person) {
        for (Household household : settlement.households()) {
            if (household.contains(person.id())) {
                return household.home();
            }
        }
        return null;
    }

    /**
     * Where this settler's trade is plied, or null for one with no fixed place.
     *
     * <p>Derived rather than recorded, because the simulation does not record
     * it: a farmer works whichever field wants working. The nearest building of
     * the trade's own kind is the honest answer and the useful one — it is where
     * they will be when they are working.
     */
    private static SimPos workplaceOf(Settlement settlement, Person person) {
        if (person.profession() == Profession.KING) {
            Building seat = KingPlanner.seat(settlement);
            return seat == null ? null : seat.origin();
        }
        BuildingRole role = tradeOf(person.profession());
        if (role == null) {
            return null;
        }
        Building nearest = null;
        long best = Long.MAX_VALUE;
        for (Building building : settlement.buildingsWithRole(role)) {
            long distance = building.origin().horizontalDistanceSq(person.position());
            if (distance < best) {
                best = distance;
                nearest = building;
            }
        }
        return nearest == null ? null : nearest.origin();
    }

    private static BuildingRole tradeOf(Profession profession) {
        return switch (profession) {
            case FARMER -> BuildingRole.CROP_FARM;
            case SHEPHERD -> BuildingRole.ANIMAL_FARM;
            case LUMBERJACK -> BuildingRole.LUMBER_CAMP;
            case MINER -> BuildingRole.MINE;
            case SMITH -> BuildingRole.SMITH;
            case MILLER -> BuildingRole.MILL;
            case CARPENTER -> BuildingRole.CARPENTRY;
            case TRADER -> BuildingRole.MARKET;
            default -> null;
        };
    }

    /**
     * What this settler is up to, in one line.
     *
     * <p>The errand first, because an errand is a fact and everything else is an
     * inference. A settler with no errand gets their appetite, which is the next
     * most useful thing to know about somebody standing still.
     */
    private static String doing(Person person) {
        HaulTask haul = person.haul();
        if (haul != null) {
            if (haul.isMeal()) {
                return "fetching a meal from " + place(haul.fromPos());
            }
            return (haul.isLoaded() ? "carrying " : "off to fetch ")
                    + haul.carried() + " " + haul.resource() + " to " + place(haul.target());
        }
        if (person.carriedLoad() > 0) {
            return "carrying " + person.carriedLoad() + " " + person.carriedMaterial();
        }
        if (person.isTooWeakToWork()) {
            return "too weak to work";
        }
        return Appetite.of(person.hunger()).word();
    }

    private static String place(SimPos at) {
        return at == null ? "nowhere" : at.x() + "," + at.z();
    }

    /** The forester's stand, as the trees it is made of. */
    private static List<Tree> trees(Settlement settlement, BlockPos origin) {
        List<Tree> trees = new ArrayList<>();
        for (SimPos at : ForesterStand.stand(settlement)) {
            if (trees.size() >= MAX_TREES) {
                break;
            }
            trees.add(new Tree(at.x() - origin.getX(), at.z() - origin.getZ()));
        }
        return trees;
    }

    private static List<Order> queue(Settlement settlement, BlockPos origin) {
        List<Order> queue = new ArrayList<>();
        for (BuildTask task : settlement.buildQueue()) {
            if (queue.size() >= MAX_ORDERS) {
                break;
            }
            String waiting = task.waitingOnHands();
            queue.add(new Order(task.blueprintId(),
                    task.origin().x() - origin.getX(), task.origin().z() - origin.getZ(),
                    task.progress(), Math.max(1, task.requiredWork()),
                    waiting == null ? "" : waiting, task.isRepair(), task.isUpgrade()));
        }
        return queue;
    }

    /** The last fifty lines of history, oldest of those first. */
    private static List<Note> events(Settlement settlement) {
        List<SettlementEvent> all = settlement.events();
        List<Note> notes = new ArrayList<>();
        for (int i = Math.max(0, all.size() - MAX_EVENTS); i < all.size(); i++) {
            notes.add(new Note(all.get(i).step(), all.get(i).message()));
        }
        return notes;
    }

    private static Overview overview(Settlement settlement, long steps) {
        List<Line> stores = new ArrayList<>();
        settlement.stores().all().forEach((resource, amount) -> {
            if (stores.size() < MAX_STORE_LINES) {
                stores.add(new Line(resource, amount));
            }
        });

        List<Job> jobs = new ArrayList<>();
        for (Profession profession : Profession.values()) {
            int count = JobPlanner.count(settlement, profession);
            if (count > 0) {
                jobs.add(new Job(profession.name().toLowerCase(Locale.ROOT), count));
            }
        }

        return new Overview(settlement.stage().pretty(), settlement.cultureId(),
                Math.max(0L, steps), TownOverviewPayload.Distress.of(settlement),
                folkOf(settlement), larderOf(settlement), ledgersOf(settlement),
                watchOf(settlement), stores, jobs);
    }

    private static Folk folkOf(Settlement settlement) {
        int worst = 0;
        int starving = 0;
        int hungry = 0;
        int equipped = 0;
        int embodied = 0;
        for (Person person : settlement.residents()) {
            worst = Math.max(worst, person.hunger());
            if (person.hunger() >= Person.HUNGER_SEVERE) {
                starving++;
            }
            if (person.hunger() >= Person.HUNGER_HUNGRY) {
                hungry++;
            }
            if (person.hasTool()) {
                equipped++;
            }
            if (person.isEmbodied()) {
                embodied++;
            }
        }
        return new Folk(settlement.population(),
                PopulationPlanner.totalHousingCapacity(settlement), embodied,
                settlement.households().size(), worst, starving, hungry, equipped);
    }

    private static Larder larderOf(Settlement settlement) {
        return new Larder(settlement.foodStock(), FoodPlanner.granaryCapacity(settlement),
                FoodPlanner.farmStock(settlement), FoodPlanner.marketStock(settlement),
                FoodPlanner.pantryTotal(settlement), FoodPlanner.farmGrain(settlement),
                FoodPlanner.bakeryGrain(settlement), FoodPlanner.bakery(settlement) != null);
    }

    /**
     * The woods and the rock, summed over every camp and every mine.
     *
     * <p>A town may have more than one of each. {@code /civ info} prints a line
     * per camp because it is a report; a panel has one line, so the counts add
     * and "counted" means anybody has counted anything.
     */
    private static Ledgers ledgersOf(Settlement settlement) {
        int trees = 0;
        int growing = 0;
        boolean standCounted = false;
        boolean standBare = false;
        for (Building camp : settlement.buildingsWithRole(BuildingRole.LUMBER_CAMP)) {
            if (!Stand.isCounted(camp)) {
                continue;
            }
            standCounted = true;
            trees += Stand.trees(camp);
            growing += Stand.growing(camp);
            standBare |= Stand.isBare(camp);
        }
        int seam = 0;
        boolean seamCounted = false;
        boolean exhausted = false;
        for (Building mine : settlement.buildingsWithRole(BuildingRole.MINE)) {
            if (!Seam.isCounted(mine)) {
                continue;
            }
            seamCounted = true;
            seam += Seam.remaining(mine);
            exhausted |= Seam.isExhausted(mine);
        }
        return new Ledgers(trees, growing, standCounted, standBare,
                seam, seamCounted, exhausted, settlement.treasury());
    }

    private static Watch watchOf(Settlement settlement) {
        Person crowned = KingPlanner.crownsAKing(settlement)
                ? KingPlanner.king(settlement) : null;
        PathNetwork paths = settlement.paths();
        return new Watch(settlement.threatLevel(), RaidPlanner.defensePower(settlement),
                Garrison.guardStrength(settlement), Garrison.neededGuards(settlement),
                crowned == null ? "" : crowned.name(),
                paths.segments().size(), paths.totalLength(), paths.joined().size());
    }

    // --- odds and ends ---

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static <T> List<T> capped(List<T> list, int max) {
        return list.size() <= max ? List.copyOf(list) : List.copyOf(list.subList(0, max));
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
     * Hands the reading to the client's screen.
     *
     * <p>Named through {@link CivilizationScreens} the way every screen here is, so
     * nothing on the server side ever mentions a class that only exists on a
     * client.
     */
    public static void handle(TownMapPayload payload, IPayloadContext context) {
        CivilizationScreens.openTownMap(payload);
    }
}
