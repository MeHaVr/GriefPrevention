package com.griefprevention.customitems;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Verhindert die Duplizierung von Claim Beacons und Claim Crystals.
 *
 * Strategie: Jedes Custom Item hat eine eindeutige UUID (PDC: griefprevention:item_uuid).
 * Beim Aufnehmen, Droppen und bei Hopper-Transfers wird geprüft ob die UUID bekannt/gültig ist.
 * Items mit unbekannter UUID (extern erstellt oder gedupet) werden sofort entfernt.
 */
public class AntiDupeListener implements Listener
{
    private final Plugin plugin;

    public AntiDupeListener(@NotNull Plugin plugin)
    {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(@NotNull EntityPickupItemEvent event)
    {
        ItemStack item = event.getItem().getItemStack();
        if (!CustomItems.isCustomItem(plugin, item)) return;

        if (!AntiDupeManager.isValid(item, plugin))
        {
            event.setCancelled(true);
            event.getItem().remove();
            logRemoval(item, event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(@NotNull PlayerDropItemEvent event)
    {
        ItemStack item = event.getItemDrop().getItemStack();
        if (!CustomItems.isCustomItem(plugin, item)) return;

        if (!AntiDupeManager.isValid(item, plugin))
        {
            event.setCancelled(true);
            event.getPlayer().getInventory().remove(item);
            logRemoval(item, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(@NotNull InventoryMoveItemEvent event)
    {
        ItemStack item = event.getItem();
        if (!CustomItems.isCustomItem(plugin, item)) return;

        if (!AntiDupeManager.isValid(item, plugin))
        {
            event.setCancelled(true);
            event.getSource().remove(item);
            plugin.getLogger().warning("[AntiDupe] Ungültiges Custom Item in Hopper entfernt.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMerge(@NotNull ItemMergeEvent event)
    {
        // Verhindert das Zusammenführen von Custom Items (würde Stackgröße ändern)
        ItemStack item = event.getEntity().getItemStack();
        if (CustomItems.isCustomItem(plugin, item)) event.setCancelled(true);
    }

    private void logRemoval(@NotNull ItemStack item, @NotNull Entity entity)
    {
        String type = CustomItems.isClaimBeacon(plugin, item) ? "ClaimBeacon" : "ClaimCrystal";
        String who  = entity instanceof Player p ? p.getName() : entity.getType().name();
        plugin.getLogger().warning(
            "[AntiDupe] Duplikat entfernt: " + type
            + " | Spieler/Entity=" + who
            + " | Ort=" + entity.getLocation().getWorld().getName()
            + "," + entity.getLocation().getBlockX()
            + "," + entity.getLocation().getBlockY()
            + "," + entity.getLocation().getBlockZ());
    }
}
