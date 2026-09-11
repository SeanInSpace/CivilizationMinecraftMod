package com.civilization.neoforge;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mod answers to one name, everywhere.
 *
 * <p>The rename from "kingdoms" to "civilization" touched a registry id, a
 * resource folder, a translation key and a package name for every single thing
 * the mod adds, and a miss in any one of those is invisible until the game is
 * running: a block whose model lives under the old namespace is a purple-and-black
 * cube, a translation key nobody renamed is a raw string in the creative tab, and
 * a recipe pointing at {@code kingdoms:founding_charter} simply never loads. None
 * of that fails a compile. So it is asserted here instead.
 *
 * <p>Two halves, because a rename can be half-done in two different ways. The
 * registry half walks every {@link DeferredRegister} the mod owns and checks the
 * namespace of every id in it — that catches a register created with a literal
 * instead of {@link CivilizationMod#MOD_ID}. The resource half walks the shipped
 * {@code assets/} and {@code data/} trees on disk and checks both the paths and
 * the file contents — that catches a folder or a key the move missed, which the
 * registry half cannot see at all.
 */
class ModIdentityTest {

    private static final String OLD_NAME = "kingdoms";

    @Test
    void theModIdIsCivilization() {
        assertEquals("civilization", CivilizationMod.MOD_ID);
    }

    @Test
    void everyRegisteredIdIsInTheModsNamespace() {
        assertRegistered("blocks", CivilizationBlocks.BLOCKS);
        assertRegistered("block entities", CivilizationBlockEntities.BLOCK_ENTITIES);
        assertRegistered("items", CivilizationItems.ITEMS);
        assertRegistered("data components", CivilizationComponents.COMPONENTS);
        assertRegistered("entity types", CivilizationEntities.ENTITY_TYPES);
        assertRegistered("attachment types", CivilizationAttachments.ATTACHMENTS);
        assertRegistered("creative tabs", CivilizationTabs.TABS);
    }

    private static void assertRegistered(String what, DeferredRegister<?> register) {
        assertEquals(CivilizationMod.MOD_ID, register.getNamespace(),
                "the " + what + " register was created for the wrong mod id");
        assertTrue(!register.getEntries().isEmpty(), "no " + what + " registered at all");
        for (DeferredHolder<?, ?> entry : register.getEntries()) {
            Identifier id = entry.getId();
            assertEquals(CivilizationMod.MOD_ID, id.getNamespace(),
                    "registered under the wrong namespace: " + id);
        }
    }

    /**
     * Nothing shipped in {@code assets/} or {@code data/} still says the old name
     * — not in a folder name, not in a file name, not inside a file.
     *
     * <p>Walked from the classpath, so this reads what {@code processResources}
     * actually produced rather than what the source tree looks like.
     */
    @Test
    void noShippedResourceStillSaysTheOldName() throws IOException, URISyntaxException {
        Path root = resourceRoot();
        List<String> offenders = new ArrayList<>();
        int seen = 0;
        for (String tree : List.of("assets", "data")) {
            Path dir = root.resolve(tree);
            assertTrue(Files.isDirectory(dir), "no " + tree + "/ on the classpath at " + root);
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    seen++;
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    if (relative.toLowerCase(Locale.ROOT).contains(OLD_NAME)) {
                        offenders.add(relative + " (path)");
                    }
                    if (!relative.endsWith(".png") && !relative.endsWith(".nbt")
                            && Files.readString(file, StandardCharsets.UTF_8)
                                    .toLowerCase(Locale.ROOT).contains(OLD_NAME)) {
                        offenders.add(relative + " (contents)");
                    }
                }
            }
        }
        // A walk that found nothing would pass for the wrong reason.
        assertTrue(seen > 100, "only " + seen + " resources found; the walk is looking in the wrong place");
        assertEquals(List.of(), offenders, "resources still carrying the old mod name");
    }

    /** The directory {@code assets/} and {@code data/} sit in, on the test classpath. */
    private static Path resourceRoot() throws URISyntaxException {
        URL marker = ModIdentityTest.class.getResource("/assets/civilization/lang/en_us.json");
        assertNotNull(marker, "the mod's own lang file is not on the test classpath");
        assertEquals("file", marker.getProtocol(),
                "resources are packaged rather than on disk; this test walks a directory");
        // .../assets/civilization/lang/en_us.json -> lang -> civilization -> assets -> root
        return Path.of(marker.toURI()).getParent().getParent().getParent().getParent();
    }
}
