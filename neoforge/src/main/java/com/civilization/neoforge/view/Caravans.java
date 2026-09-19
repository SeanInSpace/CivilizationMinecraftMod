package com.civilization.neoforge.view;

import com.civilization.neoforge.entity.Pace;
import com.civilization.neoforge.entity.PersonEntity;
import com.civilization.neoforge.CivilizationEntities;
import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.Caravan;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The caravan the inn trades with, walking up the road with two llamas behind it.
 *
 * <p><strong>This is theatre and is worth nothing.</strong> {@code InnPlanner}
 * books the trade on its own clock — so many loaves off the shelf, so much iron
 * onto it, one line in the log — and it does that whether a player is standing
 * in the street or a thousand blocks away. Nothing in this file touches a store,
 * a ledger, an event log or a person. Run it or do not run it and the save comes
 * out identical, which is the rule the whole mod is built on and the one a body
 * that only exists while somebody is looking is most likely to break.
 *
 * <p><strong>What it does.</strong> On the steps {@link Caravan#isVisiting} says
 * a wagon is due, a trader is put down at the town's edge — a gate if the wall
 * has one, else the far end of the longest opened street, which is
 * {@code TownEdge}'s question — walks up the road to the inn, stands about in the
 * yard, and then walks back out and is gone. Two pack llamas with chests on them
 * come in behind him on a leash and leave with him.
 *
 * <p><strong>{@link Pens}'s rules, with one deliberate difference.</strong> Spawn
 * the missing and never delete anything that is not ours; one caravan to a town,
 * ever; pinned against the mob cap, because a trader the game decided was surplus
 * halfway up the street is a trader who never arrives. The difference is the
 * ground: Pens puts its loose fowl on grass and never on a road, because a
 * chicken in the carriageway is the most obvious way loose dressing goes wrong. A
 * caravan is the opposite — it arrives <em>on</em> the road, because that is what
 * the road is for, and putting it on the verge beside the gate would be a wagon
 * that came cross-country.
 *
 * <p>The bodies are not on the books and are not meant to be. They carry no
 * person id, so {@code PersonEntityManager.reapOrphans} never sees them, and
 * every one of them is discarded the moment the visit ends. What they
 * <em>are</em> is saved with their chunk like any other entity, which is the
 * one way a wagon could outlive its visit: a chunk that unloaded mid-visit, or a
 * world closed with a trader in the yard, would hand the next session a body
 * nothing remembers. So every body is tagged {@link #CARAVAN_TAG}, and
 * {@code CivilizationMod.onEntityJoin} refuses a tagged body that {@link #owns}
 * does not know — which is every one that came off disk, since the map is
 * emptied on close and cleared the moment a trader cannot be found. A world
 * reloaded mid-visit therefore has no caravan in it, which is right: the wagon
 * was never on the books in the first place.
 */
public final class Caravans {

    private Caravans() {
    }

    /**
     * The mark every caravan body carries, so a body that came off disk can be
     * told from one this class put down. See the class comment.
     */
    public static final String CARAVAN_TAG = "civilization_caravan";

    /** Whether this entity is one of a caravan's bodies, ours or a stale one. */
    public static boolean isCaravanBody(Entity entity) {
        return entity != null && entity.entityTags().contains(CARAVAN_TAG);
    }

    /**
     * Whether a body with this id belongs to a wagon that is on the ground now.
     *
     * <p>The join hook's question. A tagged body that answers no here is one a
     * chunk or a save handed back after the visit that made it was over, and it
     * is refused rather than left to stand in an inn yard for ever.
     */
    public static boolean owns(UUID entityId) {
        for (Visit visit : VISITING.values()) {
            if (visit.trader().equals(entityId) || visit.pack().contains(entityId)) {
                return true;
            }
        }
        return false;
    }

    /** Pack animals behind the trader: two, which is a wagon's worth and not a herd. */
    private static final int PACK_ANIMALS = 2;

    /** What the pack animals are. A llama with a chest on it is a pack llama. */
    private static final String PACK_BEAST = "minecraft:llama";

    /** How near the inn counts as being at it. */
    private static final double ARRIVED = 3.0;

    /** How near the edge counts as gone. */
    private static final double AWAY = 2.5;

    /**
     * Passes a trader is given to get out of town before it is simply removed.
     *
     * <p>A leaving caravan walks to the edge and despawns when it gets there.
     * Sometimes it cannot get there — the street it came up has been built over,
     * a player has fenced the gate, the pathfinder has given up on a staircase —
     * and a trader stuck halfway is a permanent resident nobody invited. Half a
     * minute of trying — a manager pass is {@code TICK_INTERVAL} at twenty ticks,
     * so thirty of them is thirty seconds — and then it is simply gone, which
     * nobody sees because nobody watches a stranger leave.
     */
    private static final int LEAVING_PASSES = 30;

    /** One town's wagon: the bodies it is made of and how it is getting on. */
    private record Visit(UUID trader, List<UUID> pack, long arrivedOn) {
    }

    /** Which towns have a wagon in them. One entry each, which is the cap. */
    private static final Map<Settlement.Id, Visit> VISITING = new HashMap<>();

    /** How long each town's wagon has been trying to leave. */
    private static final Map<Settlement.Id, Integer> LEAVING = new HashMap<>();

    /**
     * Who is holding the seat under each town's trader.
     *
     * <p>{@code Pastimes} owns the seat entity and is the only thing that can
     * take it out of the world, so a wagon that has asked for one has to
     * remember which seating it asked — there is one per level and the static
     * maps up here are not. Entered when the trader sits and dropped by
     * {@link #clear} and {@link #forget}, which are the two ways a visit ends.
     */
    private static final Map<Settlement.Id, Pastimes> SEATED = new HashMap<>();

    /**
     * Drops everything remembered about a world that is closing.
     *
     * <p>{@code FurnishingLayer.forget}'s housekeeping. The entity ids in here
     * belong to a level that is going away, and a stale one would have the next
     * session's first pass looking up a body that cannot exist.
     *
     * <p>And it gets every trader out of his chair first. A seat is an entity of
     * our own making that is saved with its chunk, so a world closed on a
     * trader sitting at the inn would otherwise leave an invisible block display
     * in the yard that nothing remembers and nothing can find — the exact fault
     * {@code Pastimes.stop} exists to prevent for a resident, said again for
     * somebody who is on no roster at all.
     */
    public static void forget() {
        for (Map.Entry<Settlement.Id, Pastimes> held : SEATED.entrySet()) {
            Visit visit = VISITING.get(held.getKey());
            if (visit != null) {
                held.getValue().releaseVisitor(visit.trader());
            }
        }
        SEATED.clear();
        VISITING.clear();
        LEAVING.clear();
    }

    /**
     * One pass of the caravan, for one town.
     *
     * <p>Called from the manager's own pass, so "the town is watched" is already
     * true of everything else running beside it — but not of the <em>edge</em>,
     * which is the far end of a street and may be well outside the radius that
     * embodied anybody. So it is asked again, about the column the wagon would
     * actually stand on.
     *
     * <p>Every pass, with no beat of its own. {@code Pens} keeps one because
     * reconciling a pen means counting the entities in a box, which is a query
     * worth doing rarely; this reissues a navigation target, which is what
     * {@code Pastimes} does to every idle body in the town on every pass. A
     * counter shared across settlements would be worse than none anyway: it
     * advances once per town per pass, so in a world holding a multiple of its
     * period in towns the same one wins every time and the rest never run at all.
     */
    static void tend(ServerLevel level, SimWorld world, Settlement settlement,
                     Pastimes seating) {
        Visit visit = VISITING.get(settlement.id());
        boolean due = Caravan.isVisiting(world.stepsElapsed());
        if (visit == null) {
            if (!due || !Caravan.hasAnInn(settlement)) {
                return;
            }
            // The cheap question before the dear one. This runs for every town
            // in the world every pass, and the edge is every gate against every
            // opened run; a town with nobody anywhere near it is refused on a
            // distance before its streets are walked at all. Generous on
            // purpose: the edge can lie a claim's width from the middle.
            if (!world.bridge().playerWithin(settlement.center(),
                    world.settings().observedRadius() + settlement.claimRadius())) {
                return;
            }
            SimPos edge = Caravan.entersAt(settlement);
            if (edge != null) {
                arrive(level, world, settlement, edge);
            }
            return;
        }
        // A wagon on the ground is only kept while the town it is calling on
        // can still be called on; a street built over mid-visit sends it home.
        due = due && Caravan.callsAt(settlement);
        if (!alive(level, visit)) {
            // Something killed the trader, or its chunk went away and took it
            // with it. Either way the wagon is over; the books never knew.
            clear(level, settlement, visit, seating);
            return;
        }
        if (due && visit.arrivedOn() == Caravan.arrivedOn(world.stepsElapsed())) {
            stayAWhile(level, settlement, visit, seating);
            return;
        }
        leave(level, settlement, visit, seating);
    }

    // --- coming in -------------------------------------------------------------

    /**
     * Puts a trader and its animals down at the edge of town.
     *
     * <p>Refused outright if the edge is not loaded. A body spawned into ground
     * nobody has read is a body standing on nothing, and the point of the whole
     * feature is a wagon somebody watches come up the road.
     */
    private static void arrive(ServerLevel level, SimWorld world, Settlement settlement,
                               SimPos edge) {
        BlockPos gate = footing(level, edge);
        if (gate == null || !level.isLoaded(gate)) {
            return;
        }
        if (!world.bridge().playerWithin(
                new SimPos(gate.getX(), gate.getY(), gate.getZ()),
                world.settings().observedRadius())) {
            return;   // nobody to see it arrive, so nothing arrives
        }
        PersonEntity trader = new PersonEntity(CivilizationEntities.PERSON.get(), level);
        // The race of the town it is calling on rather than of the town it came
        // from, because nothing knows where it came from — see Caravan.nameFor,
        // which names the nearest neighbour and is honest about guessing. A
        // trader who looked like a different people would be making a claim about
        // the world that the simulation cannot back up.
        trader.applyRace(Culture.of(settlement.cultureId()).race(), UUID.randomUUID());
        trader.setPos(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5);
        trader.setCustomName(Component.literal(Caravan.nameFor(world, settlement)));
        trader.setCustomNameVisible(true);
        trader.setPersistenceRequired();
        if (!level.addFreshEntity(trader)) {
            return;
        }
        // Tagged after it is in the world, not before: the join hook refuses a
        // tagged body it does not own, and the wagon is not owned until the
        // map entry below. The tag is saved with the entity, which is the point.
        trader.addTag(CARAVAN_TAG);
        List<UUID> pack = new ArrayList<>();
        for (int beast = 0; beast < PACK_ANIMALS; beast++) {
            // Each animal gets the footing the trader got. A block south of the
            // gate is as often a palisade post or a drop as it is road, and a
            // llama put down inside a post is a llama that suffocates on a leash.
            BlockPos spot = footing(level, new SimPos(gate.getX(), gate.getY(),
                    gate.getZ() + beast + 1));
            if (spot == null) {
                continue;
            }
            Mob animal = packAnimal(level, spot, trader);
            if (animal != null) {
                animal.addTag(CARAVAN_TAG);
                pack.add(animal.getUUID());
            }
        }
        VISITING.put(settlement.id(),
                new Visit(trader.getUUID(), List.copyOf(pack),
                        Caravan.arrivedOn(world.stepsElapsed())));
        LEAVING.remove(settlement.id());
    }

    /**
     * One pack llama, chested and leashed to the trader.
     *
     * <p>A chest because that is what makes a llama read as freight rather than
     * as livestock somebody lost, and a leash because vanilla's own caravan goal
     * only makes a llama follow <em>another llama</em> — there is no goal that
     * makes one follow a person. A leash drags elastically and snaps at distance,
     * which is exactly the behavior wanted: the animals trail the trader up the
     * street and, if the path takes him somewhere they cannot follow, the leash
     * gives rather than teleporting anybody.
     *
     * <p>Pinned like everything else here, and never bred, fed, counted or put on
     * anybody's books. {@code Herd} is the ledger for animals a town owns; these
     * are not the town's.
     */
    private static Mob packAnimal(ServerLevel level, BlockPos at, Entity leader) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                .getOptional(Identifier.parse(PACK_BEAST)).orElse(null);
        if (type == null) {
            return null;   // a game without llamas in it, which is allowed
        }
        Entity spawned = type.spawn(level, at, EntitySpawnReason.MOB_SUMMONED);
        if (!(spawned instanceof Mob animal)) {
            if (spawned != null) {
                spawned.discard();
            }
            return null;
        }
        animal.setPersistenceRequired();
        if (animal instanceof AbstractChestedHorse packed) {
            packed.setChest(true);
        }
        animal.setLeashedTo(leader, true);
        return animal;
    }

    // --- being here, and going again -------------------------------------------

    /**
     * Walks the trader to the inn and sits it down once it is there.
     *
     * <p>Sitting rather than standing, and the seat is asked of {@code Pastimes}
     * rather than made here, because the seat is a live entity and this class
     * has no lifecycle for one — {@code Pastimes} already owns every
     * {@code civilization_seat} in the world and already knows how to sweep one
     * up. What it did <em>not</em> know is that a body might not be on anybody's
     * roster; see {@code Pastimes.seatVisitor}, which is that and nothing else.
     *
     * <p>Once seated the trader is left entirely alone. A rider has no
     * navigation worth issuing and re-aiming one every pass at a yard he is
     * three blocks off — because the bench beside the door was — would be a
     * trader shuffling in his chair for the whole visit.
     */
    private static void stayAWhile(ServerLevel level, Settlement settlement, Visit visit,
                                   Pastimes seating) {
        PersonEntity trader = traderOf(level, visit);
        if (trader == null) {
            return;
        }
        if (seating != null && seating.isVisitorSeated(visit.trader())) {
            return;   // sat down, and staying sat
        }
        SimPos stall = Caravan.standsAt(settlement);
        BlockPos yard = footing(level, stall);
        if (yard == null) {
            return;
        }
        BlockPos seat = seating == null ? yard : seating.visitorSeatNear(yard);
        if (near(trader, seat, ARRIVED)) {
            // Where a caravan gets to when it gets where it is going: off the
            // road, facing the inn, and sitting down if there is anything to sit
            // on. Facing it rather than wherever the pathfinder left him looking.
            trader.getNavigation().stop();
            trader.getLookControl().setLookAt(
                    yard.getX() + 0.5, yard.getY() + 1.0, yard.getZ() + 0.5);
            if (seating != null && seating.seatVisitor(visit.trader(), trader, seat)) {
                SEATED.put(settlement.id(), seating);
            }
            return;
        }
        trader.getNavigation().moveTo(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5,
                Pace.WALK);
    }

    /**
     * Walks the trader back out and takes the whole wagon off the map when it
     * gets there — or when it plainly is not going to.
     *
     * <p>Out of the chair first, and unconditionally. A rider is not steered by
     * anything, so a trader still sitting when his visit ended would take every
     * one of {@link #LEAVING_PASSES} standing still and then be removed from the
     * seat he was sitting on — which works, and which is half a minute of a
     * stranger who will not leave.
     */
    private static void leave(ServerLevel level, Settlement settlement, Visit visit,
                              Pastimes seating) {
        if (seating != null) {
            seating.releaseVisitor(visit.trader());
            SEATED.remove(settlement.id());
        }
        PersonEntity trader = traderOf(level, visit);
        int tries = LEAVING.merge(settlement.id(), 1, Integer::sum);
        SimPos edge = Caravan.entersAt(settlement);
        BlockPos gate = edge == null ? null : footing(level, edge);
        if (trader == null || gate == null || tries > LEAVING_PASSES
                || near(trader, gate, AWAY)) {
            clear(level, settlement, visit, seating);
            return;
        }
        trader.getNavigation().moveTo(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5,
                Pace.WALK);
    }

    /**
     * Takes one town's wagon off the map.
     *
     * <p>By the exact ids that were put down, and nothing else. That is the half
     * of {@code Pens}'s rule that matters most here: a sweep that discarded
     * "whatever llamas are near the gate" would eat the pair a player walked in
     * from three biomes away, and no apology covers that.
     */
    private static void clear(ServerLevel level, Settlement settlement, Visit visit,
                              Pastimes seating) {
        // Before the body goes, for the reason PersonEntityManager.release gets
        // its people out of their chairs before it despawns them: the seat is an
        // entity of ours and a body discarded while riding one leaves it in the
        // yard for ever, invisible, with nothing left that remembers it.
        if (seating != null) {
            seating.releaseVisitor(visit.trader());
        }
        SEATED.remove(settlement.id());
        VISITING.remove(settlement.id());
        LEAVING.remove(settlement.id());
        for (UUID id : visit.pack()) {
            Entity animal = level.getEntity(id);
            if (animal != null && !animal.isRemoved()) {
                if (animal instanceof Mob led) {
                    led.dropLeash();
                }
                animal.discard();
            }
        }
        Entity trader = level.getEntity(visit.trader());
        if (trader != null && !trader.isRemoved()) {
            trader.discard();
        }
    }

    // --- the plumbing ----------------------------------------------------------

    private static boolean alive(ServerLevel level, Visit visit) {
        Entity trader = level.getEntity(visit.trader());
        return trader instanceof PersonEntity body && body.isAlive() && !body.isRemoved();
    }

    private static PersonEntity traderOf(ServerLevel level, Visit visit) {
        return level.getEntity(visit.trader()) instanceof PersonEntity body
                && body.isAlive() ? body : null;
    }

    private static boolean near(Entity who, BlockPos where, double reach) {
        double dx = who.getX() - (where.getX() + 0.5);
        double dz = who.getZ() - (where.getZ() + 0.5);
        return dx * dx + dz * dz <= reach * reach;
    }

    /**
     * Somewhere at this column a body can stand.
     *
     * <p>The plan's y is the simulation's idea of the ground and the ground is
     * the world's, so the column is walked for two blocks of air on something
     * solid — up first, because the usual disagreement is a road laid a course
     * higher than the plan thought, and then down. Null when there is nowhere,
     * which refuses the visit rather than dropping somebody into a hillside.
     */
    private static BlockPos footing(ServerLevel level, SimPos at) {
        BlockPos start = new BlockPos(at.x(), at.y(), at.z());
        for (int up = 0; up <= 4; up++) {
            if (standable(level, start.above(up))) {
                return start.above(up);
            }
        }
        for (int down = 1; down <= 6; down++) {
            if (standable(level, start.below(down))) {
                return start.below(down);
            }
        }
        return null;
    }

    private static boolean standable(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).blocksMotion();
    }
}
