package com.civilization.neoforge.trade;

import com.civilization.neoforge.CivilizationItems;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The money, in the one form a player can hold.
 *
 * <p>Inside a town money is an integer on the settlement and nobody owns any of
 * it. At a counter the two meet: the item below is created out of a treasury
 * when the town pays, and consumed into one when the town is paid, one for one.
 * The invariant is that every one of these entering the world came out of a
 * treasury and every one leaving it went into one, which is why nothing in
 * {@link MarketCounter} moves an item without the ledger having moved first.
 *
 * <p><strong>The name is not settled, so it lives in as few places as
 * possible.</strong> There are exactly two, and this class exists to make that
 * true:
 *
 * <ol>
 *   <li>{@link #ID} — the registry path, {@code civilization:coin}, which is
 *       also what the three asset files are called.</li>
 *   <li>{@code item.civilization.coin} in {@code en_us.json} — the word a
 *       player actually reads.</li>
 * </ol>
 *
 * <p>Nothing else anywhere writes the name down. Every message that says it out
 * loud builds from {@link #name()}, which is the translated value of that lang
 * key, so changing what players call the money is a one-line change in a
 * language file and needs no Java touched at all — not even a recompile. See
 * {@code docs/CURRENCY.md}, which spells the whole procedure out.
 *
 * <p><strong>Why no plural.</strong> {@link #amount} says "7 Coin", not "7
 * Coins". A plural is a second word, and a second word is a third place the
 * name lives — one whose grammar this mod would be guessing at the moment
 * anybody translates it. Money is commonly uncountable in English anyway ("7
 * gold", "7 silver"), so the reading costs nothing and the rename stays cheap.
 */
public final class Currency {

    /**
     * The registry path. <strong>Rename point 1 of 2.</strong>
     *
     * <p>A compile-time constant, so {@link CivilizationItems} can register the
     * item under it without this class having to be initialized first.
     */
    public static final String ID = "coin";

    private Currency() {
    }

    /** The one item money takes the shape of. */
    public static Item item() {
        return CivilizationItems.COIN.get();
    }

    /** A fresh single, for anything that hands money over a stack at a time. */
    public static ItemStack stack() {
        return new ItemStack(item());
    }

    /** Whether this is money — the only test anything should make of a stack. */
    public static boolean is(ItemStack stack) {
        return stack.is(item());
    }

    /**
     * The money's name, as players read it. <strong>Rename point 2 of 2 lives
     * at the other end of this.</strong>
     *
     * <p>The description id is derived by vanilla from the registry key at
     * construction — {@code item.civilization.coin} — so this is the same
     * component vanilla itself hangs on the item, and it resolves through
     * whatever language the reader is in.
     */
    public static Component name() {
        return Component.translatable(item().getDescriptionId());
    }

    /**
     * A sum of money, for dropping into a sentence: "7 Coin".
     *
     * <p>The number is a number in every language; only the word is translated.
     */
    public static MutableComponent amount(int howMuch) {
        return Component.literal(howMuch + " ").append(name());
    }
}
