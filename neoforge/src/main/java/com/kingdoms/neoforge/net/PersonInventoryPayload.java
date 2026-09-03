package com.kingdoms.neoforge.net;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.client.KingdomsScreens;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Inventory;
import com.kingdoms.sim.person.Person;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;

/**
 * Everything one settler is holding, for the screen that reads their pockets.
 *
 * <p>A snapshot, like the town overview and the warehouse bill: what was true
 * when the screen opened. The simulation steps once every five seconds and a
 * count that shifted while it was being read would be worse than a stale one.
 *
 * <p><strong>Two unrelated loads travel here, and they are not the same thing.</strong>
 * {@link #slots()} is the settler's own {@link Inventory} — bread they will eat,
 * a loaf a player handed over. {@link #carriedMaterial()} and {@link #carriedLoad()}
 * are the building material drawn from the town's stores and walked to a site,
 * which lives in scalar fields on {@link Person} and never enters the inventory.
 * A builder walking sixteen stone to a wall is empty-handed by the first measure
 * and fully laden by the second, so the screen shows them apart.
 *
 * <p>The build load is empty-string-and-zero when there is none, matching how
 * {@code KingdomsCodecs} already writes it to disk — {@code Person} keeps the
 * material null in that state and a stream codec has no null. Read
 * {@code carriedLoad() > 0}, not the string.
 */
public record PersonInventoryPayload(
        String name,
        String profession,
        int hunger,
        List<Inventory.Slot> slots,
        String carriedMaterial,
        int carriedLoad,
        Optional<Errand> errand)
        implements CustomPacketPayload {

    /**
     * Clipped and copied on the way in, so no construction path can build a
     * payload the codec would then refuse to write.
     *
     * <p>The clipping is not tidiness. {@code ByteBufCodecs.stringUtf8} throws
     * {@code EncoderException} on an over-long string, and a custom packet that
     * throws while encoding is not skippable — netty drops the connection, so a
     * settler named with a hundred characters by command would disconnect
     * whoever sneak-clicked it. The chat line this replaced had no such cap.
     *
     * <p>The slot list is trimmed to {@link Inventory#SLOTS} for the mirror of
     * that reason: the codec's list cap bites on <em>read</em>, so an over-long
     * list encodes cleanly and then disconnects the client decoding it.
     */
    public PersonInventoryPayload {
        name = clip(name, MAX_NAME);
        profession = clip(profession, MAX_WORD);
        slots = slots.stream()
                .limit(Inventory.SLOTS)
                .map(slot -> new Inventory.Slot(clip(slot.itemId(), MAX_ID), slot.count()))
                .toList();
        carriedMaterial = clip(carriedMaterial, MAX_WORD);
    }

    /**
     * The haul somebody is running, flattened for the wire.
     *
     * <p>{@code carried} is zero on the outward leg — a hauler walks to the
     * source empty-handed — so the two legs read differently on the screen and
     * "empty to FARM" is not shown as though a load were already on their back.
     * The store kinds travel as their enum names; the client formats them.
     *
     * <p>The resource is clipped for the same reason the payload's own strings
     * are: the town ledger lets anything be stored under any name.
     */
    public record Errand(String resource, String from, String to, int carried, int requested) {
        public Errand {
            resource = clip(resource, MAX_WORD);
            from = clip(from, MAX_WORD);
            to = clip(to, MAX_WORD);
        }
    }

    /**
     * Characters, which is what {@code Utf8String.write} counts: it refuses on
     * {@code value.length() > maxLength} and separately on an encoded size above
     * four bytes per allowed character, and no string short enough for the first
     * can fail the second. Cutting one char short of a surrogate pair keeps a
     * name in an alphabet that needs them from ending in half a letter.
     */
    private static String clip(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(max - 1)) ? max - 1 : max;
        return text.substring(0, end);
    }

    public static final Type<PersonInventoryPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "person_inventory"));

    /** Generous for a custom-named settler, short enough not to be a payload attack. */
    private static final int MAX_NAME = 96;
    private static final int MAX_ID = 96;

    /** Profession, resource and store names are all single words from an enum or the ledger. */
    private static final int MAX_WORD = 32;

    private static final StreamCodec<RegistryFriendlyByteBuf, Inventory.Slot> SLOT_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_ID), Inventory.Slot::itemId,
                    ByteBufCodecs.VAR_INT, Inventory.Slot::count,
                    Inventory.Slot::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Errand> ERRAND_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_WORD), Errand::resource,
                    ByteBufCodecs.stringUtf8(MAX_WORD), Errand::from,
                    ByteBufCodecs.stringUtf8(MAX_WORD), Errand::to,
                    ByteBufCodecs.VAR_INT, Errand::carried,
                    ByteBufCodecs.VAR_INT, Errand::requested,
                    Errand::new);

    // Order here is load-bearing: the terminal ::new is the canonical record
    // constructor, so each getter must sit where its component does.
    //
    // The list is capped at Inventory.SLOTS because that is genuinely the most a
    // settler can hold — the cap is the simulation's own, not a guess.
    public static final StreamCodec<RegistryFriendlyByteBuf, PersonInventoryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_NAME), PersonInventoryPayload::name,
                    ByteBufCodecs.stringUtf8(MAX_WORD), PersonInventoryPayload::profession,
                    ByteBufCodecs.VAR_INT, PersonInventoryPayload::hunger,
                    SLOT_CODEC.apply(ByteBufCodecs.list(Inventory.SLOTS)),
                    PersonInventoryPayload::slots,
                    ByteBufCodecs.stringUtf8(MAX_WORD), PersonInventoryPayload::carriedMaterial,
                    ByteBufCodecs.VAR_INT, PersonInventoryPayload::carriedLoad,
                    ByteBufCodecs.optional(ERRAND_CODEC), PersonInventoryPayload::errand,
                    PersonInventoryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Reads one settler into a payload.
     *
     * @param name what to call them on screen — the entity's custom name if it has
     *             one, which is not necessarily {@link Person#name()}
     */
    public static PersonInventoryPayload of(String name, Person person) {
        HaulTask haul = person.haul();
        Optional<Errand> errand = haul == null
                ? Optional.empty()
                : Optional.of(new Errand(haul.resource(), haul.fromStore().name(),
                        haul.toStore().name(), haul.carried(), haul.requested()));
        String material = person.carriedMaterial();
        return new PersonInventoryPayload(
                name,
                person.profession().name(),
                person.hunger(),
                List.copyOf(person.inventory().slots()),
                material == null ? "" : material,
                person.carriedLoad(),
                errand);
    }

    public static void handle(PersonInventoryPayload payload, IPayloadContext context) {
        KingdomsScreens.openPersonInventory(payload);
    }
}
