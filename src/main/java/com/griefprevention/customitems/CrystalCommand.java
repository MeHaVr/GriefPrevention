package com.griefprevention.customitems;

import com.griefprevention.customitems.gui.CrystalShopGui;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /crystals – Crystal-Währungs-Befehle.
 *
 * Subcommands:
 *   balance [spieler]         – Kontostand anzeigen
 *   pay <spieler> <betrag>    – Crystals senden
 *   top                       – Top 10 Besitzer
 *   shop                      – Shop öffnen (Items mit Crystals kaufen)
 *   admin give <spieler> <n>  – Admin: hinzufügen
 *   admin take <spieler> <n>  – Admin: entfernen
 *   admin set  <spieler> <n>  – Admin: setzen
 */
public class CrystalCommand implements TabExecutor
{
    private static final String PERM_USE   = "griefprevention.crystals";
    private static final String PERM_ADMIN = "griefprevention.crystals.admin";

    private final Plugin plugin;
    private final CrystalDatabase db;

    public CrystalCommand(@NotNull Plugin plugin, @NotNull CrystalDatabase db)
    {
        this.plugin = plugin;
        this.db     = db;
        JavaPlugin jp = (JavaPlugin) plugin;
        jp.getCommand("crystals").setExecutor(this);
        jp.getCommand("crystals").setTabCompleter(this);
    }

    // ------- Command -------

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args)
    {
        if (!sender.hasPermission(PERM_USE))
        { ClaimMessages.error(sender, "Keine Berechtigung."); return true; }

        if (args.length == 0 || args[0].equalsIgnoreCase("balance"))
        { handleBalance(sender, args); return true; }

        switch (args[0].toLowerCase())
        {
            case "pay"   -> handlePay(sender, args);
            case "top"   -> handleTop(sender);
            case "shop"  -> handleShop(sender);
            case "admin" -> handleAdmin(sender, args);
            default      -> sendHelp(sender);
        }
        return true;
    }

    // ------- Subcommands -------

    private void handleBalance(@NotNull CommandSender sender, @NotNull String[] args)
    {
        OfflinePlayer target;
        if (args.length >= 2)
        {
            if (!sender.hasPermission(PERM_ADMIN))
            { ClaimMessages.error(sender, "Keine Berechtigung, fremde Kontostände einzusehen."); return; }
            @SuppressWarnings("deprecation")
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            if (!op.hasPlayedBefore())
            { ClaimMessages.error(sender, "Spieler &l" + args[1] + "&c nicht gefunden."); return; }
            target = op;
        }
        else if (sender instanceof Player p)
            target = p;
        else
        { ClaimMessages.error(sender, "Gib einen Spielernamen an."); return; }

        db.createAccount(target.getUniqueId());
        double balance = db.getBalance(target.getUniqueId());
        String name = target.getName() != null ? target.getName() : target.getUniqueId().toString();

        if (target.getUniqueId().equals(sender instanceof Player sp ? sp.getUniqueId() : null))
            ClaimMessages.info(sender, "Dein Guthaben&8: &b" + format(balance));
        else
            ClaimMessages.info(sender, "Guthaben von &9" + name + "&7&8: &b" + format(balance));
    }

    private void handlePay(@NotNull CommandSender sender, @NotNull String[] args)
    {
        if (!(sender instanceof Player payer))
        { ClaimMessages.error(sender, "Nur Spieler können Crystals senden."); return; }
        if (args.length < 3)
        { ClaimMessages.hint(sender, "Verwendung: /crystals pay &e<spieler> <betrag>"); return; }

        double amount;
        try { amount = Double.parseDouble(args[2].replace(",", ".")); }
        catch (NumberFormatException e) { ClaimMessages.error(sender, "Ungültiger Betrag."); return; }
        if (amount <= 0) { ClaimMessages.error(sender, "Betrag muss positiv sein."); return; }

        @SuppressWarnings("deprecation")
        OfflinePlayer receiver = Bukkit.getOfflinePlayer(args[1]);
        if (!receiver.hasPlayedBefore())
        { ClaimMessages.error(sender, "Spieler &l" + args[1] + "&c nicht gefunden."); return; }
        if (receiver.getUniqueId().equals(payer.getUniqueId()))
        { ClaimMessages.hint(sender, "Du kannst dir selbst keine Crystals senden."); return; }

        db.createAccount(payer.getUniqueId());
        db.createAccount(receiver.getUniqueId());

        if (!db.withdraw(payer.getUniqueId(), amount))
        { ClaimMessages.error(sender, "Nicht genug Crystals. Du hast &b" + format(db.getBalance(payer.getUniqueId())) + "&c."); return; }

        db.deposit(receiver.getUniqueId(), amount);

        String receiverName = receiver.getName() != null ? receiver.getName() : args[1];
        ClaimMessages.success(sender, "Du hast &b" + format(amount) + "&a an &9" + receiverName + "&a gesendet.");
        if (receiver.isOnline() && receiver.getPlayer() != null)
            ClaimMessages.info(receiver.getPlayer(), "Du hast &b" + format(amount) + "&7 von &9" + payer.getName() + "&7 erhalten.");
    }

    private void handleTop(@NotNull CommandSender sender)
    {
        List<CrystalDatabase.TopEntry> top = db.getTop(10);
        ClaimMessages.info(sender, "&b&lTop Crystal-Besitzer&8:");
        for (int i = 0; i < top.size(); i++)
        {
            CrystalDatabase.TopEntry e = top.get(i);
            @SuppressWarnings("deprecation")
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.uuid());
            String name = op.getName() != null ? op.getName() : e.uuid().toString().substring(0, 8);
            ClaimMessages.info(sender, "  &8#" + (i + 1) + " &9" + name + " &8— &b" + format(e.balance()));
        }
        if (top.isEmpty()) ClaimMessages.info(sender, "Noch keine Konten vorhanden.");
    }

    private void handleShop(@NotNull CommandSender sender)
    {
        if (!(sender instanceof Player player))
        { ClaimMessages.error(sender, "Nur Spieler können den Shop öffnen."); return; }
        new CrystalShopGui(plugin, db, player).open(player);
    }

    private void handleAdmin(@NotNull CommandSender sender, @NotNull String[] args)
    {
        if (!sender.hasPermission(PERM_ADMIN))
        { ClaimMessages.error(sender, "Keine Berechtigung für Admin-Befehle."); return; }
        if (args.length < 4)
        { ClaimMessages.hint(sender, "Verwendung: /crystals admin &e<give|take|set> <spieler> <betrag>"); return; }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (!target.hasPlayedBefore())
        { ClaimMessages.error(sender, "Spieler &l" + args[2] + "&c nicht gefunden."); return; }

        double amount;
        try { amount = Double.parseDouble(args[3].replace(",", ".")); }
        catch (NumberFormatException e) { ClaimMessages.error(sender, "Ungültiger Betrag."); return; }
        if (amount < 0) { ClaimMessages.error(sender, "Betrag darf nicht negativ sein."); return; }

        db.createAccount(target.getUniqueId());
        String tName = target.getName() != null ? target.getName() : args[2];

        switch (args[1].toLowerCase())
        {
            case "give" ->
            {
                db.deposit(target.getUniqueId(), amount);
                ClaimMessages.success(sender, "&b" + format(amount) + "&a zu &9" + tName + "&a hinzugefügt.");
                if (target.isOnline() && target.getPlayer() != null)
                    ClaimMessages.info(target.getPlayer(), "Du hast &b" + format(amount) + "&7 von einem Admin erhalten.");
            }
            case "take" ->
            {
                boolean ok = db.withdraw(target.getUniqueId(), amount);
                if (ok) ClaimMessages.success(sender, "&b" + format(amount) + "&a von &9" + tName + "&a abgezogen.");
                else    ClaimMessages.error(sender, tName + " hat nicht genug Crystals.");
            }
            case "set" ->
            {
                db.setBalance(target.getUniqueId(), amount);
                ClaimMessages.success(sender, "Guthaben von &9" + tName + "&a auf &b" + format(amount) + "&a gesetzt.");
            }
            default -> ClaimMessages.hint(sender, "Verwendung: /crystals admin &e<give|take|set> <spieler> <betrag>");
        }
    }

    // ------- Hilfe -------

    private void sendHelp(@NotNull CommandSender sender)
    {
        ClaimMessages.info(sender, "&b&lCrystal&8-Befehle&8:");
        ClaimMessages.info(sender, "  &b/crystals balance &8[spieler]");
        ClaimMessages.info(sender, "  &b/crystals pay &e<spieler> <betrag>");
        ClaimMessages.info(sender, "  &b/crystals top");
        ClaimMessages.info(sender, "  &b/crystals shop &8— &7Items kaufen");
        if (sender.hasPermission(PERM_ADMIN))
        {
            ClaimMessages.info(sender, "  &b/crystals admin give &e<spieler> <n>");
            ClaimMessages.info(sender, "  &b/crystals admin take &e<spieler> <n>");
            ClaimMessages.info(sender, "  &b/crystals admin set  &e<spieler> <n>");
        }
    }

    // ------- Tab-Completion -------

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String alias, @NotNull String[] args)
    {
        if (!sender.hasPermission(PERM_USE)) return List.of();

        if (args.length == 1)
        {
            List<String> subs = new ArrayList<>(List.of("balance", "pay", "top", "shop"));
            if (sender.hasPermission(PERM_ADMIN)) subs.add("admin");
            return filter(subs, args[0]);
        }
        if (args.length == 2)
        {
            return switch (args[0].toLowerCase())
            {
                case "pay", "balance" -> onlinePlayers(args[1]);
                case "admin" -> sender.hasPermission(PERM_ADMIN)
                    ? filter(List.of("give", "take", "set"), args[1]) : List.of();
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin"))
            return onlinePlayers(args[2]);
        return List.of();
    }

    // ------- Helpers -------

    public String format(double amount)
    {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.GERMAN);
        return new DecimalFormat("#,##0", sym).format((long) amount) + " ✦ Crystal";
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
