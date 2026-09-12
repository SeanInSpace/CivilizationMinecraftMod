package com.civilization.sim.settlement;

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
public final class BuildCatalog {

    private BuildCatalog() {
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
            // catalog scan from ever wanting these on its own: only a stage's
            // program orders them, so an established town never retrofits a camp.
            new BuildingType("civilization:camp_post",       6,      1,     0,            0,        0,        0,       0, plot("civilization:camp_post")),
            new BuildingType("civilization:cache",          10,      1,     0,            0,        0,        0,       0, plot("civilization:cache")),
            new BuildingType("civilization:bunkhouse",      40,      1,     0,            0,        0,        6,       0, plot("civilization:bunkhouse")),
            new BuildingType("civilization:hearth",         12,      1,     0,            0,        0,        0,       0, plot("civilization:hearth")),
            new BuildingType("civilization:cottage",        28,      1,     0,            0,        0,        3,       0, plot("civilization:cottage")),
            new BuildingType("civilization:mill",           48,      1,     0,            0,        0,        0,       0, plot("civilization:mill")),
            new BuildingType("civilization:carpentry",      48,      1,     0,            0,        0,        0,       0, plot("civilization:carpentry")),
            new BuildingType("civilization:inn",            60,      1,     0,            0,        0,        0,       0, plot("civilization:inn")),
            new BuildingType("civilization:town_hall",      85,      1,     1,            0,      100,        0,       0, plot("civilization:town_hall")),
            new BuildingType("civilization:house",          40,      1,     1,            3,       80,        4,       0, plot("civilization:house")),
            // Bigger homes for towns that have people to put in them. Both hold
            // six, and both are wanted only once a settlement is past the size
            // where a cottage per family is the whole of its housing — a hamlet
            // that raised a longhouse first would have one enormous roof and
            // nothing else. They scale far more slowly than the house does, so
            // they read as the two or three large houses a village has rather
            // than as its standard dwelling.
            new BuildingType("civilization:longhouse",      55,     14,     0,           16,       78,        6,       0, plot("civilization:longhouse")),
            new BuildingType("civilization:croft",          60,     18,     0,           20,       77,        6,       0, plot("civilization:croft")),
            // The orc homes. A hut stands exactly where a cottage or a house
            // would in anybody else's town -- same priority, same one per three
            // residents -- because it IS their house, not an extra kind of
            // building. Which people get which is Homes, not a gate here: a row
            // this catalog holds is a row every town can see, and the filter
            // that keeps huts out of a human village and cottages out of a war
            // camp is one table rather than a column on every row.
            //
            // Twenty-four work against a cottage's twenty-eight: a hut is a ring
            // of wall and a cone on top, and the corners it does not build are
            // work it does not do.
            new BuildingType("civilization:hut",            24,      1,     1,            3,       80,        3,       0, plot("civilization:hut")),
            // The king's seat, and the one building in the mod that is capped at
            // one by its own row rather than by a rule somewhere else: base 1
            // and nothing per resident, so a warband has exactly one great hut
            // however big it grows. Gated at fourteen for the same reason the
            // longhouse is -- a camp that raised its chief's hall before it had
            // huts would be one enormous roof and nothing else -- and ranked
            // just under the hut, so shelter for the warband comes before a
            // throne for the chief.
            //
            // Six beds, and the first of them is the king's. See KingPlanner.
            new BuildingType("civilization:great_hut",      70,     14,     1,            0,       79,        6,       0, plot("civilization:great_hut")),
            new BuildingType("civilization:granary",        40,      4,     1,           20,       75,        0,       0, plot("civilization:granary")),
            // The farm row is a ceiling on wanting, not the floor. The floor is
            // BuildPlanner.farmsWanted -- one field per MOUTHS_PER_FARM mouths,
            // rounded up and never nought -- because this row alone said a
            // founding party of four wanted no farm at all (nought plus
            // four-sixths) and the catalog does not even run below VILLAGE.
            new BuildingType("civilization:farm",           45,      4,     0,            6,       70,        0,       0, plot("civilization:farm")),
            // Securing materials outranks trading and crafting them. A town that
            // cannot fell its own timber or cut its own stone has nothing to sell
            // and nothing to build the next thing out of.
            new BuildingType("civilization:lumber_camp",    48,      5,     1,           30,       68,        0,       0, plot("civilization:lumber_camp")),
            new BuildingType("civilization:mine",           55,      8,     1,           30,       66,        0,       0, plot("civilization:mine")),
            new BuildingType("civilization:warehouse",      60,      6,     1,           25,       64,        0,       0, plot("civilization:warehouse")),
            new BuildingType("civilization:market",         45,      6,     1,           25,       62,        0,       0, plot("civilization:market")),
            new BuildingType("civilization:smith",          60,     10,     1,           40,       57,        0,       0, plot("civilization:smith")),
            new BuildingType("civilization:animal_farm",    45,     10,     1,           40,       56,        0,       0, plot("civilization:animal_farm")),
            new BuildingType("civilization:watchtower",     45,     12,     0,           12,       60,        0,       3, plot("civilization:watchtower")),
            new BuildingType("civilization:storehouse",     48,      6,     1,           15,       55,        0,       0, plot("civilization:storehouse")),
            new BuildingType("civilization:workshop",       55,      8,     0,            8,       50,        0,       0, plot("civilization:workshop")),
            // The one deliberately outsized building, and the reason the sizing
            // machinery had to be made honest before it could exist. Twenty-three
            // by seventeen is wider than the plan's own plot pitch, so a library
            // takes two frontages and the siting loop simply walks past the offer
            // it will not fit on -- which is the behavior that had to be proved
            // rather than assumed.
            //
            // Gated at forty residents and one per town. A village that tried to
            // raise this would spend every block it owns on it and starve, which
            // is not a hypothetical: the work here is more than the whole of a
            // founding camp.
            new BuildingType("civilization:library",       400,     40,     1,            0,       45,        0,       0, plot("civilization:library")),
            // The capstone, and the only row in the catalog that has to be asked
            // for twice. Thirty-one by twenty-five, three storeys, a portico and
            // a lantern on the roof: more than twice the library's work and more
            // than twice its blocks.
            //
            // Gated three ways, because a building this expensive wanted every
            // one of them:
            //
            //   - eighty residents, which is twice the library's gate and about
            //     as large as a town in this mod gets;
            //   - one per town, from base 1 and nothing per resident, the same
            //     way the library and the great hut are capped;
            //   - and a library already standing, which is the new kind of gate
            //     and the reason BuildingType grew a `requires`. "After the
            //     library" is a fact about what a town has built, and no
            //     population number says it — see BuildPlanner.prerequisiteStands.
            //
            // Priority forty, just under the library's forty-five, so a town that
            // somehow has the people for both raises the small one first. Nothing
            // else in the catalog sits this low: it is the last thing a town
            // wants, which is the point of it.
            new BuildingType("civilization:grand_library", 900,     80,     1,            0,       40,        0,       0, plot("civilization:grand_library"),
                    "civilization:library")
    );
}
