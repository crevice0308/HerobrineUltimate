package com.yutadigital.herobrine.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

/** Loads server policy without Minecraft dependencies so failure paths are testable. */
public final class ServerConfig {
    private ServerConfig() {}

    public static Properties load(Path directory, Consumer<String> warning) {
        Properties config = new Properties();
        Path file = directory.resolve("herobrine-ultimate-server.properties");
        try {
            Files.createDirectories(directory);
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
            for (String key : new String[]{"require-permission", "destructive-lightning", "allow-player-combat", "allow-terrain"}) {
                String value = config.getProperty(key);
                if (value != null && !value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("Invalid boolean for " + key);
                }
            }
        } catch (IOException | IllegalArgumentException error) {
            // Never turn a broken restricted server into an unrestricted server.
            config.clear();
            config.setProperty("require-permission", "true");
            config.setProperty("allow-terrain", "false");
            config.setProperty("destructive-lightning", "false");
            config.setProperty("allow-player-combat", "false");
            warning.accept("Cannot read server configuration; using restrictive fallback: " + error.getMessage());
        }
        return config;
    }
}
