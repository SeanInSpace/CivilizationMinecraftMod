package com.kingdoms.sim.settlement;

/**
 * Whether a stretch of road may have blocks put on it, and on what grounds.
 *
 * <p>Drawing and mending a road are the same operation in the world — lay
 * whatever is missing — which is what makes the whole thing self-healing, and
 * is exactly why a dead town went on fixing its streets. The block-laying code
 * could not tell the two apart, so a plague village whose roads had been dug
 * up put them back, one stretch a second, with every resident in the ground.
 *
 * <p>The line is drawn here instead, in one place, with no world to ask:
 *
 * <ul>
 *   <li><strong>Drawing</strong> a stretch that has never been drawn is a
 *       record. The road was built while somebody was alive to build it; the
 *       gravel is late only because nobody was standing there to watch it go
 *       down. The same rule as a finished building painted in when its chunk
 *       loads, and it holds for a dead town for the same reason.</li>
 *   <li><strong>Mending</strong> a stretch that has been drawn once already is
 *       work. Something has grown over it or been dug out of it since, and
 *       putting it back is a morning with a spade. A town with nobody left in
 *       it has nobody to hold the spade, so it does not happen — the road
 *       stays broken, and the village looks like what it is.</li>
 * </ul>
 *
 * <p>Both questions take the settlement rather than a flag, because "is anybody
 * alive here" is the one question every planner that forgot to ask turned into
 * a way for a town of corpses to do a day's work.
 */
public final class RoadUpkeep {

    private RoadUpkeep() {
    }

    /**
     * Whether this stretch may be drawn into the world for the first time.
     *
     * <p>Opened but never laid: a record of a road the town built, not new
     * work, so it goes down whether or not anybody is left to walk it.
     */
    public static boolean mayDraw(Settlement settlement, int index) {
        PathNetwork paths = settlement.paths();
        return paths.isOpened(index) && !paths.isLaid(index);
    }

    /**
     * Whether this stretch may be mended.
     *
     * <p>Upkeep, which is labor: it needs hands. An unopened stretch is not
     * mended either — there is nothing there yet to mend, and paving one ahead
     * of the crew walking it out is the older bug this rule sits next to.
     */
    public static boolean mayMend(Settlement settlement, int index) {
        return settlement.paths().isOpened(index) && settlement.hasLivingResidents();
    }
}
