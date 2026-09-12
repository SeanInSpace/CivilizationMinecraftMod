package com.civilization.neoforge.command;

import com.keystone.api.Blueprints;
import com.keystone.api.LoadedBlueprint;
import com.keystone.api.Placer;
import com.keystone.blueprint.Blueprint;
import com.keystone.blueprint.Scanner;
import com.keystone.source.FolderSource;
import com.keystone.source.WorldSource;
import com.civilization.neoforge.CivilizationMod;
import com.civilization.neoforge.world.AuthoredReading;
import com.civilization.neoforge.world.BlueprintPlacer;
import com.civilization.neoforge.world.ScanRegion;
import com.civilization.sim.settlement.BlueprintCheck;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.CropBlock;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /civ blueprint} — building a house in creative and handing it to a town.
 *
 * <p>The whole authoring loop lives here, and it is deliberately four verbs:
 *
 * <ul>
 *   <li>{@code region} marks out the box you are about to take, and the
 *       surveyor's lamp draws it. This is the only one that is not strictly
 *       necessary, and it is the one that makes the rest usable — a scan region
 *       has no appearance otherwise, so getting it right was guesswork until the
 *       file came back the wrong size.</li>
 *   <li>{@code scan} takes it, writes it into this world's blueprint folder,
 *       and records the two things a bare box cannot say: the cell the building
 *       lines up by, and which way its front points.</li>
 *   <li>{@code check} reads the file back and holds it against the same tables
 *       the drawn buildings are held to. Run this before you trust it.</li>
 *   <li>{@code place} puts it down so you can walk round it.</li>
 * </ul>
 *
 * <p>Kept out of {@link CivilizationCommand} because it is a tool rather than a
 * debug window into the simulation. Everything else under {@code /civ} exists to
 * make the town observable; this exists to let somebody change what the town
 * builds.
 */
public final class BlueprintCommands {

    private BlueprintCommands() {
    }

    /**
     * How many blocks a scan will take without complaint: a quarter of a million.
     *
     * <p>Sixty-odd cubed, which is far bigger than any building in the mod and
     * far smaller than the region somebody selects by mistyping a coordinate.
     * The scanner itself has no limit and should not have one — the whole reason
     * it exists is that a keep or a curtain wall does not fit in a structure
     * block — so the limit belongs here, on the command a person types, where it
     * can be explained.
     */
    private static final long SCAN_WARN_VOLUME = 250_000;

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("blueprint")

                .then(Commands.literal("list")
                        .executes(BlueprintCommands::list))

                .then(Commands.literal("region")
                        .then(Commands.literal("clear")
                                .executes(BlueprintCommands::regionClear))
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(ctx -> region(ctx,
                                                BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "to"))))))

                .then(Commands.literal("scan")
                        .then(Commands.argument("name", IdentifierArgument.id())
                                .executes(ctx -> scanPending(ctx, nameOf(ctx)))
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(ctx -> scan(ctx, nameOf(ctx),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "to"),
                                                        null))
                                                .then(Commands.argument("anchor",
                                                                BlockPosArgument.blockPos())
                                                        .executes(ctx -> scan(ctx, nameOf(ctx),
                                                                BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                                BlockPosArgument.getLoadedBlockPos(ctx, "to"),
                                                                BlockPosArgument.getLoadedBlockPos(ctx, "anchor"))))))))

                .then(Commands.literal("check")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> check(ctx,
                                        StringArgumentType.getString(ctx, "name")))))

                .then(Commands.literal("place")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> place(ctx,
                                        StringArgumentType.getString(ctx, "name")))));
    }

    /**
     * The name typed after {@code scan}, in the form {@link #idOf} expects.
     *
     * <p>A blueprint name is a namespaced path — {@code civilization:norman/house}
     * is what {@code /civ blueprint list} prints — and a Brigadier unquoted
     * string stops dead at the colon, so these three verbs could only be given
     * such a name in quotes. {@code check} and {@code place} take the rest of the
     * line and are done with it, but {@code scan} has two corner positions after
     * the name, so it cannot be greedy. It reads an identifier instead, which is
     * the vanilla argument type for exactly this shape and allows the colon and
     * the slash.
     *
     * <p>The one seam: an identifier with no namespace is {@code minecraft:} by
     * the game's rules, and a bare {@code great_hut} has always meant this mod's
     * namespace here. So a {@code minecraft} namespace is handed on without one
     * and {@link #idOf} applies the usual default. Nothing is lost — the mod does
     * not write blueprints into the game's own namespace and never will.
     */
    private static String nameOf(CommandContext<CommandSourceStack> ctx) {
        Identifier typed = IdentifierArgument.getId(ctx, "name");
        return Identifier.DEFAULT_NAMESPACE.equals(typed.getNamespace())
                ? typed.getPath()
                : typed.toString();
    }

    // --- the region -----------------------------------------------------------

    private static int region(CommandContext<CommandSourceStack> ctx, BlockPos from, BlockPos to) {
        ServerPlayer player = playerOf(ctx);
        if (player == null) {
            return 0;
        }
        ScanRegion.Region marked = new ScanRegion.Region(from, to, quarterOf(player));
        ScanRegion.set(player, marked);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Region marked: " + marked.width() + "x" + marked.height() + "x"
                        + marked.depth() + " (" + marked.volume() + " blocks), front toward "
                        + sideName(marked.facing())
                        + ". Hold the surveyor's lamp to see it."), false);
        return 1;
    }

    private static int regionClear(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOf(ctx);
        if (player == null) {
            return 0;
        }
        ScanRegion.clear(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Region cleared."), false);
        return 1;
    }

    // --- scanning -------------------------------------------------------------

    private static int scanPending(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = playerOf(ctx);
        if (player == null) {
            return 0;
        }
        ScanRegion.Region marked = ScanRegion.of(player);
        if (marked == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "No region marked. Use /civ blueprint region <from> <to> first,"
                            + " or give the two corners here."));
            return 0;
        }
        return scan(ctx, name, marked.from(), marked.to(), null);
    }

    /**
     * Takes the region and writes it into this world's blueprint folder.
     *
     * <p>Two things go into the file beyond the blocks, and they are the reason
     * this is not just {@code /keystone save}:
     *
     * <ul>
     *   <li>The <strong>anchor</strong>: the cell that will be put on the build
     *       plot. Given explicitly, or taken as the building post if there is
     *       one in the region — which is nearly always the right answer, because
     *       the post is what a plot <em>is</em> as far as a town is concerned.
     *       Failing both, the middle of the floor.</li>
     *   <li>The <strong>front</strong>: the way the player is looking. Stand
     *       with your back to the building and face the way its door faces.</li>
     * </ul>
     */
    private static int scan(CommandContext<CommandSourceStack> ctx, String name,
                            BlockPos from, BlockPos to, BlockPos anchor) {
        ServerPlayer player = playerOf(ctx);
        if (player == null) {
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        Optional<Identifier> id = idOf(name);
        if (id.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "Bad name. Use lower-case letters, numbers, underscores and /,"
                            + " e.g. norman/cottage."));
            return 0;
        }
        Optional<Path> file = WorldSource.fileFor(level, id.get());
        if (file.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "That name escapes the blueprint folder."));
            return 0;
        }
        long volume = Scanner.volumeOf(from, to);
        if (volume > SCAN_WARN_VOLUME) {
            ctx.getSource().sendFailure(Component.literal(
                    "That region is " + volume + " blocks, which is almost certainly a"
                            + " mistyped coordinate. The limit is " + SCAN_WARN_VOLUME + "."));
            return 0;
        }

        int facing = quarterOf(player);
        BlockPos lineUp = anchor != null ? anchor : postIn(level, from, to);
        Blueprint scanned = Scanner.scan(level, from, to, lineUp,
                new Blueprint.Meta(facing, cropsIn(level, from, to)));
        try {
            Blueprints.saveTo(scanned, file.get());
        } catch (IOException failed) {
            ctx.getSource().sendFailure(Component.literal(
                    "Could not write: " + failed.getMessage()));
            return 0;
        }
        // Plans made before this file existed were made against the old shape.
        BlueprintPlacer.clearPlanCache();

        Vec3i size = scanned.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Saved " + id.get() + " — " + size.getX() + "x" + size.getY() + "x"
                        + size.getZ() + ", " + scanned.blocks().size() + " blocks, front toward "
                        + sideName(facing) + ", lined up on "
                        + scanned.anchor().toShortString()
                        + (anchor == null && lineUp != null ? " (its building post)" : "")
                        + ". Now run /civ blueprint check " + name), true);
        return scanned.blocks().size();
    }

    /**
     * The building post in a scanned region, or null.
     *
     * <p>Looked for because it is almost always the cell the author means. A
     * town puts a building on a plot by putting one <em>cell</em> of it on the
     * plot, and the post is the block that names the building — it is where a
     * player clicks to read what this is, and where the plan believes the
     * building is. Guessing the middle of the floor instead is what leaves an
     * imported building sitting beside its plot.
     */
    private static BlockPos postIn(ServerLevel level, BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).getBlock()
                    instanceof com.civilization.neoforge.block.BuildingPostBlock) {
                return pos.immutable();
            }
        }
        return null;
    }

    /** How many crop blocks are standing in the region, for a field. */
    private static int cropsIn(ServerLevel level, BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
        int crops = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).getBlock() instanceof CropBlock) {
                crops++;
            }
        }
        return crops;
    }

    // --- checking -------------------------------------------------------------

    /**
     * Reads a file back and says everything wrong with it.
     *
     * <p>Checked at facing nought, which is the frame the size and bed tables are
     * written in. The building will be turned to face whatever street it ends up
     * on, and every rule here is about the building rather than about the street.
     */
    private static int check(CommandContext<CommandSourceStack> ctx, String name) {
        ServerLevel level = ctx.getSource().getLevel();
        Optional<Identifier> id = idOf(name);
        if (id.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Bad name."));
            return 0;
        }
        Optional<LoadedBlueprint> found = Blueprints.loadFacing(
                level, BlockPos.ZERO, id.get(), 0);
        if (found.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No blueprint named " + id.get()));
            return 0;
        }
        String path = id.get().getPath();
        AuthoredReading.Reading reading = AuthoredReading.read(path, found.get(), 0);
        BlueprintCheck.Survey survey = reading.survey();
        List<BlueprintCheck.Finding> findings = BlueprintCheck.of(survey);

        ctx.getSource().sendSuccess(() -> Component.literal(
                id.get() + ": " + survey.width() + "x" + survey.height() + "x"
                        + survey.depth() + ", " + survey.beds() + " bed(s), "
                        + survey.crops() + " crop(s), "
                        + (survey.post() ? "a post" : "no post") + ", "
                        + (survey.door() ? "a way in" : "NO WAY IN")), false);
        if (findings.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Nothing to report. This file can stand in for the drawn one."), false);
            return 1;
        }
        for (BlueprintCheck.Finding finding : findings) {
            String line = "  " + finding.severity() + ": " + finding.message();
            if (finding.severity().isFault()) {
                ctx.getSource().sendFailure(Component.literal(line));
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
            }
        }
        return BlueprintCheck.passes(findings) ? 1 : 0;
    }

    // --- placing --------------------------------------------------------------

    /**
     * Puts a file down where the player stands, for a look at it.
     *
     * <p>Anchored the way a town anchors it — the file's own anchor cell lands
     * on the player's feet — and turned so its front faces the way the player is
     * looking. So what you walk round is what a town would raise, which is the
     * only thing a review placement is for.
     */
    private static int place(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = playerOf(ctx);
        if (player == null) {
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        Optional<Identifier> id = idOf(name);
        if (id.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Bad name."));
            return 0;
        }
        int facing = quarterOf(player);
        Optional<LoadedBlueprint> found = Blueprints.loadFacing(
                level, player.blockPosition(), id.get(), facing);
        if (found.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No blueprint named " + id.get()));
            return 0;
        }
        LoadedBlueprint loaded = found.get();
        BlockPos at = player.blockPosition();
        BlockPos origin = at.offset(-loaded.anchor().getX(), 0, -loaded.anchor().getZ());
        Placer.placeAll(level, loaded, origin);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Placed " + id.get() + " at " + at.toShortString()
                        + ", front toward " + sideName(facing)), true);
        return loaded.all().size();
    }

    // --- listing --------------------------------------------------------------

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        List<Identifier> world = WorldSource.list(level, CivilizationMod.MOD_ID);
        List<Identifier> shared = new ArrayList<>(FolderSource.list());

        ctx.getSource().sendSuccess(() -> Component.literal(
                "This world (" + WorldSource.root(level, CivilizationMod.MOD_ID) + "): "
                        + (world.isEmpty() ? "nothing" : world.size() + " file(s)")), false);
        for (Identifier id : world) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + id), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Shared (" + FolderSource.root() + "): "
                        + (shared.isEmpty() ? "nothing" : shared.size() + " file(s)")), false);
        for (Identifier id : shared) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + id), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Files shipped in the mod jar and in datapacks are not listed here;"
                        + " a world file of the same name overrides them."), false);
        return world.size() + shared.size();
    }

    // --- shared -------------------------------------------------------------

    private static ServerPlayer playerOf(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            return player;
        }
        ctx.getSource().sendFailure(Component.literal(
                "Only a player can do this: it reads which way you are facing."));
        return null;
    }

    /**
     * A blueprint name as an id, or empty if it is not one we will write to.
     *
     * <p>Narrow on purpose. The path becomes a file path, and an identifier's
     * path may legally contain dots — {@code ../../server.properties} parses
     * perfectly well. The folder sources check the resolved path against their
     * root as well, so this is the outer of two doors rather than the only one.
     */
    private static Optional<Identifier> idOf(String name) {
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty() || trimmed.contains("..")) {
            return Optional.empty();
        }
        String namespace = CivilizationMod.MOD_ID;
        String path = trimmed;
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            namespace = trimmed.substring(0, colon);
            path = trimmed.substring(colon + 1);
        }
        if (!path.matches("[a-z0-9_]+(/[a-z0-9_]+)*")
                || !namespace.matches("[a-z0-9_.-]+")) {
            return Optional.empty();
        }
        return Optional.of(Identifier.fromNamespaceAndPath(namespace, path));
    }

    /**
     * Which way the player is looking, in the quarter turns the simulation counts.
     *
     * <p>Nought is {@code +z}, which is where every drawn building puts its door,
     * and each turn is a quarter clockwise. The same convention
     * {@code Building.doorstep} reads and {@code Beds.turned} applies, said once
     * here so a command and a table cannot drift apart about it.
     */
    private static int quarterOf(ServerPlayer player) {
        return quarterOf(player.getDirection());
    }

    static int quarterOf(Direction direction) {
        return switch (direction) {
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    private static String sideName(int facing) {
        return switch (Math.floorMod(facing, 4)) {
            case 1 -> "west";
            case 2 -> "north";
            case 3 -> "east";
            default -> "south";
        };
    }
}
