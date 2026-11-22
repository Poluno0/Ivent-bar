package com.example.worldborderevent;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(WorldBorderEventMod.MODID)
public class WorldBorderEventMod {
    public static final String MODID = "worldborderevent";

    public WorldBorderEventMod() {
        BorderNetwork.register();
        ModSounds.SOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BorderConfig.SPEC);
    }
}
