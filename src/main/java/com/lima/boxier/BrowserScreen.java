package com.lima.boxier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import org.lwjgl.sdl.SDLKeycode;

public class BrowserScreen extends Screen {
    private final BlockPos pos;

    private EditBox urlBox;
    private EditBox widthBox;
    private EditBox heightBox;
    private boolean granted;
    private String status = "Requesting control...";

    private int imageX;
    private int imageY;
    private int imageWidth;
    private int imageHeight;
    private boolean mouseOverView;

    public BrowserScreen(BlockPos pos) {
        super(Component.translatable("boxier.browser.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();

        this.addRenderableWidget(Button.builder(Component.literal("<"), button -> this.sendAction(BrowserActionPayload.BACK))
                .bounds(8, 4, 28, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), button -> this.sendAction(BrowserActionPayload.FORWARD))
                .bounds(40, 4, 28, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("R"), button -> this.sendAction(BrowserActionPayload.RELOAD))
                .bounds(72, 4, 28, 20)
                .build());

        this.urlBox = new EditBox(this.font, 110, 4, this.width - 200, 20, Component.translatable("boxier.browser.url"));
        this.urlBox.setMaxLength(2048);
        this.urlBox.setValue("");
        this.addRenderableWidget(this.urlBox);

        this.addRenderableWidget(Button.builder(Component.translatable("boxier.browser.go"), button -> this.navigate())
                .bounds(this.width - 86, 4, 36, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("X"), button -> this.onClose())
                .bounds(this.width - 46, 4, 38, 20)
                .build());

        this.addRenderableWidget(new VolumeSlider(8, this.height - 30, 140, 20,
                BrowserAudioManager.getVolume()));

        int displayWidth = BrowserBlockEntity.DEFAULT_SIZE;
        int displayHeight = BrowserBlockEntity.DEFAULT_SIZE;
        if (this.minecraft.level.getBlockEntity(this.pos) instanceof BrowserBlockEntity browser) {
            displayWidth = browser.getDisplayWidth();
            displayHeight = browser.getDisplayHeight();
        }

        this.widthBox = new EditBox(this.font, 162, this.height - 30, 50, 20,
                Component.translatable("boxier.browser.width_short"));
        this.widthBox.setValue(Integer.toString(displayWidth));
        this.addRenderableWidget(this.widthBox);

        this.heightBox = new EditBox(this.font, 226, this.height - 30, 50, 20,
                Component.translatable("boxier.browser.height_short"));
        this.heightBox.setValue(Integer.toString(displayHeight));
        this.addRenderableWidget(this.heightBox);

        this.addRenderableWidget(Button.builder(Component.translatable("boxier.browser.apply"), button -> this.applySize())
                .bounds(282, this.height - 30, 60, 20)
                .build());

        this.urlBox.setFocused(false);
        this.setFocused(null);
        ClientPacketDistributor.sendToServer(new BrowserControlPayload(this.pos, false));
    }

    public boolean matches(BlockPos pos) {
        return this.pos.equals(pos);
    }

    public void handleControlResponse(BrowserControlResponsePayload payload) {
        if (!matches(payload.pos())) {
            return;
        }
        this.granted = payload.granted();
        if (payload.granted()) {
            this.status = "";
            this.urlBox.setValue(payload.url());
        } else {
            this.status = payload.message();
        }
    }

    private void navigate() {
        if (!this.granted) {
            return;
        }
        this.urlBox.setFocused(false);
        this.setFocused(null);
        ClientPacketDistributor.sendToServer(new BrowserActionPayload(this.pos, BrowserActionPayload.NAVIGATE,
                this.urlBox.getValue().trim()));
    }

    private void applySize() {
        int width = parseSize(this.widthBox.getValue(), BrowserBlockEntity.DEFAULT_SIZE);
        int height = parseSize(this.heightBox.getValue(), BrowserBlockEntity.DEFAULT_SIZE);
        this.widthBox.setValue(Integer.toString(width));
        this.heightBox.setValue(Integer.toString(height));
        this.widthBox.setFocused(false);
        this.heightBox.setFocused(false);
        this.setFocused(null);
        ClientPacketDistributor.sendToServer(new UpdateBrowserSizePayload(this.pos, width, height));
    }

    private static int parseSize(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(BrowserBlockEntity.MIN_SIZE, Math.min(BrowserBlockEntity.MAX_SIZE, parsed));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean isTextInputFocused() {
        return this.urlBox.isFocused() || this.widthBox.isFocused() || this.heightBox.isFocused();
    }

    private void sendAction(int action) {
        ClientPacketDistributor.sendToServer(new BrowserActionPayload(this.pos, action, ""));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xFF000000);
        this.layout();

        BrowserTextureCache.Entry entry = BrowserTextureCache.get(this.minecraft.level, this.pos);
        if (entry != null && entry.id() != null) {
            graphics.blit(entry.id(), this.imageX, this.imageY, this.imageX + this.imageWidth,
                    this.imageY + this.imageHeight, 0.0F, 1.0F, 0.0F, 1.0F);
        } else {
            graphics.fill(this.imageX, this.imageY, this.imageX + this.imageWidth,
                    this.imageY + this.imageHeight, 0xFF101418);
        }

        graphics.fill(0, 0, this.width, 28, 0xCC000000);
        this.mouseOverView = insideImage(mouseX, mouseY);
        if (!this.granted && !this.status.isEmpty()) {
            graphics.centeredText(this.font, Component.literal(this.status), this.width / 2,
                    this.imageY + this.imageHeight / 2, 0xFFFFFF);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, Component.translatable("boxier.browser.width_short"), 152, this.height - 25, 0xFFFFFF,
                false);
        graphics.text(this.font, Component.translatable("boxier.browser.height_short"), 216, this.height - 25, 0xFFFFFF,
                false);
    }

    private void layout() {
        int availableX = 8;
        int availableY = 32;
        int availableWidth = Math.max(1, this.width - 16);
        int availableHeight = Math.max(1, this.height - 76);

        int textureWidth = 1280;
        int textureHeight = 1280;
        BrowserTextureCache.Entry entry = BrowserTextureCache.get(this.minecraft.level, this.pos);
        if (entry != null && entry.width() > 0 && entry.height() > 0) {
            textureWidth = entry.width();
            textureHeight = entry.height();
        } else if (this.minecraft.level.getBlockEntity(this.pos) instanceof BrowserBlockEntity browser) {
            textureWidth = browser.getDisplayWidth();
            textureHeight = browser.getDisplayHeight();
        }

        double scale = Math.min(availableWidth / (double) textureWidth, availableHeight / (double) textureHeight);
        this.imageWidth = Math.max(1, (int) Math.round(textureWidth * scale));
        this.imageHeight = Math.max(1, (int) Math.round(textureHeight * scale));
        this.imageX = availableX + (availableWidth - this.imageWidth) / 2;
        this.imageY = availableY + (availableHeight - this.imageHeight) / 2;
    }

    private boolean insideImage(double mouseX, double mouseY) {
        return mouseX >= this.imageX && mouseX < this.imageX + this.imageWidth
                && mouseY >= this.imageY && mouseY < this.imageY + this.imageHeight;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (this.granted && insideImage(event.x(), event.y())) {
            this.urlBox.setFocused(false);
            this.setFocused(null);
            sendMouse(BrowserMousePayload.DOWN, event, 0.0F);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (super.mouseReleased(event)) {
            return true;
        }
        if (this.granted) {
            sendMouse(BrowserMousePayload.UP, event, 0.0F);
            return true;
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        boolean inside = insideImage(mouseX, mouseY);
        this.mouseOverView = inside;
        if (this.granted && inside) {
            sendMouse(BrowserMousePayload.MOVE, mouseX, mouseY, 0, 0, 0.0F);
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (super.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        boolean inside = insideImage(event.x(), event.y());
        this.mouseOverView = inside;
        if (this.granted && inside) {
            sendMouse(BrowserMousePayload.MOVE, event, 0.0F);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        this.mouseOverView = insideImage(mouseX, mouseY);
        if (this.granted && this.mouseOverView) {
            ClientPacketDistributor.sendToServer(new BrowserMousePayload(this.pos, BrowserMousePayload.SCROLL,
                    normalizedX(mouseX), normalizedY(mouseY), 0, 0, (float) -scrollY * 120.0F));
            return true;
        }
        return false;
    }

    private void sendMouse(int action, MouseButtonEvent event, float scroll) {
        ClientPacketDistributor.sendToServer(new BrowserMousePayload(this.pos, action,
                normalizedX(event.x()), normalizedY(event.y()), event.button(), cdpModifiers(event.modifiers()), scroll));
    }

    private void sendMouse(int action, double mouseX, double mouseY, int button, int modifiers, float scroll) {
        ClientPacketDistributor.sendToServer(new BrowserMousePayload(this.pos, action,
                normalizedX(mouseX), normalizedY(mouseY), button, modifiers, scroll));
    }

    private float normalizedX(double mouseX) {
        return clamp((float) ((mouseX - this.imageX) / (double) this.imageWidth));
    }

    private float normalizedY(double mouseY) {
        return clamp((float) ((mouseY - this.imageY) / (double) this.imageHeight));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean escape = event.keycode() == SDLKeycode.SDLK_ESCAPE;
        boolean control = (event.modifiers() & (SDLKeycode.SDL_KMOD_LCTRL | SDLKeycode.SDL_KMOD_RCTRL)) != 0;

        if (escape) {
            if (control) {
                sendKey(BrowserKeyPayload.DOWN, event, "");
                sendKey(BrowserKeyPayload.UP, event, "");
            } else {
                this.onClose();
            }
            return true;
        }

        if (this.isTextInputFocused()) {
            if (event.keycode() == SDLKeycode.SDLK_RETURN || event.keycode() == SDLKeycode.SDLK_RETURN2) {
                if (this.widthBox.isFocused() || this.heightBox.isFocused()) {
                    this.applySize();
                } else {
                    this.navigate();
                }
                return true;
            }
            if (super.keyPressed(event)) {
                return true;
            }
            return true;
        }

        sendKey(BrowserKeyPayload.DOWN, event, "");
        String text = printableText(event);
        if (!text.isEmpty()) {
            sendText(text);
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (this.isTextInputFocused()) {
            return super.keyReleased(event);
        }
        sendKey(BrowserKeyPayload.UP, event, "");
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.isTextInputFocused()) {
            return super.charTyped(event);
        }
        if (!this.granted || event.codepoint() <= 127) {
            return true;
        }
        sendText(event.codepointAsString());
        return true;
    }

    private void sendKey(int action, KeyEvent event, String text) {
        BrowserKeyMapper.KeyData data = mapKey(event);
        ClientPacketDistributor.sendToServer(new BrowserKeyPayload(this.pos, action, data.key(), data.code(),
                data.keyCode(), text, cdpModifiers(event.modifiers())));
    }

    private void sendText(String text) {
        ClientPacketDistributor.sendToServer(new BrowserKeyPayload(this.pos, BrowserKeyPayload.TEXT, "", "", 0,
                text, 0));
    }

    private static BrowserKeyMapper.KeyData mapKey(KeyEvent event) {
        return BrowserKeyMapper.map(event.keycode());
    }

    private static String printableText(KeyEvent event) {
        BrowserKeyMapper.KeyData mapped = mapKey(event);
        if (mapped.key().length() != 1) {
            return "";
        }
        int modifiers = event.modifiers();
        if ((modifiers & (SDLKeycode.SDL_KMOD_LCTRL | SDLKeycode.SDL_KMOD_RCTRL
                | SDLKeycode.SDL_KMOD_LALT | SDLKeycode.SDL_KMOD_RALT
                | SDLKeycode.SDL_KMOD_LGUI | SDLKeycode.SDL_KMOD_RGUI)) != 0) {
            return "";
        }
        boolean shift = (modifiers & (SDLKeycode.SDL_KMOD_LSHIFT | SDLKeycode.SDL_KMOD_RSHIFT)) != 0;
        int value = event.keycode();
        if (value < 32 || value > 126) {
            return "";
        }
        char character = (char) value;
        if (character >= 'a' && character <= 'z') {
            return String.valueOf(shift ? Character.toUpperCase(character) : character);
        }
        if (character >= 'A' && character <= 'Z') {
            return String.valueOf(shift ? character : Character.toLowerCase(character));
        }
        if (character >= '0' && character <= '9') {
            if (!shift) {
                return String.valueOf(character);
            }
            return switch (character) {
                case '1' -> "!";
                case '2' -> "@";
                case '3' -> "#";
                case '4' -> "$";
                case '5' -> "%";
                case '6' -> "^";
                case '7' -> "&";
                case '8' -> "*";
                case '9' -> "(";
                case '0' -> ")";
                default -> String.valueOf(character);
            };
        }
        if (character == ' ') {
            return " ";
        }
        if (shift) {
            char shifted = switch (character) {
                case '-' -> '_';
                case '=' -> '+';
                case '[' -> '{';
                case ']' -> '}';
                case '\\' -> '|';
                case ';' -> ':';
                case '\'' -> '"';
                case ',' -> '<';
                case '.' -> '>';
                case '/' -> '?';
                case '`' -> '~';
                default -> 0;
            };
            if (shifted != 0) {
                return String.valueOf(shifted);
            }
        }
        return String.valueOf(character);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.granted) {
            ClientPacketDistributor.sendToServer(new BrowserControlPayload(this.pos, true));
        }
        super.onClose();
    }

    private static final class VolumeSlider extends AbstractSliderButton {
        private VolumeSlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, Component.empty(), value);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("boxier.browser.volume", Math.round(this.value * 100.0)));
        }

        @Override
        protected void applyValue() {
            BrowserAudioManager.setVolume((float) this.value);
        }
    }

    private static int cdpModifiers(int sdlModifiers) {
        int modifiers = 0;
        if ((sdlModifiers & (SDLKeycode.SDL_KMOD_LALT | SDLKeycode.SDL_KMOD_RALT)) != 0) {
            modifiers |= 1;
        }
        if ((sdlModifiers & (SDLKeycode.SDL_KMOD_LCTRL | SDLKeycode.SDL_KMOD_RCTRL)) != 0) {
            modifiers |= 2;
        }
        if ((sdlModifiers & (SDLKeycode.SDL_KMOD_LGUI | SDLKeycode.SDL_KMOD_RGUI)) != 0) {
            modifiers |= 4;
        }
        if ((sdlModifiers & (SDLKeycode.SDL_KMOD_LSHIFT | SDLKeycode.SDL_KMOD_RSHIFT)) != 0) {
            modifiers |= 8;
        }
        return modifiers;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
