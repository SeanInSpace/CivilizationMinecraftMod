package com.kingdoms.neoforge.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Item ids off the wire, turned into something a screen can draw.
 *
 * <p>The simulation names items as plain strings so it can stay loader-free, and
 * every screen that shows one has to look it up. That lookup has to be
 * survivable: a payload's ids were written by a server that may have items this
 * client has not got, or a datapack word that is not an id at all. Both answers
 * are a barrier rather than an exception, so an unknown line shows as an unknown
 * line instead of taking the screen down.
 *
 * <p>{@code Identifier.parse} throws on a malformed id rather than returning
 * empty, which is the half the screens used to miss.
 */
public final class ItemIcons {

    private ItemIcons() {
    }

    public static Item of(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null
                ? Items.BARRIER
                : BuiltInRegistries.ITEM.getOptional(parsed).orElse(Items.BARRIER);
    }
}
