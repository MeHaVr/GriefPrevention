package com.griefprevention.customitems.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Beacon-Platzierungs-Bestätigung mit ItemsAdder-Hintergrundbild.
 *
 * Klick-Zonen (rawSlots in einem 27-Slot-Chest):
 *   Chest:      0–26
 *   Player-Inv: 27–53
 *   Hotbar:     54–62  ← unsere Buttons
 *
 *   Slots 54–57 (erste 4 Hotbar-Positionen) = Ablehnen
 *   Slots 58–62 (letzte 5 Hotbar-Positionen) = Bestätigen
 *
 * Slots im Inventar sind AIR – kein sichtbares Item, kein Titel, keine Lore.
 * Das IA-Hintergrundbild liefert die visuelle Darstellung.
 *
 * GuiManager wurde erweitert (chestSize + 36) damit Hotbar-Klicks
 * an handleClick() weitergereicht werden.
 */
public class BeaconConfirmIaGui extends ClaimGui
{
    private static final String IA_IMAGE = "norvex-desing:testmenu_bg";

    // rawSlot-Grenzen für Hotbar in einem 27-Slot-Chest
    private static final int HOTBAR_START      = 54; // 27 Chest + 27 Player-Inv
    private static final int HOTBAR_CANCEL_END = 58; // Slots 54–57 → Ablehnen  (4 Slots)
    private static final int HOTBAR_END        = 63; // Slots 58–62 → Bestätigen (5 Slots)

    private final Runnable onConfirm;
    private final Runnable onCancel;
    private boolean        confirmed = false;

    @Nullable private ItemStack[] savedContents = null;
    @Nullable private ItemStack   savedOffhand  = null;
    private boolean               restored      = false;

    public BeaconConfirmIaGui(int chunkX, int chunkZ,
                               @NotNull Runnable onConfirm,
                               @NotNull Runnable onCancel)
    {
        super(27, "");
        this.onConfirm = onConfirm;
        this.onCancel  = onCancel;
        tryApplyIaBackground();
        // Keine Items setzen – Hotbar-Klicks + IA-Hintergrundbild übernehmen alles
    }

    private void tryApplyIaBackground()
    {
        try
        {
            dev.lone.itemsadder.api.FontImages.FontImageWrapper bg =
                new dev.lone.itemsadder.api.FontImages.FontImageWrapper(IA_IMAGE);
            if (!bg.exists())
            {
                org.bukkit.Bukkit.getLogger().warning("[GriefPrevention] BeaconConfirmIaGui: IA-Image nicht gefunden: " + IA_IMAGE);
                return;
            }

            // §f = weißer Farbcode, macht das Bild hell (kein sichtbares Zeichen).
            // applyPixelsOffset(-50) = 50px nach links.
            String iaTitle = "§f" + bg.applyPixelsOffset(-50);
            this.inventory = Bukkit.createInventory(this, 27, iaTitle);
        }
        catch (Exception | Error e)
        {
            org.bukkit.Bukkit.getLogger().warning("[GriefPrevention] BeaconConfirmIaGui: IA-Hintergrund konnte nicht geladen werden: " + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void open(@NotNull Player player)
    {
        PlayerInventory inv = player.getInventory();

        savedContents = inv.getContents().clone();
        savedOffhand  = inv.getItemInOffHand().clone();

        // Komplettes Inventar + Offhand leeren
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
        PlayerInventory inv = player.getInventory();
        inv.setContents(savedContents);
        if (savedOffhand != null) inv.setItemInOffHand(savedOffhand);
        player.updateInventory();
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
