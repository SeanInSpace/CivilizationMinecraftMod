package com.kingdoms.neoforge.net;

import com.kingdoms.sim.economy.Market;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The market's wire, both ways.
 *
 * <p>A stream codec is a pair of functions nothing else checks. Two components
 * of the same type in the wrong order still compiles, still sends, and puts a
 * lot count where a price belongs — {@code StreamCodec.composite} takes the
 * canonical constructor last, so each getter has to sit exactly where its
 * component does and only a round trip can say whether it does.
 *
 * <p>The board carries three integers and a boolean per row, which is precisely
 * the shape that transposes silently, and a {@code BlockPos} that decides which
 * counter a button press names. None of it needs a client.
 */
class MarketPayloadTest {

    private static final BlockPos POST = new BlockPos(-1234, 71, 5678);

    /**
     * Registries are irrelevant to these payloads — strings, varints, a boolean
     * and a packed position, no holders — so the empty access is honest rather
     * than a shortcut. The connection type has to be named: the two-argument
     * constructor is deprecated because it guesses, and a payload only this mod
     * sends is only ever read by a NeoForge client.
     */
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static MarketPayload roundTrip(MarketPayload sent) {
        RegistryFriendlyByteBuf buf = buffer();
        MarketPayload.STREAM_CODEC.encode(buf, sent);
        MarketPayload read = MarketPayload.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(),
                "the codec read back fewer bytes than it wrote");
        return read;
    }

    private static MarketDealPayload roundTrip(MarketDealPayload sent) {
        RegistryFriendlyByteBuf buf = buffer();
        MarketDealPayload.STREAM_CODEC.encode(buf, sent);
        MarketDealPayload read = MarketDealPayload.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(),
                "the codec read back fewer bytes than it wrote");
        return read;
    }

    @Test
    void aFullBoardSurvivesTheWire() {
        // Every number deliberately different, and the two directions adjacent.
        // With a price of eight and eight lots on both rows a transposition
        // would round-trip clean and still be wrong.
        List<MarketPayload.Offer> offers = List.of(
                new MarketPayload.Offer(TownStores.FOOD, true, 6, 3,
                        Market.Reason.DESPERATE.name()),
                new MarketPayload.Offer(TownStores.STONE, false, 2, 8,
                        Market.Reason.GLUT.name()),
                new MarketPayload.Offer(TownStores.IRON, true, 16, 1,
                        Market.Reason.SHORT.name()));

        MarketPayload sent = new MarketPayload("Aldenholt", POST, 1873, true, offers);
        MarketPayload read = roundTrip(sent);

        assertEquals("Aldenholt", read.town());
        assertEquals(POST, read.post());
        assertEquals(1873, read.treasury());
        assertTrue(read.opening());
        assertEquals(offers, read.offers());
        assertEquals(sent, read);
    }

    @Test
    void anEmptyBoardSurvivesTheWire() {
        // A stall with nothing to offer still opens: the screen says so, and the
        // treasury on it is usually the reason.
        MarketPayload sent = new MarketPayload("Quietburg", BlockPos.ZERO, 0, false, List.of());

        MarketPayload read = roundTrip(sent);

        assertTrue(read.offers().isEmpty());
        assertEquals(BlockPos.ZERO, read.post());
        assertFalse(read.opening(),
                "a refresh must stay a refresh, or a closed stall reopens itself");
        assertEquals(sent, read);
    }

    @Test
    void aRequestForOneLotSurvivesTheWire() {
        MarketDealPayload sent = new MarketDealPayload(POST, TownStores.WOOD, false);

        MarketDealPayload read = roundTrip(sent);

        assertEquals(POST, read.post());
        assertEquals(TownStores.WOOD, read.resource());
        assertFalse(read.townBuys(), "the direction is the one field that reverses a trade");
        assertEquals(sent, read);
        assertTrue(roundTrip(new MarketDealPayload(POST, TownStores.WOOD, true)).townBuys());
    }

    /**
     * The disconnect these could have caused.
     *
     * <p>A town can be named by command, and {@code ByteBufCodecs.stringUtf8}
     * throws while encoding anything longer than its cap. A custom payload that
     * throws in the encoder is not skippable — netty drops the connection — so
     * both records clip in their own constructors and no construction path can
     * build one that will not send.
     */
    @Test
    void absurdNamesAreClippedRatherThanRefusedByTheEncoder() {
        MarketPayload board = new MarketPayload("N".repeat(500), POST, 1, true,
                List.of(new MarketPayload.Offer("r".repeat(500), true, 1, 1,
                        "R".repeat(500))));

        assertTrue(board.town().length() < 500, "the town name was not clipped");
        assertTrue(board.offers().getFirst().resource().length() < 500);
        assertEquals(board, roundTrip(board));

        MarketDealPayload deal = new MarketDealPayload(POST, "r".repeat(500), true);
        assertTrue(deal.resource().length() < 500);
        assertEquals(deal, roundTrip(deal));
    }

    /**
     * The board is read off the settlement, and the reason is read off the deal
     * rather than worked out a second time — a price and an explanation derived
     * separately are two things that can disagree.
     */
    @Test
    void theBoardIsReadStraightOffTheTown() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);
        town.addResident(new Person(Person.Id.random(), "Merek",
                Profession.TRADER, town.centre()));
        town.setStock(TownStores.FOOD, 0);

        MarketPayload board = roundTrip(MarketPayload.of(town, POST, true));

        assertEquals("Testburg", board.town());
        assertEquals(town.treasury(), board.treasury());
        MarketPayload.Offer grain = board.offers().stream()
                .filter(offer -> TownStores.FOOD.equals(offer.resource()) && offer.townBuys())
                .findFirst()
                .orElseThrow(() -> new AssertionError("a starving town is buying grain"));
        assertEquals(Market.Reason.DESPERATE.name(), grain.reason());
        assertEquals(Market.basePrice(TownStores.FOOD) * Market.DESPERATE,
                grain.unitPrice());
        assertEquals(grain.unitPrice() * Market.LOT, grain.lotPrice(),
                "the button's price is the lot, not the unit");
    }

    @Test
    void aBoardFromATownWithNoTraderIsEmpty() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Quietburg", new SimPos(0, 64, 0), 64);
        town.addResident(new Person(Person.Id.random(), "Ada",
                Profession.FARMER, town.centre()));

        assertTrue(MarketPayload.of(town, POST, true).offers().isEmpty(),
                "no trader, no board — the profession is worth losing");
    }
}
