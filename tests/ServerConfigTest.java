import com.yutadigital.herobrine.core.ServerConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;

public final class ServerConfigTest {
    private static int checks;
    private static void check(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }
    private static void restricted(Properties p, String name) {
        check(p.getProperty("require-permission").equals("true"), name + " restricts access");
        check(p.getProperty("allow-terrain").equals("false"), name + " blocks terrain");
        check(p.getProperty("destructive-lightning").equals("false"), name + " blocks lightning");
        check(p.getProperty("allow-player-combat").equals("false"), name + " blocks PvP");
        check(p.getProperty("allowed-uuids", "").isEmpty(), name + " clears partial allowlist");
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("herobrine-config-test-");
        ArrayList<String> warnings = new ArrayList<>();
        try {
            Path file = root.resolve("herobrine-ultimate-server.properties");
            Properties fresh = ServerConfig.load(root, warnings::add);
            check(fresh.getProperty("require-permission").equals("false"), "Fresh config remains non-OP");
            check(fresh.getProperty("allow-terrain").equals("true"), "Fresh terrain default preserved");
            check(Files.isRegularFile(file), "Fresh config saved");
            Files.writeString(file, "destructive-lightning=false\n");
            Properties legacy = ServerConfig.load(root, warnings::add);
            check(legacy.getProperty("require-permission", "false").equals("false"), "Valid legacy default preserved");
            check(legacy.getProperty("allow-terrain", "true").equals("true"), "Legacy terrain default preserved");
            String restricted = "require-permission=true\nallow-terrain=false\nallowed-uuids=00000000-0000-0000-0000-000000000001\n";
            Files.writeString(file, restricted);
            Properties first = ServerConfig.load(root, warnings::add);
            check(first.getProperty("require-permission").equals("true"), "Valid restriction retained");
            check(first.getProperty("allowed-uuids").endsWith("0001"), "Valid allowlist retained");
            Files.writeString(file, "require-permission=false\nallow-terrain=true\n");
            Properties reopened = ServerConfig.load(root, warnings::add);
            check(reopened.getProperty("require-permission").equals("false"), "Next load sees edited permission");
            check(reopened.getProperty("allow-terrain").equals("true"), "Next load sees edited terrain policy");
            check(warnings.isEmpty(), "Valid files do not warn");
            String malformed = restricted + "broken=" + (char) 92 + "uZZZZ\n";
            Files.writeString(file, malformed);
            restricted(ServerConfig.load(root, warnings::add), "Malformed Unicode");
            check(Files.readString(file).equals(malformed), "Broken config not overwritten");
            for (String key : new String[]{"require-permission", "destructive-lightning", "allow-player-combat", "allow-terrain"}) {
                Files.writeString(file, key + "=treu\n");
                restricted(ServerConfig.load(root, warnings::add), "Invalid " + key);
            }
            Files.delete(file);
            Files.createDirectory(file);
            restricted(ServerConfig.load(root, warnings::add), "Non-readable directory as config");
            check(warnings.size() == 6, "Each failure is reported");
            Files.delete(file);
            Path parentFile = root.resolve("not-a-directory");
            Files.writeString(parentFile, "x");
            restricted(ServerConfig.load(parentFile, warnings::add), "Unusable config directory");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
        System.out.println("PASS: " + checks + " server configuration assertions");
    }
}
