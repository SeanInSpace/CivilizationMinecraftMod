package com.kingdoms.sim.kingdom;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.Race;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A political entity owning several settlements.
 *
 * <p>Kingdom state outlives every entity in it, so it lives here and is persisted
 * through the platform layer's save data — never on a mob.
 */
public final class Kingdom {

    public record Id(UUID value) {
        public Id {
            Objects.requireNonNull(value, "value");
        }

        public static Id random() {
            return new Id(UUID.randomUUID());
        }
    }

    private final Id id;
    private final String name;

    /** Identifies a datapack-defined culture, e.g. {@code "kingdoms:human/norman"}. */
    private final String cultureId;

    private final Map<Settlement.Id, Settlement> settlements = new LinkedHashMap<>();
    private final Map<Id, Standing> diplomacy = new LinkedHashMap<>();

    public Kingdom(Id id, String name, String cultureId) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.cultureId = Objects.requireNonNull(cultureId, "cultureId");
    }

    /**
     * What this people call the realm their first town belongs to.
     *
     * <p>Every kingdom in the mod has been named after its first settlement:
     * a town called Bellbrook founds the kingdom of Bellbrook, which is how a
     * shire works and is fine for people who live in shires. It is not how a
     * warband works. Orcs do not follow a place, they follow whoever is in the
     * great hut, so their realm is <em>the Gorehand Warband</em> and the town in
     * the middle of it is Karrgurd.
     *
     * <p>The chief's name is drawn from the culture's own family names, which
     * for the warhost are already the right shape — Gorehand, Blacktusk, Redaxe.
     * Drawn from where the town stands rather than at random, so the same world
     * seed gives the same warband on every load, and avalanched first for the
     * reason {@code Culture.layoutFor} gives: a generated town lands on a spacing
     * grid, so the low bits of its coordinates move in lockstep and reading them
     * raw would hand a whole region one name.
     *
     * <p><strong>Not the living king's own name</strong>, and that is a
     * deliberate limit rather than an oversight. A kingdom's name is final and is
     * fixed when the first town is founded, which is long before the great hut
     * stands and longer still before anybody is crowned in it — so a realm named
     * after its current king would have to be renamed on every succession, and
     * every sign, map and chat line that ever quoted it would go stale. The
     * warband is named after its line; {@code KingPlanner} says who is presently
     * at the head of it.
     */
    public static String nameFor(Culture culture, String townName, int x, int z) {
        if (culture == null || culture.race() != Race.ORC
                || culture.familyNames().isEmpty()) {
            return townName;
        }
        List<String> lines = culture.familyNames();
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL
                ^ 0x2545F4914F6CDD1DL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return "the " + lines.get((int) Long.remainderUnsigned(h, lines.size()))
                + " Warband";
    }

    public Id id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String cultureId() {
        return cultureId;
    }

    /**
     * Takes a new settlement into the kingdom, which adopts its people.
     *
     * <p>Founding a town, or a daughter budding off one. The town has no culture
     * of its own yet, so it takes the kingdom's.
     */
    public void addSettlement(Settlement settlement) {
        settlement.setCultureId(cultureId);
        settlements.put(settlement.id(), settlement);
    }

    /**
     * Puts a settlement back into the kingdom without restamping its people.
     *
     * <p>Loading a save is not founding a town, and conflating the two silently
     * undid every per-settlement culture in every world. The codec restored a
     * settlement's own culture and then handed the list to
     * {@link #addSettlement}, which overwrote it with the kingdom's — so
     * {@code /civ culture} appeared to work, survived for as long as the server
     * stayed up, and reverted on the next load.
     *
     * <p>It cost a whole demonstration to catch: a town was set to the vale folk,
     * grew two hundred and fourteen ring-road carriageways, and came back after
     * one save as a Norman town laid out in concentric rings — keeping the
     * streets it had built under the old culture, which is a shape no
     * arrangement would ever produce. Nothing was wrong with the save; the
     * culture was written and read correctly and then thrown away one line
     * later.
     *
     * <p>Two methods rather than a flag, because the two callers want genuinely
     * different things and a boolean at the call site would have to be got right
     * every time somebody adds a third.
     */
    public void restoreSettlement(Settlement settlement) {
        settlements.put(settlement.id(), settlement);
    }

    public Collection<Settlement> settlements() {
        return Collections.unmodifiableCollection(settlements.values());
    }

    public int totalPopulation() {
        return settlements.values().stream().mapToInt(Settlement::population).sum();
    }

    public Standing standingWith(Id other) {
        return diplomacy.getOrDefault(other, Standing.NEUTRAL);
    }

    /** Exposed so the platform layer can serialize it. */
    public Map<Id, Standing> diplomacy() {
        return Collections.unmodifiableMap(diplomacy);
    }

    public void setStandingWith(Id other, Standing standing) {
        diplomacy.put(Objects.requireNonNull(other, "other"), Objects.requireNonNull(standing, "standing"));
    }

    /** Advance every settlement by one simulation step, then consider expanding. */
    public void step(SimContext ctx) {
        settlements.values().forEach(settlement -> settlement.step(ctx));
        ExpansionPlanner.advance(this, ctx);
    }

    @Override
    public String toString() {
        return name + " [" + cultureId + ", " + settlements.size() + " settlements, pop " + totalPopulation() + "]";
    }
}
