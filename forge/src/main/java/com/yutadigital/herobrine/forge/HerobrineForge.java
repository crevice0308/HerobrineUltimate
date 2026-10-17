package com.yutadigital.herobrine.forge;

import com.yutadigital.herobrine.server.HerobrineServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod(HerobrineForge.ID)
public final class HerobrineForge {
    public static final String ID = "herobrineultimate";
    private static final String PROTOCOL = "1";
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public HerobrineForge() {
        NETWORK.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buf) -> buf.writeNbt(packet.tag()))
                .decoder(buf -> new Request(buf.readableBytes() <= 8192 ? buf.readNbt() : null))
                .consumerMainThread((packet, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null && packet.tag() != null) HerobrineServer.handle(player, packet.tag());
                    context.get().setPacketHandled(true);
                }).add();
        NETWORK.messageBuilder(State.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buf) -> buf.writeNbt(packet.tag()))
                .decoder(buf -> new State(buf.readNbt()))
                .consumerMainThread((packet, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeClient.accept(packet.tag(), context.get().getNetworkManager()));
                    context.get().setPacketHandled(true);
                }).add();
        HerobrineServer.init(FMLPaths.CONFIGDIR.get(), (player, state) ->
                NETWORK.send(PacketDistributor.PLAYER.with(() -> player), new State(state)));
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::join);
        MinecraftForge.EVENT_BUS.addListener(this::logout);
        MinecraftForge.EVENT_BUS.addListener(this::damage);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::death);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::entityLoaded);
        MinecraftForge.EVENT_BUS.addListener(this::mining);
        MinecraftForge.EVENT_BUS.addListener(this::attack);
        MinecraftForge.EVENT_BUS.addListener(this::stop);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ForgeClient::register);
    }

    private void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player)
            HerobrineServer.tick(player);
    }
    private void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HerobrineServer.join(player);
    }
    private void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HerobrineServer.logout(player);
    }
    private void damage(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && HerobrineServer.preventDamage(player, event.getSource()))
            event.setCanceled(true);
    }
    private void entityLoaded(EntityJoinLevelEvent event) {
        if (!event.isCanceled() && !event.getLevel().isClientSide()) HerobrineServer.entityLoaded(event.getEntity());
    }
    private void death(LivingDeathEvent event) {
        if (!event.isCanceled()) HerobrineServer.entityDied(event.getEntity());
    }
    private void mining(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof ServerPlayer player)
            event.setNewSpeed(HerobrineServer.mining(player, event.getNewSpeed()));
    }
    private void attack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HerobrineServer.attacked(player, event.getTarget());
    }
    private void stop(ServerStoppingEvent event) { HerobrineServer.stop(); }
    public record Request(CompoundTag tag) { }
    public record State(CompoundTag tag) { }
}
