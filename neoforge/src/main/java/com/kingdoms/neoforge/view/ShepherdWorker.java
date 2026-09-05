package com.kingdoms.neoforge.view;

import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * Keeps the animal farm stocked, a pen to each kind.
 *
 * <p>Which beasts a town keeps is a property of its {@link Culture}, so a second
 * culture is a list of ids rather than new code. Pens are strips running across
 * the compound, in the same order as the culture's list, which is what keeps the
 * cows out of the chicken run.
 *
 * <p>Stocking, not breeding: vanilla will happily breed penned animals on its
 * own, so the shepherd's job is to bring the first of each kind in and to make
 * good losses, then leave them to it.
 */
public final class ShepherdWorker {

    /** How many of one kind a pen holds before the shepherd stops adding. */
    private static final int PER_PEN = 4;

    /** Depth of one pen, matching the compound laid down by the placer. */
    private static final int PEN_DEPTH = 3;

    /** Width of the compound, matching the placer. */
    private static final int COMPOUND_WIDTH = 9;

    /** How close the shepherd has to be to work the pens. */
    private static final double WORK_REACH = 12.0;

    private ShepherdWorker() {
    }

    /**
     * One shepherd's turn.
     *
     * @return true if anything changed in the world
     */
    public static boolean work(ServerLevel level, Settlement settlement, PersonEntity worker) {
        SimPos farm = farmPos(settlement);
        if (farm == null) {
            return false;
        }
        BlockPos center = new BlockPos(farm.x(), farm.y(), farm.z());
        if (worker.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5)
                > WORK_REACH * WORK_REACH) {
            worker.getNavigation().moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.7);
            return false;
        }

        Culture culture = Culture.of(settlement.cultureId());
        List<String> kinds = culture.pennedAnimals();
        for (int pen = 0; pen < kinds.size(); pen++) {
            Optional<EntityType<?>> type = typeOf(kinds.get(pen));
            if (type.isEmpty()) {
                continue;
            }
            AABB bounds = penBounds(center, pen, kinds.size());
            long present = level.getEntities((Entity) null, bounds,
                    e -> e.getType() == type.get() && e.isAlive()).size();
            if (present >= PER_PEN) {
                continue;
            }
            BlockPos spot = BlockPos.containing(bounds.getCenter());
            Entity beast = type.get().spawn(level, spot, EntitySpawnReason.MOB_SUMMONED);
            if (beast != null) {
                worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                return true;   // one beast a turn; the pen fills over time
            }
        }
        return false;
    }

    /**
     * The box a given pen occupies.
     *
     * <p>Mirrors the strips the placer lays down: the compound runs
     * {@code pens * (PEN_DEPTH + 1) + 1} deep, with a fence row before each pen.
     */
    private static AABB penBounds(BlockPos center, int pen, int pens) {
        int depth = pens * PEN_DEPTH + pens + 1;
        int rx = COMPOUND_WIDTH / 2;
        int rz = depth / 2;
        int startZ = center.getZ() - rz + 1 + pen * (PEN_DEPTH + 1);
        return new AABB(
                center.getX() - rx + 1, center.getY(), startZ,
                center.getX() + rx, center.getY() + 3.0, startZ + PEN_DEPTH);
    }

    private static Optional<EntityType<?>> typeOf(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id));
    }

    /** Where the animal farm stands, or null if the town has not built one. */
    public static SimPos farmPos(Settlement settlement) {
        for (Building building : settlement.buildings()) {
            if (BuildPlanner.baseIdOf(building.blueprintId()).endsWith("animal_farm")) {
                return building.origin();
            }
        }
        return null;
    }
}
