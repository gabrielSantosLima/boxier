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

public class FramerBlockEntity extends BlockEntity {
    public static final int DEFAULT_SIZE = 10;
    public static final int MIN_SIZE = 1;

    private String url = "";
    private int imageWidth = DEFAULT_SIZE;
    private int imageHeight = DEFAULT_SIZE;

    public FramerBlockEntity(BlockPos pos, BlockState state) {
        super(Boxier.FRAMER_BLOCK_ENTITY.get(), pos, state);
    }

    public String getUrl() {
        return this.url;
    }

    public int getImageWidth() {
        return this.imageWidth;
    }

    public int getImageHeight() {
        return this.imageHeight;
    }

    public void setImage(String url, int width, int height) {
        this.url = url == null ? "" : url.trim();
        this.imageWidth = Math.max(MIN_SIZE, width);
        this.imageHeight = Math.max(MIN_SIZE, height);
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("url", this.url);
        output.putInt("width", this.imageWidth);
        output.putInt("height", this.imageHeight);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.url = input.getStringOr("url", "");
        this.imageWidth = Math.max(MIN_SIZE, input.getIntOr("width", DEFAULT_SIZE));
        this.imageHeight = Math.max(MIN_SIZE, input.getIntOr("height", DEFAULT_SIZE));
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
