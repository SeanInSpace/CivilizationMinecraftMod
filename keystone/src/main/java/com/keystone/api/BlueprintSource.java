package com.keystone.api;

import com.keystone.blueprint.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Somewhere blueprints come from.
 *
 * <p>This is the seam. Consuming mods never name a concrete source, so the set
 * of places a structure can live is open-ended: a folder on disk, a datapack,
 * code that generates shapes, or an adapter over another mod's format. Adding
 * one is a class and a registration, not a change to anyone's call sites.
 *
 * <p>Sources are consulted in priority order and the first hit wins, so a
 * hand-authored building naturally overrides a generated fallback of the same
 * name.
 */
public interface BlueprintSource {

    /**
     * Produces the structure for {@code id}, or empty if this source has none.
     *
     * @param base where the structure will be anchored. File-backed sources
     *             ignore it; generated ones use it to fit the terrain.
     */
    Optional<Blueprint> load(ServerLevel level, BlockPos base, Identifier id);

    /**
     * Whether results may be reused across sites.
     *
     * <p>False for sources whose output depends on where it lands — a generated
     * shape that reads ground height to underpin itself produces a different
     * structure at every site, and caching one would stamp the first site's
     * foundations everywhere.
     */
    default boolean cacheable() {
        return true;
    }

    /** Higher is consulted first. */
    default int priority() {
        return 0;
    }

    /** For logs. */
    String name();
}
