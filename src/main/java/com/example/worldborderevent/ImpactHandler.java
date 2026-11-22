package com.example.worldborderevent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Серверная часть реакции на попадания: снаряды останавливаются границей, удары игроков проверяются. */
public final class ImpactHandler {
    private static final int MAX_IMPACTS_PER_SCAN = 8;
    private static final Map<UUID, Long> lastHit = new HashMap<>();

    private ImpactHandler() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 2 != 0) return;
        scan(server.getLevel(Level.OVERWORLD));
        scan(server.getLevel(Level.NETHER));
        if (server.getTickCount() % 1200 == 0) lastHit.clear();
    }

    private static void scan(ServerLevel level) {
        if (level == null || !BorderController.isBarrierActive()) return;
        double lim = BorderController.radiusFor(level);
        List<Entity> stop = null;
        int sent = 0;
        for (Entity e : level.getAllEntities()) {
            if (!(e instanceof Projectile)) continue;
            Vec3 p = e.position();
            Vec3 n = p.add(e.getDeltaMovement());
            if (Math.abs(n.x) <= lim && Math.abs(n.z) <= lim && Math.abs(p.x) <= lim && Math.abs(p.z) <= lim) continue;
            // точка пересечения отрезка p->n с плоскостью границы
            double t = 1.0;
            Vec3 d = n.subtract(p);
            if (Math.abs(n.x) > lim && Math.abs(d.x) > 1.0e-6) t = Math.min(t, (Math.copySign(lim, n.x) - p.x) / d.x);
            if (Math.abs(n.z) > lim && Math.abs(d.z) > 1.0e-6) t = Math.min(t, (Math.copySign(lim, n.z) - p.z) / d.z);
            t = Math.max(0.0, Math.min(1.0, t));
            double hx = Math.max(-lim, Math.min(lim, p.x + d.x * t));
            double hz = Math.max(-lim, Math.min(lim, p.z + d.z * t));
            if (stop == null) stop = new ArrayList<>();
            stop.add(e);
            // ограничение частоты рассылки эффектов; сама остановка снарядов работает всегда
            if (sent < MAX_IMPACTS_PER_SCAN) {
                BorderNetwork.broadcastImpact(level, hx, p.y + d.y * t, hz, 3);
                sent++;
            }
        }
        if (stop != null) for (Entity e : stop) e.discard();
    }

    /** Удар игрока по стене: проверка расстояния/позиции и частоты, затем рассылка. */
    public static void onPlayerHit(ServerPlayer p, double x, double y, double z, int kind) {
        if (!BorderController.isBarrierActive()) return;
        ServerLevel level = p.serverLevel();
        if (level.dimension() != Level.OVERWORLD && level.dimension() != Level.NETHER) return;
        double lim = BorderController.radiusFor(level);
        boolean nearX = Math.abs(Math.abs(x) - lim) < 1.5 && Math.abs(z) <= lim + 1.5;
        boolean nearZ = Math.abs(Math.abs(z) - lim) < 1.5 && Math.abs(x) <= lim + 1.5;
        if (!nearX && !nearZ) return;
        if (p.distanceToSqr(x, y, z) > 64.0) return;
        long now = level.getGameTime();
        Long last = lastHit.get(p.getUUID());
        if (last != null && now - last < 4) return;
        lastHit.put(p.getUUID(), now);
        BorderNetwork.broadcastImpact(level, x, y, z, kind == 2 ? 2 : 1);
    }
}
