package com.kingdoms.neoforge.net;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Inventory;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.TownStores;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wire, both ways.
 *
 * <p>A payload's stream codec is a pair of functions nothing else checks. Get the
 * order of two components wrong and it still compiles, still sends, and shows a
 * profession where a name should be — {@code StreamCodec.composite} takes the
 * canonical constructor last, so the getters have to sit exactly where their
 * components do and only a round trip can say whether they do.
 *
 * <p>Nothing here needs a client. The codec is asked for a buffer and asked to
 * read it back, which is the whole of what travels.
 */
class PersonInventoryPayloadTest {

    /**
     * Registries are irrelevant to this payload — it carries strings and varints,
     * no holders — so the empty access is honest rather than a shortcut. The
     * connection type has to be named: the two-argument constructor is deprecated
     * because it guesses, and a payload only this mod sends is only ever read by
     * a NeoForge client.
     */
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static PersonInventoryPayload roundTrip(PersonInventoryPayload sent) {
        RegistryFriendlyByteBuf buf = buffer();
        PersonInventoryPayload.STREAM_CODEC.encode(buf, sent);
        PersonInventoryPayload read = PersonInventoryPayload.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(),
                "the codec read back fewer bytes than it wrote");
        return read;
    }

    @Test
    void aFullSettlerSurvivesTheWire() {
        List<Inventory.Slot> slots = List.of(
                new Inventory.Slot("minecraft:bread", 16),
                new Inventory.Slot("minecraft:apple", 9),
                new Inventory.Slot("minecraft:carrot", 4),
                new Inventory.Slot("minecraft:potato", 1),
                new Inventory.Slot("minecraft:cooked_beef", 2),
                new Inventory.Slot("minecraft:wheat", 13));
        assertEquals(Inventory.SLOTS, slots.size(), "the point of this case is a full pack");

        PersonInventoryPayload sent = new PersonInventoryPayload(
                "Alric", Profession.BUILDER.name(), 42, slots,
                TownStores.WOOD, 24,
                Optional.of(new PersonInventoryPayload.Errand(
                        TownStores.FOOD, HaulTask.Store.FARM.name(),
                        HaulTask.Store.GRANARY.name(), 8, 12)));

        PersonInventoryPayload read = roundTrip(sent);

        assertEquals("Alric", read.name());
        assertEquals(Profession.BUILDER.name(), read.profession());
        assertEquals(42, read.hunger());
        assertEquals(slots, read.slots());
        assertEquals(TownStores.WOOD, read.carriedMaterial());
        assertEquals(24, read.carriedLoad());
        assertEquals(sent.errand(), read.errand());
        assertEquals(sent, read);
    }

    @Test
    void anEmptyHandedSettlerSurvivesTheWire() {
        PersonInventoryPayload sent = new PersonInventoryPayload(
                "Settler", Profession.IDLER.name(), 0, List.of(), "", 0, Optional.empty());

        PersonInventoryPayload read = roundTrip(sent);

        assertEquals("Settler", read.name());
        assertEquals(Profession.IDLER.name(), read.profession());
        assertEquals(0, read.hunger());
        assertTrue(read.slots().isEmpty());
        assertEquals("", read.carriedMaterial());
        assertEquals(0, read.carriedLoad());
        assertTrue(read.errand().isEmpty());
        assertEquals(sent, read);
    }

    /**
     * The factory's one piece of translation: {@code Person} keeps a null carried
     * material when there is no load, and a stream codec has no null. The disk
     * format already spells that as the empty string; so does this.
     */
    @Test
    void aPersonWithNothingToCarryTravelsAsAnEmptyString() {
        Person person = new Person(Person.Id.random(), "Bryn",
                Profession.FARMER, new SimPos(0, 64, 0));

        PersonInventoryPayload payload = PersonInventoryPayload.of("Bryn", person);

        assertEquals("", payload.carriedMaterial());
        assertEquals(0, payload.carriedLoad());
        assertTrue(payload.errand().isEmpty());
        assertEquals(payload, roundTrip(payload));
    }

    @Test
    void theFactoryReadsPocketsCarryAndErrandOffOnePerson() {
        Person person = new Person(Person.Id.random(), "Bryn",
                Profession.BUILDER, new SimPos(0, 64, 0));
        person.setHunger(Person.HUNGER_WEAK);
        person.inventory().add("minecraft:bread", 3);
        person.setCarry(TownStores.STONE, 16);
        // Deliberately different numbers. Asked for twelve, five picked up: with
        // one figure the constructor could transpose them and stay green, and the
        // screen branches on carried() to choose which leg of the walk to describe.
        HaulTask haul = new HaulTask(TownStores.FOOD, HaulTask.Store.GRANARY,
                new SimPos(1, 64, 1), HaulTask.Store.HOME, new SimPos(9, 64, 9), 12);
        haul.setCarried(5);
        person.setHaul(haul);

        PersonInventoryPayload payload = roundTrip(PersonInventoryPayload.of("Bryn", person));

        assertEquals("Bryn", payload.name());
        assertEquals(Profession.BUILDER.name(), payload.profession());
        assertEquals(Person.HUNGER_WEAK, payload.hunger());
        assertEquals(List.of(new Inventory.Slot("minecraft:bread", 3)), payload.slots());
        assertEquals(TownStores.STONE, payload.carriedMaterial());
        assertEquals(16, payload.carriedLoad());
        // The build load and the errand are unrelated goods. A payload that
        // confused them would show a builder walking stone to a pantry.
        PersonInventoryPayload.Errand errand = payload.errand().orElseThrow();
        assertEquals(TownStores.FOOD, errand.resource());
        assertEquals(HaulTask.Store.GRANARY.name(), errand.from());
        assertEquals(HaulTask.Store.HOME.name(), errand.to());
        assertEquals(5, errand.carried());
        assertEquals(12, errand.requested());
    }

    /**
     * The disconnect this payload could have caused.
     *
     * <p>A settler can be named by command, and {@code ByteBufCodecs.stringUtf8}
     * throws while encoding anything longer than its cap. A custom payload that
     * throws in the encoder is not skippable, so netty drops the connection — the
     * chat line this replaced simply printed the long name. Clipping happens in
     * the record's own constructor, so no path can build one that will not send.
     */
    @Test
    void aRidiculousNameIsClippedRatherThanRefusedByTheEncoder() {
        String absurd = "N".repeat(500);

        PersonInventoryPayload sent = new PersonInventoryPayload(
                absurd, Profession.IDLER.name(), 0, List.of(),
                "a".repeat(500), 3, Optional.of(new PersonInventoryPayload.Errand(
                        "b".repeat(500), "c".repeat(500), "d".repeat(500), 1, 1)));

        assertTrue(sent.name().length() < absurd.length(), "the name was not clipped");
        assertEquals(sent, roundTrip(sent));
    }
}
