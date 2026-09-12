package com.civilization.neoforge.item;

import com.civilization.neoforge.CivilizationEntities;
import com.civilization.neoforge.CivilizationItems;
import com.civilization.sim.culture.Race;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.TypedEntityData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two settler eggs: registered, drawn, named, and carrying a race.
 *
 * <p>Four separate ways an egg is broken without anything failing to compile. It
 * can be missing from the registry; it can be in the registry with no item
 * definition, which is a magenta cube in the hotbar; it can have no translation,
 * which is {@code item.civilization.orc_spawn_egg} written out in the creative
 * tab; and — the one that only this mod has — it can be in every respect a
 * perfect spawn egg that spawns the wrong race, because the race does not live in
 * the item class at all. It lives in the vanilla entity-data component, which is
 * a tag, and a tag with a typo in the key reads as a human.
 *
 * <p><strong>What this environment cannot do.</strong> Components on an item. The
 * harness fills the registries but never binds item components — in 26.2 they are
 * baked into {@code DATA_COMPONENT_INITIALIZERS} and attached to the registry
 * holder during data load, so {@code Item.components()} comes back unbound and
 * {@code new ItemStack(...)} throws outright (see {@code SiteDirectoryTest}). So
 * the component is checked one step upstream, as the object the egg's properties
 * are built from. The right-click itself is a playtest check.
 */
class SettlerEggTest {

    @Test
    @DisplayName("both eggs are registered items in the mod's own namespace")
    void bothEggsAreRegisteredItemsInTheModsOwnNamespace() {
        for (String name : new String[] {"human_spawn_egg", "orc_spawn_egg"}) {
            Identifier id = Identifier.fromNamespaceAndPath("civilization", name);
            assertTrue(BuiltInRegistries.ITEM.getOptional(id).isPresent(),
                    id + " is not in the item registry");
        }
        assertEquals("civilization:human_spawn_egg",
                BuiltInRegistries.ITEM.getKey(CivilizationItems.HUMAN_SPAWN_EGG.get()).toString());
        assertEquals("civilization:orc_spawn_egg",
                BuiltInRegistries.ITEM.getKey(CivilizationItems.ORC_SPAWN_EGG.get()).toString());
    }

    @Test
    @DisplayName("each egg names the one settler type and carries its own race in the tag")
    void eachEggNamesTheOneSettlerTypeAndCarriesItsOwnRace() {
        assertRace(CivilizationItems.HUMAN_SPAWN_EGG.get(), Race.HUMAN);
        assertRace(CivilizationItems.ORC_SPAWN_EGG.get(), Race.ORC);
    }

    private static void assertRace(Item egg, Race expected) {
        assertEquals(expected, ((PersonSpawnEggItem) egg).race(),
                "the registered egg is not the race it was registered as");
        // The component the egg's properties install, which is the last point at
        // which the type and the race sit in one object a test can hold -- see
        // PersonSpawnEggItem.entityData for why the bound item cannot be asked.
        TypedEntityData<?> data = PersonSpawnEggItem.entityData(expected);
        assertNotNull(data);
        // One entity type for every citizen of every town, so both eggs name it.
        assertSame(CivilizationEntities.PERSON.get(), data.type(),
                "the egg points at something other than the settler type");
        // And the race comes back out through the reader an in-world right-click
        // uses, which is what catches a typo in the tag key.
        assertEquals(expected, PersonSpawnEggItem.raceOf(data));
    }

    @Test
    @DisplayName("an egg with nothing written on it is a human rather than an exception")
    void anEggWithNothingWrittenOnItIsAHuman() {
        // What /give with a hand-written entity_data produces when somebody
        // misspells the key, and what a stack stripped of the component is.
        assertEquals(Race.HUMAN, PersonSpawnEggItem.raceOf((TypedEntityData<?>) null));
        assertEquals(Race.HUMAN, PersonSpawnEggItem.raceOf(
                TypedEntityData.of(CivilizationEntities.PERSON.get(),
                        new net.minecraft.nbt.CompoundTag())));
    }

    @Test
    @DisplayName("both eggs have a model, a picture and a name a player can read")
    void bothEggsHaveAModelAPictureAndAName() throws IOException {
        String lang = resource("/assets/civilization/lang/en_us.json");
        for (String name : new String[] {"human_spawn_egg", "orc_spawn_egg"}) {
            String definition = resource("/assets/civilization/items/" + name + ".json");
            assertTrue(definition.contains("civilization:item/" + name),
                    name + "'s item definition points somewhere else: " + definition);
            String model = resource("/assets/civilization/models/item/" + name + ".json");
            assertTrue(model.contains("minecraft:item/generated"),
                    name + " is not a flat item model: " + model);
            assertTrue(model.contains("civilization:item/" + name),
                    name + "'s model has no layer0 of its own: " + model);
            // 26.2 has no tinted spawn-egg template left to parent to, so each
            // egg is its own 16x16. See neoforge/tools/spawn_egg_art.py.
            assertEquals(16, iconWidth("/assets/civilization/textures/item/" + name + ".png"),
                    name + " is not a 16x16 icon");
            assertTrue(lang.contains("\"item.civilization." + name + "\""),
                    name + " has no translation; the creative tab would show the key");
        }
    }

    private static String resource(String classpath) throws IOException {
        try (InputStream in = SettlerEggTest.class.getResourceAsStream(classpath)) {
            assertNotNull(in, "no such resource on the test classpath: " + classpath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int iconWidth(String classpath) throws IOException {
        try (InputStream in = SettlerEggTest.class.getResourceAsStream(classpath)) {
            assertNotNull(in, "no such resource on the test classpath: " + classpath);
            DataInputStream data = new DataInputStream(in);
            data.readFully(new byte[8]);        // signature
            data.readInt();                     // IHDR length
            assertEquals(0x49484452, data.readInt(), classpath + " does not start with IHDR");
            return data.readInt();
        }
    }
}
