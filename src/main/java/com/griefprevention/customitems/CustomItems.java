package com.griefprevention.customitems;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class CustomItems
{
    static final String KEY_ITEM_TYPE = "item_type";
    static final String KEY_ITEM_UUID = "item_uuid";
    private static final String TYPE_CRYSTAL = "crystal";
    private static final String TYPE_BEACON  = "beacon";

    // Hex-bewusster Serializer für Item-DisplayNames und Lore
    private static final LegacyComponentSerializer AMP_HEX = LegacyComponentSerializer.builder()
        .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    @SuppressWarnings("deprecation")
    public static ItemStack createClaimCrystal(Plugin plugin)
    {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta  meta = item.getItemMeta();

        // Display: Gradient Blau→Lila
        meta.setDisplayName(color("&#9BE1E3&lClaim &b&lCrystal"));
        meta.setLore(List.of(
            color(""),
            color("&8▸ &7Claim Crystal"),
            color(""),
            color("&7Öffnet die Chunk-Karte und erlaubt"),
            color("&7das Beanspruchen angrenzender Chunks."),
            color(""),
            color("&8» &bRechtsklick &8→ &7Karte öffnen")
        ));
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, KEY_ITEM_TYPE),
            PersistentDataType.STRING, TYPE_CRYSTAL);
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, KEY_ITEM_UUID),
            PersistentDataType.STRING, UUID.randomUUID().toString());

        item.setItemMeta(meta);
        AntiDupeManager.register(item, plugin);
        return item;
    }

    public static boolean isClaimCrystal(Plugin plugin, ItemStack item)
    {
        if (item == null || !item.hasItemMeta()) return false;
        return TYPE_CRYSTAL.equals(item.getItemMeta().getPersistentDataContainer()
            .get(new NamespacedKey(plugin, KEY_ITEM_TYPE), PersistentDataType.STRING));
    }

    @SuppressWarnings("deprecation")
    public static ItemStack createClaimBeacon(Plugin plugin)
    {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta  meta = item.getItemMeta();

        // Display: Gold→Gelb Gradient
        meta.setDisplayName(color("&#FFD700&lClaim &6&lBeacon"));
        meta.setLore(List.of(
            color(""),
            color("&8▸ &7Claim Beacon"),
            color(""),
            color("&7Platziere den Beacon, um einen"),
            color("&7Chunk dauerhaft zu beanspruchen."),
            color(""),
            color("&8» &ePlatzieren &8→ &7Chunk claimen"),
            color("&8» &bRechtsklick &8→ &7Details anzeigen")
        ));
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, KEY_ITEM_TYPE),
            PersistentDataType.STRING, TYPE_BEACON);
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, KEY_ITEM_UUID),
            PersistentDataType.STRING, UUID.randomUUID().toString());

        item.setItemMeta(meta);
        AntiDupeManager.register(item, plugin);
        return item;
    }

    public static boolean isClaimBeacon(Plugin plugin, ItemStack item)
    {
        if (item == null || !item.hasItemMeta()) return false;
        return TYPE_BEACON.equals(item.getItemMeta().getPersistentDataContainer()
            .get(new NamespacedKey(plugin, KEY_ITEM_TYPE), PersistentDataType.STRING));
    }

    /** Gibt die Anti-Dupe-UUID des Items zurück, oder null wenn keine vorhanden. */
    public static @Nullable UUID getItemUuid(Plugin plugin, ItemStack item)
    {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer()
            .get(new NamespacedKey(plugin, KEY_ITEM_UUID), PersistentDataType.STRING);
        if (raw == null) return null;
        try { return UUID.fromString(raw); }
        catch (IllegalArgumentException e) { return null; }
    }

    /** Gibt true zurück wenn das Item ein Custom Item (Beacon oder Crystal) ist. */
    public static boolean isCustomItem(Plugin plugin, ItemStack item)
    {
        return isClaimBeacon(plugin, item) || isClaimCrystal(plugin, item);
    }

    /** Konvertiert &-Codes und &#RRGGBB Hex-Farben in §-basierte Legacy-Strings. */
    private static String color(String text)
    {
        return SECTION.serialize(AMP_HEX.deserialize(text));
    }
}
