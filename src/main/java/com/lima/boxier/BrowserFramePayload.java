package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BrowserFramePayload(BlockPos pos, int sequence, int width, int height, byte[] data)
        implements CustomPacketPayload {
    private static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;

    public static final CustomPacketPayload.Type<BrowserFramePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Boxier.MODID, "browser_frame"));

    public static final StreamCodec<FriendlyByteBuf, BrowserFramePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BrowserFramePayload decode(FriendlyByteBuf buffer) {
            return new BrowserFramePayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readByteArray(MAX_FRAME_BYTES));
        }

        @Override
        public void encode(FriendlyByteBuf buffer, BrowserFramePayload payload) {
            buffer.writeBlockPos(payload.pos());
            buffer.writeVarInt(payload.sequence());
            buffer.writeVarInt(payload.width());
            buffer.writeVarInt(payload.height());
            buffer.writeByteArray(payload.data());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BrowserFramePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BrowserClient.onFrame(payload));
    }
}
