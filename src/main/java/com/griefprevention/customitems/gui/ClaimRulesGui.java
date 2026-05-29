package com.griefprevention.customitems.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Zeigt alle Claim-Regeln des Servers.
 *
 * Layout (36 Slots, 4 Reihen):
 *   Reihe 0: [G x4] [TITEL @4] [G x4]
 *   Reihe 1: [G] [R1 @10] [G] [R2 @12] [G] [R3 @14] [G] [R4 @16] [G]
 *   Reihe 2: [G] [R5 @19] [G] [R6 @21] [G] [R7 @23] [G] [R8 @25] [G]
 *   Reihe 3: [G x4] [ZURÜCK @31] [G x4]
 */
public class ClaimRulesGui extends ClaimGui
{
    private static final int SLOT_TITLE = 4;
    private static final int SLOT_BACK  = 31;

    private static final int[] RULE_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25};

    public ClaimRulesGui()
    {
        super(36, title("Claim-Details", "Regeln"));
        build();
    }

    private void build()
    {
        setItem(SLOT_TITLE, Material.LECTERN,
            "&8» &e&l✦ Claim-Regeln",
            "",
            "&8» &7Alle Regeln rund um das Claim-System.",
            "",
            "&8» &7&oKlicke Zurück um das Menü zu schließen.",
            "");

        // Regel 1: Claim Beacon
        setItem(RULE_SLOTS[0], Material.BEACON,
            "&8» &6&lClaim Beacon",
            "",
            "&8» &7Durch Platzieren eines Beacons",
            "&8» &7beanspruchst du genau &e1 Chunk&7.",
            "",
            "&8» &7Der Chunk gehört dir vollständig.",
            "&8» &7Andere können dort nicht bauen.",
            "");

        // Regel 2: Claim Crystal
        setItem(RULE_SLOTS[1], Material.AMETHYST_SHARD,
            "&8» &b&lClaim Crystal",
            "",
            "&8» &7Mit einem Crystal erweiterst du",
            "&8» &7deinen Claim um &e1 angrenzenden Chunk&7.",
            "",
            "&8» &7Nur direkt benachbarte Chunks",
            "&8» &7(N/S/O/W, keine Diagonalen).",
            "");

        // Regel 3: PvP
        setItem(RULE_SLOTS[2], Material.DIAMOND_SWORD,
            "&8» &c&lPvP in Claims",
            "",
            "&8» &7PvP ist in deinem Claim",
            "&8» &7standardmäßig &cdeaktiviert&7.",
            "",
            "&8» &7Änderbar im Beacon-Menü.",
            "");

        // Regel 4: Explosionen
        setItem(RULE_SLOTS[3], Material.TNT,
            "&8» &c&lExplosionen",
            "",
            "&8» &7Explosionen (TNT, Creeper) sind",
            "&8» &7in Claims standardmäßig &cblockiert&7.",
            "",
            "&8» &7Änderbar im Beacon-Menü.",
            "");

        // Regel 5: Mob-Spawning
        setItem(RULE_SLOTS[4], Material.ZOMBIE_HEAD,
            "&8» &a&lMob-Spawning",
            "",
            "&8» &7Feindliche Mobs dürfen in deinem",
            "&8» &7Claim standardmäßig &aspawnen&7.",
            "",
            "&8» &7Änderbar im Beacon-Menü.",
            "");

        // Regel 6: Inaktivität
        setItem(RULE_SLOTS[5], Material.CLOCK,
            "&8» &e&lInaktivität",
            "",
            "&8» &7Nach &e6 Monaten &7Inaktivität kann",
            "&8» &7dein Beacon von anderen Spielern",
            "&8» &7abgebaut und der Claim entfernt werden.",
            "",
            "&8» &7&oRegelmäßig online bleiben!",
            "");

        // Regel 7: Vertrauen
        setItem(RULE_SLOTS[6], Material.BOOK,
            "&8» &d&lVertrauen (Trust)",
            "",
            "&8» &7Du kannst anderen Spielern Zugang",
            "&8» &7zu deinem Claim gewähren.",
            "",
            "&8» &7Zugang    &8– &7Türen, Knöpfe",
            "&8» &7Container &8– &7Truhen, Farming",
            "&8» &7Bauen     &8– &7Bauen & Abbauen",
            "&8» &7Verwalten &8– &7Trust vergeben",
            "");

        // Regel 8: Transfer
        setItem(RULE_SLOTS[7], Material.ENDER_PEARL,
            "&8» &6&lClaim übertragen",
            "",
            "&8» &7Du kannst deinen Claim vollständig",
            "&8» &7an einen anderen Spieler abgeben.",
            "",
            "&8» &7Alle Crystal-Chunks werden mitübertragen.",
            "&8» &7Dein Beacon bleibt am Ort.",
            "");

        setItem(SLOT_BACK, Material.ARROW,
            "&8» &7← Zurück",
            "",
            "&8» &7Zurück zum Claim-Menü.",
            "");

        fill();
    }

    @Override
    public void handleClick(int slot, @NotNull Player player)
    {
        if (slot == SLOT_BACK)
            player.closeInventory();
    }
}
