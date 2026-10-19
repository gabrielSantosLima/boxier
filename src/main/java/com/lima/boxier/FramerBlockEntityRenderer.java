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
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

public class FramerBlockEntityRenderer implements BlockEntityRenderer<FramerBlockEntity, FramerRenderState> {
    private static final float Z = 1.002F;
    private static final float ORIGIN = 3.0F / 16.0F;

    public FramerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public FramerRenderState createRenderState() {
        return new FramerRenderState();
    }

    @Override
    public void extractRenderState(FramerBlockEntity blockEntity, FramerRenderState state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.url = blockEntity.getUrl();
        state.imageWidth = blockEntity.getImageWidth();
        state.imageHeight = blockEntity.getImageHeight();
        state.yRot = blockEntity.getBlockState().getValue(FramerBlock.FACING).toYRot();
    }

    @Override
    public void submit(FramerRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        FramerImageCache.Animation animation = FramerImageCache.getAnimation(state.url);
        if (animation == null) {
            return;
        }
        Identifier texture = animation.frameAt(Util.getMillis());

        RenderType type = RenderTypes.entityCutout(texture);
        int light = state.lightCoords;

        // Anchored at the bottom-left of the frame opening, growing towards the top-right.
        float x0 = ORIGIN;
        float y0 = ORIGIN;
        float x1 = ORIGIN + state.imageWidth / 16.0F;
        float y1 = ORIGIN + state.imageHeight / 16.0F;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.rotateDegrees(Axis.YP, -state.yRot);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        quad(collector, poseStack, type, light, x0, y0, Z, x1, y0, Z, x1, y1, Z, x0, y1, Z, 0.0F, 0.0F, 1.0F);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(FramerBlockEntity blockEntity) {
        double extent = Math.max(blockEntity.getImageWidth(), blockEntity.getImageHeight()) / 16.0 + 2.0;
        return new AABB(blockEntity.getBlockPos()).inflate(extent);
    }

    private static void quad(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, int light,
            float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
            float nx, float ny, float nz) {
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            vertex(buffer, pose, ax, ay, az, 0.0F, 1.0F, light, nx, ny, nz);
            vertex(buffer, pose, bx, by, bz, 1.0F, 1.0F, light, nx, ny, nz);
            vertex(buffer, pose, cx, cy, cz, 1.0F, 0.0F, light, nx, ny, nz);
            vertex(buffer, pose, dx, dy, dz, 0.0F, 0.0F, light, nx, ny, nz);
        });
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light,
            float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
