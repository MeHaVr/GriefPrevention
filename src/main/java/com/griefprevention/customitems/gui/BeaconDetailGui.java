package com.griefprevention.customitems.gui;

import com.griefprevention.customitems.ClaimBeaconListener;
import com.griefprevention.customitems.ClaimBeaconStorage;
import com.griefprevention.customitems.ClaimChunkStorage;
import com.griefprevention.customitems.ClaimFlagsStorage;
import com.griefprevention.customitems.ClaimHologramManager;
import com.griefprevention.customitems.ClaimMessages;
import com.griefprevention.customitems.ClaimVisualizer;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rechtsklick-GUI auf einen platzierten Claim Beacon.
 *
 * Layout (27 Slots, 3 Reihen, B = BLACK_STAINED_GLASS_PANE):
 *
 *   R1:  B   B   B  SKL  B  INF  B   B   B
 *   R2:  B  VIS CMP TRU XFR MOV STG  B   B
 *   R3:  B   B   B   B   B   B   B  DEL BCK
 */
public class BeaconDetailGui extends ClaimGui
{
    private static final int SLOT_SKULL      =  3;
    private static final int SLOT_INFO       =  5;
    private static final int SLOT_VISUALIZE  = 10;
    private static final int SLOT_CRYSTALMAP = 11;
    private static final int SLOT_TRUST      = 12;
    private static final int SLOT_TRANSFER   = 13;
    private static final int SLOT_MOVE       = 14;
    private static final int SLOT_SETTINGS   = 15;
    private static final int SLOT_DELETE     = 25;
    private static final int SLOT_BACK       = 26;

    private final GriefPrevention      plugin;
    private final ClaimBeaconStorage   storage;
    private final ClaimHologramManager holoManager;
    private final ClaimFlagsStorage    flagsStorage;
    private final ClaimChunkStorage    chunkStorage;
    private final ClaimBeaconListener  listener;
    private final Claim                claim;
    private final Location             beaconLoc;

    public BeaconDetailGui(@NotNull GriefPrevention plugin,
                           @NotNull ClaimBeaconStorage storage,
                           @NotNull ClaimHologramManager holoManager,
                           @NotNull ClaimFlagsStorage flagsStorage,
                           @NotNull ClaimChunkStorage chunkStorage,
                           @NotNull ClaimBeaconListener listener,
                           @NotNull Claim claim,
                           @NotNull Location beaconLoc)
    {
        super(27, "beacon_detail", title("Claim"));
        this.plugin       = plugin;
        this.storage      = storage;
        this.holoManager  = holoManager;
        this.flagsStorage = flagsStorage;
        this.chunkStorage = chunkStorage;
        this.listener     = listener;
        this.claim        = claim;
        this.beaconLoc    = beaconLoc;
        build();
    }

    private void build()
    {
        int crystalCount = chunkStorage.getChildren(claim.getID()).size();
        int totalChunks  = 1 + crystalCount;
        int memberCount  = countMembers();

        buildSkull();

        setItem(SLOT_INFO, Material.WRITTEN_BOOK,
            "&8» &e&lClaim Info",
            "",
            "&8» &7Chunks&8:     &b" + totalChunks,
            "&8» &7Mitglieder&8: &b" + memberCount,
            "&8» &7Crystals&8:   &b" + crystalCount,
            "",
            "&8» &7Erstellt&8:   &e" + new SimpleDateFormat("dd.MM.yy").format(claim.modifiedDate),
            "");

        setItem(SLOT_VISUALIZE, Material.CYAN_DYE,
            "&8» &7Visualisieren",
            "",
            "&8» &7Zeigt Grenzen für &b15 Sek.",
            "",
            "&8» &7&oKlicken zum Anzeigen.",
            "");

        setItem(SLOT_CRYSTALMAP, Material.MAP,
            "&8» &7Crystal-Karte",
            "",
            "&8» &7Crystal-Chunks&8: &e" + crystalCount,
            "",
            "&8» &7&oKlicken zum Öffnen.",
            "");

        setItem(SLOT_TRUST, Material.EMERALD,
            "&8» &7Vertrauen",
            "",
            "&8» &7Vertraut&8: &e" + memberCount + " &7Spieler",
            "",
            "&8» &7&oKlicken zum Verwalten.",
            "");

        setItem(SLOT_TRANSFER, Material.ENDER_PEARL,
            "&8» &7Claim übertragen",
            "",
            "&8» &c⚠ &7Du verlierst alle Rechte.",
            "",
            "&8» &7&oKlicken und Name eingeben.",
            "");

        setItem(SLOT_MOVE, Material.COMPASS,
            "&8» &7Beacon verschieben",
            "",
            "&8» &8Nur innerhalb dieses Chunks.",
            "",
            "&8» &7&oKlicken zum Aktivieren.",
            "");

        setItem(SLOT_SETTINGS, Material.COMPARATOR,
            "&8» &7Einstellungen",
            "",
            "&8» &7PvP, Explosionen,",
            "&8» &7Mob-Spawning, Hologram.",
            "",
            "&8» &7&oKlicken zum Öffnen.",
            "");

        setItem(SLOT_DELETE, Material.BARRIER,
            "&8» &c&lLöschen",
            "",
            "&8» &7Crystals werden erstattet.",
            "",
            "&8» &c⚠ &7Irreversibel!",
            "",
            "&8» &7&oKlicken zum Löschen.",
            "");

        setBackItem(SLOT_BACK, "Schließen");

        fill();
    }

    @SuppressWarnings("deprecation")
    private void buildSkull()
    {
        if (claim.ownerID == null)
        {
            setItem(SLOT_SKULL, Material.BARRIER,
                "&8» &e&lAdmin-Claim",
                "",
                "&8» &7Gehört dem Server.",
                "");
            return;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(claim.ownerID);
        String ownerName = op.getName() != null ? op.getName() : claim.getOwnerName();
        if (ownerName == null) ownerName = "Unbekannt";

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta != null)
        {
            meta.setOwningPlayer(op);
            meta.setDisplayName(color("&8» &9" + ownerName));
            meta.setLore(List.of(
                color(""),
                color("&8» &7Besitzer"),
                color("")
            ));
            skull.setItemMeta(meta);
        }
        inventory.setItem(SLOT_SKULL, skull);
    }

    private int countMembers()
    {
        ArrayList<String> builders   = new ArrayList<>();
        ArrayList<String> containers = new ArrayList<>();
        ArrayList<String> accessors  = new ArrayList<>();
        ArrayList<String> managers   = new ArrayList<>();
        claim.getPermissions(builders, containers, accessors, managers);
        return builders.size() + containers.size() + accessors.size() + managers.size();
    }

    @Override
    public void handleClick(int slot, @NotNull Player player)
    {
        boolean isOwner = player.getUniqueId().equals(claim.ownerID);
        boolean isAdmin = player.hasPermission("griefprevention.claim.admin");

        switch (slot)
        {
            case SLOT_SETTINGS ->
                new ClaimSettingsGui(plugin, storage, holoManager, flagsStorage,
                    chunkStorage, listener, claim, beaconLoc).open(player);

            case SLOT_MOVE ->
            {
                if (!isOwner && !isAdmin)
                {
                    ClaimMessages.error(player, "Nur der Besitzer kann den Beacon verschieben.");
                    return;
                }
                player.closeInventory();
                listener.enterMoveMode(player, beaconLoc);
            }

            case SLOT_VISUALIZE ->
            {
                player.closeInventory();
                List<int[]> bounds = new ArrayList<>();
                bounds.add(new int[]{
                    claim.getLesserBoundaryCorner().getBlockX(),
                    claim.getLesserBoundaryCorner().getBlockZ(),
                    claim.getGreaterBoundaryCorner().getBlockX(),
                    claim.getGreaterBoundaryCorner().getBlockZ()
                });
                for (Claim crystal : chunkStorage.getCrystalClaims(plugin, claim))
                    bounds.add(new int[]{
                        crystal.getLesserBoundaryCorner().getBlockX(),
                        crystal.getLesserBoundaryCorner().getBlockZ(),
                        crystal.getGreaterBoundaryCorner().getBlockX(),
                        crystal.getGreaterBoundaryCorner().getBlockZ()
                    });
                ClaimVisualizer.showAll(plugin, player, bounds);
                ClaimMessages.info(player, "Claim-Grenzen werden &b15 Sekunden&7 angezeigt.");
            }

            case SLOT_CRYSTALMAP ->
                new CrystalMapGui(plugin, player, chunkStorage, storage).open(player);

            case SLOT_TRUST ->
            {
                if (!isOwner && !isAdmin)
                {
                    ClaimMessages.error(player, "Nur der Besitzer kann Vertrauen verwalten.");
                    return;
                }
                new TrustGui(plugin, claim, chunkStorage).open(player);
            }

            case SLOT_TRANSFER ->
            {
                if (!isOwner && !isAdmin)
                {
                    ClaimMessages.error(player, "Nur der Besitzer kann den Claim transferieren.");
                    return;
                }
                player.closeInventory();
                ClaimMessages.hint(player,
                    "Spielernamen eingeben &8(oder &ccancel &8zum Abbrechen)&8:");
                GuiManager.awaitInput(player, name ->
                {
                    if (name.equalsIgnoreCase("cancel"))
                    {
                        ClaimMessages.info(player, "Transfer abgebrochen.");
                        return;
                    }
                    performTransfer(player, name);
                });
            }

            case SLOT_DELETE ->
            {
                if (!isOwner && !isAdmin)
                {
                    ClaimMessages.error(player, "Nur der Besitzer kann diesen Claim löschen.");
                    return;
                }
                int cx = claim.getLesserBoundaryCorner().getBlockX() >> 4;
                int cz = claim.getLesserBoundaryCorner().getBlockZ() >> 4;
                new ConfirmGui(
                    "Claim löschen?",
                    () ->
                    {
                        Claim c = plugin.dataStore.getClaimAt(beaconLoc, true, null);
                        if (c != null)
                        {
                            List<Claim> crystalClaims = chunkStorage.getCrystalClaims(plugin, c);
                            for (Claim crystal : crystalClaims) plugin.dataStore.deleteClaim(crystal);
                            chunkStorage.unregisterBeacon(c.getID());
                            flagsStorage.removeClaim(c.getID());
                            plugin.dataStore.deleteClaim(c);
                        }
                        storage.remove(beaconLoc);
                        holoManager.removeHologram(beaconLoc);
                        beaconLoc.getBlock().setType(Material.AIR);
                        ClaimMessages.hint(player,
                            "Claim &8(Chunk &e" + cx + "&8, &e" + cz + "&8) &ewurde gelöscht.");
                    },
                    () -> this.open(player)
                ).open(player);
            }

            case SLOT_BACK -> player.closeInventory();
        }
    }

    @SuppressWarnings("deprecation")
    private void performTransfer(@NotNull Player player, @NotNull String targetName)
    {
        Player online = Bukkit.getPlayerExact(targetName);
        UUID   targetUUID;
        String resolvedName;

        if (online != null)
        {
            targetUUID   = online.getUniqueId();
            resolvedName = online.getName();
        }
        else
        {
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
            if (!op.hasPlayedBefore())
            {
                ClaimMessages.error(player, "Spieler &9" + targetName + "&7 nicht gefunden.");
                return;
            }
            targetUUID   = op.getUniqueId();
            resolvedName = op.getName() != null ? op.getName() : targetName;
        }

        int  cx       = claim.getLesserBoundaryCorner().getBlockX() >> 4;
        int  cz       = claim.getLesserBoundaryCorner().getBlockZ() >> 4;
        UUID oldOwner = claim.ownerID;

        // Crystal-Claims VOR dem Owner-Wechsel laden, da getCrystalClaims nach ownerID sucht
        List<Claim> crystalClaims = chunkStorage.getCrystalClaims(plugin, claim);

        claim.ownerID = targetUUID;
        try { plugin.dataStore.saveClaim(claim); }
        catch (Exception e)
        {
            claim.ownerID = oldOwner;
            ClaimMessages.error(player, "Fehler beim Speichern des Transfers.");
            return;
        }

        List<Claim> savedCrystals = new ArrayList<>();
        for (Claim crystal : crystalClaims)
        {
            UUID oldCrystalOwner = crystal.ownerID;
            crystal.ownerID = targetUUID;
            try
            {
                plugin.dataStore.saveClaim(crystal);
                savedCrystals.add(crystal);
            }
            catch (Exception e)
            {
                crystal.ownerID = oldCrystalOwner;
                for (Claim saved : savedCrystals)
                {
                    saved.ownerID = oldOwner;
                    try { plugin.dataStore.saveClaim(saved); } catch (Exception ignored) {}
                }
                claim.ownerID = oldOwner;
                try { plugin.dataStore.saveClaim(claim); } catch (Exception ignored) {}
                ClaimMessages.error(player,
                    "Fehler beim Speichern der Crystal-Claims. Transfer rückgängig gemacht.");
                return;
            }
        }

        storage.updateOwnerInChunk(cx, cz, targetUUID);
        holoManager.updateOwner(beaconLoc, resolvedName);
        ClaimMessages.success(player,
            "Claim &8(&e" + cx + "&8, &e" + cz + "&8) &aübertragen an &9" + resolvedName + "&a.");
    }
}
