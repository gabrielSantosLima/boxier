package com.lima.boxier;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class FramerRenderState extends BlockEntityRenderState {
    public String url = "";
    public int imageWidth = FramerBlockEntity.DEFAULT_SIZE;
    public int imageHeight = FramerBlockEntity.DEFAULT_SIZE;
    public float yRot;
}
