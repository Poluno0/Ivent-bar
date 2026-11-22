package com.example.worldborderevent;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер -> клиент: радиус (Верхний мир), тик ивента (-1 = нет) и флаг «ивент завершён». */
public class BorderStatePacket {
    private final double radius;
    private final int tick;
    private final boolean finished;

    public BorderStatePacket(double radius, int tick, boolean finished) {
        this.radius = radius;
        this.tick = tick;
        this.finished = finished;
    }

    public BorderStatePacket(FriendlyByteBuf b) {
        radius = b.readDouble();
        tick = b.readInt();
        finished = b.readBoolean();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeDouble(radius);
        b.writeInt(tick);
        b.writeBoolean(finished);
    }

    public void handle(Supplier<NetworkEvent.Context> s) {
        var c = s.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBorderState.applyState(radius, tick, finished)));
        c.setPacketHandled(true);
    }
}
