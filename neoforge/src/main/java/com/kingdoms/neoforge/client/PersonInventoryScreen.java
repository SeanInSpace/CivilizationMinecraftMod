package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.net.PersonInventoryPayload;
import com.kingdoms.sim.person.Appetite;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Inventory;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.settlement.Resources;
import com.kingdoms.sim.settlement.Tallies;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What one settler is carrying, read over their shoulder.
 *
 * <p><strong>A lens, not a container.</strong> Nothing here can be picked up,
 * swapped or taken: a settler's possessions belong to the simulation, which
 * eats out of them, hauls them between stores and spends them on walls. A
 * {@code MenuType} exists to move items between two inventories under the
 * server's supervision, and there is no move to supervise — so this is a plain
 * {@link Screen} opened from a payload, like every other screen in the mod, and
 * the mod registers no menu at all.
 *
 * <p>Drawn rather than textured, and laid out to match the town overview and the
 * warehouse bill so the three read as one mod.
 *
 * <p>Note the 26.2 shape: screens no longer draw immediately. They <em>extract</em>
 * render state, and the framework calls {@code extractBackground} before
 * {@code extractRenderState} on its own, so neither is invoked from here.
 */
public final class PersonInventoryScreen extends Screen {

    private static final int PANEL_WIDTH = 260;

    /** Title, subtitle and the rule under them. */
    private static final int HEADER = 44;

    private static final int ROW = 20;

    /** A line of footer prose. */
    private static final int LINE = 11;

    /** The band a divider rule sits in the middle of. */
    private static final int GAP = 8;

    private static final int PADDING = 14;
    private static final int BOTTOM = 8;

    private static final int PANEL = 0xF0201010;
    private static final int BORDER = 0xFF6A6A6A;
    private static final int RULE = 0xFF4A4A4A;
    private static final int STRIPE = 0x18FFFFFF;
    private static final int TITLE = 0xFFFFE0A0;
    private static final int LABEL = 0xFFC8C8C8;
    private static final int AMOUNT = 0xFFFFFFFF;
    private static final int SUBTLE = 0xFF9A9A9A;

    /** The same ladder of warmth the town overview's distress band uses. */
    private static final int FED_TEXT = 0xFF9A9A9A;
    private static final int HUNGRY_TEXT = 0xFFFFE070;
    private static final int WEAK_TEXT = 0xFFFFAA55;
    private static final int STARVING_TEXT = 0xFFFF6055;

    private final PersonInventoryPayload person;

    // All worked out once, in the constructor. The payload is a snapshot that
    // cannot change while the screen is open and every one of these is a pure
    // function of it, so recomputing them inside extractRenderState allocated two
    // lists and rebuilt three strings every frame for an answer that was settled
    // before the screen appeared.
    private final List<String> footer;
    private final int rows;
    private final boolean hasLoad;
    private final int panelHeight;
    private final String subtitle;

    public PersonInventoryScreen(PersonInventoryPayload person) {
        super(Component.literal(person.name()));
        this.person = person;
        this.hasLoad = person.carriedLoad() > 0;
        this.rows = Math.max(1, person.slots().size());
        this.footer = footerFor(person);
        this.subtitle = subtitleFor(person);
        this.panelHeight = HEADER + rows * ROW
                + (hasLoad ? GAP + ROW : 0)
                + GAP + footer.size() * LINE + BOTTOM;
    }

    /**
     * The prose under the goods: what the pockets are worth, what errand is being
     * run, and the standing note that none of it is the player's.
     */
    private static List<String> footerFor(PersonInventoryPayload person) {
        List<String> lines = new ArrayList<>();
        int nutrition = Inventory.totalNutrition(person.slots());
        if (nutrition > 0) {
            lines.add("Enough food to undo " + nutrition + " hunger");
        }
        person.errand().ifPresent(errand -> lines.add(errandLine(errand)));
        lines.add("Their own belongings; nothing can be taken.");
        return List.copyOf(lines);
    }

    /**
     * The two legs read differently: fetching something, or walking it home.
     *
     * <p>Bulk goods move between two buildings of the same kind — {@code
     * SupplyPlanner} always builds a storehouse-to-storehouse haul — so naming
     * "the storehouse" at both ends would identify neither. Same kind at both
     * ends gets an indefinite article, which is the most this payload honestly
     * knows: it carries the kinds of store, not which building.
     */
    private static String errandLine(PersonInventoryPayload.Errand errand) {
        String what = errand.resource().replace('_', ' ');
        boolean sameKind = errand.from().equals(errand.to());
        return errand.carried() > 0
                ? "Errand: walking " + errand.carried() + " " + what
                        + " to " + (sameKind ? "another " : "the ") + place(errand.to())
                : "Errand: fetching " + errand.requested() + " " + what
                        + " from " + (sameKind ? "a " : "the ") + place(errand.from());
    }

    /**
     * A store kind as somewhere a player could walk to, not as an enum name.
     *
     * <p>Switched over the enum rather than the string so a new kind of store is
     * a compile error here rather than a screen quietly showing "WORKSHOP".
     */
    private static String place(String store) {
        HaulTask.Store kind;
        try {
            kind = HaulTask.Store.valueOf(store);
        } catch (IllegalArgumentException unknownToThisBuild) {
            return store.toLowerCase(Locale.ROOT);
        }
        return switch (kind) {
            case FARM -> "farm";
            case GRANARY -> "granary";
            case MARKET -> "market stall";
            case HOME -> "pantry";
            case STORE -> "storehouse";
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight) / 2;
        int centre = x + PANEL_WIDTH / 2;

        graphics.fill(x, top, x + PANEL_WIDTH, top + panelHeight, PANEL);
        graphics.outline(x, top, PANEL_WIDTH, panelHeight, BORDER);

        graphics.centeredText(font, title, centre, top + 12, TITLE);
        graphics.centeredText(font, Component.literal(subtitle), centre, top + 26,
                colourOf(Appetite.of(person.hunger())));
        graphics.fill(x + PADDING, top + 38, x + PANEL_WIDTH - PADDING, top + 39, RULE);

        int y = top + HEADER;
        List<Inventory.Slot> slots = person.slots();
        if (slots.isEmpty()) {
            graphics.centeredText(font, Component.literal("Empty-handed."), centre, y + 4, SUBTLE);
        }
        for (int i = 0; i < slots.size(); i++) {
            Inventory.Slot slot = slots.get(i);
            int rowY = y + i * ROW;
            if (i % 2 == 1) {
                graphics.fill(x + PADDING - 4, rowY - 2,
                        x + PANEL_WIDTH - PADDING + 4, rowY + ROW - 4, STRIPE);
            }
            Item item = ItemIcons.of(slot.itemId());
            graphics.item(new ItemStack(item), x + PADDING, rowY);
            graphics.text(font, Component.translatable(item.getDescriptionId()),
                    x + PADDING + 22, rowY + 4, LABEL, false);
            right(graphics, x, rowY + 4, Integer.toString(slot.count()), AMOUNT);
        }
        y += rows * ROW;

        // The build load gets its own row under a rule rather than a line of
        // pockets, because it is not in their pockets: it was drawn from the
        // town's stores and is spent block by block at a site.
        if (hasLoad) {
            String material = person.carriedMaterial();
            graphics.fill(x + PADDING, y + 3, x + PANEL_WIDTH - PADDING, y + 4, RULE);
            y += GAP;
            graphics.item(new ItemStack(materialItem(material)), x + PADDING, y);
            graphics.text(font, Component.literal("Building load"),
                    x + PADDING + 22, y + 4, LABEL, false);
            // A load with no material named is a save old enough to have lost it;
            // the count is still true, so it is shown rather than a trailing space.
            String amount = material.isEmpty()
                    ? Integer.toString(person.carriedLoad())
                    : person.carriedLoad() + " " + Tallies.pretty(material);
            right(graphics, x, y + 4, amount, AMOUNT);
            y += ROW;
        }

        graphics.fill(x + PADDING, y + 3, x + PANEL_WIDTH - PADDING, y + 4, RULE);
        y += GAP;
        for (String line : footer) {
            graphics.centeredText(font, Component.literal(line), centre, y, SUBTLE);
            y += LINE;
        }

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private static String subtitleFor(PersonInventoryPayload person) {
        String profession = person.profession();
        String trade = profession.isEmpty()
                ? "Settler"
                : Tallies.pretty(profession.toLowerCase(Locale.ROOT));
        return trade + " · hunger " + person.hunger() + "/" + Person.HUNGER_MAX
                + " (" + Appetite.of(person.hunger()).word() + ")";
    }

    private void right(GuiGraphicsExtractor graphics, int x, int y, String text, int colour) {
        graphics.text(font, Component.literal(text),
                x + PANEL_WIDTH - PADDING - font.width(text), y, colour, false);
    }

    /** The colour and the word come off the same rung, so they cannot disagree. */
    private static int colourOf(Appetite appetite) {
        return switch (appetite) {
            case STARVING -> STARVING_TEXT;
            case WEAK -> WEAK_TEXT;
            case HUNGRY -> HUNGRY_TEXT;
            case FED -> FED_TEXT;
        };
    }

    /**
     * A build load names a ledger word — "wood", "stone" — not an item, so the
     * dictionary that already turns one into the other is asked rather than a
     * second switch that would drift away from it.
     *
     * <p>Falls back to a chest rather than the barrier {@link ItemIcons} uses,
     * because this is the town overview's question — an unrecognised store, not
     * an unrecognised item — and it answers it the same way.
     */
    private static Item materialItem(String resource) {
        String id = Resources.itemFor(resource);
        return id == null ? Items.CHEST : ItemIcons.of(id);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
