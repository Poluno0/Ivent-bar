package com.example.worldborderevent;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Клиентское зеркало состояния границы: радиус, фаза ивента, ударные волны, частицы, звуки. Физику не определяет. */
@Mod.EventBusSubscriber(modid = WorldBorderEventMod.MODID, value = Dist.CLIENT)
public final class ClientBorderState {
    public static final class Ripple {
        public double x, y, z;
        public int axis;        // 0 — стена x=±R (вдоль z), 1 — стена z=±R (вдоль x)
        public float strength;  // 0..1
        public double birth;    // игровое время (тики)
        public float life, maxR;
        public boolean flash;
    }

    private static final int MAX_RIPPLES = 24;
    private static final List<Ripple> ripples = new ArrayList<>();

    private static double prevR = 10000, curR = 10000, targetR = 10000;
    private static boolean hasState = false;
    private static boolean finished = false;
    private static float eventTick = -1f;
    private static float lastEventTick = -1f;
    private static Level lastLevel;
    private static long lastHitSoundTick = -100;
    private static int tickCounter = 0;

    private ClientBorderState() {}

    // ---------- вход от сети ----------

    public static void applyState(double radius, int tick, boolean fin) {
        targetR = Math.max(1, radius);
        finished = fin;
        if (!hasState) { hasState = true; prevR = curR = targetR; }
        if (tick < 0) {
            eventTick = -1f;
        } else if (eventTick < 0 || Math.abs(eventTick - tick) > 2.5f) {
            eventTick = tick;
        }
    }

    public static void addImpact(double x, double y, double z, int kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean nether = mc.level.dimension() == Level.NETHER;
        if (!nether && mc.level.dimension() != Level.OVERWORLD) return;
        double r = getRadius(nether, 1f);
        int axis;
        if (Math.abs(Math.abs(x) - r) <= Math.abs(Math.abs(z) - r)) axis = 0; else axis = 1;
        double wall = axis == 0 ? Math.copySign(r, x) : Math.copySign(r, z);
        double gt = mc.level.getGameTime();
        float strength = kind == 2 ? 1.0f : (kind == 3 ? 0.55f : 0.5f);
        // слияние близких эффектов — защита от визуального спама
        for (Ripple q : ripples) {
            if (q.axis == axis && gt - q.birth < 4 && Math.abs(q.y - y) < 1.5
                    && Math.abs((axis == 0 ? q.z - z : q.x - x)) < 1.5) {
                q.strength = Math.min(1f, q.strength + 0.15f);
                playHit(mc, x, y, z, axis, wall, kind);
                return;
            }
        }
        Ripple rp = new Ripple();
        rp.axis = axis;
        rp.x = axis == 0 ? wall : x; rp.z = axis == 1 ? wall : z; rp.y = y;
        rp.strength = strength; rp.birth = gt; rp.flash = false;
        rp.life = 10 + 14 * strength; rp.maxR = 2.0f + 4.0f * strength;
        push(rp);
        if (kind != 1) { // искра в точке попадания
            Ripple f = new Ripple();
            f.axis = axis; f.x = rp.x; f.y = y; f.z = rp.z; f.strength = strength; f.birth = gt; f.flash = true;
            f.life = 6; f.maxR = 1.6f + 1.2f * strength;
            push(f);
        }
        playHit(mc, x, y, z, axis, wall, kind);
    }

    private static void playHit(Minecraft mc, double x, double y, double z, int axis, double wall, int kind) {
        long gt = mc.level.getGameTime();
        if (gt - lastHitSoundTick < 3) return; // ограничение частоты звуков
        lastHitSoundTick = gt;
        double px = axis == 0 ? wall : x, pz = axis == 1 ? wall : z;
        float vol = (float) (BorderConfig.SOUND_VOLUME.get() * (kind == 2 ? 0.9 : 0.6));
        mc.level.playLocalSound(px, y, pz, (kind == 2 ? ModSounds.HIT_HEAVY : ModSounds.HIT_LIGHT).get(),
                net.minecraft.sounds.SoundSource.AMBIENT, vol, 0.9f + mc.level.random.nextFloat() * 0.2f, false);
    }

    private static void push(Ripple r) {
        if (ripples.size() >= MAX_RIPPLES) ripples.remove(0);
        ripples.add(r);
    }

    // ---------- доступ для рендера и звука ----------

    public static boolean hasState() { return hasState; }
    public static boolean isFinished() { return finished; }
    public static float eventTick() { return eventTick; }
    public static boolean isEventActive() { return eventTick >= 0; }
    public static List<Ripple> ripples() { return ripples; }

    /** Радиус для текущего измерения, интерполированный по partialTick. */
    public static double getRadius(boolean nether, float partial) {
        double r = prevR + (curR - prevR) * Math.max(0f, Math.min(1f, partial));
        return nether ? r / 8.0 : r;
    }

    public static boolean inSupportedDimension(Minecraft mc) {
        return mc.level != null && mc.player != null
                && (mc.level.dimension() == Level.OVERWORLD || mc.level.dimension() == Level.NETHER);
    }

    /** Расстояние от игрока до ближайшей стены (внутри — положительное). */
    public static double distanceToWall(Minecraft mc) {
        boolean nether = mc.level.dimension() == Level.NETHER;
        double r = getRadius(nether, 1f);
        double dx = r - Math.abs(mc.player.getX()), dz = r - Math.abs(mc.player.getZ());
        return Math.abs(Math.min(dx, dz));
    }

    public static void reset() {
        ripples.clear();
        hasState = false;
        eventTick = -1f;
        lastEventTick = -1f;
        lastLevel = null;
        BorderSounds.stopAll();
    }

    // ---------- тик клиента ----------

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (hasState || !ripples.isEmpty()) reset();
            return;
        }
        if (mc.level != lastLevel) { // смена измерения/мира: старые эффекты не переносим
            lastLevel = mc.level;
            ripples.clear();
        }
        prevR = curR;
        curR = targetR;
        if (eventTick >= 0) eventTick += 1f;
        long gt = mc.level.getGameTime();
        ripples.removeIf(r -> gt - r.birth > r.life + 2);
        if (!hasState || !inSupportedDimension(mc)) {
            BorderSounds.stopAll();
            lastEventTick = eventTick;
            return;
        }
        tickCounter++;
        spawnEffects(mc, gt);
        BorderSounds.update(mc, lastEventTick, eventTick);
        lastEventTick = eventTick;
    }

    private static void spawnEffects(Minecraft mc, long gt) {
        boolean nether = mc.level.dimension() == Level.NETHER;
        double r = getRadius(nether, 1f);
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        double dx = r - Math.abs(px), dz = r - Math.abs(pz);
        int axis = dx <= dz ? 0 : 1;
        double dist = Math.abs(Math.min(dx, dz));
        if (dist > 128) return;
        double wall = axis == 0 ? Math.copySign(r, px) : Math.copySign(r, pz);
        var rnd = mc.level.random;
        float t = eventTick;
        boolean calm = BorderConfig.REDUCE_FLASHES.get();

        // случайные вспышки и малые ударные волны по фазам сценария
        float chance = 0f;
        if (t >= 60 && t < 160) chance = 0.15f;
        else if (t >= 160 && t < 240) chance = 0.35f;
        else if (t >= 240 && t < 600) chance = 0.08f;
        if (calm) chance *= 0.4f;
        if (chance > 0 && rnd.nextFloat() < chance && ripples.size() < MAX_RIPPLES - 4) {
            double along = (axis == 0 ? pz : px) + (rnd.nextDouble() - 0.5) * 90;
            double y = py + (rnd.nextDouble() - 0.4) * 30;
            Ripple rp = new Ripple();
            rp.axis = axis; rp.y = y; rp.birth = gt;
            rp.x = axis == 0 ? wall : along; rp.z = axis == 1 ? wall : along;
            boolean big = t >= 160 && t < 240 && rnd.nextFloat() < 0.4f;
            rp.flash = rnd.nextFloat() < 0.5f;
            rp.strength = big ? 0.9f : 0.4f + rnd.nextFloat() * 0.3f;
            rp.life = rp.flash ? 8 : 18 + (big ? 12 : 0);
            rp.maxR = rp.flash ? 2.5f + rnd.nextFloat() * 2f : (big ? 12f : 4.5f);
            push(rp);
        }

        // частицы: ограниченное количество
        if (BorderConfig.PARTICLES.get() && dist < 40) {
            int every = isEventActive() ? 3 : 10;
            if (tickCounter % every == 0) {
                int n = isEventActive() ? 2 : 1;
                if (finished && !isEventActive()) n = 0;
                for (int i = 0; i < n; i++) {
                    double along = (axis == 0 ? pz : px) + (rnd.nextDouble() - 0.5) * 30;
                    double y = py + (rnd.nextDouble() - 0.3) * 8;
                    double inward = (rnd.nextDouble() * 0.03) * (wall > 0 ? -1 : 1);
                    double x = axis == 0 ? wall : along, z = axis == 1 ? wall : along;
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                            axis == 0 ? inward : 0, (rnd.nextDouble() - 0.5) * 0.04, axis == 1 ? inward : 0);
                }
            }
        }
    }
}
