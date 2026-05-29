package com.griefprevention.customitems.gui;

import com.griefprevention.customitems.ClaimChunkStorage;
import com.griefprevention.customitems.ClaimMessages;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Wählt die Trust-Stufe für einen Spieler.
 *
 * Layout (27 Slots, 3 Reihen):
 *   Reihe 0: [B x4] [INFO @4] [B x4]
 *   Reihe 1: [B] [BAUEN @10] [B x4] [NICHTS @16] [B x2]
 *   Reihe 2: [B x8] [BACK @26]
 */
public class TrustLevelGui extends ClaimGui
{
    private static final int SLOT_INFO    =  4;
    private static final int SLOT_BUILD   = 10;
    private static final int SLOT_NOTHING = 16;
    private static final int SLOT_BACK    = 26;

    private final GriefPrevention   plugin;
    private final Claim             claim;
    private final ClaimChunkStorage chunkStorage;
    private final UUID              targetUUID;
    private final String            targetName;

    public TrustLevelGui(@NotNull GriefPrevention plugin,
                         @NotNull Claim claim,
                         @NotNull ClaimChunkStorage chunkStorage,
                         @NotNull UUID targetUUID,
                         @NotNull String targetName)
    {
        super(27, title("Stufe"));
        this.plugin       = plugin;
        this.claim        = claim;
        this.chunkStorage = chunkStorage;
        this.targetUUID   = targetUUID;
        this.targetName   = targetName;
        build();
    }

    private void build()
    {
        setItem(SLOT_INFO, Material.PLAYER_HEAD,
            "&8» &7Stufe für &9" + targetName,
            "",
            "&8» &7Wähle eine Vertrauens-Stufe.",
            "");

        setItem(SLOT_BUILD, Material.ORANGE_WOOL,
            "&8» &6&lAusbauen",
            "",
            "&8» &7Darf Blöcke setzen und",
            "&8» &7abbauen sowie interagieren.",
            "",
            "&8» &7&oKlicken zum Bestätigen.",
            "");

        setItem(SLOT_NOTHING, Material.RED_WOOL,
            "&8» &c&lNichts",
            "",
            "&8» &7Entfernt alle Rechte",
            "&8» &7dieses Spielers im Claim.",
            "",
            "&8» &7&oKlicken zum Bestätigen.",
            "");

        setItem(SLOT_BACK, Material.ARROW,
            "&8» &7← Zurück",
            "",
            "&8» &7&oKlicken zum Zurückgehen.",
            "");

        fill();
    }

    @Override
    public void handleClick(int slot, @NotNull Player player)
    {
        if (slot == SLOT_BACK)
        {
            new TrustGui(plugin, claim, chunkStorage).open(player);
            return;
        }

        String uuidStr = targetUUID.toString();

        // Crystal-Claims VOR der Beacon-Modifikation laden (selbes Muster wie Transfer-Fix)
        List<Claim> crystalClaims = chunkStorage.getCrystalClaims(plugin, claim);

        if (slot == SLOT_BUILD)
        {
            claim.setPermission(uuidStr, ClaimPermission.Build);
            plugin.dataStore.saveClaim(claim);
            for (Claim crystal : crystalClaims)
            {
                crystal.setPermission(uuidStr, ClaimPermission.Build);
                plugin.dataStore.saveClaim(crystal);
            }
            ClaimMessages.success(player,
                "&9" + targetName + "&a darf jetzt in diesem Claim bauen.");
            new TrustGui(plugin, claim, chunkStorage).open(player);
            return;
        }

        if (slot == SLOT_NOTHING)
        {
            claim.dropPermission(uuidStr);
            plugin.dataStore.saveClaim(claim);
            for (Claim crystal : crystalClaims)
            {
                crystal.dropPermission(uuidStr);
                plugin.dataStore.saveClaim(crystal);
            }
            ClaimMessages.info(player,
                "Vertrauen von &9" + targetName + "&7 entfernt.");
            new TrustGui(plugin, claim, chunkStorage).open(player);
        }
    }
}
