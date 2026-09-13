package com.civilization.neoforge.world;

import com.civilization.neoforge.CivilizationConfig;
import com.civilization.neoforge.CivilizationMod;
import com.civilization.neoforge.bridge.NeoForgeWorldBridge;
import com.civilization.neoforge.save.CivilizationSavedData;
import com.civilization.neoforge.save.SiteLedger;
import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.TownNames;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.kingdom.Kingdom;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.world.SimWorld;
import com.civilization.sim.worldgen.SettlementSites;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 *
 * <p><strong>With one exception, and it is the point of this class now.</strong>
 * Laziness is right everywhere except at the beginning. A player who spawns into
 * an empty world and walks for ten minutes without meeting anybody has been shown
 * nothing of what the mod is; Millénaire's opening was several villages inside a
 * few hundred blocks, findable at once. So the nine regions around the world
 * spawn are guaranteed a site by {@link SettlementSites.Grid} and raised at world
 * start rather than on approach — {@link #tickAnchor} works through them one per
 * tick, so they are standing within a few seconds of the level loading and before
 * anybody has had time to look. Everything past those nine is lazy exactly as
 * before.
 *
 * <p><strong>And the ground is read before any of them is raised.</strong> A town
 * sited on unloaded chunks is sited blind — the terrain test answers "suitable"
 * to ground nobody has looked at — so its plots are a guess, and the guess is
 * found out one building at a time on the step a player walks up to it. See
 * {@link #readClaim}, which generates each claim to real terrain in bounded
 * slices before a single plot is chosen, and {@code Founding.seeded}'s bridge
 * overload, which is what then does the choosing.
 */
public final class WorldgenSettlements {

    private WorldgenSettlements() {
    }

    /**
     * Dimensions whose spawn towns are settled, so the anchor stops asking.
     *
     * <p>The ledger is the authority and this is only a memo over it: everything
     * {@link #tickAnchor} decides is recomputed from the seed and checked against
     * the ledger, so losing this set costs nine hash evaluations and changes
     * nothing. That is deliberate — it is what makes the anchor idempotent, and
     * what lets a world created before any of this existed pick up its spawn
     * towns on the next load without a migration.
     */
    private static final Set<ResourceKey<Level>> ANCHORED = new HashSet<>();

    /** Forgets the memo when the world it describes goes away. */
    public static void forget() {
        ANCHORED.clear();
    }

    /**
     * Whether this level's spawn towns are standing, or never will be.
     *
     * <p>Asked by the join greeting, which has a race to lose otherwise: the
     * nine go up one a tick from the moment the level loads, and a player
     * joining a single-player world is in before the first of them. Told "a
     * Norman crossroads, 186 blocks northeast, not raised yet" about all nine,
     * they would be reading a promise instead of a directory.
     *
     * <p>True immediately where there is nothing to wait for — worldgen off, or
     * a dimension the anchor does not touch.
     */
    public static boolean spawnTownsSettled(ServerLevel level) {
        return !CivilizationConfig.WORLDGEN_ENABLED.get()
                || !level.dimension().equals(Level.OVERWORLD)
                || ANCHORED.contains(level.dimension());
    }

    /**
     * How far out the anchor will look if all nine spawn regions refuse.
     *
     * <p>Three regions. Nine refusals means spawn is in the middle of an ocean
     * or a mountain range, which is rare and not impossible; rather than leave
     * the promise broken this widens to the ordinary scattered sites and takes
     * the nearest ground that will hold a town. It may be a long walk. It is
     * still a town.
     */
    private static final int FALLBACK_REGIONS = 3;

    /**
     * The site grid this level uses, anchored on its own spawn point.
     *
     * <p>Read fresh rather than cached, because both dials are config and an
     * operator may change them between sessions — and the anchor moves if
     * somebody moves the world spawn, which is a thing {@code /setworldspawn}
     * does. A grid is three fields; building one is not worth remembering.
     */
    public static SettlementSites.Grid gridFor(ServerLevel level) {
        return CivilizationConfig.siteGrid(worldSpawn(level));
    }

    /** Where this world starts a player, as the simulation counts positions. */
    public static SimPos worldSpawn(ServerLevel level) {
        BlockPos at = level.getRespawnData().pos();
        return new SimPos(at.getX(), at.getY(), at.getZ());
    }

    /**
     * Raises the towns around the world spawn, one per tick until they stand.
     *
     * <p>On its own beat rather than the sweep's, and the beat is every tick:
     * the whole value of these nine is that they are there before the player
     * looks, and nine sweeps a second apart is nine seconds of an empty world.
     * One town a tick for nine ticks is inside the terrain oracle's own per-tick
     * budget — the hard promise that class makes — and finishes in under half a
     * second.
     *
     * <p>Costs nothing once they are settled, and nothing on a world where
     * worldgen is off.
     */
    public static void tickAnchor(ServerLevel level) {
        if (!CivilizationConfig.WORLDGEN_ENABLED.get()
                || !level.dimension().equals(Level.OVERWORLD)
                || ANCHORED.contains(level.dimension())) {
            return;
        }
        SimWorld world = CivilizationMod.simulationFor(level);
        if (world == null) {
            return;
        }
        SiteLedger ledger = SiteLedger.get(level);
        SettlementSites.Grid grid = gridFor(level);
        Map<String, Integer> weights = CivilizationConfig.arrangementWeights();
        long seed = level.getSeed();

        // The nine, nearest the spawn point first. Refused ground still refuses;
        // taking them in this order is what makes "then try the next one out"
        // fall out of the loop rather than needing to be written.
        boolean anyStanding = false;
        for (int[] region : grid.anchoredRegions(seed)) {
            Optional<SiteLedger.Entry> decided = ledger.entry(region[0], region[1]);
            if (decided.isEmpty()) {
                // A resolve that answers "still reading its ground" writes no
                // ledger entry, so the next tick comes back to this same region
                // and pays for the next slice of chunks. Nine ticks a town rather
                // than one, and the town that stands at the end of them is
                // standing where it looks like it is.
                grid.siteIn(seed, region[0], region[1], weights).ifPresent(site ->
                        resolve(level, world, ledger, site, region[0], region[1]));
                return;   // one a tick
            }
            anyStanding |= decided.get().accepted();
        }
        if (anyStanding) {
            ANCHORED.add(level.dimension());
            return;
        }

        // All nine looked at, all nine refused. Widen rather than break the
        // promise: the ordinary scattered sites, nearest the spawn point first.
        SimPos spawn = worldSpawn(level);
        for (SettlementSites.Site site
                : grid.near(seed, spawn, FALLBACK_REGIONS * grid.region(), weights)) {
            int regionX = grid.regionXOf(site);
            int regionZ = grid.regionZOf(site);
            Optional<SiteLedger.Entry> decided = ledger.entry(regionX, regionZ);
            if (decided.isEmpty()) {
                resolve(level, world, ledger, site, regionX, regionZ);
                return;
            }
            if (decided.get().accepted()) {
                ANCHORED.add(level.dimension());
                return;
            }
        }
        ANCHORED.add(level.dimension());
        CivilizationMod.LOGGER.warn(
                "WORLDGEN no ground for a town within {} blocks of the world spawn {}",
                FALLBACK_REGIONS * grid.region(), spawn);
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
     * What a goblin camp is worth, which is as much as a camp ever gets.
     *
     * <p>TOWN rather than VILLAGE, and it is not a camp being flattered. A
     * settlement's stage is how far up the founding ladder it has climbed, and the
     * ladder's last two rungs are the ones a camp actually wants: nothing in this
     * mod stakes a palisade before TOWN ({@code PerimeterPlanner}), and the TOWN
     * program is what raises the hall — which for these people is the chieftain's
     * hut, and no chieftain is crowned until it stands.
     *
     * <p>So a village found in the world is a village still growing and a camp
     * found in the world is finished, which is the truthful pair: a camp is not a
     * town that has not got going yet. It is the whole of what a band of goblins
     * ever builds, and what it does from there is raid.
     */
    private static final SettlementStage CAMP_STAGE = SettlementStage.TOWN;

    /**
     * Goblins in a camp the world wrote down.
     *
     * <p>Eight, which is the number the user asked for and also what the camp's own
     * TOWN program houses: a hovel for three, two tents for two, and the chieftain's
     * hut for three, less the one bed the plan leaves spare. Passed outright rather
     * than derived from the beds, so a camp is eight goblins whatever the housing
     * table does next — a camp of five reads as a camp that has already lost a
     * fight.
     */
    private static final int CAMP_GOBLINS = 8;

    /**
     * Where a goblin camp is allowed to stand.
     *
     * <p>Swamp, mangrove swamp, dark forest and both old-growth taigas — bog and
     * deep wood, which is where everybody has always put goblins and, more to the
     * point, is terrain a player crosses rather than settles. A camp in a plains
     * biome would be a camp in somebody's front garden.
     *
     * <p><strong>The biome is read here and not in the site chooser.</strong>
     * {@code SettlementSites} is arithmetic on a seed with no world behind it —
     * that is the whole reason a world's towns can be enumerated without loading a
     * chunk — and a biome needs a level. So the draw is blind and this is where it
     * is checked, on ground that has just been generated for the purpose.
     */
    private static final Set<ResourceKey<net.minecraft.world.level.biome.Biome>>
            GOBLIN_COUNTRY = Set.of(
                    net.minecraft.world.level.biome.Biomes.SWAMP,
                    net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP,
                    net.minecraft.world.level.biome.Biomes.DARK_FOREST,
                    net.minecraft.world.level.biome.Biomes.OLD_GROWTH_PINE_TAIGA,
                    net.minecraft.world.level.biome.Biomes.OLD_GROWTH_SPRUCE_TAIGA);

    /** Whether this ground is bog or deep wood enough for a camp. */
    private static boolean inGoblinCountry(ServerLevel level, SimPos at) {
        return level.getBiome(new BlockPos(at.x(), at.y(), at.z()))
                .unwrapKey()
                .map(GOBLIN_COUNTRY::contains)
                .orElse(false);
    }

    /**
     * The same site, with the hostile arrangements taken out of the draw.
     *
     * <p>What a region that drew a camp on the wrong ground gets instead. Re-drawn
     * rather than rejected, and that is the interesting half: rejecting would have
     * made the goblin weight a hole in the map, so a world configured for one camp
     * in five regions would have had a fifth of its regions empty wherever the
     * ground was not boggy. The jitter that places a site reads its own hash stream
     * and not the weights, so the town lands on exactly the same spot — only the
     * people change.
     */
    private static Optional<SettlementSites.Site> withoutTheGoblins(
            SettlementSites.Grid grid, long seed, int regionX, int regionZ,
            Map<String, Integer> weights) {
        Map<String, Integer> friendly = new java.util.LinkedHashMap<>();
        weights.forEach((layoutId, weight) -> friendly.put(layoutId,
                Culture.all().stream()
                        .filter(culture -> culture.layouts().contains(layoutId))
                        .allMatch(Culture::isHostile) ? 0 : weight));
        return grid.siteIn(seed, regionX, regionZ, friendly);
    }

    /**
     * Looks for a site near each player and raises at most one town.
     *
     * <p>Overworld only. The site grid is dimension-agnostic — it is arithmetic
     * on a seed — while the ledger that remembers what has been settled is per
     * dimension, so without this gate the Nether would raise its own copy of
     * every overworld town, in lava.
     */
    public static void tick(ServerLevel level) {
        if (!CivilizationConfig.WORLDGEN_ENABLED.get()
                || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        SimWorld world = CivilizationMod.simulationFor(level);
        if (world == null) {
            return;   // before the simulation exists there is nowhere to put a town
        }
        SiteLedger ledger = SiteLedger.get(level);
        SettlementSites.Grid grid = gridFor(level);
        Map<String, Integer> weights = CivilizationConfig.arrangementWeights();
        long seed = level.getSeed();
        int reach = CivilizationConfig.WORLDGEN_REACH.get();
        int raised = 0;
        for (ServerPlayer player : level.players()) {
            if (raised >= PER_SWEEP) {
                return;
            }
            SimPos at = new SimPos((int) player.getX(), (int) player.getY(),
                    (int) player.getZ());
            for (SettlementSites.Site site : grid.near(seed, at, reach, weights)) {
                int regionX = grid.regionXOf(site);
                int regionZ = grid.regionZOf(site);
                if (ledger.isResolved(regionX, regionZ)) {
                    continue;
                }
                if (!resolve(level, world, ledger, site, regionX, regionZ)) {
                    // Its claim is still being read. That is this sweep's work —
                    // reading ground is the expensive half — so stop here and
                    // carry on with the same site a second from now.
                    return;
                }
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
    private static boolean resolve(ServerLevel level, SimWorld world, SiteLedger ledger,
                                   SettlementSites.Site site, int regionX, int regionZ) {
        // groundHeight, not surfaceHeight. surfaceHeight answers an unloaded
        // column with the y it was handed, and every column here is unloaded --
        // this runs before anybody has been near the place. The playtest's
        // village was therefore founded at y=0 and handed that height to every
        // one of its fourteen buildings.
        SimPos wanted = new SimPos(site.center().x(),
                world.bridge().groundHeight(site.center()), site.center().z());
        SimPos chosen = Founding.bestSiteNear(wanted, SITING_REACH, world.bridge());

        // And now the ground, before a single plot is chosen. Bounded, and
        // resumed next tick when the budget runs out: see readClaim.
        if (!readClaim(world, chosen, Founding.groundToRead(chosen, site.layoutId(),
                BuildCatalog.DEFAULT))) {
            return false;   // still reading; the region is undecided, so come back
        }
        // Re-asked against read ground rather than the estimate it was asked
        // against a moment ago, and at the real height. A region whose middle
        // turns out to be a ravine is now refused here instead of raising a town
        // that scatters when somebody walks up to it.
        chosen = new SimPos(chosen.x(), world.bridge().groundHeight(chosen), chosen.z());
        if (!world.bridge().isSiteSuitable(chosen, TOWN_HEART)) {
            ledger.reject(regionX, regionZ);
            CivilizationMod.LOGGER.info("WORLDGEN region {},{} refused: no ground for a town near {}",
                    regionX, regionZ, site.center());
            return true;
        }

        // Goblins belong in a bog or a deep wood and nowhere else. The draw is
        // blind to biome -- it has no world behind it -- so this is where a camp
        // that landed in a wheat field becomes an ordinary town instead. Re-drawn
        // on the same ground, so the region keeps its site.
        if (Culture.of(site.cultureId()).isHostile() && !inGoblinCountry(level, chosen)) {
            Optional<SettlementSites.Site> instead = withoutTheGoblins(
                    gridFor(level), level.getSeed(), regionX, regionZ,
                    CivilizationConfig.arrangementWeights());
            if (instead.isEmpty()) {
                ledger.reject(regionX, regionZ);
                return true;
            }
            site = instead.get();
        }

        String name = Culture.of(site.cultureId()).townNames().isEmpty()
                ? pickName(world, site, List.of("Wayside"))
                : pickName(world, site, Culture.of(site.cultureId()).townNames());
        // A shire is named after its town and a warband after its chief's line.
        // The settlement keeps the town name either way; only the realm differs.
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(),
                Kingdom.nameFor(Culture.of(site.cultureId()), name,
                        chosen.x(), chosen.z()),
                site.cultureId());
        // The arrangement the world was told it wanted, not the one this people
        // would have picked for this spot. A culture may build several ways; the
        // weights are how a world says which of them it wants to see. Named
        // before a single plot is taken: set afterwards, the buildings stood on
        // the people's default plan and the streets were drawn for this one.
        //
        // The bridge is handed in, which is the whole of this fix: every plot is
        // put to the same terrain test the player's arrival will apply, on ground
        // that has just been read, so a town that looks right from above is right
        // from the ground as well. See Founding.seeded's bridge overload.
        boolean camp = Culture.of(site.cultureId()).isHostile();
        Settlement settlement = Founding.seeded(chosen, name,
                camp ? CAMP_STAGE : STAGE,
                BuildCatalog.DEFAULT, site.cultureId(),
                camp ? CAMP_GOBLINS : Founding.AS_THE_STAGE_HOUSES,
                site.layoutId(), world.bridge());
        kingdom.addSettlement(settlement);

        world.addKingdom(kingdom);
        CivilizationSavedData.get(level).addKingdom(kingdom);
        ledger.accept(regionX, regionZ, chosen);
        CivilizationMod.LOGGER.info("WORLDGEN raised {} at {} — {} laid out as {}",
                name, chosen, site.cultureId(), site.layoutId());
        return true;
    }

    /**
     * Chunks of a claim generated to real ground in one tick.
     *
     * <p>Eight. The arithmetic that decides it is in {@link ClaimGround}: a
     * sixty-four block claim is sixty-two to sixty-nine chunks depending on where
     * in its own chunk the center falls, so a town's ground is in hand after nine
     * ticks and the nine spawn towns after about eighty — under four seconds of
     * world start, with nobody yet in the world to feel any of it.
     *
     * <p>Not the whole claim at once, which was the first shape of this and is
     * what the class's own comment on {@link #PER_SWEEP} warns against: seventy
     * cold chunks generated in a tick is a tick a player standing nearby notices,
     * and the on-approach path runs in front of somebody by definition. Not the
     * oracle's ordinary two a tick either — that is a query budget, sized so a
     * planner weighing a hillside cannot stall a tick, and at two a town would
     * take thirty-five ticks and the nine over five hundred.
     *
     * <p>The read costs nothing at all the second time: a chunk already read is
     * skipped, and on the approach path most of the claim is loaded or generated
     * already because the player is inside {@code worldgen.reach} of it.
     */
    private static final int CLAIM_CHUNKS_PER_TICK = 8;

    /**
     * Reads the ground under a town's claim, a tick's worth at a time.
     *
     * <p>The one expensive thing this class does, and it buys the whole of the
     * siting fix: until the chunks have real terrain in them the terrain test
     * answers "suitable" to everything, so a town lays its plots blind and then
     * rearranges itself on the step somebody arrives.
     *
     * <p>Generated to the carvers and no further, so ravines are in it and mobs
     * and redstone are not — a read chunk here is not a ticking chunk. See
     * {@link TerrainOracle#readGround}.
     *
     * <p><strong>As far as the plan reaches, not as far as the claim does.</strong>
     * This used to read {@code Founding.INITIAL_CLAIM} — sixty-four blocks — and a
     * village's plan reaches ninety to a hundred and thirty. So the outer four
     * plots of every seeded town were chosen against the generator's noise, judged
     * by the loose allowance an estimate gets, and then re-judged strictly the
     * moment somebody walked up and the real chunks came in. Four of fourteen
     * buildings moved on arrival in the playtest, and those were the four. See
     * {@link Founding#groundToRead}, which is where the number comes from.
     *
     * @param reach how far out the ground has to be read, in blocks
     * @return whether the claim is now read, so the town may be raised
     */
    private static boolean readClaim(SimWorld world, SimPos center, int reach) {
        if (!(world.bridge() instanceof NeoForgeWorldBridge bridge)) {
            return true;   // no oracle behind this world; nothing to read
        }
        int owed = bridge.oracle().readGround(center.x(), center.z(),
                reach, CLAIM_CHUNKS_PER_TICK);
        return owed == 0;
    }

    /**
     * The ground a town needs before it is worth starting.
     *
     * <p>Only the middle is checked. A settlement grows outward into whatever it
     * finds and has machinery for refusing a plot it cannot use; what it cannot
     * survive is having its hall in a lake.
     */
    private static final int TOWN_HEART = 16;

    /**
     * A name from the people who settled it, chosen by where they settled — and
     * never one that is already standing somewhere else.
     *
     * <p>The hash is unchanged and is still what makes a seed reproduce: it
     * decides where in the pool to start looking. What is new is that it is a
     * starting point rather than the answer. Two of the nine spawn towns came out
     * of a playtest both called Bellbrook, because nothing asked.
     *
     * <p>Every name in the world counts, whoever settled it. A Norman Bellbrook
     * and an Anglian Bellbrook are two places with one name on a map and in a
     * {@code /civ} report, and the culture that happened to get there first is no
     * comfort to a player reading either.
     *
     * <p>The walking and the qualifying are {@link TownNames}', so they can be
     * put to a pool smaller than the number of towns without a world to generate.
     */
    private static String pickName(SimWorld world, SettlementSites.Site site,
                                   List<String> pool) {
        return TownNames.pick(pool,
                site.center().x() * 31 + site.center().z(), namesTaken(world));
    }

    /** Every settlement name standing in this world, of any culture. */
    private static Set<String> namesTaken(SimWorld world) {
        Set<String> taken = new HashSet<>();
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                taken.add(settlement.name());
            }
        }
        return taken;
    }
}
