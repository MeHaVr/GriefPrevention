package com.griefprevention.customitems.gui;

import com.griefprevention.customitems.ClaimBeaconStorage;
import com.griefprevention.customitems.ClaimChunkStorage;
import com.griefprevention.customitems.ClaimMessages;
import com.griefprevention.customitems.ClaimVisualizer;
import com.griefprevention.customitems.CustomItems;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 3×3 Chunk-Karte für den Claim Crystal – scrollbar per Navigations-Buttons.
 *
 * Layout (36 Slots, 4 Reihen):
 *   Reihe 0: [G x4] [INFO @4] [G x4]
 *   Reihe 1: [NW@9] [N @10] [NE@11] [G x3] [G] [↑ @16] [G]
 *   Reihe 2: [W @18] [C @19] [E @20] [G x2] [←@23] [●@24] [→@25] [G]
 *   Reihe 3: [SW@27] [S @28] [SE@29] [G x4] [↓@34] [G]
 *
 * Materialien je Status:
 *   LIME_CONCRETE   = angrenzend & frei, genug Crystals  (grün)
 *   ORANGE_CONCRETE = angrenzend & frei, zu wenig        (orange)
 *   BEACON          = eigener Claim                       (gold)
 *   RED_CONCRETE    = fremder Claim                       (rot)
 *   GRAY_CONCRETE   = frei, aber nicht angrenzend         (grau)
 */
public class CrystalMapGui extends ClaimGui
{
    // ── Slot-Konstanten ───────────────────────────────────────────────────────
    private static final int SLOT_INFO       = 4;
    private static final int SLOT_NAV_UP     = 16;
    private static final int SLOT_NAV_LEFT   = 23;
    private static final int SLOT_NAV_CENTER = 24;
    private static final int SLOT_NAV_RIGHT  = 25;
    private static final int SLOT_NAV_DOWN   = 34;

    private static final int[]   MAP_SLOTS = {9, 10, 11, 18, 19, 20, 27, 28, 29};
    private static final int[][] OFFSETS   = {
        {-1, -1}, { 0, -1}, { 1, -1},
        {-1,  0}, { 0,  0}, { 1,  0},
        {-1,  1}, { 0,  1}, { 1,  1}
    };
    private static final String[] DIR_LABELS = {
        "Nord-West", "Norden", "Nord-Ost",
        "Westen",    "Mitte",  "Osten",
        "Süd-West",  "Süden",  "Süd-Ost"
    };

    // ── Felder ────────────────────────────────────────────────────────────────
    private final GriefPrevention    plugin;
    private final Player             player;
    private final int                viewCX;   // Ansichts-Mitte (scrollbar)
    private final int                viewCZ;
    private final int                playerCX; // tatsächlicher Spieler-Chunk (fix)
    private final int                playerCZ;
    private final World              world;
    private final ClaimChunkStorage  chunkStorage;
    private final ClaimBeaconStorage beaconStorage;
    private       int                crystalCount;

    // ── Konstruktoren ─────────────────────────────────────────────────────────

    /** Öffnet die Karte zentriert auf den Spieler. */
    public CrystalMapGui(@NotNull GriefPrevention plugin, @NotNull Player player,
                         @NotNull ClaimChunkStorage chunkStorage,
                         @NotNull ClaimBeaconStorage beaconStorage)
    {
        this(plugin, player, chunkStorage, beaconStorage,
             player.getLocation().getChunk().getX(),
             player.getLocation().getChunk().getZ());
    }

    /** Privater Konstruktor für Scroll-Navigation. */
    private CrystalMapGui(@NotNull GriefPrevention plugin, @NotNull Player player,
                          @NotNull ClaimChunkStorage chunkStorage,
                          @NotNull ClaimBeaconStorage beaconStorage,
                          int viewCX, int viewCZ)
    {
        super(36, "crystal_map", title("Crystal-Karte"));
        this.plugin        = plugin;
        this.player        = player;
        this.viewCX        = viewCX;
        this.viewCZ        = viewCZ;
        this.playerCX      = player.getLocation().getChunk().getX();
        this.playerCZ      = player.getLocation().getChunk().getZ();
        this.world         = player.getWorld();
        this.chunkStorage  = chunkStorage;
        this.beaconStorage = beaconStorage;
        build();
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void build()
    {
        crystalCount = countCrystals(player);

        setItem(SLOT_INFO, Material.NETHER_STAR,
            "&8» &e&lChunk-Karte",
            "",
            "&8» &7Legende&8:",
            "&8» &a■ &7Claimbar &8(1 ✦ Crystal)",
            "&8» &e■ &7Zu wenig Crystals",
            "&8» &6■ &7Dein Claim",
            "&8» &c■ &7Fremder Claim",
            "&8» &7■ &7Nicht angrenzend",
            "",
            "&8» &7Ansichts-Mitte&8: &eChunk " + viewCX + "&8, &e" + viewCZ,
            "&8» &7Du stehst&8:      &eChunk " + playerCX + "&8, &e" + playerCZ,
            "&8» &7Deine Crystals&8: &b" + crystalCount + "x &7✦",
            "");

        // Navigations-Buttons
        setItem(SLOT_NAV_UP,    Material.FEATHER,
            "&8» &7↑ Norden",
            "",
            "&8» &7&oKlicken zum Verschieben.",
            "");
        setItem(SLOT_NAV_DOWN,  Material.FEATHER,
            "&8» &7↓ Süden",
            "",
            "&8» &7&oKlicken zum Verschieben.",
            "");
        setItem(SLOT_NAV_LEFT,  Material.FEATHER,
            "&8» &7← Westen",
            "",
            "&8» &7&oKlicken zum Verschieben.",
            "");
        setItem(SLOT_NAV_RIGHT, Material.FEATHER,
            "&8» &7→ Osten",
            "",
            "&8» &7&oKlicken zum Verschieben.",
            "");
        setItem(SLOT_NAV_CENTER, Material.COMPASS,
            "&8» &7● Zentrieren",
            "",
            "&8» &7Ansicht auf deinen Standort zurücksetzen.",
            "",
            "&8» &7Du&8: &eChunk " + playerCX + "&8, &e" + playerCZ,
            "");

        List<Claim> ownClaims = plugin.dataStore
            .getPlayerData(player.getUniqueId()).getClaims();

        Set<Long> ownedSet = getOwnedChunkSet(ownClaims);
        for (int i = 0; i < MAP_SLOTS.length; i++)
            buildChunkSlot(MAP_SLOTS[i], i, ownClaims, ownedSet);

        fill();
    }

    private void buildChunkSlot(int slot, int idx,
                                 @NotNull List<Claim> ownClaims,
                                 @NotNull Set<Long> ownedSet)
    {
        int    dx  = OFFSETS[idx][0];
        int    dz  = OFFSETS[idx][1];
        int    cx  = viewCX + dx;
        int    cz  = viewCZ + dz;
        String dir = DIR_LABELS[idx];

        boolean isPlayerHere = (cx == playerCX && cz == playerCZ);
        String  hereSuffix   = isPlayerHere ? " &b(du bist hier)" : "";

        int   midX     = cx * 16 + 8;
        int   midZ     = cz * 16 + 8;
        Claim existing = plugin.dataStore.getClaimAt(
            world.getBlockAt(midX, 64, midZ).getLocation(), true, null);

        if (existing != null)
        {
            if (existing.ownerID.equals(player.getUniqueId()))
            {
                setItem(slot, Material.BEACON,
                    "&8» &6&l✦ Dein Claim" + hereSuffix,
                    "",
                    "&8» &7Chunk&8: &e" + cx + "&8, &e" + cz,
                    "&8» &7Richtung&8: &7" + dir,
                    "");
            }
            else
            {
                setItem(slot, Material.RED_CONCRETE,
                    "&8» &c&l✖ Belegt &8– &c" + existing.getOwnerName() + hereSuffix,
                    "",
                    "&8» &7Chunk&8: &e" + cx + "&8, &e" + cz,
                    "&8» &7Richtung&8: &7" + dir,
                    "");
            }
            return;
        }

        if (!isAdjacentToSet(cx, cz, ownedSet))
        {
            setItem(slot, Material.GRAY_CONCRETE,
                "&8» &7Chunk &e" + cx + "&8, &e" + cz + hereSuffix,
                "",
                "&8» &7Richtung&8: &7" + dir,
                "&8» &7Nicht angrenzend an deinen Claim.",
                "");
            return;
        }

        if (crystalCount >= 1)
        {
            setItem(slot, Material.LIME_CONCRETE,
                "&8» &a&l✔ Claimen &8– &aChunk &e" + cx + "&8, &e" + cz + hereSuffix,
                "",
                "&8» &7Richtung&8: &7" + dir,
                "&8» &7Kosten&8:   &b1x &7✦ Crystal",
                "",
                "&8» &a&oKlicken zum Bestätigen.",
                "");
        }
        else
        {
            setItem(slot, Material.ORANGE_CONCRETE,
                "&8» &c✖ Zu wenig Crystals" + hereSuffix,
                "",
                "&8» &7Richtung&8: &7" + dir,
                "&8» &7Kosten&8:   &b1x &7✦ Crystal",
                "&8» &c0 &8/ &71 &7benötigt",
                "");
        }
    }

    // ── Click-Handling ────────────────────────────────────────────────────────

    @Override
    public void handleClick(int slot, @NotNull Player player)
    {
        // Navigations-Buttons
        switch (slot)
        {
            case SLOT_NAV_UP ->
                new CrystalMapGui(plugin, player, chunkStorage, beaconStorage,
                    viewCX, viewCZ - 1).open(player);
            case SLOT_NAV_DOWN ->
                new CrystalMapGui(plugin, player, chunkStorage, beaconStorage,
                    viewCX, viewCZ + 1).open(player);
            case SLOT_NAV_LEFT ->
                new CrystalMapGui(plugin, player, chunkStorage, beaconStorage,
                    viewCX - 1, viewCZ).open(player);
            case SLOT_NAV_RIGHT ->
                new CrystalMapGui(plugin, player, chunkStorage, beaconStorage,
                    viewCX + 1, viewCZ).open(player);
            case SLOT_NAV_CENTER ->
                new CrystalMapGui(plugin, player, chunkStorage, beaconStorage,
                    playerCX, playerCZ).open(player);
            default -> handleMapClick(slot, player);
        }
    }

    private void handleMapClick(int slot, @NotNull Player player)
    {
        for (int i = 0; i < MAP_SLOTS.length; i++)
        {
            if (MAP_SLOTS[i] != slot) continue;

            ItemStack item = inventory.getItem(slot);
            if (item == null) return;

            if (item.getType() == Material.ORANGE_CONCRETE)
            {
                ClaimMessages.error(player, "Du hast nicht genug Crystals für diesen Chunk.");
                return;
            }
            if (item.getType() != Material.LIME_CONCRETE) return;

            int cx = viewCX + OFFSETS[i][0];
            int cz = viewCZ + OFFSETS[i][1];

            new ConfirmGui(
                "Chunk &8(&e" + cx + "&8, &e" + cz + "&8) &aclaimen&8?",
                () -> claimChunk(player, cx, cz),
                () -> this.open(player)
            ).open(player);
            return;
        }
    }

    // ── Claim-Logik ───────────────────────────────────────────────────────────

    private void claimChunk(@NotNull Player player, int tx, int tz)
    {
        int x1 = tx * 16;
        int x2 = tx * 16 + 15;
        int z1 = tz * 16;
        int z2 = tz * 16 + 15;
        int y1 = world.getMinHeight();
        int y2 = world.getMaxHeight() - 1;

        java.util.List<Claim> ownClaims = plugin.dataStore
            .getPlayerData(player.getUniqueId()).getClaims();
        Long beaconId = findBeaconIdForChunk(tx, tz, ownClaims);

        CreateClaimResult result = plugin.dataStore.createClaim(
            world, x1, x2, y1, y2, z1, z2,
            player.getUniqueId(), null, null, player);

        if (!result.succeeded)
        {
            String owner = result.claim != null ? result.claim.getOwnerName() : "einem anderen Spieler";
            ClaimMessages.error(player, "Chunk belegt – gehört bereits &l" + owner + "&c.");
            return;
        }

        if (beaconId != null)
            chunkStorage.linkCrystal(beaconId, result.claim.getID());

        consumeOneCrystal(player);

        int count = plugin.dataStore.getPlayerData(player.getUniqueId()).getClaims().size();
        ClaimVisualizer.showSuccess(plugin, player, x1, z1, x2, z2);
        ClaimMessages.success(player,
            "Chunk &8(&e" + tx + "&8, &e" + tz + "&8) &ageclaimed! "
            + "Du hast jetzt &l" + count + "&a Claim" + (count == 1 ? "" : "s") + ".");
    }

    // ── Chunk-Hilfsmethoden ───────────────────────────────────────────────────

    /** Packed chunk key: obere 32 Bit = cx, untere 32 Bit = cz (unsigned). */
    private static long chunkKey(int cx, int cz)
    {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /** Baut eine Menge aller geclaimed Chunk-Koordinaten des Spielers. */
    private @NotNull Set<Long> getOwnedChunkSet(@NotNull List<Claim> ownClaims)
    {
        Set<Long> set = new HashSet<>(ownClaims.size() * 2);
        for (Claim c : ownClaims)
        {
            int cx = c.getLesserBoundaryCorner().getBlockX() >> 4;
            int cz = c.getLesserBoundaryCorner().getBlockZ() >> 4;
            set.add(chunkKey(cx, cz));
        }
        return set;
    }

    /** True wenn (tx, tz) direkt an einen geclaimed Chunk angrenzt (N/S/E/W). */
    private boolean isAdjacentToSet(int tx, int tz, @NotNull Set<Long> ownedChunks)
    {
        return ownedChunks.contains(chunkKey(tx - 1, tz))
            || ownedChunks.contains(chunkKey(tx + 1, tz))
            || ownedChunks.contains(chunkKey(tx, tz - 1))
            || ownedChunks.contains(chunkKey(tx, tz + 1));
    }

    /** Findet den Beacon-Claim der an (tx, tz) angrenzt, oder null. */
    private @Nullable Long findBeaconIdForChunk(int tx, int tz,
                                                 @NotNull List<Claim> ownClaims)
    {
        for (Claim claim : ownClaims)
        {
            int cx = claim.getLesserBoundaryCorner().getBlockX() >> 4;
            int cz = claim.getLesserBoundaryCorner().getBlockZ() >> 4;
            boolean adjacent = (cx == tx && Math.abs(cz - tz) == 1)
                             || (cz == tz && Math.abs(cx - tx) == 1);
            if (!adjacent) continue;
            if (chunkStorage.isBeaconClaim(claim.getID())) return claim.getID();
            Long beacon = chunkStorage.getBeaconId(claim.getID());
            if (beacon != null) return beacon;
        }
        return null;
    }

    // ── Inventar-Hilfsmethoden ────────────────────────────────────────────────

    private int countCrystals(@NotNull Player player)
    {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents())
            if (CustomItems.isClaimCrystal(plugin, stack))
                count += stack.getAmount();
        return count;
    }

    private void consumeOneCrystal(@NotNull Player player)
    {
        for (int i = 0; i < player.getInventory().getSize(); i++)
        {
            ItemStack stack = player.getInventory().getItem(i);
            if (!CustomItems.isClaimCrystal(plugin, stack)) continue;
            if (stack.getAmount() > 1) stack.setAmount(stack.getAmount() - 1);
            else player.getInventory().setItem(i, null);
            return;
        }
    }

}
