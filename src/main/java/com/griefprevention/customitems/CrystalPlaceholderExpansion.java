package com.griefprevention.customitems;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * PlaceholderAPI-Expansion für das Crystal-Economy-System.
 *
 * Verfügbare Placeholders:
 *   %gp_crystals%              – Kontostand (roh, ganzzahlig)
 *   %gp_crystals_formatted%    – "1.250"
 *   %gp_crystals_symbol%       – "1.250 ✦"
 *   %gp_crystals_full%         – "1.250 ✦ Crystal"
 *   %gp_claims%                – Anzahl eigener Claims
 *   %gp_crystals_top_N_name%   – Name des N-ten Top-Spielers
 *   %gp_crystals_top_N_score%  – Kontostand des N-ten Top-Spielers (formatiert)
 */
public class CrystalPlaceholderExpansion extends PlaceholderExpansion
{
    private static final DecimalFormat FMT;
    static
    {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.GERMANY);
        FMT = new DecimalFormat("#,##0", sym);
    }

    private final GriefPrevention plugin;
    private final CrystalDatabase db;

    public CrystalPlaceholderExpansion(@NotNull GriefPrevention plugin,
                                        @NotNull CrystalDatabase db)
    {
        this.plugin = plugin;
        this.db     = db;
    }

    @Override public @NotNull String getIdentifier() { return "gp"; }
    @Override public @NotNull String getAuthor()     { return "Norvex"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(@Nullable Player player, @NotNull String params)
    {
        if (player == null) return "";

        return switch (params)
        {
            case "crystals"           -> String.valueOf((long) db.getBalance(player.getUniqueId()));
            case "crystals_formatted" -> fmt(db.getBalance(player.getUniqueId()));
            case "crystals_symbol"    -> fmt(db.getBalance(player.getUniqueId())) + " ✦";
            case "crystals_full"      -> fmt(db.getBalance(player.getUniqueId())) + " ✦ Crystal";
            case "claims"             -> String.valueOf(
                                            plugin.dataStore
                                                .getPlayerData(player.getUniqueId())
                                                .getClaims().size());
            default                   -> handleTop(params);
        };
    }

    /** Parst %gp_crystals_top_N_name% und %gp_crystals_top_N_score%. */
    private @NotNull String handleTop(@NotNull String params)
    {
        if (!params.startsWith("crystals_top_")) return "";
        String[] parts = params.split("_");
        // Format: crystals_top_N_name  → parts: [crystals, top, N, name]
        if (parts.length != 4) return "";
        int rank;
        try { rank = Integer.parseInt(parts[2]); }
        catch (NumberFormatException e) { return ""; }

        List<CrystalDatabase.TopEntry> top = db.getTop(rank);
        if (rank < 1 || rank > top.size()) return "";

        CrystalDatabase.TopEntry entry = top.get(rank - 1);
        return switch (parts[3])
        {
            case "name"  -> {
                var op = Bukkit.getOfflinePlayer(entry.uuid());
                yield op.getName() != null ? op.getName() : entry.uuid().toString();
            }
            case "score" -> fmt(entry.balance()) + " ✦";
            default      -> "";
        };
    }

    private static @NotNull String fmt(double amount)
    {
        return FMT.format((long) amount);
    }
}
