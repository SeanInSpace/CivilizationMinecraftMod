package com.kingdoms.neoforge;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.Layout;
import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.settlement.PopulationPlanner;
import com.kingdoms.sim.world.SimSettings;
import com.kingdoms.sim.world.SimWorld;
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
     * <p>Green and nothing else, on purpose and for now: a village round an open
     * middle is the most ordinary settlement shape there is, it is the one that
     * looks least like a mod announcing itself, and having exactly one makes it
     * obvious whether worldgen is working at all. Widening this is a table edit,
     * not a code change.
     */
    private static final Map<String, Integer> STARTING_WEIGHTS =
            Map.of(Culture.LAYOUT_GREEN, 100);

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
                MAX_SETTLEMENT_POPULATION.get());
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
