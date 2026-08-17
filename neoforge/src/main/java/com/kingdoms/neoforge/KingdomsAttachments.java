package com.kingdoms.neoforge;

import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Util;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Data attachments — the NeoForge mechanism for hanging custom data on entities
 * we do not own (here, vanilla villagers).
 */
public final class KingdomsAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, KingdomsMod.MOD_ID);

    /**
     * Links a view villager back to the {@code Person} record it represents.
     *
     * <p>Serialized on purpose: if a view entity ever reaches disk (crash, chunk
     * unload race), the tag survives the round-trip, and the entity-join hook
     * recognises and culls the stale copy instead of letting it duplicate. The
     * person record is the authority; the entity is always disposable.
     */
    public static final Supplier<AttachmentType<UUID>> PERSON_ID = ATTACHMENTS.register(
            "person_id",
            () -> AttachmentType.builder(() -> Util.NIL_UUID)
                    .serialize(UUIDUtil.CODEC.fieldOf("value"))
                    .build());

    private KingdomsAttachments() {
    }
}
