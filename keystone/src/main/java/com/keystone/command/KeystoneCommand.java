package com.keystone.command;

import com.keystone.KeystoneComponents;
import com.keystone.api.Blueprints;
import com.keystone.api.LoadedBlueprint;
import com.keystone.api.Placer;
import com.keystone.blueprint.Blueprint;
import com.keystone.net.SaveBlueprintPayload;
import com.keystone.preview.PlacementPreview;
import com.keystone.source.FolderSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Operator commands for inspecting and placing blueprints.
 *
 * <p>Small on purpose. The interesting way to place a blueprint is with the wand
 * and a preview; this is the plumbing underneath, and the thing you reach for
 * when you want to check that a file loads at all.
 */
public final class KeystoneCommand {

    private KeystoneCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("keystone")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))

                .then(Commands.literal("list")
                        .executes(KeystoneCommand::list))

                .then(Commands.literal("info")
                        .then(Commands.argument("blueprint", StringArgumentType.string())
                                .executes(ctx -> info(ctx,
                                        StringArgumentType.getString(ctx, "blueprint")))))

                .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(ctx -> save(ctx,
                                                        StringArgumentType.getString(ctx, "name"),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "to")))))))

                .then(Commands.literal("select")
                        .then(Commands.argument("blueprint", StringArgumentType.string())
                                .executes(ctx -> select(ctx,
                                        StringArgumentType.getString(ctx, "blueprint")))))

                .then(Commands.literal("place")
                        .then(Commands.argument("blueprint", StringArgumentType.string())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> place(ctx,
                                                StringArgumentType.getString(ctx, "blueprint"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                Rotation.NONE))
                                        .then(Commands.argument("rotation", StringArgumentType.word())
                                                .executes(ctx -> place(ctx,
                                                        StringArgumentType.getString(ctx, "blueprint"),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                        rotation(StringArgumentType.getString(ctx, "rotation")))))))));
    }

    private static Rotation rotation(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "90", "cw", "clockwise_90" -> Rotation.CLOCKWISE_90;
            case "180", "clockwise_180" -> Rotation.CLOCKWISE_180;
            case "270", "ccw", "counterclockwise_90" -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<Identifier> found = FolderSource.list();
        if (found.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "No blueprints in " + FolderSource.root()), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                found.size() + " blueprint(s) in " + FolderSource.root() + ":"), false);
        for (Identifier id : found) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + id), false);
        }
        return found.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx, String blueprint) {
        ServerLevel level = ctx.getSource().getLevel();
        Optional<LoadedBlueprint> found = Blueprints.load(
                level, BlockPos.ZERO, Identifier.parse(blueprint));
        if (found.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No blueprint named " + blueprint));
            return 0;
        }
        LoadedBlueprint loaded = found.get();
        Vec3i size = loaded.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                blueprint + ": " + size.getX() + "x" + size.getY() + "x" + size.getZ()
                        + ", " + loaded.blockCount() + " blocks to lay"
                        + " (" + loaded.all().size() + " including air)"), false);
        return loaded.blockCount();
    }

    /**
     * The command form of what the wand does, for operators and scripts.
     *
     * <p>Shares {@link Blueprints#save} with the wand, so a blueprint saved by
     * either route is written identically.
     */
    private static int save(CommandContext<CommandSourceStack> ctx, String name,
                            BlockPos from, BlockPos to) {
        Optional<Identifier> id = SaveBlueprintPayload.sanitize(name);
        if (id.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "Bad name. Use lower-case letters, numbers, underscores and /."));
            return 0;
        }
        try {
            Blueprint saved = Blueprints.save(ctx.getSource().getLevel(), id.get(), from, to);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Saved " + id.get() + " — " + saved.blocks().size() + " blocks, "
                            + saved.size().getX() + "x" + saved.size().getY()
                            + "x" + saved.size().getZ()), true);
            return saved.blocks().size();
        } catch (IOException failed) {
            ctx.getSource().sendFailure(Component.literal("Could not write: " + failed.getMessage()));
            return 0;
        }
    }

    /**
     * Lines a blueprint up on the held wand, or clears it with "none".
     *
     * <p>A command rather than a picker screen: choosing from a list is a screen's
     * worth of work for something an operator does once before placing a dozen
     * copies, and the outline is the part that actually needed building.
     */
    private static int select(CommandContext<CommandSourceStack> ctx, String blueprint) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Only a player can hold a wand."));
            return 0;
        }
        ItemStack wand = PlacementPreview.heldWand(player);
        if (wand == null) {
            ctx.getSource().sendFailure(Component.literal("Hold a blueprint wand first."));
            return 0;
        }
        if (blueprint.equalsIgnoreCase("none")) {
            wand.remove(KeystoneComponents.SELECTED.get());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Wand back to scanning mode."), false);
            return 1;
        }
        Identifier id = Identifier.parse(blueprint);
        if (Blueprints.load(ctx.getSource().getLevel(), BlockPos.ZERO, id).isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No blueprint named " + blueprint));
            return 0;
        }
        wand.set(KeystoneComponents.SELECTED.get(), id);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Lining up " + id + ". Click to place, sneak-click to rotate."), false);
        return 1;
    }

    private static int place(CommandContext<CommandSourceStack> ctx, String blueprint,
                             BlockPos pos, Rotation rotation) {
        ServerLevel level = ctx.getSource().getLevel();
        Optional<LoadedBlueprint> found = Blueprints.load(
                level, pos, Identifier.parse(blueprint), rotation, Mirror.NONE);
        if (found.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No blueprint named " + blueprint));
            return 0;
        }
        LoadedBlueprint loaded = found.get();
        Placer.placeAll(level, loaded, pos);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Placed " + blueprint + " at " + pos.toShortString()
                        + (rotation == Rotation.NONE ? "" : " rotated " + rotation.name())), true);
        return loaded.all().size();
    }
}
