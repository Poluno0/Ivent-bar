package com.example.worldborderevent;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(WorldBorderEventMod.MODID)
public class WorldBorderEventMod {
    public static final String MODID = "worldborderevent";

    public WorldBorderEventMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
    }
}
