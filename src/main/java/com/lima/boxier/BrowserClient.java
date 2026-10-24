package com.lima.boxier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Boxier.MODID, value = Dist.CLIENT)
public final class BrowserClient {
    private BrowserClient() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BrowserBlock)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (event.getEntity().isShiftKeyDown()) {
            if (event.getLevel().getBlockEntity(event.getPos()) instanceof BrowserBlockEntity browser) {
                minecraft.gui.setScreen(new BrowserSizeScreen(event.getPos(), browser.getDisplayWidth(),
                        browser.getDisplayHeight()));
            }
            return;
        }

        minecraft.gui.setScreen(new BrowserScreen(event.getPos()));
    }

    public static void onControlResponse(BrowserControlResponsePayload payload) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen instanceof BrowserScreen browserScreen && browserScreen.matches(payload.pos())) {
            browserScreen.handleControlResponse(payload);
        }
    }

    public static void onFrame(BrowserFramePayload payload) {
        if (Minecraft.getInstance().level != null) {
            BrowserTextureCache.update(Minecraft.getInstance().level, payload.pos(), payload.data());
        }
    }

    public static void onCursor(BrowserCursorPayload payload) {
        if (Minecraft.getInstance().level != null) {
            BrowserClientState.setCursor(Minecraft.getInstance().level, payload.pos(), payload.x(), payload.y());
        }
    }

    public static void onAudio(BrowserAudioPayload payload) {
        if (Minecraft.getInstance().level != null) {
            BrowserAudioManager.play(Minecraft.getInstance().level, payload.pos(), payload.data());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BrowserTextureCache.clear();
        BrowserClientState.clear();
        BrowserAudioManager.clear();
    }
}
