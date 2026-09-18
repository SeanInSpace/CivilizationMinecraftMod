package com.civilization.neoforge.entity;

import com.civilization.neoforge.CivilizationMod;
import com.civilization.sim.culture.Faces;
import com.civilization.sim.culture.Race;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Which sheet a settler of each race is drawn with.
 *
 * <p>The table is the contract between the art on disk and the renderer, and it
 * is <em>here</em> rather than in {@code PersonRenderer} for one reason: a
 * renderer is a client class and a test that loads one has to stand a client up.
 * This is a map of identifiers, nothing more, so the test that checks every
 * named sheet exists and is a 64x64 PNG can read it in a plain JUnit run.
 *
 * <p>Humans wear vanilla's nine default player skins — the wide half of
 * {@code DefaultPlayerSkin}'s table, because the renderer bakes
 * {@code ModelLayers.PLAYER} and the slim sheets would be drawn on the wrong
 * arms. They are listed in vanilla's own order so that an index here and an
 * index in {@link Faces#HUMAN_FACES} are the same number, which is the whole
 * reason the choosing can live in {@code common} and the art can live here.
 *
 * <p>Every non-vanilla sheet named here is drawn by
 * {@code neoforge/tools/orc_skin_art.py}.
 */
public final class PersonSkins {

    /** Vanilla's default player skin, and the face a body falls back to. */
    public static final Identifier STEVE = wide("steve");

    /** One of vanilla's nine, by name, as the wide (Steve-build) sheet. */
    private static Identifier wide(String name) {
        return Identifier.withDefaultNamespace("textures/entity/player/wide/" + name + ".png");
    }

    private static final Map<Race, List<Identifier>> SHEETS = build();

    private static Map<Race, List<Identifier>> build() {
        Map<Race, List<Identifier>> table = new EnumMap<>(Race.class);
        // The nine, in Faces' order, which is vanilla's order. Thirty settlers
        // wearing one face is what made a town read as a diorama; these ship
        // with the game, are already the right shape, and cost nothing.
        table.put(Race.HUMAN, Faces.HUMAN_FACES.stream().map(PersonSkins::wide).toList());
        // Two variants, and the difference is paint: one warband marks its faces
        // and one does not, which is what makes a camp of thirty look like
        // thirty orcs rather than one orc thirty times.
        table.put(Race.ORC, List.of(sheet("orc"), sheet("orc_painted")));
        // The orc's build in a sicklier green. One variant, because the model is
        // shared and a scrawnier goblin is a model change; the repaint cost
        // nothing, so it is here rather than Steve.
        table.put(Race.GOBLIN, List.of(sheet("goblin")));
        return Map.copyOf(table);
    }

    private static Identifier sheet(String name) {
        return Identifier.fromNamespaceAndPath(CivilizationMod.MOD_ID,
                "textures/entity/person/" + name + ".png");
    }

    /** Every sheet this race has, in variant order. Never empty. */
    public static List<Identifier> of(Race race) {
        List<Identifier> sheets = SHEETS.get(race);
        return sheets == null || sheets.isEmpty() ? List.of(STEVE) : sheets;
    }

    /** Every race the table has an opinion about. */
    public static java.util.Set<Race> races() {
        return SHEETS.keySet();
    }

    /**
     * The sheet for one race and one variant.
     *
     * <p>Reduced into the race's own list rather than refused, because the two
     * ends count differently on purpose: the server picks a variant from
     * {@link Faces#VARIANTS_PER_RACE} without knowing which races have that
     * many, and this is the side that knows. So a goblin handed variant one gets
     * the one goblin sheet, and adding a second goblin sheet starts splitting
     * them without anybody having to change what the server sends.
     *
     * <p>Humans are the one race where the two ends already agree: {@code Faces}
     * picks an index into the nine and this list <em>is</em> the nine, in the
     * same order, so the reduction is a no-op for them and a culture's chosen
     * face arrives intact.
     */
    public static Identifier sheetFor(Race race, int variant) {
        List<Identifier> sheets = of(race);
        return sheets.get(Math.floorMod(variant, sheets.size()));
    }

    private PersonSkins() {
    }
}
