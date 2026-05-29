package com.griefprevention.customitems;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Verknüpft jeden Crystal-Chunk-Claim mit seinem Beacon-Claim.
 *
 * Persistenz: claimChunks.yml
 *   links:
 *     "<beaconClaimId>": [crystalClaimId, ...]
 *
 * Beide Richtungen werden im RAM gehalten; die Vorwärts-Map
 * (beacon → children) ist die einzige, die gespeichert wird.
 * Die Rückwärts-Map wird beim Laden daraus rekonstruiert.
 */
public class ClaimChunkStorage
{
    private final JavaPlugin plugin;
    private final File       file;

    // beaconClaimId → Menge der Crystal-Claim-IDs
    private final Map<Long, Set<Long>> beaconToChildren = new ConcurrentHashMap<>();
    // crystalClaimId → beaconClaimId (Rückwärts-Index)
    private final Map<Long, Long>      childToBeacon    = new ConcurrentHashMap<>();

    public ClaimChunkStorage(@NotNull JavaPlugin plugin)
    {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "claimChunks.yml");
    }

    // ── Persistenz ────────────────────────────────────────────────────────────

    public void load()
    {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        if (!yml.isConfigurationSection("links")) return;

        for (String beaconKey : yml.getConfigurationSection("links").getKeys(false))
        {
            try
            {
                long beaconId = Long.parseLong(beaconKey);
                List<?> raw   = yml.getList("links." + beaconKey, List.of());
                Set<Long> children = ConcurrentHashMap.newKeySet();
                for (Object obj : raw)
                {
                    if (!(obj instanceof Number n)) continue;
                    long childId = n.longValue();
                    children.add(childId);
                    childToBeacon.put(childId, beaconId);
                }
                if (!children.isEmpty())
                    beaconToChildren.put(beaconId, children);
            }
            catch (NumberFormatException ignored) {}
        }
    }

    private void save()
    {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<Long, Set<Long>> entry : beaconToChildren.entrySet())
        {
            List<Long> list = new ArrayList<>(entry.getValue());
            yml.set("links." + entry.getKey(), list);
        }
        try { yml.save(file); }
        catch (IOException e) { plugin.getLogger().log(Level.WARNING, "claimChunks.yml konnte nicht gespeichert werden", e); }
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /** Registriert einen Beacon-Claim (ohne Crystal-Kinder). */
    public void registerBeacon(long beaconClaimId)
    {
        beaconToChildren.putIfAbsent(beaconClaimId, ConcurrentHashMap.newKeySet());
        save();
    }

    /** Entfernt einen Beacon und all seine Crystal-Kinder aus der Map. */
    public void unregisterBeacon(long beaconClaimId)
    {
        Set<Long> children = beaconToChildren.remove(beaconClaimId);
        if (children != null) children.forEach(childToBeacon::remove);
        save();
    }

    /** Verknüpft einen neuen Crystal-Claim mit seinem Beacon. */
    public void linkCrystal(long beaconClaimId, long crystalClaimId)
    {
        beaconToChildren.computeIfAbsent(beaconClaimId,
            k -> ConcurrentHashMap.newKeySet()).add(crystalClaimId);
        childToBeacon.put(crystalClaimId, beaconClaimId);
        save();
    }

    /** Entfernt die Verknüpfung eines einzelnen Crystal-Claims. */
    public void unlinkCrystal(long crystalClaimId)
    {
        Long beaconId = childToBeacon.remove(crystalClaimId);
        if (beaconId != null)
        {
            Set<Long> children = beaconToChildren.get(beaconId);
            if (children != null) { children.remove(crystalClaimId); if (children.isEmpty()) beaconToChildren.remove(beaconId); }
        }
        save();
    }

    // ── Abfragen ─────────────────────────────────────────────────────────────

    /** True wenn claimId ein registrierter Beacon-Claim ist. */
    public boolean isBeaconClaim(long claimId)
    {
        return beaconToChildren.containsKey(claimId);
    }

    /** Gibt die ID des übergeordneten Beacons zurück, oder null. */
    public @Nullable Long getBeaconId(long crystalClaimId)
    {
        return childToBeacon.get(crystalClaimId);
    }

    /** Gibt alle Crystal-Claim-IDs zurück, die zu diesem Beacon gehören. */
    public @NotNull Set<Long> getChildren(long beaconClaimId)
    {
        return Collections.unmodifiableSet(
            beaconToChildren.getOrDefault(beaconClaimId, Set.of()));
    }

    /**
     * Liefert alle GP-Claim-Objekte der Crystal-Chunks eines Beacons.
     * Lookups werden über die Owner-UUID des Beacon-Claims gemacht.
     */
    public @NotNull List<Claim> getCrystalClaims(@NotNull GriefPrevention plugin,
                                                  @NotNull Claim beaconClaim)
    {
        Set<Long> ids = getChildren(beaconClaim.getID());
        if (ids.isEmpty()) return List.of();
        if (beaconClaim.ownerID == null) return List.of();

        List<Claim> playerClaims = plugin.dataStore
            .getPlayerData(beaconClaim.ownerID).getClaims();
        Map<Long, Claim> byId = new HashMap<>();
        for (Claim c : playerClaims) byId.put(c.getID(), c);

        List<Claim> result = new ArrayList<>();
        for (Long id : ids) { Claim c = byId.get(id); if (c != null) result.add(c); }
        return result;
    }
}
