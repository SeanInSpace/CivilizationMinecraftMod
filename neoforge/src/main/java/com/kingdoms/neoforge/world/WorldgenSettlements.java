package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.KingdomsConfig;
import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.neoforge.save.SiteLedger;
import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.Founding;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.world.SimWorld;
import com.kingdoms.sim.worldgen.SettlementSites;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * Towns that are already there when you find them.
 *
 * <p>Vanilla generates a structure when a chunk is generated, which is the wrong
 * shape for these settlements entirely: a town here is a hundred and fifty to
 * three hundred blocks across, computed from a center, with roads routed against
 * terrain the generator has not decided yet. It is not a set of chunk-aligned
 * pieces and cannot be made into one without giving up everything that makes it
 * a town rather than a decoration.
 *
 * <p>So the sites are arithmetic and the towns are lazy. {@link SettlementSites}
 * cuts the world into regions and answers, for any of them, whether a town
 * belongs there and whose it is — with no state and no world access, so the same
 * seed always gives the same world. Nothing is built until somebody walks close
 * enough to look, at which point the ground is examined once, a town is raised
 * already built at its stage, and the region is written down as settled so it
 * never happens twice.
 *
 * <p>This is the same doctrine the rest of the mod runs on. A town nobody has
 * been to does not exist yet; it merely will.
 */
public final class WorldgenSettlements {

    private WorldgenSettlements() {
    }

    /**
     * Ticks between sweeps.
     *
     * <p>Once a second. Raising a town reads a good deal of ground, and a player
     * cannot walk out of range of a site and back inside a second, so there is
     * nothing to gain from asking more often.
     */
    public static final int SWEEP_INTERVAL_TICKS = 20;

    /**
     * Sites resolved per sweep.
     *
     * <p>One. Resolving a site scores a disc of ground and then stands a whole
     * town, and doing two in a tick is how a server stutters when somebody flies
     * across a continent. A player crossing several regions at once simply gets
     * them a second apart.
     */
    private static final int PER_SWEEP = 1;

    /**
     * How far a town is moved to find ground worth building on.
     *
     * <p>Wider than a charter's twelve, because nobody chose this spot: the
     * arithmetic put it there and it may have landed on a cliff. Narrower than
     * the region's margin, so a site can never wander into its neighbor's
     * ground and break the separation the grid guarantees.
     */
    private static final int SITING_REACH = 48;

    /** What a town found this way is worth, before anybody has lived in it. */
    private static final SettlementStage STAGE = SettlementStage.VILLAGE;

    /**
     * Looks for a site near each player and raises at most one town.
     *
     * <p>Overworld only. The site grid is dimension-agnostic — it is arithmetic
     * on a seed — while the ledger that remembers what has been settled is per
     * dimension, so without this gate the Nether would raise its own copy of
     * every overworld town, in lava.
     */
    public static void tick(ServerLevel level) {
        if (!KingdomsConfig.WORLDGEN_ENABLED.get()
                || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        SimWorld world = KingdomsMod.simulationFor(level);
        if (world == null) {
            return;   // before the simulation exists there is nowhere to put a town
        }
        SiteLedger ledger = SiteLedger.get(level);
        Map<String, Integer> weights = KingdomsConfig.arrangementWeights();
        long seed = level.getSeed();
        int reach = KingdomsConfig.WORLDGEN_REACH.get();
        int raised = 0;
        for (ServerPlayer player : level.players()) {
            if (raised >= PER_SWEEP) {
                return;
            }
            SimPos at = new SimPos((int) player.getX(), (int) player.getY(),
                    (int) player.getZ());
            for (SettlementSites.Site site : SettlementSites.near(seed, at, reach, weights)) {
                int regionX = SettlementSites.regionXOf(site);
                int regionZ = SettlementSites.regionZOf(site);
                if (ledger.isResolved(regionX, regionZ)) {
                    continue;
                }
                resolve(level, world, ledger, site, regionX, regionZ);
                if (++raised >= PER_SWEEP) {
                    return;
                }
            }
        }
    }

    /**
     * Settles one region, one way or the other.
     *
     * <p>Either outcome is written down. A site refused for its ground must be
     * remembered as refused, or every sweep for the rest of the world's life
     * scores the same hopeless hillside again.
     */
    private static void resolve(ServerLevel level, SimWorld world, SiteLedger ledger,
                                SettlementSites.Site site, int regionX, int regionZ) {
        SimPos wanted = new SimPos(site.centre().x(),
                world.bridge().surfaceHeight(site.centre()), site.centre().z());
        SimPos chosen = Founding.bestSiteNear(wanted, SITING_REACH, world.bridge());
        if (!world.bridge().isSiteSuitable(chosen, TOWN_HEART)) {
            ledger.reject(regionX, regionZ);
            KingdomsMod.LOGGER.info("WORLDGEN region {},{} refused: no ground for a town near {}",
                    regionX, regionZ, site.centre());
            return;
        }

        String name = Culture.of(site.cultureId()).townNames().isEmpty()
                ? "Wayside"
                : pickName(site);
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), name, site.cultureId());
        Settlement settlement = Founding.seeded(chosen, name, STAGE,
                BuildCatalogue.DEFAULT, site.cultureId());
        // The arrangement the world was told it wanted, not the one this people
        // would have picked for this spot. A culture may build several ways; the
        // weights are how a world says which of them it wants to see.
        settlement.setLayoutId(site.layoutId());
        kingdom.addSettlement(settlement);

        world.addKingdom(kingdom);
        KingdomsSavedData.get(level).addKingdom(kingdom);
        ledger.accept(regionX, regionZ, chosen);
        KingdomsMod.LOGGER.info("WORLDGEN raised {} at {} — {} laid out as {}",
                name, chosen, site.cultureId(), site.layoutId());
    }

    /**
     * The ground a town needs before it is worth starting.
     *
     * <p>Only the middle is checked. A settlement grows outward into whatever it
     * finds and has machinery for refusing a plot it cannot use; what it cannot
     * survive is having its hall in a lake.
     */
    private static final int TOWN_HEART = 16;

    /** A name from the people who settled it, chosen by where they settled. */
    private static String pickName(SettlementSites.Site site) {
        List<String> names = Culture.of(site.cultureId()).townNames();
        int at = Math.floorMod(site.centre().x() * 31 + site.centre().z(), names.size());
        return names.get(at);
    }
}
