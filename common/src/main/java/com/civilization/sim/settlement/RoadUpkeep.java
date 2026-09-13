package com.civilization.sim.settlement;

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

    /**
     * Whether a street lamp of the raised prefix may be drawn for the first time.
     *
     * <p>The lighting is upkept on exactly the same terms as the road it stands
     * beside, so its two questions live here beside the road's rather than in the
     * layer that places the blocks. A rule that only exists inside a block-placing
     * loop is a rule a dead town goes on breaking, which is the whole reason this
     * class exists.
     *
     * <p>Delegated to {@code LightPlanner} rather than restated, so there is one
     * definition of "raised". Here for the reader who comes looking for the upkeep
     * rules and finds them together.
     */
    public static boolean mayDrawLight(Settlement settlement, int index) {
        return com.civilization.sim.work.LightPlanner.mayDraw(settlement, index);
    }

    /**
     * Whether a missing street lamp may be put back.
     *
     * <p>A lamp that has been broken — a creeper, a player, a zombie that walked
     * into the post — is a hole in the town's lighting and putting it back is a
     * morning with a ladder. So it is work, and a town with nobody left in it does
     * not do it: the lamp stays out, the street goes dark, and the village looks
     * like what it is.
     */
    public static boolean mayMendLight(Settlement settlement, int index) {
        return com.civilization.sim.work.LightPlanner.mayMend(settlement, index);
    }
}
