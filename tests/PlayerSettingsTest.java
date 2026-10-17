import com.yutadigital.herobrine.core.Ability;
import com.yutadigital.herobrine.core.PlayerSettings;
import java.util.Properties;

/** Pure Java contract checks: no loader or Minecraft mocks. */
public final class PlayerSettingsTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void rejects(Runnable call, String message) {
        boolean rejected = false;
        try { call.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }
    public static void main(String[] args) {
        PlayerSettings settings = new PlayerSettings();
        check(settings.master, "Master should start on so a selected ability activates immediately");
        for (Ability ability : Ability.values()) check(!settings.enabled(ability), "No powers activate on a new world");
        settings.set(Ability.FLIGHT, true);
        settings.set(Ability.HUNGER, true);
        settings.master = false;
        check(!settings.enabled(Ability.FLIGHT), "F6 suspends enabled powers");
        check(settings.selected(Ability.FLIGHT) && settings.selected(Ability.HUNGER), "F6 preserves individual selections");
        settings.set(Ability.HUNGER, false);
        settings.master = true;
        check(settings.enabled(Ability.FLIGHT) && !settings.enabled(Ability.HUNGER), "F6 restores only the still selected powers");

        String[] keys = {"move", "mine", "attack", "damage"};
        double[] caps = {10, 100, 10, 1000};
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            settings.multiplier(key, 1e100);
            check(settings.multiplier(key) == caps[i], key + " has a server-safe upper cap");
            settings.multiplier(key, -1e100);
            check(settings.multiplier(key) == 1, key + " never underflows the baseline");
            rejects(() -> settings.multiplier(key, Double.NaN), key + " rejects NaN");
            rejects(() -> settings.multiplier(key, Double.POSITIVE_INFINITY), key + " rejects infinity");
            rejects(() -> settings.multiplier(key, Double.NEGATIVE_INFINITY), key + " rejects negative infinity");
            settings.multiplier(key, caps[i] / 2);
        }
        rejects(() -> settings.multiplier("untrusted-packet-key", 9), "Unknown wire keys must be rejected");
        check(settings.multiplier("untrusted-packet-key") == 1, "Unknown read keys retain a neutral baseline");

        settings.set(Ability.HEROBRINE, true);
        settings.set(Ability.EFFECTS, true);
        settings.master = false;
        Properties persisted = settings.toProperties();
        PlayerSettings reloaded = PlayerSettings.fromProperties(persisted);
        check(!reloaded.master, "Master suspension survives saving and loading");
        for (Ability ability : Ability.values()) check(reloaded.selected(ability) == settings.selected(ability), "Ability selections survive a world reload");
        for (String key : keys) check(reloaded.multiplier(key) == settings.multiplier(key), "Multiplier survives a world reload");
        reloaded.master = true;
        check(reloaded.enabled(Ability.FLIGHT) && reloaded.enabled(Ability.HEROBRINE), "Suspended persisted abilities restore on F6");
        reloaded.set(Ability.FLIGHT, false);
        check(settings.selected(Ability.FLIGHT), "Different players/settings instances remain independent");

        Properties corrupt = new Properties();
        corrupt.setProperty("move", "NaN");
        corrupt.setProperty("mine", "999999999999");
        corrupt.setProperty("attack", "not a number");
        corrupt.setProperty("damage", "-100");
        PlayerSettings repaired = PlayerSettings.fromProperties(corrupt);
        check(repaired.master, "Old saves without a master entry preserve defaults");
        check(repaired.multiplier("move") == 1 && repaired.multiplier("attack") == 1, "Corrupt values use safe defaults");
        check(repaired.multiplier("mine") == 100 && repaired.multiplier("damage") == 1, "Saved extreme values are clamped");
        persisted.setProperty("damage", "999");
        check(settings.multiplier("damage") == 500, "Exported properties are detached from live settings");
        System.out.println("PASS: " + assertions + " PlayerSettings assertions (defaults, F6, caps, hostile values, persistence, independence)");
    }
}
