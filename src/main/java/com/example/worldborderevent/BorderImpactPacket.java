package com.example.worldborderevent;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер -> клиенты измерения: попадание по барьеру (1 = рука, 2 = меч/инструмент, 3 = снаряд). */
public class BorderImpactPacket {
    private final double x, y, z;
    private final int kind;

    public BorderImpactPacket(double x, double y, double z, int kind) {
        this.x = x; this.y = y; this.z = z; this.kind = kind;
    }

    public BorderImpactPacket(FriendlyByteBuf b) {
        x = b.readDouble(); y = b.readDouble(); z = b.readDouble(); kind = b.readByte();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeByte(kind);
    }

    public void handle(Supplier<NetworkEvent.Context> s) {
        var c = s.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBorderState.addImpact(x, y, z, kind)));
        c.setPacketHandled(true);
    }
}
