package com.lima.boxier;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Boxier.MODID)
public final class FramerNetworking {
    private FramerNetworking() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(UpdateFramerPayload.TYPE, UpdateFramerPayload.STREAM_CODEC, UpdateFramerPayload::handle);
    }
}
