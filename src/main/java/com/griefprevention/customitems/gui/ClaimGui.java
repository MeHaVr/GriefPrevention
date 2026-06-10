package com.griefprevention.customitems.gui;

import com.griefprevention.style.NvxStyle;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

public abstract class ClaimGui implements InventoryHolder
{
    public static final String TITLE_PREFIX = NvxStyle.TITLE_PREFIX;
    public static final String TITLE_SUFFIX = NvxStyle.TITLE_SUFFIX;

    // Parst &-Codes und &#RRGGBB Hex-Farben
    private static final LegacyComponentSerializer AMP_HEX = LegacyComponentSerializer.builder()
        .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    // Serialisiert Component zurück in §-basierte Legacy-Strings für setDisplayName/setLore
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    protected Inventory inventory;

    /**
     * Erstellt das Inventar mit ItemsAdder-Hintergrund (falls in guiConfig.yml
     * für den guiKey konfiguriert), sonst mit dem Fallback-Text-Titel.
     */
    protected ClaimGui(int size, @NotNull String guiKey, @NotNull String fallbackTitle)
    {
        // §x§R§R§G§G§B§B-Format – von Minecraft 1.16+ nativ für Hex-Farben unterstützt
        String fallback = LEGACY_SECTION.serialize(AMP_HEX.deserialize(fallbackTitle));
        GuiBackgroundConfig config = GuiBackgroundConfig.getInstance();
        this.inventory = Bukkit.createInventory(this, size,
            config != null ? config.buildTitle(guiKey, fallback) : fallback);
    }

    @Override
    public final @NotNull Inventory getInventory() { return inventory; }

    public abstract void handleClick(int slot, @NotNull Player player);

    /** Wird von GuiManager aufgerufen wenn der Spieler das Inventar schließt. Standard: leer. */
    public void onClose(@NotNull Player player) { }

    public void open(@NotNull Player player)
    {
        player.openInventory(inventory);
    }

    @SuppressWarnings("deprecation")
    protected void setItem(int slot, @NotNull Material material,
                           @NotNull String name, String... lore)
    {
        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null)
        {
            meta.setDisplayName(color(name));
            if (lore.length > 0)
                meta.setLore(Arrays.stream(lore)
                    .map(ClaimGui::color)
                    .collect(Collectors.toList()));
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    /** Gibt das Material für leere Filler-Slots zurück. Kann überschrieben werden. */
    protected Material fillerMaterial() { return Material.BLACK_STAINED_GLASS_PANE; }

    /** Füllt alle leeren Slots mit Filler-Items. */
    @SuppressWarnings("deprecation")
    protected void fill()
    {
        ItemStack glass = new ItemStack(fillerMaterial());
        ItemMeta  meta  = glass.getItemMeta();
        if (meta != null) { meta.setDisplayName(color(NvxStyle.FILLER_NAME)); glass.setItemMeta(meta); }

        for (int i = 0; i < inventory.getSize(); i++)
        {
            ItemStack cur = inventory.getItem(i);
            if (cur == null || cur.getType() == Material.AIR)
                inventory.setItem(i, glass.clone());
        }
    }

    /**
     * Baut einen Breadcrumb-Titel: TITLE_PREFIX + "Teil1 &8» &7Teil2 &8» &7Teil3".
     * Ein einzelner Teil ergibt einfach TITLE_PREFIX + "Teil1".
     */
    protected static @NotNull String title(@NotNull String... parts)
    {
        if (parts.length == 0) return TITLE_PREFIX;
        StringBuilder sb = new StringBuilder(TITLE_PREFIX);
        sb.append(parts[0]);
        for (int i = 1; i < parts.length; i++)
            sb.append(" &8» &7").append(parts[i]);
        return sb.toString();
    }

    /** Setzt einen Zurück/Schließen-Button mit ARROW-Material. */
    protected void setBackItem(int slot, @NotNull String label)
    {
        setItem(slot, Material.ARROW,
            "&8» &7← " + label,
            "",
            "&7&oKlicken zum " + (label.toLowerCase().contains("schließ") ? "Schließen." : "Zurückgehen."),
            "");
    }

    /** Setzt einen Seitennavigations-Button mit FEATHER-Material. */
    protected void setPageItem(int slot, @NotNull String label)
    {
        setItem(slot, Material.FEATHER,
            "&8» &7" + label,
            "",
            "&7&oKlicken zum Blättern.",
            "");
    }

    /**
     * Umschließt Lore-Zeilen mit einer leeren ersten und letzten Zeile.
     * Verwendung: setItem(slot, mat, name, lore("&7Zeile 1", "&7Zeile 2"))
     */
    protected static @NotNull String[] lore(@NotNull String... lines)
    {
        String[] result = new String[lines.length + 2];
        result[0] = "";
        System.arraycopy(lines, 0, result, 1, lines.length);
        result[result.length - 1] = "";
        return result;
    }

    /**
     * Konvertiert &-Codes und &#RRGGBB Hex-Farben in §-basierte Legacy-Strings.
     * Muss für setDisplayName(String) und setLore(List<String>) genutzt werden.
     */
    protected static @NotNull String color(@NotNull String text)
    {
        return LEGACY_SECTION.serialize(AMP_HEX.deserialize(text));
    }
}
