package com.example.worldborderevent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BorderNetwork {
    private static final String V = "2";
    public static final SimpleChannel C = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(WorldBorderEventMod.MODID, "main"))
            .networkProtocolVersion(() -> V).clientAcceptedVersions(V::equals).serverAcceptedVersions(V::equals)
            .simpleChannel();
    private static boolean done;

    private BorderNetwork() {}

    public static void register() {
        if (done) return;
        done = true;
        C.messageBuilder(BorderStatePacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BorderStatePacket::encode).decoder(BorderStatePacket::new).consumerMainThread(BorderStatePacket::handle).add();
        C.messageBuilder(BorderImpactPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BorderImpactPacket::encode).decoder(BorderImpactPacket::new).consumerMainThread(BorderImpactPacket::handle).add();
        C.messageBuilder(BorderHitPacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BorderHitPacket::encode).decoder(BorderHitPacket::new).consumerMainThread(BorderHitPacket::handle).add();
    }

    public static void sendState(ServerPlayer p, double radius, int tick, boolean finished) {
        C.send(PacketDistributor.PLAYER.with(() -> p), new BorderStatePacket(radius, tick, finished));
    }

    public static void broadcastState(double radius, int tick, boolean finished) {
        C.send(PacketDistributor.ALL.noArg(), new BorderStatePacket(radius, tick, finished));
    }

    public static void broadcastImpact(ServerLevel level, double x, double y, double z, int kind) {
        C.send(PacketDistributor.DIMENSION.with(level::dimension), new BorderImpactPacket(x, y, z, kind));
    }
}
