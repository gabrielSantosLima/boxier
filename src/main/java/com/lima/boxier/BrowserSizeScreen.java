package com.lima.boxier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class BrowserSizeScreen extends Screen {
    private final BlockPos pos;
    private final int initialWidth;
    private final int initialHeight;

    private EditBox widthBox;
    private EditBox heightBox;

    public BrowserSizeScreen(BlockPos pos, int width, int height) {
        super(Component.translatable("boxier.browser.size_title"));
        this.pos = pos;
        this.initialWidth = width;
        this.initialHeight = height;
    }

    @Override
    protected void init() {
        super.init();

        int left = this.width / 2 - 100;
        int top = 50;

        this.widthBox = new EditBox(this.font, left, top + 30, 90, 20, Component.translatable("boxier.browser.width"));
        this.widthBox.setValue(Integer.toString(this.initialWidth));
        this.addRenderableWidget(this.widthBox);

        this.heightBox = new EditBox(this.font, left + 110, top + 30, 90, 20,
                Component.translatable("boxier.browser.height"));
        this.heightBox.setValue(Integer.toString(this.initialHeight));
        this.addRenderableWidget(this.heightBox);

        this.addRenderableWidget(Button.builder(Component.translatable("boxier.browser.save"), button -> this.save())
                .bounds(left, top + 60, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        graphics.text(this.font, Component.translatable("boxier.browser.width"), this.width / 2 - 100, 70, 0xA0A0A0,
                false);
        graphics.text(this.font, Component.translatable("boxier.browser.height"), this.width / 2 + 10, 70, 0xA0A0A0,
                false);
    }

    private void save() {
        int width = parse(this.widthBox.getValue(), BrowserBlockEntity.DEFAULT_SIZE);
        int height = parse(this.heightBox.getValue(), BrowserBlockEntity.DEFAULT_SIZE);
        ClientPacketDistributor.sendToServer(new UpdateBrowserSizePayload(this.pos, width, height));
        this.onClose();
    }

    private static int parse(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(BrowserBlockEntity.MIN_SIZE, Math.min(BrowserBlockEntity.MAX_SIZE, parsed));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
