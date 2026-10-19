package com.lima.boxier;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitherBossRenderer;
import net.minecraft.client.renderer.entity.state.WitherRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public class EpicWitherRenderer extends WitherBossRenderer {
    private static final Identifier CHARGED_TEXTURE = Identifier.fromNamespaceAndPath("boxier", "textures/entity/wither/wither_charged.png");
    private static final float CHARGE_TICKS = 220.0F;
    private static final float HALF_SQRT_3 = (float) (Math.sqrt(3.0) / 2.0);
    private static final int RAY_COLOR = 0xFF00FF;

    public EpicWitherRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(WitherRenderState state) {
        return state.invulnerableTicks > 0.0F ? CHARGED_TEXTURE : super.getTextureLocation(state);
    }

    @Override
    public void submit(WitherRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        super.submit(state, poseStack, submitNodeCollector, camera);

        if (state.invulnerableTicks <= 0.0F) {
            return;
        }

        float charge = 1.0F - Mth.clamp(state.invulnerableTicks / CHARGE_TICKS, 0.0F, 1.0F);
        poseStack.pushPose();
        poseStack.translate(0.0F, 1.5F, 0.0F);
        submitRays(poseStack, charge, submitNodeCollector);
        poseStack.popPose();
    }

    private static void submitRays(PoseStack poseStack, float charge, SubmitNodeCollector submitNodeCollector) {
        submitNodeCollector.submitCustomGeometry(
            poseStack,
            RenderTypes.dragonRays(),
            (pose, buffer) -> {
                float overDrive = Math.min(charge > 0.8F ? (charge - 0.8F) / 0.2F : 0.0F, 1.0F);
                int innerColor = ARGB.colorFromFloat(1.0F - overDrive, 1.0F, 1.0F, 1.0F);
                RandomSource random = RandomSource.createThreadLocalInstance(432L);
                Vector3f origin = new Vector3f();
                Vector3f outerLeft = new Vector3f();
                Vector3f outerRight = new Vector3f();
                Vector3f outerBottom = new Vector3f();
                Quaternionf rayRotation = new Quaternionf();
                int rayCount = Mth.floor((charge + charge * charge) / 2.0F * 60.0F);

                for (int i = 0; i < rayCount; i++) {
                    rayRotation.rotationXYZ(
                            random.nextFloat() * (float) (Math.PI * 2),
                            random.nextFloat() * (float) (Math.PI * 2),
                            random.nextFloat() * (float) (Math.PI * 2)
                        )
                        .rotateXYZ(
                            random.nextFloat() * (float) (Math.PI * 2),
                            random.nextFloat() * (float) (Math.PI * 2),
                            random.nextFloat() * (float) (Math.PI * 2) + charge * (float) (Math.PI / 2)
                        );
                    pose.rotate(rayRotation);
                    float length = random.nextFloat() * 20.0F + 5.0F + overDrive * 10.0F;
                    float width = random.nextFloat() * 2.0F + 1.0F + overDrive * 2.0F;
                    outerLeft.set(-HALF_SQRT_3 * width, length, -0.5F * width);
                    outerRight.set(HALF_SQRT_3 * width, length, -0.5F * width);
                    outerBottom.set(0.0F, length, width);
                    buffer.addVertex(pose, origin).setColor(innerColor);
                    buffer.addVertex(pose, outerLeft).setColor(RAY_COLOR);
                    buffer.addVertex(pose, outerRight).setColor(RAY_COLOR);
                    buffer.addVertex(pose, origin).setColor(innerColor);
                    buffer.addVertex(pose, outerRight).setColor(RAY_COLOR);
                    buffer.addVertex(pose, outerBottom).setColor(RAY_COLOR);
                    buffer.addVertex(pose, origin).setColor(innerColor);
                    buffer.addVertex(pose, outerBottom).setColor(RAY_COLOR);
                    buffer.addVertex(pose, outerLeft).setColor(RAY_COLOR);
                }
            }
        );
    }
}
