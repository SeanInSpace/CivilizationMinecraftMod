package com.civilization.neoforge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every model the mod ships points at a texture that is actually there.
 *
 * <p>The animal farm asked for {@code minecraft:block/oak_fence}, which is a
 * block but not a texture — a fence is drawn from plank pixels and vanilla has
 * no file by that name. Nothing failed. The mod built, the block placed, the
 * pen stood in the world as a purple-and-black cube, and the only trace was one
 * line among four thousand at startup:
 *
 * <pre>[resourceLoad/WARN] [MaterialBaker]: Missing textures in model civilization:block/animal_farm</pre>
 *
 * <p>That is the entire class of fault: a JSON file naming a PNG that does not
 * exist, or naming a {@code #variable} that nothing in its parent chain binds.
 * No compiler can see either one, and a person reading the file cannot tell a
 * real vanilla texture name from a plausible one. So the parent chain is walked
 * here, every texture reference is resolved the way the game resolves it, and
 * the file it lands on has to exist.
 *
 * <p>Read from the classpath rather than the source tree, so it checks what
 * {@code processResources} produced and can follow a {@code minecraft:} parent
 * into the game's own assets.
 */
class ModelTexturesTest {

    @Test
    void everyModelTextureResolvesToAFileOnDisk() throws IOException, URISyntaxException {
        Path root = resourceRoot();
        Path models = root.resolve("assets").resolve("civilization").resolve("models");
        assertTrue(Files.isDirectory(models), "no models/ on the classpath at " + root);

        List<String> faults = new ArrayList<>();
        int seen = 0;
        try (Stream<Path> files = Files.walk(models)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (!file.toString().endsWith(".json")) {
                    continue;
                }
                seen++;
                String name = "civilization:" + models.relativize(file).toString()
                        .replace('\\', '/').replaceAll("\\.json$", "");
                faults.addAll(faultsIn(name, readJson(Files.readString(file, StandardCharsets.UTF_8))));
            }
        }
        // A walk that found nothing would pass for the wrong reason.
        assertTrue(seen >= 20, "only " + seen + " models found; the walk is looking"
                + " in the wrong place");
        assertEquals(List.of(), faults, "models pointing at textures that are not there");
    }

    /**
     * The test can see the game's own assets, so a {@code minecraft:} texture
     * name that does not exist is caught rather than waved through.
     *
     * <p>Without this the walk above would still pass on every mod of its own
     * textures and quietly skip the vanilla ones — which is exactly where the
     * fault was.
     */
    @Test
    void theGamesOwnTexturesAreReachableFromHere() {
        assertTrue(textureExists("minecraft:block/oak_planks"),
                "minecraft:block/oak_planks is not on the test classpath, so this"
                        + " test cannot tell a real vanilla texture from a made-up one");
        assertTrue(!textureExists("minecraft:block/oak_fence"),
                "minecraft:block/oak_fence now exists; the animal farm's old"
                        + " reference was not the fault this test was written for");
    }

    // --- resolving a model the way the game does -------------------------------

    /** What is wrong with one model, or nothing. */
    private static List<String> faultsIn(String modelName, JsonObject model) {
        List<String> faults = new ArrayList<>();

        // Child wins over parent, so walk the chain to the root collecting the
        // bindings a child has not already made, and every #reference used by
        // any face along the way.
        Map<String, String> textures = new HashMap<>();
        Set<String> used = new HashSet<>();
        JsonObject current = model;
        int depth = 0;
        while (current != null) {
            if (++depth > 16) {
                faults.add(modelName + ": parent chain loops or is absurdly deep");
                return faults;
            }
            collectTextures(current, textures);
            collectFaceReferences(current, used);
            String parent = string(current, "parent");
            // "builtin/generated" and "builtin/entity" are the game's own words
            // for "the renderer takes it from here", not files. Every item model
            // chain ends at one, and looking for it on disk would fail every one
            // of them.
            if (parent == null || Split.of(parent).path.startsWith("builtin/")) {
                break;
            }
            JsonObject next = readModel(parent);
            if (next == null) {
                faults.add(modelName + ": parent " + parent + " does not exist");
                return faults;
            }
            current = next;
        }

        // Everything bound, plus everything a face asked for. A binding nothing
        // uses is still checked: it is a name somebody meant, and a wrong one is
        // a fault waiting for the day a face starts using it.
        Set<String> toResolve = new HashSet<>(used);
        toResolve.addAll(textures.keySet());
        for (String key : toResolve.stream().sorted().toList()) {
            String value = resolve(key, textures);
            if (value == null) {
                faults.add(modelName + ": #" + key + " is not bound to anything");
            } else if (!textureExists(value)) {
                faults.add(modelName + ": " + value + " is not a texture on disk");
            }
        }
        return faults;
    }

    /** Follows {@code #reference} hops until a real texture name or a dead end. */
    private static String resolve(String key, Map<String, String> textures) {
        String at = key;
        for (int hop = 0; hop < 8; hop++) {
            String value = textures.get(at);
            if (value == null) {
                return null;
            }
            if (!value.startsWith("#")) {
                return value;
            }
            at = value.substring(1);
        }
        return null;
    }

    private static void collectTextures(JsonObject model, Map<String, String> into) {
        JsonElement textures = model.get("textures");
        if (textures == null || !textures.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : textures.getAsJsonObject().entrySet()) {
            // putIfAbsent: a child's binding is the one that counts.
            if (entry.getValue().isJsonPrimitive()) {
                into.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
            }
        }
    }

    /** Every {@code #name} a face in this model draws with, and the particle. */
    private static void collectFaceReferences(JsonObject model, Set<String> into) {
        JsonElement elements = model.get("elements");
        if (elements != null && elements.isJsonArray()) {
            for (JsonElement element : elements.getAsJsonArray()) {
                JsonElement faces = element.isJsonObject()
                        ? element.getAsJsonObject().get("faces") : null;
                if (faces == null || !faces.isJsonObject()) {
                    continue;
                }
                for (Map.Entry<String, JsonElement> face : faces.getAsJsonObject().entrySet()) {
                    String texture = face.getValue().isJsonObject()
                            ? string(face.getValue().getAsJsonObject(), "texture") : null;
                    if (texture != null && texture.startsWith("#")) {
                        into.add(texture.substring(1));
                    }
                }
            }
        }
    }

    // --- the classpath ---------------------------------------------------------

    /** Whether {@code namespace:path} names a PNG that ships with something. */
    private static boolean textureExists(String texture) {
        Split split = Split.of(texture);
        return asset(split.namespace, "textures", split.path + ".png") != null;
    }

    /** A model by {@code namespace:path}, from wherever it ships. */
    private static JsonObject readModel(String name) {
        Split split = Split.of(name);
        URL model = asset(split.namespace, "models", split.path + ".json");
        if (model == null) {
            return null;
        }
        try (InputStream in = model.openStream()) {
            return JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * One shipped asset, whoever ships it.
     *
     * <p>Through the context class loader, which is the only one here that can
     * see both sides. ModDevGradle's JUnit game loads the mod's own classes in a
     * transforming loader of its own, and neither that loader nor this test's
     * class can reach into the game's jar for {@code assets/minecraft/...} — the
     * lookup simply answers null, which would have made this whole test pass by
     * finding nothing wherever it mattered most. The context loader has the
     * patched Minecraft jar on it and the mod's resource directory too, so it
     * answers for both namespaces.
     */
    private static URL asset(String namespace, String kind, String path) {
        String at = "assets/" + namespace + "/" + kind + "/" + path;
        URL found = Thread.currentThread().getContextClassLoader().getResource(at);
        return found != null ? found : ModelTexturesTest.class.getResource("/" + at);
    }

    private static JsonObject readJson(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private record Split(String namespace, String path) {
        static Split of(String id) {
            int colon = id.indexOf(':');
            return colon < 0
                    ? new Split("minecraft", id)
                    : new Split(id.substring(0, colon), id.substring(colon + 1));
        }
    }

    /** The directory {@code assets/} sits in, on the test classpath. */
    private static Path resourceRoot() throws URISyntaxException {
        URL marker = ModelTexturesTest.class.getResource(
                "/assets/civilization/lang/en_us.json");
        assertNotNull(marker, "the mod's own lang file is not on the test classpath");
        assertEquals("file", marker.getProtocol(),
                "resources are packaged rather than on disk; this test walks a directory");
        return Path.of(marker.toURI()).getParent().getParent().getParent().getParent();
    }
}
