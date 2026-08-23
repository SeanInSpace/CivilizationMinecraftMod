package com.kingdoms.neoforge;

import com.kingdoms.neoforge.bridge.NeoForgeWorldBridge;
import com.kingdoms.neoforge.client.KingdomsClient;
import com.kingdoms.neoforge.command.KingdomsCommand;
import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.neoforge.net.KingdomsNetwork;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.neoforge.view.PersonEntityManager;
import com.kingdoms.neoforge.world.StoreSync;
import com.kingdoms.neoforge.world.TownAuditor;
import com.kingdoms.sim.world.SimSettings;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
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
 * tick, and otherwise stays out of the way. All behaviour lives in {@code :common}.
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
        LOGGER.info("Initialised {} dimension simulation(s)", SIMULATIONS.size());
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
        StoreSync.forget();
    }

    /** Our own tick count — the level clock is not trusted for cadence (it can freeze). */
    private static long tickCounter;

    private static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
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
        if (tickCounter % AUDIT_INTERVAL_TICKS == 0 && KingdomsConfig.debugCommandsEnabled()) {
            auditTowns();
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
     */
    private static void auditTowns() {
        for (Map.Entry<ServerLevel, SimWorld> entry : SIMULATIONS.entrySet()) {
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
                                    + "reserve={} distress={} seen={} roads={}/{}",
                            settlement.name(), settlement.stage().pretty(),
                            settlement.population(), worstHunger,
                            total, settlement.foodStock(), fields, stalls, pantries, hauls,
                            reserve, distress.token(), seen,
                            settlement.paths().segments().size(),
                            settlement.paths().totalLength());

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
     * — is cancelled here and respawned fresh if anyone is watching.
     *
     * <p><strong>Arming hostiles:</strong> zombies hunt vanilla villagers through a
     * hardcoded type check they will never extend to our people. Every joining
     * zombie is given a targeting goal for {@link PersonEntity}, restoring vanilla
     * menace against the town.
     */
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
        if (event.getEntity() instanceof Zombie zombie) {
            zombie.targetSelector.addGoal(3,
                    new NearestAttackableTargetGoal<>(zombie, PersonEntity.class, true));
            return;
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

    /** A view entity died — the person it represented dies with it. */
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
        if (manager != null) {
            manager.onViewEntityDeath(living);
        }
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
