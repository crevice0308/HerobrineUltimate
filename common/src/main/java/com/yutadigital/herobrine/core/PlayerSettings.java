package com.yutadigital.herobrine.core;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Loader-independent settings. A master switch preserves the individual selections. */
public final class PlayerSettings {
    public boolean master = true;
    private final EnumSet<Ability> selections = EnumSet.noneOf(Ability.class);
    private final Map<String, Double> multipliers = new LinkedHashMap<>();

    public PlayerSettings() {
        multipliers.put("move", 1.0);
        multipliers.put("mine", 1.0);
        multipliers.put("attack", 1.0);
        multipliers.put("damage", 1.0);
    }

    public boolean enabled(Ability ability) { return master && selected(ability); }
    public boolean selected(Ability ability) { return selections.contains(ability); }
    public void set(Ability ability, boolean selected) {
        if (selected) selections.add(ability); else selections.remove(ability);
    }
    public double multiplier(String key) { return multipliers.getOrDefault(key, 1.0); }
    public static double maximum(String key) {
        return switch (key) {
            case "move", "attack" -> 10.0;
            case "mine" -> 100.0;
            case "damage" -> 1000.0;
            default -> throw new IllegalArgumentException("Unknown multiplier: " + key);
        };
    }
    public void multiplier(String key, double value) {
        double cap = maximum(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Multiplier must be finite");
        multipliers.put(key, Math.max(1.0, Math.min(cap, value)));
    }
    public Properties toProperties() {
        Properties result = new Properties();
        result.setProperty("master", Boolean.toString(master));
        for (Ability ability : Ability.values()) result.setProperty(ability.name(), Boolean.toString(selected(ability)));
        multipliers.forEach((key, value) -> result.setProperty(key, Double.toString(value)));
        return result;
    }
    public static PlayerSettings fromProperties(Properties source) {
        PlayerSettings result = new PlayerSettings();
        result.master = Boolean.parseBoolean(source.getProperty("master", "true"));
        for (Ability ability : Ability.values()) result.set(ability, Boolean.parseBoolean(source.getProperty(ability.name(), "false")));
        for (String key : result.multipliers.keySet()) {
            try { result.multiplier(key, Double.parseDouble(source.getProperty(key, "1"))); }
            catch (IllegalArgumentException ignored) { /* Corrupt values retain the safe default. */ }
        }
        return result;
    }
}
