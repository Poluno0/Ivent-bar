package com.example.worldborderevent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = WorldBorderEventMod.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ClientBorderRenderer {
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private ClientBorderRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Level level = mc.level;
        boolean nether = level.dimension() == Level.NETHER;
        if (!nether && level.dimension() != Level.OVERWORLD) return;

        double radius = ClientBorderState.getRadius(nether);
        var camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = pose.last().pose();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        // Only draw the four vertical corner edges and the top/bottom perimeter.
        // The previous renderer drew dozens of full horizontal square rings,
        // which caused a screen-wide diagonal lattice artifact.
        vertical(lines, matrix, -radius, -radius);
        vertical(lines, matrix, -radius, radius);
        vertical(lines, matrix, radius, -radius);
        vertical(lines, matrix, radius, radius);
        perimeter(lines, matrix, radius, MIN_Y);
        perimeter(lines, matrix, radius, MAX_Y);

        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static void vertical(VertexConsumer v, Matrix4f m, double x, double z) {
        line(v, m, x, MIN_Y, z, x, MAX_Y, z);
    }

    private static void perimeter(VertexConsumer v, Matrix4f m, double r, double y) {
        line(v, m, -r, y, -r, r, y, -r);
        line(v, m, r, y, -r, r, y, r);
        line(v, m, r, y, r, -r, y, r);
        line(v, m, -r, y, r, -r, y, -r);
    }

    private static void line(VertexConsumer v, Matrix4f m,
                           double x, double y, double z,
                           double X, double Y, double Z) {
        v.vertex(m, (float)x, (float)y, (float)z).color(35, 220, 255, 210).normal(0, 1, 0).endVertex();
        v.vertex(m, (float)X, (float)Y, (float)Z).color(35, 220, 255, 210).normal(0, 1, 0).endVertex();
    }
}
