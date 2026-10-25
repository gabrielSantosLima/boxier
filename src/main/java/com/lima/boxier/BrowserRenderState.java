package com.lima.boxier;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;

public class BrowserRenderState extends BlockEntityRenderState {
    public Identifier texture;
    public int displayWidth = BrowserBlockEntity.DEFAULT_SIZE;
    public int displayHeight = BrowserBlockEntity.DEFAULT_SIZE;
    public float yRot;
    public boolean projection360;
    public boolean hasCursor;
    public float cursorX;
    public float cursorY;
}
