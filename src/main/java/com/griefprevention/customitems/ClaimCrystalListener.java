package com.griefprevention.customitems;

import com.griefprevention.customitems.gui.CrystalMapGui;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ClaimCrystalListener implements Listener
{
    private final GriefPrevention    plugin;
    private final ClaimChunkStorage  chunkStorage;
    private final ClaimBeaconStorage beaconStorage;

    public ClaimCrystalListener(@NotNull GriefPrevention plugin,
                                @NotNull ClaimChunkStorage chunkStorage,
                                @NotNull ClaimBeaconStorage beaconStorage)
    {
        this.plugin        = plugin;
        this.chunkStorage  = chunkStorage;
        this.beaconStorage = beaconStorage;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event)
    {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        ItemStack item = event.getItem();
        if (!CustomItems.isClaimCrystal(plugin, item)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();

        // Spieler ohne eigene Claims: Hinweis geben
        boolean hasClaims = !plugin.dataStore
            .getPlayerData(player.getUniqueId()).getClaims().isEmpty();

        if (!hasClaims)
        {
            ClaimMessages.error(player,
                "Du hast noch keinen Claim. Platziere zuerst einen &lClaim Beacon&c.");
            return;
        }

        new CrystalMapGui(plugin, player, chunkStorage, beaconStorage).open(player);
    }
}
