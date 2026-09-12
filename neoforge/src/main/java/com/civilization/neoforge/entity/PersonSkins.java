package com.civilization.neoforge.entity;

import com.civilization.neoforge.CivilizationMod;
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
 * <p>Humans are listed explicitly with vanilla's Steve. Leaving them out would
 * work — the fallback is Steve either way — but then "humans have no skin of
 * their own yet" would be indistinguishable from "somebody forgot humans", and
 * the first is a decision while the second is a bug.
 *
 * <p>Every sheet named here is drawn by {@code neoforge/tools/orc_skin_art.py}.
 */
public final class PersonSkins {

    /** Vanilla's default player skin, which is what a human settler still wears. */
    public static final Identifier STEVE =
            Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private static final Map<Race, List<Identifier>> SHEETS = build();

    private static Map<Race, List<Identifier>> build() {
        Map<Race, List<Identifier>> table = new EnumMap<>(Race.class);
        table.put(Race.HUMAN, List.of(STEVE));
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
     * {@link PersonEntity#SKINS_PER_RACE} without knowing which races have that
     * many, and this is the side that knows. So a goblin handed variant one gets
     * the one goblin sheet, and adding a second goblin sheet starts splitting
     * them without anybody having to change what the server sends.
     */
    public static Identifier sheetFor(Race race, int variant) {
        List<Identifier> sheets = of(race);
        return sheets.get(Math.floorMod(variant, sheets.size()));
    }

    private PersonSkins() {
    }
}
