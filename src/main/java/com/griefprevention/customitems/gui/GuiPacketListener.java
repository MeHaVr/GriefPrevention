package com.griefprevention.customitems.gui;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class GuiPacketListener extends PacketAdapter
{
    private static final WrappedChatComponent EMPTY =
        WrappedChatComponent.fromJson("{\"text\":\"\"}");

    // Das Braille-Space-Zeichen das ClaimGui(int size) als Platzhalter-Titel setzt
    private static final String HIDE_MARKER = "⠀";

    public GuiPacketListener(@NotNull Plugin plugin)
    {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.OPEN_WINDOW);
    }

    @Override
    public void onPacketSending(PacketEvent event)
    {
        try
        {
            WrappedChatComponent comp = event.getPacket().getChatComponents().read(0);
            if (comp == null) return;
            String json = comp.getJson();
            if (json == null || !json.contains(HIDE_MARKER)) return;

            event.getPacket().getChatComponents().write(0, EMPTY);
        }
        catch (Exception ignored) { }
    }
}
