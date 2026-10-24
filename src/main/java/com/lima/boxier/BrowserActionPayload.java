package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BrowserActionPayload(BlockPos pos, int action, String url) implements CustomPacketPayload {
    public static final int NAVIGATE = 0;
    public static final int BACK = 1;
    public static final int FORWARD = 2;
    public static final int RELOAD = 3;

    public static final CustomPacketPayload.Type<BrowserActionPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Boxier.MODID, "browser_action"));

    public static final StreamCodec<FriendlyByteBuf, BrowserActionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BrowserActionPayload decode(FriendlyByteBuf buffer) {
            return new BrowserActionPayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readUtf(2048));
        }

        @Override
        public void encode(FriendlyByteBuf buffer, BrowserActionPayload payload) {
            buffer.writeBlockPos(payload.pos());
            buffer.writeVarInt(payload.action());
            buffer.writeUtf(payload.url(), 2048);
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BrowserActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BrowserSessionManager.action(player, payload);
            }
        });
    }
}
