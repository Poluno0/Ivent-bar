package com.example.worldborderevent;

import net.minecraftforge.common.ForgeConfigSpec;

/** Клиентские настройки: доступность и производительность. */
public final class BorderConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue SHAKE;
    public static final ForgeConfigSpec.BooleanValue PARTICLES;
    public static final ForgeConfigSpec.BooleanValue REDUCE_FLASHES;
    public static final ForgeConfigSpec.DoubleValue SOUND_VOLUME;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        SHAKE = b.comment("Сила тряски камеры (0 = выключена, 1 = как задумано)").defineInRange("shakeIntensity", 1.0, 0.0, 1.0);
        PARTICLES = b.comment("Частицы у стены").define("particles", true);
        REDUCE_FLASHES = b.comment("Ослабить яркие вспышки и ударные волны").define("reduceFlashes", false);
        SOUND_VOLUME = b.comment("Множитель громкости звуков границы").defineInRange("soundVolume", 1.0, 0.0, 1.0);
        SPEC = b.build();
    }

    private BorderConfig() {}
}
