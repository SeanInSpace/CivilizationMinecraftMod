package com.kingdoms.neoforge;

import com.kingdoms.sim.settlement.PopulationPlanner;
import com.kingdoms.sim.world.SimSettings;
import com.kingdoms.sim.world.SimWorld;
import net.neoforged.neoforge.common.ModConfigSpec;

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
            .comment("Births stop at this population. Housing scales with population,",
                    "so without a ceiling towns grow exponentially forever.")
            .defineInRange("population.max_per_settlement",
                    SimSettings.DEFAULT_MAX_SETTLEMENT_POPULATION, 4, 4096);

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
            .comment("Whether the /kingdoms debug commands are available to operators.")
            .define("debug.commands_enabled", true);

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

    /** Fail-open before the config loads so dev-time command registration never breaks. */
    public static boolean debugCommandsEnabled() {
        return !SPEC.isLoaded() || DEBUG_COMMANDS.get();
    }
}
