package com.keystone;

import com.keystone.api.Blueprints;
import com.keystone.command.KeystoneCommand;
import com.keystone.net.KeystoneNetwork;
import com.keystone.preview.PlacementPreview;
import com.keystone.source.DatapackSource;
import com.keystone.source.FolderSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for Keystone, a standalone blueprint mod.
 *
 * <p>Keystone reads and writes vanilla structure NBT at any size — the 48-block
 * limit lives in the structure <em>block</em>, not in the format — transforms it,
 * and serves it to other mods through {@link Blueprints}. It has no dependency
 * on Kingdoms or on any other mod.
 */
@Mod(KeystoneMod.MOD_ID)
public final class KeystoneMod {

    public static final String MOD_ID = "keystone";

    public static final Logger LOG = LoggerFactory.getLogger("Keystone");

    public KeystoneMod(IEventBus modBus, ModContainer container) {
        // Order matters only through priority: a blueprint you scanned yourself
        // beats one shipped in a datapack of the same name.
        Blueprints.register(new FolderSource());
        Blueprints.register(new DatapackSource());

        KeystoneItems.ITEMS.register(modBus);
        KeystoneComponents.COMPONENTS.register(modBus);
        KeystoneTabs.TABS.register(modBus);
        modBus.addListener(KeystoneNetwork::register);

        NeoForge.EVENT_BUS.addListener(KeystoneMod::onRegisterCommands);
        // Datapack blueprints are cached after first read, so a reload has to
        // drop them or /reload would quietly keep serving the old structure.
        NeoForge.EVENT_BUS.addListener(KeystoneMod::onReload);
        NeoForge.EVENT_BUS.addListener(KeystoneMod::onPlayerTick);

        LOG.info("Keystone loaded — blueprint services available");
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        KeystoneCommand.register(event.getDispatcher());
    }

    /**
     * Fired every time the server gathers reload listeners, which is exactly when
     * a cached datapack blueprint may have gone stale. Clearing here rather than
     * registering a listener of our own keeps this to one line and one concept.
     */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlacementPreview.tick(player);
        }
    }

    private static void onReload(AddServerReloadListenersEvent event) {
        Blueprints.clearCache();
    }
}
