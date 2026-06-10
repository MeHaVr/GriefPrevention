package com.norvex.crystaleconomy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Vault Economy Implementierung für die Crystal-Währung des Norvex-Servers.
 * Registrierung: ServicesManager.register(Economy.class, instance, plugin, ServicePriority.Normal)
 */
public class CrystalEconomy implements Economy
{
    private static final DecimalFormat FORMAT;

    static
    {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.GERMAN);
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        FORMAT = new DecimalFormat("#,##0.##", sym);
    }

    private final CrystalDatabase db;

    public CrystalEconomy(@NotNull CrystalDatabase db)
    {
        this.db = db;
    }

    // ------- Metadaten -------

    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "Crystal"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 0; }

    @Override
    public String format(double amount)
    {
        return FORMAT.format(Math.max(0, amount)) + " ✦ Crystal";
    }

    @Override public String currencyNamePlural() { return "Crystals"; }
    @Override public String currencyNameSingular() { return "Crystal"; }

    // ------- Konten -------

    @Override
    public boolean hasAccount(@NotNull OfflinePlayer player)
    {
        return db.hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(@NotNull String playerName)
    {
        return false; // UUID-basiert, Name nicht unterstützt
    }

    @Override
    public boolean hasAccount(@NotNull OfflinePlayer player, @NotNull String worldName)
    {
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(@NotNull String playerName, @NotNull String worldName)
    {
        return false;
    }

    @Override
    public boolean createPlayerAccount(@NotNull OfflinePlayer player)
    {
        return db.createAccount(player.getUniqueId());
    }

    @Override
    public boolean createPlayerAccount(@NotNull String playerName)
    {
        return false;
    }

    @Override
    public boolean createPlayerAccount(@NotNull OfflinePlayer player, @NotNull String worldName)
    {
        return createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(@NotNull String playerName, @NotNull String worldName)
    {
        return false;
    }

    // ------- Kontostand -------

    @Override
    public double getBalance(@NotNull OfflinePlayer player)
    {
        ensureAccount(player);
        return db.getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(@NotNull String playerName)
    {
        return 0;
    }

    @Override
    public double getBalance(@NotNull OfflinePlayer player, @NotNull String world)
    {
        return getBalance(player);
    }

    @Override
    public double getBalance(@NotNull String playerName, @NotNull String world)
    {
        return 0;
    }

    @Override
    public boolean has(@NotNull OfflinePlayer player, double amount)
    {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(@NotNull String playerName, double amount)
    {
        return false;
    }

    @Override
    public boolean has(@NotNull OfflinePlayer player, @NotNull String worldName, double amount)
    {
        return has(player, amount);
    }

    @Override
    public boolean has(@NotNull String playerName, @NotNull String worldName, double amount)
    {
        return false;
    }

    // ------- Transaktionen -------

    @Override
    public EconomyResponse withdrawPlayer(@NotNull OfflinePlayer player, double amount)
    {
        if (amount < 0)
            return fail("Betrag muss positiv sein.", getBalance(player));
        ensureAccount(player);
        boolean ok = db.withdraw(player.getUniqueId(), amount);
        if (ok)
            return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
        return fail("Nicht genug Crystals.", getBalance(player));
    }

    @Override
    public EconomyResponse withdrawPlayer(@NotNull String playerName, double amount)
    {
        return fail("Name-basierte Operationen nicht unterstützt.", 0);
    }

    @Override
    public EconomyResponse withdrawPlayer(@NotNull OfflinePlayer player, @NotNull String worldName, double amount)
    {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(@NotNull String playerName, @NotNull String worldName, double amount)
    {
        return fail("Name-basierte Operationen nicht unterstützt.", 0);
    }

    @Override
    public EconomyResponse depositPlayer(@NotNull OfflinePlayer player, double amount)
    {
        if (amount < 0)
            return fail("Betrag muss positiv sein.", getBalance(player));
        ensureAccount(player);
        db.deposit(player.getUniqueId(), amount);
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(@NotNull String playerName, double amount)
    {
        return fail("Name-basierte Operationen nicht unterstützt.", 0);
    }

    @Override
    public EconomyResponse depositPlayer(@NotNull OfflinePlayer player, @NotNull String worldName, double amount)
    {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(@NotNull String playerName, @NotNull String worldName, double amount)
    {
        return fail("Name-basierte Operationen nicht unterstützt.", 0);
    }

    // ------- Banken (nicht unterstützt) -------

    @Override public EconomyResponse createBank(@NotNull String name, @NotNull String player) { return unsupported(); }
    @Override public EconomyResponse createBank(@NotNull String name, @NotNull OfflinePlayer player) { return unsupported(); }
    @Override public EconomyResponse deleteBank(@NotNull String name) { return unsupported(); }
    @Override public EconomyResponse bankBalance(@NotNull String name) { return unsupported(); }
    @Override public EconomyResponse bankHas(@NotNull String name, double amount) { return unsupported(); }
    @Override public EconomyResponse bankWithdraw(@NotNull String name, double amount) { return unsupported(); }
    @Override public EconomyResponse bankDeposit(@NotNull String name, double amount) { return unsupported(); }
    @Override public EconomyResponse isBankOwner(@NotNull String name, @NotNull String playerName) { return unsupported(); }
    @Override public EconomyResponse isBankOwner(@NotNull String name, @NotNull OfflinePlayer player) { return unsupported(); }
    @Override public EconomyResponse isBankMember(@NotNull String name, @NotNull String playerName) { return unsupported(); }
    @Override public EconomyResponse isBankMember(@NotNull String name, @NotNull OfflinePlayer player) { return unsupported(); }
    @Override public List<String> getBanks() { return List.of(); }

    // ------- Hilfsmethoden -------

    private void ensureAccount(@NotNull OfflinePlayer player)
    {
        if (!db.hasAccount(player.getUniqueId()))
            db.createAccount(player.getUniqueId());
    }

    private static @NotNull EconomyResponse fail(@NotNull String msg, double balance)
    {
        return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, msg);
    }

    private static @NotNull EconomyResponse unsupported()
    {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken nicht unterstützt.");
    }
}
