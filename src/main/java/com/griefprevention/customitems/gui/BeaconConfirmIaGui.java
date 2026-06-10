package com.griefprevention.customitems.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Beacon-Platzierungs-Bestätigung mit ItemsAdder-Hintergrundbild
 * (guiConfig.yml, Key "beacon_confirm").
 *
 * Klick-Zonen (rawSlots in einem 27-Slot-Chest):
 *   Chest:      0–26
 *   Player-Inv: 27–53
 *   Hotbar:     54–62  ← unsere Buttons
 *
 *   Slots 54–57 (erste 4 Hotbar-Positionen) = Ablehnen
 *   Slots 58–62 (letzte 5 Hotbar-Positionen) = Bestätigen
 *
 * Slots im Inventar sind AIR – das IA-Hintergrundbild liefert die Darstellung.
 * Das Spieler-Inventar wird geleert und beim Schließen wiederhergestellt;
 * zur Crash-Sicherheit liegt während der Anzeige ein Backup auf Platte
 * ({@link InventoryBackup}), das GuiManager beim nächsten Join einspielt.
 */
public class BeaconConfirmIaGui extends ClaimGui
{
    // rawSlot-Grenzen für Hotbar in einem 27-Slot-Chest
    private static final int HOTBAR_START      = 54; // 27 Chest + 27 Player-Inv
    private static final int HOTBAR_CANCEL_END = 58; // Slots 54–57 → Ablehnen  (4 Slots)
    private static final int HOTBAR_END        = 63; // Slots 58–62 → Bestätigen (5 Slots)

    private final Plugin   plugin;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private boolean        confirmed = false;

    @Nullable private ItemStack[] savedContents = null;
    private boolean               restored      = false;

    public BeaconConfirmIaGui(@NotNull Plugin plugin,
                              @NotNull Runnable onConfirm,
                              @NotNull Runnable onCancel)
    {
        super(27, "beacon_confirm", title("Beacon platzieren?"));
        this.plugin    = plugin;
        this.onConfirm = onConfirm;
        this.onCancel  = onCancel;
        // Keine Items setzen – Hotbar-Klicks + IA-Hintergrundbild übernehmen alles
    }

    @Override
    @SuppressWarnings("deprecation")
    public void open(@NotNull Player player)
    {
        PlayerInventory inv = player.getInventory();

        // Crash-Sicherheit: Backup auf Platte, In-Memory-Kopie für die Wiederherstellung
        InventoryBackup.save(plugin, player);
        savedContents = inv.getContents().clone();

        // Komplettes Inventar (inkl. Rüstung + Offhand) leeren
        inv.clear();
        inv.setItemInOffHand(new ItemStack(Material.AIR));

        // Client sofort aktualisieren
        player.updateInventory();

        player.openInventory(inventory);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onClose(@NotNull Player player)
    {
        if (restored || savedContents == null) return;
        restored = true;
        player.getInventory().setContents(savedContents);
        player.updateInventory();
        InventoryBackup.delete(plugin, player.getUniqueId());
    }

    @Override
    public void handleClick(int slot, @NotNull Player player)
    {
        boolean isCancel  = slot >= HOTBAR_START && slot < HOTBAR_CANCEL_END;
        boolean isConfirm = slot >= HOTBAR_CANCEL_END && slot < HOTBAR_END;

        if (isConfirm)
        {
            if (confirmed) return;
            confirmed = true;
            player.closeInventory();
            onConfirm.run();
        }
        else if (isCancel)
        {
            player.closeInventory();
            onCancel.run();
        }
    }
}
