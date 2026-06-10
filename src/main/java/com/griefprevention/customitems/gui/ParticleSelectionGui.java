package com.griefprevention.customitems.gui;

import com.griefprevention.customitems.ClaimBeaconListener;
import com.griefprevention.customitems.ClaimBeaconStorage;
import com.griefprevention.customitems.ClaimChunkStorage;
import com.griefprevention.customitems.ClaimFlagsStorage;
import com.griefprevention.customitems.ClaimHologramManager;
import com.griefprevention.customitems.ClaimMessages;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * GUI zur Auswahl des Partikel-Effekt-Typs für einen Claim-Beacon.
 *
 * Layout (27 Slots, 3 Reihen):
 *   R1: B   B   B   B   B   B   B   B   B
 *   R2: B  FL   B  SL   B  WT   B  ER   B
 *   R3: B  EN   B  CL   B  DV   B  SN  BCK
 */
public class ParticleSelectionGui extends ClaimGui
{
    private record ParticleOption(int slot, String particleKey, Material material, String label) {}

    private static final ParticleOption[] OPTIONS = {
        new ParticleOption(10, "FLAME",           Material.BLAZE_POWDER,    "Flamme"),
        new ParticleOption(12, "SOUL_FIRE_FLAME", Material.SOUL_SAND,       "Seelenfeuer"),
        new ParticleOption(14, "WITCH",           Material.PURPLE_DYE,      "Hexenstaub"),
        new ParticleOption(16, "END_ROD",         Material.END_ROD,         "Enderstab"),
        new ParticleOption(19, "ENCHANT",         Material.ENCHANTED_BOOK,  "Verzauberung"),
        new ParticleOption(21, "CHERRY_LEAVES",   Material.CHERRY_LEAVES,   "Kirschblüten"),
        new ParticleOption(23, "HAPPY_VILLAGER",  Material.EMERALD,         "Dorfbewohner"),
        new ParticleOption(25, "SNOWFLAKE",       Material.SNOWBALL,        "Schneeflocke"),
    };

    private static final int SLOT_BACK = 26;

    private final GriefPrevention      plugin;
    private final ClaimBeaconStorage   storage;
    private final ClaimHologramManager holoManager;
    private final ClaimFlagsStorage    flagsStorage;
    private final ClaimChunkStorage    chunkStorage;
    private final ClaimBeaconListener  listener;
    private final Claim                claim;
    private final Location             beaconLoc;

    public ParticleSelectionGui(@NotNull GriefPrevention plugin,
                                @NotNull ClaimBeaconStorage storage,
                                @NotNull ClaimHologramManager holoManager,
                                @NotNull ClaimFlagsStorage flagsStorage,
                                @NotNull ClaimChunkStorage chunkStorage,
                                @NotNull ClaimBeaconListener listener,
                                @NotNull Claim claim,
                                @NotNull Location beaconLoc)
    {
        super(27, "particle_selection", title("Einstellungen", "Partikel-Typ"));
        this.plugin       = plugin;
        this.storage      = storage;
        this.holoManager  = holoManager;
        this.flagsStorage = flagsStorage;
        this.chunkStorage = chunkStorage;
        this.listener     = listener;
        this.claim        = claim;
        this.beaconLoc    = beaconLoc;
    }

    @Override
    public void open(@NotNull Player player)
    {
        boolean isAdmin = player.hasPermission("griefprevention.claim.admin");
        build(isAdmin || player.hasPermission(PERM_PARTICLE_TYPE));
        super.open(player);
    }

    private static final String PERM_PARTICLE_TYPE = "griefprevention.claim.particletype";

    private void build(boolean hasPermission)
    {
        String current = flagsStorage.getParticleType(claim.getID());
        for (ParticleOption opt : OPTIONS)
        {
            boolean selected = opt.particleKey().equals(current);
            if (!hasPermission)
            {
                setItem(opt.slot(), Material.GRAY_DYE,
                    "&8» &8&l" + opt.label() + " &8• &cGesperrt",
                    "",
                    "&8» &7Benötigt einen höheren Rang.",
                    "");
            }
            else
            {
                setItem(opt.slot(), opt.material(),
                    selected ? "&8» &a&l" + opt.label() : "&8» &e" + opt.label(),
                    "",
                    selected ? "&8» &a✔ Ausgewählt" : "&8» &7Klicken zum Auswählen.",
                    "");
            }
        }
        setBackItem(SLOT_BACK, "Einstellungen");
        fill();
    }

    @Override
    public void handleClick(int slot, @NotNull Player player)
    {
        if (slot == SLOT_BACK)
        {
            new ClaimSettingsGui(plugin, storage, holoManager, flagsStorage,
                chunkStorage, listener, claim, beaconLoc).open(player);
            return;
        }
        boolean isAdmin = player.hasPermission("griefprevention.claim.admin");
        boolean hasPermission = isAdmin || player.hasPermission(PERM_PARTICLE_TYPE);
        for (ParticleOption opt : OPTIONS)
        {
            if (opt.slot() == slot)
            {
                if (!hasPermission)
                {
                    ClaimMessages.error(player, "Dein Rang erlaubt keine Partikel-Typ-Auswahl.");
                    return;
                }
                flagsStorage.setParticleType(claim.getID(), opt.particleKey());
                build(true);
                ClaimMessages.info(player, "Partikel-Typ auf &e" + opt.label() + " &7gesetzt.");
                return;
            }
        }
    }

    /** Gibt den deutschen Label-Namen für einen Partikel-Key zurück. */
    public static @NotNull String getLabel(@NotNull String particleKey)
    {
        for (ParticleOption opt : OPTIONS)
            if (opt.particleKey().equals(particleKey)) return opt.label();
        return particleKey;
    }
}
