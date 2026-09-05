package com.kingdoms.neoforge.block;

import com.kingdoms.neoforge.KingdomsBlockEntities;
import com.kingdoms.sim.settlement.Resources;
import com.kingdoms.sim.settlement.StoreMirror;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
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
 * <p>It behaves as the chest players already know, and its size is taken from
 * the ledger it has to show rather than chosen for looks — see {@link #SLOTS}
 * and {@link #MIRRORED} for what it speaks for and what it deliberately does
 * not.
 */
public class StoreChestBlockEntity extends BaseContainerBlockEntity {

    /**
     * Six rows — a double chest.
     *
     * <p>Sized from the ledger it has to show rather than picked for looks. A
     * town with one storehouse can hold 912 timber and 912 stone, which is
     * thirty slots before anything else is counted; three rows would have
     * overflowed on an ordinary mature town.
     */
    public static final int SLOTS = 54;

    /**
     * The resources this chest speaks for.
     *
     * <p>Read from {@link Resources#STORED} rather than listed again here.
     * Which goods a store holds is a fact about the settlement, and the
     * settlement now decides it: a building's ledger and the container showing
     * that ledger must agree about what is being shown, and two lists that had
     * to be kept identical would eventually stop being.
     */
    public static final List<String> MIRRORED = Resources.STORED;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    /**
     * What the reconciler last wrote here, per resource.
     *
     * <p>This is what makes a player's hand distinguishable from the town's own
     * bookkeeping. The chest is rewritten from the ledger every sync, so the
     * only way to know somebody took a stack is to compare what is here now
     * against what was put here last time.
     *
     * <p>It is persisted for the same reason the contents are. If it were lost
     * on reload, the next sync would read the whole chest as a donation and
     * credit the town twice for stock it already had.
     */
    private final Map<String, Integer> lastSynced = new LinkedHashMap<>();

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
        return ChestMenu.sixRows(id, playerInventory, this);
    }

    /** What the reconciler last wrote for a resource. */
    public int lastSynced(String resource) {
        return lastSynced.getOrDefault(resource, 0);
    }

    public void setLastSynced(String resource, int amount) {
        // No setChanged here: the reconciler marks the entity once when it is
        // done with it. Doing it per resource cost four dirty-marks a second,
        // each of which walks the neighbors looking for a comparator.
        lastSynced.put(resource, Math.max(0, amount));
    }

    /**
     * Only what the town can actually use — advisory, not a guarantee.
     *
     * <p>Worth stating exactly, because the reconciler must not be written as
     * though this were enforced. Hoppers and other automation do ask. A player
     * is never asked: the chest screen builds plain slots whose
     * {@code mayPlace} returns true unconditionally, so anything at all can be
     * dragged in by hand.
     *
     * <p>So this keeps machines honest and no more. Whatever does get in is
     * left exactly where it lies — neither counted toward the town's stock nor
     * cleared away — which is why the reconciler has to tolerate finding it
     * rather than assume it cannot be there.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        // Only what this chest actually speaks for. Accepting food or a sword
        // here would put items in a container nothing reconciles, and the next
        // sync would rewrite the slot out from under them.
        return speaksFor(stack);
    }

    /**
     * Whether this chest is the authority for an item.
     *
     * <p>One predicate, because two would have to agree exactly: the slot uses
     * it to decide what may be put in, and the reconciler uses it to decide
     * what it may clear out. Disagreement means either clearing a slot the
     * chest accepted, or leaving one it refused.
     */
    public static boolean speaksFor(ItemStack stack) {
        return !stack.isEmpty() && StoreMirror.mirrors(idOf(stack), MIRRORED);
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
        lastSynced.clear();
        for (String resource : MIRRORED) {
            // Clamped here because this is the one way a negative can arrive:
            // the setter guards its own writes, but a hand-edited or damaged
            // save comes straight in through this line.
            lastSynced.put(resource, Math.max(0, input.getIntOr(SYNCED + resource, 0)));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        for (String resource : MIRRORED) {
            output.putInt(SYNCED + resource, lastSynced(resource));
        }
    }

    private static final String SYNCED = "synced_";
}
