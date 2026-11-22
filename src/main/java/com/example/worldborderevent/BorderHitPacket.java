package com.example.worldborderevent;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент -> сервер: игрок ударил по барьеру. Сервер проверяет и рассылает эффект всем в измерении. */
public class BorderHitPacket {
    private final double x, y, z;
    private final int kind;

    public BorderHitPacket(double x, double y, double z, int kind) {
        this.x = x; this.y = y; this.z = z; this.kind = kind;
    }

    public BorderHitPacket(FriendlyByteBuf b) {
        x = b.readDouble(); y = b.readDouble(); z = b.readDouble(); kind = b.readByte();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeByte(kind);
    }

    public void handle(Supplier<NetworkEvent.Context> s) {
        var c = s.get();
        ServerPlayer p = c.getSender();
        c.enqueueWork(() -> { if (p != null) ImpactHandler.onPlayerHit(p, x, y, z, kind); });
        c.setPacketHandled(true);
    }
}
