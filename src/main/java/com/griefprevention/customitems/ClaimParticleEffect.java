package com.griefprevention.customitems;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.jetbrains.annotations.NotNull;

public class ClaimParticleEffect
{
    private static final int    POINTS = 16;
    private static final double RADIUS = 1.3;

    public static void play(@NotNull Location center, @NotNull String typeName)
    {
        Particle particle;
        try { particle = Particle.valueOf(typeName); }
        catch (IllegalArgumentException e) { particle = Particle.FLAME; }

        Location base = center.clone().add(0, 0.5, 0);
        for (int i = 0; i < POINTS; i++)
        {
            double angle = (2 * Math.PI / POINTS) * i;
            base.getWorld().spawnParticle(
                particle,
                base.clone().add(Math.cos(angle) * RADIUS, 0, Math.sin(angle) * RADIUS),
                3, 0, 0, 0, 0);
        }
    }
}
