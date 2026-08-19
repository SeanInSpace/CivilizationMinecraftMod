package com.keystone.item;

import com.keystone.KeystoneComponents;
import com.keystone.blueprint.Scanner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.BiConsumer;

/**
 * Marks out a region and saves it as a blueprint.
 *
 * <p>Click a block for the first corner, sneak-click for the second, then click
 * the air to name and save what is between them. The selection lives on the item
 * itself, so it survives being put away and picked up again.
 *
 * <p>No size limit — that is the point. Vanilla's structure block stops at 48
 * blocks per axis, which is what rules it out for anything the size of a keep.
 */
public final class BlueprintWandItem extends Item {

    /** Big enough for a castle; small enough that a slip of the hand is caught. */
    public static final long WARN_ABOVE = 2_000_000L;

    /**
     * Opens the naming screen. Installed by the client at startup and left as a
     * no-op on a server, so that nothing here ever names a client-only class —
     * a dedicated server would fail to load the moment it tried to link one.
     */
    public static BiConsumer<BlockPos, BlockPos> saveScreen = (a, b) -> {
    };

    public BlueprintWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack wand = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        boolean second = context.isSecondaryUseActive();
        wand.set(second ? KeystoneComponents.CORNER_B.get()
                : KeystoneComponents.CORNER_A.get(), clicked);

        report(player, wand, second ? "Second corner" : "First corner", clicked);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack wand = player.getItemInHand(hand);
        BlockPos a = wand.get(KeystoneComponents.CORNER_A.get());
        BlockPos b = wand.get(KeystoneComponents.CORNER_B.get());

        if (a == null || b == null) {
            if (!level.isClientSide()) {
                player.sendSystemMessage(Component.literal(
                        "Mark two corners first: click a block, then sneak-click another."));
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            // The naming screen is client-only; the server hears about the
            // result when the save payload arrives.
            saveScreen.accept(a, b);
        }
        return InteractionResult.SUCCESS;
    }

    private static void report(Player player, ItemStack wand, String which, BlockPos at) {
        BlockPos a = wand.get(KeystoneComponents.CORNER_A.get());
        BlockPos b = wand.get(KeystoneComponents.CORNER_B.get());

        StringBuilder message = new StringBuilder(which + " at " + at.toShortString());
        if (a != null && b != null) {
            long volume = Scanner.volumeOf(a, b);
            message.append(" — region ")
                    .append(Math.abs(a.getX() - b.getX()) + 1).append("x")
                    .append(Math.abs(a.getY() - b.getY()) + 1).append("x")
                    .append(Math.abs(a.getZ() - b.getZ()) + 1)
                    .append(volume > WARN_ABOVE ? " (very large)" : "")
                    .append(". Right-click the air to save.");
        }
        player.sendSystemMessage(Component.literal(message.toString()));
    }
}
