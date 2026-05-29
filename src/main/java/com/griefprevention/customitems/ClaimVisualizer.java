package com.griefprevention.customitems;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Partikel-Visualisierung für Claim-Grenzen.
 *
 * Effekt: „Marching Ants" – zwei Farben wechseln sich alle 0.5 Blöcke ab.
 * Die Phase verschiebt sich jede Sekunde, sodass die Linie zu laufen scheint.
 *
 * Erfolg  → Gold (#FFD700) + Norvex-Cyan (#9BE1E3) – äußere Ränder, 4 Streifen
 * Naht    → Kristall-Blau (#64C8FF)                 – Innenkanten, 1 Streifen
 * Konflikt → Rot  (#E61E1E) + Orange     (#FF8200)
 */
public class ClaimVisualizer
{
    // ── Farb-Paare ────────────────────────────────────────────────────────────

    private static final Particle.DustOptions DUST_GOLD =
        new Particle.DustOptions(Color.fromRGB(255, 215,   0), 1.5f);
    private static final Particle.DustOptions DUST_CYAN =
        new Particle.DustOptions(Color.fromRGB(155, 225, 227), 1.5f);

    private static final Particle.DustOptions DUST_RED =
        new Particle.DustOptions(Color.fromRGB(230,  30,  30), 1.5f);
    private static final Particle.DustOptions DUST_ORANGE =
        new Particle.DustOptions(Color.fromRGB(255, 130,   0), 1.5f);

    // Naht-Farbe: Kristall-Blau für Innenkanten zwischen verbundenen Chunks
    private static final Particle.DustOptions DUST_SEAM =
        new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.2f);

    // ── State ─────────────────────────────────────────────────────────────────

    private static final Map<UUID, BukkitTask> activeSuccessTasks = new ConcurrentHashMap<>();

    // ── Öffentliche API ───────────────────────────────────────────────────────

    /** Einzelnen Bereich visualisieren (z.B. direkt nach einem Crystal-Claim). */
    public static void showSuccess(Plugin plugin, Player player, int x0, int z0, int x1, int z1)
    {
        showAll(plugin, player, List.of(new int[]{x0, z0, x1, z1}), DUST_GOLD, DUST_CYAN, true);
    }

    /** Mehrere Bereiche gleichzeitig visualisieren (Beacon + alle Crystal-Chunks). */
    public static void showAll(Plugin plugin, Player player, List<int[]> bounds)
    {
        showAll(plugin, player, bounds, DUST_GOLD, DUST_CYAN, true);
    }

    /** Konflikt-Bereich rot/orange visualisieren; bricht laufende Erfolgs-Anzeige ab. */
    public static void showConflict(Plugin plugin, Player player, int x0, int z0, int x1, int z1)
    {
        BukkitTask existing = activeSuccessTasks.remove(player.getUniqueId());
        if (existing != null) existing.cancel();
        showAll(plugin, player, List.of(new int[]{x0, z0, x1, z1}), DUST_RED, DUST_ORANGE, false);
    }

    // ── Intern ────────────────────────────────────────────────────────────────

    private static void showAll(Plugin plugin, Player player, List<int[]> bounds,
                                Particle.DustOptions dustA, Particle.DustOptions dustB,
                                boolean isSuccess)
    {
        if (isSuccess)
        {
            BukkitTask existing = activeSuccessTasks.remove(player.getUniqueId());
            if (existing != null) existing.cancel();
        }

        // Kanten in äußere Ränder und Naht-Kanten aufteilen
        Set<Long>   chunkSet   = buildChunkSet(bounds);
        List<int[]> outerEdges = new ArrayList<>();
        List<int[]> seamEdges  = new ArrayList<>();
        splitEdges(bounds, chunkSet, outerEdges, seamEdges);

        BukkitRunnable runnable = new BukkitRunnable()
        {
            int iterations = 0;

            @Override
            public void run()
            {
                if (!player.isOnline() || iterations >= 15)
                {
                    if (isSuccess) activeSuccessTasks.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                // Äußere Ränder: 4 Streifen, marching-ants
                for (int[] edge : outerEdges)
                    drawEdge(player, edge, dustA, dustB, iterations, 4);

                // Naht-Kanten: 1 Streifen, Kristall-Blau, kein marching
                for (int[] edge : seamEdges)
                    drawEdge(player, edge, DUST_SEAM, DUST_SEAM, 0, 1);

                iterations++;
            }
        };

        BukkitTask task = runnable.runTaskTimer(plugin, 0L, 20L);
        if (isSuccess)
            activeSuccessTasks.put(player.getUniqueId(), task);
    }

    /**
     * Teilt alle Chunk-Kanten in äußere (Außenrahmen) und Naht-Kanten (Innenkanten).
     *
     * Kanten-Format: {constCoord, varStart, varEnd, isVertical}
     *   isVertical=0: horizontale Linie (z = constCoord, x von varStart bis varEnd)
     *   isVertical=1: vertikale Linie   (x = constCoord, z von varStart bis varEnd)
     *
     * Naht-Kanten werden nur vom SÜDLICHEN bzw. ÖSTLICHEN Chunk eingetragen,
     * damit jede innere Kante exakt einmal erscheint.
     */
    private static void splitEdges(List<int[]> bounds, Set<Long> chunkSet,
                                    List<int[]> outer, List<int[]> seams)
    {
        for (int[] b : bounds)
        {
            int cx  = b[0] >> 4;
            int cz  = b[1] >> 4;
            int bx0 = cx * 16;
            int bz0 = cz * 16;
            int bx1 = bx0 + 16;
            int bz1 = bz0 + 16;

            // Nord-Kante (z = bz0, horizontal)
            if (!chunkSet.contains(chunkKey(cx, cz - 1)))
                outer.add(new int[]{bz0, bx0, bx1, 0});

            // West-Kante (x = bx0, vertikal)
            if (!chunkSet.contains(chunkKey(cx - 1, cz)))
                outer.add(new int[]{bx0, bz0, bz1, 1});

            // Süd-Kante (z = bz1, horizontal)
            if (!chunkSet.contains(chunkKey(cx, cz + 1)))
                outer.add(new int[]{bz1, bx0, bx1, 0});
            else
                seams.add(new int[]{bz1, bx0, bx1, 0}); // innere Naht nach Süden

            // Ost-Kante (x = bx1, vertikal)
            if (!chunkSet.contains(chunkKey(cx + 1, cz)))
                outer.add(new int[]{bx1, bz0, bz1, 1});
            else
                seams.add(new int[]{bx1, bz0, bz1, 1}); // innere Naht nach Osten
        }
    }

    /**
     * Zeichnet eine Kante als Partikel-Linie.
     *
     * @param edge       {constCoord, varStart, varEnd, isVertical}
     * @param stripes    Anzahl horizontaler Streifen (äußere = 4, Naht = 1)
     */
    private static void drawEdge(Player player, int[] edge,
                                  Particle.DustOptions dustA, Particle.DustOptions dustB,
                                  int phase, int stripes)
    {
        double baseY      = player.getLocation().getY() + 0.5;
        int    constCoord = edge[0];
        int    varStart   = edge[1];
        int    varEnd     = edge[2];
        boolean vertical  = edge[3] == 1;

        for (int dy = 0; dy < stripes; dy++)
        {
            double y = baseY + dy;
            int    k = 0;

            if (vertical)
            {
                for (double z = varStart; z <= varEnd; z += 0.5, k++)
                {
                    Particle.DustOptions d = ((k + phase) % 4 < 2) ? dustA : dustB;
                    player.spawnParticle(Particle.DUST, constCoord, y, z, 1, 0, 0, 0, 0, d);
                }
            }
            else
            {
                for (double x = varStart; x <= varEnd; x += 0.5, k++)
                {
                    Particle.DustOptions d = ((k + phase) % 4 < 2) ? dustA : dustB;
                    player.spawnParticle(Particle.DUST, x, y, constCoord, 1, 0, 0, 0, 0, d);
                }
            }
        }
    }

    // ── Chunk-Hilfsmethoden ───────────────────────────────────────────────────

    private static Set<Long> buildChunkSet(List<int[]> bounds)
    {
        Set<Long> set = new HashSet<>(bounds.size() * 2);
        for (int[] b : bounds)
            set.add(chunkKey(b[0] >> 4, b[1] >> 4));
        return set;
    }

    private static long chunkKey(int cx, int cz)
    {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
