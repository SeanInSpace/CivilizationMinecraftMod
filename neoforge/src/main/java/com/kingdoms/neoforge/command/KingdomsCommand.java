package com.kingdoms.neoforge.command;

import com.kingdoms.neoforge.KingdomsConfig;
import com.kingdoms.neoforge.KingdomsMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import com.kingdoms.neoforge.net.TownOverviewPayload;
import com.kingdoms.neoforge.view.PersonEntityManager;
import com.kingdoms.neoforge.view.TickRate;
import com.kingdoms.neoforge.world.TownAuditor;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.neoforge.save.SiteLedger;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.worldgen.SettlementSites;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildCatalogue;
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
import com.kingdoms.neoforge.world.LevelStoreWorld;
import com.kingdoms.neoforge.world.Shelves;
import com.kingdoms.neoforge.world.BuildTest;
import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.culture.Layout;
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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import com.kingdoms.sim.settlement.Founding;

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

                // Raises a settlement that is already what /civ found spends
                // some four hundred steps becoming. The count is the
                // population, which is the one thing about a seeded town worth
                // overriding by hand: everything else follows from the stage.
                .then(Commands.literal("seed")
                        .then(Commands.argument("stage", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (SettlementStage known : SettlementStage.values()) {
                                        builder.suggest(known.pretty());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> seed(ctx,
                                        StringArgumentType.getString(ctx, "stage"),
                                        Founding.AS_THE_STAGE_HOUSES))
                                .then(Commands.argument("count",
                                                IntegerArgumentType.integer(1, 200))
                                        .executes(ctx -> seed(ctx,
                                                StringArgumentType.getString(ctx, "stage"),
                                                IntegerArgumentType.getInteger(ctx, "count"))))))

                .then(Commands.literal("info")
                        .executes(KingdomsCommand::info))

                .then(Commands.literal("overview")
                        .executes(KingdomsCommand::overview))

                .then(Commands.literal("audit")
                        .executes(KingdomsCommand::audit)
                        .then(Commands.literal("selftest")
                                .executes(KingdomsCommand::auditSelfTest)))

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

                .then(Commands.literal("list")
                        .executes(KingdomsCommand::list))

                .then(Commands.literal("wall")
                        .executes(KingdomsCommand::wall)
                        .then(Commands.literal("complete")
                                .executes(KingdomsCommand::wallComplete))
                        .then(Commands.literal("map")
                                .executes(KingdomsCommand::wallMap)))

                .then(Commands.literal("buildstop")
                        .executes(KingdomsCommand::buildStop))

                .then(Commands.literal("buildtest")
                        .executes(ctx -> buildTest(ctx, 1, 64, DRAWN_BY_DEFAULT))
                        .then(Commands.argument("bps", IntegerArgumentType.integer(1, 20))
                                .executes(ctx -> buildTest(ctx,
                                        IntegerArgumentType.getInteger(ctx, "bps"), 64,
                                        DRAWN_BY_DEFAULT))
                                .then(Commands.argument("count",
                                                IntegerArgumentType.integer(1, 256))
                                        .executes(ctx -> buildTest(ctx,
                                                IntegerArgumentType.getInteger(ctx, "bps"),
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                DRAWN_BY_DEFAULT))
                                        .then(Commands.argument("layout",
                                                        StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    for (Layout known : Layouts.all()) {
                                                        builder.suggest(known.id());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> buildTest(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "bps"),
                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                        StringArgumentType.getString(ctx, "layout")))))))

                .then(Commands.literal("plan")
                        .executes(KingdomsCommand::plan))

                .then(Commands.literal("oracle")
                        .executes(KingdomsCommand::oracle))

                .then(Commands.literal("terrain")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(32, 512))
                                .executes(ctx -> terrain(ctx,
                                        IntegerArgumentType.getInteger(ctx, "radius")))))

                .then(Commands.literal("stores")
                        .executes(KingdomsCommand::stores))

                .then(Commands.literal("culture")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    for (Culture known : Culture.all()) {
                                        builder.suggest(known.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> culture(ctx,
                                        StringArgumentType.getString(ctx, "id")))))

                .then(Commands.literal("hunger")
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 99))
                                .executes(ctx -> hunger(ctx, IntegerArgumentType.getInteger(ctx, "level")))))

                .then(Commands.literal("sites")
                        .executes(ctx -> sites(ctx, DEFAULT_SITE_REACH))
                        .then(Commands.argument("reach", IntegerArgumentType.integer(64, 8192))
                                .executes(ctx -> sites(ctx,
                                        IntegerArgumentType.getInteger(ctx, "reach")))))
        );
    }

    /**
     * How far {@code /civ sites} looks when nobody says.
     *
     * <p>Four regions across. Sampled over 400 random positions at the test
     * seed that is a mean of 4.3 sites and never more than 11 — few enough to
     * read in chat, and wide enough that "none" is news rather than the usual
     * reply.
     */
    private static final int DEFAULT_SITE_REACH = 2 * SettlementSites.REGION;

    /**
     * How many sites {@code /civ sites} will print before summarising.
     *
     * <p>The client keeps a hundred lines of chat. A reach of 8192 finds close
     * to three hundred sites, so without a cap the widest sweep pushes its own
     * heading off the top of the screen and the nearest sites — the only ones
     * anybody asked about — go with it.
     */
    private static final int MAX_SITES_LISTED = 24;

    /**
     * Walks every loaded building and reports what is built wrong.
     *
     * <p>The checks are the faults live play keeps finding and logs never show:
     * buildings in water, floors above or below their own ground, walls through
     * other walls, doors nobody can reach, fields stripped of their crops. Each
     * fault is also written to the log under {@code AUDIT}, so a scripted run
     * can grep for regressions without a person standing in the town.
     */
    /**
     * Asks the auditor to check itself, and says plainly whether it passed.
     *
     * <p>"No faults" from a broken auditor reads exactly like "no faults" from
     * a healthy town, so this exists to tell those two apart before anybody
     * trusts a clean sweep.
     */
    private static int auditSelfTest(CommandContext<CommandSourceStack> ctx) {
        java.util.List<String> lines = TownAuditor.selfTest();
        long failed = lines.stream().filter(line -> line.startsWith("FAIL")).count();
        StringBuilder out = new StringBuilder("=== Auditor self-test ===");
        for (String line : lines) {
            out.append("\n  ").append(line);
        }
        out.append("\n  ").append(lines.size() - failed).append("/").append(lines.size())
                .append(failed == 0
                        ? " — it catches what it should and ignores what it should not"
                        : " — THE AUDITOR IS WRONG; do not trust a clean sweep");
        String report = out.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(report), false);
        KingdomsMod.LOGGER.info("AUDITSELFTEST {}/{} passed", lines.size() - failed, lines.size());
        return failed == 0 ? 1 : 0;
    }

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

    /**
     * Lists the settlement sites the seed puts near the player.
     *
     * <p>The sites exist as arithmetic long before anything is built on them, so
     * without this there is no way to see them at all: nothing is placed,
     * nothing is loaded, and the first evidence a region was ever going to hold
     * a town is a town. This prints the answer the chooser gives and, beside it,
     * what the ledger has since made of it — which is the only way to tell "not
     * visited yet" from "looked at and refused", two states that look identical
     * from inside the world.
     *
     * <p>Shows the <em>raw</em> centre, deliberately. See the note on
     * {@link SettlementSites#UNRESOLVED_Y}: the terrain-adjusted centre does not
     * exist until somebody has generated the ground to adjust against, and
     * making a debug listing do that would have {@code /civ sites} quietly
     * generate a few hundred chunks. Once a region is resolved the ledger's
     * entry carries where the town actually went, and that is printed too — so
     * both numbers are visible for a resolved region and only the honest one for
     * an unresolved one.
     */
    private static int sites(CommandContext<CommandSourceStack> ctx, int reach) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        SimPos here = toSimPos(source.getPosition());
        List<SettlementSites.Site> found =
                SettlementSites.near(level.getSeed(), here, reach,
                        KingdomsConfig.arrangementWeights());
        SiteLedger ledger = SiteLedger.get(level);

        // The dimension is in the heading because the trap is silent otherwise.
        // The chooser knows nothing about dimensions and getSeed answers the
        // same in every one of them, so this prints the overworld's sites
        // wherever it is run — while the ledger it checks them against is this
        // dimension's, and in the Nether will be empty forever. Two answers
        // about different worlds on the same line.
        StringBuilder sb = new StringBuilder("=== Sites within " + reach + " blocks of ")
                .append(level.dimension().identifier())
                .append(" ===");
        if (found.isEmpty()) {
            sb.append("\n  none — regions are ").append(SettlementSites.REGION)
                    .append(" blocks across and only some hold a town");
        }
        for (SettlementSites.Site site : found.subList(0, Math.min(found.size(), MAX_SITES_LISTED))) {
            int regionX = SettlementSites.regionXOf(site);
            int regionZ = SettlementSites.regionZOf(site);
            SimPos centre = site.centre();
            sb.append("\n  r(").append(regionX).append(", ").append(regionZ).append(")")
                    .append("  x=").append(centre.x()).append(" z=").append(centre.z())
                    .append("  ").append(site.cultureId())
                    .append(" ").append(site.layoutId())
                    .append("  ").append(Math.round(centre.horizontalDistance(here)))
                    .append("m  ")
                    .append(ledger.entry(regionX, regionZ)
                            .map(entry -> entry.centre()
                                    .map(at -> "founded at x=" + at.x() + " z=" + at.z())
                                    .orElse("refused"))
                            .orElse("unresolved"));
        }
        if (found.size() > MAX_SITES_LISTED) {
            sb.append("\n  ... and ").append(found.size() - MAX_SITES_LISTED)
                    .append(" further out");
        }
        sb.append("\n  (y is unresolved until the ground is looked at)");
        String report = sb.toString();
        source.sendSuccess(() -> Component.literal(report), false);
        return found.size();
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
                  found <name>              found a settlement here, party and all
                  seed <stage> [pop]        raise one already built at that stage
                  info                      full state of every settlement
                  overview                  open the town overview screen
                  populate <n> <job>        BUILDER/FARMER/GUARD/TRADER/LUMBERJACK/MINER/IDLER
                  build <blueprint> <work>  queue construction here
                  step [n]                  fast-forward the simulation
                  raid [strength]           attack the nearest settlement
                  threat <level>            set the alarm level
                  hunger <0-99>             set everyone's hunger
                  sites [reach]             settlement sites the seed puts nearby
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
        // The same founding a charter performs, party and all. This used to
        // raise a settlement with a kit and nobody to spend it, so every
        // scripted run had to follow with /civ populate and no test ever
        // exercised what a player actually gets.
        // Look at the ground before planting on it. A town founded across a
        // ravine fights it forever, and no routing downstream undoes that.
        SimPos wanted = centre;
        SimPos chosen = Founding.bestSiteNear(centre, Founding.SITING_REACH,
                KingdomsMod.simulationFor(level).bridge());
        if (!chosen.equals(wanted)) {
            KingdomsMod.LOGGER.info("FOUND moved {} from {} to {} for better ground",
                    name, wanted, chosen);
        }
        Settlement settlement = Founding.party(chosen, name + " Town");
        kingdom.addSettlement(settlement);

        // Both, deliberately: the saved data is what persists, the sim world is what ticks.
        KingdomsSavedData.get(level).addKingdom(kingdom);
        world.addKingdom(kingdom);

        source.sendSuccess(() -> Component.literal(
                "Founded " + name + " at " + chosen
                        + (chosen.equals(wanted) ? "" : " (moved off poor ground)")
                        + " (claim radius 64)"), true);
        return 1;
    }

    /**
     * The culture a seeded town belongs to.
     *
     * <p>The same lowlanders {@code /civ found} plants, so the two commands
     * produce towns of the same people and a seeded one can be compared
     * directly with a grown one.
     */
    private static final String SEEDED_CULTURE = "kingdoms:norman";

    /**
     * Raises a settlement that is already built, staffed and stocked at a stage.
     *
     * <p>The whole point of the unit this belongs to: what world generation will
     * place, reachable by hand so it can be walked around and argued with. The
     * plumbing is {@code /civ found}'s — the ground is judged before anything is
     * planted, and the kingdom is registered with the saved data as well as the
     * simulation, because omitting the first is a recorded bug that costs a
     * whole verification pass and looks exactly like success until the world is
     * reloaded.
     *
     * <p><strong>The kingdom is built before the town, and its culture handed
     * down.</strong> {@code Kingdom.addSettlement} overwrites a settlement's
     * culture with the kingdom's, and {@code restoreSettlement} does not — and
     * for a seeded town that difference is not cosmetic. Every building was
     * placed on a plot from the culture's own arrangement, so a town stamped
     * with a <em>different</em> culture afterwards would be standing on one
     * people's plan while its siting code read another's: its own buildings
     * would no longer sit on any plot it recognises, and the next thing it built
     * would go through one of them. So {@code addSettlement} is used, which is
     * the honest verb here — this is a founding, not a load — and the culture is
     * taken from the kingdom on the way in so the stamp changes nothing.
     */
    private static int seed(CommandContext<CommandSourceStack> ctx, String stageName,
                            int residents) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        SimWorld world = KingdomsMod.simulationFor(level);
        if (world == null) {
            source.sendFailure(Component.literal("No simulation for this dimension."));
            return 0;
        }
        SettlementStage stage = SettlementStage.parse(stageName, null);
        if (stage == null) {
            source.sendFailure(Component.literal(
                    "No such stage: " + stageName + ". Try one of "
                            + Arrays.stream(SettlementStage.values())
                                    .map(SettlementStage::pretty).toList()));
            return 0;
        }

        SimPos wanted = toSimPos(source.getPosition());
        // A charter's reach, not a generated town's. Somebody typed this
        // standing somewhere, which is a choice, and Founding.CHARTER_REACH
        // exists to say how much of that choice may be overruled: a dozen
        // blocks is "shifted off the cliff edge", sixty is "went somewhere
        // else". World generation, which has no opinion about where it lands,
        // is the caller that gets SITING_REACH.
        SimPos chosen = Founding.bestSiteNear(wanted, Founding.CHARTER_REACH, world.bridge());
        if (!chosen.equals(wanted)) {
            KingdomsMod.LOGGER.info("SEED moved from {} to {} for better ground",
                    wanted, chosen);
        }

        String name = seededName(world);
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), name, SEEDED_CULTURE);
        Settlement settlement = Founding.seeded(chosen, name, stage,
                BuildCatalogue.DEFAULT,
                kingdom.cultureId(), residents);
        kingdom.addSettlement(settlement);

        // Both, deliberately: the saved data is what persists, the sim world is
        // what ticks.
        KingdomsSavedData.get(level).addKingdom(kingdom);
        world.addKingdom(kingdom);

        int population = settlement.population();
        int buildings = settlement.buildings().size();
        source.sendSuccess(() -> Component.literal(
                "Seeded " + name + " at " + chosen + " as a " + stage.pretty()
                        + (chosen.equals(wanted) ? "" : " (moved off poor ground)")
                        + ": " + buildings + " buildings, " + population + " residents, "
                        + "claim radius " + settlement.claimRadius()
                        + ". Nothing is drawn until somebody is near enough to see it."
                        // Said out loud because it surprises everybody once. A
                        // settlement standing the whole of its stage's programme
                        // has met most of that stage's graduation conditions, so
                        // it moves up on the next step -- which is what an
                        // honestly-grown town does too, and is why this is a
                        // note rather than a fault.
                        + (stage == SettlementStage.TOWN || stage == SettlementStage.HOMESTEAD
                                ? ""
                                : " It has finished this stage, so expect it to reach "
                                        + stage.next().pretty() + " on the next step.")), true);
        KingdomsMod.LOGGER.info("SEED {} stage={} at {} buildings={} pop={}",
                name, stage.pretty(), chosen, buildings, population);
        return 1;
    }

    /**
     * A name from the culture's own list, so seeding twice does not give two
     * towns the same name.
     */
    private static String seededName(SimWorld world) {
        List<String> pool = Culture.of(SEEDED_CULTURE).townNames();
        if (pool == null || pool.isEmpty()) {
            pool = List.of("Seedholt");
        }
        int already = world.kingdoms().size();
        String base = pool.get(already % pool.size());
        int wrap = already / pool.size();
        return wrap == 0 ? base : base + " " + (wrap + 1);
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
        // Read this line before believing any other line in the report. Every
        // rate below it — the wall going up, the paths being joined, the litter
        // being picked up — is paced by the manager's pass, and on a server that
        // never caught up from a /civ step that pass runs a fifth as often as it
        // is scheduled to. A town doing a fifth of the work it should looks
        // exactly like a town with a bug in it.
        if (digger != null) {
            sb.append("\n  manager ").append(digger.rate().describe())
                    .append("  (intended ")
                    .append(Math.round(TickRate.intendedPerMinute(
                            PersonEntityManager.TICK_INTERVAL)))
                    .append("/min)");
        }
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
                long eating = s.residents().stream()
                        .filter(FoodPlanner::isGoingToEat).count();
                sb.append("\n      hunger: worst ").append(worstHunger)
                        .append(starving > 0 ? " (" + starving + " STARVING)" : "")
                        .append(eating > 0 ? ", " + eating + " off to eat" : "");
                // The line the roof report is for. A body with a trade that has
                // not moved a block in a quarter of a minute is being steered by
                // nobody, and which of the several ways that happens cannot be
                // told apart from inside the simulation -- so say who, where,
                // doing what, and how hungry, and let the world settle it.
                List<String> idle = digger == null ? List.of() : digger.idleReport(s);
                if (!idle.isEmpty()) {
                    sb.append("\n      idle (").append(idle.size()).append(" over ")
                            .append(PersonEntityManager.IDLE_REPORT_SECONDS).append("s):");
                    for (String line : idle) {
                        sb.append("\n        ").append(line);
                    }
                }
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

    /**
     * Re-badges the nearest settlement as another people.
     *
     * <p>Until this existed there was no way to reach any culture but the
     * default from inside a game: every settlement started on
     * {@code kingdoms:default} and nothing ever picked another. So the layouts,
     * the names and the beasts were all reachable only from a unit test, which
     * is a poor place to find out that a warren looks wrong from the ground.
     *
     * <p>Only the plots not yet taken move. A town already standing keeps every
     * building where it is — re-planning ground that has houses on it would
     * demolish them — so what this changes is how the town grows from here.
     * Re-badging a settlement with nothing built yet gives a clean example of
     * the new arrangement.
     */
    private static int culture(CommandContext<CommandSourceStack> ctx, String id) {
        CommandSourceStack source = ctx.getSource();
        Settlement settlement = nearestSettlement(source);
        if (settlement == null) {
            source.sendFailure(Component.literal("No settlement nearby."));
            return 0;
        }
        Culture chosen = Culture.of(id);
        if (!chosen.id().equals(id)) {
            source.sendFailure(Component.literal(
                    "No culture called " + id + ". Known: "
                            + Culture.all().stream().map(Culture::id).sorted().toList()));
            return 0;
        }
        settlement.setCultureId(chosen.id());
        // Un-settled here rather than inside setCultureId, which leaves a
        // recorded arrangement alone on purpose. This is the one caller that
        // means to throw the old shape away: re-badging a town that went on
        // building as the people it used to be would look like the command had
        // done nothing.
        settlement.setLayoutId(null);
        // The town's own arrangement rather than the culture's, because a people
        // can build in several and which one this town gets is decided by where
        // it stands.
        String arrangement = settlement.arrangement().id();
        markDirty(source);
        source.sendSuccess(() -> Component.literal(
                settlement.name() + " is now " + chosen.id()
                        + " — laying out as " + arrangement
                        + " from its next plot on"), true);
        KingdomsMod.LOGGER.info("CULTURE {} -> {} ({})",
                settlement.name(), chosen.id(), arrangement);
        return 1;
    }

    /**
     * Every store in the nearest town: where it is, what its ledger says, and
     * what is actually on its shelves.
     *
     * <p>The ledger is the truth and the chest is a window onto it, so the two
     * can disagree for an honest reason — a chest holds fifty-four slots and a
     * building's ledger has no such limit. This prints both side by side so the
     * difference can be seen rather than argued about, and prints the
     * coordinates so somebody can go and open the thing.
     */
    /**
     * The state of the palisade, post by post, for the stretches loaded enough
     * to look at.
     *
     * <p>Written because the wall was being built straight through woodland and
     * there was no way to see it without walking the whole ring. The auditor
     * checks buildings and says nothing about the wall; this counts what is
     * standing, what is missing, and what has a tree in it.
     */
    /**
     * Every settlement in the world, nearest first, with where to find it.
     *
     * <p>There was no way to ask this. {@code /civ info} and {@code /civ stores}
     * answer about the nearest town, {@code /civ overview} draws a screen and
     * needs a player, and {@code /civ audit} reports faults rather than places.
     * So the only way to find out what a world contained was to read a log and
     * infer it from the names, which is how a session went by before anybody
     * noticed six towns had been founded rather than one.
     *
     * <p>Sorted by distance because the question behind the question is nearly
     * always "which one am I standing near, and where is the next".
     */
    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        SimWorld world = KingdomsMod.simulationFor(level);
        if (world == null) {
            source.sendFailure(Component.literal("No simulation for this dimension."));
            return 0;
        }
        Vec3 from = source.getPosition();
        record Row(double away, String line) { }
        java.util.List<Row> rows = new java.util.ArrayList<>();
        int total = 0;
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                total++;
                SimPos at = settlement.centre();
                double away = Math.sqrt(Math.pow(at.x() - from.x, 2)
                        + Math.pow(at.z() - from.z, 2));
                String wall = settlement.perimeter() == null ? "none"
                        : settlement.perimeter().laid() + "/"
                                + settlement.perimeter().length();
                rows.add(new Row(away, String.format(
                        "  %-22s %-10s pop %-4d  at %6d %4d %6d  %5.0fm away"
                                + "  wall %-9s coin %-6d food %d",
                        settlement.name(), settlement.stage().pretty(),
                        settlement.population(), at.x(), at.y(), at.z(), away,
                        wall, settlement.treasury(), settlement.foodStock())));
                KingdomsMod.LOGGER.info(
                        "LIST {} kingdom={} stage={} pop={} at={} {} {} wall={} coin={} food={}",
                        settlement.name(), kingdom.name(), settlement.stage().name(),
                        settlement.population(), at.x(), at.y(), at.z(), wall,
                        settlement.treasury(), settlement.foodStock());
            }
        }
        rows.sort(java.util.Comparator.comparingDouble(Row::away));
        StringBuilder out = new StringBuilder(
                "=== " + total + " settlement" + (total == 1 ? "" : "s") + " ===");
        for (Row row : rows) {
            out.append(NEWLINE).append(row.line());
        }
        if (rows.isEmpty()) {
            out.append(NEWLINE).append("  Nothing has been founded in this world.");
        }
        String report = out.toString();
        source.sendSuccess(() -> Component.literal(report), false);
        return total;
    }

    /** A line break in a chat report. Named so no editor can eat the escape. */
    private static final String NEWLINE = String.valueOf((char) 10);

    /**
     * Raises the whole ring at once, paid for by nobody.
     *
     * <p>A wall only shuts anybody in once it closes, and a town closes its ring
     * when it can afford to -- which under the current economy means when a
     * player has traded with it, since coin enters the world no other way. So
     * the one state worth testing for lock-in is the one a test world will not
     * reach on its own: every headless run stalls at about a quarter of the
     * ring with gaps a herd could walk through, and reports no one trapped
     * because nothing is yet capable of trapping them.
     *
     * <p>This is a debug command and says so. It skips the cost, not the work:
     * the posts still go up through {@code PerimeterLayer} exactly as they
     * would have, so what you are looking at afterwards is a real wall.
     */
    private static int wallComplete(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Settlement settlement = nearestSettlement(source);
        if (settlement == null) {
            source.sendFailure(Component.literal("No settlement nearby."));
            return 0;
        }
        com.kingdoms.sim.settlement.Perimeter ring = settlement.perimeter();
        if (ring == null) {
            source.sendFailure(Component.literal(
                    settlement.name() + " has not staked a wall yet."));
            return 0;
        }
        int was = ring.laid();
        ring.setLaid(ring.length());
        KingdomsSavedData.get(source.getLevel()).setDirty();
        source.sendSuccess(() -> Component.literal(
                "  " + settlement.name() + ": ring raised " + was + " -> "
                        + ring.length() + " (unpaid; debug)"), false);
        KingdomsMod.LOGGER.info("WALLCOMPLETE {} {} -> {} gates={}",
                settlement.name(), was, ring.length(), ring.gates());
        return 1;
    }

    /**
     * A plan view of the ring, drawn into the log.
     *
     * <p>Counts could not settle what a wall looked like. A ring of 2758 posts
     * around a town of eighty is either a long boundary or a wrong one, and
     * "2758" says nothing about which -- while a screenshot from inside a town
     * shows fence in every direction and cannot say whether that is one line
     * seen from within or twenty lines that should not be there.
     *
     * <p>So: the ring as {@code #}, its gates as {@code G}, building plots as
     * {@code B}, the centre as {@code +}, scaled to fit a readable grid. One
     * look answers the shape question that no tally can.
     */
    private static int wallMap(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Settlement settlement = nearestSettlement(source);
        if (settlement == null) {
            source.sendFailure(Component.literal("No settlement nearby."));
            return 0;
        }
        com.kingdoms.sim.settlement.Perimeter ring = settlement.perimeter();
        if (ring == null) {
            source.sendFailure(Component.literal("No wall staked."));
            return 0;
        }
        java.util.List<com.kingdoms.sim.geom.SimPos> line = ring.ringPositions();
        int west = Integer.MAX_VALUE;
        int east = Integer.MIN_VALUE;
        int north = Integer.MAX_VALUE;
        int south = Integer.MIN_VALUE;
        for (com.kingdoms.sim.geom.SimPos at : line) {
            west = Math.min(west, at.x());
            east = Math.max(east, at.x());
            north = Math.min(north, at.z());
            south = Math.max(south, at.z());
        }
        int width = Math.max(1, east - west);
        int depth = Math.max(1, south - north);
        final int cols = 78;
        int rows = Math.max(8, Math.min(40, cols * depth / Math.max(1, width) / 2));
        char[][] grid = new char[rows][cols];
        for (char[] row : grid) {
            java.util.Arrays.fill(row, ' ');
        }
        for (com.kingdoms.sim.settlement.Building building : settlement.buildings()) {
            com.kingdoms.sim.geom.SimPos at = building.origin();
            int cx = (at.x() - west) * (cols - 1) / width;
            int cz = (at.z() - north) * (rows - 1) / depth;
            if (cx >= 0 && cx < cols && cz >= 0 && cz < rows) {
                grid[cz][cx] = 'B';
            }
        }
        for (com.kingdoms.sim.geom.SimPos at : line) {
            int cx = (at.x() - west) * (cols - 1) / width;
            int cz = (at.z() - north) * (rows - 1) / depth;
            if (grid[cz][cx] != 'G') {
                grid[cz][cx] = ring.isGateway(at) ? 'G' : '#';
            }
        }
        int px = (settlement.centre().x() - west) * (cols - 1) / width;
        int pz = (settlement.centre().z() - north) * (rows - 1) / depth;
        if (px >= 0 && px < cols && pz >= 0 && pz < rows) {
            grid[pz][px] = '+';
        }
        KingdomsMod.LOGGER.info("WALLMAP {} posts={} vertices={} box={}x{} at {},{}",
                settlement.name(), line.size(), ring.vertices().size(),
                width, depth, west, north);
        for (char[] row : grid) {
            KingdomsMod.LOGGER.info("WALLMAP |{}|", new String(row));
        }
        source.sendSuccess(() -> Component.literal(
                "  plan of " + settlement.name() + " written to the log ("
                        + line.size() + " posts, " + ring.vertices().size()
                        + " vertices)"), false);
        return 1;
    }

    /**
     * Dumps the whole town as data, for drawing outside the game.
     *
     * <p>Every picture of this simulation so far has been a <em>port</em> of it:
     * the layout formulas rewritten in another language and plotted on a blank
     * sheet. Those pictures are faithful to the arithmetic and to nothing else
     * — no ground, no water, no trees, no roads, no wall, and every plot drawn
     * the same size because the port did not know the catalogue. A port also
     * drifts from what it copied, silently, the first time either side changes.
     *
     * <p>This emits the town's own numbers instead: real origins, real spans
     * from the real catalogue, the roads the path planner actually laid, the
     * ring as staked, and the ground under all of it. What is drawn from this
     * is the town, not a reconstruction of it.
     *
     * <p>Written to the log rather than to chat. It is thousands of lines, and
     * the log is the only sink that will take it.
     */
    /**
     * Checks the terrain oracle against the ground it is guessing at.
     *
     * <p>The oracle's whole worth is being right about land nobody has loaded.
     * That is a claim, and an unchecked claim about terrain is how this project
     * ended up siting a quarter of a town in water. So: walk the chunks that
     * <em>are</em> loaded, ask the generator what it would have said about each
     * column without them, and compare with what is actually there.
     *
     * <p>Height error and water disagreement are reported separately because
     * they fail differently. Being a course out on a hillside costs a little
     * excavation; calling a lake dry puts a house in it.
     */
    private static int oracle(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        com.kingdoms.neoforge.world.TerrainOracle oracle =
                ((com.kingdoms.neoforge.bridge.NeoForgeWorldBridge)
                        KingdomsMod.simulationFor(level).bridge()).oracle();
        net.minecraft.world.phys.Vec3 at = source.getPosition();
        int cx = (int) at.x;
        int cz = (int) at.z;

        int compared = 0;
        int exact = 0;
        long error = 0;
        int worst = 0;
        int wetAgreed = 0;
        int saidDryIsWet = 0;
        int saidWetIsDry = 0;
        for (int dz = -96; dz <= 96; dz += 4) {
            for (int dx = -96; dx <= 96; dx += 4) {
                int x = cx + dx;
                int z = cz + dz;
                if (!level.hasChunkAt(new net.minecraft.core.BlockPos(x, 0, z))) {
                    continue;
                }
                int real = level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x, z);
                // OCEAN_FLOOR counts a tree: trunk and leaves are both solid and
                // dry, so a wooded column reads sixty blocks above the ground the
                // generator is describing. Comparing those is measuring the
                // forest, not the oracle -- so wooded columns sit this out.
                net.minecraft.world.level.block.state.BlockState under =
                        level.getBlockState(new net.minecraft.core.BlockPos(x, real - 1, z));
                if (com.kingdoms.neoforge.world.WallClearing.isGrowth(under)) {
                    continue;
                }
                boolean reallyWet =
                        !level.getFluidState(
                                new net.minecraft.core.BlockPos(x, real, z)).isEmpty()
                        || !level.getFluidState(
                                new net.minecraft.core.BlockPos(x, real - 1, z)).isEmpty();
                int guess = oracle.noiseHeight(x, z);
                boolean guessWet = oracle.noiseWet(x, z);

                compared++;
                int off = Math.abs(guess - real);
                error += off;
                worst = Math.max(worst, off);
                if (off == 0) {
                    exact++;
                }
                if (guessWet == reallyWet) {
                    wetAgreed++;
                } else if (reallyWet) {
                    saidDryIsWet++;
                } else {
                    saidWetIsDry++;
                }
            }
        }
        if (compared == 0) {
            source.sendFailure(Component.literal(
                    "Nothing loaded here to check the oracle against."));
            return 0;
        }
        final int n = compared;
        final long total = error;
        final int hits = exact;
        final int agreed = wetAgreed;
        final int missedWater = saidDryIsWet;
        final int imaginedWater = saidWetIsDry;
        final int deepest = worst;
        String report = "=== oracle against " + n + " loaded columns ===" + NEWLINE
                + "  height: " + (100 * hits / n) + "% exact, mean error "
                + String.format("%.2f", total / (double) n)
                + ", worst " + deepest + NEWLINE
                + "  water : " + (100 * agreed / n) + "% agree, "
                + missedWater + " lakes missed, " + imaginedWater + " imagined";
        source.sendSuccess(() -> Component.literal(report), false);
        KingdomsMod.LOGGER.info("ORACLE compared={} exactPct={} meanErr={} worst={} "
                        + "wetAgreePct={} missedWater={} imaginedWater={}",
                n, 100 * hits / n, String.format("%.2f", total / (double) n), deepest,
                100 * agreed / n, missedWater, imaginedWater);
        return 1;
    }

    /**
     * Draws a gridiron town here, one building at a time, with nobody in it.
     *
     * <p>An instrument rather than a feature. Every question about the shape of
     * a town has had to be asked of a grown settlement — seven minutes of
     * simulation, a population deciding what to build, ground that varies — so
     * a fault in the arrangement arrives tangled with all of it, and the last
     * three were found only by dumping the result and measuring it afterwards.
     *
     * <p>This draws the plan and nothing else. Same {@code TownPlan} the
     * simulation uses, so the geometry under test is the real geometry; no
     * people, no stores, no queue, no stages. On flat ground it draws the same
     * town every time from the same spot, so a difference in the result means a
     * difference in the code.
     */
    /** What /civ buildtest draws when nobody names an arrangement. */
    private static final String DRAWN_BY_DEFAULT = Culture.LAYOUT_STRONGHOLD_STREETS;

    private static int buildTest(CommandContext<CommandSourceStack> ctx,
                                 int buildingsPerSecond, int count, String layoutId) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        if (BuildTest.stop(level)) {
            source.sendSuccess(() -> Component.literal(
                    "  stopped the run that was already going"), false);
        }
        var at = source.getPosition();
        SimPos centre = new SimPos((int) Math.floor(at.x), (int) Math.floor(at.y),
                (int) Math.floor(at.z));
        int placing = BuildTest.start(level, centre, count, buildingsPerSecond, layoutId);
        source.sendSuccess(() -> Component.literal(
                "  drawing a gridiron of " + placing + " buildings at " + centre
                        + ", " + buildingsPerSecond + " a second"
                        + " — /civ buildtest again to restart, /civ buildstop to halt"), true);
        return 1;
    }

    private static int buildStop(CommandContext<CommandSourceStack> ctx) {
        boolean was = BuildTest.stop(ctx.getSource().getLevel());
        ctx.getSource().sendSuccess(() -> Component.literal(
                was ? "  stopped" : "  nothing was being drawn"), false);
        return 1;
    }

    private static int plan(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Settlement settlement = nearestSettlement(source);
        if (settlement == null) {
            source.sendFailure(Component.literal("No settlement nearby."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        SimPos centre = settlement.centre();

        KingdomsMod.LOGGER.info("PLAN TOWN {} {} {} {} {} {} {} {}",
                settlement.name().replace(' ', '_'), centre.x(), centre.y(), centre.z(),
                settlement.stage().name(), settlement.population(),
                settlement.cultureId(), settlement.arrangement().id());

        for (com.kingdoms.sim.settlement.Building b : settlement.buildings()) {
            SimPos at = b.origin();
            com.kingdoms.sim.settlement.Footprint print = b.footprint();
            // Both facings. The plan gives a plot the way it should look -- at
            // the street it fronts -- while this command has always reported
            // what the old centre-facing rule would say, and the two now
            // disagree for every planned town. Printing one of them would hide
            // whichever is wrong.
            KingdomsMod.LOGGER.info("PLAN B {} {} {} {} {} {} {} {} {} {}",
                    b.blueprintId(), at.x(), at.y(), at.z(),
                    com.kingdoms.sim.settlement.BuildPlanner.plotSpanOf(
                            b.blueprintId(), settlement.catalogue()),
                    com.kingdoms.sim.settlement.BuildPlanner.facingToward(at, centre),
                    b.role(),
                    print.isKnown() ? print.width() : -1,
                    print.isKnown() ? print.depth() : -1,
                    b.facing());
        }

        java.util.List<com.kingdoms.sim.settlement.PathNetwork.Segment> runs =
                settlement.paths().segments();
        for (int i = 0; i < runs.size(); i++) {
            com.kingdoms.sim.settlement.PathNetwork.Segment seg = runs.get(i);
            // Width and opened state, without which a map cannot tell a planned
            // carriageway from a trodden footpath, nor a road that exists on the
            // ground from one that is still only intended.
            KingdomsMod.LOGGER.info("PLAN R {} {} {} {} {} {}",
                    seg.from().x(), seg.from().z(), seg.to().x(), seg.to().z(),
                    seg.width(), settlement.paths().isOpened(i) ? 1 : 0);
        }

        // The ground under each run, block by block along it. Without this a
        // survey can say where a road goes and not whether anybody could walk
        // it: a stretch that climbs four blocks in one step is a wall with
        // gravel on it, and reads on a map as a perfectly ordinary street.
        com.kingdoms.neoforge.world.TerrainOracle roadGround =
                ((com.kingdoms.neoforge.bridge.NeoForgeWorldBridge)
                        KingdomsMod.simulationFor(level).bridge()).oracle();
        for (int i = 0; i < runs.size(); i++) {
            StringBuilder profile = new StringBuilder();
            for (SimPos step : runs.get(i).positions()) {
                profile.append(roadGround.height(step.x(), step.z())).append(',');
            }
            KingdomsMod.LOGGER.info("PLAN RS {} {}", i, profile);
        }

        com.kingdoms.sim.settlement.Perimeter ring = settlement.perimeter();
        if (ring != null) {
            StringBuilder line = new StringBuilder();
            for (SimPos v : ring.vertices()) {
                line.append(v.x()).append(',').append(v.z()).append(' ');
            }
            KingdomsMod.LOGGER.info("PLAN W {} {} {}",
                    ring.laid(), ring.length(), line.toString().trim());
            StringBuilder gates = new StringBuilder();
            for (SimPos g : ring.gates()) {
                gates.append(g.x()).append(',').append(g.z()).append(' ');
            }
            KingdomsMod.LOGGER.info("PLAN G {}", gates.toString().trim());

            // Every post on the loop, with whether it is an opening and whether
            // it has been raised yet. The vertices alone describe the shape the
            // wall is meant to be; these are the blocks it is actually made of,
            // which is the only way to see a gate that is not where a road is.
            java.util.List<SimPos> posts = ring.ringPositions();
            StringBuilder row = new StringBuilder();
            for (int i = 0; i < posts.size(); i++) {
                SimPos post = posts.get(i);
                row.append(post.x()).append(',').append(post.z()).append(',')
                   .append(ring.isGateway(post) ? 'G' : 'P')
                   .append(i < ring.laid() ? 'L' : 'u').append(' ');
                if (row.length() > 3000) {
                    KingdomsMod.LOGGER.info("PLAN P {}", row.toString().trim());
                    row.setLength(0);
                }
            }
            if (row.length() > 0) {
                KingdomsMod.LOGGER.info("PLAN P {}", row.toString().trim());
            }
        }

        // The ground, sampled every PLAN_STEP blocks: surface height, whether
        // that column is water, and whether the reading came from a real chunk
        // ('.') or from the generator's noise (',').
        com.kingdoms.neoforge.world.TerrainOracle oracle =
                ((com.kingdoms.neoforge.bridge.NeoForgeWorldBridge)
                        KingdomsMod.simulationFor(level).bridge()).oracle();
        // Somebody asked; read the whole square rather than metering it out at
        // the planner's budget and returning a map nine tenths unread.
        oracle.warm(centre.x(), centre.z(), PLAN_REACH, PLAN_STEP);
        int half = PLAN_REACH;
        for (int dz = -half; dz <= half; dz += PLAN_STEP) {
            StringBuilder heights = new StringBuilder();
            StringBuilder wet = new StringBuilder();
            for (int dx = -half; dx <= half; dx += PLAN_STEP) {
                // The oracle, not the loaded world. A survey that could only
                // read loaded chunks came back with a tenth of the map blank
                // and needed three thousand chunks force-loaded to avoid it --
                // which starved the server of the ticks the town needed to draw
                // itself. It answers everywhere, and says which readings are
                // certain.
                int x = centre.x() + dx;
                int z = centre.z() + dz;
                heights.append(oracle.height(x, z)).append(',');
                // ':' rather than ',' -- the heights beside it are comma
                // separated, and a marker that collides with the separator made
                // the field unreadable to the survey parser.
                wet.append(oracle.isWet(x, z) ? '~'
                        : oracle.isCertain(x, z) ? '.' : ':');
            }
            KingdomsMod.LOGGER.info("PLAN H {} {} {}", dz, heights, wet);
        }

        source.sendSuccess(() -> Component.literal(
                "  " + settlement.name() + ": plan written to the log ("
                        + settlement.buildings().size() + " buildings, "
                        + settlement.paths().segments().size() + " road runs)"), false);
        return 1;
    }

    /** Half-width of ground sampled by {@code /civ plan}, and the sample pitch. */
    /**
     * Captures the real ground into a height field, for the test suite to use.
     *
     * <p>Run by hand, once, and the output committed. It exists because every
     * fake terrain this project tests against is smooth — {@code TerrainFake} is
     * three sine waves whose steepest gradient is about half a block per block,
     * so a rule that refuses ground climbing more than a block a step can never
     * fire in the suite at all. Three separate fixes for roads on unclimbable
     * slopes measured perfectly clean in the simulation and changed nothing in a
     * world, because the simulation had no unclimbable slopes in it.
     *
     * <p>So: real ground, recorded. Not the oracle's estimate — this forces full
     * chunk generation, which is far too slow to do while a town is running and
     * exactly right for a one-off capture. What comes back is the same
     * {@code OCEAN_FLOOR} the live rules read, jagged as it actually is.
     *
     * <p>Written at {@value #TERRAIN_GRAIN}-block grain. Grain one doubles the
     * file for detail no rule looks at; two still shows every two-block step.
     */
    private static int terrain(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        int cx = (int) Math.round(at.x);
        int cz = (int) Math.round(at.z);
        int x0 = cx - radius;
        int z0 = cz - radius;
        int cells = (2 * radius) / TERRAIN_GRAIN + 1;

        StringBuilder out = new StringBuilder();
        out.append("origin ").append(x0).append(' ').append(z0).append('\n');
        out.append("grain ").append(TERRAIN_GRAIN).append('\n');
        out.append("size ").append(cells).append(' ').append(cells).append('\n');
        out.append("sea ").append(level.getSeaLevel()).append('\n');

        long began = System.currentTimeMillis();
        for (int iz = 0; iz < cells; iz++) {
            int z = z0 + iz * TERRAIN_GRAIN;
            StringBuilder row = new StringBuilder();
            for (int ix = 0; ix < cells; ix++) {
                int x = x0 + ix * TERRAIN_GRAIN;
                // Full generation, forced. A capture may be slow; it may not lie.
                level.getChunk(x >> 4, z >> 4);
                // The sim's own definition of ground, not a raw heightmap.
                // OCEAN_FLOOR counts a log as ground, so a forest records as a
                // plateau eleven blocks up and every road judged against it is
                // judged against the treetops. groundLevel strips that
                // overburden, and is what the live bridge answers with.
                int height = com.kingdoms.neoforge.world.BlueprintPlacer
                        .groundLevel(level, x, z);
                // At the ground, and just under it. groundLevel returns the
                // first empty block ABOVE the surface, so testing the fluid
                // there tests the air: the first capture recorded not one wet
                // column in sixty-six thousand, and the fixture's water rules
                // were therefore never exercised at all.
                boolean wet = !level.getFluidState(
                                new net.minecraft.core.BlockPos(x, height, z)).isEmpty()
                        || !level.getFluidState(
                                new net.minecraft.core.BlockPos(x, height - 1, z)).isEmpty();
                if (ix > 0) {
                    row.append(' ');
                }
                row.append(height);
                if (wet) {
                    row.append('~');
                }
            }
            out.append(row).append('\n');
        }

        java.nio.file.Path file = level.getServer().getServerDirectory()
                .resolve("terrain_" + cx + "_" + cz + "_r" + radius + ".hf");
        try {
            java.nio.file.Files.writeString(file, out.toString());
        } catch (java.io.IOException failed) {
            source.sendFailure(Component.literal("could not write: " + failed.getMessage()));
            return 0;
        }
        long took = System.currentTimeMillis() - began;
        source.sendSuccess(() -> Component.literal(
                "  wrote " + cells + "x" + cells + " cells to " + file.getFileName()
                        + " in " + took + "ms"), true);
        KingdomsMod.LOGGER.info("TERRAIN wrote {} cells to {} in {}ms",
                cells * cells, file, took);
        return 1;
    }

    /** How far apart the recorded ground is sampled, in blocks. */
    private static final int TERRAIN_GRAIN = 2;

    private static final int PLAN_REACH = 200;
    private static final int PLAN_STEP = 4;

    private static int wall(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Settlement settlement = nearestSettlement(source);
        if (settlement == null) {
            source.sendFailure(Component.literal("No settlement nearby."));
            return 0;
        }
        com.kingdoms.sim.settlement.Perimeter ring = settlement.perimeter();
        if (ring == null) {
            source.sendSuccess(() -> Component.literal(
                    "  " + settlement.name() + " has not staked a wall yet."), false);
            return 0;
        }
        ServerLevel level = source.getLevel();
        int looked = 0;
        int standing = 0;
        int missing = 0;
        int blocked = 0;
        int gateways = 0;
        int shutByBuilding = 0;
        int unfooted = 0;
        java.util.Map<String, Integer> why = new java.util.TreeMap<>();
        java.util.Map<String, Integer> inTheWay = new java.util.TreeMap<>();
        java.util.List<String> examples = new java.util.ArrayList<>();
        java.util.List<com.kingdoms.sim.geom.SimPos> positions = ring.ringPositions();
        int laid = Math.min(ring.laid(), positions.size());
        for (int i = 0; i < laid; i++) {
            com.kingdoms.sim.geom.SimPos at = positions.get(i);
            net.minecraft.core.BlockPos here =
                    new net.minecraft.core.BlockPos(at.x(), at.y(), at.z());
            if (!level.isLoaded(here)) {
                continue;
            }
            looked++;
            // A gateway is SUPPOSED to be open. Counting the three-wide opening
            // at every gate as a hole made the first report of this untrustworthy
            // — "51 missing" with no way to tell a doorway from a failure.
            if (ring.isGateway(at)) {
                gateways++;
                continue;
            }
            net.minecraft.core.BlockPos ground =
                    com.kingdoms.neoforge.world.PerimeterLayer.footingFor(level, at);
            if (ground == null) {
                unfooted++;   // water, or no ground the layer would accept
                continue;
            }
            net.minecraft.core.BlockPos growth =
                    com.kingdoms.neoforge.world.WallClearing.inTheWay(level, ground);
            if (growth != null) {
                blocked++;
                // What, and where relative to the post's foot. "Growth in the
                // line: 240" is a number to argue with; "240, all of them leaves
                // four above the footing" is a number to act on, and the two
                // readings mean entirely different things about the wall.
                inTheWay.merge(level.getBlockState(growth).getBlock().getName().getString()
                        + " at +" + (growth.getY() - ground.getY()), 1, Integer::sum);
            }
            if (com.kingdoms.neoforge.world.PerimeterLayer.postStands(level, ground)) {
                standing++;
            } else if (com.kingdoms.neoforge.world.PerimeterLayer
                    .lineIsClosed(level, ground)) {
                shutByBuilding++;   // the ring runs through a wall; that is a wall
            } else {
                missing++;
                // Name what is there instead. A count says a post is absent; the
                // block standing in its place says why, which is the difference
                // between a report you can act on and one you can only argue with.
                String what = level.getBlockState(ground).getBlock()
                        .getName().getString();
                why.merge(what, 1, Integer::sum);
                if (examples.size() < 5) {
                    examples.add(ground.toShortString() + " (" + what + ")");
                }
            }
        }
        // Who the wall has ended up on the wrong side of. A ring is meant to
        // put the town inside and everything else outside; a settler whose bed
        // is in and whose body is out is a settler the wall has shut out, and
        // nothing measured that before -- the fault was reported from play and
        // could only be argued about.
        int in = 0;
        int out_ = 0;
        int shutOut = 0;
        for (com.kingdoms.sim.person.Person person : settlement.residents()) {
            if (!person.isEmbodied()) {
                continue;
            }
            com.kingdoms.sim.geom.SimPos at = person.position();
            boolean within = com.kingdoms.sim.geom.Hull.contains(ring.vertices(), at);
            if (within) {
                in++;
                continue;
            }
            out_++;
            for (com.kingdoms.sim.person.Household household : settlement.households()) {
                if (!household.isHoused() || !household.members().contains(person.id())) {
                    continue;
                }
                if (com.kingdoms.sim.geom.Hull.contains(
                        ring.vertices(), household.home())) {
                    shutOut++;   // bed inside, body outside
                }
                break;
            }
        }
        final int inside = in;
        final int outside = out_;
        final int strandedFromBed = shutOut;

        StringBuilder out = new StringBuilder("=== wall of " + settlement.name() + " ===")
                .append("\n  laid ").append(ring.laid()).append(" of ").append(ring.length())
                .append(", ").append(looked).append(" close enough to look at")
                .append("\n  posts standing     : ").append(standing)
                .append("\n  gateways (open)    : ").append(gateways)
                .append("\n  no footing         : ").append(unfooted)
                .append("\n  growth in the line : ").append(blocked)
                .append("\n  GENUINELY MISSING  : ").append(missing);
        for (var entry : why.entrySet()) {
            out.append("\n      ").append(entry.getValue()).append(" x ").append(entry.getKey());
        }
        for (var entry : inTheWay.entrySet()) {
            out.append(NEWLINE).append("      ").append(entry.getValue())
                    .append(" x ").append(entry.getKey());
        }
        for (String example : examples) {
            out.append("\n      at ").append(example);
        }
        String report = out.toString();
        source.sendSuccess(() -> Component.literal(report), false);
        KingdomsMod.LOGGER.info(
                "WALL {} laid={}/{} looked={} standing={} gateways={} unfooted={} "
                        + "blocked={} missing={} shutByBuilding={} because={} inTheWay={} "
                        + "inside={} outside={} shutOutOfBed={}",
                settlement.name(), ring.laid(), ring.length(), looked, standing,
                gateways, unfooted, blocked, missing, shutByBuilding, why, inTheWay,
                inside, outside, strandedFromBed);
        return 1;
    }

    private static int stores(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Settlement settlement = nearestSettlement(source);
        if (settlement == null) {
            source.sendFailure(Component.literal("No settlement nearby."));
            return 0;
        }
        LevelStoreWorld world = new LevelStoreWorld(source.getLevel());
        StringBuilder out = new StringBuilder("=== stores of " + settlement.name() + " ===");
        int shownTotal = 0;
        int heldTotal = 0;
        for (Building building : settlement.buildings()) {
            if (!building.isStore() || !building.hasStores()) {
                continue;
            }
            int held = building.stores().all().values().stream().mapToInt(Integer::intValue).sum();
            heldTotal += held;
            Shelves shelves = world.shelvesOf(building);
            out.append("\n  ").append(plain(building.blueprintId()))
                    .append(" @ ").append(building.origin().x()).append(' ')
                    .append(building.origin().y()).append(' ')
                    .append(building.origin().z());
            if (shelves == null) {
                out.append("  ledger=").append(held).append("  (no chest found)");
                continue;
            }
            int onShelves = 0;
            int used = 0;
            for (int slot = 0; slot < shelves.slots(); slot++) {
                if (!shelves.isEmpty(slot)) {
                    used++;
                    onShelves += shelves.amountAt(slot);
                }
            }
            shownTotal += onShelves;
            out.append("  ledger=").append(held)
                    .append("  onShelves=").append(onShelves)
                    .append("  slots=").append(used).append('/').append(shelves.slots());
            if (held > onShelves) {
                out.append("  << ").append(held - onShelves).append(" held but not shown");
            }
        }
        // The stall, which is the only place money changes hands and therefore
        // the thing anybody testing trade actually wants the coordinates of.
        Building market = settlement.buildingWithRole(
                com.kingdoms.sim.settlement.BuildingRole.MARKET);
        boolean trader = com.kingdoms.sim.economy.Market.hasTrader(settlement);
        out.append("\n  market: ");
        if (market == null) {
            out.append("none built");
        } else {
            out.append(market.origin().x()).append(' ').append(market.origin().y())
                    .append(' ').append(market.origin().z())
                    .append(trader ? "  (trader on duty)" : "  (NOBODY TRADES HERE)");
            for (com.kingdoms.sim.economy.Market.Deal deal
                    : com.kingdoms.sim.economy.Market.offers(settlement)) {
                out.append("\n    ").append(deal.townBuys() ? "buys  " : "sells ")
                        .append(deal.resource())
                        .append(" @ ").append(deal.unitPrice()).append("/unit, ")
                        .append(deal.lots()).append(" lot(s) of ")
                        .append(com.kingdoms.sim.economy.Market.LOT);
            }
            KingdomsMod.LOGGER.info("MARKET {} at {} {} {} trader={} offers={}",
                    settlement.name(), market.origin().x(), market.origin().y(),
                    market.origin().z(), trader,
                    com.kingdoms.sim.economy.Market.offers(settlement).size());
        }
        out.append("\n  town total: ledger=").append(heldTotal)
                .append(" onShelves=").append(shownTotal)
                .append("  treasury=").append(settlement.treasury());
        String report = out.toString();
        source.sendSuccess(() -> Component.literal(report), false);
        // One machine-readable line with everything a run wants to plot. Logged
        // rather than only shown, because the thing reading it is usually a
        // script driving a headless server — and unlike the audit's vitals,
        // every figure here is simulation state, so it reports the same whether
        // or not a single chunk of the town is loaded.
        KingdomsMod.LOGGER.info(
                "STORES {} pop={} stage={} coin={} wood={} stone={} food={} iron={} "
                        + "ledger={} shelves={}",
                settlement.name(), settlement.population(), settlement.stage().name(),
                settlement.treasury(), settlement.woodStock(),
                settlement.stores().get(com.kingdoms.sim.settlement.TownStores.STONE),
                settlement.foodStock(),
                settlement.stores().get(com.kingdoms.sim.settlement.TownStores.IRON),
                heldTotal, shownTotal);
        return 1;
    }

    private static String plain(String blueprintId) {
        return blueprintId.substring(blueprintId.indexOf(':') + 1);
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
