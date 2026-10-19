package com.lima.boxier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class FramerScreen extends Screen {
    private final BlockPos pos;
    private final String initialUrl;
    private final int initialWidth;
    private final int initialHeight;

    private EditBox urlBox;
    private EditBox widthBox;
    private EditBox heightBox;

    public FramerScreen(BlockPos pos, String url, int width, int height) {
        super(Component.translatable("boxier.framer.title"));
        this.pos = pos;
        this.initialUrl = url;
        this.initialWidth = width;
        this.initialHeight = height;
    }

    @Override
    protected void init() {
        super.init();

        int left = this.width / 2 - 100;
        int top = 50;

        this.urlBox = new EditBox(this.font, left, top, 200, 20, Component.translatable("boxier.framer.url"));
        this.urlBox.setMaxLength(UpdateFramerPayload.MAX_URL_LENGTH);
        this.urlBox.setValue(this.initialUrl);
        this.addRenderableWidget(this.urlBox);

        this.widthBox = new EditBox(this.font, left, top + 30, 90, 20, Component.translatable("boxier.framer.width"));
        this.widthBox.setValue(Integer.toString(this.initialWidth));
        this.addRenderableWidget(this.widthBox);

        this.heightBox = new EditBox(this.font, left + 110, top + 30, 90, 20, Component.translatable("boxier.framer.height"));
        this.heightBox.setValue(Integer.toString(this.initialHeight));
        this.addRenderableWidget(this.heightBox);

        this.addRenderableWidget(Button.builder(Component.translatable("boxier.framer.save"), button -> this.save())
                .bounds(left, top + 60, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        graphics.text(this.font, Component.translatable("boxier.framer.url"), this.width / 2 - 100, 40, 0xA0A0A0, false);
    }

    private void save() {
        int width = parse(this.widthBox.getValue(), FramerBlockEntity.DEFAULT_SIZE);
        int height = parse(this.heightBox.getValue(), FramerBlockEntity.DEFAULT_SIZE);
        ClientPacketDistributor.sendToServer(new UpdateFramerPayload(this.pos, this.urlBox.getValue().trim(), width, height));
        this.onClose();
    }

    private static int parse(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
