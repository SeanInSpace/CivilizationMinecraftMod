package com.civilization.neoforge;

import com.mojang.serialization.Codec;
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
public final class CivilizationAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CivilizationMod.MOD_ID);

    /**
     * Links a view villager back to the {@code Person} record it represents.
     *
     * <p>Serialized on purpose: if a view entity ever reaches disk (crash, chunk
     * unload race), the tag survives the round-trip, and the entity-join hook
     * recognizes and culls the stale copy instead of letting it duplicate. The
     * person record is the authority; the entity is always disposable.
     */
    public static final Supplier<AttachmentType<UUID>> PERSON_ID = ATTACHMENTS.register(
            "person_id",
            () -> AttachmentType.builder(() -> Util.NIL_UUID)
                    .serialize(UUIDUtil.CODEC.fieldOf("value"))
                    .build());

    /**
     * Whether this player has already been handed a wayfinder.
     *
     * <p>"First join" is not a thing the login event knows — it fires every time
     * anybody connects — so it has to be remembered on the player. Serialized,
     * because the whole point is that the second login is different from the
     * first; and copied across death, because dropping your wayfinder in lava is
     * a loss the player should feel rather than a way to farm another.
     *
     * <p>Nothing reads this but the join hook, and a player who deliberately
     * throws theirs away can craft another: the flag says they were <em>given</em>
     * one, not that they have one.
     */
    public static final Supplier<AttachmentType<Boolean>> WAYFINDER_GIVEN =
            ATTACHMENTS.register("wayfinder_given",
                    () -> AttachmentType.builder(() -> Boolean.FALSE)
                            .serialize(Codec.BOOL.fieldOf("value"))
                            .copyOnDeath()
                            .build());

    private CivilizationAttachments() {
    }
}
