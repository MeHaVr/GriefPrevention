package com.griefprevention.customitems.gui;

import com.griefprevention.customitems.ClaimMessages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class GuiManager implements Listener
{
    private static final Map<UUID, Consumer<String>> pendingInputs = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> inputTimeouts = new ConcurrentHashMap<>();

    private static final int INPUT_TIMEOUT_TICKS = 20 * 60; // 60 Sekunden

    private static Plugin pluginInstance;

    public GuiManager(@NotNull Plugin plugin)
    {
        pluginInstance = plugin;
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event)
    {
        if (!(event.getInventory().getHolder() instanceof ClaimGui gui)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Chest-Slots + Player-Inventar (27) + Hotbar (9) = chestSize + 36
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize() + 36) return;
        gui.handleClick(event.getRawSlot(), player);
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event)
    {
        if (!(event.getInventory().getHolder() instanceof ClaimGui gui)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        gui.onClose(player);
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event)
    {
        if (event.getInventory().getHolder() instanceof ClaimGui)
            event.setCancelled(true);
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onAsyncChat(@NotNull AsyncPlayerChatEvent event)
    {
        Consumer<String> handler = pendingInputs.remove(event.getPlayer().getUniqueId());
        if (handler == null) return;
        cancelTimeout(event.getPlayer().getUniqueId());
        event.setCancelled(true);
        String msg = event.getMessage();
        pluginInstance.getServer().getScheduler()
            .runTask(pluginInstance, () -> handler.accept(msg));
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event)
    {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingInputs.remove(uuid);
        cancelTimeout(uuid);
        // Falls der Spieler mit offenem GUI disconnected ist und kein
        // InventoryCloseEvent gefeuert wurde: Inventar-Backup einspielen.
        InventoryBackup.restore(pluginInstance, event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event)
    {
        // Crash-Recovery: Wurde der Server beendet, während ein GUI das
        // Inventar geleert hatte, liegt noch ein Backup auf Platte.
        if (InventoryBackup.restore(pluginInstance, event.getPlayer()))
            pluginInstance.getLogger().info("[GUI] Inventar von " + event.getPlayer().getName()
                + " aus Backup wiederhergestellt.");
    }

    /**
     * Schließt alle offenen ClaimGuis (löst onClose und damit die
     * Inventar-Wiederherstellung aus). Wird beim Plugin-Shutdown aufgerufen.
     */
    public static void closeAllOpen()
    {
        if (pluginInstance == null) return;
        for (Player player : pluginInstance.getServer().getOnlinePlayers())
        {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof ClaimGui)
                player.closeInventory();
        }
    }

    /** Registriert einen einmaligen Chat-Handler mit 60-Sekunden-Timeout. */
    public static void awaitInput(@NotNull Player player, @NotNull Consumer<String> handler)
    {
        UUID uuid = player.getUniqueId();
        pendingInputs.put(uuid, handler);
        int taskId = pluginInstance.getServer().getScheduler().runTaskLater(pluginInstance, () -> {
            if (pendingInputs.remove(uuid) != null)
                ClaimMessages.hint(player, "&7Eingabe abgebrochen &8(Timeout)&7.");
            inputTimeouts.remove(uuid);
        }, INPUT_TIMEOUT_TICKS).getTaskId();
        Integer old = inputTimeouts.put(uuid, taskId);
        if (old != null) pluginInstance.getServer().getScheduler().cancelTask(old);
    }

    /** Bricht eine laufende Chat-Eingabe ab. */
    public static void cancelInput(@NotNull Player player)
    {
        UUID uuid = player.getUniqueId();
        pendingInputs.remove(uuid);
        cancelTimeout(uuid);
    }

    private static void cancelTimeout(@NotNull UUID uuid)
    {
        Integer taskId = inputTimeouts.remove(uuid);
        if (taskId != null) pluginInstance.getServer().getScheduler().cancelTask(taskId);
    }
}
