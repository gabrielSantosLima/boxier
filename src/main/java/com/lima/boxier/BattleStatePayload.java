package com.lima.boxier;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BattleStatePayload(boolean active) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BattleStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Boxier.MODID, "wither_battle_state"));

    public static final StreamCodec<ByteBuf, BattleStatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BattleStatePayload decode(ByteBuf buffer) {
            return new BattleStatePayload(buffer.readBoolean());
        }

        @Override
        public void encode(ByteBuf buffer, BattleStatePayload payload) {
            buffer.writeBoolean(payload.active());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
