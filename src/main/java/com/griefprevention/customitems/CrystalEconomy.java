package com.griefprevention.customitems;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Vault Economy Provider für die Crystal-Währung.
 * Wird nur registriert wenn Vault auf dem Server installiert ist.
 */
@SuppressWarnings("deprecation")
public class CrystalEconomy implements Economy
{
    private final Plugin plugin;
    private final CrystalDatabase db;

    public CrystalEconomy(@NotNull Plugin plugin, @NotNull CrystalDatabase db)
    {
        this.plugin = plugin;
        this.db     = db;
    }

    @Override public boolean isEnabled()           { return true; }
    @Override public String getName()              { return "Crystal"; }
    @Override public boolean hasBankSupport()      { return false; }
    @Override public int fractionalDigits()        { return 0; }
    @Override public String currencyNamePlural()   { return "Crystals"; }
    @Override public String currencyNameSingular() { return "Crystal"; }

    @Override
    public String format(double amount)
    {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.GERMAN);
        DecimalFormat fmt = new DecimalFormat("#,##0", sym);
        return fmt.format((long) amount) + " ✦ Crystal";
    }

    // ---- Account (OfflinePlayer) ----

    @Override public boolean hasAccount(@NotNull OfflinePlayer p)              { return db.hasAccount(p.getUniqueId()); }
    @Override public boolean hasAccount(@NotNull OfflinePlayer p, String w)    { return hasAccount(p); }
    @Override public boolean createPlayerAccount(@NotNull OfflinePlayer p)     { return db.createAccount(p.getUniqueId()); }
    @Override public boolean createPlayerAccount(@NotNull OfflinePlayer p, String w) { return createPlayerAccount(p); }

    // ---- Account (String – deprecated, nicht implementiert) ----

    @Override public boolean hasAccount(String n)             { return false; }
    @Override public boolean hasAccount(String n, String w)   { return false; }
    @Override public boolean createPlayerAccount(String n)    { return false; }
    @Override public boolean createPlayerAccount(String n, String w) { return false; }

    // ---- Balance (OfflinePlayer) ----

    @Override public double getBalance(@NotNull OfflinePlayer p)              { return db.getBalance(p.getUniqueId()); }
    @Override public double getBalance(@NotNull OfflinePlayer p, String w)    { return getBalance(p); }
    @Override public boolean has(@NotNull OfflinePlayer p, double amount)     { return db.getBalance(p.getUniqueId()) >= amount; }
    @Override public boolean has(@NotNull OfflinePlayer p, String w, double a){ return has(p, a); }

    // ---- Balance (String – deprecated) ----

    @Override public double getBalance(String n)              { return 0; }
    @Override public double getBalance(String n, String w)    { return 0; }
    @Override public boolean has(String n, double a)          { return false; }
    @Override public boolean has(String n, String w, double a){ return false; }

    // ---- Transactions (OfflinePlayer) ----

    @Override
    public EconomyResponse withdrawPlayer(@NotNull OfflinePlayer p, double amount)
    {
        db.createAccount(p.getUniqueId());
        if (!has(p, amount))
            return new EconomyResponse(0, getBalance(p), EconomyResponse.ResponseType.FAILURE, "Nicht genug Crystals.");
        db.withdraw(p.getUniqueId(), amount);
        return new EconomyResponse(amount, getBalance(p), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override public EconomyResponse withdrawPlayer(@NotNull OfflinePlayer p, String w, double a) { return withdrawPlayer(p, a); }

    @Override
    public EconomyResponse depositPlayer(@NotNull OfflinePlayer p, double amount)
    {
        db.createAccount(p.getUniqueId());
        db.deposit(p.getUniqueId(), amount);
        return new EconomyResponse(amount, getBalance(p), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override public EconomyResponse depositPlayer(@NotNull OfflinePlayer p, String w, double a) { return depositPlayer(p, a); }

    // ---- Transactions (String – deprecated) ----

    @Override public EconomyResponse withdrawPlayer(String n, double a)          { return notImpl(); }
    @Override public EconomyResponse withdrawPlayer(String n, String w, double a){ return notImpl(); }
    @Override public EconomyResponse depositPlayer(String n, double a)           { return notImpl(); }
    @Override public EconomyResponse depositPlayer(String n, String w, double a) { return notImpl(); }

    // ---- Bank (nicht unterstützt) ----

    @Override public EconomyResponse createBank(String n, OfflinePlayer p)  { return notImpl(); }
    @Override public EconomyResponse createBank(String n, String p)          { return notImpl(); }
    @Override public EconomyResponse deleteBank(String n)                    { return notImpl(); }
    @Override public EconomyResponse bankBalance(String n)                   { return notImpl(); }
    @Override public EconomyResponse bankHas(String n, double a)             { return notImpl(); }
    @Override public EconomyResponse bankWithdraw(String n, double a)        { return notImpl(); }
    @Override public EconomyResponse bankDeposit(String n, double a)         { return notImpl(); }
    @Override public EconomyResponse isBankOwner(String n, OfflinePlayer p)  { return notImpl(); }
    @Override public EconomyResponse isBankOwner(String n, String p)         { return notImpl(); }
    @Override public EconomyResponse isBankMember(String n, OfflinePlayer p) { return notImpl(); }
    @Override public EconomyResponse isBankMember(String n, String p)        { return notImpl(); }
    @Override public List<String> getBanks()                                  { return List.of(); }

    private static EconomyResponse notImpl()
    {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Nicht unterstützt.");
    }
}
