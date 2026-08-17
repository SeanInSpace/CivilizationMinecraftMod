package com.kingdoms.neoforge.save;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.sim.kingdom.Kingdom;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Durable storage for every kingdom in a dimension.
 *
 * <p>This is where kingdom-scale state belongs — not on entities, which unload and
 * die, and not in chunk data, since a kingdom spans many chunks and must exist while
 * all of them are unloaded. Minecraft writes this alongside the level automatically;
 * there is no explicit save call to make.
 *
 * <p><strong>Note the shared references.</strong> The {@link Kingdom} objects held
 * here are the same instances the running simulation mutates, so a completed building
 * or a changed threat level is already reflected the next time this is serialized.
 * The cost of that convenience is that mutation does not go through a setter, so
 * nothing marks this dirty on its own — {@link KingdomsMod} calls {@link #setDirty()}
 * after each simulation step instead.
 */
public final class KingdomsSavedData extends SavedData {

    public static final Codec<KingdomsSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            KingdomsCodecs.KINGDOM_LIST.fieldOf("kingdoms").forGetter(data -> data.kingdoms)
    ).apply(i, KingdomsSavedData::new));

    public static final SavedDataType<KingdomsSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "kingdoms"),
            KingdomsSavedData::new,
            CODEC);

    private final List<Kingdom> kingdoms;

    public KingdomsSavedData() {
        this.kingdoms = new ArrayList<>();
    }

    private KingdomsSavedData(List<Kingdom> kingdoms) {
        this.kingdoms = new ArrayList<>(kingdoms);
    }

    /** Loads existing data for this dimension, or creates empty data on first run. */
    public static KingdomsSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Kingdom> kingdoms() {
        return Collections.unmodifiableList(kingdoms);
    }

    public void addKingdom(Kingdom kingdom) {
        kingdoms.add(kingdom);
        setDirty();
    }

    public boolean removeKingdom(Kingdom.Id id) {
        boolean removed = kingdoms.removeIf(k -> k.id().equals(id));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public boolean isEmpty() {
        return kingdoms.isEmpty();
    }
}
