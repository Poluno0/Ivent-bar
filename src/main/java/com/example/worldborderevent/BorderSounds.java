package com.example.worldborderevent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.function.Supplier;

/** Клиентские звуки: фоновый гул у стены, низкочастотный слой ивента, разовые удары. */
public final class BorderSounds {
    private static Loop hum, rumble;
    private static long lastPulse = -1;

    private BorderSounds() {}

    static final class Loop extends AbstractTickableSoundInstance {
        private final Supplier<Float> volumeFn;
        private boolean killed;

        Loop(SoundEvent event, Supplier<Float> volumeFn) {
            super(event, SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.volumeFn = volumeFn;
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.0f;
            this.pitch = 1.0f;
        }

        @Override
        public boolean canStartSilent() { return true; }

        @Override
        public void tick() {
            if (killed) { stop(); return; }
            float v = volumeFn.get();
            // плавное изменение громкости — без щелчков
            this.volume += (v - this.volume) * 0.15f;
        }

        void kill() { killed = true; stop(); }
    }

    private static float proximity(Minecraft mc) {
        double d = ClientBorderState.distanceToWall(mc);
        double f = 1.0 - (d - 6.0) / 90.0;
        f = Math.max(0.0, Math.min(1.0, f));
        return (float) (f * f);
    }

    static void update(Minecraft mc, float prevTick, float tick, long gameTime) {
        float master = BorderConfig.SOUND_VOLUME.get().floatValue();
        boolean fin = ClientBorderState.isFinished();
        if (hum == null || hum.isStopped()) {
            hum = new Loop(ModSounds.HUM.get(), () -> {
                Minecraft m = Minecraft.getInstance();
                if (m.level == null || !ClientBorderState.inSupportedDimension(m)) return 0f;
                return 0.95f * BorderConfig.SOUND_VOLUME.get().floatValue() * proximity(m)
                        * EventTimeline.hum(ClientBorderState.eventTick(), ClientBorderState.isFinished());
            });
            mc.getSoundManager().play(hum);
        }
        if (rumble == null || rumble.isStopped()) {
            rumble = new Loop(ModSounds.RUMBLE.get(), () -> {
                Minecraft m = Minecraft.getInstance();
                if (m.level == null || !ClientBorderState.inSupportedDimension(m)) return 0f;
                float p = proximity(m);
                return 1.0f * BorderConfig.SOUND_VOLUME.get().floatValue() * EventTimeline.rumble(ClientBorderState.eventTick()) * (0.4f + 0.6f * p);
            });
            mc.getSoundManager().play(rumble);
        }
        if (master <= 0f) return;
        // Раз в минуту барьер издаёт два звука по очереди; слышно только вблизи стены (как гул), синхронно по игровому времени
        if (ClientBorderState.barrierVisible() && !(tick >= 0 && tick < 250) && gameTime % 1200 == 0 && gameTime != lastPulse) {
            lastPulse = gameTime;
            boolean a = (gameTime / 1200) % 2 == 0;
            double d = ClientBorderState.distanceToWall(mc);
            float f = (float) Math.max(0.0, 1.0 - d / 160.0);
            float v = f * f * master;
            if (v > 0.01f) once(mc, (a ? ModSounds.PULSE_A : ModSounds.PULSE_B).get(), v, 1.0f);
        }
        if (tick < 0) return;
        if (crossed(prevTick, tick, 0)) once(mc, ModSounds.AWAKEN.get(), 1.0f * master, 1.0f);   // нарастающий бас на 12 секунд
        // удары по бочке: чем дальше, тем чаще, ниже и громче
        for (int i = 0; i < DRUM_TICKS.length; i++) {
            if (crossed(prevTick, tick, DRUM_TICKS[i])) {
                float p = i / (float) (DRUM_TICKS.length - 1);
                once(mc, ModSounds.DRUM.get(), (0.18f + 0.82f * p) * master, 1.0f - 0.18f * p);
            }
        }
        // присланные звуки (вперемешку): всё громче к кульминации
        for (int i = 0; i < THUNDER_TICKS.length; i++) {
            if (crossed(prevTick, tick, THUNDER_TICKS[i])) {
                float p = i / (float) (THUNDER_TICKS.length - 1);
                once(mc, ModSounds.THUNDERS[THUNDER_ORDER[i]].get(), (0.6f + 0.4f * p) * master, 1.0f);
            }
        }
        // «отдаление»: у звука 3-я секунда совпадает с началом движения барьера (тик 240), т.е. запуск на 3 с (60 тиков) раньше
        if (crossed(prevTick, tick, RETREAT_START_TICK - 60)) once(mc, ModSounds.RETREAT.get(), 1.0f * master, 1.0f);
        if (crossed(prevTick, tick, 240)) once(mc, ModSounds.BOOM.get(), 1.0f * master, 1.0f);  // кульминация: начало движения
    }

    private static final int RETREAT_START_TICK = (int) EventTimeline.INTRO_TICKS;
    private static final int[] THUNDER_TICKS = {60, 105, 140, 170, 200, 222};
    private static final int[] THUNDER_ORDER = {2, 0, 3, 1, 0, 2};
    private static final int[] DRUM_TICKS = buildDrumTicks();

    /** Расписание ударов по бочке: интервал сокращается, ударов становится всё больше. */
    private static int[] buildDrumTicks() {
        java.util.ArrayList<Integer> l = new java.util.ArrayList<>();
        double t = 20, iv = 26;
        while (t < 236) { l.add((int) Math.round(t)); iv = Math.max(4, iv * 0.91); t += iv; }
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    private static boolean crossed(float prev, float now, float thr) {
        return prev < thr && now >= thr && now - thr < 4f; // не срабатывать, если вошли посреди ивента
    }

    private static void once(Minecraft mc, SoundEvent ev, float volume, float pitch) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, volume));
    }

    static void stopAll() {
        if (hum != null) { hum.kill(); hum = null; }
        if (rumble != null) { rumble.kill(); rumble = null; }
    }
}
