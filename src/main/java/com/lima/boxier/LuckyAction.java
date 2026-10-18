package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

@FunctionalInterface
public interface LuckyAction {
    void run(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random);
}
