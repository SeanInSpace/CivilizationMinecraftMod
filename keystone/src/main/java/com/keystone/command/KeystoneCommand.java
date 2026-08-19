package com.keystone.command;

import com.keystone.api.Blueprints;
import com.keystone.api.LoadedBlueprint;
import com.keystone.api.Placer;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

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
