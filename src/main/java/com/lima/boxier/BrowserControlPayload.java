package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BrowserControlPayload(BlockPos pos, boolean release) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BrowserControlPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Boxier.MODID, "browser_control"));

    public static final StreamCodec<FriendlyByteBuf, BrowserControlPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BrowserControlPayload decode(FriendlyByteBuf buffer) {
            return new BrowserControlPayload(buffer.readBlockPos(), buffer.readBoolean());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, BrowserControlPayload payload) {
            buffer.writeBlockPos(payload.pos());
            buffer.writeBoolean(payload.release());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BrowserControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (payload.release()) {
                BrowserSessionManager.release(level, payload.pos(), player);
            } else {
                BrowserSessionManager.requestControl(level, payload.pos(), player);
            }
        });
    }
}
