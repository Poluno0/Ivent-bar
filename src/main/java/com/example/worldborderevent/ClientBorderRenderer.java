package com.example.worldborderevent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** A simple color-only wall renderer, avoiding textured entity vertex formats. */
@Mod.EventBusSubscriber(modid = WorldBorderEventMod.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ClientBorderRenderer {
    private static final int MIN_Y = -64, MAX_Y = 320;
    private ClientBorderRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Level level = mc.level;
        boolean nether = level.dimension() == Level.NETHER;
        if (!nether && level.dimension() != Level.OVERWORLD) return;

        double radius = Math.max(1, ClientBorderState.getRadius(nether));
        double phase = (mc.level.getGameTime() + event.getPartialTick()) * 0.015;
        float pulse = (float)(0.88 + 0.12 * Math.sin(phase * 2));
        int alpha = ClientBorderState.isEventActive() ? 220 : 168;
        int red = 45, green = (int)(205 * pulse), blue = (int)(235 * pulse);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        var camera = event.getCamera().getPosition();
        pose.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = pose.last().pose();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.debugQuads();
        VertexConsumer consumer = buffers.getBuffer(type);
        wall(consumer, matrix, -radius, -radius, radius, -radius, red, green, blue, alpha);
        wall(consumer, matrix, radius, -radius, radius, radius, red, green, blue, alpha);
        wall(consumer, matrix, radius, radius, -radius, radius, red, green, blue, alpha);
        wall(consumer, matrix, -radius, radius, -radius, -radius, red, green, blue, alpha);
        buffers.endBatch(type);
        pose.popPose();
    }

    private static void wall(VertexConsumer v, Matrix4f m, double x1, double z1, double x2, double z2,
                            int r, int g, int b, int a) {
        vertex(v, m, x1, MIN_Y, z1, r, g, b, a);
        vertex(v, m, x2, MIN_Y, z2, r, g, b, a);
        vertex(v, m, x2, MAX_Y, z2, r, g, b, a);
        vertex(v, m, x1, MAX_Y, z1, r, g, b, a);
    }

    private static void vertex(VertexConsumer v, Matrix4f m, double x, double y, double z,
                              int r, int g, int b, int a) {
        v.vertex(m, (float)x, (float)y, (float)z).color(r, g, b, a).endVertex();
    }
}
