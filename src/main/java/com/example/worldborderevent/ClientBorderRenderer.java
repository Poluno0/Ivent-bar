package com.example.worldborderevent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Draws four world-aligned textured wall planes. No camera-facing billboard or diagonal geometry. */
@Mod.EventBusSubscriber(modid = WorldBorderEventMod.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ClientBorderRenderer {
    private static final int MIN_Y = -64, MAX_Y = 320;
    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/misc/forcefield.png");
    private ClientBorderRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Level level = mc.level;
        boolean nether = level.dimension() == Level.NETHER;
        if (!nether && level.dimension() != Level.OVERWORLD) return;

        double r = Math.max(1, ClientBorderState.getRadius(nether));
        double phase = (mc.level.getGameTime() + event.getPartialTick()) * 0.015;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        var cam = event.getCamera().getPosition();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer v = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        float alpha = ClientBorderState.isEventActive() ? 0.86f : 0.66f;
        float pulse = (float)(0.88 + 0.12 * Math.sin(phase * 2));
        int blue = (int)(205 * pulse), green = (int)(235 * pulse);
        // Four vertical planes. UV scroll gives the forcefield a slow upward flow.
        wall(v,m,-r,-r,r,-r,phase,alpha,green,blue);
        wall(v,m,r,-r,r,r,phase,alpha,green,blue);
        wall(v,m,r,r,-r,r,phase,alpha,green,blue);
        wall(v,m,-r,r,-r,-r,phase,alpha,green,blue);
        buffers.endBatch(RenderType.entityTranslucent(TEXTURE));
        pose.popPose();
    }

    private static void wall(VertexConsumer v, Matrix4f m,double x1,double z1,double x2,double z2,double phase,float a,int g,int b) {
        float u0=0,u1=(float)(Math.abs(x2-x1)+Math.abs(z2-z1))/16f;
        float v0=(float)(MIN_Y/16.0+phase),v1=(float)(MAX_Y/16.0+phase);
        quad(v,m,x1,MIN_Y,z1,u0,v0,x2,MIN_Y,z2,u1,v0,x2,MAX_Y,z2,u1,v1,x1,MAX_Y,z1,u0,v1,a,g,b);
    }
    private static void quad(VertexConsumer v,Matrix4f m,double x0,double y0,double z0,float u0,float v0,double x1,double y1,double z1,float u1,float v1,double x2,double y2,double z2,float u2,float v2,double x3,double y3,double z3,float u3,float v3,float a,int g,int b){
        put(v,m,x0,y0,z0,u0,v0,a,g,b);put(v,m,x1,y1,z1,u1,v1,a,g,b);put(v,m,x2,y2,z2,u2,v2,a,g,b);put(v,m,x3,y3,z3,u3,v3,a,g,b);
    }
    private static void put(VertexConsumer v,Matrix4f m,double x,double y,double z,float u,float t,float a,int g,int b){
        v.vertex(m,(float)x,(float)y,(float)z).uv(u,t).color(60,g,b,(int)(a*255)).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0,0,1).endVertex();
    }
}
