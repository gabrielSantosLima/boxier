package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BrowserControlResponsePayload(BlockPos pos, boolean granted, String message, String url)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BrowserControlResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Boxier.MODID, "browser_control_response"));

    public static final StreamCodec<FriendlyByteBuf, BrowserControlResponsePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BrowserControlResponsePayload decode(FriendlyByteBuf buffer) {
            return new BrowserControlResponsePayload(buffer.readBlockPos(), buffer.readBoolean(),
                    buffer.readUtf(512), buffer.readUtf(2048));
        }

        @Override
        public void encode(FriendlyByteBuf buffer, BrowserControlResponsePayload payload) {
            buffer.writeBlockPos(payload.pos());
            buffer.writeBoolean(payload.granted());
            buffer.writeUtf(payload.message(), 512);
            buffer.writeUtf(payload.url(), 2048);
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BrowserControlResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BrowserClient.onControlResponse(payload));
    }
}
