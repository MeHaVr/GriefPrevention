package com.griefprevention.customitems;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet eindeutige UUIDs für Custom Items (Claim Beacon und Claim Crystal),
 * um Duplikationen durch Exploits zu verhindern.
 *
 * Jedes legitim erstellte Item bekommt eine UUID via PDC (griefprevention:item_uuid).
 * Diese UUID wird in einem persistenten Set gespeichert. Items ohne gültige UUID
 * oder mit unbekannter UUID gelten als ungültig.
 */
public class AntiDupeManager
{
    private static final Set<UUID> validUuids = ConcurrentHashMap.newKeySet();
    private static File   yamlFile;
    private static Plugin pluginRef;
    private static int    pendingSaveTask = -1;

    public static void init(@NotNull Plugin plugin)
    {
        pluginRef = plugin;
        yamlFile  = new File(plugin.getDataFolder(), "antiDupeIds.yml");
        loadFromDisk();
        plugin.getLogger().info("[AntiDupe] " + validUuids.size() + " gültige Item-UUID(s) geladen.");
    }

    /** Registriert ein neu erstelltes Item. Wird von CustomItems.create*() aufgerufen. */
    public static void register(@NotNull ItemStack item, @NotNull Plugin plugin)
    {
        UUID uuid = CustomItems.getItemUuid(plugin, item);
        if (uuid == null) return;
        validUuids.add(uuid);
        scheduleSave();
    }

    /** Entfernt eine UUID wenn das Item dauerhaft verbraucht wird (z.B. Beacon platziert). */
    public static void consume(@NotNull ItemStack item, @NotNull Plugin plugin)
    {
        UUID uuid = CustomItems.getItemUuid(plugin, item);
        if (uuid == null) return;
        if (validUuids.remove(uuid)) scheduleSave();
    }

    /**
     * Prüft ob das Custom Item eine gültige, registrierte UUID hat.
     * Gibt false zurück wenn das Item kein Custom Item ist, keine UUID hat,
     * oder die UUID unbekannt ist (potenziell gedupet oder extern erstellt).
     */
    public static boolean isValid(@NotNull ItemStack item, @NotNull Plugin plugin)
    {
        if (!CustomItems.isCustomItem(plugin, item)) return true; // nicht unser Item → ignorieren
        UUID uuid = CustomItems.getItemUuid(plugin, item);
        if (uuid == null) return false; // Custom Item ohne UUID → ungültig
        return validUuids.contains(uuid);
    }

    /** Gibt die Anzahl der registrierten UUIDs zurück (für Diagnose). */
    public static int size()
    {
        return validUuids.size();
    }

    // ------- Persistenz -------

    private static void loadFromDisk()
    {
        if (!yamlFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(yamlFile);
        java.util.List<String> list = config.getStringList("uuids");
        for (String s : list)
        {
            try { validUuids.add(UUID.fromString(s)); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    private static void scheduleSave()
    {
        if (pluginRef == null) return;
        if (pendingSaveTask != -1) pluginRef.getServer().getScheduler().cancelTask(pendingSaveTask);
        pendingSaveTask = pluginRef.getServer().getScheduler()
            .runTaskLaterAsynchronously(pluginRef, AntiDupeManager::saveToDisk, 60L).getTaskId();
    }

    private static void saveToDisk()
    {
        pendingSaveTask = -1;
        YamlConfiguration config = new YamlConfiguration();
        config.set("uuids", validUuids.stream().map(UUID::toString).toList());
        try { config.save(yamlFile); }
        catch (IOException e)
        {
            if (pluginRef != null)
                pluginRef.getLogger().warning("[AntiDupe] antiDupeIds.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Sofortiges Speichern beim Server-Shutdown. */
    public static void saveNow()
    {
        if (pendingSaveTask != -1 && pluginRef != null)
            pluginRef.getServer().getScheduler().cancelTask(pendingSaveTask);
        saveToDisk();
    }
}
