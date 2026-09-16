package com.civilization.neoforge.world;

import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Where a town's smoke comes out.
 *
 * <p>A village with fires in it and no smoke over it is the same fault as a
 * village with people in it who never sit down: everything is modeled and
 * nothing is visible. The drawings have had chimneys since the houses got styles
 * — {@code Parts.dress} puts one up whenever the style asks — and nothing has
 * ever used them for anything.
 *
 * <p>Two jobs, and the first is the awkward one. <strong>Finding</strong> the
 * chimney is expensive: nothing writes one down, so the only way to know where
 * it came out is to rebuild the building's plan and look for the stack of
 * masonry sticking out of the roof (see {@code BlueprintPlacer.chimneyTops}).
 * That is far too dear to do once a tick, and it never needs doing twice — a
 * chimney does not move, and a building that moves is a different building at a
 * different origin. So it is derived the first time a standing building is
 * looked at and remembered by exactly that: its origin.
 *
 * <p><strong>Emitting</strong> is then nearly free, and deliberately kept that
 * way. Particles are sent from the server, so a chimney nobody can see costs a
 * loop iteration and nothing else, and the whole sweep is skipped outright in
 * daylight.
 */
public final class Chimneys {

    private final ServerLevel level;

    public Chimneys(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    /**
     * Which buildings have a fire in them.
     *
     * <p>Somebody's own house, because a house has a hearth in it and that is
     * what the chimney on it is for; the camp's hearth, which is a fire with a
     * roof over it; the inn, whose common room is the one other place the town
     * sits round a fire; and the smithy, whose chimney is a forge flue and the
     * one in the mod that is drawn on purpose rather than by the house style.
     *
     * <p>Not the hall, not the store, not the mill. A chimney the drawing never
     * put there cannot smoke, and a plan this asked about would pay the full cost
     * of being rebuilt to find that out.
     */
    public static boolean hasAFire(Settlement settlement, Building building) {
        BuildingRole role = building.role();
        return role == BuildingRole.HEARTH
                || role == BuildingRole.INN
                || role == BuildingRole.SMITH
                || settlement.isFamilyHome(building.origin());
    }

    /**
     * This building's chimney pots, derived once and then remembered.
     *
     * <p>Empty both for a building with no chimney and for one whose ground is
     * not loaded, and the two do not need telling apart: nothing can be drawn
     * over an unloaded chunk anyway, and the entry is not kept if the plan could
     * not be read, so it is asked again when the chunk comes back.
     */
    public List<BlockPos> potsOf(Building building) {
        BlockPos origin = new BlockPos(building.origin().x(), building.origin().y(),
                building.origin().z());
        Roof key = new Roof(origin, building.blueprintId(), building.facing());
        List<BlockPos> known = pots.get(key);
        if (known != null) {
            return known;
        }
        if (!level.isLoaded(origin)) {
            return List.of();
        }
        List<BlockPos> found = BlueprintPlacer.chimneyTops(level, building.blueprintId(),
                origin, building.facing());
        pots.put(key, found);
        return found;
    }

    /**
     * What a derived answer belongs to.
     *
     * <p>Not simply the origin, because a razed cottage replaced by a smithy on
     * the same plot is a different roof with a different flue, and the old
     * answer would have the new building smoking out of thin air. Keying on
     * everything the derivation was made from means the cache cannot be stale —
     * it can only be unused, and an entry for a building that is gone is one
     * short list.
     */
    private record Roof(BlockPos origin, String blueprintId, int facing) {
    }

    /** Every chimney the derivation has been paid for. */
    private final Map<Roof, List<BlockPos>> pots = new HashMap<>();
}
