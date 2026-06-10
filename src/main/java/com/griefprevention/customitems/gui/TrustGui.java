package com.griefprevention.customitems.gui;

import com.griefprevention.customitems.ClaimChunkStorage;
import com.griefprevention.customitems.ClaimMessages;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Zeigt alle vertrauten Spieler des Claims und erlaubt Hinzufügen/Entfernen.
 *
 * Layout (54 Slots, 6 Reihen):
 *   Reihen 0-3 (Slots 0-35): Spieler-Heads (max 36 pro Seite, paginiert)
 *   Reihe 4: leer / Navigation (Slots 36-44)
 *   Reihe 5: Aktionen (Slots 45-53)
 *     Slot 46: Vorherige Seite
 *     Slot 49: Hinzufügen (EMERALD)
 *     Slot 52: Nächste Seite
 *     Slot 53: Schließen (ARROW)
 */
public class TrustGui extends ClaimGui
{
    private static final int PAGE_SIZE  = 36;
    private static final int SLOT_PREV  = 46;
    private static final int SLOT_NEXT  = 52;
    private static final int SLOT_ADD   = 49;
    private static final int SLOT_CLOSE = 53;

    private final GriefPrevention  plugin;
    private final Claim            claim;
    private final ClaimChunkStorage chunkStorage;
    private final int              page;
    private final List<String>     trustedUuids;
    private final Map<String, String>          uuidToName;
    private final Map<String, ClaimPermission> uuidToLevel;

    public TrustGui(@NotNull GriefPrevention plugin,
                    @NotNull Claim claim,
                    @NotNull ClaimChunkStorage chunkStorage)
    {
        this(plugin, claim, chunkStorage, 0);
    }

    private TrustGui(@NotNull GriefPrevention plugin,
                     @NotNull Claim claim,
                     @NotNull ClaimChunkStorage chunkStorage,
                     int page)
    {
        super(54, "trust", title("Vertrauen"));
        this.plugin       = plugin;
        this.claim        = claim;
        this.chunkStorage = chunkStorage;
        this.page         = page;

        ArrayList<String> builders   = new ArrayList<>();
        ArrayList<String> containers = new ArrayList<>();
        ArrayList<String> accessors  = new ArrayList<>();
        ArrayList<String> managers   = new ArrayList<>();
        claim.getPermissions(builders, containers, accessors, managers);

        uuidToLevel = new LinkedHashMap<>();
        for (String s : managers)   uuidToLevel.put(s, ClaimPermission.Manage);
        for (String s : builders)   uuidToLevel.put(s, ClaimPermission.Build);
        for (String s : containers) uuidToLevel.put(s, ClaimPermission.Container);
        for (String s : accessors)  uuidToLevel.put(s, ClaimPermission.Access);

        trustedUuids = new ArrayList<>(uuidToLevel.keySet());
        uuidToName   = new java.util.HashMap<>();
        for (String uuidStr : trustedUuids)
        {
            try
            {
                OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                uuidToName.put(uuidStr, op.getName() != null ? op.getName() : uuidStr.substring(0, 8));
            }
            catch (IllegalArgumentException e)
            {
                uuidToName.put(uuidStr, uuidStr.substring(0, 8));
            }
        }
        build();
    }

    private void build()
    {
        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, trustedUuids.size());

        for (int i = start; i < end; i++)
            buildHead(i - start, trustedUuids.get(i));

        if (page > 0)
            setItem(SLOT_PREV, Material.FEATHER,
                "&8» &7◄ Vorherige Seite",
                "",
                "&8» &7&oKlicken zum Blättern.",
                "");

        if (end < trustedUuids.size())
            setItem(SLOT_NEXT, Material.FEATHER,
                "&8» &7Nächste Seite ►",
                "",
                "&8» &7&oKlicken zum Blättern.",
                "");

        setItem(SLOT_ADD, Material.EMERALD,
            "&8» &a&l✚ Spieler hinzufügen",
            "",
            "&8» &7Spielernamen eingeben und",
            "&8» &7Vertrauens-Stufe wählen.",
            "");

        setItem(SLOT_CLOSE, Material.ARROW,
            "&8» &7← Schließen",
            "",
            "&8» &7&oKlicken zum Schließen.",
            "");

        fill();
    }

    @SuppressWarnings("deprecation")
    private void buildHead(int slot, @NotNull String uuidStr)
    {
        ClaimPermission level = uuidToLevel.get(uuidStr);
        String name  = uuidToName.get(uuidStr);
        String label = levelLabel(level);

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta != null)
        {
            try { meta.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(uuidStr))); }
            catch (IllegalArgumentException ignored) {}
            meta.setDisplayName(color("&8» &9" + name));
            meta.setLore(java.util.List.of(
                color(""),
                color("&8» &7Stufe&8: &e" + label),
                color(""),
                color("&8» &7&oKlicken zum Entfernen."),
                color("")));
            skull.setItemMeta(meta);
        }
        inventory.setItem(slot, skull);
    }

    @Override
    public void handleClick(int slot, @NotNull Player player)
    {
        if (slot == SLOT_ADD)
        {
            player.closeInventory();
            ClaimMessages.hint(player, "Spielernamen eingeben &8(oder &ccancel&8)&8:");
            GuiManager.awaitInput(player, input ->
            {
                if (input.equalsIgnoreCase("cancel"))
                {
                    ClaimMessages.info(player, "Abgebrochen.");
                    return;
                }
                resolvePlayer(player, input);
            });
            return;
        }

        if (slot == SLOT_CLOSE)
        {
            player.closeInventory();
            return;
        }

        if (slot == SLOT_PREV)
        {
            new TrustGui(plugin, claim, chunkStorage, page - 1).open(player);
            return;
        }

        if (slot == SLOT_NEXT)
        {
            new TrustGui(plugin, claim, chunkStorage, page + 1).open(player);
            return;
        }

        // Klick auf Player-Head → Entfernen
        int start = page * PAGE_SIZE;
        if (slot < PAGE_SIZE)
        {
            int idx = start + slot;
            if (idx >= trustedUuids.size()) return;
            String uuidStr = trustedUuids.get(idx);
            String name    = uuidToName.get(uuidStr);

            new ConfirmGui(
                "Vertrauen für &9" + name + "&7 entfernen?",
                () ->
                {
                    claim.dropPermission(uuidStr);
                    plugin.dataStore.saveClaim(claim);
                    for (Claim crystal : chunkStorage.getCrystalClaims(plugin, claim))
                    {
                        crystal.dropPermission(uuidStr);
                        plugin.dataStore.saveClaim(crystal);
                    }
                    ClaimMessages.info(player, "Vertrauen von &9" + name + "&7 entfernt.");
                    new TrustGui(plugin, claim, chunkStorage).open(player);
                },
                () -> new TrustGui(plugin, claim, chunkStorage, page).open(player)
            ).open(player);
        }
    }

    @SuppressWarnings("deprecation")
    private void resolvePlayer(@NotNull Player player, @NotNull String input)
    {
        Player online = Bukkit.getPlayerExact(input);
        UUID   targetUUID;
        String targetName;

        if (online != null)
        {
            targetUUID = online.getUniqueId();
            targetName = online.getName();
        }
        else
        {
            OfflinePlayer op = Bukkit.getOfflinePlayer(input);
            if (!op.hasPlayedBefore())
            {
                ClaimMessages.error(player, "Spieler &9" + input + "&7 nicht gefunden.");
                Bukkit.getScheduler().runTask(plugin,
                    () -> new TrustGui(plugin, claim, chunkStorage).open(player));
                return;
            }
            targetUUID = op.getUniqueId();
            targetName = op.getName() != null ? op.getName() : input;
        }

        if (targetUUID.equals(claim.ownerID))
        {
            ClaimMessages.error(player, "Das ist der Claim-Besitzer.");
            Bukkit.getScheduler().runTask(plugin,
                () -> new TrustGui(plugin, claim, chunkStorage).open(player));
            return;
        }

        final UUID   uuid = targetUUID;
        final String name = targetName;
        Bukkit.getScheduler().runTask(plugin,
            () -> new TrustLevelGui(plugin, claim, chunkStorage, uuid, name).open(player));
    }

    private static @NotNull String levelLabel(@NotNull ClaimPermission level)
    {
        return switch (level)
        {
            case Build -> "Ausbauen";
            default    -> "Ausbauen";
        };
    }
}
