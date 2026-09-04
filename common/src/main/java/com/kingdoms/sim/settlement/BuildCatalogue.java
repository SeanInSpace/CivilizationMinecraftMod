package com.kingdoms.sim.settlement;

import java.util.List;

/**
 * What settlements know how to build.
 *
 * <p>Deliberately a hardcoded list. This is the single most obvious thing to move
 * into datapacks, and when you do, only this class should need to change — the
 * planner reads {@link BuildingType} values and does not care where they came from.
 *
 * <p>See {@code BUILD_DECISIONS.md} for what these numbers produce in practice.
 */
public final class BuildCatalogue {

    private BuildCatalogue() {
    }

    /**
     * How much ground this kind of building holds.
     *
     * <p>Read from {@link BuildingSizes} rather than written out here, because
     * the two used to be separate numbers and had drifted: a cottage was drawn
     * five blocks across and reserved nine, a house was drawn five and reserved
     * eleven. Every street in the mod was therefore spaced for buildings roughly
     * twice the size of the ones standing in it.
     */
    private static int plot(String id) {
        return BuildingSizes.plotSpanOf(id);
    }

    /*
     * On the work costs below.
     *
     * They are an estimate, and only an estimate. The moment a build is watched,
     * BlueprintPlacer measures the real plan and calls task.setPlan with the
     * actual block count, so this number decides only how long an UNWATCHED town
     * takes on the clock. It still has to be roughly right, or a town nobody is
     * looking at builds at a different speed from the same town with somebody
     * standing in it.
     *
     * They moved with the sizes. A cabin is about 2*w*d + 2*(w+d-2)*h blocks, so
     * a cottage going from five by five to seven by seven is not a tenth bigger,
     * it is seven tenths bigger, and the old figure would have had an unwatched
     * town running up cottages at nearly twice the rate a watched one could.
     */

    public static final List<BuildingType> DEFAULT = List.of(
            //                id                      work  minPop  base  perResidents  priority  capacity  defense  plot
            // The founding program's own content (FOUNDING.md). Base 0 keeps the
            // catalogue scan from ever wanting these on its own: only a stage's
            // program orders them, so an established town never retrofits a camp.
            new BuildingType("kingdoms:camp_post",       6,      1,     0,            0,        0,        0,       0, plot("kingdoms:camp_post")),
            new BuildingType("kingdoms:cache",          10,      1,     0,            0,        0,        0,       0, plot("kingdoms:cache")),
            new BuildingType("kingdoms:bunkhouse",      40,      1,     0,            0,        0,        6,       0, plot("kingdoms:bunkhouse")),
            new BuildingType("kingdoms:hearth",         12,      1,     0,            0,        0,        0,       0, plot("kingdoms:hearth")),
            new BuildingType("kingdoms:cottage",        28,      1,     0,            0,        0,        3,       0, plot("kingdoms:cottage")),
            new BuildingType("kingdoms:mill",           48,      1,     0,            0,        0,        0,       0, plot("kingdoms:mill")),
            new BuildingType("kingdoms:carpentry",      48,      1,     0,            0,        0,        0,       0, plot("kingdoms:carpentry")),
            new BuildingType("kingdoms:inn",            60,      1,     0,            0,        0,        0,       0, plot("kingdoms:inn")),
            new BuildingType("kingdoms:town_hall",      85,      1,     1,            0,      100,        0,       0, plot("kingdoms:town_hall")),
            new BuildingType("kingdoms:house",          40,      1,     1,            3,       80,        4,       0, plot("kingdoms:house")),
            // Bigger homes for towns that have people to put in them. Both hold
            // six, and both are wanted only once a settlement is past the size
            // where a cottage per family is the whole of its housing — a hamlet
            // that raised a longhouse first would have one enormous roof and
            // nothing else. They scale far more slowly than the house does, so
            // they read as the two or three large houses a village has rather
            // than as its standard dwelling.
            new BuildingType("kingdoms:longhouse",      55,     14,     0,           16,       78,        6,       0, plot("kingdoms:longhouse")),
            new BuildingType("kingdoms:croft",          60,     18,     0,           20,       77,        6,       0, plot("kingdoms:croft")),
            new BuildingType("kingdoms:granary",        40,      4,     1,           20,       75,        0,       0, plot("kingdoms:granary")),
            new BuildingType("kingdoms:farm",           45,      4,     0,            6,       70,        0,       0, plot("kingdoms:farm")),
            // Securing materials outranks trading and crafting them. A town that
            // cannot fell its own timber or cut its own stone has nothing to sell
            // and nothing to build the next thing out of.
            new BuildingType("kingdoms:lumber_camp",    48,      5,     1,           30,       68,        0,       0, plot("kingdoms:lumber_camp")),
            new BuildingType("kingdoms:mine",           55,      8,     1,           30,       66,        0,       0, plot("kingdoms:mine")),
            new BuildingType("kingdoms:warehouse",      60,      6,     1,           25,       64,        0,       0, plot("kingdoms:warehouse")),
            new BuildingType("kingdoms:market",         45,      6,     1,           25,       62,        0,       0, plot("kingdoms:market")),
            new BuildingType("kingdoms:smith",          60,     10,     1,           40,       57,        0,       0, plot("kingdoms:smith")),
            new BuildingType("kingdoms:animal_farm",    45,     10,     1,           40,       56,        0,       0, plot("kingdoms:animal_farm")),
            new BuildingType("kingdoms:watchtower",     45,     12,     0,           12,       60,        0,       3, plot("kingdoms:watchtower")),
            new BuildingType("kingdoms:storehouse",     48,      6,     1,           15,       55,        0,       0, plot("kingdoms:storehouse")),
            new BuildingType("kingdoms:workshop",       55,      8,     0,            8,       50,        0,       0, plot("kingdoms:workshop")),
            // The one deliberately outsized building, and the reason the sizing
            // machinery had to be made honest before it could exist. Twenty-three
            // by seventeen is wider than the plan's own plot pitch, so a library
            // takes two frontages and the siting loop simply walks past the offer
            // it will not fit on -- which is the behaviour that had to be proved
            // rather than assumed.
            //
            // Gated at forty residents and one per town. A village that tried to
            // raise this would spend every block it owns on it and starve, which
            // is not a hypothetical: the work here is more than the whole of a
            // founding camp.
            new BuildingType("kingdoms:library",       400,     40,     1,            0,       45,        0,       0, plot("kingdoms:library"))
    );
}
