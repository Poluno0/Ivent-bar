package com.example.worldborderevent;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Отрисовка энергетической стены: текстура с прокруткой, пульсация, волна по периметру, ударные кольца. */
@Mod.EventBusSubscriber(modid = WorldBorderEventMod.MODID, value = Dist.CLIENT)
public final class ClientBorderRenderer {
    private static final ResourceLocation WALL = new ResourceLocation(WorldBorderEventMod.MODID, "textures/misc/wall.png");
    private static final ResourceLocation RING = new ResourceLocation(WorldBorderEventMod.MODID, "textures/misc/ring.png");
    private static final ResourceLocation FLASH = new ResourceLocation(WorldBorderEventMod.MODID, "textures/misc/flash.png");
    private static final ResourceLocation BAND = new ResourceLocation(WorldBorderEventMod.MODID, "textures/misc/band.png");

    private static final float MIN_Y = -64f, MAX_Y = 320f;
    private static final float TILE_W = 16f, TILE_H = 32f; // блоков на одну текстуру (128x256 px)

    private static double scroll = 0, lastTime = -1;

    private ClientBorderRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ClientBorderState.hasState()) return;
        boolean nether = mc.level.dimension() == Level.NETHER;
        if (!nether && mc.level.dimension() != Level.OVERWORLD) return;

        float partial = event.getPartialTick();
        double now = mc.level.getGameTime() + partial;
        if (lastTime < 0 || now < lastTime) lastTime = now;
        double dt = Math.min(5.0, now - lastTime);
        lastTime = now;

        float t = ClientBorderState.eventTick();
        if (t >= 0) t += partial;
        boolean fin = ClientBorderState.isFinished();
        float glow = EventTimeline.glow(t, fin);
        float alphaMul = EventTimeline.alpha(t, fin);
        float speed = 0.004f + 0.02f * glow * glow;
        scroll = (scroll + speed * dt) % 1000.0;
        double pulseFreq = 0.08 + 0.18 * glow;
        float pulse = (float) (0.86 + 0.14 * Math.sin(now * pulseFreq) * (0.5 + glow));
        float a = Math.min(1f, (0.62f + 0.3f * glow) * pulse) * alphaMul;
        int tint = (int) (255 * Math.min(1f, 0.55f + 0.5f * glow));
        int alpha = (int) (255 * Math.max(0f, Math.min(1f, a)));
        if (alpha <= 2) return;

        double r = ClientBorderState.getRadius(nether, partial);
        PoseStack pose = event.getPoseStack();
        var cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();

        double view = Math.min(400, mc.options.getEffectiveRenderDistance() * 16 + 48);

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // 1) основная стена
        RenderSystem.setShaderTexture(0, WALL);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        for (int side = 0; side < 4; side++) {
            int axis = side < 2 ? 0 : 1;
            double fixed = (side % 2 == 0) ? r : -r;
            double p = axis == 0 ? cam.z : cam.x;
            double a0 = Math.max(-r, p - view), a1 = Math.min(r, p + view);
            if (a1 <= a0) continue;
            float u0 = (float) frac(a0 / TILE_W), u1 = u0 + (float) ((a1 - a0) / TILE_W);
            float vTop = (float) (-scroll % 1.0), vH = (MAX_Y - MIN_Y) / TILE_H;
            quad(bb, m, axis, fixed, a0, a1, MIN_Y, MAX_Y, u0, u1, vTop + vH, vTop, tint, tint, tint, alpha);
        }
        BufferUploader.drawWithShader(bb.end());

        // 2) аддитивные эффекты: волна по периметру, ударные кольца, вспышки
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        boolean calm = BorderConfig.REDUCE_FLASHES.get();
        float fx = calm ? 0.5f : 1f;

        if (t >= 160 && t < 260) {
            float prog = (t - 160) / 100f;
            float fade = (float) Math.sin(Math.PI * prog);
            RenderSystem.setShaderTexture(0, BAND);
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
            for (int side = 0; side < 4; side++) {
                int axis = side < 2 ? 0 : 1;
                double fixed = (side % 2 == 0) ? r : -r;
                double p = axis == 0 ? cam.z : cam.x;
                double centre = p + (prog * 2 - 1) * 380.0;
                double half = 26.0;
                double a0 = Math.max(Math.max(-r, p - view), centre - half), a1 = Math.min(Math.min(r, p + view), centre + half);
                if (a1 <= a0) continue;
                float u0 = (float) ((a0 - (centre - half)) / (2 * half)), u1 = (float) ((a1 - (centre - half)) / (2 * half));
                double off = Math.signum((axis == 0 ? cam.x : cam.z) - fixed) * 0.04;
                quad(bb, m, axis, fixed + off, a0, a1, MIN_Y, MAX_Y, u0, u1, 6f, 0f, 255, 255, 255, (int) (255 * fade * 0.8f * fx));
            }
            BufferUploader.drawWithShader(bb.end());
        }

        double gt = mc.level.getGameTime() + partial;
        drawRipples(bb, m, cam, r, gt, RING, false, fx);
        drawRipples(bb, m, cam, r, gt, FLASH, true, fx);

        // восстановить состояние GL
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void drawRipples(BufferBuilder bb, Matrix4f m, net.minecraft.world.phys.Vec3 cam, double r, double gt,
                                    ResourceLocation tex, boolean flash, float fx) {
        boolean any = false;
        for (ClientBorderState.Ripple rp : ClientBorderState.ripples()) if (rp.flash == flash) { any = true; break; }
        if (!any) return;
        RenderSystem.setShaderTexture(0, tex);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        for (ClientBorderState.Ripple rp : ClientBorderState.ripples()) {
            if (rp.flash != flash) continue;
            float age = (float) ((gt - rp.birth) / rp.life);
            if (age < 0 || age > 1) continue;
            float ease = 1f - (1f - age) * (1f - age);
            double rad = rp.maxR * (flash ? (0.5 + 0.5 * ease) : ease) + 0.2;
            float al = (float) Math.pow(1f - age, flash ? 1.2 : 1.6) * (0.5f + 0.5f * rp.strength) * fx;
            int ia = (int) (255 * Math.max(0f, Math.min(1f, al)));
            if (ia <= 1) continue;
            double fixed = rp.axis == 0 ? rp.x : rp.z;
            double along = rp.axis == 0 ? rp.z : rp.x;
            double off = Math.signum((rp.axis == 0 ? cam.x : cam.z) - fixed) * 0.05;
            if (off == 0) off = 0.05;
            quad(bb, m, rp.axis, fixed + off, along - rad, along + rad, (float) (rp.y - rad), (float) (rp.y + rad), 0f, 1f, 1f, 0f, 255, 255, 255, ia);
        }
        BufferUploader.drawWithShader(bb.end());
    }

    /** Четырёхугольник в плоскости стены: axis 0 — x=fixed (вдоль z), axis 1 — z=fixed (вдоль x). */
    private static void quad(BufferBuilder bb, Matrix4f m, int axis, double fixed, double a0, double a1, double y0, double y1,
                             float u0, float u1, float vBottom, float vTop, int r, int g, int b, int a) {
        vert(bb, m, axis, fixed, a0, y0, u0, vBottom, r, g, b, a);
        vert(bb, m, axis, fixed, a1, y0, u1, vBottom, r, g, b, a);
        vert(bb, m, axis, fixed, a1, y1, u1, vTop, r, g, b, a);
        vert(bb, m, axis, fixed, a0, y1, u0, vTop, r, g, b, a);
    }

    private static void vert(BufferBuilder bb, Matrix4f m, int axis, double fixed, double along, double y,
                             float u, float v, int r, int g, int b, int a) {
        float x = (float) (axis == 0 ? fixed : along);
        float z = (float) (axis == 0 ? along : fixed);
        bb.vertex(m, x, (float) y, z).color(r, g, b, a).uv(u, v).endVertex();
    }

    private static double frac(double v) { return v - Math.floor(v); }
}
