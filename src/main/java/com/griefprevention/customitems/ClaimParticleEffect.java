package com.griefprevention.customitems;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.jetbrains.annotations.NotNull;

public class ClaimParticleEffect
{
    private static final int    POINTS        = 24;
    private static final double ENTER_RADIUS  = 1.6;
    private static final double BEACON_RADIUS = 1.2;
    private static final int    REPEATS       = 3;
    private static final int    DELAY         = 4;

    /** Wird beim Betreten eines Claims abgespielt (Ring um den Spieler). */
    public static void play(@NotNull GriefPrevention plugin, @NotNull Location center, @NotNull String typeName)
    {
        Particle particle = resolve(typeName);
        for (int r = 0; r < REPEATS; r++)
        {
            int delayTicks = r * DELAY;
            plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> spawnRing(center.clone().add(0, 0.3, 0), particle, ENTER_RADIUS, 5), delayTicks);
        }
    }

    /** Wird dauerhaft vom BeaconParticleTask aufgerufen (Ring über dem Beacon-Block). */
    public static void spawnAtBeacon(@NotNull Location beaconLoc, @NotNull String typeName)
    {
        // Beacon-Block-Mitte: +0.5 X/Z, +1.4 Y (knapp über dem Block)
        Location center = beaconLoc.clone().add(0.5, 1.4, 0.5);
        spawnRing(center, resolve(typeName), BEACON_RADIUS, 2);
    }

    private static void spawnRing(@NotNull Location center, @NotNull Particle particle,
                                  double radius, int count)
    {
        for (int i = 0; i < POINTS; i++)
        {
            double angle = (2 * Math.PI / POINTS) * i;
            center.getWorld().spawnParticle(
                particle,
                center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
                count, 0, 0, 0, 0);
        }
    }

    private static @NotNull Particle resolve(@NotNull String typeName)
    {
        try { return Particle.valueOf(typeName); }
        catch (IllegalArgumentException e) { return Particle.FLAME; }
    }
}
