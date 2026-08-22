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
            //                id                      work  minPop  base  perResidents  priority  capacity  defense  plot
            // plot is the ground the building takes: its walls, the shelf cleared
            // around them, and room for the levels it may be raised to. Two plots
            // are never allowed to overlap, so these are what keep a town from
            // building its granary through the side of its own hall.
            //
            // These move with BlueprintPlacer.APRON_MARGIN: each is two narrower
            // than when the cleared shelf was two blocks rather than one. Leaving
            // them wide would have kept the town as spread out as before while
            // the buildings themselves stopped taking that much ground — the
            // footprint change would have been real and entirely invisible.
            // The founding program's own content (FOUNDING.md). Base 0 keeps the
            // catalogue scan from ever wanting these on its own: only a stage's
            // program orders them, so an established town never retrofits a camp.
            new BuildingType("kingdoms:camp_post",       6,      1,     0,            0,        0,        0,       0, 7),
            new BuildingType("kingdoms:cache",          10,      1,     0,            0,        0,        0,       0, 7),
            new BuildingType("kingdoms:bunkhouse",      22,      1,     0,            0,        0,        6,       0, 11),
            new BuildingType("kingdoms:hearth",         12,      1,     0,            0,        0,        0,       0, 9),
            new BuildingType("kingdoms:town_hall",      40,      1,     1,            0,      100,        0,       0, 13),
            new BuildingType("kingdoms:house",          20,      1,     1,            3,       80,        4,       0, 11),
            new BuildingType("kingdoms:granary",        25,      4,     1,           20,       75,        0,       0, 9),
            new BuildingType("kingdoms:farm",           45,      4,     0,            6,       70,        0,       0, 15),
            // Securing materials outranks trading and crafting them. A town that
            // cannot fell its own timber or cut its own stone has nothing to sell
            // and nothing to build the next thing out of.
            new BuildingType("kingdoms:lumber_camp",    30,      5,     1,           30,       68,        0,       0, 9),
            new BuildingType("kingdoms:mine",           35,      8,     1,           30,       66,        0,       0, 9),
            new BuildingType("kingdoms:warehouse",      35,      6,     1,           25,       64,        0,       0, 11),
            new BuildingType("kingdoms:market",         30,      6,     1,           25,       62,        0,       0, 9),
            new BuildingType("kingdoms:smith",          40,     10,     1,           40,       57,        0,       0, 9),
            new BuildingType("kingdoms:animal_farm",    45,     10,     1,           40,       56,        0,       0, 19),
            new BuildingType("kingdoms:watchtower",     45,     12,     0,           12,       60,        0,       3, 7),
            new BuildingType("kingdoms:storehouse",     30,      6,     1,           15,       55,        0,       0, 9),
            new BuildingType("kingdoms:workshop",       35,      8,     0,            8,       50,        0,       0, 9)
    );
}
