package com.kingdoms.neoforge.save;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.worldgen.SettlementSites;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which regions have been looked at, and what came of it.
 *
 * <p>{@link SettlementSites} is a function: it will answer "there is a town at
 * (1184, 0, -2720)" forever, for nothing, whether or not one was ever built.
 * That is the point of it, and it is also why something has to remember. Two
 * facts do not fit in a pure function:
 *
 * <ul>
 *   <li><strong>A site was refused.</strong> The arithmetic picks a centre
 *       blind; the terrain check that follows can find a lake or a cliff and
 *       say no. Without a record, the next player to walk past asks again, gets
 *       the same centre, and pays for the same refusal.</li>
 *   <li><strong>A town was built.</strong> Nothing else can tell. A settlement
 *       moves off its arithmetic centre when the ground is better a few blocks
 *       over, grows, and is eventually indistinguishable from one a player
 *       founded — so "is there already a town for this region" is not a
 *       question the settlement list can answer.</li>
 * </ul>
 *
 * <p>So a region is decided once and the decision is kept. Deliberately the
 * smallest thing that achieves that: region coordinates, and either where the
 * town actually went or nothing at all, meaning refused. Not the culture, not
 * the settlement id, not the date — everything else is either recoverable from
 * the seed or belongs to the settlement itself, and a duplicated fact in a save
 * file is a fact that will eventually disagree with itself.
 *
 * <p>Stands apart from {@link KingdomsSavedData} rather than joining it,
 * because the two have nothing to say to each other: this one is written once
 * per region for the life of a world, that one is rewritten every simulation
 * step.
 */
public final class SiteLedger extends SavedData {

    /**
     * What became of one region's site.
     *
     * @param regionX region coordinate, as {@link SettlementSites#regionOf} counts them
     * @param regionZ region coordinate
     * @param centre  where the town was actually founded, or empty when the
     *                site was refused. Note this is the <em>final</em> centre,
     *                after any shift onto better ground — the arithmetic centre
     *                is always recoverable from the seed, and the one worth
     *                keeping is the one that cannot be recomputed.
     */
    public record Entry(int regionX, int regionZ, Optional<SimPos> centre) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("region_x").forGetter(Entry::regionX),
                Codec.INT.fieldOf("region_z").forGetter(Entry::regionZ),
                // Absent rather than a flag: "no centre" and "refused" are the
                // same statement, and storing both invites them to disagree.
                KingdomsCodecs.SIM_POS.optionalFieldOf("centre").forGetter(Entry::centre)
        ).apply(i, Entry::new));

        public boolean accepted() {
            return centre.isPresent();
        }
    }

    public static final Codec<SiteLedger> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().optionalFieldOf("resolved", List.of())
                    .forGetter(SiteLedger::entries)
    ).apply(i, SiteLedger::new));

    public static final SavedDataType<SiteLedger> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "settlement_sites"),
            SiteLedger::new,
            CODEC);

    /**
     * Keyed by packed region coordinates, in the order the regions were decided.
     *
     * <p>A map because the question asked of this class is always "has region
     * (x, z) been decided", once per site per player approach; a list would make
     * that a scan over every region anyone has ever walked through.
     */
    private final Map<Long, Entry> resolved;

    public SiteLedger() {
        this.resolved = new LinkedHashMap<>();
    }

    private SiteLedger(List<Entry> entries) {
        this.resolved = new LinkedHashMap<>();
        for (Entry entry : entries) {
            resolved.put(key(entry.regionX(), entry.regionZ()), entry);
        }
    }

    /** Loads existing data for this dimension, or creates empty data on first run. */
    public static SiteLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Every decision made so far, oldest first. */
    public List<Entry> entries() {
        return List.copyOf(resolved.values());
    }

    public boolean isResolved(int regionX, int regionZ) {
        return resolved.containsKey(key(regionX, regionZ));
    }

    /** What became of this region, or empty if nobody has decided yet. */
    public Optional<Entry> entry(int regionX, int regionZ) {
        return Optional.ofNullable(resolved.get(key(regionX, regionZ)));
    }

    /**
     * Records that a town was raised for this region, at this centre.
     *
     * @return whether this was a new decision; false leaves the ledger untouched
     */
    public boolean accept(int regionX, int regionZ, SimPos centre) {
        return record(new Entry(regionX, regionZ, Optional.of(centre)));
    }

    /**
     * Records that this region's site was looked at and refused.
     *
     * @return whether this was a new decision; false leaves the ledger untouched
     */
    public boolean reject(int regionX, int regionZ) {
        return record(new Entry(regionX, regionZ, Optional.empty()));
    }

    /**
     * First writer wins.
     *
     * <p>Not last, which is the reflex. A region already carrying an accepted
     * centre has a town standing on it; overwriting that with a later refusal —
     * or with a second acceptance — is how the same region ends up raising two
     * towns, which is the single thing this class exists to prevent.
     */
    private boolean record(Entry entry) {
        Long at = key(entry.regionX(), entry.regionZ());
        if (resolved.containsKey(at)) {
            return false;
        }
        resolved.put(at, entry);
        setDirty();
        return true;
    }

    public int size() {
        return resolved.size();
    }

    public boolean isEmpty() {
        return resolved.isEmpty();
    }

    /** Region coordinates packed into one key; both halves are signed ints. */
    private static long key(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFF_FFFFL);
    }
}
