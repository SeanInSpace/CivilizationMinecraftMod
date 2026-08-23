package com.kingdoms.neoforge.block;

import com.kingdoms.neoforge.KingdomsBlockEntities;
import com.kingdoms.sim.settlement.Resources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The town's stores, as a container you can open.
 *
 * <p>Until this existed, a settlement's resources were an integer map and
 * nothing else — a charter could announce four hundred and eighty timber with
 * not one log anywhere in the world, and a storehouse was a signpost with a
 * number behind it. This is the first real storage the mod has ever had.
 *
 * <p><strong>The ledger remains the authority.</strong> This chest is the
 * town's stock made visible and handleable, not a second set of books: while
 * the chunk is loaded the reconciler keeps the two agreeing, and away from the
 * town the ledger carries on alone so a settlement still grows while nobody is
 * watching. Contents are persisted anyway — the reconciler decides what is
 * true on load, but a player's items must never evaporate because a save
 * happened at an awkward moment.
 *
 * <p>Three rows, so it reads and behaves as the chest players already know.
 * That is also the ceiling on what a store can show: see
 * {@link Resources#slotsFor} for why gear is the size that matters — a store of
 * thirty-two swords is thirty-two slots, not one.
 */
public class StoreChestBlockEntity extends BaseContainerBlockEntity {

    /** A vanilla three-row chest, and deliberately nothing cleverer. */
    public static final int SLOTS = 27;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public StoreChestBlockEntity(BlockPos pos, BlockState state) {
        super(KingdomsBlockEntities.STORE_CHEST.get(), pos, state);
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> replacement) {
        this.items = replacement;
    }

    @Override
    protected Component getDefaultName() {
        return Component.literal("Town Stores");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory playerInventory) {
        return ChestMenu.threeRows(id, playerInventory, this);
    }

    /**
     * Only what the town can actually use.
     *
     * <p>The stores are not a junk drawer. Refusing at the slot keeps the
     * invariant the reconciler depends on — everything in here is a resource
     * the ledger can account for — which is what stops a donated diamond from
     * being silently converted into nothing on the next sync.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.isEmpty() || Resources.isStorable(idOf(stack));
    }

    /** The registry id of a stack, in the form the simulation names items by. */
    public static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
    }
}
