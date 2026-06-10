package com.griefprevention.customitems.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Einfaches Ja/Nein-Bestätigungs-GUI (27 Slots).
 *
 * Layout:
 *   Reihe 0: [G x9]
 *   Reihe 1: [G x3] [Nein @12] [G] [Ja @14] [G x3]
 *   Reihe 2: [G x9]
 */
public class ConfirmGui extends ClaimGui
{
    private static final int SLOT_TITLE   = 4;
    private static final int SLOT_CONFIRM = 14;
    private static final int SLOT_CANCEL  = 12;

    private final Runnable onConfirm;
    private final Runnable onCancel;
    private boolean        confirmed = false;

    public ConfirmGui(@NotNull String guiName,
                      @NotNull Runnable onConfirm,
                      @NotNull Runnable onCancel)
    {
        super(27);
        this.onConfirm = onConfirm;
        this.onCancel  = onCancel;
        build(guiName);
    }

    private void build(@NotNull String guiName)
    {
        setItem(SLOT_TITLE, Material.NAME_TAG,
            "&8» &f" + guiName);

        setItem(SLOT_CONFIRM, Material.LIME_CONCRETE,
            "&8» &a&l✔ BESTÄTIGEN",
            "",
            "&8» &7Ja, Aktion durchführen.",
            "");
        setItem(SLOT_CANCEL, Material.RED_CONCRETE,
            "&8» &c&l✘ ABBRECHEN",
            "",
            "&8» &7Nein, Aktion abbrechen.",
            "");
        fill();
    }

    @Override
    public void handleClick(int slot, @NotNull Player player)
    {
        if (slot == SLOT_CONFIRM)
        {
            if (confirmed) return;
            confirmed = true;
            player.closeInventory();
            onConfirm.run();
        }
        else if (slot == SLOT_CANCEL)
        {
            player.closeInventory();
            onCancel.run();
        }
    }
}
