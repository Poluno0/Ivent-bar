package com.example.worldborderevent;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only radius smoothing. Server packet values are interpolation targets. */
@Mod.EventBusSubscriber(modid = WorldBorderEventMod.MODID, value = Dist.CLIENT)
public final class ClientBorderState {
    private static volatile double radius = 10000.0;
    private static volatile double targetRadius = 10000.0;
    private ClientBorderState() {}

    public static void setRadius(double next) {
        targetRadius = Math.max(1.0, next);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        double delta = targetRadius - radius;
        if (Math.abs(delta) < 0.02) radius = targetRadius;
        else radius += delta * 0.40;
    }

    public static double getRadius(boolean nether) {
        double r = radius;
        return nether ? r / 8.0 : r;
    }
}