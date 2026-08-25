package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.world.SimContext;

/**
 * Buildings notice when they have been knocked about, and fix themselves when
 * it gets bad enough to be worth the timber.
 *
 * <p>Nothing in the simulation used to look at a standing building ever again.
 * Once raised it was raised, and a cottage with its front wall blown off was
 * still, as far as the town was concerned, a cottage — counted for housing,
 * assigned to a family, audited for a door that was now a hole. That was
 * tolerable while nothing in the mod destroyed buildings. It stopped being
 * tolerable when creepers arrived.
 *
 * <p><strong>How damage is measured.</strong> Not by watching for explosions,
 * which would mean catching every possible way a block can leave — fire, lava,
 * a player with a pickaxe, another mod entirely. Instead the building is
 * counted when it is first seen whole, and counted again afterwards; the
 * shortfall is the damage. It does not matter what took the blocks.
 *
 * <p>The census is deliberately not the same thing as the blueprint. A
 * blueprint says what should have been laid; a census says what actually stood
 * there once the ground had been levelled, the doorway cut, and the builders
 * had finished improvising. Judging against the blueprint would report every
 * building as damaged from the day it was finished.
 *
 * <p><strong>Why repair waits for severe.</strong> A town that rebuilt a wall
 * every time a single block went missing would spend its whole timber supply on
 * cosmetics, and a builder walking off a queue of real work to replace one
 * plank reads as fussiness rather than diligence. Minor damage is recorded and
 * left; the town fixes what actually threatens the building.
 */
public final class RepairPlanner {

    private RepairPlanner() {
    }

    /**
     * Damage at or above this, in percent, and the town does something about it.
     *
     * <p>A quarter of a building missing is past cosmetic — that is a wall
     * gone, or a roof open to the weather.
     */
    public static final int SEVERE_DAMAGE = 25;

    /**
     * Below this, in percent, the building is called whole again.
     *
     * <p>Not zero. A census taken through a doorway a settler happened to be
     * standing in, or with a torch burnt out, comes back a block or two light,
     * and a building that flickered between "damaged" and "sound" every step
     * would fill the event log with nothing.
     */
    public static final int NOISE_FLOOR = 5;

    /** Work a full rebuild would cost, scaled by how much is actually missing. */
    public static int repairWork(BuildingType type, int damagePercent) {
        return Math.max(1, type.workCost() * damagePercent / 100);
    }

    /**
     * One pass: re-count what is standing, record the shortfall, and queue a
     * repair for anything badly hurt.
     */
    public static void advance(Settlement settlement, SimContext ctx) {
        for (Building building : settlement.buildings()) {
            if (!building.isMaterialized() || !building.footprint().isKnown()) {
                continue;
            }
            int standing = ctx.bridge().solidBlocksIn(building.origin(), building.footprint());
            if (standing < 0) {
                continue;   // nobody is there to look; an unwatched building is not decaying
            }
            if (!building.hasCensus()) {
                // First sight of it whole. This is the baseline everything after
                // is judged against.
                building.setSoundCensus(standing);
                continue;
            }
            if (standing >= building.soundCensus()) {
                // Whole again, or better than it was — somebody has repaired or
                // improved it. Take the higher figure as the new truth.
                building.setSoundCensus(standing);
                markSound(settlement, building, ctx);
                continue;
            }
            int missing = building.soundCensus() - standing;
            int damage = Math.min(100, missing * 100 / building.soundCensus());
            record(settlement, building, damage, ctx);
        }
    }

    private static void record(Settlement settlement, Building building, int damage,
                               SimContext ctx) {
        if (damage < NOISE_FLOOR) {
            markSound(settlement, building, ctx);
            return;
        }
        boolean wasSevere = building.needsRepair();
        building.setDamage(damage);
        if (building.needsRepair() && !wasSevere) {
            settlement.logEvent(ctx.step(), niceName(building) + " at " + building.origin()
                    + " is badly damaged — " + damage + "% of it is gone");
        }
        if (building.needsRepair()) {
            queueRepair(settlement, building, damage, ctx);
        }
    }

    private static void markSound(Settlement settlement, Building building, SimContext ctx) {
        if (building.damage() >= SEVERE_DAMAGE) {
            settlement.logEvent(ctx.step(),
                    niceName(building) + " at " + building.origin() + " is whole again");
        }
        building.setDamage(0);
    }

    /**
     * Books the work, unless it is already booked.
     *
     * <p>Enqueued as an upgrade to its own level, which is exactly what a repair
     * is: the completion path already knows how to raise a building in place
     * without the town believing it now owns two of them.
     */
    private static void queueRepair(Settlement settlement, Building building, int damage,
                                    SimContext ctx) {
        for (BuildTask queued : settlement.buildQueue()) {
            if (building.origin().equals(queued.upgradeOf())) {
                return;   // already being seen to
            }
        }
        BuildingType type = settlement.catalogue().stream()
                .filter(candidate -> candidate.id()
                        .equals(BuildPlanner.baseIdOf(building.blueprintId())))
                .findFirst()
                .orElse(null);
        if (type == null) {
            return;   // a building the catalogue no longer knows; nothing to rebuild it from
        }
        BuildTask work = new BuildTask(building.blueprintId(), building.origin(),
                repairWork(type, damage));
        work.setFacing(building.facing());
        work.setUpgradeOf(building.origin());
        settlement.enqueueUrgent(work);
        settlement.logEvent(ctx.step(), "Repairs begin on the " + niceName(building)
                + " at " + building.origin());
    }

    private static String niceName(Building building) {
        String id = BuildPlanner.baseIdOf(building.blueprintId());
        return id.substring(id.indexOf(':') + 1).replace('_', ' ');
    }

    /** Convenience for callers that only want the plot. */
    public static SimPos plotOf(Building building) {
        return building.origin();
    }
}
