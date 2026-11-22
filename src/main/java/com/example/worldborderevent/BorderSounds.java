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
        // Периодические звуки барьера: раз в 15 с по очереди, синхронно у всех игроков (по игровому времени)
        if (ClientBorderState.barrierVisible() && !(tick >= 0 && tick < 250) && gameTime % 300 == 0 && gameTime != lastPulse) {
            lastPulse = gameTime;
            boolean a = (gameTime / 300) % 2 == 0;
            float v = Math.max(0.3f, proximity(mc)) * master;
            once(mc, (a ? ModSounds.PULSE_A : ModSounds.PULSE_B).get(), v, 1.0f);
        }
        if (tick < 0) return;
        if (crossed(prevTick, tick, 0)) once(mc, ModSounds.AWAKEN.get(), 1.0f * master, 1.0f);   // нарастающий бас на 12 секунд
        if (crossed(prevTick, tick, 100)) once(mc, ModSounds.THUNDER.get(), 0.9f * master, 0.8f);
        if (crossed(prevTick, tick, 175)) once(mc, ModSounds.THUNDER.get(), 1.0f * master, 0.7f);
        if (crossed(prevTick, tick, 230)) once(mc, ModSounds.BOOM.get(), 1.0f * master, 0.85f); // глубокий удар в кульминации
        if (crossed(prevTick, tick, 242)) once(mc, ModSounds.BOOM.get(), 1.0f * master, 0.6f);  // начало движения
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
