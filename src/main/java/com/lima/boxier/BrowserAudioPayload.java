package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BrowserAudioPayload(BlockPos pos, byte[] data) implements CustomPacketPayload {
    private static final int MAX_AUDIO_BYTES = 8192;

    public static final CustomPacketPayload.Type<BrowserAudioPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Boxier.MODID, "browser_audio"));

    public static final StreamCodec<FriendlyByteBuf, BrowserAudioPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BrowserAudioPayload decode(FriendlyByteBuf buffer) {
            return new BrowserAudioPayload(buffer.readBlockPos(), buffer.readByteArray(MAX_AUDIO_BYTES));
        }

        @Override
        public void encode(FriendlyByteBuf buffer, BrowserAudioPayload payload) {
            buffer.writeBlockPos(payload.pos());
            buffer.writeByteArray(payload.data());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BrowserAudioPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BrowserClient.onAudio(payload));
    }
}
