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

    /**
     * The lowland people, who are what every town has quietly been all along.
     *
     * <p>Settlements were already stamped {@code kingdoms:norman} and the
     * blueprint loader was already looking for {@code kingdoms:norman/house}
     * before anything defined a culture by that name — so every lookup fell
     * through to {@link #DEFAULT} and nobody noticed, because the default was
     * the only thing there was to fall through to. Naming it is what turns the
     * fallback from a coincidence into a decision.
     */
    public static final Culture NORMAN = new Culture(
            "kingdoms:norman",
            List.of("minecraft:cow", "minecraft:sheep", "minecraft:pig", "minecraft:chicken"),
            LAYOUT_RING);

    /**
     * The hill people, who keep different beasts.
     *
     * <p>A second entry in the table, which is the whole claim the culture type
     * was making: that a second people is filling this in rather than threading
     * a new idea through the simulation. Goats and rabbits over pigs and cows —
     * the same four pens, because the animal farm's plot is reserved in the
     * catalogue and a culture cannot quietly outgrow the ground set aside for
     * it. Widening that reservation is what a fifth pen would cost.
     */
    public static final Culture HIGHLAND = new Culture(
            "kingdoms:highland",
            List.of("minecraft:goat", "minecraft:sheep", "minecraft:rabbit",
                    "minecraft:chicken"),
            LAYOUT_RING);

    private static final Map<String, Culture> KNOWN = Map.of(
            DEFAULT.id(), DEFAULT,
            NORMAN.id(), NORMAN,
            HIGHLAND.id(), HIGHLAND);

    /** Every culture that has been defined. */
    public static java.util.Collection<Culture> all() {
        return KNOWN.values();
    }

    /**
     * The named culture, or the default when nobody has defined it.
     *
     * <p>Null-guarded, and not defensively: {@code KNOWN} is a {@code Map.of},
     * which throws on a null key rather than missing it. A settlement restored
     * from a save written before cultures had names carries no id at all, so
     * the one lookup guaranteed to happen on an old world was the one that
     * would have thrown.
     */
    public static Culture of(String id) {
        return id == null ? DEFAULT : KNOWN.getOrDefault(id, DEFAULT);
    }

    /** How many pens the animal farm needs to hold this culture's beasts. */
    public int penCount() {
        return pennedAnimals.size();
    }
}
