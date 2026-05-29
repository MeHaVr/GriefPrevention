package com.griefprevention.customitems;

import com.griefprevention.style.NvxStyle;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class ClaimMessages
{
    public static final String PREFIX = NvxStyle.MSG_PREFIX;

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .build();

    private ClaimMessages() {}

    public static void success(@NotNull Player player, @NotNull String text)
    {
        send(player, PREFIX + NvxStyle.MSG_SUCCESS + text);
    }

    public static void error(@NotNull Player player, @NotNull String text)
    {
        send(player, PREFIX + NvxStyle.MSG_ERROR + text);
    }

    public static void info(@NotNull Player player, @NotNull String text)
    {
        send(player, PREFIX + NvxStyle.MSG_INFO + text);
    }

    public static void hint(@NotNull Player player, @NotNull String text)
    {
        send(player, PREFIX + NvxStyle.MSG_HINT + text);
    }

    public static void success(@NotNull CommandSender sender, @NotNull String text)
    {
        if (sender instanceof Player p) success(p, text);
        else sendRaw(sender, PREFIX + NvxStyle.MSG_SUCCESS + text);
    }

    public static void error(@NotNull CommandSender sender, @NotNull String text)
    {
        if (sender instanceof Player p) error(p, text);
        else sendRaw(sender, PREFIX + NvxStyle.MSG_ERROR + text);
    }

    public static void info(@NotNull CommandSender sender, @NotNull String text)
    {
        if (sender instanceof Player p) info(p, text);
        else sendRaw(sender, PREFIX + NvxStyle.MSG_INFO + text);
    }

    public static void hint(@NotNull CommandSender sender, @NotNull String text)
    {
        if (sender instanceof Player p) hint(p, text);
        else sendRaw(sender, PREFIX + NvxStyle.MSG_HINT + text);
    }

    public static void console(@NotNull CommandSender sender, @NotNull String text)
    {
        sender.sendMessage("[GriefPrevention] " + text);
    }

    private static void sendRaw(@NotNull CommandSender sender, @NotNull String legacy)
    {
        // §x§R§R§G§G§B§B-Format für korrekte Hex-Farbendarstellung in der Konsole
        sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(SERIALIZER.deserialize(legacy)));
    }

    private static void send(@NotNull Player player, @NotNull String legacy)
    {
        ((Audience) player).sendMessage(parse(legacy));
    }

    private static @NotNull Component parse(@NotNull String legacy)
    {
        return SERIALIZER.deserialize(legacy);
    }
}
