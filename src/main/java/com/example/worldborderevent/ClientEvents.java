package com.example.worldborderevent;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Тряска камеры, обнаружение ударов игрока по стене, очистка при выходе из мира. */
@Mod.EventBusSubscriber(modid = WorldBorderEventMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static long lastSwingTick = -100;

    private ClientEvents() {}

    @SubscribeEvent
    public static void cameraShake(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ClientBorderState.hasState()) return;
        float t = ClientBorderState.eventTick();
        if (t < 0) return;
        double strength = BorderConfig.SHAKE.get();
        if (strength <= 0.0 || !ClientBorderState.inSupportedDimension(mc)) return;
        float tt = t + (float) e.getPartialTick();
        float amp = EventTimeline.shake(tt) * (float) strength;
        if (amp <= 0f) return;
        double time = mc.level.getGameTime() + e.getPartialTick();
        // малые углы: тряска не лишает управления и не мешает прицеливанию
        float pitch = (float) (Math.sin(time * 1.9) + 0.6 * Math.sin(time * 4.3 + 1.0)) * 0.45f * amp;
        float yaw = (float) (Math.sin(time * 1.4 + 2.0) + 0.5 * Math.sin(time * 3.7)) * 0.45f * amp;
        float roll = (float) (Math.sin(time * 2.3 + 0.5) + 0.5 * Math.sin(time * 5.1)) * 0.8f * amp;
        e.setPitch(e.getPitch() + pitch);
        e.setYaw(e.getYaw() + yaw);
        e.setRoll(e.getRoll() + roll);
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty e) {
        tryHit(e.getEntity());
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getLevel().isClientSide) tryHit(e.getEntity());
    }

    private static void tryHit(Player p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || p != mc.player || !ClientBorderState.hasState() || !ClientBorderState.inSupportedDimension(mc)) return;
        if (!ClientBorderState.barrierVisible()) return;
        long gt = mc.level.getGameTime();
        if (gt - lastSwingTick < 4) return;
        boolean nether = mc.level.dimension() == Level.NETHER;
        double r = ClientBorderState.getRadius(nether, 1f);
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double best = Double.MAX_VALUE;
        for (int axis = 0; axis < 2; axis++) {
            double o = axis == 0 ? eye.x : eye.z, d = axis == 0 ? look.x : look.z;
            if (Math.abs(d) < 1.0e-6) continue;
            double wall = Math.copySign(r, d);
            double dist = (wall - o) / d;
            if (dist > 0 && dist < best) best = dist;
        }
        double reach = 4.5;
        if (best > reach) return;
        Vec3 hit = eye.add(look.scale(best));
        if (Math.abs(hit.x) > r + 0.5 || Math.abs(hit.z) > r + 0.5) return;
        lastSwingTick = gt;
        int kind = p.getMainHandItem().getItem() instanceof TieredItem ? 2 : 1;
        BorderNetwork.C.sendToServer(new BorderHitPacket(hit.x, hit.y, hit.z, kind));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientBorderState.reset();
    }
}
