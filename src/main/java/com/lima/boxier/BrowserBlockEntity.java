package com.lima.boxier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BrowserBlockEntity extends BlockEntity {
    public static final int DEFAULT_SIZE = 10;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 64;

    private int displayWidth = DEFAULT_SIZE;
    private int displayHeight = DEFAULT_SIZE;

    public BrowserBlockEntity(BlockPos pos, BlockState state) {
        super(Boxier.BROWSER_BLOCK_ENTITY.get(), pos, state);
    }

    public int getDisplayWidth() {
        return this.displayWidth;
    }

    public int getDisplayHeight() {
        return this.displayHeight;
    }

    public void setSize(int width, int height) {
        this.displayWidth = Math.max(MIN_SIZE, Math.min(MAX_SIZE, width));
        this.displayHeight = Math.max(MIN_SIZE, Math.min(MAX_SIZE, height));
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("display_width", this.displayWidth);
        output.putInt("display_height", this.displayHeight);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.displayWidth = Math.max(MIN_SIZE, Math.min(MAX_SIZE, input.getIntOr("display_width", DEFAULT_SIZE)));
        this.displayHeight = Math.max(MIN_SIZE, Math.min(MAX_SIZE, input.getIntOr("display_height", DEFAULT_SIZE)));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
