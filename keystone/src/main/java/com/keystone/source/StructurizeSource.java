package com.keystone.source;

import com.keystone.KeystoneMod;
import com.keystone.api.BlueprintSource;
import com.keystone.blueprint.Blueprint;
import com.keystone.blueprint.StructurizeNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Structurize {@code .blueprint} files, read from the same folder as everything
 * else.
 *
 * <p>Drop {@code baker1.blueprint} in beside {@code house.nbt} and ask for it by
 * the same kind of id. This is the whole point of the source seam: an entire
 * content ecosystem — MineColonies schematic packs, and anything else authored
 * with Structurize's scan tool — becomes available to a consuming mod without a
 * single call site changing, because a consuming mod never names a source.
 *
 * <p>Ranked just under {@link FolderSource}, so a building you drew yourself
 * beats an imported one of the same name. Both beat the datapack.
 */
public final class StructurizeSource implements BlueprintSource {

    static final String EXTENSION = ".blueprint";

    /** Resolves an id to a file, or empty if it would escape the blueprint folder. */
    public static Optional<Path> fileFor(Identifier id) {
        return FolderSource.fileFor(id, EXTENSION);
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
            return Optional.of(StructurizeNbt.readFile(file.get(), BlockLookup.of(level)));
        } catch (IOException unreadable) {
            KeystoneMod.LOG.error("Could not read blueprint {} from {}", id, file.get(), unreadable);
            return Optional.empty();
        }
    }

    /** Every imported blueprint on disk. */
    public static List<Identifier> list() {
        return FolderSource.list(EXTENSION);
    }

    @Override
    public int priority() {
        return 90;
    }

    @Override
    public String name() {
        return "structurize";
    }
}
