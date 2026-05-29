package com.griefprevention.customitems;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * MariaDB/MySQL-Verbindung für Custom-Claim-Daten.
 * Liest dieselbe database.properties wie GriefPrevention.
 * Fällt automatisch auf YAML zurück wenn keine DB konfiguriert ist.
 */
public class ClaimDatabase
{
    private static ClaimDatabase instance;

    private final GriefPrevention plugin;
    private @Nullable HikariDataSource pool;
    private boolean available = false;

    public ClaimDatabase(@NotNull GriefPrevention plugin)
    {
        this.plugin = plugin;
        instance = this;
        connect();
    }

    public static @Nullable ClaimDatabase getInstance() { return instance; }
    public boolean isAvailable() { return available; }

    // ------- Init -------

    private void connect()
    {
        String url = "", user = "", pass = "";

        // 1. database.properties (gleicher Pfad wie GP)
        File propsFile = new File(plugin.getDataFolder(), "database.properties");
        if (propsFile.exists())
        {
            try (FileReader reader = new FileReader(propsFile))
            {
                Properties props = new Properties();
                props.load(reader);
                url  = props.getProperty("jdbcUrl",   "");
                user = props.getProperty("username",  "");
                pass = props.getProperty("password",  "");
            }
            catch (IOException e)
            {
                plugin.getLogger().warning("[ClaimDB] Fehler beim Lesen von database.properties: " + e.getMessage());
            }
        }

        // 2. Fallback: Legacy-Config
        if (url.isBlank())
        {
            url  = plugin.getConfig().getString("GriefPrevention.Database.URL",      "");
            user = plugin.getConfig().getString("GriefPrevention.Database.UserName", "");
            pass = plugin.getConfig().getString("GriefPrevention.Database.Password", "");
        }

        if (url.isBlank())
        {
            plugin.getLogger().info("[ClaimDB] Keine Datenbank konfiguriert – verwende YAML-Speicher.");
            return;
        }

        try
        {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(url);
            cfg.setUsername(user);
            cfg.setPassword(pass);
            cfg.setMaximumPoolSize(3);
            cfg.setConnectionTimeout(5_000);
            cfg.setPoolName("GP-Claims");
            cfg.addDataSourceProperty("cachePrepStmts", "true");
            pool = new HikariDataSource(cfg);
            createTables();
            available = true;
            plugin.getLogger().info("[ClaimDB] Datenbankverbindung hergestellt.");
        }
        catch (Exception e)
        {
            available = false;
            plugin.getLogger().warning("[ClaimDB] Verbindung fehlgeschlagen – verwende YAML: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException
    {
        try (Connection conn = pool.getConnection(); Statement stmt = conn.createStatement())
        {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS claim_beacons (" +
                "  world       VARCHAR(64)  NOT NULL," +
                "  x           INT          NOT NULL," +
                "  y           INT          NOT NULL," +
                "  z           INT          NOT NULL," +
                "  owner_uuid  VARCHAR(36)  NOT NULL," +
                "  PRIMARY KEY (world, x, y, z)" +
                ")");
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS claim_flags (" +
                "  claim_id    BIGINT       NOT NULL," +
                "  flag_name   VARCHAR(50)  NOT NULL," +
                "  flag_value  BOOLEAN      NOT NULL," +
                "  PRIMARY KEY (claim_id, flag_name)" +
                ")");
        }
    }

    // ------- Beacons -------

    /** Lädt alle Beacon-Einträge beim Start (blockiert kurz, aber nur einmal). */
    public @NotNull Map<String, UUID> loadBeacons()
    {
        Map<String, UUID> result = new HashMap<>();
        if (!available) return result;
        try (Connection conn = pool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT world, x, y, z, owner_uuid FROM claim_beacons"))
        {
            while (rs.next())
            {
                String key = rs.getString("world") + ","
                    + rs.getInt("x") + "," + rs.getInt("y") + "," + rs.getInt("z");
                result.put(key, UUID.fromString(rs.getString("owner_uuid")));
            }
        }
        catch (SQLException e)
        {
            plugin.getLogger().warning("[ClaimDB] Fehler beim Laden der Beacons: " + e.getMessage());
        }
        return result;
    }

    /** Async-Upsert eines Beacons. */
    public void saveBeacon(@NotNull String key, @NotNull UUID owner)
    {
        if (!available) return;
        String[] p = key.split(",");
        if (p.length != 4) return;
        async(() ->
        {
            try (Connection conn = pool.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO claim_beacons (world,x,y,z,owner_uuid) VALUES(?,?,?,?,?) " +
                     "ON DUPLICATE KEY UPDATE owner_uuid=?"))
            {
                ps.setString(1, p[0]);
                ps.setInt(2, Integer.parseInt(p[1]));
                ps.setInt(3, Integer.parseInt(p[2]));
                ps.setInt(4, Integer.parseInt(p[3]));
                ps.setString(5, owner.toString());
                ps.setString(6, owner.toString());
                ps.executeUpdate();
            }
            catch (SQLException e)
            {
                plugin.getLogger().warning("[ClaimDB] saveBeacon: " + e.getMessage());
            }
        });
    }

    /** Async-Löschen eines Beacons. */
    public void deleteBeacon(@NotNull String key)
    {
        if (!available) return;
        String[] p = key.split(",");
        if (p.length != 4) return;
        async(() ->
        {
            try (Connection conn = pool.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM claim_beacons WHERE world=? AND x=? AND y=? AND z=?"))
            {
                ps.setString(1, p[0]);
                ps.setInt(2, Integer.parseInt(p[1]));
                ps.setInt(3, Integer.parseInt(p[2]));
                ps.setInt(4, Integer.parseInt(p[3]));
                ps.executeUpdate();
            }
            catch (SQLException e)
            {
                plugin.getLogger().warning("[ClaimDB] deleteBeacon: " + e.getMessage());
            }
        });
    }

    /** Async-Update des Besitzers für einen Beacon-Key. */
    public void updateBeaconOwner(@NotNull String key, @NotNull UUID newOwner)
    {
        if (!available) return;
        String[] p = key.split(",");
        if (p.length != 4) return;
        async(() ->
        {
            try (Connection conn = pool.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE claim_beacons SET owner_uuid=? WHERE world=? AND x=? AND y=? AND z=?"))
            {
                ps.setString(1, newOwner.toString());
                ps.setString(2, p[0]);
                ps.setInt(3, Integer.parseInt(p[1]));
                ps.setInt(4, Integer.parseInt(p[2]));
                ps.setInt(5, Integer.parseInt(p[3]));
                ps.executeUpdate();
            }
            catch (SQLException e)
            {
                plugin.getLogger().warning("[ClaimDB] updateBeaconOwner: " + e.getMessage());
            }
        });
    }

    // ------- Flags -------

    public @NotNull Map<String, Boolean> loadFlags(long claimId)
    {
        Map<String, Boolean> result = new HashMap<>();
        if (!available) return result;
        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT flag_name, flag_value FROM claim_flags WHERE claim_id=?"))
        {
            ps.setLong(1, claimId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                result.put(rs.getString("flag_name"), rs.getBoolean("flag_value"));
        }
        catch (SQLException e)
        {
            plugin.getLogger().warning("[ClaimDB] loadFlags: " + e.getMessage());
        }
        return result;
    }

    public void setFlag(long claimId, @NotNull String flag, boolean value)
    {
        if (!available) return;
        async(() ->
        {
            try (Connection conn = pool.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO claim_flags (claim_id,flag_name,flag_value) VALUES(?,?,?) " +
                     "ON DUPLICATE KEY UPDATE flag_value=?"))
            {
                ps.setLong(1, claimId);
                ps.setString(2, flag);
                ps.setBoolean(3, value);
                ps.setBoolean(4, value);
                ps.executeUpdate();
            }
            catch (SQLException e)
            {
                plugin.getLogger().warning("[ClaimDB] setFlag: " + e.getMessage());
            }
        });
    }

    // ------- Lifecycle -------

    public void close()
    {
        if (pool != null && !pool.isClosed())
            pool.close();
        instance = null;
    }

    private void async(@NotNull Runnable task)
    {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }
}
