package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BrowserCursorPayload(BlockPos pos, float x, float y) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BrowserCursorPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Boxier.MODID, "browser_cursor"));

    public static final StreamCodec<FriendlyByteBuf, BrowserCursorPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BrowserCursorPayload decode(FriendlyByteBuf buffer) {
            return new BrowserCursorPayload(buffer.readBlockPos(), buffer.readFloat(), buffer.readFloat());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, BrowserCursorPayload payload) {
            buffer.writeBlockPos(payload.pos());
            buffer.writeFloat(payload.x());
            buffer.writeFloat(payload.y());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BrowserCursorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BrowserClient.onCursor(payload));
    }
}
