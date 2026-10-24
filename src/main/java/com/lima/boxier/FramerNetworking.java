package com.lima.boxier;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Boxier.MODID)
public final class FramerNetworking {
    private FramerNetworking() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(UpdateFramerPayload.TYPE, UpdateFramerPayload.STREAM_CODEC, UpdateFramerPayload::handle);

        registrar.playToServer(UpdateBrowserSizePayload.TYPE, UpdateBrowserSizePayload.STREAM_CODEC,
                UpdateBrowserSizePayload::handle);
        registrar.playToServer(BrowserControlPayload.TYPE, BrowserControlPayload.STREAM_CODEC,
                BrowserControlPayload::handle);
        registrar.playToServer(BrowserMousePayload.TYPE, BrowserMousePayload.STREAM_CODEC, BrowserMousePayload::handle);
        registrar.playToServer(BrowserKeyPayload.TYPE, BrowserKeyPayload.STREAM_CODEC, BrowserKeyPayload::handle);
        registrar.playToServer(BrowserActionPayload.TYPE, BrowserActionPayload.STREAM_CODEC,
                BrowserActionPayload::handle);

        registrar.playToClient(BrowserControlResponsePayload.TYPE, BrowserControlResponsePayload.STREAM_CODEC,
                BrowserControlResponsePayload::handle);
        registrar.playToClient(BrowserFramePayload.TYPE, BrowserFramePayload.STREAM_CODEC, BrowserFramePayload::handle);
        registrar.playToClient(BrowserCursorPayload.TYPE, BrowserCursorPayload.STREAM_CODEC,
                BrowserCursorPayload::handle);
        registrar.playToClient(BrowserAudioPayload.TYPE, BrowserAudioPayload.STREAM_CODEC, BrowserAudioPayload::handle);
    }
}
