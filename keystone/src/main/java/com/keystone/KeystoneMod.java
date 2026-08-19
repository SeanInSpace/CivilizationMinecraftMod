package com.keystone;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for Keystone, a standalone blueprint mod.
 *
 * <p>Keystone reads and writes vanilla structure NBT at any size — the 48-block
 * limit lives in the structure <em>block</em>, not in the format — transforms it,
 * and serves it to other mods through {@code BlueprintSource}. It has no
 * dependency on Kingdoms or on any other mod.
 */
@Mod(KeystoneMod.MOD_ID)
public final class KeystoneMod {

    public static final String MOD_ID = "keystone";

    public static final Logger LOG = LoggerFactory.getLogger("Keystone");

    public KeystoneMod(IEventBus modBus, ModContainer container) {
        LOG.info("Keystone loaded — blueprint services available");
    }
}
