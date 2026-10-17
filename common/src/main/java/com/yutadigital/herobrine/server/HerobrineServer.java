package com.yutadigital.herobrine.server;

import com.yutadigital.herobrine.core.Ability;
import com.yutadigital.herobrine.core.PlayerSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** All actions are run on the server thread; packets never specify another player. */
public final class HerobrineServer {
    private static final UUID MOVE_ID = UUID.fromString("c56ba990-5a62-4c52-826d-88759e104fa1");
    private static final UUID ATTACK_ID = UUID.fromString("c56ba990-5a62-4c52-826d-88759e104fa2");
    private static final UUID DAMAGE_ID = UUID.fromString("c56ba990-5a62-4c52-826d-88759e104fa3");
    private static final String FLIGHT_OWNED = "herobrineultimate:flight_owned";
    private static final String PRIOR_MAYFLY = "herobrineultimate:prior_mayfly";
    private static final String PRIOR_FLYING = "herobrineultimate:prior_flying";
    private static final String INVISIBILITY_OWNED = "herobrineultimate:invisibility_owned";
    private static final String PRIOR_INVISIBLE = "herobrineultimate:prior_invisible";
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Set<UUID> ALLOWLIST = new HashSet<>();
    private static final Set<UUID> OFFLINE = new HashSet<>();
    private static BiConsumer<ServerPlayer, CompoundTag> sender = (player, packet) -> {};
    private static boolean requirePermission;
    private static boolean destructiveLightning;
    private static boolean playerCombat;
    private static boolean terrainEnabled = true;

    private HerobrineServer() {}

    private static final class Session {
        ServerPlayer player;
        HerobrineWorldData data;
        HerobrineWorldData.Record record;
        boolean flightApplied;
        boolean previousMayFly;
        boolean previousFlying;
        boolean invisApplied;
        boolean previousInvisible;
        boolean transformed;
        boolean mobsActive;
        boolean lastAllowed;
        long bucketTick = Long.MIN_VALUE;
        int bucketCount;
        long windowTick = Long.MIN_VALUE;
        int windowCount;
        final Map<String, Long> cooldowns = new HashMap<>();
    }

    public static void init(Path configDirectory, BiConsumer<ServerPlayer, CompoundTag> outbound) {
        sender = outbound;
        ALLOWLIST.clear();
        Properties config = new Properties();
        Path file = configDirectory.resolve("herobrine-ultimate-server.properties");
        try {
            Files.createDirectories(configDirectory);
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) { config.load(reader); }
            } else {
                config.setProperty("require-permission", "false");
                config.setProperty("allowed-uuids", "");
                config.setProperty("destructive-lightning", "false");
                config.setProperty("allow-player-combat", "false");
                config.setProperty("allow-terrain", "true");
                try (Writer writer = Files.newBufferedWriter(file)) {
                    config.store(writer, "Herobrine Ultimate: abilities are available without OP or cheats by default. Set require-permission=true to use OP/owner/UUID restrictions. Destructive actions also require GUI confirmation.");
                }
            }
        } catch (IOException | IllegalArgumentException error) {
            config.clear();
            System.err.println("[Herobrine Ultimate] Cannot read server configuration: " + error.getMessage());
        }
        for (String token : config.getProperty("allowed-uuids", "").split(",")) {
            try { if (!token.isBlank()) ALLOWLIST.add(UUID.fromString(token.trim())); }
            catch (IllegalArgumentException error) { System.err.println("[Herobrine Ultimate] Invalid allowlist UUID ignored."); }
        }
        requirePermission = Boolean.parseBoolean(config.getProperty("require-permission", "false"));
        destructiveLightning = Boolean.parseBoolean(config.getProperty("destructive-lightning", "false"));
        playerCombat = Boolean.parseBoolean(config.getProperty("allow-player-combat", "false"));
        terrainEnabled = Boolean.parseBoolean(config.getProperty("allow-terrain", "true"));
    }

    public static boolean allowed(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && (!requirePermission || player.hasPermissions(2)
                || server.isSingleplayerOwner(player.getGameProfile()) || ALLOWLIST.contains(player.getUUID()));
    }

    private static Session session(ServerPlayer player) {
        Session result = SESSIONS.get(player.getUUID());
        if (result == null) {
            result = new Session();
            result.data = HerobrineWorldData.get(player.getServer());
            result.record = result.data.player(player.getUUID());
            result.player = player;
            recoverOwnedState(result);
            result.lastAllowed = allowed(player);
            SESSIONS.put(player.getUUID(), result);
        } else if (result.player != player) {
            restore(result);
            result.player = player;
            // Respawn can copy scoreboard tags before the old player is restored.
            // Recover only ownership saved on this exact replacement player.
            recoverOwnedState(result);
        }
        return result;
    }

    private static void recoverOwnedState(Session session) {
        ServerPlayer player = session.player;
        // Player tags and native abilities share one save file. SavedData can be
        // an older snapshot; its ownership flags must never invent a restoration
        // on an untagged player after logout, respawn or an interrupted save.
        if (player.getTags().contains(FLIGHT_OWNED)) {
            session.previousMayFly = player.getTags().contains(PRIOR_MAYFLY);
            session.previousFlying = player.getTags().contains(PRIOR_FLYING);
            session.flightApplied = true;
            restoreFlight(session);
        } else if (session.record.flightOwned) {
            session.record.flightOwned = false;
            session.data.setDirty();
        }
        if (player.getTags().contains(INVISIBILITY_OWNED)) {
            session.previousInvisible = player.getTags().contains(PRIOR_INVISIBLE);
            session.invisApplied = true;
            restoreInvisibility(session);
        } else if (session.record.invisibilityOwned) {
            session.record.invisibilityOwned = false;
            session.data.setDirty();
        }
    }

    private static boolean active(ServerPlayer player, Ability ability) {
        return allowed(player) && session(player).record.settings.enabled(ability);
    }

    public static boolean god(ServerPlayer player) { return active(player, Ability.GOD); }
    /** Explicit policy: the void and /kill retain their vanilla behavior. */
    public static boolean preventDamage(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) return false;
        return god(player) || source.is(DamageTypes.FALL) && active(player, Ability.FLIGHT);
    }
    public static boolean noFall(ServerPlayer player) { return god(player) || active(player, Ability.FLIGHT); }
    public static float mining(ServerPlayer player, float baseSpeed) {
        return active(player, Ability.MOTION) ? (float) Math.min(100000.0, baseSpeed * session(player).record.settings.multiplier("mine")) : baseSpeed;
    }
    public static boolean transformed(UUID uuid) {
        Session session = SESSIONS.get(uuid);
        return session != null && active(session.player, Ability.HEROBRINE);
    }

    public static void join(ServerPlayer player) {
        OFFLINE.remove(player.getUUID());
        Session session = session(player);
        reconcile(session);
        broadcast(player.getServer());
    }
    public static void logout(ServerPlayer player) {
        OFFLINE.add(player.getUUID());
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) restore(session);
        broadcast(player.getServer());
    }
    public static void stop() {
        for (Session session : SESSIONS.values()) restore(session);
        SESSIONS.clear();
        OFFLINE.clear();
    }

    public static void tick(ServerPlayer player) {
        Session session = session(player);
        boolean permission = allowed(player);
        if (permission != session.lastAllowed) {
            session.lastAllowed = permission;
            reconcile(session);
            broadcast(player.getServer());
        }
        if (!player.isAlive()) { restore(session); return; }
        reconcile(session);
        if (permission && session.record.settings.enabled(Ability.HUNGER)) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20.0F);
            player.getFoodData().setExhaustion(0.0F);
        }
        if (noFall(player)) player.fallDistance = 0;
        if (player.tickCount % 10 == 0) updateMobs(session);
        if (player.tickCount % 100 == 0) sync(player);
    }

    private static void reconcile(Session session) {
        ServerPlayer player = session.player;
        PlayerSettings settings = session.record.settings;
        boolean allowed = allowed(player) && player.isAlive();
        boolean mobs = allowed && settings.enabled(Ability.MOB);
        if (!mobs && session.mobsActive) stopMobs(session);
        session.mobsActive = mobs;
        boolean flight = allowed && settings.enabled(Ability.FLIGHT);
        if (flight && !session.flightApplied) {
            boolean nativeFlight = player.isCreative() || player.isSpectator();
            session.previousMayFly = !nativeFlight && player.getAbilities().mayfly;
            session.previousFlying = !nativeFlight && player.getAbilities().flying;
            session.flightApplied = true;
            session.record.flightOwned = true;
            session.record.priorMayFly = session.previousMayFly;
            session.record.priorFlying = session.previousFlying;
            flagTag(player, PRIOR_MAYFLY, session.previousMayFly);
            flagTag(player, PRIOR_FLYING, session.previousFlying);
            player.addTag(FLIGHT_OWNED);
            session.data.setDirty();
            player.getAbilities().mayfly = true;
            updateAbilities(player);
        } else if (!flight && session.flightApplied) restoreFlight(session);
        else if (flight && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            updateAbilities(player);
        }
        boolean invisible = allowed && settings.enabled(Ability.INVISIBILITY);
        if (invisible && !session.invisApplied) {
            session.previousInvisible = player.isInvisible() && !player.hasEffect(MobEffects.INVISIBILITY) && !player.isSpectator();
            session.invisApplied = true;
            session.record.invisibilityOwned = true;
            session.record.priorInvisible = session.previousInvisible;
            flagTag(player, PRIOR_INVISIBLE, session.previousInvisible);
            player.addTag(INVISIBILITY_OWNED);
            session.data.setDirty();
        }
        if (invisible) player.setInvisible(true);
        else if (session.invisApplied) restoreInvisibility(session);
        boolean motion = allowed && settings.enabled(Ability.MOTION);
        boolean combat = allowed && settings.enabled(Ability.COMBAT);
        modifier(player, Attributes.MOVEMENT_SPEED, MOVE_ID, motion ? settings.multiplier("move") : 1.0);
        modifier(player, Attributes.ATTACK_SPEED, ATTACK_ID, motion ? settings.multiplier("attack") : 1.0);
        modifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, combat ? settings.multiplier("damage") : 1.0);
        boolean transform = allowed && settings.enabled(Ability.HEROBRINE);
        if (transform != session.transformed) {
            session.transformed = transform;
            if (settings.enabled(Ability.EFFECTS)) {
                player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(), 20, .4, .7, .4, .02);
                if (transform) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(player.serverLevel());
                    if (bolt != null) {
                        bolt.moveTo(player.position());
                        bolt.setVisualOnly(true);
                        player.serverLevel().addFreshEntity(bolt);
                    }
                }
            }
        }
    }

    private static void modifier(ServerPlayer player, Attribute type, UUID id, double multiplier) {
        AttributeInstance instance = player.getAttribute(type);
        if (instance == null) return;
        AttributeModifier existing = instance.getModifier(id);
        double amount = multiplier - 1.0;
        if (existing != null && Double.compare(existing.getAmount(), amount) == 0) return;
        if (existing != null) instance.removeModifier(id);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(id, "Herobrine Ultimate", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void updateAbilities(ServerPlayer player) {
        // Vanilla also recomputes invisibility here. Flight updates must not
        // erase an unrelated mod's existing invisible flag before we capture
        // or restore our own invisibility baseline.
        boolean invisible = player.isInvisible();
        player.onUpdateAbilities();
        if (invisible) player.setInvisible(true);
    }

    private static void restoreFlight(Session session) {
        ServerPlayer player = session.player;
        boolean nativeFlight = player.isCreative() || player.isSpectator();
        player.getAbilities().mayfly = nativeFlight || session.previousMayFly;
        player.getAbilities().flying = nativeFlight ? player.getAbilities().flying : session.previousFlying && session.previousMayFly;
        player.fallDistance = 0;
        updateAbilities(player);
        session.flightApplied = false;
        session.record.flightOwned = false;
        player.removeTag(FLIGHT_OWNED);
        player.removeTag(PRIOR_MAYFLY);
        player.removeTag(PRIOR_FLYING);
        session.data.setDirty();
    }
    private static void restoreInvisibility(Session session) {
        session.player.setInvisible(session.previousInvisible || session.player.hasEffect(MobEffects.INVISIBILITY) || session.player.isSpectator());
        session.invisApplied = false;
        session.record.invisibilityOwned = false;
        session.player.removeTag(INVISIBILITY_OWNED);
        session.player.removeTag(PRIOR_INVISIBLE);
        session.data.setDirty();
    }
    private static void flagTag(ServerPlayer player, String key, boolean flag) {
        if (flag) player.addTag(key); else player.removeTag(key);
    }
    private static void restore(Session session) {
        stopMobs(session);
        session.mobsActive = false;
        if (session.flightApplied) restoreFlight(session);
        if (session.invisApplied) restoreInvisibility(session);
        modifier(session.player, Attributes.MOVEMENT_SPEED, MOVE_ID, 1.0);
        modifier(session.player, Attributes.ATTACK_SPEED, ATTACK_ID, 1.0);
        modifier(session.player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, 1.0);
        session.transformed = false;
    }

    /** Packet entry point. Loader handlers must copy the bounded NBT and enqueue this on the server. */
    public static void handle(ServerPlayer player, CompoundTag packet) {
        if (player.getServer() == null || !player.getServer().isSameThread()) return;
        if (packet == null || packet.getAllKeys().size() > 8 || packet.toString().length() > 2048) return;
        for (String key : packet.getAllKeys()) {
            Tag value = packet.get(key);
            if (key.length() > 32 || value == null || value.getId() == Tag.TAG_COMPOUND || value.getId() == Tag.TAG_LIST || value.getId() == Tag.TAG_BYTE_ARRAY || value.getId() == Tag.TAG_INT_ARRAY || value.getId() == Tag.TAG_LONG_ARRAY) return;
        }
        Session session = session(player);
        long now = player.serverLevel().getGameTime();
        if (session.bucketTick != now) { session.bucketTick = now; session.bucketCount = 0; }
        if (++session.bucketCount > 12) return;
        if (session.windowTick == Long.MIN_VALUE || now < session.windowTick || now - session.windowTick >= 20) {
            session.windowTick = now; session.windowCount = 0;
        }
        if (++session.windowCount > 40) return;
        String op = packet.getString("op");
        String key = packet.getString("key");
        if (op.length() > 16 || key.length() > 64) return;
        if (op.equals("sync")) { if (cooldown(session, "sync", 20)) sync(player); return; }
        if (!allowed(player)) { tell(player, "Your server administrator must allow Herobrine abilities."); sync(player); return; }
        boolean configuring = op.equals("master") || op.equals("toggle") || op.equals("number");
        if (!configuring && (!player.isAlive() || player.isSpectator())) return;
        try {
            switch (op) {
                case "master" -> { session.record.settings.master = packet.getBoolean("flag"); changed(session); }
                case "toggle" -> { session.record.settings.set(Ability.valueOf(key), packet.getBoolean("flag")); changed(session); }
                case "number" -> { session.record.settings.multiplier(key, packet.getDouble("value")); changed(session); }
                case "lightning" -> { if (require(session, Ability.LIGHTNING, op, 5)) lightning(session, packet); }
                case "attack" -> { if (require(session, Ability.COMBAT, op, 5)) specialAttack(session, packet); }
                case "teleport" -> { if (require(session, Ability.TELEPORT, op, 10)) teleportLook(player); }
                case "save" -> { if (require(session, Ability.TELEPORT, op, 10)) saveWaypoint(session); }
                case "return" -> { if (require(session, Ability.TELEPORT, op, 10)) returnWaypoint(session); }
                case "summon" -> { if (require(session, Ability.MOB, op, 20)) summon(session, key); }
                case "rally" -> { if (require(session, Ability.MOB, op, 10)) rally(session); }
                case "dismiss" -> { if (require(session, Ability.MOB, op, 10)) dismiss(session); }
                case "day", "night", "clear", "rain", "thunder" -> { if (require(session, Ability.WORLD, "world", 10)) world(player, op); }
                case "terrain" -> { if (require(session, Ability.WORLD, op, 20)) terrain(session, packet); }
                default -> { }
            }
        } catch (IllegalArgumentException ignored) {
            // Unknown enum names and nonfinite/out-of-range values cannot escape the packet handler.
        }
    }

    private static boolean cooldown(Session session, String operation, int ticks) {
        long now = session.player.serverLevel().getGameTime();
        long previous = session.cooldowns.getOrDefault(operation, Long.MIN_VALUE / 2);
        if (now >= previous && now - previous < ticks) return false;
        session.cooldowns.put(operation, now);
        return true;
    }
    private static boolean require(Session session, Ability ability, String operation, int ticks) {
        if (!session.record.settings.enabled(ability)) { tell(session.player, "Enable " + ability.name() + " first."); return false; }
        return cooldown(session, operation, ticks);
    }
    private static void changed(Session session) {
        session.data.setDirty();
        reconcile(session);
        if (!session.record.settings.enabled(Ability.MOB)) stopMobs(session);
        broadcast(session.player.getServer());
    }
    private static void tell(ServerPlayer player, String text) { player.sendSystemMessage(Component.literal("[Herobrine] " + text)); }

    private static BlockHitResult look(ServerPlayer player, double range) {
        Vec3 from = player.getEyePosition();
        return player.serverLevel().clip(new ClipContext(from, from.add(player.getLookAngle().scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    private static void lightning(Session session, CompoundTag packet) {
        ServerPlayer player = session.player;
        BlockHitResult hit = look(player, 64);
        if (hit.getType() == HitResult.Type.MISS || !player.serverLevel().hasChunkAt(hit.getBlockPos())) return;
        boolean destructive = packet.getBoolean("flag") && destructiveLightning && packet.getBoolean("confirm");
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(player.serverLevel());
        if (lightning == null) return;
        lightning.moveTo(Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())));
        lightning.setVisualOnly(!destructive);
        lightning.setCause(player);
        player.serverLevel().addFreshEntity(lightning);
        if (packet.getBoolean("flag") && !destructive) tell(player, "Visual lightning: destructive lightning requires server permission and confirmation.");
    }

    private static LivingEntity target(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition();
        BlockHitResult block = look(player, range);
        Vec3 end = block.getType() == HitResult.Type.MISS ? start.add(player.getLookAngle().scale(range)) : block.getLocation();
        AABB search = player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1);
        LivingEntity closest = null;
        double distance = start.distanceToSqr(end);
        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class, search, e -> e != player && e.isAlive() && !e.isSpectator())) {
            var intersection = candidate.getBoundingBox().inflate(.15).clip(start, end);
            if (intersection.isEmpty()) continue;
            double squared = start.distanceToSqr(intersection.get());
            if (squared < distance) { closest = candidate; distance = squared; }
        }
        return closest;
    }

    private static void specialAttack(Session session, CompoundTag packet) {
        ServerPlayer player = session.player;
        LivingEntity target = target(player, 24);
        if (target == null || !eligibleTarget(player, target)) return;
        boolean instant = packet.getBoolean("flag");
        if (instant && !packet.getBoolean("confirm")) { tell(player, "Confirm instant attack before using it."); return; }
        float damage = instant ? Math.min(1000000F, target.getHealth() + target.getAbsorptionAmount() + 100F) : (float) Math.min(100000F, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        target.hurt(player.damageSources().playerAttack(player), damage);
        attacked(player, target);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }
    private static boolean eligibleTarget(ServerPlayer player, LivingEntity target) {
        return !target.isAlliedTo(player) && (!(target instanceof ServerPlayer other) || playerCombat && player.getServer().isPvpAllowed() && player.canHarmPlayer(other));
    }

    public static void attacked(ServerPlayer player, Entity entity) {
        if (!(entity instanceof LivingEntity target) || !active(player, Ability.MOB) || !eligibleTarget(player, target)) return;
        Session session = session(player);
        for (Mob mob : ownedLoaded(session)) if (mob != target && !mob.isAlliedTo(target)) mob.setTarget(target);
    }

    /** Keep registered ownership valid when a bound mob loads or changes dimension. */
    public static void entityLoaded(Entity entity) {
        if (!(entity instanceof Mob) || !(entity.level() instanceof ServerLevel level)) return;
        for (String tag : entity.getTags()) {
            if (!tag.startsWith("herobrine_owner:")) continue;
            try {
                UUID owner = UUID.fromString(tag.substring("herobrine_owner:".length()));
                HerobrineWorldData data = HerobrineWorldData.get(level.getServer());
                HerobrineWorldData.Record record = data.players.get(owner);
                if (record == null || !record.mobs.containsKey(entity.getUUID())) continue;
                String current = level.dimension().location().toString();
                if (!current.equals(record.mobs.get(entity.getUUID()))) {
                    record.mobs.put(entity.getUUID(), current);
                    data.setDirty();
                }
            } catch (IllegalArgumentException ignored) { }
        }
    }

    /** Called by each loader's living-death hook, including while the owner is offline. */
    public static void entityDied(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        for (String tag : entity.getTags()) {
            if (!tag.startsWith("herobrine_owner:")) continue;
            try {
                UUID owner = UUID.fromString(tag.substring("herobrine_owner:".length()));
                HerobrineWorldData data = HerobrineWorldData.get(level.getServer());
                HerobrineWorldData.Record record = data.players.get(owner);
                if (record != null && record.mobs.remove(entity.getUUID()) != null) data.setDirty();
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private static boolean safe(ServerPlayer player, ServerLevel level, Vec3 position) {
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) return false;
        BlockPos feet = BlockPos.containing(position);
        if (position.y < level.getMinBuildHeight() + 1 || position.y + player.getBbHeight() >= level.getMaxBuildHeight()) return false;
        AABB box = player.getBoundingBox().move(position.subtract(player.position()));
        if (!level.getWorldBorder().isWithinBounds(box) || !level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) return false;
        if (!level.noCollision(player, box) || level.containsAnyLiquid(box)) return false;
        // Require nearby support, including slabs/stairs at fractional heights.
        if (!level.getBlockCollisions(player, box.move(0, -.08, 0)).iterator().hasNext()) return false;
        for (BlockPos cursor : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - .1, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (hazardous(level.getBlockState(cursor))) return false;
        }
        return true;
    }
    private static boolean hazardous(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW);
    }
    private static Vec3 safeNear(ServerPlayer player, ServerLevel level, BlockPos base) {
        for (int dy : new int[]{0, 1, 2, 3, -1, -2}) {
            BlockPos floor = base.offset(0, dy - 1, 0);
            if (!level.hasChunkAt(floor)) continue;
            var shape = level.getBlockState(floor).getCollisionShape(level, floor);
            if (shape.isEmpty()) continue;
            Vec3 position = new Vec3(base.getX() + .5, floor.getY() + shape.max(Direction.Axis.Y), base.getZ() + .5);
            if (safe(player, level, position)) return position;
        }
        return null;
    }
    private static void teleportLook(ServerPlayer player) {
        BlockHitResult hit = look(player, 64);
        if (hit.getType() == HitResult.Type.MISS) { tell(player, "Aim at a solid block within 64 blocks."); return; }
        Vec3 position = safeNear(player, player.serverLevel(), hit.getBlockPos().relative(hit.getDirection()));
        if (position == null) { tell(player, "No safe loaded destination near that block."); return; }
        player.teleportTo(player.serverLevel(), position.x, position.y, position.z, player.getYRot(), player.getXRot());
        player.fallDistance = 0;
    }
    private static void saveWaypoint(Session session) {
        ServerPlayer player = session.player;
        CompoundTag waypoint = new CompoundTag();
        waypoint.putString("dimension", player.serverLevel().dimension().location().toString());
        waypoint.putDouble("x", player.getX()); waypoint.putDouble("y", player.getY()); waypoint.putDouble("z", player.getZ());
        waypoint.putFloat("yaw", player.getYRot()); waypoint.putFloat("pitch", player.getXRot());
        session.record.waypoint = waypoint;
        session.data.setDirty();
        tell(player, "Waypoint saved for this world.");
    }
    private static ServerLevel dimension(MinecraftServer server, String name) {
        ResourceLocation location = ResourceLocation.tryParse(name);
        return location == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
    }
    private static void returnWaypoint(Session session) {
        ServerPlayer player = session.player;
        CompoundTag waypoint = session.record.waypoint;
        if (waypoint == null) { tell(player, "Save a waypoint first."); return; }
        ServerLevel level = dimension(player.getServer(), waypoint.getString("dimension"));
        Vec3 position = new Vec3(waypoint.getDouble("x"), waypoint.getDouble("y"), waypoint.getDouble("z"));
        float yaw = waypoint.getFloat("yaw");
        float pitch = waypoint.getFloat("pitch");
        if (level == null || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !safe(player, level, position)) { tell(player, "Waypoint must be safe and its chunks already loaded."); return; }
        player.teleportTo(level, position.x, position.y, position.z, yaw, pitch);
        player.fallDistance = 0;
    }

    private static void summon(Session session, String key) {
        ServerPlayer player = session.player;
        if (session.record.mobs.size() >= 16) { tell(player, "Maximum 16 bound mobs. Dismiss them before summoning more."); return; }
        EntityType<? extends Mob> type = switch (key) {
            case "wolf" -> EntityType.WOLF;
            case "cat" -> EntityType.CAT;
            case "parrot" -> EntityType.PARROT;
            case "iron_golem" -> EntityType.IRON_GOLEM;
            default -> null;
        };
        if (type == null) { tell(player, "Available mobs: wolf, cat, parrot, iron_golem."); return; }
        Vec3 position = safeNear(player, player.serverLevel(), player.blockPosition().relative(player.getDirection(), 3));
        if (position == null) return;
        Mob mob = type.create(player.serverLevel());
        if (mob == null) return;
        mob.moveTo(position.x, position.y, position.z, player.getYRot(), 0);
        if (!player.serverLevel().noCollision(mob)) return;
        mob.setPersistenceRequired();
        mob.addTag("herobrine_owner:" + player.getUUID());
        if (mob instanceof TamableAnimal tame) { tame.tame(player); tame.setOrderedToSit(false); }
        if (mob instanceof IronGolem golem) golem.setPlayerCreated(true);
        if (player.serverLevel().addFreshEntity(mob)) {
            session.record.mobs.put(mob.getUUID(), player.serverLevel().dimension().location().toString());
            session.data.setDirty();
        }
    }
    private static ArrayList<Mob> ownedLoaded(Session session) {
        ArrayList<Mob> result = new ArrayList<>();
        for (var entry : session.record.mobs.entrySet()) {
            ServerLevel level = dimension(session.player.getServer(), entry.getValue());
            if (level == null) continue;
            Entity entity = level.getEntity(entry.getKey());
            if (entity instanceof Mob mob && mob.getTags().contains("herobrine_owner:" + session.player.getUUID())) result.add(mob);
        }
        return result;
    }
    private static void updateMobs(Session session) {
        boolean follow = allowed(session.player) && session.record.settings.enabled(Ability.MOB);
        for (Mob mob : ownedLoaded(session)) {
            if (!mob.isAlive()) { session.record.mobs.remove(mob.getUUID()); session.data.setDirty(); continue; }
            if (!follow) { mob.setTarget(null); mob.getNavigation().stop(); continue; }
            if (mob.getTarget() == session.player || mob.getTarget() != null && mob.getTarget().isAlliedTo(session.player)) mob.setTarget(null);
            if (mob.level() == session.player.level() && mob.getTarget() == null && mob.distanceToSqr(session.player) > 16 && mob.distanceToSqr(session.player) < 4096) mob.getNavigation().moveTo(session.player, 1.2);
        }
    }
    private static void stopMobs(Session session) {
        for (Mob mob : ownedLoaded(session)) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }
    private static void rally(Session session) {
        for (Mob mob : ownedLoaded(session)) {
            mob.setTarget(null);
            if (mob.level() == session.player.level() && mob.distanceToSqr(session.player) < 4096) mob.getNavigation().moveTo(session.player, 1.3);
        }
        tell(session.player, "Loaded bound mobs are following you.");
    }
    private static void dismiss(Session session) {
        for (Mob mob : ownedLoaded(session)) { mob.discard(); session.record.mobs.remove(mob.getUUID()); }
        session.data.setDirty();
        tell(session.player, "Loaded bound mobs dismissed; unloaded mobs remain registered.");
    }
    private static void world(ServerPlayer player, String op) {
        ServerLevel level = player.serverLevel();
        switch (op) {
            case "day" -> level.setDayTime(level.getDayTime() - Math.floorMod(level.getDayTime(), 24000L) + 1000);
            case "night" -> level.setDayTime(level.getDayTime() - Math.floorMod(level.getDayTime(), 24000L) + 13000);
            case "clear" -> level.setWeatherParameters(6000, 0, false, false);
            case "rain" -> level.setWeatherParameters(0, 6000, true, false);
            case "thunder" -> level.setWeatherParameters(0, 6000, true, true);
            default -> { }
        }
    }
    private static void terrain(Session session, CompoundTag packet) {
        ServerPlayer player = session.player;
        if (!terrainEnabled) { tell(player, "Terrain editing is disabled by the server."); return; }
        if (!packet.getBoolean("confirm")) { tell(player, "Confirm terrain editing; keep a world backup."); return; }
        BlockHitResult hit = look(player, 6);
        if (hit.getType() == HitResult.Type.MISS) return;
        int count = 0;
        BlockPos center = hit.getBlockPos();
        for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            BlockPos position = cursor.immutable();
            if (player.distanceToSqr(Vec3.atCenterOf(position)) > 64 || !player.serverLevel().hasChunkAt(position) || !player.serverLevel().getWorldBorder().isWithinBounds(position)) continue;
            var state = player.serverLevel().getBlockState(position);
            if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(player.serverLevel(), position) < 0 || !player.serverLevel().mayInteract(player, position)) continue;
            // Use the ordinary player game-mode path so loader protection hooks can veto each block.
            if (player.gameMode.destroyBlock(position)) count++;
        }
        tell(player, "Terrain edit: " + count + " blocks processed (maximum 27).");
    }

    private static ListTag visuals(MinecraftServer server) {
        ListTag list = new ListTag();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (OFFLINE.contains(player.getUUID()) || !active(player, Ability.HEROBRINE) || !player.isAlive()) continue;
            CompoundTag entry = new CompoundTag(); entry.putUUID("uuid", player.getUUID()); list.add(entry);
        }
        return list;
    }
    private static void sync(ServerPlayer player) {
        if (OFFLINE.contains(player.getUUID())) return;
        CompoundTag packet = new CompoundTag();
        packet.putString("kind", "state");
        packet.put("settings", HerobrineWorldData.settingsTag(session(player).record.settings));
        packet.putBoolean("allowed", allowed(player));
        packet.put("visuals", visuals(player.getServer()));
        sender.accept(player, packet);
    }
    private static void broadcast(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player);
    }
}
