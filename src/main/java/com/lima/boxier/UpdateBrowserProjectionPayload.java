package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateBrowserProjectionPayload(BlockPos pos, boolean projection360) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdateBrowserProjectionPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Boxier.MODID, "update_browser_projection"));

    public static final StreamCodec<FriendlyByteBuf, UpdateBrowserProjectionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public UpdateBrowserProjectionPayload decode(FriendlyByteBuf buffer) {
            return new UpdateBrowserProjectionPayload(buffer.readBlockPos(), buffer.readBoolean());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, UpdateBrowserProjectionPayload payload) {
            buffer.writeBlockPos(payload.pos());
            buffer.writeBoolean(payload.projection360());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateBrowserProjectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            BrowserSessionManager.setProjection(level, payload.pos(), player, payload.projection360());
        });
    }
}
