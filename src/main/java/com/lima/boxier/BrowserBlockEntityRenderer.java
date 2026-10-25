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
    private static final float SPHERE_RADIUS = 8.0F;
    private static final int SPHERE_LATITUDES = 24;
    private static final int SPHERE_LONGITUDES = 48;
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
        state.projection360 = blockEntity.isProjection360();

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

        if (state.projection360) {
            sphere(collector, poseStack, textureType, light);
            return;
        }

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
        if (blockEntity.isProjection360()) {
            return new AABB(blockEntity.getBlockPos()).inflate(SPHERE_RADIUS + 2.0);
        }
        double extent = Math.max(blockEntity.getDisplayWidth(), blockEntity.getDisplayHeight()) + 2.0;
        return new AABB(blockEntity.getBlockPos()).inflate(extent);
    }

    private static void sphere(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, int light) {
        float centerX = 0.5F;
        float centerY = 0.5F;
        float centerZ = 0.5F;

        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            for (int latitude = 0; latitude < SPHERE_LATITUDES; latitude++) {
                double lat0 = Math.PI * latitude / SPHERE_LATITUDES;
                double lat1 = Math.PI * (latitude + 1) / SPHERE_LATITUDES;
                for (int longitude = 0; longitude < SPHERE_LONGITUDES; longitude++) {
                    double lon0 = Math.PI * 2.0 * longitude / SPHERE_LONGITUDES;
                    double lon1 = Math.PI * 2.0 * (longitude + 1) / SPHERE_LONGITUDES;

                    float x00 = centerX + (float) (Math.sin(lat0) * Math.cos(lon0)) * SPHERE_RADIUS;
                    float y00 = centerY + (float) Math.cos(lat0) * SPHERE_RADIUS;
                    float z00 = centerZ + (float) (Math.sin(lat0) * Math.sin(lon0)) * SPHERE_RADIUS;
                    float x01 = centerX + (float) (Math.sin(lat0) * Math.cos(lon1)) * SPHERE_RADIUS;
                    float y01 = centerY + (float) Math.cos(lat0) * SPHERE_RADIUS;
                    float z01 = centerZ + (float) (Math.sin(lat0) * Math.sin(lon1)) * SPHERE_RADIUS;
                    float x11 = centerX + (float) (Math.sin(lat1) * Math.cos(lon1)) * SPHERE_RADIUS;
                    float y11 = centerY + (float) Math.cos(lat1) * SPHERE_RADIUS;
                    float z11 = centerZ + (float) (Math.sin(lat1) * Math.sin(lon1)) * SPHERE_RADIUS;
                    float x10 = centerX + (float) (Math.sin(lat1) * Math.cos(lon0)) * SPHERE_RADIUS;
                    float y10 = centerY + (float) Math.cos(lat1) * SPHERE_RADIUS;
                    float z10 = centerZ + (float) (Math.sin(lat1) * Math.sin(lon0)) * SPHERE_RADIUS;

                    float u0 = (float) longitude / SPHERE_LONGITUDES;
                    float u1 = (float) (longitude + 1) / SPHERE_LONGITUDES;
                    float v0 = (float) latitude / SPHERE_LATITUDES;
                    float v1 = (float) (latitude + 1) / SPHERE_LATITUDES;

                    vertex(buffer, pose, x00, y00, z00, u0, v0, light,
                            -(float) (Math.sin(lat0) * Math.cos(lon0)), -(float) Math.cos(lat0),
                            -(float) (Math.sin(lat0) * Math.sin(lon0)));
                    vertex(buffer, pose, x01, y01, z01, u1, v0, light,
                            -(float) (Math.sin(lat0) * Math.cos(lon1)), -(float) Math.cos(lat0),
                            -(float) (Math.sin(lat0) * Math.sin(lon1)));
                    vertex(buffer, pose, x11, y11, z11, u1, v1, light,
                            -(float) (Math.sin(lat1) * Math.cos(lon1)), -(float) Math.cos(lat1),
                            -(float) (Math.sin(lat1) * Math.sin(lon1)));
                    vertex(buffer, pose, x10, y10, z10, u0, v1, light,
                            -(float) (Math.sin(lat1) * Math.cos(lon0)), -(float) Math.cos(lat1),
                            -(float) (Math.sin(lat1) * Math.sin(lon0)));
                }
            }
        });
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
