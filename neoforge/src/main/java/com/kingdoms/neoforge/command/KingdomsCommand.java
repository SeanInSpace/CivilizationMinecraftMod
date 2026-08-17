package com.kingdoms.neoforge.command;

import com.kingdoms.neoforge.KingdomsConfig;
import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.JobPlanner;
import com.kingdoms.sim.settlement.PopulationPlanner;
import com.kingdoms.sim.settlement.RaidPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementEvent;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimWorld;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Debug commands. This exists purely so the simulation is observable.
 *
 * <p>Until there is an entity view layer and blueprint placement, nothing about
 * this mod is visible in a running world — settlements grow and build entirely in
 * data. These commands are the window into that, and the fastest way to check that
 * a change to the simulation did what you intended.
 *
 * <p>Expect to delete or gate this before shipping.
 */
public final class KingdomsCommand {

    private KingdomsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kingdoms")
                .requires(source -> KingdomsConfig.debugCommandsEnabled()
                        && Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source))

                .then(Commands.literal("found")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> found(ctx, StringArgumentType.getString(ctx, "name")))))

                .then(Commands.literal("info")
                        .executes(KingdomsCommand::info))

                .then(Commands.literal("populate")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                .then(Commands.argument("profession", StringArgumentType.word())
                                        .executes(ctx -> populate(ctx,
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                StringArgumentType.getString(ctx, "profession"))))))

                .then(Commands.literal("build")
                        .then(Commands.argument("blueprint", StringArgumentType.word())
                                .then(Commands.argument("work", IntegerArgumentType.integer(1, 100000))
                                        .executes(ctx -> build(ctx,
                                                StringArgumentType.getString(ctx, "blueprint"),
                                                IntegerArgumentType.getInteger(ctx, "work"))))))

                .then(Commands.literal("threat")
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 1000))
                                .executes(ctx -> threat(ctx, IntegerArgumentType.getInteger(ctx, "level")))))

                .then(Commands.literal("step")
                        .executes(ctx -> step(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 10000))
                                .executes(ctx -> step(ctx, IntegerArgumentType.getInteger(ctx, "count")))))

                .then(Commands.literal("raid")
                        .executes(ctx -> raid(ctx, 0))
                        .then(Commands.argument("strength", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> raid(ctx, IntegerArgumentType.getInteger(ctx, "strength")))))
        );
    }

    // --- subcommands ---

    private static int found(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        SimWorld world = KingdomsMod.simulationFor(level);
        if (world == null) {
            source.sendFailure(Component.literal("No simulation for this dimension."));
            return 0;
        }

        SimPos centre = toSimPos(source.getPosition());
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), name, "kingdoms:norman");
        Settlement settlement = new Settlement(Settlement.Id.random(), name + " Town", centre, 64);
        kingdom.addSettlement(settlement);

        // Both, deliberately: the saved data is what persists, the sim world is what ticks.
        KingdomsSavedData.get(level).addKingdom(kingdom);
        world.addKingdom(kingdom);

        source.sendSuccess(() -> Component.literal(
                "Founded " + name + " at " + centre + " (claim radius 64)"), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        SimWorld world = KingdomsMod.simulationFor(source.getLevel());
        if (world == null) {
            source.sendFailure(Component.literal("No simulation for this dimension."));
            return 0;
        }

        if (world.kingdoms().isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "No kingdoms yet. Try: /kingdoms found Normandy"), false);
            return 0;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Kingdoms (").append(world.stepsElapsed()).append(" steps elapsed) ===");
        for (Kingdom kingdom : world.kingdoms()) {
            sb.append("\n").append(kingdom.name())
                    .append(" [").append(kingdom.cultureId()).append("] pop ")
                    .append(kingdom.totalPopulation());
            for (Settlement s : kingdom.settlements()) {
                sb.append("\n  - ").append(s.name())
                        .append(": pop ").append(s.population())
                        .append("/").append(PopulationPlanner.totalHousingCapacity(s)).append(" housed")
                        .append(", threat ").append(s.threatLevel())
                        .append(", centre ").append(s.centre());
                sb.append("\n      jobs: ");
                for (Profession p : Profession.values()) {
                    int n = JobPlanner.count(s, p);
                    if (n > 0) {
                        sb.append(p.name().toLowerCase(Locale.ROOT)).append(" x").append(n).append("  ");
                    }
                }
                long embodied = s.residents().stream().filter(Person::isEmbodied).count();
                sb.append("(").append(embodied).append(" visible as villagers)");
                sb.append("\n      defense ").append(RaidPlanner.defensePower(s))
                        .append(" (guards x").append(RaidPlanner.GUARD_POWER).append(" + structures)");
                List<SettlementEvent> events = s.events();
                if (!events.isEmpty()) {
                    sb.append("\n      history:");
                    for (int e = Math.max(0, events.size() - 5); e < events.size(); e++) {
                        sb.append("\n        ").append(events.get(e));
                    }
                }
                if (!s.households().isEmpty()) {
                    sb.append("\n      families (").append(s.households().size()).append("):");
                    for (Household h : s.households()) {
                        sb.append("\n        the ").append(h.name()).append("s — ")
                                .append(h.size()).append(" member(s), ")
                                .append(h.isHoused()
                                        ? "home " + h.home() + ", growth " + h.growthProgress()
                                          + "/" + PopulationPlanner.STEPS_PER_BIRTH
                                        : "NO HOME (cannot grow)");
                    }
                }
                for (BuildTask task : s.buildQueue()) {
                    sb.append("\n      building ").append(task.blueprintId())
                            .append(" ").append(task.progress()).append("/").append(task.requiredWork())
                            .append(" (").append(Math.round(task.completionFraction() * 100)).append("%)");
                }
                if (s.buildQueue().isEmpty()) {
                    sb.append("\n      build queue empty");
                }
                if (s.buildings().isEmpty()) {
                    sb.append("\n      no buildings yet");
                } else {
                    sb.append("\n      built (").append(s.buildings().size()).append("):");
                    for (Building b : s.buildings()) {
                        sb.append("\n        ").append(b.blueprintId())
                                .append(" @ ").append(b.origin())
                                .append(" step ").append(b.completedOnStep())
                                .append(b.isMaterialized() ? "" : " [PENDING placement]");
                    }
                }
            }
        }

        String report = sb.toString();
        source.sendSuccess(() -> Component.literal(report), false);
        return 1;
    }

    private static int populate(CommandContext<CommandSourceStack> ctx, int count, String professionName) {
        CommandSourceStack source = ctx.getSource();
        Settlement settlement = nearestSettlement(source);
        if (settlement == null) {
            source.sendFailure(Component.literal("No settlement nearby. Use /kingdoms found <name> first."));
            return 0;
        }

        Profession profession;
        try {
            profession = Profession.valueOf(professionName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(
                    "Unknown profession '" + professionName + "'. Try one of: IDLER, FARMER, BUILDER, GUARD, TRADER"));
            return 0;
        }

        for (int i = 0; i < count; i++) {
            settlement.addResident(new Person(
                    Person.Id.random(),
                    profession.name().charAt(0) + profession.name().substring(1).toLowerCase(Locale.ROOT) + " " + (settlement.population() + 1),
                    profession,
                    settlement.centre()));
        }
        markDirty(source);

        final int pop = settlement.population();
        source.sendSuccess(() -> Component.literal(
                "Added " + count + " x " + profession + " to " + settlement.name() + " (pop now " + pop + ")"), true);
        return count;
    }

    private static int build(CommandContext<CommandSourceStack> ctx, String blueprint, int work) {
        CommandSourceStack source = ctx.getSource();
        Settlement settlement = nearestSettlement(source);
        if (settlement == null) {
            source.sendFailure(Component.literal("No settlement nearby. Use /kingdoms found <name> first."));
            return 0;
        }

        SimPos origin = toSimPos(source.getPosition());
        settlement.enqueueBuild(new BuildTask(blueprint, origin, work));
        markDirty(source);

        source.sendSuccess(() -> Component.literal(
                "Queued " + blueprint + " (" + work + " work) at " + settlement.name()
                        + ". Needs BUILDERs — each contributes 1 work per step."), true);
        return 1;
    }

    private static int threat(CommandContext<CommandSourceStack> ctx, int level) {
        CommandSourceStack source = ctx.getSource();
        Settlement settlement = nearestSettlement(source);
        if (settlement == null) {
            source.sendFailure(Component.literal("No settlement nearby."));
            return 0;
        }
        settlement.setThreatLevel(level);
        markDirty(source);

        source.sendSuccess(() -> Component.literal(
                "Threat on " + settlement.name() + " set to " + level + " (decays 1 per step)"), true);
        return level;
    }

    /**
     * Forces simulation steps immediately rather than waiting for the slow tick.
     * A settlement that would take five real minutes to build something can be
     * fast-forwarded here in one command.
     */
    private static int step(CommandContext<CommandSourceStack> ctx, int count) {
        CommandSourceStack source = ctx.getSource();
        SimWorld world = KingdomsMod.simulationFor(source.getLevel());
        if (world == null) {
            source.sendFailure(Component.literal("No simulation for this dimension."));
            return 0;
        }
        for (int i = 0; i < count; i++) {
            world.step();
        }
        markDirty(source);

        source.sendSuccess(() -> Component.literal(
                "Ran " + count + " simulation step(s); " + world.stepsElapsed() + " total"), true);
        return count;
    }

    /**
     * Forces a raid on the nearest settlement, at natural strength or a chosen one.
     * With you standing there it spawns real hostiles; run it on an unwatched town
     * (via console) and it resolves statistically.
     */
    private static int raid(CommandContext<CommandSourceStack> ctx, int strength) {
        CommandSourceStack source = ctx.getSource();
        SimWorld world = KingdomsMod.simulationFor(source.getLevel());
        Settlement settlement = nearestSettlement(source);
        if (world == null || settlement == null) {
            source.sendFailure(Component.literal("No settlement nearby. Use /kingdoms found <name> first."));
            return 0;
        }

        int raidStrength = strength > 0 ? strength : RaidPlanner.raidStrength(settlement, world.stepsElapsed());
        SimContext simCtx = new SimContext(world.bridge(), world.stepsElapsed(), world.settings());
        RaidPlanner.execute(settlement, simCtx, raidStrength);
        markDirty(source);

        String latest = settlement.events().isEmpty()
                ? "(no outcome recorded)"
                : settlement.events().getLast().message();
        source.sendSuccess(() -> Component.literal(
                "Raid of " + raidStrength + " vs defense " + RaidPlanner.defensePower(settlement)
                        + " — " + latest), true);
        return raidStrength;
    }

    // --- helpers ---

    private static void markDirty(CommandSourceStack source) {
        KingdomsSavedData.get(source.getLevel()).setDirty();
    }

    private static SimPos toSimPos(Vec3 pos) {
        return new SimPos((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));
    }

    private static Settlement nearestSettlement(CommandSourceStack source) {
        SimWorld world = KingdomsMod.simulationFor(source.getLevel());
        if (world == null) {
            return null;
        }
        SimPos here = toSimPos(source.getPosition());
        Settlement best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                long d = settlement.centre().horizontalDistanceSq(here);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = settlement;
                }
            }
        }
        return best;
    }
}
