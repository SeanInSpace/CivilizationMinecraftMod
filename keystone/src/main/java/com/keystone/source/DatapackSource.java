package com.keystone.source;

import com.keystone.KeystoneMod;
import com.keystone.api.BlueprintSource;
import com.keystone.blueprint.Blueprint;
import com.keystone.blueprint.BlueprintNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Blueprints shipped in datapacks and mod jars, at
 * {@code data/<namespace>/structure/<path>.nbt}.
 *
 * <p>Deliberately the same location vanilla structure templates use, so a
 * datapack written for vanilla structure blocks needs no changes to be built
 * course by course by a settlement here.
 *
 * <p>{@code data/<namespace>/blueprints/<path>.nbt} is read first, and is where
 * a mod or a datapack puts buildings that are <em>its own</em> rather than
 * vanilla structures: a culture's whole set of houses does not belong in the
 * folder the jungle temple lives in, and a pack author adding a style wants a
 * directory whose name says what is in it. Both are read, so nothing that
 * worked before stops working.
 */
public final class DatapackSource implements BlueprintSource {

    /**
     * Where to look, in order. Ours first: a pack that ships both means the
     * second as a vanilla structure and the first as a building.
     */
    private static final List<String> PREFIXES = List.of("blueprints/", "structure/");

    private static final String EXTENSION = ".nbt";

    @Override
    public Optional<Blueprint> load(ServerLevel level, BlockPos base, Identifier id) {
        for (String prefix : PREFIXES) {
            Identifier file = Identifier.fromNamespaceAndPath(
                    id.getNamespace(), prefix + id.getPath() + EXTENSION);

            Optional<Resource> resource = level.getServer().getResourceManager().getResource(file);
            if (resource.isEmpty()) {
                continue;
            }
            try (InputStream in = resource.get().open()) {
                return Optional.of(BlueprintNbt.readStream(in, BlockLookup.of(level)));
            } catch (IOException unreadable) {
                KeystoneMod.LOG.error("Could not read datapack blueprint {}", file, unreadable);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public String name() {
        return "datapack";
    }
}
