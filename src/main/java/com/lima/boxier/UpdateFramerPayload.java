package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateFramerPayload(BlockPos pos, String url, int width, int height) implements CustomPacketPayload {
    public static final int MAX_URL_LENGTH = 2048;

    public static final CustomPacketPayload.Type<UpdateFramerPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Boxier.MODID, "update_framer"));

    public static final StreamCodec<FriendlyByteBuf, UpdateFramerPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public UpdateFramerPayload decode(FriendlyByteBuf buffer) {
            return new UpdateFramerPayload(buffer.readBlockPos(), buffer.readUtf(MAX_URL_LENGTH), buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, UpdateFramerPayload payload) {
            buffer.writeBlockPos(payload.pos());
            buffer.writeUtf(payload.url(), MAX_URL_LENGTH);
            buffer.writeVarInt(payload.width());
            buffer.writeVarInt(payload.height());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateFramerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getBlockEntity(payload.pos()) instanceof FramerBlockEntity framer) {
                framer.setImage(payload.url(), payload.width(), payload.height());
            }
        });
    }
}
