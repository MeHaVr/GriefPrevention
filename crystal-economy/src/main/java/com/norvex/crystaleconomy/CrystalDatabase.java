package com.norvex.crystaleconomy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Speichert Crystal-Kontostände.
 *
 * Wenn GriefPrevention eine Datenbank konfiguriert hat (plugins/GriefPrevention/database.properties
 * oder GriefPrevention.Database.* in config.yml), wird dieselbe MariaDB/MySQL-Verbindung genutzt
 * und die Tabelle crystal_balances dort angelegt.
 *
 * Ohne Datenbank-Konfiguration wird die YAML-Datei crystalBalances.yml im Plugin-Ordner verwendet –
 * genau wie GriefPrevention seinen FlatFileDataStore nutzt.
 */
public class CrystalDatabase
{
    private final Plugin plugin;

    private @Nullable HikariDataSource pool;
    private boolean dbMode = false;

    // YAML-Fallback
    private File           yamlFile;
    private YamlConfiguration yaml;

    // In-Memory-Cache: uuid → balance (für beide Modi)
    private final ConcurrentMap<UUID, Double> cache = new ConcurrentHashMap<>();

    public CrystalDatabase(@NotNull Plugin plugin)
    {
        this.plugin = plugin;
        init();
    }

    // ------- Init -------

    private void init()
    {
        yamlFile = new File(plugin.getDataFolder(), "crystalBalances.yml");

        String url;
        String user;
        String pass;

        // Eigene config.yml hat Vorrang (database.host/port/name/user/password)
        String ownUrl = readFromOwnConfig();
        if (ownUrl != null)
        {
            url  = ownUrl;
            user = plugin.getConfig().getString("database.user", "").trim();
            pass = plugin.getConfig().getString("database.password", "").trim();
        }
        else
        {
            // Fallback: GriefPrevention-Konfiguration (database.properties / config.yml)
            url  = readDbUrl();
            user = readDbUser();
            pass = readDbPass();
        }

        if (!url.isBlank())
        {
            tryConnectDb(url, user, pass);
        }

        if (dbMode)
        {
            plugin.getLogger().info("[CrystalDB] Datenbank-Modus aktiv (MariaDB/MySQL).");
        }
        else
        {
            plugin.getLogger().info("[CrystalDB] Kein Datenbank-Eintrag gefunden – nutze YAML-Speicher.");
            loadFromYaml();
        }

        plugin.getLogger().info("[CrystalDB] " + cache.size() + " Konto/Konten geladen.");
    }

    /** Liest database.host/port/name aus der eigenen config.yml und baut daraus eine JDBC-URL. */
    private @Nullable String readFromOwnConfig()
    {
        String host = plugin.getConfig().getString("database.host", "").trim();
        if (host.isBlank()) return null;
        int    port = plugin.getConfig().getInt("database.port", 3306);
        String name = plugin.getConfig().getString("database.name", "economy").trim();
        return "jdbc:mysql://" + host + ":" + port + "/" + name
            + "?useSSL=false&allowPublicKeyRetrieval=true";
    }

    private void tryConnectDb(@NotNull String url, @NotNull String user, @NotNull String pass)
    {
        try
        {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(url);
            cfg.setUsername(user);
            cfg.setPassword(pass);
            cfg.setMaximumPoolSize(2);
            cfg.setConnectionTimeout(5_000);
            cfg.setPoolName("CrystalEconomy");
            cfg.addDataSourceProperty("cachePrepStmts", "true");
            pool = new HikariDataSource(cfg);
            createTable();
            loadFromDb();
            dbMode = true;
        }
        catch (Exception e)
        {
            plugin.getLogger().warning("[CrystalDB] Datenbankverbindung fehlgeschlagen, nutze YAML: " + e.getMessage());
            if (pool != null && !pool.isClosed()) pool.close();
            pool = null;
        }
    }

    // ------- GP-Datenbank-Konfiguration lesen -------

    private @NotNull String readDbUrl()
    {
        // 1. plugins/GriefPrevention/database.properties (gleicher Weg wie ClaimDatabase in GP)
        File props = new File(gpFolder(), "database.properties");
        if (props.exists())
        {
            try (FileReader r = new FileReader(props))
            {
                Properties p = new Properties();
                p.load(r);
                String url = p.getProperty("jdbcUrl", "").trim();
                if (!url.isBlank()) return url;
            }
            catch (IOException ignored) {}
        }

        // 2. GP config.yml Legacy-Pfad
        File cfg = new File(gpFolder(), "config.yml");
        if (cfg.exists())
        {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(cfg);
            return c.getString("GriefPrevention.Database.URL", "").trim();
        }
        return "";
    }

    private @NotNull String readDbUser()
    {
        File props = new File(gpFolder(), "database.properties");
        if (props.exists())
        {
            try (FileReader r = new FileReader(props))
            {
                Properties p = new Properties();
                p.load(r);
                return p.getProperty("username", "").trim();
            }
            catch (IOException ignored) {}
        }
        File cfg = new File(gpFolder(), "config.yml");
        if (cfg.exists())
            return YamlConfiguration.loadConfiguration(cfg)
                .getString("GriefPrevention.Database.UserName", "").trim();
        return "";
    }

    private @NotNull String readDbPass()
    {
        File props = new File(gpFolder(), "database.properties");
        if (props.exists())
        {
            try (FileReader r = new FileReader(props))
            {
                Properties p = new Properties();
                p.load(r);
                return p.getProperty("password", "").trim();
            }
            catch (IOException ignored) {}
        }
        File cfg = new File(gpFolder(), "config.yml");
        if (cfg.exists())
            return YamlConfiguration.loadConfiguration(cfg)
                .getString("GriefPrevention.Database.Password", "").trim();
        return "";
    }

    private @NotNull File gpFolder()
    {
        return new File(plugin.getDataFolder().getParentFile(), "GriefPrevention");
    }

    // ------- Datenbank-Modus -------

    private void createTable() throws SQLException
    {
        try (Connection conn = pool.getConnection(); Statement stmt = conn.createStatement())
        {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS crystal_balances (" +
                "  uuid    VARCHAR(36) NOT NULL PRIMARY KEY," +
                "  balance DOUBLE      NOT NULL DEFAULT 0.0" +
                ")");
        }
    }

    private void loadFromDb() throws SQLException
    {
        try (Connection conn = pool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT uuid, balance FROM crystal_balances"))
        {
            while (rs.next())
            {
                try { cache.put(UUID.fromString(rs.getString("uuid")), rs.getDouble("balance")); }
                catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void upsertDb(@NotNull UUID uuid, double balance)
    {
        async(() ->
        {
            try (Connection conn = pool.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO crystal_balances (uuid, balance) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE balance = ?"))
            {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, balance);
                ps.setDouble(3, balance);
                ps.executeUpdate();
            }
            catch (SQLException e)
            {
                plugin.getLogger().warning("[CrystalDB] upsertDb: " + e.getMessage());
            }
        });
    }

    private void loadTopFromDb(@NotNull List<TopEntry> result, int limit)
    {
        if (!dbMode || pool == null) return;
        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT uuid, balance FROM crystal_balances ORDER BY balance DESC LIMIT ?"))
        {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                try { result.add(new TopEntry(UUID.fromString(rs.getString("uuid")), rs.getDouble("balance"))); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        catch (SQLException e)
        {
            plugin.getLogger().warning("[CrystalDB] getTop: " + e.getMessage());
        }
    }

    // ------- YAML-Modus -------

    private void loadFromYaml()
    {
        yaml = yamlFile.exists()
            ? YamlConfiguration.loadConfiguration(yamlFile)
            : new YamlConfiguration();

        for (String key : yaml.getKeys(false))
        {
            try { cache.put(UUID.fromString(key), yaml.getDouble(key, 0.0)); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveToYaml(@NotNull UUID uuid, double balance)
    {
        if (yaml == null) yaml = new YamlConfiguration();
        yaml.set(uuid.toString(), balance);
        async(() ->
        {
            try { yaml.save(yamlFile); }
            catch (IOException e)
            {
                plugin.getLogger().warning("[CrystalDB] crystalBalances.yml konnte nicht gespeichert werden: " + e.getMessage());
            }
        });
    }

    private void deleteFromYaml(@NotNull UUID uuid)
    {
        if (yaml == null) return;
        yaml.set(uuid.toString(), null);
        async(() ->
        {
            try { yaml.save(yamlFile); }
            catch (IOException e)
            {
                plugin.getLogger().warning("[CrystalDB] Fehler beim Speichern von crystalBalances.yml: " + e.getMessage());
            }
        });
    }

    // ------- Öffentliche API -------

    public boolean hasAccount(@NotNull UUID uuid)
    {
        return cache.containsKey(uuid);
    }

    public boolean createAccount(@NotNull UUID uuid)
    {
        if (cache.containsKey(uuid)) return false;
        cache.put(uuid, 0.0);
        persist(uuid, 0.0);
        return true;
    }

    public double getBalance(@NotNull UUID uuid)
    {
        return cache.getOrDefault(uuid, 0.0);
    }

    /** Atomares Withdraw – gibt true zurück wenn genug Guthaben vorhanden war. */
    public synchronized boolean withdraw(@NotNull UUID uuid, double amount)
    {
        double current = cache.getOrDefault(uuid, 0.0);
        if (current < amount) return false;
        double newBalance = current - amount;
        cache.put(uuid, newBalance);
        persist(uuid, newBalance);
        return true;
    }

    public synchronized void deposit(@NotNull UUID uuid, double amount)
    {
        double newBalance = cache.getOrDefault(uuid, 0.0) + amount;
        cache.put(uuid, newBalance);
        persist(uuid, newBalance);
    }

    public synchronized void setBalance(@NotNull UUID uuid, double amount)
    {
        double newBalance = Math.max(0.0, amount);
        cache.put(uuid, newBalance);
        persist(uuid, newBalance);
    }

    /** Top-N nach Kontostand (absteigend). */
    public @NotNull List<TopEntry> getTop(int limit)
    {
        if (dbMode)
        {
            List<TopEntry> result = new ArrayList<>();
            loadTopFromDb(result, limit);
            return result;
        }
        // YAML-Modus: aus Cache sortieren
        return cache.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(limit)
            .map(e -> new TopEntry(e.getKey(), e.getValue()))
            .toList();
    }

    // ------- Lifecycle -------

    public void close()
    {
        if (dbMode && pool != null && !pool.isClosed()) pool.close();
        // YAML wird bereits nach jeder Änderung gespeichert
    }

    // ------- Interna -------

    private void persist(@NotNull UUID uuid, double balance)
    {
        if (dbMode) upsertDb(uuid, balance);
        else        saveToYaml(uuid, balance);
    }

    private void async(@NotNull Runnable task)
    {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    public record TopEntry(@NotNull UUID uuid, double balance) {}
}
