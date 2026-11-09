package com.cnethuka.kegare.database;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Random;
import java.util.function.Function;

/**
 * {@code config/kegare-database.toml} (DESIGN §3.33). Read by our own code on the server only and never registered as a config:
 * {@code kegare-server.toml} is a SERVER config, which NeoForge sends to every client that joins, so nothing here may ever go in it.
 * {@link #toString} leaves the password out, so a stray log line can't carry it.
 */
public record DatabaseConfig(boolean enabled, String url, String username, String password, int poolSize, String serverId,
                             int connectTimeoutSeconds, int socketTimeoutSeconds, int saveIntervalSeconds, int shutdownTimeoutSeconds,
                             int leaseWaitSeconds, int leaseStaleSeconds, int heartbeatSeconds, int pollSeconds) {
    public static final String FILE_NAME = "kegare-database.toml";
    public static final String PASSWORD_ENV = "KEGARE_DB_PASSWORD";

    @Override
    public String toString() {
        return "DatabaseConfig[enabled=" + enabled + ", serverId=" + serverId + ", poolSize=" + poolSize + "]";
    }

    /** Reads the file, writing it first if it isn't there (off, with an empty password). */
    public static DatabaseConfig load(Path configDir, Function<String, String> environment) throws IOException {
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            Files.createDirectories(configDir);
            Files.writeString(file, template(), StandardCharsets.UTF_8);
        }
        CommentedConfig toml = new TomlParser().parse(file, FileNotFoundAction.THROW_ERROR);
        String password = toml.getOrElse("password", "");
        String fromEnvironment = environment.apply(PASSWORD_ENV);
        if (fromEnvironment != null && !fromEnvironment.isEmpty()) password = fromEnvironment;
        return new DatabaseConfig(toml.getOrElse("enabled", false), toml.getOrElse("url", ""), toml.getOrElse("username", ""), password,
                positive(toml, "poolSize", 4), toml.getOrElse("serverId", "server"), positive(toml, "connectTimeoutSeconds", 5),
                positive(toml, "socketTimeoutSeconds", 15), positive(toml, "saveIntervalSeconds", 5), positive(toml, "shutdownTimeoutSeconds", 20),
                positive(toml, "leaseWaitSeconds", 15), positive(toml, "leaseStaleSeconds", 45), positive(toml, "heartbeatSeconds", 10),
                positive(toml, "pollSeconds", 2));
    }

    private static int positive(CommentedConfig toml, String key, int fallback) {
        Number value = toml.getOrElse(key, (Number) fallback);
        return Math.max(1, value.intValue());
    }

    private static String template() {
        byte[] id = new byte[4];
        new Random().nextBytes(id);
        return """
                # Kegare's optional Postgres storage (DESIGN section 3.33; the README has a setup guide). Read on the server only: this file is
                # never sent to a client. Off by default: with it off, Kegare's data stays in the world's files and no connection is ever opened.
                enabled = false
                url = "jdbc:postgresql://127.0.0.1:5432/kegare"
                username = "kegare"
                # Leave empty and set the environment variable KEGARE_DB_PASSWORD instead if you can: it wins over this.
                password = ""
                # Connections this server may hold open.
                poolSize = 4
                # Names this server. Every server sharing a database needs its own.
                serverId = "server-%s"
                connectTimeoutSeconds = 5
                socketTimeoutSeconds = 15
                # How often each online player's data is checked for changes and saved.
                saveIntervalSeconds = 5
                # On server stop: how long to wait for saves to finish before writing what's left to a local file.
                shutdownTimeoutSeconds = 20
                # On joining: how long to wait for another server to finish saving this player and let go.
                leaseWaitSeconds = 15
                # A server that hasn't checked in for this long counts as dead, and its players can join elsewhere.
                leaseStaleSeconds = 45
                heartbeatSeconds = 10
                # How often this server looks for what other servers have done (unique traits).
                pollSeconds = 2
                """.formatted(HexFormat.of().formatHex(id));
    }
}
