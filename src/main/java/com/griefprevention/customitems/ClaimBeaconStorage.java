package com.griefprevention.customitems;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistenz der platzierten Claim Beacons.
 * Primär: MariaDB via ClaimDatabase (wenn verfügbar).
 * Fallback: YAML-Datei (claimBeacons.yml).
 * In-Memory-Cache für O(1)-Lookups auf dem Main Thread.
 */
public class ClaimBeaconStorage
{
    private final Plugin plugin;
    private final File yamlFile;
    private final Map<String, UUID> cache = new HashMap<>();

    public ClaimBeaconStorage(@NotNull Plugin plugin)
    {
        this.plugin   = plugin;
        this.yamlFile = new File(plugin.getDataFolder(), "claimBeacons.yml");
    }

    // ------- Laden -------

    public void load()
    {
        ClaimDatabase db = ClaimDatabase.getInstance();
        if (db != null && db.isAvailable())
        {
            cache.putAll(db.loadBeacons());
            plugin.getLogger().info("[ClaimBeaconStorage] " + cache.size() + " Beacon(s) aus DB geladen.");
        }
        else
        {
            loadFromYaml();
            plugin.getLogger().info("[ClaimBeaconStorage] " + cache.size() + " Beacon(s) aus YAML geladen.");
        }
    }

    private void loadFromYaml()
    {
        if (!yamlFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(yamlFile);
        if (!config.isConfigurationSection("beacons")) return;
        for (String key : config.getConfigurationSection("beacons").getKeys(false))
        {
            String uuidStr = config.getString("beacons." + key);
            try { cache.put(key, UUID.fromString(uuidStr)); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveYaml()
    {
        YamlConfiguration config = new YamlConfiguration();
        cache.forEach((k, v) -> config.set("beacons." + k, v.toString()));
        try { config.save(yamlFile); }
        catch (IOException e) { plugin.getLogger().severe("YAML-Speicherfehler: " + e.getMessage()); }
    }

    // ------- Zugriff -------

    public void add(@NotNull Location loc, @NotNull UUID owner)
    {
        String key = toKey(loc);
        cache.put(key, owner);
        persist(key, owner, PersistOp.SAVE);
    }

    public void remove(@NotNull Location loc)
    {
        String key = toKey(loc);
        cache.remove(key);
        persist(key, null, PersistOp.DELETE);
    }

    /**
     * Verschiebt einen Beacon von oldLoc nach newLoc.
     * Wird nach dem physischen Versetzen des Blocks aufgerufen.
     */
    public void updatePosition(@NotNull Location oldLoc, @NotNull Location newLoc)
    {
        String oldKey = toKey(oldLoc);
        UUID owner = cache.remove(oldKey);
        if (owner == null) return;
        persist(oldKey, null, PersistOp.DELETE);
        String newKey = toKey(newLoc);
        cache.put(newKey, owner);
        persist(newKey, owner, PersistOp.SAVE);
    }

    public boolean isClaimBeacon(@NotNull Location loc)
    {
        return cache.containsKey(toKey(loc));
    }

    public @Nullable UUID getOwner(@NotNull Location loc)
    {
        return cache.get(toKey(loc));
    }

    /**
     * Aktualisiert den Besitzer aller Beacons im angegebenen Chunk.
     * Wird nach einem Claim-Transfer aufgerufen.
     */
    public void updateOwnerInChunk(int chunkX, int chunkZ, @NotNull UUID newOwner)
    {
        int minX = chunkX * 16, maxX = minX + 15;
        int minZ = chunkZ * 16, maxZ = minZ + 15;
        boolean changed = false;

        for (Map.Entry<String, UUID> entry : cache.entrySet())
        {
            String[] parts = entry.getKey().split(",");
            if (parts.length != 4) continue;
            try
            {
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[3]);
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ)
                {
                    entry.setValue(newOwner);
                    persist(entry.getKey(), newOwner, PersistOp.UPDATE);
                    changed = true;
                }
            }
            catch (NumberFormatException ignored) {}
        }
        if (changed) maybeFlushYaml();
    }

    /**
     * Entfernt alle Beacons im angegebenen Chunk einer Welt.
     * Gibt die String-Keys der entfernten Einträge zurück (Format: "welt,x,y,z").
     */
    public @NotNull List<String> removeInChunk(int chunkX, int chunkZ, @NotNull String worldName)
    {
        int minX = chunkX * 16, maxX = minX + 15;
        int minZ = chunkZ * 16, maxZ = minZ + 15;
        List<String> removed = new ArrayList<>();

        Iterator<Map.Entry<String, UUID>> it = cache.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<String, UUID> entry = it.next();
            String[] parts = entry.getKey().split(",");
            if (parts.length != 4 || !parts[0].equals(worldName)) continue;
            try
            {
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[3]);
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ)
                {
                    it.remove();
                    persist(entry.getKey(), null, PersistOp.DELETE);
                    removed.add(entry.getKey());
                }
            }
            catch (NumberFormatException ignored) {}
        }
        if (!removed.isEmpty()) maybeFlushYaml();
        return removed;
    }

    /** Gibt eine Kopie aller gespeicherten Einträge zurück (für HologramManager). */
    public @NotNull Map<String, UUID> getAll()
    {
        return new HashMap<>(cache);
    }

    // ------- Interna -------

    private String toKey(@NotNull Location loc)
    {
        return loc.getWorld().getName() + ","
            + loc.getBlockX() + ","
            + loc.getBlockY() + ","
            + loc.getBlockZ();
    }

    private enum PersistOp { SAVE, DELETE, UPDATE }

    private void persist(@NotNull String key, @Nullable UUID owner, @NotNull PersistOp op)
    {
        ClaimDatabase db = ClaimDatabase.getInstance();
        if (db != null && db.isAvailable())
        {
            switch (op)
            {
                case SAVE   -> db.saveBeacon(key, owner);
                case DELETE -> db.deleteBeacon(key);
                case UPDATE -> db.updateBeaconOwner(key, owner);
            }
        }
        else
        {
            saveYaml();
        }
    }

    private void maybeFlushYaml()
    {
        ClaimDatabase db = ClaimDatabase.getInstance();
        if (db == null || !db.isAvailable()) saveYaml();
    }
}
