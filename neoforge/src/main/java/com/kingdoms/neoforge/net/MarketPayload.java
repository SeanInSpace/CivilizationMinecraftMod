package com.kingdoms.neoforge.net;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.client.KingdomsScreens;
import com.kingdoms.sim.economy.Market;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * What the stall is offering, and why.
 *
 * <p>A snapshot, like the town overview and the warehouse bill. Unlike them it
 * is sent again after every trade, because a market a player has just changed
 * and which goes on showing the old prices is worse than no screen — the second
 * lot of grain is not the same price as the first when the first one was what
 * stopped the town starving.
 *
 * <p>Each row carries {@link Market.Reason}. That single field is the entire
 * argument for this screen existing rather than a merchant's: vanilla can show
 * that grain costs six and has nowhere to put <em>they are starving</em>, and
 * the reason is the town's whole character. It travels as the enum's name, the
 * way a profession does, and is worded on the client.
 *
 * <p>The post's position travels too, so the button a player presses can name
 * the counter they are standing at. The server does not trust it — see
 * {@link MarketDealPayload} — it only needs to be told which of a kingdom's
 * markets is meant.
 *
 * <p>{@code opening} tells a right-click from a refresh, and is the difference
 * between a screen a player opened and a screen that opens itself: press a
 * button, hit escape before the reply lands, and without it the stall pops back
 * up on its own.
 */
public record MarketPayload(String town, BlockPos post, int treasury, boolean opening,
                            List<Offer> offers)
        implements CustomPacketPayload {

    /**
     * One deal, flattened for the wire.
     *
     * <p>{@code townBuys} is from the town's side, matching {@link Market.Deal},
     * because the alternative is a flag whose meaning flips depending on which
     * end of the counter you read it from. The screen turns it round once, into
     * the verb the player's button carries.
     *
     * @param unitPrice coin for one unit; a lot is {@link Market#LOT} of them
     * @param lots      how many lots the town will do before it changes its mind
     */
    public record Offer(String resource, boolean townBuys, int unitPrice, int lots,
                        String reason) {

        /** Coin — and so emeralds — for the single lot a button press trades. */
        public int lotPrice() {
            return unitPrice * Market.LOT;
        }
    }

    public static final Type<MarketPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "market"));

    /** Generous for a custom-named town, short enough not to be a payload attack. */
    private static final int MAX_NAME = 96;

    /** Resource words and reason names are single words from the ledger or an enum. */
    private static final int MAX_WORD = 32;

    /**
     * Two directions on each of {@link Market#TRADED}, which is every row the
     * town can possibly offer. The cap bites on read, so it is the client this
     * protects.
     */
    private static final int MAX_OFFERS = 16;

    private static final StreamCodec<RegistryFriendlyByteBuf, Offer> OFFER_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_WORD), Offer::resource,
                    ByteBufCodecs.BOOL, Offer::townBuys,
                    ByteBufCodecs.VAR_INT, Offer::unitPrice,
                    ByteBufCodecs.VAR_INT, Offer::lots,
                    ByteBufCodecs.stringUtf8(MAX_WORD), Offer::reason,
                    Offer::new);

    // Order is load-bearing: the terminal ::new is the canonical record
    // constructor, so each getter must sit where its component does.
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_NAME), MarketPayload::town,
                    BlockPos.STREAM_CODEC, MarketPayload::post,
                    ByteBufCodecs.VAR_INT, MarketPayload::treasury,
                    ByteBufCodecs.BOOL, MarketPayload::opening,
                    OFFER_CODEC.apply(ByteBufCodecs.list(MAX_OFFERS)), MarketPayload::offers,
                    MarketPayload::new);

    /**
     * Clipped and trimmed on the way in, so no construction path can build a
     * payload the codec would then refuse to write.
     *
     * <p>A town can be named by command and {@code ByteBufCodecs.stringUtf8}
     * throws while encoding anything longer than its cap — and a custom payload
     * that throws in the encoder is not skippable, so netty drops the
     * connection rather than the packet.
     */
    public MarketPayload {
        town = clip(town, MAX_NAME);
        offers = offers.stream()
                .limit(MAX_OFFERS)
                .map(offer -> new Offer(clip(offer.resource(), MAX_WORD), offer.townBuys(),
                        offer.unitPrice(), offer.lots(), clip(offer.reason(), MAX_WORD)))
                .toList();
    }

    /** Cutting one char short of a surrogate pair keeps a name from ending in half a letter. */
    private static String clip(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(max - 1)) ? max - 1 : max;
        return text.substring(0, end);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Reads a town's board off the settlement itself.
     *
     * @param opening true for the right-click that asked for the stall, false
     *                for the board sent back after a trade
     */
    public static MarketPayload of(Settlement settlement, BlockPos post, boolean opening) {
        List<Offer> offers = new ArrayList<>();
        for (Market.Deal deal : Market.offers(settlement)) {
            offers.add(new Offer(deal.resource(), deal.townBuys(), deal.unitPrice(),
                    deal.lots(), deal.reason().name()));
        }
        return new MarketPayload(settlement.name(), post, settlement.treasury(),
                opening, offers);
    }

    public static void handle(MarketPayload payload, IPayloadContext context) {
        KingdomsScreens.openMarket(payload);
    }
}
