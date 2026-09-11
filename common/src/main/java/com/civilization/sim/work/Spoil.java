package com.civilization.sim.work;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.HaulTask;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Pockets;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.LumberPlanner;
import com.civilization.sim.settlement.MinePlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.TownStores;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What a broken block is worth, and how it gets from the hole to the shelves.
 *
 * <p><strong>One rule, and it has no exceptions.</strong> Every block a citizen
 * breaks yields its material to that citizen, and what a citizen carries ends up
 * in the town's stores. Site clearing, the trunks leaning on a plot, the canopy
 * over it, the wall line, a road driven through a wood, the earth dug out from
 * under a floor, the lumberjack's felling and the miner's cutting are all the
 * same act — somebody swung at something and it came away in their hands.
 *
 * <p>That is a correction. Ground clearing used to call its timber "spoil" and
 * throw it away on the argument that a town should have to raise a lumber camp
 * before it had any wood. It is the wrong argument: a town that fells six oaks
 * to stand a cottage on their stumps has six oaks' worth of timber, whether or
 * not it has a camp, and a player watching the trees vanish was right to say so.
 * The lumber camp is still what makes timber <em>renewable</em>; it was never
 * what makes a felled tree real.
 *
 * <p><strong>Coarse on purpose.</strong> A town keeps four bulk materials and
 * this maps a whole world of blocks onto them. Anything that is not one of the
 * four — leaves, litter, crops, glass, somebody's furnace — is worth nothing,
 * because charging the world's variety into a ledger with four columns would
 * only make the ledger lie in more places.
 *
 * <p>The table lives here, in the simulation, so that both fidelities read one
 * list. The platform layer's only job is to say which {@link Kind} a block state
 * belongs to — see {@code BlueprintPlacer.spoilOf}, which asks the block tags
 * first and falls back to {@link #ofBlockName} when they are not bound.
 */
public final class Spoil {

    private Spoil() {
    }

    /**
     * What a block comes away as.
     *
     * <p>One unit per block throughout. A log is a timber, a boulder is a stone,
     * a barrow of dirt is an earth — the arithmetic a player can do in their head
     * while watching, which is the only arithmetic worth having here.
     */
    public enum Kind {

        /** Logs, wood, stems, hyphae — anything an axe takes out whole. */
        TIMBER(TownStores.WOOD, 1),

        /** Stone and its kin, and the ores of metals the town does not track. */
        ROCK(TownStores.STONE, 1),

        /** Dirt, sand, gravel, clay, snow: the barrow-load half of a dig. */
        SOIL(TownStores.EARTH, 1),

        /** Iron ore, which is the one metal a town's books have a column for. */
        ORE(TownStores.IRON, 1),

        /** Leaves, litter, crops, glass, and everything else there is. */
        NOTHING(null, 0);

        private final String resource;
        private final int perBlock;

        Kind(String resource, int perBlock) {
            this.resource = resource;
            this.perBlock = perBlock;
        }

        /** The town's name for what this is worth, or null when it is worth nothing. */
        public String resource() {
            return resource;
        }

        /** How much one block of it yields. */
        public int perBlock() {
            return perBlock;
        }

        public boolean isSomething() {
            return resource != null && perBlock > 0;
        }
    }

    /** Endings that make a block timber whatever else its name says. */
    private static final Set<String> TIMBER_ENDINGS =
            Set.of("_log", "_wood", "_stem", "_hyphae");

    /**
     * Stone by name, for a run with no tags bound.
     *
     * <p>Deliberately the rock a town actually digs through rather than every
     * grey block there is. Worked stone somebody has already built with — bricks,
     * slabs, walls — is not on this list and is not meant to be: pulling a
     * neighbor's wall down is demolition, which has its own salvage rules, and
     * the excavation never schedules it.
     */
    private static final Set<String> ROCK_NAMES = Set.of(
            "stone", "cobblestone", "mossy_cobblestone", "smooth_stone",
            "deepslate", "cobbled_deepslate", "polished_deepslate",
            "andesite", "polished_andesite",
            "diorite", "polished_diorite",
            "granite", "polished_granite",
            "tuff", "polished_tuff",
            "calcite", "dripstone_block", "basalt", "blackstone");

    /**
     * Earth by name.
     *
     * <p>Red sand joins the list its ordinary twin is on: a desert is not a
     * different kind of digging.
     */
    private static final Set<String> SOIL_NAMES = Set.of(
            "dirt", "coarse_dirt", "rooted_dirt", "grass_block", "podzol", "mycelium",
            "dirt_path", "mud", "clay", "gravel", "sand", "red_sand", "snow_block");

    /** The one ore the town has a column for. */
    private static final Set<String> IRON_NAMES =
            Set.of("iron_ore", "deepslate_iron_ore", "raw_iron_block");

    /**
     * What a block called this is worth.
     *
     * <p>The fallback half of the classifier, and the half unit tests can reach:
     * block tags are bound when a server loads its datapacks and a JUnit run has
     * no server, so {@code state.is(BlockTags.LOGS)} is simply false there. The
     * registry <em>names</em> are available in both, which makes this the list
     * that can be asserted against.
     *
     * @param name a block's registry path — "oak_log", "deepslate_iron_ore"
     */
    public static Kind ofBlockName(String name) {
        if (name == null || name.isEmpty()) {
            return Kind.NOTHING;
        }
        if (IRON_NAMES.contains(name)) {
            return Kind.ORE;
        }
        for (String ending : TIMBER_ENDINGS) {
            if (name.endsWith(ending)) {
                return Kind.TIMBER;
            }
        }
        if (ROCK_NAMES.contains(name)) {
            return Kind.ROCK;
        }
        if (SOIL_NAMES.contains(name)) {
            return Kind.SOIL;
        }
        // The ores of everything else. A town that does not keep copper still
        // has the rock the vein was in, which is what a pickaxe actually brings
        // up, and is why this is stone rather than nothing.
        if (name.endsWith("_ore")) {
            return Kind.ROCK;
        }
        return Kind.NOTHING;
    }

    /**
     * What a list of broken blocks comes to, by resource.
     *
     * <p>The unwatched half of the rule: nobody is holding anything, so the sum
     * of what would have been dug is credited where the digging would have
     * happened. Insertion-ordered so a report of it reads the same twice.
     */
    public static Map<String, Integer> tally(Iterable<Kind> broken) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Kind kind : broken) {
            if (kind == null || !kind.isSomething()) {
                continue;
            }
            out.merge(kind.resource(), kind.perBlock(), Integer::sum);
        }
        return out;
    }

    /**
     * How much of this the town may hold.
     *
     * <p>The same ceilings the trades are held to, because a town's timber
     * capacity cannot depend on whether the timber came off a lumberjack's axe
     * or off a house plot. What the ceiling refuses is not created: a full store
     * is a full store, and this is exactly what already happens when a lumberjack
     * fells into one.
     */
    public static int ceilingFor(Settlement settlement, String resource) {
        if (TownStores.WOOD.equals(resource)) {
            return LumberPlanner.woodCapacity(settlement);
        }
        if (TownStores.STONE.equals(resource)) {
            return MinePlanner.stoneCapacity(settlement);
        }
        if (TownStores.IRON.equals(resource)) {
            return MinePlanner.MAX_IRON;
        }
        // Earth has never had a ceiling and does not get one here. It is spoil
        // with one use — filling the next dip the town wants to build on — and a
        // cap on it would only mean a hillside site that cannot pay for itself.
        return Integer.MAX_VALUE;
    }

    /** Puts a yield on the nearest shelves to where it was made, under its ceiling. */
    public static int credit(Settlement settlement, SimPos at, String resource, int amount) {
        if (settlement == null || resource == null || amount <= 0) {
            return 0;
        }
        return settlement.produceNear(at, resource, amount, ceilingFor(settlement, resource));
    }

    /** The whole of a tally, credited where it was dug. */
    public static void credit(Settlement settlement, SimPos at, Map<String, Integer> tally) {
        for (Map.Entry<String, Integer> held : tally.entrySet()) {
            credit(settlement, at, held.getKey(), held.getValue());
        }
    }

    /**
     * One block, into the hands of whoever broke it.
     *
     * <p>Into their pockets rather than into the town's books, and that is the
     * whole of the watched half: a citizen who dug a barrow of earth is carrying
     * a barrow of earth, visibly, and the town has it when they have walked it
     * somewhere. Somebody who cannot hold any more simply does not gain it —
     * they are about to be sent to a store with what they already have.
     *
     * @return how much went into their pockets
     */
    public static int gain(Person digger, Kind kind) {
        if (digger == null || kind == null || !kind.isSomething()) {
            return 0;
        }
        return digger.pockets().put(kind.resource(), kind.perBlock());
    }

    /**
     * Sends somebody to put down what they are carrying, if they are carrying any.
     *
     * <p>One errand per material, oldest first, because a load on a back is one
     * load of one thing — the same shape every other errand in the town has. A
     * digger with earth and timber in their pockets makes two trips, and makes
     * them in the order they picked the stuff up.
     *
     * <p>The destination is the nearest store, or the ground where they stand
     * when the town has not raised one: a camp on its first morning has nowhere
     * to put anything, and a rule that waited for shelves would throw away
     * exactly the timber that pays for the first storehouse. Setting it down is
     * {@code HaulPlanner}'s job from here, which is deliberate — the errand is an
     * ordinary haul with an unusual source, and a second mover for it would be a
     * second set of rules about arriving.
     *
     * @return true if an errand was started
     */
    public static boolean startDelivery(Settlement settlement, Person carrier) {
        if (settlement == null || carrier == null || carrier.haul() != null) {
            return false;
        }
        Pockets pockets = carrier.pockets();
        String resource = pockets.first();
        if (resource == null) {
            return false;
        }
        int load = pockets.takeAll(resource);
        if (load <= 0) {
            return false;
        }
        Building store = settlement.nearestStore(carrier.position());
        SimPos to = store == null ? carrier.position() : store.origin();
        HaulTask errand = new HaulTask(resource,
                HaulTask.Store.SELF, carrier.position(),
                HaulTask.Store.STORE, to, load);
        errand.setCarried(load);   // it is already on their back; there is no first leg
        carrier.setHaul(errand);
        return true;
    }
}
