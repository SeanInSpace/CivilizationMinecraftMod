package com.kingdoms.neoforge;

import com.kingdoms.neoforge.bridge.NeoForgeWorldBridge;
import com.kingdoms.neoforge.client.KingdomsClient;
import com.kingdoms.neoforge.command.KingdomsCommand;
import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.neoforge.entity.Quarry;
import com.kingdoms.neoforge.net.KingdomsNetwork;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.neoforge.view.PersonEntityManager;
import com.kingdoms.neoforge.view.TickRate;
import com.kingdoms.neoforge.world.StoreSync;
import com.kingdoms.neoforge.world.HandDig;
import com.kingdoms.neoforge.world.TownAuditor;
import com.kingdoms.sim.world.SimSettings;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mod entrypoint and the only place the simulation is wired to the game.
 *
 * <p>This class deliberately contains no game logic. It owns one {@link SimWorld}
 * and one {@link PersonEntityManager} per dimension, drives them from the server
 * tick, and otherwise stays out of the way. All behavior lives in {@code :common}.
 */
@Mod(KingdomsMod.MOD_ID)
public final class KingdomsMod {

    public static final String MOD_ID = "kingdoms";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Map<ServerLevel, SimWorld> SIMULATIONS = new HashMap<>();
    private static final Map<ServerLevel, PersonEntityManager> MANAGERS = new HashMap<>();

    public KingdomsMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, KingdomsConfig.SPEC);
        KingdomsAttachments.ATTACHMENTS.register(modBus);
        KingdomsBlocks.BLOCKS.register(modBus);
        KingdomsBlockEntities.BLOCK_ENTITIES.register(modBus);
        KingdomsItems.ITEMS.register(modBus);
        KingdomsComponents.COMPONENTS.register(modBus);
        KingdomsEntities.ENTITY_TYPES.register(modBus);
        KingdomsTabs.TABS.register(modBus);
        modBus.addListener(KingdomsNetwork::register);
        modBus.addListener(KingdomsEntities::createAttributes);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modBus.addListener(KingdomsClient::registerRenderers);
        }

        NeoForge.EVENT_BUS.addListener(KingdomsMod::onServerStarted);
        NeoForge.EVENT_BUS.addListener(KingdomsMod::onServerStopping);
        NeoForge.EVENT_BUS.addListener(KingdomsMod::onServerTick);
        NeoForge.EVENT_BUS.addListener(KingdomsMod::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(KingdomsMod::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(KingdomsMod::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(KingdomsMod::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(KingdomsMod::onFarmlandTrample);

        LOGGER.info("Kingdoms loaded");
    }

    private static void onServerStarted(ServerStartedEvent event) {
        SimSettings settings = KingdomsConfig.settings();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SimWorld world = new SimWorld(new NeoForgeWorldBridge(level), settings);

            // Rehydrate from disk. These are the same Kingdom instances the save
            // data holds, so simulation changes are picked up on the next write.
            KingdomsSavedData.get(level).kingdoms().forEach(world::addKingdom);

            SIMULATIONS.put(level, world);
            MANAGERS.put(level, new PersonEntityManager(level, world));
        }
        LOGGER.info("Initialized {} dimension simulation(s)", SIMULATIONS.size());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        // Write every embodied person's state back before the level saves.
        MANAGERS.values().forEach(PersonEntityManager::releaseAll);
        MANAGERS.clear();
        SIMULATIONS.clear();
        // The audit's memories outlive the world they describe. Kept across a
        // quit to the title screen, a stale fingerprint swallows the next
        // session's first report of a fault that was already standing — which
        // is the one report a scripted run reads.
        AUDIT_SEEN.clear();
        TownAuditor.forget();
        // Crack overlays belong to entity ids in a world about to close.
        HandDig.forget();
        StoreSync.forget();
        // And the wall's cursor and its clock, which are about a ring that is
        // going away — a timestamp from a closed world would have the next one's
        // first sweep measuring an interval that spans the two.
        com.kingdoms.neoforge.world.PerimeterLayer.forget();
        // And where each paving crew had got along its run, which is a place in
        // a network that is going away with the world it belonged to.
        com.kingdoms.neoforge.view.Foreman.forget();
        // And whether this world's spawn towns are settled, which is a fact
        // about a ledger that is going away with the level that held it.
        com.kingdoms.neoforge.world.WorldgenSettlements.forget();
        // And anybody still waiting to be told where the towns are, since the
        // towns and the world they stand in are both going away.
        GREETINGS.clear();
    }

    /** Our own tick count — the level clock is not trusted for cadence (it can freeze). */
    private static long tickCounter;

    private static void onServerTick(ServerTickEvent.Post event) {
        // The plan renderer, if one is running. Independent of the simulation
        // on purpose: it is an instrument for looking at arrangement, and a
        // settlement's people are exactly what it leaves out.
        for (ServerLevel level : event.getServer().getAllLevels()) {
            com.kingdoms.neoforge.world.BuildTest.tick(level);
        }
        tickCounter++;
        // The towns around the world spawn, raised at world start rather than
        // on approach -- one a tick, so they are standing before the player has
        // finished loading in. Costs nothing the moment they are settled.
        for (ServerLevel level : event.getServer().getAllLevels()) {
            com.kingdoms.neoforge.world.WorldgenSettlements.tickAnchor(level);
        }
        // And then tell whoever just joined where they are. After the anchor,
        // never before: the whole reason the greeting waits is those nine.
        deliverGreetings(event.getServer());
        // Towns that were always going to be there, raised when somebody first
        // comes close enough to see them. On its own beat, because it reads
        // ground rather than stepping people.
        if (tickCounter % com.kingdoms.neoforge.world.WorldgenSettlements.SWEEP_INTERVAL_TICKS == 0) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                com.kingdoms.neoforge.world.WorldgenSettlements.tick(level);
            }
        }
        for (Map.Entry<ServerLevel, SimWorld> entry : SIMULATIONS.entrySet()) {
            if (entry.getValue().onGameTick()) {
                // A step ran, so the kingdoms changed in place. Nothing else marks
                // this dirty, because the simulation mutates the objects directly.
                KingdomsSavedData.get(entry.getKey()).setDirty();
            }
        }
        if (tickCounter % PersonEntityManager.TICK_INTERVAL == 0) {
            for (PersonEntityManager manager : MANAGERS.values()) {
                manager.tick();
            }
        }
        // Construction runs several times a second so builders visibly lay block
        // after block, rather than a course appearing every full pass.
        if (tickCounter % PersonEntityManager.CONSTRUCTION_TICK_INTERVAL == 0) {
            for (PersonEntityManager manager : MANAGERS.values()) {
                manager.tickConstruction();
            }
        }
        // Digging is the one thing that runs every single tick. Block hardness is
        // measured in ticks, so anything coarser than this cannot reproduce the
        // time a player would spend on the same block with the same tool.
        for (PersonEntityManager manager : MANAGERS.values()) {
            manager.tickDigging(tickCounter);
        }
        if (tickCounter % AUDIT_INTERVAL_TICKS == 0) {
            // Ungated, unlike the report below it. A town going on believing in
            // a cottage a creeper flattened is a fault in the game rather than
            // in the log, so it has to be put right for the player who never
            // turns debug commands on.
            razeRuins();
            if (KingdomsConfig.debugCommandsEnabled()) {
                auditTowns();
            }
        }
    }

    /**
     * Lets every town notice the buildings it no longer has.
     *
     * <p>On the audit's beat because it is the audit's measurement — the walls a
     * building has left — and because a minute is the right cadence for it
     * either way: this is the only thing in the mod that removes a building, and
     * it does so after {@link TownAuditor#SWEEPS_BEFORE_WRITTEN_OFF} sweeps in a
     * row agree, which is three minutes of a shell reading as gone.
     */
    private static void razeRuins() {
        for (Map.Entry<ServerLevel, SimWorld> entry : SIMULATIONS.entrySet()) {
            for (var kingdom : entry.getValue().kingdoms()) {
                for (var settlement : kingdom.settlements()) {
                    var razed = TownAuditor.demolishRuins(entry.getKey(), settlement);
                    if (razed.isEmpty()) {
                        continue;
                    }
                    for (var lost : razed) {
                        LOGGER.info("DEMOLISHED {} {} at {}", settlement.name(),
                                lost.blueprintId(), lost.origin());
                    }
                    // The buildings list changed under the save data, and
                    // nothing else marks it dirty: the simulation mutates these
                    // objects in place.
                    KingdomsSavedData.get(entry.getKey()).setDirty();
                }
            }
        }
    }

    /**
     * A settler never ruins the field that feeds them.
     *
     * <p>Vanilla tramples farmland under anything that lands on it, and settlers
     * cross their own fields all day — hopping the irrigation channel was enough.
     * Half a farm went back to dirt with the crops popped into item drops, which
     * the player sees as a field of floating seeds. Players and mobs still
     * trample; the town's own people know where to step.
     */
    private static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof PersonEntity) {
            event.setCanceled(true);
        }
    }

    /** A minute between sweeps; the audit reads a lot of chunk state. */
    private static final int AUDIT_INTERVAL_TICKS = 1200;

    /** What each settlement's building faults looked like last sweep, to keep the log quiet. */
    private static final Map<UUID, Integer> AUDIT_SEEN = new HashMap<>();

    /**
     * Sweeps every town for distress and geometry faults, and logs what it finds.
     *
     * <p>This is the harness's eyes. Every fault the auditor knows was found by a
     * person walking through a town, because nothing in a log betrayed it; this
     * sweep writes those same observations into the log, where a scripted run can
     * grep them. Debug-gated.
     *
     * <p>Three kinds of line on two cadences, because they answer different
     * questions. Vitals go out every sweep: they are a trend, and a trend needs
     * every reading. Building faults go out only when the fault list changes — a
     * crooked wall does not get to fill the log by standing still. Town faults go
     * out every sweep like the vitals, because a famine standing still <em>is</em>
     * the news, and deduplicating it would silence it exactly while the town was
     * dying fastest.
     *
     * <p>The vitals also carry {@code pace}, {@code pacegap} and {@code paceover}:
     * how often the dimension's manager really ran, the worst single gap in that
     * reading, and how much real time it is measured over. They belong on a
     * per-town line because every figure beside them was produced at that
     * cadence — a wall at {@code 96/640} means one thing at sixty passes a
     * minute and something else entirely at twelve.
     *
     * <p>{@code paceover} is worth reading rather than assuming. This sweep is
     * scheduled by game ticks and the pace is measured in real seconds, so on a
     * server behind on its ticks these lines are minutes apart while the pace
     * describes only the last minute of that. It is a reading of the server as
     * the line was printed, not a summary of the interval between lines.
     */
    private static void auditTowns() {
        for (Map.Entry<ServerLevel, SimWorld> entry : SIMULATIONS.entrySet()) {
            // This dimension's manager, not any other's. Everything paced per
            // pass — the wall, the paths, the litter — runs at whatever cadence
            // this reports, so a vitals line that did not carry it was
            // describing work without saying how often anybody was asked to do
            // it. Sixty a minute is the intended rate.
            PersonEntityManager manager = MANAGERS.get(entry.getKey());
            String pace = manager == null ? TickRate.NO_READING : manager.rate().describe();
            for (var kingdom : entry.getValue().kingdoms()) {
                for (var settlement : kingdom.settlements()) {
                    // No skipping a town with nothing loaded. Geometry needs
                    // chunks and is skipped building by building inside the
                    // auditor, but hunger and stores are pure simulation state —
                    // and an unwatched town is precisely the one that starves
                    // quietly, so the guard that used to stand here blinded the
                    // log to every settlement it most needed to describe.
                    int seen = TownAuditor.visibleCount(entry.getKey(), settlement);
                    var faults = TownAuditor.audit(entry.getKey(), settlement);
                    // A town dies of hunger in a handful of minutes, which is
                    // longer than any watched playtest and shorter than a
                    // player's attention — this line is what lets a scripted run
                    // see it coming.
                    int worstHunger = settlement.residents().stream()
                            .mapToInt(p -> p.hunger()).max().orElse(0);
                    long hauls = settlement.residents().stream()
                            .filter(p -> p.haul() != null).count();
                    // Every shelf of the larder, or the numbers lie. A draining
                    // granary once read as a dying town when the traders were
                    // simply moving the food onto market stalls, as designed.
                    int fields = 0;
                    int stalls = 0;
                    for (var building : settlement.buildings()) {
                        String base = com.kingdoms.sim.settlement.BuildPlanner
                                .baseIdOf(building.blueprintId());
                        if (base.endsWith("farm") && !base.endsWith("animal_farm")) {
                            fields += building.foodStored();
                        } else if (base.endsWith("market")) {
                            stalls += building.foodStored();
                        }
                    }
                    int pantries = settlement.households().stream()
                            .mapToInt(h -> h.pantry()).sum();
                    int total = settlement.foodStock() + fields + stalls + pantries;
                    // The verdict, judged on the very figures printed beside it,
                    // and the reserve it turns on: how many steps the larder
                    // still feeds the town for. Hunger only climbs once the food
                    // has gone, so a line carrying hunger alone announces a
                    // famine; carrying the reserve, it predicts one.
                    int reserve = TownAuditor.reserveSteps(total, settlement.population());
                    TownAuditor.Distress distress =
                            TownAuditor.distress(worstHunger, total, settlement.population());
                    LOGGER.info("AUDIT {} vitals stage={} pop={} hunger={} total={} granary={} "
                                    + "fields={} market={} pantries={} hauls={} "
                                    + "reserve={} distress={} seen={} roads={}/{} "
                                    + "coin={} wood={} stone={} wall={}/{} opened={} {}",
                            settlement.name(), settlement.stage().pretty(),
                            settlement.population(), worstHunger,
                            total, settlement.foodStock(), fields, stalls, pantries, hauls,
                            reserve, distress.token(), seen,
                            settlement.paths().segments().size(),
                            settlement.paths().totalLength(),
                            settlement.treasury(), settlement.woodStock(),
                            settlement.stores().get(
                                    com.kingdoms.sim.settlement.TownStores.STONE),
                            settlement.perimeter() == null ? 0
                                    : settlement.perimeter().laid(),
                            settlement.perimeter() == null ? 0
                                    : settlement.perimeter().length(),
                            settlement.paths().openedCount(), pace);

                    List<TownAuditor.Fault> standing = new ArrayList<>();
                    boolean townFault = false;
                    for (TownAuditor.Fault fault : faults) {
                        if (fault.isTownScope()) {
                            townFault = true;
                            LOGGER.info("AUDIT {} {}", settlement.name(), fault.describe());
                        } else {
                            standing.add(fault);
                        }
                    }
                    if (seen == 0) {
                        // Not one building was looked at, so nothing can be said
                        // about the geometry — least of all "clean". Leaving the
                        // fingerprint alone as well is what stops a town emptying
                        // its whole fault list back into the log every time a
                        // player walks out of range and back again.
                        continue;
                    }
                    // The fingerprint covers the building faults and the bare
                    // fact of a town fault, not its text — the text is meant to
                    // change as a famine deepens, and it is logged every sweep
                    // above regardless. But "clean" claims nothing is wrong
                    // anywhere, so a famine starting or ending has to move the
                    // fingerprint or the all-clear would never be said again.
                    int fingerprint = 31 * standing.stream().map(TownAuditor.Fault::describe)
                            .sorted().toList().hashCode() + (townFault ? 1 : 0);
                    Integer before = AUDIT_SEEN.put(settlement.id().value(), fingerprint);
                    if (before != null && before == fingerprint) {
                        continue;
                    }
                    if (standing.isEmpty()) {
                        if (!townFault) {
                            LOGGER.info("AUDIT {} clean", settlement.name());
                        }
                        continue;
                    }
                    for (TownAuditor.Fault fault : standing) {
                        LOGGER.info("AUDIT {} {}", settlement.name(), fault.describe());
                    }
                }
            }
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        KingdomsCommand.register(event.getDispatcher());
    }

    /**
     * Two duties as entities enter the world.
     *
     * <p><strong>Culling stale views:</strong> a view entity can reach disk if the
     * server crashes or a chunk unloads in the same tick a player leaves. The
     * person record is the authority, so any person-tagged entity we did not spawn
     * ourselves this session — including villager-bodied views from older versions
     * — is canceled here and respawned fresh if anyone is watching.
     *
     * <p><strong>Arming creatures:</strong> vanilla's hostiles hunt players and
     * {@code AbstractVillager}, and a citizen is neither, so a town read as empty
     * to everything in the game. Only zombies were told otherwise, by name. Every
     * joining creature is now handed to {@link Quarry}, which decides by class and
     * interface what it should make of a citizen — hunt one, hit back at one, or
     * pay no attention — and so covers whatever a mod has added without knowing
     * anything about it.
     */
    /**
     * Tells a joining player where the towns are, and hands them a needle.
     *
     * <p>The message is the whole feature, not decoration on it. Sites have been
     * computable since worldgen went in and there has never been a way for a
     * player to know that — the first evidence a region held a town was walking
     * into one. Millénaire put a list on the join screen and that one message is
     * most of why its worlds read as inhabited from the first minute.
     *
     * <p>Sent on every login rather than only the first, because "where am I and
     * what is near me" is exactly the question somebody coming back after a week
     * has. The wayfinder is given once; see
     * {@link KingdomsAttachments#WAYFINDER_GIVEN}.
     */
    private static void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player
            .PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GREETINGS.add(new Greeting(player.getUUID(), tickCounter));
        }
    }

    /**
     * A player who has joined and not yet been told where the towns are.
     *
     * @param player who to tell
     * @param since  the tick they joined on, which is the deadline's start
     */
    private record Greeting(UUID player, long since) {
    }

    private static final List<Greeting> GREETINGS = new ArrayList<>();

    /**
     * How long a greeting waits for the spawn towns before giving up on them.
     *
     * <p>Three seconds. The nine go up one a tick, so this is generous by a
     * factor of six and exists only so that a level which cannot settle them —
     * every site refused, a dimension the anchor skips — still gets its message
     * rather than silence.
     */
    private static final int GREETING_GRACE_TICKS = 60;

    /**
     * Says where the towns are, once they are there to point at.
     *
     * <p>Held rather than sent on the login event, and the wait is the point. The
     * spawn towns are raised one a tick from the moment the level loads, and a
     * player joining a single-player world is in before the first of them — so a
     * greeting sent on login would describe all nine as places a town is going
     * to be, which is true and useless. Waiting the handful of ticks it takes
     * them to stand turns the same message into a list of names.
     *
     * <p>Sent on every login rather than only the first, because "where am I and
     * what is near me" is exactly the question somebody coming back after a week
     * has. The wayfinder is given once; see
     * {@link KingdomsAttachments#WAYFINDER_GIVEN}.
     */
    private static void deliverGreetings(net.minecraft.server.MinecraftServer server) {
        if (GREETINGS.isEmpty()) {
            return;
        }
        GREETINGS.removeIf(greeting -> {
            ServerPlayer player = server.getPlayerList().getPlayer(greeting.player());
            if (player == null) {
                return true;   // logged out again before we got to them
            }
            if (!(player.level() instanceof ServerLevel level)) {
                return true;
            }
            if (!com.kingdoms.neoforge.world.WorldgenSettlements.spawnTownsSettled(level)
                    && tickCounter - greeting.since() < GREETING_GRACE_TICKS) {
                return false;   // the towns are still going up; wait for them
            }
            greet(player, level);
            return true;
        });
    }

    private static void greet(ServerPlayer player, ServerLevel level) {
        com.kingdoms.sim.geom.SimPos here = new com.kingdoms.sim.geom.SimPos(
                player.getBlockX(), player.getBlockY(), player.getBlockZ());
        java.util.List<com.kingdoms.neoforge.world.SiteDirectory.Near> near =
                com.kingdoms.neoforge.world.SiteDirectory.near(level, here);
        StringBuilder said = new StringBuilder(
                com.kingdoms.neoforge.world.SiteDirectory.heading(near.size()));
        for (com.kingdoms.neoforge.world.SiteDirectory.Near town : near) {
            said.append("\n").append(com.kingdoms.neoforge.world.SiteDirectory.line(
                    town.what(), town.standing(),
                    town.at().x() - here.x(), town.at().z() - here.z()));
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(said.toString()));

        if (!KingdomsConfig.WORLDGEN_WAYFINDER_ON_JOIN.get()
                || Boolean.TRUE.equals(player.getData(KingdomsAttachments.WAYFINDER_GIVEN))) {
            return;
        }
        player.setData(KingdomsAttachments.WAYFINDER_GIVEN, Boolean.TRUE);
        net.minecraft.world.item.ItemStack wayfinder =
                new net.minecraft.world.item.ItemStack(KingdomsItems.WAYFINDER.get());
        // Already aimed. A compass that has to be clicked before it does
        // anything is a compass that looks broken.
        if (!near.isEmpty()) {
            com.kingdoms.neoforge.item.WayfinderItem.aimAt(wayfinder, level, near.get(0).at());
        }
        if (!player.getInventory().add(wayfinder)) {
            player.drop(wayfinder, false);
        }
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Debug breadcrumb: every item that appears in the world, with what and
        // where. Crops popping into drops was invisible in every log while being
        // the most visible fault in the game — the shape of these lines (a blob,
        // a line, a scatter) is what finally identifies the culprit.
        if (KingdomsConfig.debugCommandsEnabled()
                && event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item) {
            LOGGER.info("ITEMPOP {} x{} at {}", item.getItem().getItem(),
                    item.getItem().getCount(), item.blockPosition().toShortString());
        }
        if (event.getEntity() instanceof Mob creature) {
            Quarry.teach(creature);
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (!living.hasData(KingdomsAttachments.PERSON_ID.get())) {
            return;
        }
        PersonEntityManager manager = MANAGERS.get(level);
        UUID personId = living.getData(KingdomsAttachments.PERSON_ID.get());
        if (manager == null || !manager.owns(personId, living)) {
            event.setCanceled(true);
        }
    }

    /**
     * A view entity died — the person it represented dies with it.
     *
     * <p>Only the body the manager actually owns. The join handler above refuses
     * a body it does not own, and this asks the same question for the same
     * reason: a stale duplicate — one left behind by a chunk that unloaded
     * mid-release, or an old body still standing when the person was embodied
     * afresh — is a prop, and a prop dying must not kill a person who has a
     * perfectly good body elsewhere.
     */
    private static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (!(living.level() instanceof ServerLevel level)) {
            return;
        }
        if (!living.hasData(KingdomsAttachments.PERSON_ID.get())) {
            return;
        }
        PersonEntityManager manager = MANAGERS.get(level);
        if (manager == null) {
            return;
        }
        UUID personId = living.getData(KingdomsAttachments.PERSON_ID.get());
        if (!manager.owns(personId, living)) {
            return;   // a stale body; the person is not in it
        }
        manager.onViewEntityDeath(living);
    }

    /** Access the simulation for a given dimension, or null if the server is not running. */
    /** The view layer for a dimension, or null before the server has started. */
    public static PersonEntityManager managerFor(ServerLevel level) {
        return MANAGERS.get(level);
    }

    public static SimWorld simulationFor(ServerLevel level) {
        return SIMULATIONS.get(level);
    }
}
