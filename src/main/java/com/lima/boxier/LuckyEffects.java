package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class LuckyEffects {
    private LuckyEffects() {
    }

    public static Entity spawnNear(ServerLevel level, BlockPos pos, RandomSource random, EntityType<?> type, double radius) {
        Entity entity = type.create(level, EntitySpawnReason.EVENT);
        if (entity == null) {
            return null;
        }
        int x = pos.getX() + Mth.floor((random.nextDouble() * 2.0 - 1.0) * radius);
        int z = pos.getZ() + Mth.floor((random.nextDouble() * 2.0 - 1.0) * radius);
        entity.snapTo(x + 0.5, pos.getY(), z + 0.5, random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(entity);
        return entity;
    }

    public static int spawnMany(ServerLevel level, BlockPos pos, RandomSource random, EntityType<?> type, int count, double radius) {
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (spawnNear(level, pos, random, type, radius) != null) {
                spawned++;
            }
        }
        return spawned;
    }

    public static void drop(ServerLevel level, BlockPos pos, ItemStack stack) {
        Block.popResource(level, pos, stack);
    }

    public static void place(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.isLoaded(pos)) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
    }

    public static void placeIfAir(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.isLoaded(pos) && level.getBlockState(pos).isAir()) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
    }

    public static BlockPos findGroundSpot(ServerLevel level, BlockPos center, RandomSource random, int radius) {
        for (int attempt = 0; attempt < 24; attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            BlockPos candidate = center.offset(dx, 0, dz);
            if (level.isLoaded(candidate)
                    && level.getBlockState(candidate).isAir()
                    && level.getBlockState(candidate.below()).isSolidRender()) {
                return candidate;
            }
        }
        return null;
    }
}
