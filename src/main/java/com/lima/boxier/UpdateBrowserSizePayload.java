package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateBrowserSizePayload(BlockPos pos, int width, int height) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdateBrowserSizePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Boxier.MODID, "update_browser_size"));

    public static final StreamCodec<FriendlyByteBuf, UpdateBrowserSizePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public UpdateBrowserSizePayload decode(FriendlyByteBuf buffer) {
            return new UpdateBrowserSizePayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, UpdateBrowserSizePayload payload) {
            buffer.writeBlockPos(payload.pos());
            buffer.writeVarInt(payload.width());
            buffer.writeVarInt(payload.height());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateBrowserSizePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().level() instanceof ServerLevel level)) {
                return;
            }
            if (level.getBlockEntity(payload.pos()) instanceof BrowserBlockEntity browser) {
                browser.setSize(payload.width(), payload.height());
                BrowserSessionManager.resize(level, payload.pos(), browser.getDisplayWidth(), browser.getDisplayHeight());
            }
        });
    }
}
