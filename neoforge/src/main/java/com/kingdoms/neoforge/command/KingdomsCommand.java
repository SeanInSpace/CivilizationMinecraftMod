package com.kingdoms.neoforge.command;

import com.kingdoms.neoforge.KingdomsConfig;
import com.kingdoms.neoforge.KingdomsMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import com.kingdoms.neoforge.net.TownOverviewPayload;
import com.kingdoms.neoforge.view.PersonEntityManager;
import com.kingdoms.neoforge.world.TownAuditor;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.JobPlanner;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.PopulationPlanner;
import com.kingdoms.sim.settlement.RaidPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
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
        dispatcher.register(Commands.literal("civ")
                .executes(KingdomsCommand::help)
                .requires(source -> KingdomsConfig.debugCommandsEnabled()
                        && Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source))

                .then(Commands.literal("found")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> found(ctx, StringArgumentType.getString(ctx, "name")))))

                .then(Commands.literal("info")
                        .executes(KingdomsCommand::info))

                .then(Commands.literal("overview")
                        .executes(KingdomsCommand::overview))

                .then(Commands.literal("audit")
                        .executes(KingdomsCommand::audit))

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

                .then(Commands.literal("hunger")
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 99))
                                .executes(ctx -> hunger(ctx, IntegerArgumentType.getInteger(ctx, "level")))))
        );
    }

    /**
     * Walks every loaded building and reports what is built wrong.
     *
     * <p>The checks are the faults live play keeps finding and logs never show:
     * buildings in water, floors above or below their own ground, walls through
     * other walls, doors nobody can reach, fields stripped of their crops. Each
     * fault is also written to the log under {@code AUDIT}, so a scripted run
     * can grep for regressions without a person standing in the town.
     */
    private static int audit(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        SimWorld world = KingdomsMod.simulationFor(level);
        if (world == null) {
            source.sendFailure(Component.literal("No simulation for this dimension."));
            return 0;
        }

        StringBuilder sb = new StringBuilder("=== Audit ===");
        int total = 0;
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                // No skip for an unloaded town. The geometry checks genuinely need
                // chunks, but hunger and a frozen build queue are pure simulation
                // and need none — and an unwatched town is precisely the one most
                // likely to be starving unattended. The sweep learned this; the
                // command was still turning those towns away at the door.
                int visible = TownAuditor.visibleCount(level, settlement);
                java.util.List<TownAuditor.Fault> faults = TownAuditor.audit(level, settlement);
                if (visible == 0 && faults.isEmpty()) {
                    continue;   // nothing loaded, and nothing the simulation objects to
                }
                sb.append("\n").append(settlement.name()).append(": ")
                        .append(visible).append(" buildings seen, ")
                        .append(faults.size()).append(" fault(s)");
                for (TownAuditor.Fault fault : faults) {
                    sb.append("\n  - ").append(fault.describe());
                    KingdomsMod.LOGGER.info("AUDIT {} {}", settlement.name(), fault.describe());
                }
                total += faults.size();
            }
        }
        if (sb.length() == "=== Audit ===".length()) {
            sb.append("\nNo settlement has a loaded building to look at.");
        }
        String report = sb.toString();
        source.sendSuccess(() -> Component.literal(report), false);
        return total;
    }

    /** Sets every resident's hunger in the nearest settlement — the starvation test lever. */
    private static int hunger(CommandContext<CommandSourceStack> ctx, int value) {
        CommandSourceStack source = ctx.getSource();
        Settlement settlement = nearestSettlement(source);
        if (settlement == null) {
            source.sendFailure(Component.literal("No settlement nearby."));
            return 0;
        }
        settlement.residents().forEach(p -> p.setHunger(value));
        markDirty(source);
        source.sendSuccess(() -> Component.literal(
                "Hunger of all " + settlement.population() + " residents of "
                        + settlement.name() + " set to " + value), true);
        return value;
    }

    // --- subcommands ---

    /** Bare {@code /civ} lists what is available, rather than a bare parse error. */
    private static int help(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("""
                === /civ ===
                  found <name>              found a settlement here
                  info                      full state of every settlement
                  overview                  open the town overview screen
                  populate <n> <job>        BUILDER/FARMER/GUARD/TRADER/LUMBERJACK/MINER/IDLER
                  build <blueprint> <work>  queue construction here
                  step [n]                  fast-forward the simulation
                  raid [strength]           attack the nearest settlement
                  threat <level>            set the alarm level
                  hunger <0-99>             set everyone's hunger
                  audit                     walk the town, report what is built wrong"""), false);
        return 1;
    }

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
        // Fresh foundings live the ladder; only loaded saves default to TOWN.
        settlement.setStage(SettlementStage.CAMP);
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
        PersonEntityManager digger = KingdomsMod.managerFor(source.getLevel());
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
                        .append(" [").append(s.stage().pretty()).append("]")
                        .append(": pop ").append(s.population())
                        .append("/").append(PopulationPlanner.totalHousingCapacity(s)).append(" housed")
                        .append(", threat ").append(s.threatLevel())
                        .append(", centre ").append(s.centre());
                sb.append("\n      roads: ")
                        .append(s.paths().segments().size()).append(" runs, ")
                        .append(s.paths().totalLength()).append(" blocks, ")
                        .append(s.paths().joined().size()).append(" buildings joined");
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
                sb.append("\n      food: granary ").append(s.foodStock())
                        .append("/").append(FoodPlanner.granaryCapacity(s))
                        .append(", fields ").append(FoodPlanner.farmStock(s))
                        .append(", market ").append(FoodPlanner.marketStock(s))
                        .append(", pantries ").append(FoodPlanner.pantryTotal(s));
                int worstHunger = s.residents().stream().mapToInt(Person::hunger).max().orElse(0);
                long starving = s.residents().stream()
                        .filter(p -> p.hunger() >= Person.HUNGER_SEVERE).count();
                sb.append("\n      hunger: worst ").append(worstHunger)
                        .append(starving > 0 ? " (" + starving + " STARVING)" : "");
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
                sb.append("\n      stores:");
                if (s.stores().all().isEmpty()) {
                    sb.append(" empty");
                } else {
                    s.stores().all().forEach((resource, amount) ->
                            sb.append(" ").append(resource).append("=").append(amount));
                }
                // Where those goods actually are. The total above is a sum
                // now, not a figure anybody keeps, so the interesting line
                // is this one: which building is holding what, and how much
                // is still lying in the open waiting for a store to be built.
                sb.append("\n      where:");
                boolean anywhere = false;
                if (!s.loosePile().all().isEmpty()) {
                    anywhere = true;
                    sb.append(" open").append(s.loosePile().all());
                }
                for (Building b : s.buildings()) {
                    if (b.isStore() && b.hasStores()) {
                        anywhere = true;
                        sb.append(" ").append(b.blueprintId())
                                .append("(").append(b.origin().x()).append(",")
                                .append(b.origin().z()).append(")")
                                .append(b.stores().all());
                    }
                }
                if (!anywhere) {
                    sb.append(" nothing anywhere");
                }
                if (!s.tallies().all().isEmpty()) {
                    sb.append("\n      deeds:");
                    s.tallies().all().forEach((stat, count) ->
                            sb.append(" ").append(stat).append("=").append(count));
                }
                sb.append("\n      culture ").append(s.cultureId())
                        .append(", equipped ")
                        .append(s.residents().stream().filter(Person::hasTool).count())
                        .append("/").append(s.population());
                for (BuildTask task : s.buildQueue()) {
                    sb.append("\n      building ").append(task.blueprintId())
                            .append(" ").append(task.progress()).append("/").append(task.requiredWork())
                            .append(" (").append(Math.round(task.completionFraction() * 100)).append("%)");
                }
                if (s.buildQueue().isEmpty()) {
                    sb.append("\n      build queue empty");
                }
                // A town stalled at four per cent is usually not stalled at all;
                // it is thirty blocks into a hillside. Say so, rather than leaving
                // it to be guessed at from a percentage that will not move for a while.
                String digging = digger == null ? null : digger.digStatus(s);
                if (digging != null) {
                    sb.append("\n      digging ").append(digging);
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
    /** Opens the town overview for the nearest settlement, without walking to a hall. */
    private static int overview(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can be shown a screen."));
            return 0;
        }
        SimWorld world = KingdomsMod.simulationFor(source.getLevel());
        if (world == null) {
            source.sendFailure(Component.literal("No simulation for this dimension."));
            return 0;
        }
        Settlement nearest = null;
        long best = Long.MAX_VALUE;
        SimPos here = toSimPos(source.getPosition());
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                long distance = settlement.centre().horizontalDistanceSq(here);
                if (distance < best) {
                    best = distance;
                    nearest = settlement;
                }
            }
        }
        if (nearest == null) {
            source.sendFailure(Component.literal("No settlements exist yet."));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, TownOverviewPayload.of(nearest));
        return 1;
    }

    private static int step(CommandContext<CommandSourceStack> ctx, int count) {
        CommandSourceStack source = ctx.getSource();
        SimWorld world = KingdomsMod.simulationFor(source.getLevel());
        if (world == null) {
            source.sendFailure(Component.literal("No simulation for this dimension."));
            return 0;
        }
        // Stepping passes no game ticks, so the view layer never runs on its own.
        // Pump it after each step or the builders would be granted blocks they
        // never lay, and the finished building would be stamped in whole on top
        // of the half-built one standing at the site.
        PersonEntityManager manager = KingdomsMod.managerFor(source.getLevel());
        for (int i = 0; i < count; i++) {
            world.step();
            if (manager != null) {
                manager.flushConstruction();
            }
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
