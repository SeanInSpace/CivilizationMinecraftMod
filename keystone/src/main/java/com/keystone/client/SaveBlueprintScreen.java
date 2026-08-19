package com.keystone.client;

import com.keystone.blueprint.Scanner;
import com.keystone.item.BlueprintWandItem;
import com.keystone.net.SaveBlueprintPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Names a marked region and saves it.
 *
 * <p>Drawn rather than textured — a panel, a field and two buttons need no art,
 * and shipping none keeps the mod's assets to what actually has to exist.
 *
 * <p>Note the 26.2 shape: screens no longer draw immediately. They <em>extract</em>
 * render state, and the framework calls {@code extractBackground} before
 * {@code extractRenderState} on its own, so neither is invoked from here.
 */
public final class SaveBlueprintScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 116;

    private static final int PANEL = 0xF0201010;
    private static final int BORDER = 0xFF6A6A6A;
    private static final int LABEL = 0xFFA0A0A0;
    private static final int WARNING = 0xFFFFAA55;

    private static final int KEY_ENTER = 257;
    private static final int KEY_NUMPAD_ENTER = 335;

    private final BlockPos cornerA;
    private final BlockPos cornerB;

    private EditBox nameField;

    public SaveBlueprintScreen(BlockPos cornerA, BlockPos cornerB) {
        super(Component.literal("Save blueprint"));
        this.cornerA = cornerA;
        this.cornerB = cornerB;
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    @Override
    protected void init() {
        int x = left() + 12;
        int buttonWidth = (PANEL_WIDTH - 32) / 2;

        nameField = new EditBox(font, x, top() + 44, PANEL_WIDTH - 24, 20,
                Component.literal("Name"));
        nameField.setMaxLength(96);
        nameField.setHint(Component.literal("my_house   or   kingdoms:house"));
        addRenderableWidget(nameField);
        setInitialFocus(nameField);

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(x, top() + PANEL_HEIGHT - 30, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(x + buttonWidth + 8, top() + PANEL_HEIGHT - 30, buttonWidth, 20)
                .build());
    }

    private void save() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        // The server re-derives the region from the wand actually in hand; all
        // that travels is the name.
        ClientPacketDistributor.sendToServer(new SaveBlueprintPayload(name));
        onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == KEY_ENTER || event.key() == KEY_NUMPAD_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = left();
        int y = top();

        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, PANEL);
        graphics.fill(x, y, x + PANEL_WIDTH, y + 1, BORDER);
        graphics.fill(x, y + PANEL_HEIGHT - 1, x + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER);
        graphics.fill(x, y, x + 1, y + PANEL_HEIGHT, BORDER);
        graphics.fill(x + PANEL_WIDTH - 1, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER);

        graphics.text(font, title, x + 12, y + 12, 0xFFFFFFFF, false);

        int w = Math.abs(cornerA.getX() - cornerB.getX()) + 1;
        int h = Math.abs(cornerA.getY() - cornerB.getY()) + 1;
        int d = Math.abs(cornerA.getZ() - cornerB.getZ()) + 1;
        long volume = Scanner.volumeOf(cornerA, cornerB);

        graphics.text(font,
                Component.literal(w + " x " + h + " x " + d + "   (" + volume + " blocks)"),
                x + 12, y + 26, LABEL, false);

        if (volume > BlueprintWandItem.WARN_ABOVE) {
            graphics.text(font,
                    Component.literal("Large region — this may take a moment."),
                    x + 12, y + 70, WARNING, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
