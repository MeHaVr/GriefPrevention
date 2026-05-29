package com.griefprevention.customitems;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Verwaltet schwebende Hologramme (DecentHolograms) über platzierten Claim Beacons.
 * Ist DecentHolograms nicht installiert, werden alle Methoden lautlos übersprungen.
 */
public class ClaimHologramManager
{
    private final GriefPrevention plugin;
    private final boolean enabled;

    public ClaimHologramManager(@NotNull GriefPrevention plugin)
    {
        this.plugin  = plugin;
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
        if (enabled)
            plugin.getLogger().info("[Claims] DecentHolograms erkannt – Hologramme aktiviert.");
    }

    /** Erstellt ein Hologramm über einem platzierten Beacon. */
    public void createHologram(@NotNull Location beaconLoc,
                               @NotNull String ownerName,
                               int chunkX, int chunkZ)
    {
        if (!enabled) return;
        try
        {
            String name = holoName(beaconLoc);
            // Evtl. vorhandenes (Alt-)Hologramm entfernen
            if (DHAPI.getHologram(name) != null)
                DHAPI.removeHologram(name);

            Location top = beaconLoc.clone().add(0.5, 2.3, 0.5);
            Hologram holo = DHAPI.createHologram(name, top);
            DHAPI.addHologramLine(holo, "&8【 &9&l" + ownerName + "'s Claim &8】");
            DHAPI.addHologramLine(holo, "&8Chunk &e" + chunkX + "&8, &e" + chunkZ);
        }
        catch (Exception e)
        {
            plugin.getLogger().warning("[ClaimHolo] createHologram: " + e.getMessage());
        }
    }

    /** Setzt beide Zeilen eines Hologramms (unterstützt & Farbcodes). */
    public void setLines(@NotNull Location beaconLoc, @NotNull String line1, @NotNull String line2)
    {
        if (!enabled) return;
        try
        {
            Hologram holo = DHAPI.getHologram(holoName(beaconLoc));
            if (holo == null) return;
            DHAPI.setHologramLine(holo, 0, line1);
            DHAPI.setHologramLine(holo, 1, line2);
        }
        catch (Exception e)
        {
            plugin.getLogger().warning("[ClaimHolo] setLines: " + e.getMessage());
        }
    }

    /**
     * Zeigt oder versteckt ein Hologramm.
     * Hide = Hologramm wird entfernt; Show = neu erstellt mit gespeichertem Owner/Chunk.
     */
    public void setVisible(@NotNull Location beaconLoc, boolean visible,
                           @NotNull String ownerName)
    {
        if (!enabled) return;
        if (visible)
            createHologram(beaconLoc, ownerName,
                beaconLoc.getBlockX() >> 4, beaconLoc.getBlockZ() >> 4);
        else
            removeHologram(beaconLoc);
    }

    /** Gibt zurück ob ein Hologramm für diesen Beacon existiert (= sichtbar). */
    public boolean isVisible(@NotNull Location beaconLoc)
    {
        if (!enabled) return false;
        try { return DHAPI.getHologram(holoName(beaconLoc)) != null; }
        catch (Exception e) { return false; }
    }

    /** Entfernt das Hologramm eines Beacons. */
    public void removeHologram(@NotNull Location beaconLoc)
    {
        if (!enabled) return;
        try
        {
            DHAPI.removeHologram(holoName(beaconLoc));
        }
        catch (Exception e)
        {
            plugin.getLogger().warning("[ClaimHolo] removeHologram: " + e.getMessage());
        }
    }

    /** Aktualisiert den Besitzernamen (z.B. nach einem Transfer). */
    public void updateOwner(@NotNull Location beaconLoc, @NotNull String newOwnerName)
    {
        if (!enabled) return;
        try
        {
            Hologram holo = DHAPI.getHologram(holoName(beaconLoc));
            if (holo == null) return;
            DHAPI.setHologramLine(holo, 0, "&8【 &9&l" + newOwnerName + "'s Claim &8】");
        }
        catch (Exception e)
        {
            plugin.getLogger().warning("[ClaimHolo] updateOwner: " + e.getMessage());
        }
    }

    /**
     * Stellt beim Serverstart alle Hologramme aus dem BeaconStorage wieder her.
     * Wird async ausgeführt (OfflinePlayer-Lookup), Hologramm-Erstellung zurück auf Main Thread.
     */
    public void recreateAll(@NotNull Map<String, UUID> beacons)
    {
        if (!enabled || beacons.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            record HoloData(Location loc, String ownerName, int cx, int cz) {}
            java.util.List<HoloData> data = new java.util.ArrayList<>();

            for (Map.Entry<String, UUID> entry : beacons.entrySet())
            {
                String[] parts = entry.getKey().split(",");
                if (parts.length != 4) continue;
                try
                {
                    World world = Bukkit.getWorld(parts[0]);
                    if (world == null) continue;
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    String owner = Bukkit.getOfflinePlayer(entry.getValue()).getName();
                    if (owner == null) owner = entry.getValue().toString().substring(0, 8);
                    data.add(new HoloData(new Location(world, x, y, z), owner, x >> 4, z >> 4));
                }
                catch (NumberFormatException ignored) {}
            }

            Bukkit.getScheduler().runTask(plugin, () ->
                data.forEach(d -> createHologram(d.loc(), d.ownerName(), d.cx(), d.cz())));
        });
    }

    private @NotNull String holoName(@NotNull Location loc)
    {
        return "cb_" + loc.getWorld().getName()
            + "_" + loc.getBlockX()
            + "_" + loc.getBlockY()
            + "_" + loc.getBlockZ();
    }
}
