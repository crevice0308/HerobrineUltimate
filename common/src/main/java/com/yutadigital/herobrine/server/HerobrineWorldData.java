package com.yutadigital.herobrine.server;

import com.yutadigital.herobrine.core.Ability;
import com.yutadigital.herobrine.core.PlayerSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Kept in the overworld, so settings follow players across dimensions and restarts. */
final class HerobrineWorldData extends SavedData {
    static final String ID = "herobrine_ultimate";
    final Map<UUID, Record> players = new HashMap<>();

    static final class Record {
        PlayerSettings settings = new PlayerSettings();
        CompoundTag waypoint;
        final Map<UUID, String> mobs = new HashMap<>();
        boolean flightOwned;
        boolean priorMayFly;
        boolean priorFlying;
        boolean invisibilityOwned;
        boolean priorInvisible;
    }

    static HerobrineWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HerobrineWorldData::load, HerobrineWorldData::new, ID);
    }

    Record player(UUID uuid) {
        Record record = players.get(uuid);
        if (record == null) {
            record = new Record();
            players.put(uuid, record);
            setDirty();
        }
        return record;
    }

    static CompoundTag settingsTag(PlayerSettings settings) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("master", settings.master);
        for (Ability ability : Ability.values()) tag.putBoolean(ability.name(), settings.selected(ability));
        for (String key : new String[]{"move", "mine", "attack", "damage"}) tag.putDouble(key, settings.multiplier(key));
        return tag;
    }

    static PlayerSettings readSettings(CompoundTag tag) {
        PlayerSettings result = new PlayerSettings();
        result.master = !tag.contains("master") || tag.getBoolean("master");
        for (Ability ability : Ability.values()) result.set(ability, tag.getBoolean(ability.name()));
        for (String key : new String[]{"move", "mine", "attack", "damage"}) {
            try { if (tag.contains(key)) result.multiplier(key, tag.getDouble(key)); }
            catch (IllegalArgumentException ignored) { }
        }
        return result;
    }

    static HerobrineWorldData load(CompoundTag tag) {
        HerobrineWorldData result = new HerobrineWorldData();
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            if (!entry.hasUUID("uuid")) continue;
            Record record = new Record();
            record.settings = readSettings(entry.getCompound("settings"));
            CompoundTag restore = entry.getCompound("restore");
            record.flightOwned = restore.getBoolean("flight");
            record.priorMayFly = restore.getBoolean("mayfly");
            record.priorFlying = restore.getBoolean("flying");
            record.invisibilityOwned = restore.getBoolean("invisibility");
            record.priorInvisible = restore.getBoolean("invisible");
            if (entry.contains("waypoint", Tag.TAG_COMPOUND)) record.waypoint = entry.getCompound("waypoint").copy();
            ListTag mobs = entry.getList("mobs", Tag.TAG_COMPOUND);
            for (int j = 0; j < mobs.size() && record.mobs.size() < 16; j++) {
                CompoundTag mob = mobs.getCompound(j);
                String dimension = mob.getString("dimension");
                if (mob.hasUUID("uuid") && dimension.length() <= 256 && dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
                    record.mobs.put(mob.getUUID("uuid"), dimension);
                }
            }
            result.players.put(entry.getUUID("uuid"), record);
        }
        return result;
    }

    @Override public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        players.forEach((uuid, record) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            entry.put("settings", settingsTag(record.settings));
            CompoundTag restore = new CompoundTag();
            restore.putBoolean("flight", record.flightOwned);
            restore.putBoolean("mayfly", record.priorMayFly);
            restore.putBoolean("flying", record.priorFlying);
            restore.putBoolean("invisibility", record.invisibilityOwned);
            restore.putBoolean("invisible", record.priorInvisible);
            entry.put("restore", restore);
            if (record.waypoint != null) entry.put("waypoint", record.waypoint.copy());
            ListTag mobs = new ListTag();
            record.mobs.forEach((mobUuid, dimension) -> {
                CompoundTag mob = new CompoundTag();
                mob.putUUID("uuid", mobUuid);
                mob.putString("dimension", dimension);
                mobs.add(mob);
            });
            entry.put("mobs", mobs);
            list.add(entry);
        });
        root.put("players", list);
        root.putInt("version", 1);
        return root;
    }
}
