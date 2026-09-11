package com.keystone.api;

import com.keystone.KeystoneMod;
import com.keystone.blueprint.Blueprint;
import com.keystone.blueprint.BlueprintNbt;
import com.keystone.blueprint.Scanner;
import com.keystone.blueprint.Transforms;
import com.keystone.source.FolderSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The front door: ask for a blueprint by id, get one ready to build.
 *
 * <p>Sources are consulted in priority order, first hit wins. Results are
 * transformed, put in build order, and cached — but only when the source says
 * they may be, since a terrain-fitted shape is different at every site.
 *
 * <p>Server-thread only. No synchronization, deliberately: a torn read here
 * would be a bug worth crashing on rather than one worth hiding behind a lock.
 */
public final class Blueprints {

    /** Enough for the handful of structures a world builds in rotation. */
    private static final int CACHE_LIMIT = 64;

    private static final List<BlueprintSource> SOURCES = new ArrayList<>();

    private static final Map<Key, LoadedBlueprint> CACHE =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Key, LoadedBlueprint> eldest) {
                    return size() > CACHE_LIMIT;
                }
            };

    /**
     * The same structures before they were turned.
     *
     * <p>Kept separately because of {@link #loadFacing}, which cannot know which
     * rotation it wants until it has read the file and seen which way the author
     * pointed the front. Without this, discovering that would mean reading and
     * parsing the file twice for every building a town raises.
     */
    private static final Map<Identifier, Blueprint> RAW_CACHE =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Identifier, Blueprint> eldest) {
                    return size() > CACHE_LIMIT;
                }
            };

    private record Key(Identifier id, Rotation rotation, Mirror mirror) {
    }

    private Blueprints() {
    }

    public static void register(BlueprintSource source) {
        SOURCES.add(source);
        SOURCES.sort(Comparator.comparingInt(BlueprintSource::priority).reversed());
        KeystoneMod.LOG.info("Blueprint source registered: {} (priority {})",
                source.name(), source.priority());
    }

    public static List<BlueprintSource> sources() {
        return List.copyOf(SOURCES);
    }

    /** Drops resolved blueprints. Call when datapacks reload or files change. */
    public static void clearCache() {
        CACHE.clear();
        RAW_CACHE.clear();
    }

    public static Optional<LoadedBlueprint> load(ServerLevel level, BlockPos base, Identifier id) {
        return load(level, base, id, Rotation.NONE, Mirror.NONE);
    }

    public static Optional<LoadedBlueprint> load(ServerLevel level, BlockPos base, Identifier id,
                                                 Rotation rotation, Mirror mirror) {
        Key key = new Key(id, rotation, mirror);
        LoadedBlueprint cached = CACHE.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<Blueprint> raw = raw(level, base, id);
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        LoadedBlueprint resolved = new LoadedBlueprint(Transforms.apply(raw.get(), rotation, mirror));
        if (RAW_CACHE.containsKey(id)) {
            // Cacheable is a property of the source, and the raw cache already
            // decided it: a structure in there came from a source that said its
            // answer travels, so a turned copy of it travels too.
            CACHE.put(key, resolved);
        }
        return Optional.of(resolved);
    }

    /**
     * The structure as the file holds it, before any turn.
     *
     * <p>Separated out because {@link #loadFacing} has to see the file's own
     * stated front before it can know which way to turn it.
     */
    private static Optional<Blueprint> raw(ServerLevel level, BlockPos base, Identifier id) {
        Blueprint cached = RAW_CACHE.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        for (BlueprintSource source : SOURCES) {
            Optional<Blueprint> found;
            try {
                found = source.load(level, base, id);
            } catch (RuntimeException broken) {
                // One misbehaving source must not deny every other source a turn.
                KeystoneMod.LOG.error("Blueprint source {} failed on {}", source.name(), id, broken);
                continue;
            }
            if (found.isEmpty()) {
                continue;
            }
            if (source.cacheable()) {
                RAW_CACHE.put(id, found.get());
            }
            return found;
        }
        return Optional.empty();
    }

    /**
     * Loads a structure and turns it so its own front faces the way asked.
     *
     * <p>The difference between this and passing a {@link Rotation} is the whole
     * of what a stated facing buys. A consumer turning a building to face its
     * street knows which way it wants the door to end up; it does not know, and
     * should not have to know, which way the author happened to be standing when
     * they scanned it. Asking for a rotation assumes every file was drawn with
     * its door southward, and a file that was not gets a blank wall on the
     * street and a door into the hillside behind it.
     *
     * @param quarters where the front should end up, in quarter turns clockwise
     *                 from {@code +z}
     * @return a structure whose {@code facing()} is {@code quarters}
     */
    public static Optional<LoadedBlueprint> loadFacing(ServerLevel level, BlockPos base,
                                                       Identifier id, int quarters) {
        Optional<Blueprint> raw = raw(level, base, id);
        return raw.map(blueprint -> new LoadedBlueprint(Transforms.apply(blueprint,
                Transforms.between(blueprint.facing(), quarters), Mirror.NONE)));
    }

    /** The first of these ids that resolves, turned to face the way asked. */
    public static Optional<LoadedBlueprint> loadFirstFacing(ServerLevel level, BlockPos base,
                                                            List<Identifier> candidates,
                                                            int quarters) {
        for (Identifier id : candidates) {
            Optional<LoadedBlueprint> found = loadFacing(level, base, id, quarters);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Captures a region of the world and writes it to the blueprint folder.
     *
     * <p>Shared by the wand and the command so there is one definition of what
     * saving means, including dropping the cache — a re-saved blueprint that kept
     * serving its previous contents would be a maddening thing to debug.
     *
     * @return the blueprint that was written
     */
    public static Blueprint save(ServerLevel level, Identifier id, BlockPos a, BlockPos b)
            throws IOException {
        Path file = FolderSource.fileFor(id)
                .orElseThrow(() -> new IOException("Blueprint id escapes the blueprint folder: " + id));
        Blueprint blueprint = Scanner.scan(level, a, b);
        BlueprintNbt.writeFile(blueprint, file);
        clearCache();
        return blueprint;
    }

    /**
     * Writes a structure somebody has already scanned to a file of their choosing.
     *
     * <p>The half of {@link #save} that is not scanning, for callers who want
     * the blueprint to say more than a bare region can — an anchor cell, which
     * way the front points, how many crops are in it. Drops the cache for the
     * same reason: a re-saved blueprint that went on serving its old contents is
     * a maddening thing to debug.
     */
    public static Blueprint saveTo(Blueprint blueprint, Path file) throws IOException {
        BlueprintNbt.writeFile(blueprint, file);
        clearCache();
        return blueprint;
    }

    /**
     * Tries each id in turn and returns the first that resolves.
     *
     * <p>This is how architectural styles work: ask for
     * {@code civilization:norman/house} and then plain {@code civilization:house}, and a
     * culture that has not drawn its own version of a building quietly falls back
     * to the common one.
     */
    public static Optional<LoadedBlueprint> loadFirst(ServerLevel level, BlockPos base,
                                                      List<Identifier> candidates) {
        return loadFirst(level, base, candidates, Rotation.NONE, Mirror.NONE);
    }

    public static Optional<LoadedBlueprint> loadFirst(ServerLevel level, BlockPos base,
                                                      List<Identifier> candidates,
                                                      Rotation rotation, Mirror mirror) {
        for (Identifier id : candidates) {
            Optional<LoadedBlueprint> found = load(level, base, id, rotation, mirror);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
