package com.kingdoms.sim.culture;

import java.util.List;
import java.util.Map;

/**
 * What makes one people's town different from another's.
 *
 * <p>One culture ships today. The point of the type is that everything which
 * *should* vary by culture has somewhere to live before there are two of them —
 * so adding a second is filling in a table rather than threading a new concept
 * through the simulation.
 *
 * <p>Every field here is a plain value or a list of ids, precisely so this can
 * become a datapack entry later without the planners changing.
 *
 * @param id            the culture's identifier
 * @param pennedAnimals which beasts the animal farm keeps, one pen each, in order
 * @param layout        how the settlement arranges itself; see {@code Layouts}
 */
public record Culture(String id, List<String> pennedAnimals, String layout) {

    /** Arrangement ids. Only one exists so far, and it is the fallback for any unknown. */
    public static final String LAYOUT_RING = "ring";

    public static final Culture DEFAULT = new Culture(
            "kingdoms:default",
            List.of("minecraft:cow", "minecraft:sheep", "minecraft:pig", "minecraft:chicken"),
            LAYOUT_RING);

    private static final Map<String, Culture> KNOWN = Map.of(DEFAULT.id(), DEFAULT);

    /** The named culture, or the default when nobody has defined it. */
    public static Culture of(String id) {
        return KNOWN.getOrDefault(id, DEFAULT);
    }

    /** How many pens the animal farm needs to hold this culture's beasts. */
    public int penCount() {
        return pennedAnimals.size();
    }
}
