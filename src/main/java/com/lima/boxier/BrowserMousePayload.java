package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BrowserMousePayload(BlockPos pos, int action, float x, float y, int button, int modifiers, float scroll)
        implements CustomPacketPayload {
    public static final int MOVE = 0;
    public static final int DOWN = 1;
    public static final int UP = 2;
    public static final int SCROLL = 3;

    public static final CustomPacketPayload.Type<BrowserMousePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Boxier.MODID, "browser_mouse"));

    public static final StreamCodec<FriendlyByteBuf, BrowserMousePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BrowserMousePayload decode(FriendlyByteBuf buffer) {
            return new BrowserMousePayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, BrowserMousePayload payload) {
            buffer.writeBlockPos(payload.pos());
            buffer.writeVarInt(payload.action());
            buffer.writeFloat(payload.x());
            buffer.writeFloat(payload.y());
            buffer.writeVarInt(payload.button());
            buffer.writeVarInt(payload.modifiers());
            buffer.writeFloat(payload.scroll());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BrowserMousePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BrowserSessionManager.mouse(player, payload);
            }
        });
    }
}
