package com.kingdoms.sim.kingdom;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SettlementNames;
import com.kingdoms.sim.world.SimContext;

import java.util.ArrayList;
import java.util.List;

/**
 * How one town becomes a kingdom.
 *
 * <p>When a settlement reaches its population ceiling, it does not simply stop —
 * it sends people <em>out</em>. A founding party of its youngest families departs
 * and establishes a daughter settlement under the same banner, far enough away to
 * claim its own land. The parent, relieved of the emigrants, resumes growing; when
 * it fills again and the daughter is established, it founds another. Sprawl is the
 * steady state.
 *
 * <p>Rules, all deterministic:
 * <ul>
 *   <li>Only a <strong>full</strong> settlement founds (population at the ceiling).</li>
 *   <li>Not while any sibling is still <strong>young</strong> (below
 *       {@link #ESTABLISHED_POPULATION}) — one frontier town at a time.</li>
 *   <li>The party is whole families, never split, chosen youngest-first.</li>
 *   <li>Families with anyone currently embodied stay — people a player can see
 *       never teleport across the map.</li>
 *   <li>The site is picked by hashed direction at {@link #DAUGHTER_DISTANCE},
 *       stepping further out if the kingdom's own claims are in the way.</li>
 * </ul>
 */
public final class ExpansionPlanner {

    /** Most people in a founding party (whole families, so usually a little under). */
    public static final int FOUNDING_PARTY_MAX = 6;

    /** How far from the parent a daughter settlement is planted. */
    public static final int DAUGHTER_DISTANCE = 160;

    /** A settlement below this population is still being established. */
    public static final int ESTABLISHED_POPULATION = 10;

    private static final int INITIAL_CLAIM = 64;
    private static final int SITE_ATTEMPTS = 8;

    private ExpansionPlanner() {
    }

    /** Called once per kingdom step. At most one founding per step. */
    public static void advance(Kingdom kingdom, SimContext ctx) {
        // Doubly stopped, and deliberately. The flag below turns it off; and the
        // trigger it waits for -- a settlement at its population ceiling -- can
        // no longer happen either, because there is no ceiling any more. Neither
        // is load-bearing on its own, which is the point: turning expansion back
        // on now means choosing a cap as well as setting the flag, rather than
        // discovering daughters appearing because a number moved.
        if (!ctx.settings().expansionEnabled()) {
            // Off by default. Everything below works; what it also does is turn
            // one settlement into six inside a single run, and a kingdom is a
            // much harder thing to watch than a village. The whole decision tree
            // is preserved rather than deleted -- see the class javadoc -- so
            // turning it back on is one flag.
            return;
        }
        for (Settlement settlement : List.copyOf(kingdom.settlements())) {
            if (settlement.population() < ctx.settings().maxSettlementPopulation()) {
                continue;
            }
            // The hall is the expansion gate: only a chartered TOWN with its
            // hall actually standing sends people out. A settlement still
            // climbing the founding ladder pours its people into the climb.
            if (!settlement.stage().atLeast(SettlementStage.TOWN)
                    || settlement.countBuildings("kingdoms:town_hall") < 1) {
                continue;
            }
            if (!allOthersEstablished(kingdom, settlement)) {
                continue;
            }
            found(kingdom, settlement, ctx);
            return;
        }
    }

    private static boolean allOthersEstablished(Kingdom kingdom, Settlement except) {
        for (Settlement s : kingdom.settlements()) {
            if (s != except && s.population() < ESTABLISHED_POPULATION) {
                return false;
            }
        }
        return true;
    }

    static void found(Kingdom kingdom, Settlement parent, SimContext ctx) {
        List<Household> party = pickParty(parent);
        if (party.isEmpty()) {
            return;
        }
        // The dowry: the same kit a player's charter grants, paid by the
        // parent. A town that cannot outfit its emigrants keeps them until it
        // can -- nobody is sent into the wilderness with empty hands, which is
        // how the very first founding party nearly died.
        int provisions = FoodPlanner.STARTING_PROVISIONS;
        if (!parent.stores().has(TownStores.WOOD, TownStores.FOUNDING_WOOD)
                || !parent.stores().has(TownStores.STONE, TownStores.FOUNDING_STONE)
                || !parent.stores().has(TownStores.FOOD, provisions)) {
            return;
        }

        SimPos flat = com.kingdoms.sim.settlement.Founding.bestSiteNear(
                siteFor(kingdom, parent),
                com.kingdoms.sim.settlement.Founding.SITING_REACH, ctx.bridge());
        int y = ctx.bridge().surfaceHeight(flat);
        SimPos centre = new SimPos(flat.x(), y, flat.z());

        Settlement daughter = new Settlement(
                Settlement.Id.random(), SettlementNames.forPosition(centre), centre, INITIAL_CLAIM);
        daughter.setCatalogue(parent.catalogue());
        // Daughters live the same founding the charter does: they arrive as a
        // camp of pioneers and earn their stages, hall last. Their old trades
        // are re-earned as the camp crystallizes them -- a smith on a bare
        // hillside is a pioneer whatever their papers say.
        daughter.setStage(SettlementStage.CAMP);

        // The Settlement constructor already banked the daughter's kit -- every
        // settlement bootstraps with one. What changes here is who pays for
        // it: the parent's ledger loses exactly what the daughter's gained,
        // so expansion stops conjuring resources out of the founding itself.
        parent.stores().take(TownStores.WOOD, TownStores.FOUNDING_WOOD);
        parent.stores().take(TownStores.STONE, TownStores.FOUNDING_STONE);
        parent.stores().take(TownStores.FOOD, provisions);

        int emigrants = 0;
        for (Household household : party) {
            emigrants += household.size();
            moveHousehold(parent, daughter, household, centre);
        }
        for (Person emigrant : daughter.residents()) {
            emigrant.setProfession(Profession.PIONEER);
        }
        kingdom.addSettlement(daughter);

        parent.logEvent(ctx.step(), emigrants + " set out to found " + daughter.name());
        daughter.logEvent(ctx.step(), daughter.name() + " founded by settlers from " + parent.name());
        ctx.bridge().log(kingdom.name() + " expands: " + daughter.name() + " founded at " + centre);
    }

    /**
     * Whole families, youngest first, none currently watched, until the party is
     * full. Youngest-first means the founding generation emigrates — the elders
     * who built the parent stay in it.
     */
    static List<Household> pickParty(Settlement parent) {
        List<Household> party = new ArrayList<>();
        int members = 0;
        List<Household> households = parent.households();
        for (int i = households.size() - 1; i >= 0; i--) {
            Household household = households.get(i);
            if (household.size() == 0 || members + household.size() > FOUNDING_PARTY_MAX) {
                continue;
            }
            if (anyEmbodied(parent, household)) {
                continue;
            }
            party.add(household);
            members += household.size();
            if (members >= FOUNDING_PARTY_MAX) {
                break;
            }
        }
        return party;
    }

    private static boolean anyEmbodied(Settlement settlement, Household household) {
        for (Person.Id memberId : household.members()) {
            Person person = settlement.resident(memberId);
            if (person != null && person.isEmbodied()) {
                return true;
            }
        }
        return false;
    }

    /** Hashed direction, stepping outward until clear of the kingdom's own claims. */
    public static SimPos siteFor(Kingdom kingdom, Settlement parent) {
        int n = kingdom.settlements().size();
        for (int attempt = 0; attempt < SITE_ATTEMPTS; attempt++) {
            long h = mix(parent.id().value().hashCode(), n * 31L + attempt * 131L);
            double angle = Math.toRadians(Math.floorMod(h, 360));
            int distance = DAUGHTER_DISTANCE + attempt * 48;
            SimPos site = new SimPos(
                    parent.centre().x() + (int) Math.round(distance * Math.cos(angle)),
                    parent.centre().y(),
                    parent.centre().z() + (int) Math.round(distance * Math.sin(angle)));
            if (clearOfKingdom(kingdom, site)) {
                return site;
            }
        }
        // Every direction crowded: plant it far out along the last angle's east.
        return new SimPos(parent.centre().x() + DAUGHTER_DISTANCE * 3, parent.centre().y(), parent.centre().z());
    }

    private static boolean clearOfKingdom(Kingdom kingdom, SimPos site) {
        for (Settlement s : kingdom.settlements()) {
            long limit = s.claimRadius() + 96L;
            if (s.centre().horizontalDistanceSq(site) < limit * limit) {
                return false;
            }
        }
        return true;
    }

    private static void moveHousehold(Settlement from, Settlement to, Household household, SimPos centre) {
        for (Person.Id memberId : List.copyOf(household.members())) {
            Person person = from.removeResident(memberId);
            if (person != null) {
                person.setPosition(centre);
                to.addResident(person);
            }
        }
        from.removeHousehold(household);
        household.setHome(null);
        household.resetGrowthProgress();
        to.addHousehold(household);
    }

    private static long mix(long a, long b) {
        long x = a * 0x9E3779B97F4A7C15L + b;
        x ^= x >>> 27;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 31;
        return x;
    }
}
