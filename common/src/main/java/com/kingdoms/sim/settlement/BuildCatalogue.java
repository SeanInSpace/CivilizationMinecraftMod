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

    public static final List<BuildingType> DEFAULT = List.of(
            //                id                      work  minPop  base  perResidents  priority  capacity  defense
            new BuildingType("kingdoms:town_hall",      40,      1,     1,            0,      100,        0,       0),
            new BuildingType("kingdoms:house",          20,      1,     1,            3,       80,        4,       0),
            new BuildingType("kingdoms:granary",        25,      4,     1,           20,       75,        0,       0),
            new BuildingType("kingdoms:farm",           45,      4,     0,            6,       70,        0,       0),
            // Securing materials outranks trading and crafting them. A town that
            // cannot fell its own timber or cut its own stone has nothing to sell
            // and nothing to build the next thing out of.
            new BuildingType("kingdoms:lumber_camp",    30,      5,     1,           30,       68,        0,       0),
            new BuildingType("kingdoms:mine",           35,      8,     1,           30,       66,        0,       0),
            new BuildingType("kingdoms:warehouse",      35,      6,     1,           25,       64,        0,       0),
            new BuildingType("kingdoms:market",         30,      6,     1,           25,       62,        0,       0),
            new BuildingType("kingdoms:smith",          40,     10,     1,           40,       57,        0,       0),
            new BuildingType("kingdoms:animal_farm",    45,     10,     1,           40,       56,        0,       0),
            new BuildingType("kingdoms:watchtower",     45,     12,     0,           12,       60,        0,       3),
            new BuildingType("kingdoms:storehouse",     30,      6,     1,           15,       55,        0,       0),
            new BuildingType("kingdoms:workshop",       35,      8,     0,            8,       50,        0,       0)
    );
}
