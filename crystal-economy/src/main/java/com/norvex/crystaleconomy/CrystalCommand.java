package com.norvex.crystaleconomy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /crystal-Befehl mit Subcommands:
 *   balance [spieler]           – Kontostand anzeigen
 *   pay <spieler> <betrag>      – Zahlung senden
 *   top                         – Reichste Spieler (Top 10)
 *   admin give <spieler> <n>    – Crystals geben (Admin)
 *   admin take <spieler> <n>    – Crystals entfernen (Admin)
 *   admin set  <spieler> <n>    – Kontostand setzen (Admin)
 */
public class CrystalCommand implements TabExecutor
{
    private static final String PERM_USE   = "norvex.economy.use";
    private static final String PERM_ADMIN = "norvex.economy.admin";

    private final CrystalDatabase db;
    private final CrystalEconomy  economy;

    public CrystalCommand(@NotNull CrystalDatabase db, @NotNull CrystalEconomy economy)
    {
        this.db      = db;
        this.economy = economy;
    }

    // ------- Command -------

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args)
    {
        if (args.length == 0 || args[0].equalsIgnoreCase("balance"))
        {
            handleBalance(sender, args);
            return true;
        }

        switch (args[0].toLowerCase())
        {
            case "pay"   -> handlePay(sender, args);
            case "top"   -> handleTop(sender);
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
            { error(sender, "Keine Berechtigung, fremde Kontostände einzusehen."); return; }
            @SuppressWarnings("deprecation")
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            if (!op.hasPlayedBefore())
            { error(sender, "Spieler &l" + args[1] + "&c nicht gefunden."); return; }
            target = op;
        }
        else if (sender instanceof Player p)
        {
            target = p;
        }
        else
        {
            error(sender, "Gib einen Spielernamen an.");
            return;
        }

        db.createAccount(target.getUniqueId());
        double balance = db.getBalance(target.getUniqueId());
        String name = target.getName() != null ? target.getName() : target.getUniqueId().toString();

        if (target instanceof Player p && p.equals(sender))
            info(sender, "Dein Kontostand&8: &b" + economy.format(balance));
        else
            info(sender, "Kontostand von &9" + name + "&8: &b" + economy.format(balance));
    }

    private void handlePay(@NotNull CommandSender sender, @NotNull String[] args)
    {
        if (!(sender instanceof Player payer))
        { error(sender, "Nur Spieler können Crystals senden."); return; }
        if (args.length < 3)
        { hint(sender, "Verwendung: /crystal pay &e<spieler> <betrag>"); return; }

        double amount;
        try { amount = Double.parseDouble(args[2].replace(",", ".")); }
        catch (NumberFormatException e) { error(sender, "Ungültiger Betrag."); return; }
        if (amount <= 0) { error(sender, "Betrag muss positiv sein."); return; }

        @SuppressWarnings("deprecation")
        OfflinePlayer receiver = Bukkit.getOfflinePlayer(args[1]);
        if (!receiver.hasPlayedBefore())
        { error(sender, "Spieler &l" + args[1] + "&c nicht gefunden."); return; }
        if (receiver.getUniqueId().equals(payer.getUniqueId()))
        { hint(sender, "Du kannst dir selbst keine Crystals senden."); return; }

        db.createAccount(payer.getUniqueId());
        db.createAccount(receiver.getUniqueId());

        boolean ok = db.withdraw(payer.getUniqueId(), amount);
        if (!ok)
        { error(sender, "Nicht genug Crystals. Du hast &b" + economy.format(db.getBalance(payer.getUniqueId())) + "&c."); return; }
        db.deposit(receiver.getUniqueId(), amount);

        String receiverName = receiver.getName() != null ? receiver.getName() : args[1];
        success(sender, "Du hast &b" + economy.format(amount) + "&a an &9" + receiverName + "&a gesendet.");

        if (receiver.isOnline() && receiver.getPlayer() != null)
            info(receiver.getPlayer(), "Du hast &b" + economy.format(amount) + "&7 von &9" + payer.getName() + "&7 erhalten.");
    }

    private void handleTop(@NotNull CommandSender sender)
    {
        List<CrystalDatabase.TopEntry> top = db.getTop(10);
        info(sender, "&b&lTop Crystal-Besitzer&8:");
        for (int i = 0; i < top.size(); i++)
        {
            CrystalDatabase.TopEntry e = top.get(i);
            @SuppressWarnings("deprecation")
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.uuid());
            String name = op.getName() != null ? op.getName() : e.uuid().toString().substring(0, 8);
            info(sender, "  &8#" + (i + 1) + " &9" + name + " &8— &b" + economy.format(e.balance()));
        }
        if (top.isEmpty()) info(sender, "Noch keine Konten vorhanden.");
    }

    private void handleAdmin(@NotNull CommandSender sender, @NotNull String[] args)
    {
        if (!sender.hasPermission(PERM_ADMIN))
        { error(sender, "Keine Berechtigung für Admin-Befehle."); return; }
        if (args.length < 4)
        { hint(sender, "Verwendung: /crystal admin &e<give|take|set> <spieler> <betrag>"); return; }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (!target.hasPlayedBefore())
        { error(sender, "Spieler &l" + args[2] + "&c nicht gefunden."); return; }

        double amount;
        try { amount = Double.parseDouble(args[3].replace(",", ".")); }
        catch (NumberFormatException e) { error(sender, "Ungültiger Betrag."); return; }
        if (amount < 0) { error(sender, "Betrag darf nicht negativ sein."); return; }

        db.createAccount(target.getUniqueId());
        String tName = target.getName() != null ? target.getName() : args[2];

        switch (args[1].toLowerCase())
        {
            case "give" ->
            {
                db.deposit(target.getUniqueId(), amount);
                success(sender, "&b" + economy.format(amount) + "&a zu &9" + tName + "&a hinzugefügt.");
                if (target.isOnline() && target.getPlayer() != null)
                    info(target.getPlayer(), "Du hast &b" + economy.format(amount) + "&7 von einem Admin erhalten.");
            }
            case "take" ->
            {
                boolean ok = db.withdraw(target.getUniqueId(), amount);
                if (ok) success(sender, "&b" + economy.format(amount) + "&a von &9" + tName + "&a abgezogen.");
                else    error(sender, tName + " hat nicht genug Crystals.");
            }
            case "set" ->
            {
                db.setBalance(target.getUniqueId(), amount);
                success(sender, "Kontostand von &9" + tName + "&a auf &b" + economy.format(amount) + "&a gesetzt.");
            }
            default -> hint(sender, "Verwendung: /crystal admin &e<give|take|set> <spieler> <betrag>");
        }
    }

    // ------- Hilfe -------

    private void sendHelp(@NotNull CommandSender sender)
    {
        info(sender, "&b&lCrystal&8-Befehle&8:");
        info(sender, "  &b/crystal balance &8[spieler] &8— &7Kontostand anzeigen");
        info(sender, "  &b/crystal pay &e<spieler> <betrag> &8— &7Crystals senden");
        info(sender, "  &b/crystal top &8— &7Reichste Spieler");
        if (sender.hasPermission(PERM_ADMIN))
        {
            info(sender, "  &b/crystal admin give &e<spieler> <n> &8— &7Crystals geben");
            info(sender, "  &b/crystal admin take &e<spieler> <n> &8— &7Crystals entfernen");
            info(sender, "  &b/crystal admin set &e<spieler> <n>  &8— &7Kontostand setzen");
        }
    }

    // ------- Tab-Completion -------

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String alias, @NotNull String[] args)
    {
        if (args.length == 1)
        {
            List<String> subs = new ArrayList<>(List.of("balance", "pay", "top"));
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

    // ------- Nachrichten (Norvex-Stil) -------

    private static final String PREFIX = ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "N"
        + ChatColor.AQUA + "" + ChatColor.BOLD + "o"
        + ChatColor.AQUA + "" + ChatColor.BOLD + "r"
        + ChatColor.BLUE + "" + ChatColor.BOLD + "v"
        + ChatColor.BLUE + "" + ChatColor.BOLD + "e"
        + ChatColor.DARK_BLUE + "" + ChatColor.BOLD + "x "
        + ChatColor.DARK_GRAY + "» ";

    @SuppressWarnings("deprecation")
    private void success(@NotNull CommandSender s, @NotNull String text) { send(s, PREFIX + ChatColor.GREEN + c(text)); }
    @SuppressWarnings("deprecation")
    private void error(@NotNull CommandSender s, @NotNull String text)   { send(s, PREFIX + ChatColor.RED + c(text)); }
    @SuppressWarnings("deprecation")
    private void info(@NotNull CommandSender s, @NotNull String text)    { send(s, PREFIX + ChatColor.GRAY + c(text)); }
    @SuppressWarnings("deprecation")
    private void hint(@NotNull CommandSender s, @NotNull String text)    { send(s, PREFIX + ChatColor.YELLOW + c(text)); }

    @SuppressWarnings("deprecation")
    private static String c(@NotNull String text)
    {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void send(@NotNull CommandSender s, @NotNull String msg)
    {
        s.sendMessage(msg);
    }

    // ------- Helpers -------

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
