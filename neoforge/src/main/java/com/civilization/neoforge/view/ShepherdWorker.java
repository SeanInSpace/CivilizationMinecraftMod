package com.civilization.neoforge.view;

import com.civilization.neoforge.entity.Pace;
import com.civilization.neoforge.entity.PersonEntity;
import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.Herd;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.TownStores;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * What a shepherd's hands actually do at the pens.
 *
 * <p>The old version of this file conjured livestock. It counted the beasts in
 * a pen, and if there were fewer than four it summoned one — out of nothing, in
 * full view of whoever was standing at the gate, on a schedule nothing else in
 * the mod could see. That was the last place in the simulation where a resource
 * appeared from nowhere, and it is gone.
 *
 * <p>What is here instead is the watched half of {@link Herd}, and it does the
 * three things the arithmetic does when nobody is looking:
 * <ul>
 *   <li><strong>Feeding.</strong> A sheaf out of the compound's own shelf into a
 *       beast, which is what breeds them. The ledger is advanced by exactly the
 *       turn that was spent, so a fed pen fills at the same rate whether the
 *       feeding was done by a hand or by the clock.</li>
 *   <li><strong>Culling.</strong> A full pen, or a hungry town, and the shepherd
 *       kills one by hand. Vanilla drops what vanilla drops.</li>
 *   <li><strong>Gathering.</strong> The drops, off the ground, onto the
 *       compound's shelf. This is the whole of how the town is paid for a cull
 *       while somebody is watching — nothing is credited at the swing, so what
 *       the books show is what actually fell.</li>
 * </ul>
 *
 * <p>Putting the beasts in the pens is {@link Pens}' job and not a shepherd's: a
 * pen is stocked from the ledger the moment a player is near enough to see it,
 * whether or not anybody is on shift.
 */
public final class ShepherdWorker {

    /** How close the shepherd has to be to work the pens. */
    public static final double WORK_REACH = 12.0;

    /** The walk to the next tree, face or pen; see {@link Pace}. */
    public static final double WALK_SPEED = Pace.WALK;

    /** How far from the beast a shepherd can feed or strike it. */
    private static final double HAND_REACH = 4.5;

    private ShepherdWorker() {
    }

    /**
     * One shepherd's turn.
     *
     * @return true if anything changed in the world
     */
    public static boolean work(ServerLevel level, Settlement settlement, PersonEntity worker,
                               int simIntervalTicks) {
        Building farm = compoundNear(settlement, worker);
        if (farm == null) {
            return false;
        }
        SimPos origin = farm.origin();
        BlockPos center = new BlockPos(origin.x(), origin.y(), origin.z());
        if (worker.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5)
                > WORK_REACH * WORK_REACH) {
            worker.getNavigation().moveTo(center.getX() + 0.5, center.getY(),
                    center.getZ() + 0.5, WALK_SPEED);
            return false;
        }
        // The drops first, every turn, and before anything that might make more
        // of them. A joint of beef left on the grass despawns in five minutes
        // and the town is out a cow it has already paid the ledger for.
        if (gather(level, settlement, farm, center)) {
            return true;
        }

        Culture culture = Culture.of(settlement.cultureId());
        List<String> kinds = Herd.kindsOf(culture);
        boolean starving = settlement.isStarving();
        for (int pen = 0; pen < kinds.size(); pen++) {
            String species = kinds.get(pen);
            EntityType<?> type = typeOf(species);
            if (type == null) {
                continue;
            }
            AABB box = Pens.penBox(center, kinds.size(), pen);
            List<Entity> here = level.getEntities((Entity) null, box,
                    e -> e.getType() == type && e.isAlive());
            if (here.isEmpty()) {
                continue;   // the ledger's beasts are not standing here yet
            }
            if (Herd.wantsCulling(farm, culture, species, starving)
                    && farm.stores().get(TownStores.MEAT) < Herd.FARM_MEAT_CAP) {
                if (cull(level, farm, species, worker, here)) {
                    return true;
                }
            }
            if (feed(farm, culture, species, worker, here, simIntervalTicks)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A sheaf into a beast: one turn's feeding.
     *
     * <p>The ledger is advanced by {@link Herd#feed} with the same turn count a
     * pass is worth, so the pen fills at the pace it fills at out of sight. The
     * grain is spent inside {@code Herd.feed} at the birth, not here — a shepherd
     * who fed a pair and got no calf that step has not wasted a sheaf, he has
     * done a fraction of a calf's worth of feeding, and the remainder is on the
     * books.
     */
    private static boolean feed(Building farm, Culture culture, String species,
                                PersonEntity worker, List<Entity> here,
                                int simIntervalTicks) {
        if (!farm.stores().has(TownStores.GRAIN, Herd.FEED_PER_HEAD)
                || Herd.room(farm, culture, species) <= 0 || here.size() < 2) {
            return false;
        }
        Entity beast = nearest(worker, here);
        if (beast == null) {
            return false;
        }
        if (worker.distanceToSqr(beast) > HAND_REACH * HAND_REACH) {
            worker.getNavigation().moveTo(beast, WALK_SPEED);
            return false;
        }
        worker.getLookControl().setLookAt(beast, 30.0F, 30.0F);
        worker.swing(InteractionHand.MAIN_HAND);
        // One pass is one turn, and a pair takes two -- so the ledger is handed
        // the pass's worth and works out for itself whether that finished a pair.
        Herd.feed(farm, culture, species, Herd.TURNS_PER_PAIR, simIntervalTicks);
        // And the turn comes off this step's allowance, so Herd.advance's clock
        // spends one fewer. The hands are not a second herd; they are this one.
        farm.creditByHand(Herd.TURNS_PER_PAIR);
        return true;
    }

    /**
     * The kill, by hand, and nothing credited for it.
     *
     * <p>The head comes off the ledger at the swing and the meat does not go on
     * — vanilla's own loot table decides what this cow was worth, it falls on the
     * grass, and {@link #gather} picks it up next turn. That is the whole reason
     * a watched cull and an unwatched one cannot pay differently.
     */
    private static boolean cull(ServerLevel level, Building farm, String species,
                                PersonEntity worker, List<Entity> here) {
        Entity beast = nearest(worker, here);
        if (!(beast instanceof LivingEntity victim)) {
            return false;
        }
        if (worker.distanceToSqr(beast) > HAND_REACH * HAND_REACH) {
            worker.getNavigation().moveTo(beast, WALK_SPEED);
            return false;
        }
        if (!Herd.cull(farm, species)) {
            return false;   // down to the breeding pair; the pen keeps them
        }
        // A kill is a turn like any other, and it comes off this step's
        // allowance so the clock does not cull a second beast for the same work.
        farm.creditByHand(1);
        worker.getLookControl().setLookAt(beast, 30.0F, 30.0F);
        worker.swing(InteractionHand.MAIN_HAND);
        victim.hurtServer(level, level.damageSources().mobAttack(worker),
                victim.getMaxHealth() * 2.0F);
        return true;
    }

    /**
     * Everything lying loose in the compound, onto the compound's shelf.
     *
     * <p>Only what a beast is actually worth — see {@link Herd.Yield} — because
     * this runs over a fenced yard a player can throw anything into, and a
     * shepherd who swept a dropped diamond into the meat safe would be a hole in
     * the store ledger rather than a helpful one.
     *
     * @return whether anything was picked up
     */
    private static boolean gather(ServerLevel level, Settlement settlement, Building farm,
                                  BlockPos center) {
        int pens = Herd.penCount(Culture.of(settlement.cultureId()));
        AABB compound = new AABB(center).inflate(
                Herd.compoundSize().width() / 2.0 + 1.0, 4.0,
                Herd.compoundDepth(pens) / 2.0 + 1.0);
        boolean took = false;
        for (ItemEntity dropped : level.getEntitiesOfClass(ItemEntity.class, compound,
                item -> item.isAlive() && !item.hasPickUpDelay())) {
            ItemStack stack = dropped.getItem();
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            String good = goodFor(id);
            if (good == null) {
                continue;
            }
            int cap = TownStores.MEAT.equals(good) ? Herd.FARM_MEAT_CAP : Herd.FARM_CLIP_CAP;
            int taken = farm.stores().addCapped(good, stack.getCount(), cap);
            if (taken <= 0) {
                continue;   // the shelf is full; it stays on the grass
            }
            stack.shrink(taken);
            if (stack.isEmpty()) {
                dropped.discard();
            } else {
                dropped.setItem(stack);
            }
            took = true;
        }
        return took;
    }

    /**
     * Which of the compound's ledgers an item belongs in, or null for anything
     * a pen has no business holding.
     *
     * <p>Named outright rather than matched on a substring, for the reason
     * {@code BuildingRole} gives: {@code minecraft:rotten_flesh} ends in "flesh"
     * and is not dinner, and a rule that guessed would have the town eating it.
     */
    private static String goodFor(String itemId) {
        return switch (itemId) {
            case "minecraft:beef", "minecraft:porkchop", "minecraft:mutton",
                 "minecraft:chicken", "minecraft:rabbit" -> TownStores.MEAT;
            case "minecraft:leather", "minecraft:rabbit_hide" -> TownStores.LEATHER;
            case "minecraft:feather" -> TownStores.FEATHERS;
            default -> isWool(itemId) ? TownStores.WOOL : null;
        };
    }

    /** Any dye of it. A sheep drops the fleece it is wearing, whatever color that is. */
    private static boolean isWool(String itemId) {
        return itemId.startsWith("minecraft:") && itemId.endsWith("_wool");
    }

    /** The compound this shepherd is working: the nearest one the town has. */
    private static Building compoundNear(Settlement settlement, PersonEntity worker) {
        Building nearest = null;
        double best = Double.MAX_VALUE;
        for (Building building : settlement.buildingsWithRole(BuildingRole.ANIMAL_FARM)) {
            SimPos at = building.origin();
            double distance = worker.distanceToSqr(at.x() + 0.5, at.y(), at.z() + 0.5);
            if (distance < best) {
                best = distance;
                nearest = building;
            }
        }
        return nearest;
    }

    private static Entity nearest(PersonEntity worker, List<Entity> among) {
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity candidate : among) {
            double distance = worker.distanceToSqr(candidate);
            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static EntityType<?> typeOf(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id)).orElse(null);
    }

    /** Where the animal farm stands, or null if the town has not built one. */
    public static SimPos farmPos(Settlement settlement) {
        Building farm = settlement.buildingWithRole(BuildingRole.ANIMAL_FARM);
        return farm == null ? null : farm.origin();
    }
}
