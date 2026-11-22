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
            if (crossed(prevTick, tick, DRUM_TICKS[i])) once(mc, ModSounds.DRUM.get(), DRUM_VOL[i] * master, DRUM_PITCH[i]);
        }
        if (crossed(prevTick, tick, 120)) once(mc, ModSounds.THUNDER.get(), 0.8f * master, 0.8f);
        if (crossed(prevTick, tick, 200)) once(mc, ModSounds.THUNDER.get(), 0.95f * master, 0.7f);
        if (crossed(prevTick, tick, 240)) once(mc, ModSounds.BOOM.get(), 1.0f * master, 1.0f);  // кульминация: начало движения
    }

    private static final int[] DRUM_TICKS = {40, 80, 115, 145, 170, 190, 206, 219, 230};
    private static final float[] DRUM_VOL = {0.22f, 0.30f, 0.40f, 0.50f, 0.62f, 0.74f, 0.85f, 0.94f, 1.0f};
    private static final float[] DRUM_PITCH = {1.0f, 0.99f, 0.97f, 0.95f, 0.93f, 0.91f, 0.89f, 0.87f, 0.85f};

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
