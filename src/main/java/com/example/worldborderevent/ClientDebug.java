package com.example.worldborderevent;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Locale;

/** Клиентская отладка: /wbedebug — снимок состояния в чат и в latest.log; /wbedebug log true — писать в лог раз в секунду. */
@Mod.EventBusSubscriber(modid = WorldBorderEventMod.MODID, value = Dist.CLIENT)
public final class ClientDebug {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean logging = false;
    private static int counter = 0;

    private ClientDebug() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("wbedebug")
                .executes(ctx -> {
                    String s = snapshot();
                    for (String line : s.split("\n")) ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                    LOGGER.info("[WBE debug]\n{}", s);
                    return 1;
                })
                .then(Commands.literal("log").then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                    logging = BoolArgumentType.getBool(ctx, "on");
                    ctx.getSource().sendSuccess(() -> Component.literal("WBE: запись состояния в latest.log " + (logging ? "включена" : "выключена")), false);
                    return 1;
                })))
                .then(Commands.literal("hide").then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                    ClientBorderRenderer.hideWall = BoolArgumentType.getBool(ctx, "on");
                    ctx.getSource().sendSuccess(() -> Component.literal("WBE: отрисовка стены " + (ClientBorderRenderer.hideWall ? "ВЫКЛЮЧЕНА (проверка: пропал ли артефакт на экране)" : "включена")), false);
                    return 1;
                })))
                .then(Commands.literal("trace").then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                    ClientBorderRenderer.trace = BoolArgumentType.getBool(ctx, "on");
                    ctx.getSource().sendSuccess(() -> Component.literal("WBE: трассировка рендера в latest.log " + (ClientBorderRenderer.trace ? "включена" : "выключена")), false);
                    return 1;
                }))));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (!logging || e.phase != TickEvent.Phase.END || ++counter % 20 != 0) return;
        LOGGER.info("[WBE debug]\n{}", snapshot());
    }

    static String snapshot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return "нет мира";
        boolean nether = mc.level.dimension() == Level.NETHER;
        double r = ClientBorderState.getRadius(nether, 1f);
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT, "Измерение: %s, состояние получено: %s, ивент-тик: %.1f, завершён: %s%n",
                mc.level.dimension().location(), ClientBorderState.hasState(), ClientBorderState.eventTick(), ClientBorderState.isFinished()));
        b.append(String.format(Locale.ROOT, "Радиус: %.1f, видимость стены: %.2f, яркость: %.2f, эффектов: %d%n",
                r, ClientBorderState.visibility(), ClientBorderState.brightness(), ClientBorderState.ripples().size()));
        b.append(String.format(Locale.ROOT, "Игрок: %.1f %.1f %.1f. До стен: +X %.1f, -X %.1f, +Z %.1f, -Z %.1f%n", px, py, pz, r - px, r + px, r - pz, r + pz));
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getLookAngle();
        for (int axis = 0; axis < 2; axis++) {
            double o = axis == 0 ? eye.x : eye.z, d = axis == 0 ? look.x : look.z;
            if (Math.abs(d) < 1.0e-6) continue;
            double dist = (Math.copySign(r, d) - o) / d;
            if (dist > 0) {
                Vec3 h = eye.add(look.scale(dist));
                b.append(String.format(Locale.ROOT, "Взгляд пересекает стену %s на расстоянии %.1f (y=%.1f)%n", axis == 0 ? (d > 0 ? "+X" : "-X") : (d > 0 ? "+Z" : "-Z"), dist, h.y));
            }
        }
        b.append("Рендер (последний кадр): ").append(ClientBorderRenderer.lastInfo).append(ClientBorderRenderer.hideWall ? " [стена скрыта]" : "");
        return b.toString();
    }
}
