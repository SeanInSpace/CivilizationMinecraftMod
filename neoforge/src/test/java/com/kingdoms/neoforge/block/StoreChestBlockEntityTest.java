package com.kingdoms.neoforge.block;

import com.kingdoms.sim.settlement.Resources;
import com.kingdoms.sim.settlement.TownStores;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The join between the ledger's words and the game's items.
 *
 * <p>{@code :common} may not know what an item is, so it names them by string
 * and its own tests can only check those strings against each other. Nothing
 * anywhere checked them against the actual registry — so a typo, or a
 * vanilla rename between versions, would have shown up as a store that quietly
 * could not pay anything out: {@code itemFor} returns an id, the lookup misses,
 * and {@code writeLedgerInto} skips the resource with the comment "nothing to
 * pay this out in". A silent empty chest, in a green build.
 *
 * <p><strong>What this environment cannot do.</strong> Item <em>stacks</em>
 * cannot be built here. ModDevGradle's JUnit game runs
 * {@code SharedConstants.tryDetectVersion}, {@code Bootstrap.bootStrap} and
 * {@code ServerModLoader.load}, which is enough to populate and query the
 * registries — 1564 items, ids resolving — but item components are never bound
 * to their holders, so {@code new ItemStack(Items.OAK_LOG)} throws "Components
 * not bound yet" from {@code Holder.Reference.components}. Re-running bootstrap
 * does not help and neither does {@code getDefaultInstance}; this was measured,
 * not assumed. The same wall stands in front of any property an item keeps in
 * its components, {@code getDefaultMaxStackSize} included — so "does a resource
 * claim a bigger stack than its item allows" cannot be asked here, and stays a
 * playtest concern along with everything else needing a real stack.
 */
class StoreChestBlockEntityTest {

    private static Optional<Item> lookUp(String id) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id));
    }

    @Test
    void everyItemTheLedgerPromisesIsARealItem() {
        for (String resource : Resources.known()) {
            String id = Resources.itemFor(resource);
            assertNotNull(id, resource + " has nothing to be paid out in");
            assertTrue(lookUp(id).isPresent(),
                    "the ledger pays " + resource + " out in " + id
                            + ", which is not an item this game has");
        }
    }

    @Test
    void whatTheStoreLaysOutItReadsBackAsTheSameThing() {
        // The round trip the reconciler depends on. If a store shows resource R
        // as item X but X reads back as something else, then the very next pass
        // counts the shelves it just filled as a withdrawal — the town billed
        // for its own stock, once a second.
        for (String resource : Resources.STORED) {
            String id = Resources.itemFor(resource);
            assertEquals(resource, Resources.resourceOf(id),
                    id + " is laid out for " + resource + " but does not read back as it");
        }
    }

    @Test
    void aStoreShowsExactlyWhatTheSettlementSaysAStoreHolds() {
        // Two lists that had to be kept identical would eventually stop being,
        // so the container reads the settlement's. This is the assertion that
        // keeps it that way if anybody re-introduces a local copy.
        assertEquals(Resources.STORED, StoreChestBlockEntity.MIRRORED,
                "the container must not have opinions of its own about what it holds");
    }

    @Test
    void theBulkMaterialsAreTheOnesABuilderFetches() {
        assertTrue(StoreChestBlockEntity.MIRRORED.contains(TownStores.WOOD));
        assertTrue(StoreChestBlockEntity.MIRRORED.contains(TownStores.STONE));
        assertTrue(StoreChestBlockEntity.MIRRORED.contains(TownStores.IRON));
        assertTrue(StoreChestBlockEntity.MIRRORED.contains(TownStores.SAPLINGS));
    }

    @Test
    void foodIsLeftToItsOwnEconomy() {
        // Granary, stalls, pantries and haulers already move food. A timber
        // store that mirrored it would clear it off the shelves every pass.
        assertTrue(!StoreChestBlockEntity.MIRRORED.contains(TownStores.FOOD),
                "food belongs to the granary, not the timber store");
    }

    @Test
    void gearIsLeftOutBecauseItDoesNotStack() {
        assertTrue(!StoreChestBlockEntity.MIRRORED.contains(TownStores.TOOLS));
        assertTrue(!StoreChestBlockEntity.MIRRORED.contains(TownStores.WEAPONS));
        assertTrue(!StoreChestBlockEntity.MIRRORED.contains(TownStores.ARMOUR));
    }

    @Test
    void theStoreIsSixRowsBecauseAMatureTownFillsThree() {
        // Sized from the ledger it has to show rather than picked for looks: a
        // town with one storehouse can hold 912 timber and 912 stone, which is
        // thirty slots before anything else is counted.
        assertEquals(54, StoreChestBlockEntity.SLOTS);
    }

}
