package com.keystone.api;

import com.keystone.KeystoneMod;
import com.keystone.blueprint.Blueprint;
import com.keystone.blueprint.Transforms;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

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
 * <p>Server-thread only. No synchronisation, deliberately: a torn read here
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
            LoadedBlueprint resolved =
                    new LoadedBlueprint(Transforms.apply(found.get(), rotation, mirror));
            if (source.cacheable()) {
                CACHE.put(key, resolved);
            }
            return Optional.of(resolved);
        }
        return Optional.empty();
    }

    /**
     * Tries each id in turn and returns the first that resolves.
     *
     * <p>This is how architectural styles work: ask for
     * {@code kingdoms:norman/house} and then plain {@code kingdoms:house}, and a
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
