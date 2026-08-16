package dev.minse.interiorveil.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

public class VeilForcefieldRenderer {
    private static volatile dev.minse.interiorveil.network.ForcefieldStatePayload currentState;

    public static void accept(dev.minse.interiorveil.network.ForcefieldStatePayload payload) {
        currentState = payload;
    }

    public static dev.minse.interiorveil.network.ForcefieldStatePayload getCurrentState() {
        return currentState;
    }

    public static void clear() {
        currentState = null;
    }

    public static void render(WorldRenderContext context) {
        dev.minse.interiorveil.network.ForcefieldStatePayload state = currentState;
        if (state == null || !state.active()) {
            return;
        }
        
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.gameRenderer == null) {
            return;
        }

        PoseStack.Pose pose = context.matrices().last();
        VertexConsumer consumer = context.consumers().getBuffer(RenderType.lightning());
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();

        double radius = state.radius();
        double centerY = state.centerY();
        
        int boundaryAlpha = Math.max(40, Math.min(130, (int) (state.density() * 0.5f)));

        int color = state.color();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        drawEllipsoid(consumer, pose, camera, radius, centerY, state.centerX(), state.centerZ(), r, g, b, boundaryAlpha);

        if (context.consumers() instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch(RenderType.lightning());
        }
    }

    private static void drawEllipsoid(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, double radius, double centerY, double centerX, double centerZ, int r, int g, int b, int a) {
        int latitudeBands = Math.min(64, Math.max(32, (int) (radius * 1.6)));
        int longitudeBands = Math.min(64, Math.max(32, (int) (radius * 1.6)));

        double radiusY = radius;

        if (radius <= 0) radius = 1.0;

        for (int latNumber = 0; latNumber < latitudeBands; latNumber++) {
            float theta1 = (float) (latNumber * Math.PI / latitudeBands);
            float theta2 = (float) ((latNumber + 1) * Math.PI / latitudeBands);

            float sinTheta1 = (float) Math.sin(theta1);
            float cosTheta1 = (float) Math.cos(theta1);
            float sinTheta2 = (float) Math.sin(theta2);
            float cosTheta2 = (float) Math.cos(theta2);

            for (int longNumber = 0; longNumber < longitudeBands; longNumber++) {
                float phi1 = (float) (longNumber * 2 * Math.PI / longitudeBands);
                float phi2 = (float) ((longNumber + 1) * 2 * Math.PI / longitudeBands);

                float sinPhi1 = (float) Math.sin(phi1);
                float cosPhi1 = (float) Math.cos(phi1);
                float sinPhi2 = (float) Math.sin(phi2);
                float cosPhi2 = (float) Math.cos(phi2);

                float x1 = (float) (centerX + radius * cosPhi1 * sinTheta1 - camera.x);
                float y1 = (float) (centerY + radiusY * cosTheta1 - camera.y);
                float z1 = (float) (centerZ + radius * sinPhi1 * sinTheta1 - camera.z);

                float x2 = (float) (centerX + radius * cosPhi1 * sinTheta2 - camera.x);
                float y2 = (float) (centerY + radiusY * cosTheta2 - camera.y);
                float z2 = (float) (centerZ + radius * sinPhi1 * sinTheta2 - camera.z);

                float x3 = (float) (centerX + radius * cosPhi2 * sinTheta2 - camera.x);
                float y3 = (float) (centerY + radiusY * cosTheta2 - camera.y);
                float z3 = (float) (centerZ + radius * sinPhi2 * sinTheta2 - camera.z);

                float x4 = (float) (centerX + radius * cosPhi2 * sinTheta1 - camera.x);
                float y4 = (float) (centerY + radiusY * cosTheta1 - camera.y);
                float z4 = (float) (centerZ + radius * sinPhi2 * sinTheta1 - camera.z);

                // 단일 쿼드 렌더링 (Z-fighting 중복 노이즈 방지)
                addVertex(consumer, pose, x1, y1, z1, r, g, b, a);
                addVertex(consumer, pose, x2, y2, z2, r, g, b, a);
                addVertex(consumer, pose, x3, y3, z3, r, g, b, a);
                addVertex(consumer, pose, x4, y4, z4, r, g, b, a);
            }
        }
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, int r, int g, int b, int a) {
        consumer.addVertex(pose, x, y, z)
                .setColor(r, g, b, a);
    }
}
