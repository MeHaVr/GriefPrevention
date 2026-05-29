package com.griefprevention.customitems;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClaimFlagsListener implements Listener
{
    private final GriefPrevention   plugin;
    private final ClaimFlagsStorage flagsStorage;
    private final ClaimChunkStorage chunkStorage;

    public ClaimFlagsListener(@NotNull GriefPrevention plugin,
                              @NotNull ClaimFlagsStorage flagsStorage,
                              @NotNull ClaimChunkStorage chunkStorage)
    {
        this.plugin        = plugin;
        this.flagsStorage  = flagsStorage;
        this.chunkStorage  = chunkStorage;
    }

    /** Gibt die Claim-ID zurück, gegen die Flags geprüft werden.
     *  Crystal-Chunk-Claims delegieren auf ihren Beacon. */
    private long resolveFlagsId(@NotNull Claim claim)
    {
        Long beaconId = chunkStorage.getBeaconId(claim.getID());
        return beaconId != null ? beaconId : claim.getID();
    }

    // ── PvP ──────────────────────────────────────────────────────────────────

    // ignoreCancelled=false damit wir GP's eigene Cancellation ggf. überschreiben können
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onPvP(@NotNull EntityDamageByEntityEvent event)
    {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity()  instanceof Player))          return;

        Claim claim = claimAt(event.getEntity().getLocation());
        if (claim == null) return;

        boolean pvpEnabled = flagsStorage.getFlag(resolveFlagsId(claim), ClaimFlagsStorage.FLAG_PVP);
        if (pvpEnabled)
        {
            // Flag sagt PvP erlaubt → GP-Cancellation aufheben
            event.setCancelled(false);
        }
        else
        {
            event.setCancelled(true);
            ClaimMessages.hint(attacker, "PvP ist in diesem Claim &cdeaktiviert&7.");
        }
    }

    // ── Explosionen ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(@NotNull EntityExplodeEvent event)
    {
        event.blockList().removeIf(block -> {
            Claim claim = claimAt(block.getLocation());
            return claim != null
                && !flagsStorage.getFlag(resolveFlagsId(claim), ClaimFlagsStorage.FLAG_EXPLOSIONS);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(@NotNull BlockExplodeEvent event)
    {
        event.blockList().removeIf(block -> {
            Claim claim = claimAt(block.getLocation());
            return claim != null
                && !flagsStorage.getFlag(resolveFlagsId(claim), ClaimFlagsStorage.FLAG_EXPLOSIONS);
        });
    }

    // ── Mob-Spawning ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCreatureSpawn(@NotNull CreatureSpawnEvent event)
    {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL
                && reason != CreatureSpawnEvent.SpawnReason.JOCKEY
                && reason != CreatureSpawnEvent.SpawnReason.CHUNK_GEN)
            return;

        Claim claim = claimAt(event.getLocation());
        if (claim == null) return;

        if (!flagsStorage.getFlag(resolveFlagsId(claim), ClaimFlagsStorage.FLAG_MOB_SPAWNING))
            event.setCancelled(true);
    }

    // ── Hilfsmethode ─────────────────────────────────────────────────────────

    private @Nullable Claim claimAt(@NotNull Location loc)
    {
        return plugin.dataStore.getClaimAt(loc, true, null);
    }
}
