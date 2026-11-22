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

    private BorderSounds() {}

    static final class Loop extends AbstractTickableSoundInstance {
        private final Supplier<Float> volumeFn;
        private boolean killed;

        Loop(SoundEvent event, Supplier<Float> volumeFn) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
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

    static void update(Minecraft mc, float prevTick, float tick) {
        float master = BorderConfig.SOUND_VOLUME.get().floatValue();
        boolean fin = ClientBorderState.isFinished();
        if (hum == null || hum.isStopped()) {
            hum = new Loop(ModSounds.HUM.get(), () -> {
                Minecraft m = Minecraft.getInstance();
                if (m.level == null || !ClientBorderState.inSupportedDimension(m)) return 0f;
                return 0.55f * BorderConfig.SOUND_VOLUME.get().floatValue() * proximity(m)
                        * EventTimeline.hum(ClientBorderState.eventTick(), ClientBorderState.isFinished());
            });
            mc.getSoundManager().play(hum);
        }
        if (rumble == null || rumble.isStopped()) {
            rumble = new Loop(ModSounds.RUMBLE.get(), () -> {
                Minecraft m = Minecraft.getInstance();
                if (m.level == null || !ClientBorderState.inSupportedDimension(m)) return 0f;
                float p = proximity(m);
                return 0.9f * BorderConfig.SOUND_VOLUME.get().floatValue() * EventTimeline.rumble(ClientBorderState.eventTick()) * (0.4f + 0.6f * p);
            });
            mc.getSoundManager().play(rumble);
        }
        if (tick < 0 || master <= 0f) return;
        if (crossed(prevTick, tick, 60)) once(mc, ModSounds.SWELL.get(), 0.9f * master, 1.0f);
        if (crossed(prevTick, tick, 160)) once(mc, ModSounds.BOOM.get(), 0.5f * master, 1.2f);
        if (crossed(prevTick, tick, 230)) once(mc, ModSounds.BOOM.get(), 1.0f * master, 0.8f); // глубокий удар в кульминации
        if (crossed(prevTick, tick, 240)) once(mc, ModSounds.BOOM.get(), 0.7f * master, 0.6f);
    }

    private static boolean crossed(float prev, float now, float thr) {
        return prev < thr && now >= thr;
    }

    private static void once(Minecraft mc, SoundEvent ev, float volume, float pitch) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, volume));
    }

    static void stopAll() {
        if (hum != null) { hum.kill(); hum = null; }
        if (rumble != null) { rumble.kill(); rumble = null; }
    }
}
