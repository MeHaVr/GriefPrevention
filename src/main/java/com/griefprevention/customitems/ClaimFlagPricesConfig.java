package com.griefprevention.customitems;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Lädt Umschalt-Kosten für Claim-Flags aus claimFlagPrices.yml.
 * Singleton – wird in GriefPrevention.java initialisiert.
 */
public class ClaimFlagPricesConfig
{
    private static @Nullable ClaimFlagPricesConfig instance;

    public static @Nullable ClaimFlagPricesConfig getInstance() { return instance; }

    private final YamlConfiguration config;

    public ClaimFlagPricesConfig(@NotNull Plugin plugin)
    {
        instance = this;
        File file = new File(plugin.getDataFolder(), "claimFlagPrices.yml");
        if (!file.exists())
            plugin.saveResource("claimFlagPrices.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        InputStream defaults = plugin.getResource("claimFlagPrices.yml");
        if (defaults != null)
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults)));
    }

    /** Gibt den Preis (in Crystals) für das Umschalten des angegebenen Flags zurück. */
    public double getPrice(@NotNull String flag)
    {
        return config.getDouble("prices." + flag, 0.0);
    }
}
