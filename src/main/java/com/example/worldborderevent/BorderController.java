package com.example.worldborderevent;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/** Серверное состояние границы: центр (0,0), радиус, ивент, физическое ограничение. Сервер — источник истины. */
@Mod.EventBusSubscriber(modid = WorldBorderEventMod.MODID)
public final class BorderController {
    private static double overworldRadius = 10000.0;
    private static double eventStartRadius = overworldRadius;
    private static boolean eventRunning = false;
    private static boolean finished = false;
    private static long eventTick = 0;
    private static MinecraftServer server;
    private static BorderSavedData data;

    private BorderController() {}

    public static double radiusFor(Level level) {
        if (level.dimension() == Level.NETHER) return overworldRadius / 8.0;
        return overworldRadius;
    }

    /** Физическое ограничение действует, пока ивент не дошёл до конца отдаления и не завершился. */
    public static boolean isBarrierActive() {
        if (finished) return false;
        return !(eventRunning && eventTick >= EventTimeline.INTRO_TICKS + EventTimeline.MOVE_TICKS);
    }

    // ---------- команды (названия и аргументы сохранены) ----------

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wbe").requires(s -> s.hasPermission(2))
            .then(Commands.literal("set").then(Commands.argument("radius", IntegerArgumentType.integer(16, 30000000))
                .executes(ctx -> {
                    if (eventRunning) stopEvent();
                    overworldRadius = IntegerArgumentType.getInteger(ctx, "radius");
                    eventStartRadius = overworldRadius;
                    finished = false;
                    persist();
                    broadcast();
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "Радиус установлен: Верхний мир %.1f, Незер %.2f (1:8). Центр: 0,0.", overworldRadius, overworldRadius / 8.0)), true);
                    return 1;
                })))
            .then(Commands.literal("event")
                .then(Commands.literal("start").executes(ctx -> startEvent(ctx.getSource())))
                .then(Commands.literal("restart").executes(ctx -> restartEvent(ctx.getSource())))
                .then(Commands.literal("stop").executes(ctx -> {
                    stopEvent();
                    ctx.getSource().sendSuccess(() -> Component.literal("Ивент отступления остановлен. Граница сброшена к исходному радиусу."), true);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    String msg = eventRunning ? String.format(Locale.ROOT, "Ивент идёт: тик %d/%d. Текущие радиусы: Верхний мир %.1f, Незер %.2f.", eventTick, EventTimeline.TOTAL_TICKS, currentOverworldRadius(), currentOverworldRadius() / 8.0)
                        : (finished ? "Ивент завершён: барьер снят. Используйте /wbe set <radius>, чтобы установить его заново."
                        : String.format(Locale.ROOT, "Ивент не запущен. Радиусы границы: Верхний мир %.1f, Незер %.2f.", overworldRadius, overworldRadius / 8.0));
                    ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                    return 1;
                }))));
    }

    private static int startEvent(CommandSourceStack source) {
        if (eventRunning) {
            source.sendFailure(Component.literal("Ивент уже запущен. Используй /wbe event restart."));
            return 0;
        }
        beginEvent();
        source.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§3§lГраница пробуждается...§r Начинается низкий гул."), false);
        return 1;
    }

    private static int restartEvent(CommandSourceStack source) {
        beginEvent();
        source.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§b§lИвент границы перезапущен.§r Гул возвращается..."), false);
        return 1;
    }

    private static void beginEvent() {
        eventStartRadius = overworldRadius;
        eventTick = 0;
        eventRunning = true;
        finished = false;
        persist();
        broadcast();
    }

    private static void stopEvent() {
        if (eventRunning) overworldRadius = eventStartRadius;
        eventRunning = false;
        finished = false;
        eventTick = 0;
        persist();
        broadcast();
    }

    private static double currentOverworldRadius() {
        if (!eventRunning) return overworldRadius;
        if (eventTick <= EventTimeline.INTRO_TICKS) return eventStartRadius;
        double progress = Math.min(1.0, (eventTick - EventTimeline.INTRO_TICKS) / (double) EventTimeline.MOVE_TICKS);
        double eased = progress * progress * (3.0 - 2.0 * progress); // smoothstep
        return eventStartRadius + EventTimeline.RETREAT_BLOCKS * eased;
    }

    // ---------- синхронизация и сохранение ----------

    private static void broadcast() {
        if (server == null) return;
        BorderNetwork.broadcastState(overworldRadius, eventRunning ? (int) eventTick : -1, finished);
    }

    private static void sendTo(ServerPlayer p) {
        BorderNetwork.sendState(p, overworldRadius, eventRunning ? (int) eventTick : -1, finished);
    }

    private static void persist() {
        if (data == null) return;
        data.radius = overworldRadius;
        data.startRadius = eventStartRadius;
        data.running = eventRunning;
        data.finished = finished;
        data.tick = eventTick;
        data.setDirty();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        data = server.overworld().getDataStorage().computeIfAbsent(BorderSavedData::load, BorderSavedData::new, BorderSavedData.NAME);
        overworldRadius = data.radius;
        eventStartRadius = data.startRadius;
        eventRunning = data.running;
        finished = data.finished;
        eventTick = data.tick;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        persist();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // Статические поля живут между одиночными мирами — сбрасываем.
        server = null;
        data = null;
        overworldRadius = 10000.0;
        eventStartRadius = overworldRadius;
        eventRunning = false;
        finished = false;
        eventTick = 0;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (eventRunning) {
            eventTick++;
            overworldRadius = currentOverworldRadius();
            if (eventTick >= EventTimeline.TOTAL_TICKS) {
                overworldRadius = eventStartRadius + EventTimeline.RETREAT_BLOCKS;
                eventRunning = false;
                finished = true;
                eventTick = 0;
                persist();
                broadcast();
                event.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§bГраница отступила. Ивент завершён."), false);
            } else {
                if (eventTick % 20 == 0) persist();
                broadcast(); // каждый тик: клиент плавно интерполирует и не отстаёт от физической границы
            }
        }
        ImpactHandler.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sendTo(p);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sendTo(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sendTo(p);
    }

    // ---------- физическое ограничение ----------

    @SubscribeEvent
    public static void enforceCustomBorder(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!isBarrierActive()) return;
        Level level = player.level();
        if (level.dimension() != Level.OVERWORLD && level.dimension() != Level.NETHER) return;
        double radius = radiusFor(level);
        double x = player.getX(), z = player.getZ();
        double limit = radius - 0.35;
        if (Math.abs(x) > limit || Math.abs(z) > limit) {
            double nx = Math.max(-limit, Math.min(limit, x));
            double nz = Math.max(-limit, Math.min(limit, z));
            player.teleportTo(nx, player.getY(), nz);
            player.setDeltaMovement(Vec3.ZERO);
        }
    }
}
