package com.griefprevention.customitems.gui;

import com.griefprevention.customitems.ClaimChunkStorage;
import com.griefprevention.customitems.ClaimMessages;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Admin-GUI: Übersicht aller Claims eines Spielers mit Teleport-Funktion.
 *
 * Layout (54 Slots, 6 Reihen):
 *   Reihen 0-4 (Slots 0-44): Claim-Items (max 45 pro Seite)
 *   Reihe 5: Slot 46 Zurück, Slot 49 Info, Slot 52 Weiter, Slot 53 Schließen
 */
public class ClaimLookupGui extends ClaimGui
{
    private static final int PAGE_SIZE  = 45;
    private static final int SLOT_PREV  = 46;
    private static final int SLOT_INFO  = 49;
    private static final int SLOT_NEXT  = 52;
    private static final int SLOT_CLOSE = 53;

    private final GriefPrevention   plugin;
    private final ClaimChunkStorage chunkStorage;
    private final String            targetName;
    private final List<Claim>       claims;
    private final int               page;

    public ClaimLookupGui(@NotNull GriefPrevention plugin,
                          @NotNull ClaimChunkStorage chunkStorage,
                          @NotNull String targetName,
                          @NotNull List<Claim> claims)
    {
        this(plugin, chunkStorage, targetName, claims, 0);
    }

    private ClaimLookupGui(@NotNull GriefPrevention plugin,
                           @NotNull ClaimChunkStorage chunkStorage,
                           @NotNull String targetName,
                           @NotNull List<Claim> claims,
                           int page)
    {
        super(54, title("Admin", "Lookup: " + targetName));
        this.plugin       = plugin;
        this.chunkStorage = chunkStorage;
        this.targetName   = targetName;
        this.claims       = claims;
        this.page         = page;
        build();
    }

    private void build()
    {
        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, claims.size());

        for (int i = start; i < end; i++)
        {
            Claim   claim    = claims.get(i);
            int     cx       = claim.getLesserBoundaryCorner().getBlockX() >> 4;
            int     cz       = claim.getLesserBoundaryCorner().getBlockZ() >> 4;
            String  world    = claim.getLesserBoundaryCorner().getWorld().getName();
            boolean isBeacon = chunkStorage.isBeaconClaim(claim.getID());
            int     crystals = isBeacon ? chunkStorage.getChildren(claim.getID()).size() : 0;

            setItem(i - start,
                isBeacon ? Material.BEACON : Material.AMETHYST_SHARD,
                "&8» &e&lChunk &b" + cx + "&8, &b" + cz,
                "",
                "&8» &7Welt&8: &b" + world,
                "&8» &7Typ&8:  " + (isBeacon ? "&6Beacon-Claim" : "&bCrystal-Chunk"),
                isBeacon ? "&8» &7Crystals&8: &e" + crystals + " &8(&e" + (1 + crystals) + " Chunks gesamt&8)" : "",
                "",
                "&8» &7&oKlicken zum Teleportieren.",
                "");
        }

        if (page > 0)
            setItem(SLOT_PREV, Material.FEATHER,
                "&8» &7◄ Vorherige Seite",
                "", "&8» &7&oKlicken zum Blättern.", "");

        if (end < claims.size())
            setItem(SLOT_NEXT, Material.FEATHER,
                "&8» &7Nächste Seite ►",
                "", "&8» &7&oKlicken zum Blättern.", "");

        int totalPages = Math.max(1, (int) Math.ceil((double) claims.size() / PAGE_SIZE));
        setItem(SLOT_INFO, Material.EMERALD,
            "&8» &9" + targetName,
            "",
            "&8» &7Claims gesamt&8: &e" + claims.size(),
            "&8» &7Seite&8: &e" + (page + 1) + " &8/ &e" + totalPages,
            "");

        setItem(SLOT_CLOSE, Material.ARROW,
            "&8» &7← Schließen",
            "", "&8» &7&oKlicken zum Schließen.", "");

        fill();
    }

    @Override
    public void handleClick(int slot, @NotNull Player player)
    {
        if (slot == SLOT_CLOSE) { player.closeInventory(); return; }
        if (slot == SLOT_PREV && page > 0)
        {
            new ClaimLookupGui(plugin, chunkStorage, targetName, claims, page - 1).open(player);
            return;
        }
        if (slot == SLOT_NEXT && page * PAGE_SIZE + PAGE_SIZE < claims.size())
        {
            new ClaimLookupGui(plugin, chunkStorage, targetName, claims, page + 1).open(player);
            return;
        }

        int idx = page * PAGE_SIZE + slot;
        if (slot >= PAGE_SIZE || idx >= claims.size()) return;

        Claim claim = claims.get(idx);
        int   cx    = claim.getLesserBoundaryCorner().getBlockX() >> 4;
        int   cz    = claim.getLesserBoundaryCorner().getBlockZ() >> 4;
        World world = claim.getLesserBoundaryCorner().getWorld();
        if (world == null) { ClaimMessages.error(player, "Welt nicht geladen."); return; }

        int tpX = cx * 16 + 8;
        int tpZ = cz * 16 + 8;
        int tpY = world.getHighestBlockYAt(tpX, tpZ) + 1;

        player.closeInventory();
        player.teleport(new Location(world, tpX + 0.5, tpY, tpZ + 0.5,
            player.getLocation().getYaw(), player.getLocation().getPitch()));
        ClaimMessages.success(player,
            "Teleportiert zu Chunk &e" + cx + "&8, &e" + cz + " &8(" + world.getName() + "&8)&a.");
    }
}
