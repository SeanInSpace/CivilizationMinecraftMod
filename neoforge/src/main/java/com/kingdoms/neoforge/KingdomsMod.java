package com.kingdoms.neoforge;

import com.kingdoms.neoforge.bridge.NeoForgeWorldBridge;
import com.kingdoms.neoforge.client.KingdomsClient;
import com.kingdoms.neoforge.command.KingdomsCommand;
import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.neoforge.view.PersonEntityManager;
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
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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
        KingdomsItems.ITEMS.register(modBus);
        KingdomsEntities.ENTITY_TYPES.register(modBus);
        modBus.addListener(KingdomsItems::addToCreativeTabs);
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
    public static SimWorld simulationFor(ServerLevel level) {
        return SIMULATIONS.get(level);
    }
}
