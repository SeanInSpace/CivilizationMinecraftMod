package com.kingdoms.neoforge.item;

import com.kingdoms.sim.combat.Weaponry;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * The orcs' armory, as real items: what each weapon is made of and what it
 * swings like.
 *
 * <p>Every entry is built the way vanilla builds its own tools —
 * {@code Item.Properties.sword(material, damage, speed)} and {@code AxeItem} —
 * so an orc greatsword in a player's hand obeys the same cooldown sweep, the
 * same enchanting, the same repair and the same durability bar as an iron one.
 * Nothing here is a reskinned stick with a number stapled on.
 *
 * <p><strong>Two materials, and that is the whole of the tier.</strong>
 * {@link #CRUDE} is what a camp with a fire and a rock produces; {@link #FORGED}
 * is what comes off the smithy's rack. They sit either side of the human watch's
 * wood-to-iron step, which is the point: an orc with nothing is already better
 * armed than a militiaman with a wooden sword, and an orc with a smithy behind
 * him is the reason to keep the gate shut.
 *
 * <p>The damage figures below are what a <em>player</em> holding one does. What a
 * settler does with one is {@code GUARD_DAMAGE} plus {@link Weaponry#bonusFor},
 * which is a separate and much flatter table — see the reasoning there.
 */
public final class OrcWeapons {

    private OrcWeapons() {
    }

    /**
     * Beaten out cold over a campfire: harder than wood, softer than iron, and
     * it does not keep an edge.
     *
     * <p>Stone's repair tag and stone's mining tier, because that is honestly
     * what it is, and a crude weapon a player can mend with cobblestone is one
     * they can mend in the field where they found it.
     */
    public static final ToolMaterial CRUDE = new ToolMaterial(
            BlockTags.INCORRECT_FOR_STONE_TOOL, 140, 3.0F, 1.0F, 5,
            ItemTags.STONE_TOOL_MATERIALS);

    /**
     * Off the rack at the smithy: iron, and it holds.
     *
     * <p>Deliberately a shade tougher than vanilla iron (320 uses against 250)
     * and no better at mining. The extra life is the smith's work; the mining
     * tier is not something a weapon should be buying.
     */
    public static final ToolMaterial FORGED = new ToolMaterial(
            BlockTags.INCORRECT_FOR_IRON_TOOL, 320, 6.0F, 2.0F, 14,
            ItemTags.IRON_TOOL_MATERIALS);

    /** The material for a given tier. */
    public static ToolMaterial materialFor(boolean forged) {
        return forged ? FORGED : CRUDE;
    }

    /**
     * How hard each weapon hits and how slowly, before the material's own bonus.
     *
     * <p>Read against vanilla: a sword is {@code (3.0, -2.4)} and an iron axe is
     * {@code (6.0, -3.1)}. Damage is added to the player's base 1 and the
     * material's bonus (+1 crude, +2 forged); speed is added to the base 4, so
     * {@code -3.2} is five swings every four seconds.
     *
     * <p>The shape of the table is the argument. The falchion and the cleaver
     * win on damage over time and lose every exchange they cannot finish; the
     * greatsword and the morningstar are the other way round. The axe sits
     * where vanilla's iron axe sits, because it is one.
     */
    public static float damageBaseline(Weaponry weapon) {
        return switch (weapon) {
            case GREATSWORD -> 7.0F;
            case FALCHION -> 3.0F;
            case CLEAVER -> 4.0F;
            case AXE -> 6.0F;
            case MORNINGSTAR -> 8.0F;
        };
    }

    /** How slowly it comes back up. More negative is slower. */
    public static float speedBaseline(Weaponry weapon) {
        return switch (weapon) {
            case GREATSWORD -> -3.2F;
            case FALCHION -> -2.2F;
            case CLEAVER -> -2.4F;
            case AXE -> -3.1F;
            case MORNINGSTAR -> -3.4F;
        };
    }

    /**
     * Whether this one is an axe in the game's sense — strips logs, scrapes
     * copper, and takes the axe's blocking penalty when it lands on a shield.
     *
     * <p>Only the axe is. A greatsword is not a felling tool however much it
     * looks like one, and a morningstar least of all.
     */
    public static boolean isAxe(Weaponry weapon) {
        return weapon == Weaponry.AXE;
    }

    /** Builds the real item for one weapon at one tier. */
    public static Item make(Weaponry weapon, boolean forged, Item.Properties properties) {
        ToolMaterial material = materialFor(forged);
        if (isAxe(weapon)) {
            return new AxeItem(material, damageBaseline(weapon), speedBaseline(weapon),
                    properties);
        }
        return new Item(properties.sword(material, damageBaseline(weapon),
                speedBaseline(weapon)));
    }

    /**
     * What a player sees on the tooltip for one of these.
     *
     * <p>Here rather than in a test's head: the attack damage a held weapon
     * shows is the player's base 1, plus the weapon's baseline, plus whatever
     * the material adds.
     */
    public static float displayedDamage(Weaponry weapon, boolean forged) {
        return 1.0F + damageBaseline(weapon) + materialFor(forged).attackDamageBonus();
    }

    /** And the swings per second it shows beside it. */
    public static float displayedSpeed(Weaponry weapon) {
        return 4.0F + speedBaseline(weapon);
    }
}
