package com.example.worldborderevent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = WorldBorderEventMod.MODID)
public final class BorderController {
    private static double overworldRadius = 10000.0;
    private static boolean eventRunning = false;
    private static long eventTick = 0;
    private static final long INTRO_TICKS = 200; // 10 sec
    private static final long MOVE_TICKS = 400;  // 20 sec
    private static double eventStartRadius = overworldRadius;
    private static final double RETREAT_BLOCKS = 500.0;
    private static boolean wasOutsideWarning = false;

    private BorderController() {}

    public static double radiusFor(Level level) {
        if (level.dimension() == Level.NETHER) return overworldRadius / 8.0;
        return overworldRadius;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wbe").requires(s -> s.hasPermission(2))
            .then(Commands.literal("set").then(Commands.argument("radius", IntegerArgumentType.integer(16, 30000000))
                .executes(ctx -> {
                    if (eventRunning) stopEvent();
                    overworldRadius = IntegerArgumentType.getInteger(ctx, "radius");
                    eventStartRadius = overworldRadius;
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
                    String msg = eventRunning ? String.format(Locale.ROOT, "Ивент идёт: тик %d/%d. Текущие радиусы: Верхний мир %.1f, Незер %.2f.", eventTick, INTRO_TICKS + MOVE_TICKS, currentOverworldRadius(), currentOverworldRadius()/8.0)
                        : String.format(Locale.ROOT, "Ивент не запущен. Радиусы границы: Верхний мир %.1f, Незер %.2f.", overworldRadius, overworldRadius/8.0);
                    ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                    return 1;
                }))));
    }


    private static int startEvent(CommandSourceStack source) {
        if (eventRunning) {
            source.sendFailure(Component.literal("Ивент уже запущен. Используй /wbe event restart."));
            return 0;
        }
        eventStartRadius = overworldRadius;
        eventTick = 0;
        eventRunning = true;
        source.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§3§lГраница пробуждается...§r Начинается низкий гул."), false);
        playCue(source.getServer(), 0.65f, 0.55f);
        return 1;
    }


    private static int restartEvent(CommandSourceStack source) {
        eventStartRadius = overworldRadius;
        eventTick = 0;
        eventRunning = true;
        source.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§b§lИвент границы перезапущен.§r Гул возвращается..."), false);
        playCue(source.getServer(), 0.9f, 0.65f);
        return 1;
    }

    private static void playCue(MinecraftServer server, float volume, float pitch) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.playSound(SoundEvents.AMBIENT_CAVE, volume, pitch);
    }

    private static void stopEvent() {
        if (eventRunning) overworldRadius = eventStartRadius;
        eventRunning = false;
        eventTick = 0;
    }

    private static double currentOverworldRadius() {
        if (!eventRunning) return overworldRadius;
        if (eventTick <= INTRO_TICKS) return eventStartRadius;
        double progress = Math.min(1.0, (eventTick - INTRO_TICKS) / (double) MOVE_TICKS);
        // Smoothstep: easing starts and ends gently while the actual enforced radius changes every tick.
        double eased = progress * progress * (3.0 - 2.0 * progress);
        return eventStartRadius + RETREAT_BLOCKS * eased;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !eventRunning) return;
        eventTick++;
        overworldRadius = currentOverworldRadius();
        // Send each tick; client-side interpolation prevents stepped movement.
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) BorderNetwork.sendRadius(p, overworldRadius);
        if (eventTick == INTRO_TICKS || eventTick == INTRO_TICKS + 100 || eventTick == INTRO_TICKS + 250)
            playCue(event.getServer(), 0.45f, 0.75f + (eventTick - INTRO_TICKS) / 500.0f);
        if (eventTick == INTRO_TICKS + MOVE_TICKS - 1) playCue(event.getServer(), 1.0f, 0.9f);
        if (eventTick >= INTRO_TICKS + MOVE_TICKS) {
            overworldRadius = eventStartRadius + RETREAT_BLOCKS;
            eventRunning = false;
            eventTick = INTRO_TICKS + MOVE_TICKS;
            event.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§bГраница отступила. Ивент завершён."), false);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer p) BorderNetwork.sendRadius(p, overworldRadius); }

    @SubscribeEvent
    public static void enforceCustomBorder(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
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
