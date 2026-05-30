package com.griefprevention.customitems.gui;

import com.griefprevention.customitems.ClaimBeaconListener;
import com.griefprevention.customitems.ClaimBeaconStorage;
import com.griefprevention.customitems.ClaimChunkStorage;
import com.griefprevention.customitems.ClaimFlagPricesConfig;
import com.griefprevention.customitems.ClaimFlagsStorage;
import com.griefprevention.customitems.ClaimHologramManager;
import com.griefprevention.customitems.ClaimMessages;
import com.griefprevention.customitems.CrystalDatabase;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Einstellungs-Sub-GUI des Claim Beacons.
 *
 * Layout (36 Slots, 4 Reihen):
 *   R1: B   B   B   B   B   B   B   B   B
 *   R2: B  PVP  B  EXP  B  MOB  B  HOL  B
 *   R3: B  PAR  B  TYP  B   B   B   B   B
 *   R4: B   B   B   B   B   B   B   B  BCK
 */
public class ClaimSettingsGui extends ClaimGui
{
    private static final int SLOT_PVP      = 10;
    private static final int SLOT_EXPL     = 12;
    private static final int SLOT_MOB      = 14;
    private static final int SLOT_HOL      = 16;
    private static final int SLOT_PAR      = 19;
    private static final int SLOT_PAR_TYPE = 21;
    private static final int SLOT_BACK     = 35;

    private final GriefPrevention      plugin;
    private final ClaimBeaconStorage   storage;
    private final ClaimHologramManager holoManager;
    private final ClaimFlagsStorage    flagsStorage;
    private final ClaimChunkStorage    chunkStorage;
    private final ClaimBeaconListener  listener;
    private final Claim                claim;
    private final Location             beaconLoc;

    public ClaimSettingsGui(@NotNull GriefPrevention plugin,
                            @NotNull ClaimBeaconStorage storage,
                            @NotNull ClaimHologramManager holoManager,
                            @NotNull ClaimFlagsStorage flagsStorage,
                            @NotNull ClaimChunkStorage chunkStorage,
                            @NotNull ClaimBeaconListener listener,
                            @NotNull Claim claim,
                            @NotNull Location beaconLoc)
    {
        super(36, title("Einstellungen"));
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
        buildFlagItems();
        setBackItem(SLOT_BACK, "Claim-Details");
        fill();
    }

    private void buildFlagItems()
    {
        long id = claim.getID();
        ClaimFlagPricesConfig prices = ClaimFlagPricesConfig.getInstance();

        boolean pvp     = flagsStorage.getFlag(id, ClaimFlagsStorage.FLAG_PVP);
        double  pvpCost = prices != null ? prices.getPrice(ClaimFlagsStorage.FLAG_PVP) : 0;
        setItem(SLOT_PVP,
            pvp ? Material.LIME_DYE : Material.RED_DYE,
            pvp ? "&8» &a&lPvP &8• &aAktiv" : "&8» &c&lPvP &8• &cInaktiv",
            "",
            pvp ? "&8» &7Spieler können sich bekämpfen." : "&8» &7Kein Schaden zwischen Spielern.",
            "",
            pvpCost > 0 ? "&8» &7Kosten&8: &b" + (long) pvpCost + " &7✦" : "&8» &7Kostenlos",
            "",
            "&8» &7&oKlicken zum " + (pvp ? "Deaktivieren." : "Aktivieren."),
            "");

        boolean expl     = flagsStorage.getFlag(id, ClaimFlagsStorage.FLAG_EXPLOSIONS);
        double  explCost = prices != null ? prices.getPrice(ClaimFlagsStorage.FLAG_EXPLOSIONS) : 0;
        setItem(SLOT_EXPL,
            expl ? Material.LIME_DYE : Material.RED_DYE,
            expl ? "&8» &a&lExplosionen &8• &aAktiv" : "&8» &c&lExplosionen &8• &cInaktiv",
            "",
            expl ? "&8» &7Explosionen zerstören Blöcke." : "&8» &7Kein Blockschaden durch Explosionen.",
            "",
            explCost > 0 ? "&8» &7Kosten&8: &b" + (long) explCost + " &7✦" : "&8» &7Kostenlos",
            "",
            "&8» &7&oKlicken zum " + (expl ? "Deaktivieren." : "Aktivieren."),
            "");

        boolean mob     = flagsStorage.getFlag(id, ClaimFlagsStorage.FLAG_MOB_SPAWNING);
        double  mobCost = prices != null ? prices.getPrice(ClaimFlagsStorage.FLAG_MOB_SPAWNING) : 0;
        setItem(SLOT_MOB,
            mob ? Material.LIME_DYE : Material.RED_DYE,
            mob ? "&8» &a&lMob-Spawning &8• &aAktiv" : "&8» &c&lMob-Spawning &8• &cInaktiv",
            "",
            mob ? "&8» &7Mobs spawnen in diesem Claim." : "&8» &7Kein Mob-Spawning im Claim.",
            "",
            mobCost > 0 ? "&8» &7Kosten&8: &b" + (long) mobCost + " &7✦" : "&8» &7Kostenlos",
            "",
            "&8» &7&oKlicken zum " + (mob ? "Deaktivieren." : "Aktivieren."),
            "");

        boolean hol = flagsStorage.getFlag(id, ClaimFlagsStorage.FLAG_HOLOGRAM);
        setItem(SLOT_HOL,
            hol ? Material.LIME_DYE : Material.RED_DYE,
            hol ? "&8» &a&lHologram &8• &aSichtbar" : "&8» &c&lHologram &8• &cAusgeblendet",
            "",
            hol ? "&8» &7Hologram über dem Beacon sichtbar." : "&8» &7Hologram ist ausgeblendet.",
            "",
            "&8» &7&oKlicken zum " + (hol ? "Ausblenden." : "Einblenden."),
            "");

        boolean par     = flagsStorage.getFlag(id, ClaimFlagsStorage.FLAG_PARTICLES);
        double  parCost = prices != null ? prices.getPrice(ClaimFlagsStorage.FLAG_PARTICLES) : 0;
        setItem(SLOT_PAR,
            par ? Material.LIME_DYE : Material.RED_DYE,
            par ? "&8» &a&lPartikel-Effekt &8• &aAktiv" : "&8» &c&lPartikel-Effekt &8• &cInaktiv",
            "",
            par ? "&8» &7Partikel-Ring beim Betreten des Claims." : "&8» &7Kein Effekt beim Betreten.",
            "",
            parCost > 0 ? "&8» &7Kosten&8: &b" + (long) parCost + " &7✦" : "&8» &7Kostenlos",
            "",
            "&8» &7&oKlicken zum " + (par ? "Deaktivieren." : "Aktivieren."),
            "");

        String  parType = flagsStorage.getParticleType(id);
        String  parTypeName = ParticleSelectionGui.getLabel(parType);
        setItem(SLOT_PAR_TYPE,
            par ? Material.FIREWORK_STAR : Material.GRAY_DYE,
            par ? "&8» &e&lPartikel-Typ &8• &7" + parTypeName : "&8» &8&lPartikel-Typ &8• &8Inaktiv",
            "",
            par ? "&8» &7Aktuell&8: &e" + parTypeName : "&8» &7Partikel erst aktivieren.",
            "",
            par ? "&8» &7&oKlicken zum Ändern." : "",
            "");
    }

    @Override
    public void handleClick(int slot, @NotNull Player player)
    {
        boolean isOwner = player.getUniqueId().equals(claim.ownerID);
        boolean isAdmin = player.hasPermission("griefprevention.claim.admin");

        switch (slot)
        {
            case SLOT_PVP  -> toggleFlag(player, isOwner, isAdmin,
                ClaimFlagsStorage.FLAG_PVP, "PvP");
            case SLOT_EXPL -> toggleFlag(player, isOwner, isAdmin,
                ClaimFlagsStorage.FLAG_EXPLOSIONS, "Explosionen");
            case SLOT_MOB  -> toggleFlag(player, isOwner, isAdmin,
                ClaimFlagsStorage.FLAG_MOB_SPAWNING, "Mob-Spawning");

            case SLOT_HOL ->
            {
                if (!isOwner && !isAdmin && !player.hasPermission("griefprevention.claim.hologram"))
                {
                    ClaimMessages.error(player, "Keine Berechtigung zum Ändern des Hologramms.");
                    return;
                }
                boolean newVal = !flagsStorage.getFlag(claim.getID(), ClaimFlagsStorage.FLAG_HOLOGRAM);
                flagsStorage.setFlag(claim.getID(), ClaimFlagsStorage.FLAG_HOLOGRAM, newVal);
                String ownerName = claim.ownerID != null
                    ? Bukkit.getOfflinePlayer(claim.ownerID).getName() : null;
                if (ownerName == null) ownerName = "Unbekannt";
                holoManager.setVisible(beaconLoc, newVal, ownerName);
                buildFlagItems();
                fill();
                ClaimMessages.info(player,
                    "Hologram ist jetzt " + (newVal ? "&aSichtbar" : "&cAusgeblendet") + "&7.");
            }

            case SLOT_PAR  -> toggleFlag(player, isOwner, isAdmin,
                ClaimFlagsStorage.FLAG_PARTICLES, "Partikel-Effekt");

            case SLOT_PAR_TYPE ->
            {
                if (!isOwner && !isAdmin)
                {
                    ClaimMessages.error(player, "Nur der Besitzer kann den Partikel-Typ ändern.");
                    return;
                }
                if (!flagsStorage.getFlag(claim.getID(), ClaimFlagsStorage.FLAG_PARTICLES))
                {
                    ClaimMessages.error(player, "Aktiviere zuerst den Partikel-Effekt.");
                    return;
                }
                if (!player.hasPermission("griefprevention.claim.particletype") && !isAdmin)
                {
                    ClaimMessages.error(player, "Dein Rang erlaubt keine Partikel-Typ-Auswahl.");
                    return;
                }
                new ParticleSelectionGui(plugin, storage, holoManager, flagsStorage,
                    chunkStorage, listener, claim, beaconLoc).open(player);
            }

            case SLOT_BACK ->
                new BeaconDetailGui(plugin, storage, holoManager, flagsStorage,
                    chunkStorage, listener, claim, beaconLoc).open(player);
        }
    }

    private void toggleFlag(@NotNull Player player, boolean isOwner, boolean isAdmin,
                            @NotNull String flag, @NotNull String label)
    {
        if (!isOwner && !isAdmin)
        {
            ClaimMessages.error(player, "Nur der Besitzer kann Flags ändern.");
            return;
        }
        ClaimFlagPricesConfig prices = ClaimFlagPricesConfig.getInstance();
        if (prices != null)
        {
            double cost = prices.getPrice(flag);
            if (cost > 0)
            {
                CrystalDatabase db = CrystalDatabase.getInstance();
                if (db == null || !db.withdraw(player.getUniqueId(), cost))
                {
                    ClaimMessages.error(player,
                        "Nicht genug Crystals. Kosten&8: &b" + (long) cost + " &7✦.");
                    return;
                }
            }
        }
        boolean newVal = !flagsStorage.getFlag(claim.getID(), flag);
        flagsStorage.setFlag(claim.getID(), flag, newVal);
        buildFlagItems();
        fill();
        ClaimMessages.info(player, label + " ist jetzt " + (newVal ? "&aAN" : "&cAUS") + "&7.");
    }
}
