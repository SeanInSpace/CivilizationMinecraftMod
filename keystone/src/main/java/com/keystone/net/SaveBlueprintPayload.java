package com.keystone.net;

import com.keystone.KeystoneComponents;
import com.keystone.KeystoneItems;
import com.keystone.KeystoneMod;
import com.keystone.api.Blueprints;
import com.keystone.blueprint.Blueprint;
import com.keystone.source.FolderSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * "Save what my wand has selected, under this name."
 *
 * <p>The client sends only a name. Everything else — which region, whether the
 * player may do this at all — is re-derived on the server from the wand the
 * player is actually holding, because a payload is a claim by a client and not
 * evidence of anything.
 */
public record SaveBlueprintPayload(String name) implements CustomPacketPayload {

    public static final Type<SaveBlueprintPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KeystoneMod.MOD_ID, "save_blueprint"));

    /** Long enough for "castles/great_keep", short enough not to be a payload attack. */
    private static final int MAX_NAME = 96;

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveBlueprintPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_NAME), SaveBlueprintPayload::name,
                    SaveBlueprintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveBlueprintPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack wand = player.getMainHandItem();
        if (!wand.is(KeystoneItems.BLUEPRINT_WAND.get())) {
            wand = player.getOffhandItem();
        }
        if (!wand.is(KeystoneItems.BLUEPRINT_WAND.get())) {
            player.sendSystemMessage(Component.literal("You are not holding a blueprint wand."));
            return;
        }

        BlockPos a = wand.get(KeystoneComponents.CORNER_A.get());
        BlockPos b = wand.get(KeystoneComponents.CORNER_B.get());
        if (a == null || b == null) {
            player.sendSystemMessage(Component.literal("That wand has no region marked."));
            return;
        }

        Optional<Identifier> id = sanitize(payload.name());
        if (id.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "Bad name. Use lower-case letters, numbers, underscores and /."));
            return;
        }
        Optional<Path> file = FolderSource.fileFor(id.get());
        if (file.isEmpty()) {
            player.sendSystemMessage(Component.literal("That name escapes the blueprint folder."));
            return;
        }

        ServerLevel level = player.level() instanceof ServerLevel server ? server : null;
        if (level == null) {
            return;
        }

        Blueprint blueprint;
        try {
            blueprint = Blueprints.save(level, id.get(), a, b);
        } catch (IOException failed) {
            KeystoneMod.LOG.error("Could not save blueprint {}", id.get(), failed);
            player.sendSystemMessage(Component.literal("Could not write that file — see the log."));
            return;
        }
        player.sendSystemMessage(Component.literal(
                "Saved " + id.get() + " — " + blueprint.blocks().size()
                        + " blocks, " + blueprint.size().getX() + "x"
                        + blueprint.size().getY() + "x" + blueprint.size().getZ()));
    }

    /**
     * Turns typed text into a safe identifier.
     *
     * <p>A bare name lands in Keystone's own namespace; writing
     * {@code kingdoms:house} instead saves into that mod's, which is how a
     * scanned building comes to replace one a settlement would otherwise build
     * for itself.
     *
     * <p>Rejects rather than repairs anything containing {@code ..}: a name is a
     * few keystrokes to retype, and silently rewriting a path traversal into
     * something that merely looks harmless is how these things get missed.
     */
    public static Optional<Identifier> sanitize(String raw) {
        String text = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (text.isEmpty() || text.length() > MAX_NAME || text.contains("..")) {
            return Optional.empty();
        }

        String namespace = KeystoneMod.MOD_ID;
        String path = text;
        int colon = text.indexOf(':');
        if (colon >= 0) {
            namespace = text.substring(0, colon);
            path = text.substring(colon + 1);
            if (!namespace.matches("[a-z0-9_.-]+")) {
                return Optional.empty();
            }
        }
        if (path.isEmpty() || path.startsWith("/") || path.endsWith("/")
                || !path.matches("[a-z0-9_/-]+")) {
            return Optional.empty();
        }
        return Optional.of(Identifier.fromNamespaceAndPath(namespace, path));
    }
}
