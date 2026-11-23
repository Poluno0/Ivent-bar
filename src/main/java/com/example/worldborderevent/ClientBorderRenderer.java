package com.example.worldborderevent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Отрисовка стены. Основа — ванильная текстура границы мира (forcefield), окрашенная в бирюзовый.
 * Используются штатные RenderType (они корректно работают с шейдерами Oculus/Iris), цвет задаётся вершинами.
 */
@Mod.EventBusSubscriber(modid = WorldBorderEventMod.MODID, value = Dist.CLIENT)
public final class ClientBorderRenderer {
    private static final ResourceLocation WALL = new ResourceLocation("minecraft", "textures/misc/forcefield.png");
    private static final ResourceLocation RING = new ResourceLocation(WorldBorderEventMod.MODID, "textures/misc/ring.png");
    private static final ResourceLocation FLASH = new ResourceLocation(WorldBorderEventMod.MODID, "textures/misc/flash.png");
    private static final ResourceLocation BAND = new ResourceLocation(WorldBorderEventMod.MODID, "textures/misc/band.png");

    private static final float MIN_Y = -64f, MAX_Y = 320f;
    private static final float FADE_HEIGHT = 50f;   // плавное исчезновение стены у верхней границы
    private static final float TILE = 2f;           // блоков на один повтор текстуры (как у ванильной границы)
    private static final int LIGHT = 15728880;

    private static double scroll = 0, lastTime = -1;
    static volatile String lastInfo = "(не рисовалась)";
    static volatile boolean hideWall = false;
    static volatile boolean trace = false;
    private static long lastTrace = 0;

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
        float vis = EventTimeline.alpha(t, fin);
        if (vis <= 0.01f) return; // стена полностью исчезла
        float glow = EventTimeline.glow(t, fin);

        scroll = (scroll + (0.010 + 0.022 * glow * glow) * dt) % 1000.0;
        float pulse = (float) (0.88 + 0.12 * Math.sin(now * (0.08 + 0.18 * glow)) * (0.5 + glow));
        float br = ClientBorderState.brightness();
        float a = Math.min(1f, (0.55f + 0.35f * glow) * pulse * Math.min(1.6f, br)) * vis;
        // цвет: тёмно-синий -> бирюзовый, ярче при росте свечения
        float k = Math.min(1.25f, (0.55f + 0.45f * glow) * (0.75f + 0.25f * br));
        int cr = Math.min(255, (int) (70 * k)), cg = Math.min(255, (int) (215 * k)), cb = Math.min(255, (int) (255 * Math.min(1f, 0.7f + 0.3f * k)));

        double r = ClientBorderState.getRadius(nether, partial);
        PoseStack pose = event.getPoseStack();
        var cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        double view = Math.min(400, mc.options.getEffectiveRenderDistance() * 16 + 48);

        if (hideWall) { pose.popPose(); return; }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // 1) основная стена
        RenderType wallType = RenderType.beaconBeam(WALL, true);
        VertexConsumer vc = buffers.getBuffer(wallType);
        boolean ov = hasOverlay(wallType);
        float yFade = MAX_Y - FADE_HEIGHT;
        int aFull = (int) (255 * Math.max(0f, Math.min(1f, a)));
        StringBuilder dbg = new StringBuilder();
        for (int side = 0; side < 4; side++) {
            int axis = side < 2 ? 0 : 1;
            double fixed = (side % 2 == 0) ? r : -r;
            wallSide(vc, ov, m, cam, axis, fixed, 0.0, r, view, yFade, cr, cg, cb, aFull, dbg);
        }
        lastInfo = String.format("t=%.1f fin=%b vis=%.2f glow=%.2f br=%.2f a=%d r=%.1f view=%.0f cam=(%.1f,%.1f,%.1f) sides:%s",
                t, fin, vis, glow, br, aFull, r, view, cam.x, cam.y, cam.z, dbg);
        buffers.endBatch(wallType);

        // дополнительное аддитивное свечение при яркости выше 1
        if (br > 1.0f) {
            float extra = Math.min(1f, (br - 1.0f) * 0.45f * vis * pulse);
            int ae = (int) (255 * extra);
            if (ae > 2) {
                RenderType glowType = RenderType.eyes(WALL);
                VertexConsumer gc = buffers.getBuffer(glowType);
                boolean gov = hasOverlay(glowType);
                for (int side = 0; side < 4; side++) {
                    int axis = side < 2 ? 0 : 1;
                    double fixed = (side % 2 == 0) ? r : -r;
                    wallSide(gc, gov, m, cam, axis, fixed, 0.03, r, view, yFade, cr, cg, cb, ae, null);
                }
                buffers.endBatch(glowType);
            }
        }

        // 2) аддитивные эффекты
        boolean calm = BorderConfig.REDUCE_FLASHES.get();
        float fx = (calm ? 0.5f : 1f) * vis;

        if (t >= 160 && t < 260 && fx > 0.01f) {
            float prog = (t - 160) / 100f;
            float fade = (float) Math.sin(Math.PI * prog);
            RenderType bandType = RenderType.eyes(BAND);
            VertexConsumer bc = buffers.getBuffer(bandType);
            boolean bov = hasOverlay(bandType);
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
                int al = (int) (255 * Math.max(0f, Math.min(1f, fade * 0.8f * fx)));
                quad(bc, bov, m, axis, fixed + off, a0, a1, MIN_Y, yFade, u0, u1, 6f, 0f, 255, 255, 255, al, al);
            }
            buffers.endBatch(bandType);
        }

        double gt = mc.level.getGameTime() + partial;
        drawRipples(buffers, m, cam, gt, RING, false, fx);
        drawRipples(buffers, m, cam, gt, FLASH, true, fx);
        pose.popPose();
        if (trace && System.currentTimeMillis() - lastTrace > 2000) {
            lastTrace = System.currentTimeMillis();
            com.mojang.logging.LogUtils.getLogger().info("[WBE trace] {}", lastInfo);
        }
    }

    /** Одна сторона стены, разбитая на сегменты вдоль: дальние сегменты плавно гаснут (убирает муар от мелкой текстуры). */
    private static void wallSide(VertexConsumer vc, boolean ov, Matrix4f m, net.minecraft.world.phys.Vec3 cam, int axis, double fixed, double offMag,
                                 double r, double view, float yFade, int cr, int cg, int cb, int aFull, StringBuilder dbg) {
        double p = axis == 0 ? cam.z : cam.x;
        double camFixed = axis == 0 ? cam.x : cam.z;
        double a0 = Math.max(-r, p - view), a1 = Math.min(r, p + view);
        if (a1 <= a0) return;
        double off = offMag == 0 ? 0 : Math.signum(camFixed - fixed) * offMag;
        double dFixed = Math.abs(fixed - camFixed);
        if (dbg != null) dbg.append(String.format(" [%s%s dist=%.1f along=%.0f..%.0f]", axis == 0 ? "x" : "z", fixed > 0 ? "+" : "-", dFixed, a0, a1));
        float vOf = (float) scroll;
        double fadeStart = view * 0.5;
        int n = Math.max(1, (int) Math.ceil((a1 - a0) / 24.0));
        for (int i = 0; i < n; i++) {
            double s0 = a0 + (a1 - a0) * i / n, s1 = a0 + (a1 - a0) * (i + 1) / n;
            int al0 = (int) (aFull * distFade(Math.hypot(dFixed, s0 - p), fadeStart, view));
            int al1 = (int) (aFull * distFade(Math.hypot(dFixed, s1 - p), fadeStart, view));
            if (al0 <= 0 && al1 <= 0) continue;
            float u0 = (float) (frac(s0 / TILE) + scroll), u1 = u0 + (float) ((s1 - s0) / TILE);
            quadA(vc, ov, m, axis, fixed + off, s0, s1, MIN_Y, yFade, u0, u1, (yFade - MIN_Y) / TILE + vOf, vOf, cr, cg, cb, al0, al1, al0, al1);
            quadA(vc, ov, m, axis, fixed + off, s0, s1, yFade, MAX_Y, u0, u1, vOf, vOf - FADE_HEIGHT / TILE, cr, cg, cb, al0, al1, 0, 0);
        }
    }

    private static double distFade(double d, double start, double end) {
        if (d <= start) return 1.0;
        if (d >= end) return 0.0;
        double f = (end - d) / (end - start);
        return f * f * (3 - 2 * f);
    }

    private static void drawRipples(MultiBufferSource.BufferSource buffers, Matrix4f m, net.minecraft.world.phys.Vec3 cam, double gt,
                                    ResourceLocation tex, boolean flash, float fx) {
        boolean any = false;
        for (ClientBorderState.Ripple rp : ClientBorderState.ripples()) if (rp.flash == flash) { any = true; break; }
        if (!any || fx <= 0.01f) return;
        RenderType type = RenderType.eyes(tex);
        VertexConsumer vc = buffers.getBuffer(type);
        boolean ov = hasOverlay(type);
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
            quad(vc, ov, m, rp.axis, fixed + off, along - rad, along + rad, (float) (rp.y - rad), (float) (rp.y + rad), 0f, 1f, 1f, 0f, 255, 255, 255, ia, ia);
        }
        buffers.endBatch(type);
    }

    private static boolean hasOverlay(RenderType type) {
        VertexFormat f = type.format();
        return f.getElements().contains(DefaultVertexFormat.ELEMENT_UV1);
    }

    /** Четырёхугольник в плоскости стены (axis 0 — x=fixed, вдоль z; axis 1 — z=fixed, вдоль x), оба направления обхода. */
    private static void quad(VertexConsumer vc, boolean ov, Matrix4f m, int axis, double fixed, double a0, double a1, double y0, double y1,
                             float u0, float u1, float vBottom, float vTop, int r, int g, int b, int aBottom, int aTop) {
        vert(vc, ov, m, axis, fixed, a0, y0, u0, vBottom, r, g, b, aBottom);
        vert(vc, ov, m, axis, fixed, a1, y0, u1, vBottom, r, g, b, aBottom);
        vert(vc, ov, m, axis, fixed, a1, y1, u1, vTop, r, g, b, aTop);
        vert(vc, ov, m, axis, fixed, a0, y1, u0, vTop, r, g, b, aTop);
        vert(vc, ov, m, axis, fixed, a0, y1, u0, vTop, r, g, b, aTop);
        vert(vc, ov, m, axis, fixed, a1, y1, u1, vTop, r, g, b, aTop);
        vert(vc, ov, m, axis, fixed, a1, y0, u1, vBottom, r, g, b, aBottom);
        vert(vc, ov, m, axis, fixed, a0, y0, u0, vBottom, r, g, b, aBottom);
    }

    /** Как quad, но с отдельной прозрачностью каждого угла: aBL, aBR (низ), aTL, aTR (верх). */
    private static void quadA(VertexConsumer vc, boolean ov, Matrix4f m, int axis, double fixed, double a0, double a1, double y0, double y1,
                              float u0, float u1, float vBottom, float vTop, int r, int g, int b, int aBL, int aBR, int aTL, int aTR) {
        vert(vc, ov, m, axis, fixed, a0, y0, u0, vBottom, r, g, b, aBL);
        vert(vc, ov, m, axis, fixed, a1, y0, u1, vBottom, r, g, b, aBR);
        vert(vc, ov, m, axis, fixed, a1, y1, u1, vTop, r, g, b, aTR);
        vert(vc, ov, m, axis, fixed, a0, y1, u0, vTop, r, g, b, aTL);
        vert(vc, ov, m, axis, fixed, a0, y1, u0, vTop, r, g, b, aTL);
        vert(vc, ov, m, axis, fixed, a1, y1, u1, vTop, r, g, b, aTR);
        vert(vc, ov, m, axis, fixed, a1, y0, u1, vBottom, r, g, b, aBR);
        vert(vc, ov, m, axis, fixed, a0, y0, u0, vBottom, r, g, b, aBL);
    }

    private static void vert(VertexConsumer vc, boolean ov, Matrix4f m, int axis, double fixed, double along, double y,
                             float u, float v, int r, int g, int b, int a) {
        float x = (float) (axis == 0 ? fixed : along);
        float z = (float) (axis == 0 ? along : fixed);
        VertexConsumer c = vc.vertex(m, x, (float) y, z).color(r, g, b, a).uv(u, v);
        if (ov) c = c.overlayCoords(OverlayTexture.NO_OVERLAY);
        c.uv2(LIGHT).normal(0f, 1f, 0f).endVertex();
    }

    private static double frac(double v) { return v - Math.floor(v); }
}
