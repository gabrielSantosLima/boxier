package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class LuckyBlock extends Block {
    public LuckyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockState result = super.playerWillDestroy(level, pos, state, player);

        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            RandomSource random = level.getRandom();
            LuckyEvent event = LuckyEvents.roll(random);
            event.action().run(serverLevel, serverPlayer, pos, random);
            serverPlayer.sendSystemMessage(Component.translatable("boxier.lucky." + event.id()));
        }

        return result;
    }
}
