package com.keystone.source;

import com.keystone.KeystoneMod;
import com.keystone.api.BlueprintSource;
import com.keystone.blueprint.Blueprint;
import com.keystone.blueprint.BlueprintNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Blueprints kept inside the save, at
 * {@code <world>/<namespace>/blueprints/<path>.nbt}.
 *
 * <p>For {@code kingdoms:norman/cottage} that is
 * {@code <world>/kingdoms/blueprints/norman/cottage.nbt}, which is the layout a
 * player is told about: one folder per mod, one folder per style inside it.
 *
 * <p><strong>The highest priority there is</strong>, above the shared folder and
 * far above anything shipped in a jar. That ranking is the point of this source
 * existing at all: a building the player laid out and scanned in <em>this</em>
 * world is the most specific answer anyone could give to "what does a cottage
 * look like here", and it must beat the mod's own file of the same name without
 * the player having to delete anything or rename anything. Drop a file in, and
 * the next cottage the town raises is yours.
 *
 * <p>Inside the save rather than beside it, deliberately. A world folder travels
 * — it is what gets zipped up, handed to a friend, or uploaded — and a town whose
 * buildings came from files left behind on one machine would arrive at the other
 * end as a different town. {@link FolderSource} is the other half of that
 * bargain and keeps its files globally, so work-in-progress from a creative
 * world is usable in the survival one; this is where a building goes once it
 * belongs to a particular world.
 *
 * <p>Both formats are read, and by content rather than by extension: a
 * Structurize export saved as {@code .nbt} is recognized for what it is. See
 * {@link BlueprintNbt#readAnyFile}.
 */
public final class WorldSource implements BlueprintSource {

    /** Where the files sit under the namespace folder. */
    public static final String DIRECTORY = "blueprints";

    /** Read in this order, first hit wins. */
    static final List<String> EXTENSIONS = List.of(".nbt", ".blueprint");

    private WorldSource() {
    }

    public static WorldSource create() {
        return new WorldSource();
    }

    /** The save's own root, which is what every path here is checked against. */
    public static Path root(ServerLevel level, String namespace) {
        return level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve(namespace)
                .resolve(DIRECTORY);
    }

    /**
     * Resolves an id to a file, or empty if it would escape the folder.
     *
     * <p>Identifier paths permit dots, so {@code ../../../server.properties}
     * parses happily and resolves to something a command must never be allowed
     * to write. Normalized and re-checked against the root, exactly as
     * {@link FolderSource#fileFor} does it — the check is copied rather than
     * shared only because the roots are found differently, and both are tested.
     */
    public static Optional<Path> fileFor(ServerLevel level, Identifier id, String extension) {
        Path root = root(level, id.getNamespace()).normalize();
        Path candidate = root.resolve(id.getPath() + extension).normalize();
        return candidate.startsWith(root) ? Optional.of(candidate) : Optional.empty();
    }

    /** Where a scan of this id is written: always our own format. */
    public static Optional<Path> fileFor(ServerLevel level, Identifier id) {
        return fileFor(level, id, EXTENSIONS.get(0));
    }

    /** The file that actually exists for this id, in whichever format. */
    public static Optional<Path> existing(ServerLevel level, Identifier id) {
        for (String extension : EXTENSIONS) {
            Optional<Path> candidate = fileFor(level, id, extension);
            if (candidate.isPresent() && Files.isRegularFile(candidate.get())) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Blueprint> load(ServerLevel level, BlockPos base, Identifier id) {
        Optional<Path> file = existing(level, id);
        if (file.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(BlueprintNbt.readAnyFile(file.get(), BlockLookup.of(level)));
        } catch (IOException unreadable) {
            KeystoneMod.LOG.error("Could not read blueprint {} from {}", id, file.get(), unreadable);
            return Optional.empty();
        }
    }

    /** Every blueprint in this world's folder for one namespace, for pickers and commands. */
    public static List<Identifier> list(ServerLevel level, String namespace) {
        Path root = root(level, namespace);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Identifier> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                for (String extension : EXTENSIONS) {
                    if (!name.endsWith(extension)) {
                        continue;
                    }
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    String path = relative.substring(0, relative.length() - extension.length());
                    Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
                    if (!found.contains(id)) {
                        found.add(id);
                    }
                    break;
                }
            }
        } catch (IOException | IllegalArgumentException unreadable) {
            KeystoneMod.LOG.error("Could not list blueprints under {}", root, unreadable);
        }
        return found;
    }

    /**
     * Never cached across sites — but not for the usual reason.
     *
     * <p>The contents do not depend on the site; they depend on the <em>file</em>,
     * and the file is one a player edits with the game running. A cached world
     * blueprint is a scan you cannot re-take: you fix the roof, save it again,
     * and the town goes on building the old one until the server restarts.
     * {@link com.keystone.api.Blueprints#save} drops the cache for exactly this
     * reason, and a file dropped in by hand never goes through it.
     */
    @Override
    public boolean cacheable() {
        return false;
    }

    @Override
    public int priority() {
        return 120;
    }

    @Override
    public String name() {
        return "world";
    }
}
