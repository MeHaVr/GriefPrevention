package com.griefprevention.customitems.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Crash-sicheres Inventar-Backup für GUIs, die das Spieler-Inventar temporär
 * leeren (z.B. {@link BeaconConfirmIaGui}).
 *
 * Beim Öffnen wird das komplette Inventar (inkl. Rüstung und Offhand)
 * zusätzlich auf Platte gesichert (invBackups/&lt;uuid&gt;.yml). Nach erfolgreicher
 * Wiederherstellung wird die Datei gelöscht. Stürzt der Server ab, während das
 * GUI offen ist, stellt {@link GuiManager} das Backup beim nächsten Join wieder her.
 */
final class InventoryBackup
{
    private InventoryBackup() {}

    private static @NotNull File backupFile(@NotNull Plugin plugin, @NotNull UUID uuid)
    {
        return new File(new File(plugin.getDataFolder(), "invBackups"), uuid + ".yml");
    }

    /** Sichert das komplette Inventar auf Platte (synchron – die Datei ist klein). */
    static void save(@NotNull Plugin plugin, @NotNull Player player)
    {
        YamlConfiguration config = new YamlConfiguration();
        config.set("contents", Arrays.asList(player.getInventory().getContents()));

        File file = backupFile(plugin, player.getUniqueId());
        file.getParentFile().mkdirs();
        try
        {
            config.save(file);
        }
        catch (IOException e)
        {
            plugin.getLogger().warning("[GUI] Inventar-Backup für " + player.getName()
                + " konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /**
     * Stellt ein vorhandenes Backup wieder her und löscht die Datei.
     *
     * @return true wenn ein Backup vorhanden war und wiederhergestellt wurde
     */
    @SuppressWarnings("deprecation")
    static boolean restore(@NotNull Plugin plugin, @NotNull Player player)
    {
        File file = backupFile(plugin, player.getUniqueId());
        if (!file.exists()) return false;

        List<?> list = YamlConfiguration.loadConfiguration(file).getList("contents");
        if (list != null)
        {
            ItemStack[] contents = list.stream()
                .map(entry -> entry instanceof ItemStack stack ? stack : null)
                .toArray(ItemStack[]::new);
            player.getInventory().setContents(contents);
            player.updateInventory();
        }
        delete(plugin, player.getUniqueId());
        return true;
    }

    /** Löscht das Backup (nach erfolgreicher In-Memory-Wiederherstellung). */
    static void delete(@NotNull Plugin plugin, @NotNull UUID uuid)
    {
        File file = backupFile(plugin, uuid);
        if (file.exists() && !file.delete())
            plugin.getLogger().warning("[GUI] Inventar-Backup " + file.getName() + " konnte nicht gelöscht werden.");
    }
}
