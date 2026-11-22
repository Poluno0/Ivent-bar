package com.example.worldborderevent;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, WorldBorderEventMod.MODID);
    public static final RegistryObject<SoundEvent> HUM = reg("hum");
    public static final RegistryObject<SoundEvent> RUMBLE = reg("rumble");
    public static final RegistryObject<SoundEvent> AWAKEN = reg("awaken");
    public static final RegistryObject<SoundEvent> THUNDER = reg("thunder");
    public static final RegistryObject<SoundEvent> PULSE_A = reg("pulse_a");
    public static final RegistryObject<SoundEvent> PULSE_B = reg("pulse_b");
    public static final RegistryObject<SoundEvent> BOOM = reg("boom");
    public static final RegistryObject<SoundEvent> HIT_LIGHT = reg("hit_light");
    public static final RegistryObject<SoundEvent> HIT_HEAVY = reg("hit_heavy");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(WorldBorderEventMod.MODID, name)));
    }
}
