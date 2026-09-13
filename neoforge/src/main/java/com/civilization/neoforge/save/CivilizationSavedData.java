package com.civilization.neoforge.save;

import com.civilization.neoforge.CivilizationMod;
import com.civilization.sim.kingdom.Kingdom;
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
 * nothing marks this dirty on its own — {@link CivilizationMod} calls {@link #setDirty()}
 * after each simulation step instead.
 */
public final class CivilizationSavedData extends SavedData {

    public static final Codec<CivilizationSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            CivilizationCodecs.KINGDOM_LIST.fieldOf("kingdoms").forGetter(data -> data.kingdoms),
            // The simulation's own clock. Optional and zero by default, because a
            // world written before this existed genuinely has no count to give and
            // zero is what it used to load with anyway -- which is the one case
            // where the old behavior is the right behavior.
            Codec.LONG.optionalFieldOf("steps_elapsed", 0L)
                    .forGetter(data -> data.stepsElapsed)
    ).apply(i, CivilizationSavedData::new));

    public static final SavedDataType<CivilizationSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(CivilizationMod.MOD_ID, "kingdoms"),
            CivilizationSavedData::new,
            CODEC);

    private final List<Kingdom> kingdoms;

    /**
     * How many simulation steps this dimension has ever run.
     *
     * <p>{@link com.civilization.sim.world.SimWorld} owns the counter while the
     * server is up; this is the copy that goes to disk, written after every step
     * that runs. It is a field here rather than a getter through to the live world
     * because the save data is loaded before the world is built — the count has to
     * exist for {@code SimWorld.restoreStepsElapsed} to be handed.
     *
     * <p>Everything durable that carries a step number —
     * {@code Perimeter.stakedOn}, {@code Building.completedOnStep},
     * {@code Settlement.firstStep}, the raid schedule — is a number in this
     * counter's units. Losing it made every one of those a date in the future.
     */
    private long stepsElapsed;

    public CivilizationSavedData() {
        this.kingdoms = new ArrayList<>();
    }

    private CivilizationSavedData(List<Kingdom> kingdoms, long stepsElapsed) {
        this.kingdoms = new ArrayList<>(kingdoms);
        this.stepsElapsed = Math.max(0L, stepsElapsed);
    }

    /** Loads existing data for this dimension, or creates empty data on first run. */
    public static CivilizationSavedData get(ServerLevel level) {
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

    /** The step count this dimension loaded with, or has since reached. */
    public long stepsElapsed() {
        return stepsElapsed;
    }

    /**
     * Records where the simulation's clock has got to.
     *
     * <p>Called after every step that runs, next to the {@link #setDirty()} the
     * step already needed: the kingdoms mutate in place and this does not, so it
     * is the one piece of per-step state that has to be copied across rather than
     * shared. Never moves backwards, because a clock that could would make every
     * recorded step number ambiguous.
     */
    public void setStepsElapsed(long steps) {
        if (steps > stepsElapsed) {
            stepsElapsed = steps;
            setDirty();
        }
    }

    public boolean isEmpty() {
        return kingdoms.isEmpty();
    }
}
