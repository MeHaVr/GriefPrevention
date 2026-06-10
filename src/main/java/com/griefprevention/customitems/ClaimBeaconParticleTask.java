package com.griefprevention.customitems;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spawnt jede Sekunde einen Partikel-Ring über platzierten Claim Beacons,
 * sofern ein Spieler in der Nähe ist und das Partikel-Flag aktiv ist.
 *
 * Performance: Die Spieler-Positionen werden einmal pro Lauf und Welt
 * eingesammelt; teure Claim-Lookups passieren nur für Beacons, bei denen
 * tatsächlich ein Spieler in Reichweite steht.
 */
public class ClaimBeaconParticleTask extends BukkitRunnable
{
    private static final double SPAWN_RADIUS_SQ = 48.0 * 48.0;

    private final GriefPrevention    plugin;
    private final ClaimBeaconStorage beaconStorage;
    private final ClaimFlagsStorage  flagsStorage;

    public ClaimBeaconParticleTask(@NotNull GriefPrevention plugin,
                                   @NotNull ClaimBeaconStorage beaconStorage,
                                   @NotNull ClaimFlagsStorage flagsStorage)
    {
        this.plugin        = plugin;
        this.beaconStorage = beaconStorage;
        this.flagsStorage  = flagsStorage;
    }

    @Override
    public void run()
    {
        // Spieler-Positionen einmal pro Lauf einsammeln (statt pro Beacon)
        Map<World, List<Location>> playersByWorld = new HashMap<>();

        for (Map.Entry<String, UUID> entry : beaconStorage.getAll().entrySet())
        {
            Location loc = parseKey(entry.getKey());
            if (loc == null) continue;

            List<Location> players = playersByWorld.computeIfAbsent(loc.getWorld(),
                ClaimBeaconParticleTask::collectPlayerLocations);
            if (!hasNearbyPlayer(loc, players)) continue;

            Claim claim = plugin.dataStore.getClaimAt(loc, true, null);
            if (claim == null) continue;
            if (!flagsStorage.getFlag(claim.getID(), ClaimFlagsStorage.FLAG_PARTICLES)) continue;

            ClaimParticleEffect.spawnAtBeacon(loc, flagsStorage.getParticleType(claim.getID()));
        }
    }

    private static @NotNull List<Location> collectPlayerLocations(@NotNull World world)
    {
        List<Location> locations = new ArrayList<>();
        for (Player p : world.getPlayers())
            locations.add(p.getLocation());
        return locations;
    }

    private static boolean hasNearbyPlayer(@NotNull Location loc, @NotNull List<Location> players)
    {
        for (Location p : players)
            if (p.distanceSquared(loc) <= SPAWN_RADIUS_SQ) return true;
        return false;
    }

    private static @Nullable Location parseKey(@NotNull String key)
    {
        String[] parts = key.split(",");
        if (parts.length != 4) return null;
        try
        {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            return new Location(world,
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]));
        }
        catch (NumberFormatException e) { return null; }
    }
}
