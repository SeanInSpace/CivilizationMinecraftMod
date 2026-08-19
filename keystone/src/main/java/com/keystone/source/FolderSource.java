package com.keystone.source;

import com.keystone.KeystoneMod;
import com.keystone.api.BlueprintSource;
import com.keystone.blueprint.Blueprint;
import com.keystone.blueprint.BlueprintNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Blueprints scanned in-game, kept in {@code <gamedir>/keystone/blueprints}.
 *
 * <p>Global rather than per-world on purpose: a building you lay out in a
 * creative world is meant to be usable in the survival world you actually play.
 *
 * <p>Highest priority of the built-in sources, so a structure you authored
 * yourself always beats one shipped in a datapack under the same name.
 */
public final class FolderSource implements BlueprintSource {

    public static final String DIRECTORY = "keystone";

    private static final String EXTENSION = ".nbt";

    public static Path root() {
        return FMLPaths.GAMEDIR.get().resolve(DIRECTORY).resolve("blueprints");
    }

    /**
     * Resolves an id to a file, or empty if it would escape the blueprint folder.
     *
     * <p>Identifier paths permit dots, so {@code ../../secrets} parses happily.
     * Everything is normalised and re-checked against the root before use.
     */
    public static Optional<Path> fileFor(Identifier id) {
        Path root = root().normalize();
        Path candidate = root
                .resolve(id.getNamespace())
                .resolve(id.getPath() + EXTENSION)
                .normalize();
        return candidate.startsWith(root) ? Optional.of(candidate) : Optional.empty();
    }

    @Override
    public Optional<Blueprint> load(ServerLevel level, BlockPos base, Identifier id) {
        Optional<Path> file = fileFor(id);
        if (file.isEmpty()) {
            KeystoneMod.LOG.warn("Refusing blueprint id that escapes the blueprint folder: {}", id);
            return Optional.empty();
        }
        if (!Files.isRegularFile(file.get())) {
            return Optional.empty();
        }
        try {
            return Optional.of(BlueprintNbt.readFile(file.get(), BlockLookup.of(level)));
        } catch (IOException unreadable) {
            KeystoneMod.LOG.error("Could not read blueprint {} from {}", id, file.get(), unreadable);
            return Optional.empty();
        }
    }

    /** Every blueprint on disk, for pickers and commands. */
    public static List<Identifier> list() {
        Path root = root();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Identifier> found = new ArrayList<>();
        try (Stream<Path> namespaces = Files.list(root)) {
            for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                try (Stream<Path> files = Files.walk(namespace)) {
                    files.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                            .forEach(p -> {
                                String relative = namespace.relativize(p).toString()
                                        .replace('\\', '/');
                                String path = relative.substring(
                                        0, relative.length() - EXTENSION.length());
                                Identifier id = Identifier.fromNamespaceAndPath(
                                        namespace.getFileName().toString(), path);
                                found.add(id);
                            });
                }
            }
        } catch (IOException | IllegalArgumentException unreadable) {
            KeystoneMod.LOG.error("Could not list blueprints under {}", root, unreadable);
        }
        return found;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public String name() {
        return "folder";
    }
}
