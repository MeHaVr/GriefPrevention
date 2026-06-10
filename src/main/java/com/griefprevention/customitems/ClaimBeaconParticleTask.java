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

import java.util.Map;
import java.util.UUID;

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
        for (Map.Entry<String, UUID> entry : beaconStorage.getAll().entrySet())
        {
            Location loc = parseKey(entry.getKey());
            if (loc == null) continue;

            if (!hasNearbyPlayer(loc)) continue;

            Claim claim = plugin.dataStore.getClaimAt(loc, false, null);
            if (claim == null) continue;
            if (!flagsStorage.getFlag(claim.getID(), ClaimFlagsStorage.FLAG_PARTICLES)) continue;

            ClaimParticleEffect.spawnAtBeacon(loc, flagsStorage.getParticleType(claim.getID()));
        }
    }

    private static boolean hasNearbyPlayer(@NotNull Location loc)
    {
        for (Player p : loc.getWorld().getPlayers())
            if (p.getLocation().distanceSquared(loc) <= SPAWN_RADIUS_SQ) return true;
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
