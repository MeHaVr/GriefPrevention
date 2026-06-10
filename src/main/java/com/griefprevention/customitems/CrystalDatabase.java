package com.griefprevention.customitems;

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
 * Nutzt dieselbe MariaDB-Verbindung wie GriefPrevention wenn konfiguriert,
 * fällt sonst auf YAML (crystalBalances.yml im GP-Ordner) zurück.
 */
public class CrystalDatabase
{
    private static @Nullable CrystalDatabase instance;

    public static @Nullable CrystalDatabase getInstance() { return instance; }

    private final Plugin plugin;

    private @Nullable HikariDataSource pool;
    private boolean dbMode = false;

    private File yamlFile;
    private YamlConfiguration yaml;

    private final ConcurrentMap<UUID, Double> cache = new ConcurrentHashMap<>();

    public CrystalDatabase(@NotNull Plugin plugin)
    {
        this.plugin = plugin;
        instance = this;
        init();
    }

    // ------- Init -------

    private void init()
    {
        yamlFile = new File(plugin.getDataFolder(), "crystalBalances.yml");

        String url  = readDbUrl();
        String user = readDbUser();
        String pass = readDbPass();

        if (!url.isBlank())
            tryConnectDb(url, user, pass);

        if (dbMode)
            plugin.getLogger().info("[Crystal] Datenbank-Modus (MariaDB/MySQL).");
        else
        {
            plugin.getLogger().info("[Crystal] YAML-Modus (crystalBalances.yml).");
            loadFromYaml();
        }

        plugin.getLogger().info("[Crystal] " + cache.size() + " Konto/Konten geladen.");
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
            cfg.setPoolName("CrystalDB");
            cfg.addDataSourceProperty("cachePrepStmts", "true");
            pool = new HikariDataSource(cfg);
            createTable();
            loadFromDb();
            dbMode = true;
        }
        catch (Exception e)
        {
            plugin.getLogger().warning("[Crystal] DB-Verbindung fehlgeschlagen, nutze YAML: " + e.getMessage());
            if (pool != null && !pool.isClosed()) pool.close();
            pool = null;
        }
    }

    // ------- GP-Konfiguration lesen -------

    private @NotNull String readDbUrl()
    {
        File props = new File(plugin.getDataFolder(), "database.properties");
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
        File cfg = new File(plugin.getDataFolder(), "config.yml");
        if (cfg.exists())
            return YamlConfiguration.loadConfiguration(cfg)
                .getString("GriefPrevention.Database.URL", "").trim();
        return "";
    }

    private @NotNull String readDbUser()
    {
        File props = new File(plugin.getDataFolder(), "database.properties");
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
        File cfg = new File(plugin.getDataFolder(), "config.yml");
        if (cfg.exists())
            return YamlConfiguration.loadConfiguration(cfg)
                .getString("GriefPrevention.Database.UserName", "").trim();
        return "";
    }

    private @NotNull String readDbPass()
    {
        File props = new File(plugin.getDataFolder(), "database.properties");
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
        File cfg = new File(plugin.getDataFolder(), "config.yml");
        if (cfg.exists())
            return YamlConfiguration.loadConfiguration(cfg)
                .getString("GriefPrevention.Database.Password", "").trim();
        return "";
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

    private void upsertDb(@NotNull UUID uuid)
    {
        async(() ->
        {
            // Kontostand erst beim Schreiben aus dem Cache lesen: So überschreibt
            // ein verspätet ausgeführter Task nie einen neueren Wert mit einem alten.
            double balance = cache.getOrDefault(uuid, 0.0);
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
                plugin.getLogger().warning("[Crystal] upsertDb: " + e.getMessage());
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
            plugin.getLogger().warning("[Crystal] getTop: " + e.getMessage());
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
        // Snapshot synchron erstellen – YamlConfiguration ist nicht thread-safe.
        String snapshot = yaml.saveToString();
        async(() ->
        {
            try { java.nio.file.Files.writeString(yamlFile.toPath(), snapshot); }
            catch (IOException e)
            {
                plugin.getLogger().warning("[Crystal] Fehler beim Speichern von crystalBalances.yml: " + e.getMessage());
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

    public @NotNull List<TopEntry> getTop(int limit)
    {
        if (dbMode)
        {
            List<TopEntry> result = new ArrayList<>();
            loadTopFromDb(result, limit);
            return result;
        }
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
        instance = null;
    }

    // ------- Intern -------

    private void persist(@NotNull UUID uuid, double balance)
    {
        if (dbMode) upsertDb(uuid);
        else        saveToYaml(uuid, balance);
    }

    private void async(@NotNull Runnable task)
    {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    public record TopEntry(@NotNull UUID uuid, double balance) {}
}
