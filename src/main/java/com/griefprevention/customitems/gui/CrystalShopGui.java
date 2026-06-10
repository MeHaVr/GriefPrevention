package com.griefprevention.customitems.gui;

import com.griefprevention.customitems.ClaimMessages;
import com.griefprevention.customitems.CrystalDatabase;
import com.griefprevention.customitems.CustomItems;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * GUI-Shop: Spieler kaufen Claim Crystals und Claim Beacons mit Crystals.
 *
 * Layout (27 Slots, 3 Reihen):
 *   Reihe 1: Glas-Rand
 *   Slot 11: Claim Crystal  (Preis: CRYSTAL_PRICE)
 *   Slot 13: Kontostand-Info
 *   Slot 15: Claim Beacon   (Preis: BEACON_PRICE)
 *   Reihe 3: Glas-Rand, Slot 22 = Schließen (Mitte)
 */
public class CrystalShopGui extends ClaimGui
{
    private static final double CRYSTAL_PRICE = 100.0;
    private static final double BEACON_PRICE  = 500.0;

    private static final int SLOT_CRYSTAL = 11;
    private static final int SLOT_INFO    = 13;
    private static final int SLOT_BEACON  = 15;
    private static final int SLOT_CLOSE   = 22;

    private final Plugin plugin;
    private final CrystalDatabase db;
    private final Player player;

    public CrystalShopGui(@NotNull Plugin plugin, @NotNull CrystalDatabase db, @NotNull Player player)
    {
        super(27, "crystal_shop", TITLE_PREFIX + "Crystal Shop");
        this.plugin = plugin;
        this.db     = db;
        this.player = player;
        build();
    }

    private void build()
    {
        db.createAccount(player.getUniqueId());
        double balance = db.getBalance(player.getUniqueId());

        // Claim Crystal
        setItem(SLOT_CRYSTAL, Material.AMETHYST_SHARD,
            "&8» &b&lClaim Crystal",
            "",
            "&8» &7Ermöglicht das Beanspruchen",
            "&8» &7angrenzender Chunks.",
            "",
            "&8» &7Preis&8:    &b" + formatPrice(CRYSTAL_PRICE) + " &7✦ Crystal",
            "&8» &7Guthaben&8: &b" + formatPrice(balance) + " &7✦ Crystal",
            "",
            "&8» &a&oKlicken zum Kaufen.",
            ""
        );

        // Kontostand
        setItem(SLOT_INFO, Material.EMERALD,
            "&8» &e&lDein Guthaben",
            "",
            "&8» &b" + formatPrice(balance) + " &7✦ Crystal",
            "",
            "&8» &7Claim Crystal&8: &b" + formatPrice(CRYSTAL_PRICE),
            "&8» &7Claim Beacon&8:  &b" + formatPrice(BEACON_PRICE),
            ""
        );

        // Claim Beacon
        setItem(SLOT_BEACON, Material.BEACON,
            "&8» &6&lClaim Beacon",
            "",
            "&8» &7Platzierbar zum dauerhaften",
            "&8» &7Beanspruchen eines Chunks.",
            "",
            "&8» &7Preis&8:    &b" + formatPrice(BEACON_PRICE) + " &7✦ Crystal",
            "&8» &7Guthaben&8: &b" + formatPrice(balance) + " &7✦ Crystal",
            "",
            "&8» &a&oKlicken zum Kaufen.",
            ""
        );

        // Schließen
        setItem(SLOT_CLOSE, Material.ARROW,
            "&8» &7← Schließen",
            "",
            "&8» &7&oKlicken zum Schließen.",
            "");

        fill();
    }

    @Override
    public void handleClick(int slot, @NotNull Player clicker)
    {
        if (slot == SLOT_CRYSTAL)
        {
            purchase(clicker, CRYSTAL_PRICE, "Claim Crystal", CustomItems.createClaimCrystal(plugin));
        }
        else if (slot == SLOT_BEACON)
        {
            purchase(clicker, BEACON_PRICE, "Claim Beacon", CustomItems.createClaimBeacon(plugin));
        }
        else if (slot == SLOT_CLOSE)
        {
            clicker.closeInventory();
        }
    }

    private void purchase(@NotNull Player buyer, double price, @NotNull String itemName, @NotNull ItemStack item)
    {
        db.createAccount(buyer.getUniqueId());

        if (!db.withdraw(buyer.getUniqueId(), price))
        {
            ClaimMessages.error(buyer, "Nicht genug Crystals. Du brauchst &b" + formatPrice(price) + " ✦ Crystal&c.");
            buyer.playSound(buyer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (buyer.getInventory().firstEmpty() == -1)
        {
            // Inventar voll – Geld zurückgeben
            db.deposit(buyer.getUniqueId(), price);
            ClaimMessages.error(buyer, "Dein Inventar ist voll!");
            buyer.playSound(buyer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        buyer.getInventory().addItem(item);
        ClaimMessages.success(buyer, "Du hast &b1x " + itemName + "&a für &b" + formatPrice(price) + " ✦ Crystal&a gekauft.");
        buyer.playSound(buyer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);

        // GUI-Info aktualisieren
        buyer.closeInventory();
    }

    private static String formatPrice(double amount)
    {
        long val = (long) amount;
        if (val >= 1000)
        {
            String s = Long.toString(val);
            StringBuilder sb = new StringBuilder();
            int start = s.length() % 3;
            if (start > 0) sb.append(s, 0, start);
            for (int i = start; i < s.length(); i += 3)
            {
                if (sb.length() > 0) sb.append('.');
                sb.append(s, i, i + 3);
            }
            return sb.toString();
        }
        return Long.toString(val);
    }
}
