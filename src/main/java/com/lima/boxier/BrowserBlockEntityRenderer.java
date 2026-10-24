package com.lima.boxier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

public class BrowserBlockEntityRenderer implements BlockEntityRenderer<BrowserBlockEntity, BrowserRenderState> {
    private static final float Z = 1.002F;
    private static final float CURSOR_SIZE = 0.25F;
    private static final Identifier CURSOR_TEXTURE = Identifier.fromNamespaceAndPath(Boxier.MODID, "gui/browser_cursor");

    public BrowserBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public BrowserRenderState createRenderState() {
        return new BrowserRenderState();
    }

    @Override
    public void extractRenderState(BrowserBlockEntity blockEntity, BrowserRenderState state, float partialTicks,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.displayWidth = blockEntity.getDisplayWidth();
        state.displayHeight = blockEntity.getDisplayHeight();
        state.yRot = blockEntity.getBlockState().getValue(BrowserBlock.FACING).toYRot();

        BrowserTextureCache.Entry entry = BrowserTextureCache.get(blockEntity.getLevel(), blockEntity.getBlockPos());
        state.texture = entry == null ? null : entry.id();

        BrowserClientState.Cursor cursor = BrowserClientState.getCursor(blockEntity.getLevel(), blockEntity.getBlockPos());
        state.hasCursor = cursor != null;
        if (cursor != null) {
            state.cursorX = cursor.x();
            state.cursorY = cursor.y();
        }
    }

    @Override
    public void submit(BrowserRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (state.texture == null) {
            return;
        }

        int light = state.lightCoords;
        RenderType textureType = RenderTypes.entityCutout(state.texture);

        float x1 = state.displayWidth;
        float y1 = state.displayHeight;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.rotateDegrees(Axis.YP, -state.yRot);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        quad(collector, poseStack, textureType, light, 0.0F, 0.0F, Z, x1, 0.0F, Z, x1, y1, Z, 0.0F, y1, Z,
                0.0F, 0.0F, 1.0F);

        if (state.hasCursor) {
            float cursorX = state.cursorX * x1;
            float cursorY = (1.0F - state.cursorY) * y1;
            float half = CURSOR_SIZE / 2.0F;
            RenderType cursorType = RenderTypes.entityCutout(CURSOR_TEXTURE);
            quad(collector, poseStack, cursorType, light,
                    cursorX - half, cursorY - half, Z + 0.001F,
                    cursorX + half, cursorY - half, Z + 0.001F,
                    cursorX + half, cursorY + half, Z + 0.001F,
                    cursorX - half, cursorY + half, Z + 0.001F,
                    0.0F, 0.0F, 1.0F);
        }
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(BrowserBlockEntity blockEntity) {
        double extent = Math.max(blockEntity.getDisplayWidth(), blockEntity.getDisplayHeight()) + 2.0;
        return new AABB(blockEntity.getBlockPos()).inflate(extent);
    }

    private static void quad(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, int light,
            float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx,
            float dy, float dz, float nx, float ny, float nz) {
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            vertex(buffer, pose, ax, ay, az, 0.0F, 1.0F, light, nx, ny, nz);
            vertex(buffer, pose, bx, by, bz, 1.0F, 1.0F, light, nx, ny, nz);
            vertex(buffer, pose, cx, cy, cz, 1.0F, 0.0F, light, nx, ny, nz);
            vertex(buffer, pose, dx, dy, dz, 0.0F, 0.0F, light, nx, ny, nz);
        });
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v,
            int light, float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
