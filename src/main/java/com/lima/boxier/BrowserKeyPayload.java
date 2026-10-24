package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BrowserKeyPayload(BlockPos pos, int action, String key, String code, int keyCode, String text,
        int modifiers) implements CustomPacketPayload {
    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int TEXT = 2;

    public static final CustomPacketPayload.Type<BrowserKeyPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Boxier.MODID, "browser_key"));

    public static final StreamCodec<FriendlyByteBuf, BrowserKeyPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BrowserKeyPayload decode(FriendlyByteBuf buffer) {
            return new BrowserKeyPayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readUtf(32),
                    buffer.readUtf(32), buffer.readVarInt(), buffer.readUtf(16), buffer.readVarInt());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, BrowserKeyPayload payload) {
            buffer.writeBlockPos(payload.pos());
            buffer.writeVarInt(payload.action());
            buffer.writeUtf(payload.key(), 32);
            buffer.writeUtf(payload.code(), 32);
            buffer.writeVarInt(payload.keyCode());
            buffer.writeUtf(payload.text(), 16);
            buffer.writeVarInt(payload.modifiers());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BrowserKeyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BrowserSessionManager.key(player, payload);
            }
        });
    }
}
