package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.block.StoreChestBlockEntity;
import com.kingdoms.sim.settlement.Resources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A real chest, answering in resources.
 *
 * <p>Every {@code ItemStack} the mirror would otherwise have handled lives in
 * this one class. What is left in {@link StoreSync} is arithmetic about slots
 * and counts, which is the point.
 */
public record ChestShelves(StoreChestBlockEntity chest) implements Shelves {

    @Override
    public int slots() {
        return chest.getContainerSize();
    }

    @Override
    public boolean isEmpty(int slot) {
        return chest.getItem(slot).isEmpty();
    }

    @Override
    public String resourceAt(int slot) {
        ItemStack stack = chest.getItem(slot);
        if (!StoreChestBlockEntity.speaksFor(stack)) {
            return null;   // empty, or something the stores have no use for
        }
        return Resources.resourceOf(StoreChestBlockEntity.idOf(stack));
    }

    @Override
    public int amountAt(int slot) {
        return chest.getItem(slot).getCount();
    }

    @Override
    public void lay(int slot, String resource, int amount) {
        Item item = itemFor(resource);
        if (item == null) {
            return;   // guarded by perSlot returning zero; belt and braces
        }
        chest.setItem(slot, new ItemStack(item, amount));
    }

    @Override
    public void empty(int slot) {
        chest.setItem(slot, ItemStack.EMPTY);
    }

    @Override
    public int lastSynced(String resource) {
        return chest.lastSynced(resource);
    }

    @Override
    public void setLastSynced(String resource, int amount) {
        chest.setLastSynced(resource, amount);
    }

    @Override
    public int perSlot(String resource) {
        Item item = itemFor(resource);
        if (item == null) {
            return 0;   // nothing to pay this out in
        }
        // The smaller of what the game allows and what the town chose. Gear is
        // one to a slot because it does not stack; timber is sixty-four.
        return Math.min(item.getDefaultMaxStackSize(), Resources.stackSize(resource));
    }

    @Override
    public void done() {
        chest.setChanged();
    }

    private static Item itemFor(String resource) {
        String id = Resources.itemFor(resource);
        return id == null ? null
                : BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(null);
    }
}
