package com.yutadigital.herobrine.client;

import com.yutadigital.herobrine.core.Ability;
import com.yutadigital.herobrine.core.PlayerSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Client state is an acknowledged server snapshot; GUI edits only send requests. */
public final class HerobrineClient {
    public static final ResourceLocation SKIN = new ResourceLocation("herobrineultimate", "textures/entity/herobrine.png");
    private static Consumer<CompoundTag> sender = ignored -> { };
    private static PlayerSettings settings = new PlayerSettings();
    private static final Set<UUID> transformed = new HashSet<>();
    private static boolean allowed;
    private static boolean hasState;
    private static boolean wasConnected;
    private static long revision;
    private static Path preferencesFile;
    private static boolean panelGlow = true;
    private static long flashUntil;

    private HerobrineClient() { }

    public static void init(Path configDir, Consumer<CompoundTag> packetSender) {
        sender = packetSender;
        preferencesFile = configDir.resolve("herobrineultimate-client.properties");
        panelGlow = true;
        if (Files.isRegularFile(preferencesFile)) {
            Properties preferences = new Properties();
            try (InputStream stream = Files.newInputStream(preferencesFile)) {
                preferences.load(stream);
                panelGlow = Boolean.parseBoolean(preferences.getProperty("panelGlow", "true"));
            } catch (IOException | IllegalArgumentException ex) {
                System.getLogger("HerobrineUltimate").log(System.Logger.Level.WARNING,
                        "Could not load client preferences; using defaults", ex);
            }
        }
    }

    public static void accept(CompoundTag packet) {
        // Loader receivers schedule this on the client thread.
        if (!"state".equals(packet.getString("kind"))) return;
        Minecraft mc = Minecraft.getInstance();
        boolean previous = mc.player != null && transformed(mc.player.getUUID());
        CompoundTag state = packet.getCompound("settings");
        PlayerSettings next = new PlayerSettings();
        next.master = state.getBoolean("master");
        for (Ability ability : Ability.values()) next.set(ability, state.getBoolean(ability.name()));
        for (String key : new String[]{"move", "mine", "attack", "damage"}) {
            if (state.contains(key, Tag.TAG_ANY_NUMERIC) && Double.isFinite(state.getDouble(key))) {
                next.multiplier(key, state.getDouble(key));
            }
        }
        settings = next;
        allowed = packet.getBoolean("allowed");
        hasState = true;
        transformed.clear();
        ListTag visuals = packet.getList("visuals", Tag.TAG_COMPOUND);
        for (int i = 0; i < visuals.size(); i++) {
            CompoundTag visual = visuals.getCompound(i);
            if (visual.hasUUID("uuid")) transformed.add(visual.getUUID("uuid"));
        }
        revision++;
        if (!previous && mc.player != null && transformed(mc.player.getUUID())
                && settings.enabled(Ability.EFFECTS) && mc.level != null) {
            flashUntil = net.minecraft.Util.getMillis() + 350;
            for (int i = 0; i < 18; i++) {
                double angle = i * Math.PI * 2 / 18;
                mc.level.addParticle(ParticleTypes.PORTAL,
                        mc.player.getX() + Math.cos(angle) * .6, mc.player.getY() + .5 + i * .06,
                        mc.player.getZ() + Math.sin(angle) * .6, 0, .06, 0);
            }
        }
    }

    public static PlayerSettings settings() { return settings; }
    public static boolean allowed() { return allowed; }
    public static boolean hasState() { return hasState; }
    public static long revision() { return revision; }
    public static boolean transformed(UUID uuid) { return transformed.contains(uuid); }
    public static boolean panelGlow() { return panelGlow; }

    /** Local rendering preference remains available even on servers which restrict abilities. */
    public static void togglePanelGlow() {
        panelGlow = !panelGlow;
        revision++;
        if (preferencesFile == null) return;
        Properties preferences = new Properties();
        preferences.setProperty("panelGlow", Boolean.toString(panelGlow));
        try {
            Files.createDirectories(preferencesFile.getParent());
            try (OutputStream stream = Files.newOutputStream(preferencesFile)) {
                preferences.store(stream, "Herobrine Ultimate client rendering preferences");
            }
        } catch (IOException ex) {
            System.getLogger("HerobrineUltimate").log(System.Logger.Level.WARNING,
                    "Could not save client preferences", ex);
        }
    }

    /** Brief bounded flash, sampled by client GUI overlays without adding per-frame particles. */
    public static int flashAlpha() {
        if (!settings.enabled(Ability.EFFECTS)) return 0;
        return (int) Math.max(0, Math.min(72, (flashUntil - net.minecraft.Util.getMillis()) * 72 / 350));
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof HerobrineScreen) mc.setScreen(null);
        else mc.setScreen(new HerobrineScreen());
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean connected = mc.player != null && mc.getConnection() != null;
        if (wasConnected && !connected) clear();
        wasConnected = connected;
    }

    public static void clear() {
        settings = new PlayerSettings();
        allowed = false;
        hasState = false;
        transformed.clear();
        flashUntil = 0;
        revision++;
    }

    public static void master() {
        CompoundTag packet = operation("master");
        packet.putBoolean("flag", !settings.master);
        send(packet);
    }

    public static void toggle(Ability ability) {
        CompoundTag packet = operation("toggle");
        packet.putString("key", ability.name());
        packet.putBoolean("flag", !settings.selected(ability));
        send(packet);
    }

    public static void number(String key, double value) {
        CompoundTag packet = operation("number");
        packet.putString("key", key);
        packet.putDouble("value", value);
        send(packet);
    }

    public static void action(String operation) { request(operation, false); }

    public static void request(String operation, boolean confirm) {
        request(operation, "", false, confirm);
    }

    public static void request(String operation, String key, boolean flag, boolean confirm) {
        CompoundTag packet = operation(operation);
        packet.putString("key", key);
        packet.putBoolean("flag", flag);
        packet.putBoolean("confirm", confirm);
        send(packet);
    }

    private static CompoundTag operation(String operation) {
        CompoundTag packet = new CompoundTag();
        packet.putString("op", operation);
        return packet;
    }

    private static void send(CompoundTag packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        if (!hasState || !allowed) {
            mc.player.displayClientMessage(Component.translatable(hasState
                    ? "herobrineultimate.message.denied" : "herobrineultimate.message.waiting"), true);
            return;
        }
        sender.accept(packet);
    }
}
