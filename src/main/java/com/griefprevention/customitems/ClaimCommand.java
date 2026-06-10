package com.griefprevention.customitems;

import com.griefprevention.customitems.gui.ClaimLookupGui;
import com.griefprevention.customitems.gui.ConfirmGui;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Einheitlicher /claim-Befehl – überschreibt GriefPreventions internen /claim-Handler.
 * Registrierung erfolgt in GriefPrevention.onEnable() NACH setUpCommands().
 */
public class ClaimCommand implements TabExecutor
{
    private static final String PERM_ADMIN = "griefprevention.claim.admin";

    private final GriefPrevention    plugin;
    private final ClaimBeaconStorage beaconStorage;
    private final ClaimHologramManager holoManager;
    private final ClaimFlagsStorage  flagsStorage;
    private final ClaimChunkStorage  chunkStorage;

    public ClaimCommand(@NotNull GriefPrevention plugin,
                        @NotNull ClaimBeaconStorage beaconStorage,
                        @NotNull ClaimHologramManager holoManager,
                        @NotNull ClaimFlagsStorage flagsStorage,
                        @NotNull ClaimChunkStorage chunkStorage)
    {
        this.plugin        = plugin;
        this.beaconStorage = beaconStorage;
        this.holoManager   = holoManager;
        this.flagsStorage  = flagsStorage;
        this.chunkStorage  = chunkStorage;
        plugin.getCommand("claim").setExecutor(this);
        plugin.getCommand("claim").setTabCompleter(this);
    }

    // ------- Command -------

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args)
    {
        if (!(sender instanceof Player player))
        {
            ClaimMessages.console(sender, "Nur Spieler können diesen Befehl nutzen.");
            return true;
        }

        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase())
        {
            case "liste"     -> handleList(player);
            case "ignoriere" -> handleIgnoriere(player);
            case "aufgeben"  -> handleAufgeben(player);
            case "transfer"  -> handleTransfer(player, args);
            case "visualize" -> handleVisualize(player);
            case "give"      -> handleGive(player, args);
            case "admin"     -> handleAdmin(player, args);
            default          -> sendHelp(player);
        }
        return true;
    }

    // ------- Sub-Befehle -------

    private void handleList(@NotNull Player player)
    {
        List<Claim> claims = plugin.dataStore.getPlayerData(player.getUniqueId()).getClaims();
        if (claims.isEmpty())
        {
            ClaimMessages.info(player, "Du hast noch keine Claims. Benutze einen &bClaim Beacon&7 oder ein &bClaim Crystal&7!");
            return;
        }
        ClaimMessages.info(player, "Deine Claims &8(&e" + claims.size() + "&8)&7:");
        for (Claim c : claims)
        {
            int    cx = c.getLesserBoundaryCorner().getBlockX() >> 4;
            int    cz = c.getLesserBoundaryCorner().getBlockZ() >> 4;
            String w  = c.getLesserBoundaryCorner().getWorld().getName();
            boolean isBeacon = chunkStorage.isBeaconClaim(c.getID());
            ClaimMessages.info(player, "  " + (isBeacon ? "&6⬡" : "&b◆") + " &7Chunk &e" + cx + "&7, &e" + cz + " &8(" + w + "&8)");
        }
    }

    private void handleIgnoriere(@NotNull Player player)
    {
        if (!player.hasPermission(PERM_ADMIN))
        {
            ClaimMessages.error(player, "Du hast keine Berechtigung dafür.");
            return;
        }
        PlayerData pd = plugin.dataStore.getPlayerData(player.getUniqueId());
        pd.ignoreClaims = !pd.ignoreClaims;
        if (pd.ignoreClaims)
            ClaimMessages.success(player, "Du ignorierst jetzt alle Claim-Schutzregeln.");
        else
            ClaimMessages.info(player, "Du respektierst wieder alle Claim-Schutzregeln.");
    }

    private void handleAufgeben(@NotNull Player player)
    {
        Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), false, null);
        if (claim == null)
        {
            ClaimMessages.error(player, "Du stehst in keinem Claim.");
            return;
        }
        boolean isOwner = player.getUniqueId().equals(claim.ownerID);
        boolean isAdmin = player.hasPermission(PERM_ADMIN);
        if (!isOwner && !isAdmin)
        {
            ClaimMessages.error(player, "Dieser Claim gehört dir nicht.");
            return;
        }

        boolean isBeacon = chunkStorage.isBeaconClaim(claim.getID());
        int     cx       = claim.getLesserBoundaryCorner().getBlockX() >> 4;
        int     cz       = claim.getLesserBoundaryCorner().getBlockZ() >> 4;
        String  worldName = claim.getLesserBoundaryCorner().getWorld().getName();

        String msg = isBeacon
            ? "Claim &8(Chunk &e" + cx + "&8, &e" + cz + "&8) &7aufgeben?"
            : "Crystal-Chunk &8(&e" + cx + "&8, &e" + cz + "&8) &7entfernen?";

        new ConfirmGui(msg,
            () -> {
                if (isBeacon)
                {
                    // Alle Crystal-Chunks entfernen
                    for (Claim crystal : chunkStorage.getCrystalClaims(plugin, claim))
                        plugin.dataStore.deleteClaim(crystal);
                    chunkStorage.unregisterBeacon(claim.getID());
                    flagsStorage.removeClaim(claim.getID());
                    plugin.dataStore.deleteClaim(claim);

                    // Beacon-Block entfernen + Hologram löschen + Beacon zurückgeben
                    for (String key : beaconStorage.removeInChunk(cx, cz, worldName))
                    {
                        String[] parts = key.split(",");
                        if (parts.length != 4) continue;
                        try
                        {
                            World w = Bukkit.getWorld(parts[0]);
                            if (w == null) continue;
                            Location loc = new Location(w,
                                Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[3]));
                            holoManager.removeHologram(loc);
                            loc.getBlock().setType(Material.AIR);
                            w.dropItemNaturally(loc, CustomItems.createClaimBeacon(plugin));
                        }
                        catch (NumberFormatException ignored) {}
                    }
                    ClaimMessages.hint(player, "Claim aufgegeben. Dein Beacon wurde zurückgegeben.");
                }
                else
                {
                    // Nur diesen Crystal-Chunk entfernen
                    chunkStorage.unlinkCrystal(claim.getID());
                    plugin.dataStore.deleteClaim(claim);
                    org.bukkit.inventory.ItemStack crystal = CustomItems.createClaimCrystal(plugin);
                    if (player.getInventory().firstEmpty() != -1)
                        player.getInventory().addItem(crystal);
                    else
                        player.getWorld().dropItemNaturally(player.getLocation(), crystal);
                    ClaimMessages.hint(player, "Crystal-Chunk entfernt. Du hast ein &bClaim Crystal&7 zurückerhalten.");
                }
            },
            () -> {}
        ).open(player);
    }

    private void handleTransfer(@NotNull Player player, @NotNull String[] args)
    {
        if (args.length < 2) { ClaimMessages.hint(player, "Verwendung: /claim transfer &e<spieler>"); return; }

        Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), false, null);
        if (claim == null) { ClaimMessages.error(player, "Du stehst in keinem Claim."); return; }

        boolean isOwner = player.getUniqueId().equals(claim.ownerID);
        boolean isAdmin = player.hasPermission(PERM_ADMIN);
        if (!isOwner && !isAdmin) { ClaimMessages.error(player, "Dieser Claim gehört dir nicht."); return; }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { ClaimMessages.error(player, "Spieler &l" + args[1] + "&c nicht gefunden oder offline."); return; }
        if (target.equals(player)) { ClaimMessages.hint(player, "Du kannst einen Claim nicht an dich selbst übertragen."); return; }

        int  cx = claim.getLesserBoundaryCorner().getBlockX() >> 4;
        int  cz = claim.getLesserBoundaryCorner().getBlockZ() >> 4;

        UUID oldOwner = claim.ownerID;
        claim.ownerID = target.getUniqueId();
        try
        {
            plugin.dataStore.saveClaim(claim);
        }
        catch (Exception e)
        {
            claim.ownerID = oldOwner;
            ClaimMessages.error(player, "Fehler beim Speichern des Transfers.");
            return;
        }

        beaconStorage.updateOwnerInChunk(cx, cz, target.getUniqueId());
        World w = claim.getLesserBoundaryCorner().getWorld();
        holoManager.updateOwner(new Location(w, cx * 16 + 8, 0, cz * 16 + 8), target.getName());

        ClaimMessages.success(player, "Chunk &l(" + cx + ", " + cz + ")&a wurde an &l" + target.getName() + "&a übertragen!");
        ClaimMessages.info(target, "Spieler &9" + player.getName() + "&7 hat dir einen Claim übertragen &8(Chunk &e" + cx + "&7, &e" + cz + "&8)&7.");
    }

    private void handleVisualize(@NotNull Player player)
    {
        Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), false, null);
        if (claim == null) { ClaimMessages.error(player, "Du stehst in keinem Claim."); return; }
        if (!player.getUniqueId().equals(claim.ownerID) && !player.hasPermission(PERM_ADMIN))
        {
            ClaimMessages.error(player, "Das ist nicht dein Claim.");
            return;
        }

        Claim beaconClaim;
        if (chunkStorage.isBeaconClaim(claim.getID()))
            beaconClaim = claim;
        else
        {
            Long beaconId = chunkStorage.getBeaconId(claim.getID());
            beaconClaim = beaconId != null ? findClaimById(beaconId, claim.ownerID) : null;
        }

        List<int[]> bounds = new ArrayList<>();
        if (beaconClaim != null)
        {
            bounds.add(claimBounds(beaconClaim));
            for (Claim crystal : chunkStorage.getCrystalClaims(plugin, beaconClaim))
                bounds.add(claimBounds(crystal));
        }
        else
            bounds.add(claimBounds(claim));

        ClaimVisualizer.showAll(plugin, player, bounds);
        int count = bounds.size();
        ClaimMessages.info(player, "Claim-Grenzen werden &b15 Sekunden&7 lang angezeigt"
            + (count > 1 ? " &8(&e" + count + " Chunks&8)" : "") + "&7.");
    }

    private void handleGive(@NotNull Player player, @NotNull String[] args)
    {
        if (!player.hasPermission(PERM_ADMIN)) { ClaimMessages.error(player, "Du hast keine Berechtigung dafür."); return; }
        if (args.length < 2) { ClaimMessages.hint(player, "Verwendung: /claim give &e<beacon|crystal>"); return; }

        switch (args[1].toLowerCase())
        {
            case "beacon"  -> { player.getInventory().addItem(CustomItems.createClaimBeacon(plugin));  ClaimMessages.success(player, "Du hast einen &lClaim Beacon&a erhalten!"); }
            case "crystal" -> { player.getInventory().addItem(CustomItems.createClaimCrystal(plugin)); ClaimMessages.success(player, "Du hast einen &lClaim Crystal&a erhalten!"); }
            default        -> ClaimMessages.hint(player, "Verwendung: /claim give &e<beacon|crystal>");
        }
    }

    private void handleAdmin(@NotNull Player player, @NotNull String[] args)
    {
        if (!player.hasPermission(PERM_ADMIN)) { ClaimMessages.error(player, "Du hast keine Berechtigung dafür."); return; }
        if (args.length < 2) { sendAdminHelp(player); return; }

        switch (args[1].toLowerCase())
        {
            case "list"   -> handleAdminList(player, args);
            case "lookup" -> handleAdminLookup(player, args);
            case "delete" -> handleAdminDelete(player);
            case "give"   -> handleAdminGive(player, args);
            default       -> sendAdminHelp(player);
        }
    }

    private void handleAdminList(@NotNull Player player, @NotNull String[] args)
    {
        if (args.length < 3) { ClaimMessages.hint(player, "Verwendung: /claim admin list &e<spieler>"); return; }

        String targetName = args[2];
        UUID   targetUUID = resolveUUID(targetName);
        if (targetUUID == null) { ClaimMessages.error(player, "Spieler &l" + targetName + "&c wurde noch nicht auf dem Server gesehen."); return; }

        List<Claim> claims = plugin.dataStore.getPlayerData(targetUUID).getClaims();
        ClaimMessages.info(player, "Claims von &9" + targetName + " &8(&e" + claims.size() + "&8)&7:");
        for (Claim c : claims)
        {
            int    cx = c.getLesserBoundaryCorner().getBlockX() >> 4;
            int    cz = c.getLesserBoundaryCorner().getBlockZ() >> 4;
            boolean isBeacon = chunkStorage.isBeaconClaim(c.getID());
            ClaimMessages.info(player, "  " + (isBeacon ? "&6⬡" : "&b◆") + " &7Chunk &e" + cx + "&7, &e" + cz
                + " &8(" + c.getLesserBoundaryCorner().getWorld().getName() + "&8)");
        }
    }

    private void handleAdminLookup(@NotNull Player player, @NotNull String[] args)
    {
        if (args.length < 3) { ClaimMessages.hint(player, "Verwendung: /claim admin lookup &e<spieler>"); return; }

        String targetName = args[2];
        UUID   targetUUID = resolveUUID(targetName);
        if (targetUUID == null) { ClaimMessages.error(player, "Spieler &l" + targetName + "&c wurde noch nicht auf dem Server gesehen."); return; }

        @SuppressWarnings("deprecation")
        String resolved = Bukkit.getOfflinePlayer(targetUUID).getName();
        String displayName = resolved != null ? resolved : targetName;

        List<Claim> claims = plugin.dataStore.getPlayerData(targetUUID).getClaims();
        new ClaimLookupGui(plugin, chunkStorage, displayName, claims).open(player);
    }

    private void handleAdminDelete(@NotNull Player player)
    {
        Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), false, null);
        if (claim == null) { ClaimMessages.error(player, "Du stehst in keinem Claim."); return; }

        int    cx        = claim.getLesserBoundaryCorner().getBlockX() >> 4;
        int    cz        = claim.getLesserBoundaryCorner().getBlockZ() >> 4;
        String owner     = claim.getOwnerName() != null ? claim.getOwnerName() : "Unbekannt";
        String worldName = player.getWorld().getName();

        plugin.dataStore.deleteClaim(claim);

        for (String key : beaconStorage.removeInChunk(cx, cz, worldName))
        {
            String[] parts = key.split(",");
            if (parts.length != 4) continue;
            try
            {
                World w = Bukkit.getWorld(parts[0]);
                if (w == null) continue;
                Location loc = new Location(w,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
                holoManager.removeHologram(loc);
                loc.getBlock().setType(Material.AIR);
            }
            catch (NumberFormatException ignored) {}
        }
        ClaimMessages.hint(player, "Claim von &9" + owner + "&e in Chunk &l(" + cx + ", " + cz + ")&e wurde gelöscht.");
    }

    private void handleAdminGive(@NotNull Player player, @NotNull String[] args)
    {
        if (args.length < 4) { ClaimMessages.hint(player, "Verwendung: /claim admin give &e<spieler> <beacon|crystal> [anzahl]"); return; }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { ClaimMessages.error(player, "Spieler &l" + args[2] + "&c nicht gefunden oder offline."); return; }

        int amount = 1;
        if (args.length >= 5)
        {
            try { amount = Integer.parseInt(args[4]); }
            catch (NumberFormatException e) { ClaimMessages.error(player, "&l" + args[4] + "&c ist keine gültige Zahl."); return; }
            if (amount < 1 || amount > 9999) { ClaimMessages.error(player, "Anzahl muss zwischen &l1&c und &l9999&c liegen."); return; }
        }

        int finalAmount = amount;
        switch (args[3].toLowerCase())
        {
            case "beacon" ->
            {
                giveItems(target, CustomItems.createClaimBeacon(plugin), finalAmount);
                ClaimMessages.success(player, "&l" + finalAmount + "x &aClaim Beacon an &l" + target.getName() + "&a gegeben.");
                ClaimMessages.info(target, "Du hast &b" + finalAmount + "x Claim Beacon&7 von &9" + player.getName() + "&7 erhalten.");
            }
            case "crystal" ->
            {
                giveItems(target, CustomItems.createClaimCrystal(plugin), finalAmount);
                ClaimMessages.success(player, "&l" + finalAmount + "x &aClaim Crystal an &l" + target.getName() + "&a gegeben.");
                ClaimMessages.info(target, "Du hast &b" + finalAmount + "x Claim Crystal&7 von &9" + player.getName() + "&7 erhalten.");
            }
            default -> ClaimMessages.hint(player, "Verwendung: /claim admin give &e<spieler> <beacon|crystal> [anzahl]");
        }
    }

    // ------- Hilfsmethoden -------

    @SuppressWarnings("deprecation")
    private @Nullable UUID resolveUUID(@NotNull String name)
    {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        return op.hasPlayedBefore() ? op.getUniqueId() : null;
    }

    private static int[] claimBounds(@NotNull Claim c)
    {
        return new int[]{
            c.getLesserBoundaryCorner().getBlockX(),
            c.getLesserBoundaryCorner().getBlockZ(),
            c.getGreaterBoundaryCorner().getBlockX(),
            c.getGreaterBoundaryCorner().getBlockZ()
        };
    }

    private @Nullable Claim findClaimById(long id, @NotNull UUID ownerID)
    {
        for (Claim c : plugin.dataStore.getPlayerData(ownerID).getClaims())
            if (c.getID() == id) return c;
        return null;
    }

    private void giveItems(@NotNull Player target, @NotNull org.bukkit.inventory.ItemStack template, int total)
    {
        int remaining = total;
        while (remaining > 0)
        {
            int stackSize = Math.min(remaining, 64);
            org.bukkit.inventory.ItemStack stack = template.clone();
            stack.setAmount(stackSize);
            target.getInventory().addItem(stack);
            remaining -= stackSize;
        }
    }

    // ------- Hilfe -------

    private void sendHelp(@NotNull Player player)
    {
        ClaimMessages.info(player, "&b&lClaim&7-Befehle &8【 /claim &8】");
        ClaimMessages.info(player, "  &b/claim liste &8— &7Deine Claims anzeigen");
        ClaimMessages.info(player, "  &b/claim aufgeben &8— &7Aktuellen Claim aufgeben");
        ClaimMessages.info(player, "  &b/claim transfer &e<spieler> &8— &7Claim übertragen");
        ClaimMessages.info(player, "  &b/claim visualize &8— &7Claim-Grenzen anzeigen");
        if (player.hasPermission(PERM_ADMIN))
        {
            ClaimMessages.info(player, "  &b/claim ignoriere &8— &7Claim-Schutz ignorieren umschalten");
            ClaimMessages.info(player, "  &b/claim give &e<beacon|crystal> &8— &7Item erhalten");
            ClaimMessages.info(player, "  &b/claim admin lookup &e<spieler> &8— &7Claims-GUI eines Spielers");
            ClaimMessages.info(player, "  &b/claim admin list &e<spieler> &8— &7Claims in Chat anzeigen");
            ClaimMessages.info(player, "  &b/claim admin delete &8— &7Claim am Standort löschen");
            ClaimMessages.info(player, "  &b/claim admin give &e<spieler> <beacon|crystal> [anzahl]");
        }
    }

    private void sendAdminHelp(@NotNull Player player)
    {
        ClaimMessages.info(player, "&b/claim admin lookup &e<spieler> &8— &7Claims-GUI öffnen");
        ClaimMessages.info(player, "&b/claim admin list &e<spieler> &8— &7Claims in Chat anzeigen");
        ClaimMessages.info(player, "&b/claim admin delete &8— &7Claim am aktuellen Standort löschen");
        ClaimMessages.info(player, "&b/claim admin give &e<spieler> <beacon|crystal> [anzahl] &8— &7Item(s) vergeben");
    }

    // ------- Tab-Completion -------

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args)
    {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1)
        {
            List<String> subs = new ArrayList<>(Arrays.asList("liste", "aufgeben", "transfer", "visualize"));
            if (player.hasPermission(PERM_ADMIN)) { subs.add("ignoriere"); subs.add("give"); subs.add("admin"); }
            return filter(subs, args[0]);
        }

        if (args.length == 2)
            return switch (args[0].toLowerCase())
            {
                case "give"     -> filter(List.of("beacon", "crystal"), args[1]);
                case "transfer" -> onlinePlayers(args[1]);
                case "admin"    -> filter(List.of("lookup", "list", "delete", "give"), args[1]);
                default         -> List.of();
            };

        if (args.length == 3)
            return switch (args[0].toLowerCase())
            {
                case "admin" -> (args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("lookup") || args[1].equalsIgnoreCase("give"))
                    ? onlinePlayers(args[2]) : List.of();
                default -> List.of();
            };

        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("give"))
            return filter(List.of("beacon", "crystal"), args[3]);

        if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("give"))
            return filter(List.of("1", "5", "10", "32", "64"), args[4]);

        return List.of();
    }

    private static @NotNull List<String> filter(@NotNull List<String> opts, @NotNull String in)
    {
        return opts.stream().filter(s -> s.toLowerCase().startsWith(in.toLowerCase())).toList();
    }

    private static @NotNull List<String> onlinePlayers(@NotNull String prefix)
    {
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(n -> n.toLowerCase().startsWith(prefix.toLowerCase()))
            .toList();
    }
}
