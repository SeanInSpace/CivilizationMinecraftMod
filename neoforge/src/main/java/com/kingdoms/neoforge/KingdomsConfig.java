package com.kingdoms.neoforge;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.Layout;
import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.settlement.PopulationPlanner;
import com.kingdoms.sim.world.SimSettings;
import com.kingdoms.sim.world.SimWorld;
import com.kingdoms.sim.world.YieldPolicy;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server config. Written to {@code serverconfig/kingdoms-server.toml} per world.
 *
 * <p>Values are read once at server start and handed to the simulation as an
 * immutable {@link SimSettings} — the sim itself never sees the config system.
 */
public final class KingdomsConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SIM_INTERVAL_TICKS = BUILDER
            .comment("Game ticks between simulation steps. 100 = one step every 5 seconds.",
                    "Raising this is the cheapest performance lever the mod has.")
            .defineInRange("simulation.interval_ticks", SimWorld.SIM_INTERVAL_TICKS, 1, 24000);

    public static final ModConfigSpec.IntValue STEPS_PER_BIRTH = BUILDER
            .comment("Simulation steps a housed family needs before each child.")
            .defineInRange("population.steps_per_birth", PopulationPlanner.STEPS_PER_BIRTH, 1, 10000);

    public static final ModConfigSpec.IntValue MAX_SETTLEMENT_POPULATION = BUILDER
            .comment("Births stop at this population. Default is no ceiling at all:",
                    "growth is held back by how fast a town can house and feed",
                    "people, and by births costing more the fuller it already is",
                    "(population.steps_per_birth scales with size). Set a real",
                    "number here to put a hard cap back.")
            .defineInRange("population.max_per_settlement",
                    SimSettings.DEFAULT_MAX_SETTLEMENT_POPULATION, 4, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue OBSERVED_RADIUS = BUILDER
            .comment("Distance (blocks) from a player at which simulated people appear as villagers.")
            .defineInRange("view.observed_radius", 96.0, 16.0, 512.0);

    public static final ModConfigSpec.IntValue MAX_VILLAGERS_PER_SETTLEMENT = BUILDER
            .comment("Most view villagers one settlement may show at once. Protects the tick budget in big towns.")
            .defineInRange("view.max_villagers_per_settlement", 64, 0, 1024);

    public static final ModConfigSpec.BooleanValue RAIDS_ENABLED = BUILDER
            .comment("Whether settlements come under periodic attack. The heart of the mod; disable for peaceful building.")
            .define("defense.raids_enabled", true);

    public static final ModConfigSpec.IntValue RAID_INTERVAL_STEPS = BUILDER
            .comment("Simulation steps between raid checks per settlement. 50 steps at the default interval is about every 4 minutes.")
            .defineInRange("defense.raid_interval_steps", SimSettings.DEFAULT_RAID_INTERVAL_STEPS, 10, 100000);

    public static final ModConfigSpec.BooleanValue DEBUG_COMMANDS = BUILDER
            .comment("Whether the /civ debug commands are available to operators.")
            .define("debug.commands_enabled", true);

    public static final ModConfigSpec.BooleanValue WORLDGEN_ENABLED = BUILDER
            .comment("Whether towns appear in a generated world without anybody founding them.",
                    "Sites are decided arithmetically from the world seed, and a town is",
                    "raised when a player first comes near one. Turning this off leaves",
                    "/civ found and /civ seed working as before.")
            .define("worldgen.enabled", true);

    public static final ModConfigSpec.IntValue WORLDGEN_REACH = BUILDER
            .comment("How close (blocks) a player must come before a town is raised.",
                    "Wider means towns appear sooner and further off; narrower means",
                    "fewer chunks are read at once.")
            .defineInRange("worldgen.reach", 256, 64, 2048);

    /**
     * What a fresh world starts with.
     *
     * <p>One arrangement and nothing else, on purpose and for now: having exactly
     * one makes it obvious whether worldgen is working at all, and it puts one
     * people's towns in front of a player long enough to be judged. Widening
     * this is a table edit, not a code change.
     *
     * <p>Crossroads is the burghers' town: two roads meeting at a market square,
     * ribs off the arms. It took the green's place here as the next best
     * measured of the thirteen. Grown seven hundred steps on smooth ground and
     * five hundred on the rough recorded seed, the green stood 1.09 and 1.07
     * buildings to the thousand square blocks with 6 and 0 doors more than
     * eight blocks from an opened road; crossroads stood 0.67 and 0.69 with 3
     * and 3, and 92 and 86 percent of its planned road opened. Every other
     * streets-first shape was sparser, or stranded more doors (bastide 24,
     * stronghold_streets 26, high_street 11 on smooth ground), or sprawled
     * (crescents to 353 blocks). The lattices are denser still but have no
     * roads, which is the scattered-huts problem the comment on the table
     * names.
     */
    private static final Map<String, Integer> STARTING_WEIGHTS =
            Map.of(Culture.LAYOUT_CROSSROADS, 100);

    /**
     * How often each arrangement is drawn.
     *
     * <p>One entry per arrangement, so the table names them all and a world can
     * say it wants nothing but bastides. The numbers are weights against each
     * other rather than percentages -- two arrangements at 50 apiece is the same
     * world as two at 1 apiece -- but they are written as percentages because
     * that is how somebody reaching for this will think about it.
     *
     * <p>A zero is a refusal: an arrangement weighted zero never appears. All
     * zeroes is treated as no table at all, since a world where nothing can be
     * built is not what turning everything off was meant to mean.
     */
    private static final Map<String, ModConfigSpec.IntValue> ARRANGEMENT_WEIGHTS = weights();

    private static Map<String, ModConfigSpec.IntValue> weights() {
        BUILDER.comment(
                "How likely each town arrangement is, weighted against the others.",
                "Zero means never. All zero is treated as no preference at all.",
                "The lattice arrangements (ring, warren, stronghold, organic) have",
                "no roads, so a world of them is a world of scattered huts.")
                .push("worldgen.arrangements");
        Map<String, ModConfigSpec.IntValue> table = new LinkedHashMap<>();
        for (Layout layout : Layouts.all()) {
            table.put(layout.id(), BUILDER.defineInRange(
                    layout.id(), STARTING_WEIGHTS.getOrDefault(layout.id(), 0), 0, 1000));
        }
        BUILDER.pop();
        return table;
    }

    /**
     * How much of the clock's yield an unwatched building is credited.
     *
     * <p>The mod runs at two fidelities. A lumberjack you can see fells an
     * actual tree; the same lumberjack out past the observed radius is a
     * number, and the clock credits the timber for him. This table is how much
     * of that credit a world actually wants. Zero — the default — means a
     * resource is only ever gained by hands a player could have watched, so
     * every town supplies itself. A hundred is the old behavior, everything
     * abstract; seventy is the middle the mod shipped with for two days.
     *
     * <p><strong>There is no food row any more.</strong> Both tables are built
     * from {@link YieldPolicy#SCALED_RESOURCES}, and food has left it: a loaf
     * comes off a field that was sown and ripened, not off a percentage. An
     * older config file that still carries {@code ...food} in either table
     * loads perfectly well — {@code ModConfigSpec.correct} reports the key as
     * {@code CorrectionAction.REMOVE}, drops it and writes the file back, which
     * is the same thing that happens to any setting the mod stops using. It is
     * a line in the log, not an error.
     */
    private static final Map<String, ModConfigSpec.IntValue> UNWATCHED_YIELD = unwatchedYield();

    /**
     * How much of the clock's yield a <em>watched</em> building is credited when
     * the real hands have gone quiet.
     *
     * <p>A camp whose trees are all felled, a mine on flat grass with no shaft
     * sunk: after twelve steps without real work the clock used to step back in
     * at full rate, so that being looked at could never cost a town its income.
     * Default zero, because in front of a player only real work should count —
     * raise it if you would rather a stuck worker cost the town nothing.
     */
    private static final Map<String, ModConfigSpec.IntValue> WATCHED_FLOOR = watchedFloor();

    private static Map<String, ModConfigSpec.IntValue> unwatchedYield() {
        BUILDER.comment(
                "Percent of the simulation's yield credited when NO player is near",
                "the producing building. 0 -- the default -- means the resource is",
                "never conjured: an unwatched town still builds, hauls, eats and",
                "spends what it holds, but gains nothing out of nothing. 70 is what",
                "the mod shipped with before; 100 is fully abstract, which is what",
                "it did before this table existed at all.",
                "Fractions are carried between steps, so 70 really is 70 percent",
                "even for a camp that only cuts one log a step.",
                "Food is not on this table. A town eats what its fields grew.")
                .push("economy.unwatched_yield_percent");
        Map<String, ModConfigSpec.IntValue> table = new LinkedHashMap<>();
        for (String resource : YieldPolicy.SCALED_RESOURCES) {
            table.put(resource, BUILDER.defineInRange(
                    resource, YieldPolicy.DEFAULT_UNWATCHED_PERCENT, 0, 100));
        }
        BUILDER.pop();
        return table;
    }

    private static Map<String, ModConfigSpec.IntValue> watchedFloor() {
        BUILDER.comment(
                "Percent of the simulation's yield credited when a player IS near",
                "but the real workers have not produced anything for a while.",
                "0 -- the default -- means that in front of a player, only real",
                "work counts. 100 restores the old floor, which credited a watched",
                "building in full whenever its hands went quiet.",
                "Food is not on this table either; the fields answer to the crop.")
                .push("economy.watched_floor_percent");
        Map<String, ModConfigSpec.IntValue> table = new LinkedHashMap<>();
        for (String resource : YieldPolicy.SCALED_RESOURCES) {
            table.put(resource, BUILDER.defineInRange(
                    resource, YieldPolicy.DEFAULT_WATCHED_FLOOR_PERCENT, 0, 100));
        }
        BUILDER.pop();
        return table;
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private KingdomsConfig() {
    }

    public static SimSettings settings() {
        return new SimSettings(
                SIM_INTERVAL_TICKS.get(),
                STEPS_PER_BIRTH.get(),
                OBSERVED_RADIUS.get(),
                MAX_VILLAGERS_PER_SETTLEMENT.get(),
                RAID_INTERVAL_STEPS.get(),
                RAIDS_ENABLED.get(),
                MAX_SETTLEMENT_POPULATION.get())
                .withYields(yieldPolicy());
    }

    /**
     * The two abstraction tables, as the simulation wants them.
     *
     * <p>Before the config has loaded this is {@link YieldPolicy#DEFAULTS}
     * rather than an empty policy, because an empty one means "everything
     * abstract" and a world that has not read its file yet should behave like
     * the file it is about to read.
     */
    public static YieldPolicy yieldPolicy() {
        if (!SPEC.isLoaded()) {
            return YieldPolicy.DEFAULTS;
        }
        Map<String, Integer> unwatched = new LinkedHashMap<>();
        Map<String, Integer> floor = new LinkedHashMap<>();
        UNWATCHED_YIELD.forEach((id, value) -> unwatched.put(id, value.get()));
        WATCHED_FLOOR.forEach((id, value) -> floor.put(id, value.get()));
        return new YieldPolicy(unwatched, floor);
    }

    /**
     * The arrangement table, as the site chooser wants it.
     *
     * <p>Empty before the config has loaded, which the chooser reads as "no
     * preference" rather than "nothing may be built" -- the same fail-open the
     * debug commands use, and for the same reason.
     */
    public static Map<String, Integer> arrangementWeights() {
        if (!SPEC.isLoaded()) {
            return Map.of();
        }
        Map<String, Integer> table = new LinkedHashMap<>();
        ARRANGEMENT_WEIGHTS.forEach((id, value) -> table.put(id, value.get()));
        return table;
    }

    /** Fail-open before the config loads so dev-time command registration never breaks. */
    public static boolean debugCommandsEnabled() {
        return !SPEC.isLoaded() || DEBUG_COMMANDS.get();
    }
}
