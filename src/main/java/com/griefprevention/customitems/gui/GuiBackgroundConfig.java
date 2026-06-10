package com.griefprevention.customitems.gui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zentrale Konfiguration der ItemsAdder-Hintergrundbilder für alle GUIs.
 *
 * Liest guiConfig.yml (wird beim ersten Start aus den Resources extrahiert).
 * Pro GUI-Key ein Font-Image (namespace:id) + Pixel-Offset. Ist kein Bild
 * konfiguriert oder ItemsAdder nicht verfügbar, wird der normale Text-Titel
 * verwendet.
 */
public final class GuiBackgroundConfig
{
    private static @Nullable GuiBackgroundConfig instance;

    public static void init(@NotNull Plugin plugin) { instance = new GuiBackgroundConfig(plugin); }

    public static @Nullable GuiBackgroundConfig getInstance() { return instance; }

    private record Background(@NotNull String image, int offset) {}

    private final Plugin plugin;
    private final Map<String, Background> backgrounds = new HashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    private GuiBackgroundConfig(@NotNull Plugin plugin)
    {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "guiConfig.yml");
        if (!file.exists()) plugin.saveResource("guiConfig.yml", false);

        ConfigurationSection section = YamlConfiguration.loadConfiguration(file)
            .getConfigurationSection("backgrounds");
        if (section == null) return;

        for (String key : section.getKeys(false))
        {
            String image = section.getString(key + ".image", "");
            backgrounds.put(key, new Background(
                image == null ? "" : image.trim(),
                section.getInt(key + ".offset", -50)));
        }
    }

    /**
     * Baut den Inventar-Titel für ein GUI: ItemsAdder-Hintergrundbild wenn
     * konfiguriert und verfügbar, sonst der übergebene Fallback-Titel.
     */
    public @NotNull String buildTitle(@NotNull String guiKey, @NotNull String fallbackTitle)
    {
        Background bg = backgrounds.get(guiKey);
        if (bg == null || bg.image().isEmpty()) return fallbackTitle;

        try
        {
            var wrapper = new dev.lone.itemsadder.api.FontImages.FontImageWrapper(bg.image());
            if (wrapper.exists())
            {
                // §f hellt das Bild auf, ohne ein sichtbares Zeichen zu erzeugen.
                return "§f" + wrapper.applyPixelsOffset(bg.offset());
            }
            warnOnce(guiKey, "IA-Image nicht gefunden: " + bg.image());
        }
        catch (Exception | Error e)
        {
            warnOnce(guiKey, "ItemsAdder nicht verfügbar (" + e.getClass().getSimpleName() + ")");
        }
        return fallbackTitle;
    }

    private void warnOnce(@NotNull String guiKey, @NotNull String message)
    {
        if (warned.add(guiKey))
            plugin.getLogger().warning("[GUI] '" + guiKey + "': " + message + " – nutze Text-Titel.");
    }
}
