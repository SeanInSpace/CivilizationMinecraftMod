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
import java.util.Optional;

/**
 * Blueprints shipped in datapacks and mod jars, at
 * {@code data/<namespace>/structure/<path>.nbt}.
 *
 * <p>Deliberately the same location vanilla structure templates use, so a
 * datapack written for vanilla structure blocks needs no changes to be built
 * course by course by a settlement here.
 */
public final class DatapackSource implements BlueprintSource {

    private static final String PREFIX = "structure/";
    private static final String EXTENSION = ".nbt";

    @Override
    public Optional<Blueprint> load(ServerLevel level, BlockPos base, Identifier id) {
        Identifier file = Identifier.fromNamespaceAndPath(
                id.getNamespace(), PREFIX + id.getPath() + EXTENSION);

        Optional<Resource> resource = level.getServer().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream in = resource.get().open()) {
            return Optional.of(BlueprintNbt.readStream(in, BlockLookup.of(level)));
        } catch (IOException unreadable) {
            KeystoneMod.LOG.error("Could not read datapack blueprint {}", file, unreadable);
            return Optional.empty();
        }
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
