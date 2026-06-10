package com.griefprevention.customitems;

import com.griefprevention.customitems.gui.BeaconConfirmIaGui;
import com.griefprevention.customitems.gui.BeaconDetailGui;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimBeaconListener implements Listener
{
    private static final long SIX_MONTHS_MS = 6L * 30 * 24 * 60 * 60 * 1000;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&').hexColors().build();

    private final GriefPrevention      plugin;
    private final ClaimBeaconStorage   storage;
    private final ClaimHologramManager holoManager;
    private final ClaimFlagsStorage    flagsStorage;
    private final ClaimChunkStorage    chunkStorage;

    private final ConcurrentHashMap<UUID, Location> pendingMoves       = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer>  moveActionbarTasks = new ConcurrentHashMap<>();

    public ClaimBeaconListener(@NotNull GriefPrevention plugin,
                               @NotNull ClaimBeaconStorage storage,
                               @NotNull ClaimHologramManager holoManager,
                               @NotNull ClaimFlagsStorage flagsStorage,
                               @NotNull ClaimChunkStorage chunkStorage)
    {
        this.plugin        = plugin;
        this.storage       = storage;
        this.holoManager   = holoManager;
        this.flagsStorage  = flagsStorage;
        this.chunkStorage  = chunkStorage;
    }

    // ------- Platzieren: Bestätigung + Auto-Snap zur Chunk-Mitte -------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event)
    {
        if (!CustomItems.isClaimBeacon(plugin, event.getItemInHand())) return;

        Player player  = event.getPlayer();
        Chunk  chunk   = event.getBlock().getChunk();
        World  world   = event.getBlock().getWorld();

        int x1      = chunk.getX() * 16;
        int z1      = chunk.getZ() * 16;
        int x2      = x1 + 15;
        int z2      = z1 + 15;
        int y1      = world.getMinHeight();
        int y2      = world.getMaxHeight() - 1;
        int centerX = x1 + 8;
        int centerZ = z1 + 8;
        int placedY = event.getBlock().getY();
        int chunkX  = chunk.getX();
        int chunkZ  = chunk.getZ();

        // Sofort canceln – wir platzieren entweder gar nicht oder programmatisch
        event.setCancelled(true);

        // GUI 1 Tick verzögert öffnen: BlockPlaceEvent muss vollständig abgeschlossen sein
        // bevor wir das Spieler-Inventar leeren und ein neues Inventar öffnen,
        // sonst entstehen Paket-Konflikte und das Inventar erscheint nicht.
        Bukkit.getScheduler().runTask(plugin, () ->
            new BeaconConfirmIaGui(
                chunkX, chunkZ,
                () -> Bukkit.getScheduler().runTask(plugin, () ->
                    placeBeacon(player, world, x1, x2, y1, y2, z1, z2,
                        centerX, centerZ, placedY, chunkX, chunkZ)),
                () -> {}
            ).open(player));
    }

    private void placeBeacon(@NotNull Player player, @NotNull World world,
                             int x1, int x2, int y1, int y2, int z1, int z2,
                             int centerX, int centerZ, int placedY,
                             int chunkX, int chunkZ)
    {
        CreateClaimResult result = plugin.dataStore.createClaim(
            world, x1, x2, y1, y2, z1, z2,
            player.getUniqueId(), null, null, player);

        if (!result.succeeded)
        {
            String owner = result.claim != null ? result.claim.getOwnerName() : "einem anderen Spieler";
            ClaimMessages.error(player, "Dieser Chunk gehört bereits &l" + owner + "&c.");
            if (result.claim != null)
                ClaimVisualizer.showConflict(plugin, player,
                    result.claim.getLesserBoundaryCorner().getBlockX(),
                    result.claim.getLesserBoundaryCorner().getBlockZ(),
                    result.claim.getGreaterBoundaryCorner().getBlockX(),
                    result.claim.getGreaterBoundaryCorner().getBlockZ());
            return;
        }

        // Beacon-Position mit Bodenkontrolle bestimmen:
        // 1. Von placedY abwärts suchen bis fester Boden gefunden
        int y = placedY;
        while (y > y1 + 1
            && world.getBlockAt(centerX, y - 1, centerZ).getType().isAir())
        {
            y--;
        }
        // Falls wir bis fast ganz unten gekommen sind, Oberfläche verwenden
        if (y <= y1 + 1)
            y = world.getHighestBlockYAt(centerX, centerZ, HeightMap.MOTION_BLOCKING) + 1;

        // 2. Aufwärts suchen falls der Slot besetzt ist
        Block center = world.getBlockAt(centerX, y, centerZ);
        while (!center.getType().isAir() && y < y2)
        {
            y++;
            center = world.getBlockAt(centerX, y, centerZ);
        }

        center.setType(Material.BEACON);
        storage.add(center.getLocation(), player.getUniqueId());
        chunkStorage.registerBeacon(result.claim.getID());

        // Item manuell verbrauchen (BlockPlaceEvent war gecancelt)
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (CustomItems.isClaimBeacon(plugin, hand))
        {
            if (hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1);
            else player.getInventory().setItemInMainHand(null);
        }

        holoManager.createHologram(center.getLocation(), player.getName(), chunkX, chunkZ);

        int count = plugin.dataStore.getPlayerData(player.getUniqueId()).getClaims().size();
        ClaimMessages.success(player,
            "Chunk &8(&e" + chunkX + "&8, &e" + chunkZ + "&8) &ageclaimed! "
            + "Du hast jetzt &l" + count + "&a Claim" + (count == 1 ? "" : "s") + ".");
        ClaimVisualizer.showSuccess(plugin, player, x1, z1, x2, z2);
    }

    // ------- Abbauen -------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event)
    {
        if (event.getBlock().getType() != Material.BEACON) return;

        Location loc = event.getBlock().getLocation();
        if (!storage.isClaimBeacon(loc)) return;

        Player player  = event.getPlayer();
        UUID   ownerID = storage.getOwner(loc);

        if (ownerID == null)
        {
            plugin.getLogger().warning("[ClaimBeacon] Beacon ohne Besitzer bei " + loc + " – bereinige Storage.");
            // GP-Claim mitlöschen um dangling Claims zu vermeiden
            Claim orphan = plugin.dataStore.getClaimAt(loc, true, null);
            if (orphan != null)
            {
                java.util.List<Claim> crystalClaims = chunkStorage.getCrystalClaims(plugin, orphan);
                for (Claim c : crystalClaims) plugin.dataStore.deleteClaim(c);
                chunkStorage.unregisterBeacon(orphan.getID());
                flagsStorage.removeClaim(orphan.getID());
                plugin.dataStore.deleteClaim(orphan);
            }
            storage.remove(loc);
            holoManager.removeHologram(loc);
            return;
        }

        // Claim-Koordinaten für Nachricht
        Claim claim  = plugin.dataStore.getClaimAt(loc, true, null);
        int   chunkX = claim != null ? claim.getLesserBoundaryCorner().getBlockX() >> 4 : loc.getChunk().getX();
        int   chunkZ = claim != null ? claim.getLesserBoundaryCorner().getBlockZ() >> 4 : loc.getChunk().getZ();

        if (player.getUniqueId().equals(ownerID))
        {
            holoManager.removeHologram(loc);
            deleteBeaconClaim(loc, ownerID);
            ClaimMessages.hint(player,
                "Dein Chunk-Claim &8(Chunk &e" + chunkX + "&8, &e" + chunkZ + "&8) &ewurde entfernt.");
            return;
        }

        long inactiveMs   = System.currentTimeMillis() - Bukkit.getOfflinePlayer(ownerID).getLastPlayed();
        long inactiveDays = inactiveMs / (1000L * 60 * 60 * 24);

        if (inactiveMs < SIX_MONTHS_MS)
        {
            event.setCancelled(true);
            ClaimMessages.error(player,
                "Geschützter Beacon! Besitzer war vor &l" + inactiveDays + "&c Tagen zuletzt online.");
            return;
        }

        holoManager.removeHologram(loc);
        deleteBeaconClaim(loc, ownerID);
        ClaimMessages.hint(player,
            "Claim Beacon eines inaktiven Spielers entfernt "
            + "&8(offline seit &e" + inactiveDays + " &8Tagen&8).");
    }

    // ------- Rechtsklick: Claim-Detail-GUI öffnen -------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event)
    {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block  block  = event.getClickedBlock();
        Player player = event.getPlayer();

        // Wenn Spieler im Move-Modus ist, Klick abfangen
        if (pendingMoves.containsKey(player.getUniqueId()))
        {
            event.setCancelled(true);
            if (block != null) handleMoveClick(player, block);
            return;
        }

        if (block == null || block.getType() != Material.BEACON) return;
        if (!storage.isClaimBeacon(block.getLocation())) return;

        event.setCancelled(true);
        Claim claim = plugin.dataStore.getClaimAt(block.getLocation(), true, null);

        if (claim == null)
        {
            ClaimMessages.hint(player, "Dieser Beacon hat keinen aktiven Claim.");
            return;
        }

        new BeaconDetailGui(plugin, storage, holoManager, flagsStorage, chunkStorage, this,
            claim, block.getLocation()).open(player);
    }

    // ------- Move-Modus -------

    public void enterMoveMode(@NotNull Player player, @NotNull Location beaconLoc)
    {
        UUID uuid = player.getUniqueId();
        pendingMoves.put(uuid, beaconLoc.clone());

        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () ->
        {
            if (!pendingMoves.containsKey(uuid)) return;
            ((Audience) player).sendActionBar(LEGACY.deserialize(
                "&8» &bKlicke einen Block zum Verschieben &7| &c/cancel &7zum Abbrechen"));
        }, 0L, 20L).getTaskId();

        moveActionbarTasks.put(uuid, taskId);
    }

    private void exitMoveMode(@NotNull Player player, boolean showMessage)
    {
        UUID uuid = player.getUniqueId();
        pendingMoves.remove(uuid);
        Integer taskId = moveActionbarTasks.remove(uuid);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
        if (showMessage) ClaimMessages.info(player, "Verschieben abgebrochen.");
    }

    private void handleMoveClick(@NotNull Player player, @NotNull Block target)
    {
        Location beaconLoc = pendingMoves.get(player.getUniqueId());
        if (beaconLoc == null) return;

        // Chunk-Prüfung über Koordinaten (kein Chunk-Objekt-Vergleich)
        int beaconCx = beaconLoc.getBlockX() >> 4;
        int beaconCz = beaconLoc.getBlockZ() >> 4;
        int targetCx = target.getX() >> 4;
        int targetCz = target.getZ() >> 4;

        if (beaconCx != targetCx || beaconCz != targetCz
            || !beaconLoc.getWorld().equals(target.getWorld()))
        {
            ClaimMessages.error(player, "Der Zielblock muss im selben Chunk sein.");
            return;
        }

        // Beacon landet auf dem angeklickten Block
        Location newLoc  = target.getLocation().add(0, 1, 0);
        Block    newBlock = newLoc.getBlock();

        if (!newBlock.getType().isAir())
        {
            ClaimMessages.error(player, "Dieser Platz ist blockiert. Wähle eine freie Position.");
            return;
        }

        // Gleiche Position: Modus beenden
        if (newLoc.getBlockX() == beaconLoc.getBlockX()
            && newLoc.getBlockY() == beaconLoc.getBlockY()
            && newLoc.getBlockZ() == beaconLoc.getBlockZ())
        {
            exitMoveMode(player, false);
            return;
        }

        exitMoveMode(player, false);

        Location oldLoc = beaconLoc.clone();
        oldLoc.getBlock().setType(Material.AIR);
        newBlock.setType(Material.BEACON);

        storage.updatePosition(oldLoc, newLoc);

        holoManager.removeHologram(oldLoc);
        UUID ownerID = storage.getOwner(newLoc);
        String ownerName = ownerID != null ? Bukkit.getOfflinePlayer(ownerID).getName() : null;
        if (ownerName == null) ownerName = player.getName();
        holoManager.createHologram(newLoc, ownerName, newLoc.getBlockX() >> 4, newLoc.getBlockZ() >> 4);

        ClaimMessages.success(player,
            "Beacon verschoben zu &8(&e" + newLoc.getBlockX()
            + "&8, &e" + newLoc.getBlockY()
            + "&8, &e" + newLoc.getBlockZ() + "&8).");
    }

    @EventHandler
    public void onInventoryOpen(@NotNull InventoryOpenEvent event)
    {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (pendingMoves.containsKey(player.getUniqueId()))
            exitMoveMode(player, true);
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event)
    {
        exitMoveMode(event.getPlayer(), false);
    }

    @EventHandler
    public void onWorldChange(@NotNull PlayerChangedWorldEvent event)
    {
        exitMoveMode(event.getPlayer(), true);
    }

    @EventHandler
    public void onCommandPreprocess(@NotNull PlayerCommandPreprocessEvent event)
    {
        if (!pendingMoves.containsKey(event.getPlayer().getUniqueId())) return;
        if (event.getMessage().equalsIgnoreCase("/cancel"))
        {
            event.setCancelled(true);
            exitMoveMode(event.getPlayer(), true);
        }
    }

    // ------- Hilfsmethode -------

    private void deleteBeaconClaim(@NotNull Location loc, @NotNull UUID ownerID)
    {
        Claim claim = plugin.dataStore.getClaimAt(loc, true, null);
        if (claim != null && ownerID.equals(claim.ownerID))
        {
            // Alle Crystal-Chunk-Claims dieses Beacons mitlöschen
            java.util.List<Claim> crystalClaims = chunkStorage.getCrystalClaims(plugin, claim);
            for (Claim c : crystalClaims) plugin.dataStore.deleteClaim(c);
            chunkStorage.unregisterBeacon(claim.getID());
            plugin.dataStore.deleteClaim(claim);
        }
        storage.remove(loc);
    }
}
