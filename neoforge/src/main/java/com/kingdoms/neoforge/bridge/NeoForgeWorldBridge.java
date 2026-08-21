package com.kingdoms.neoforge.bridge;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.world.BlueprintPlacer;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.platform.WorldBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.Objects;

/**
 * Translates between the simulation's plain data and an actual {@link ServerLevel}.
 *
 * <p>This is the only class that knows about both worlds. Keep it thin — it should
 * translate and delegate, never decide.
 */
public final class NeoForgeWorldBridge implements WorldBridge {

    private final ServerLevel level;

    public NeoForgeWorldBridge(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    public ServerLevel level() {
        return level;
    }

    public static BlockPos toBlockPos(SimPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    public static SimPos toSimPos(BlockPos pos) {
        return new SimPos(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public boolean playerWithin(SimPos pos, double radius) {
        return level.hasNearbyAlivePlayer(pos.x(), pos.y(), pos.z(), radius);
    }

    @Override
    public boolean isLoaded(SimPos pos) {
        return level.isLoaded(toBlockPos(pos));
    }

    @Override
    public int surfaceHeight(SimPos pos) {
        if (!level.isLoaded(toBlockPos(pos))) {
            return pos.y();
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.x(), pos.z());
    }

    /**
     * Draws a finished building: a datapack structure template when one exists for
     * the blueprint id, a procedural structure otherwise. See {@link BlueprintPlacer}.
     *
     * <p>Never force-loads a chunk. If the area is not loaded this does nothing and
     * the building stays pending until a later step finds it available.
     */
    @Override
    public Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed) {
        if (!level.isLoaded(toBlockPos(origin))) {
            return Footprint.UNKNOWN;
        }
        // A surveyed site keeps its measured height. Re-measuring here is what put
        // a stamped building one course above the same building the builders had
        // begun by hand — two copies of it, a block apart.
        //
        // An unsurveyed origin carries a planning estimate, so it does get snapped
        // — through the same floorFor the builders would have used, not the raw
        // surface, or the two paths disagree again.
        int y = surveyed ? origin.y() : BlueprintPlacer.floorFor(surfaceHeight(origin));
        BlockPos base = new BlockPos(origin.x(), y, origin.z());
        Footprint placed = BlueprintPlacer.place(level, blueprintId, base);
        // Logs the base actually used, not the requested origin. A mismatch
        // between the two is precisely the double-placement bug.
        KingdomsMod.LOGGER.info("Materialized {} at {} (origin {}, surveyed {})",
                blueprintId, base, origin, surveyed);
        return placed;
    }

    @Override
    public void log(String message) {
        KingdomsMod.LOGGER.info(message);
    }

    @Override
    public int hostilesNear(SimPos centre, double radius) {
        if (!level.isLoaded(toBlockPos(centre))) {
            return 0;
        }
        AABB box = new AABB(
                centre.x() - radius, centre.y() - 32, centre.z() - radius,
                centre.x() + radius, centre.y() + 32, centre.z() + radius);
        return level.getEntitiesOfClass(Monster.class, box, LivingEntity::isAlive).size();
    }

    /**
     * The observed half of a raid: real zombies in a ring at the edge of town,
     * nudged toward the centre so vanilla targeting (zombies already hunt
     * villagers) takes over. Entity combat decides everything from here — every
     * villager death flows through the normal view-death path.
     */
    @Override
    public void spawnHostiles(int count, SimPos around) {
        if (!level.isLoaded(toBlockPos(around))) {
            return;
        }
        double distance = 32.0;
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i) / count;
            int x = around.x() + (int) Math.round(distance * Math.cos(angle));
            int z = around.z() + (int) Math.round(distance * Math.sin(angle));
            SimPos ringPos = new SimPos(x, around.y(), z);
            if (!level.isLoaded(toBlockPos(ringPos))) {
                continue;
            }
            Zombie zombie = new Zombie(level);
            zombie.setPos(x + 0.5, surfaceHeight(ringPos), z + 0.5);
            // Deliberately NOT persistence-required: raiders that outlive the raid
            // despawn like any mob. Persistent raiders once accumulated across
            // sessions into a permanent roaming horde.
            if (level.addFreshEntity(zombie)) {
                zombie.getNavigation().moveTo(around.x() + 0.5, around.y(), around.z() + 0.5, 1.0);
                spawned++;
            }
        }
        KingdomsMod.LOGGER.info("Raid: {} hostiles spawned around {}", spawned, around);
    }
}
