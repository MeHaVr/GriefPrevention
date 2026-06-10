package com.griefprevention.customitems;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimEnterListener implements Listener
{
    private static final LegacyComponentSerializer SERIALIZER =
        LegacyComponentSerializer.builder().character('&').hexColors().build();

    // Sentinel-UUID für "Wildnis" (ConcurrentHashMap erlaubt keine null-Werte)
    private static final UUID WILDERNESS = new UUID(0, 0);

    private final GriefPrevention      plugin;
    private final ClaimFlagsStorage    flagsStorage;
    private final Map<UUID, UUID>      lastOwner = new ConcurrentHashMap<>();

    public ClaimEnterListener(@NotNull GriefPrevention plugin, @NotNull ClaimFlagsStorage flagsStorage)
    {
        this.plugin       = plugin;
        this.flagsStorage = flagsStorage;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(@NotNull PlayerMoveEvent event)
    {
        // Nur bei Chunk-Wechsel auslösen
        if (event.getFrom().getBlockX() >> 4 == event.getTo().getBlockX() >> 4
                && event.getFrom().getBlockZ() >> 4 == event.getTo().getBlockZ() >> 4)
            return;

        Player player   = event.getPlayer();
        Claim  claim    = plugin.dataStore.getClaimAt(event.getTo(), false, null);
        UUID   newOwner = (claim != null) ? claim.ownerID : WILDERNESS;

        // ActionBar nur senden wenn sich der Besitzer geändert hat
        UUID prev = lastOwner.get(player.getUniqueId());
        if (newOwner.equals(prev)) return;
        lastOwner.put(player.getUniqueId(), newOwner);

        if (claim != null && flagsStorage.getFlag(claim.getID(), ClaimFlagsStorage.FLAG_PARTICLES))
        {
            ClaimParticleEffect.play(plugin, event.getTo(), flagsStorage.getParticleType(claim.getID()));
        }

        Component bar;
        if (claim != null)
        {
            String owner = claim.getOwnerName() != null ? claim.getOwnerName() : "Unbekannt";
            bar = SERIALIZER.deserialize("&8◆ &9" + owner + "&7's Claim &8◆");
        }
        else
        {
            bar = Component.empty();
        }

        ((Audience) player).sendActionBar(bar);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event)
    {
        lastOwner.remove(event.getPlayer().getUniqueId());
    }
}
