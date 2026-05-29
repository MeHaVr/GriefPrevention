package com.griefprevention.style;

/**
 * Zentrale Design-Konstanten für den Norvex-Server.
 *
 * GUI-Regeln:
 *   - Titel: TITLE_PREFIX + Name + TITLE_SUFFIX
 *   - Filler: Schwarze Glasplatten, DisplayName = "&r"
 *   - Bestätigen: LIME_CONCRETE, Slot 15
 *   - Abbrechen:  RED_CONCRETE, Slot 11
 *   - Zurück:     ARROW, letzter Slot der letzten Reihe
 *   - Seiten:     FEATHER für prev/next, immer in letzter Reihe
 *
 * Nachrichten-Regeln:
 *   - Prefix:  MSG_PREFIX
 *   - Erfolg:  MSG_SUCCESS + Text
 *   - Fehler:  MSG_ERROR  + Text
 *   - Hinweis: MSG_HINT   + Text
 *   - Info:    MSG_INFO   + Text
 *
 * Item-Regeln:
 *   - Custom Items: PDC-basiert (Key: "griefprevention:item_type")
 *   - Lore-Stil: erste Zeile &8▸ Typ, dann &7Details, dann &8» Aktion
 */
public final class NvxStyle
{
    private NvxStyle() {}

    // ──────────────────────────── GUI ────────────────────────────

    /** Präfix für alle Inventory-Titel. */
    public static final String TITLE_PREFIX =
        "&8» | &#9BE1E3&lɴ&#9BE1E3&lᴏ&#77BAE6&lʀ&#5393E9&lᴠ&#2F6CEC&lᴇ&#0B45EF&lx &8• &7";

    /** Suffix für alle Inventory-Titel (aktuell leer). */
    public static final String TITLE_SUFFIX = "";

    /** DisplayName des Filler-Items (schwarze Glasplatte). */
    public static final String FILLER_NAME = "&r";

    // ──────────────────────────── Nachrichten ────────────────────────────

    /**
     * Norvex-Nachrichten-Prefix.
     * Endet mit Leerzeichen – Text direkt anhängen.
     */
    public static final String MSG_PREFIX =
        "&#9BE1E3&lɴ&#9BE1E3&lᴏ&#77BAE6&lʀ&#5393E9&lᴠ&#2F6CEC&lᴇ&#0B45EF&lx &8» ";

    /** Erfolg-Prefix: grünes Häkchen + helles Grün. */
    public static final String MSG_SUCCESS = "&#00FF87✔ &a";

    /** Fehler-Prefix: rotes Kreuz + Rot. */
    public static final String MSG_ERROR   = "&c✘ &c";

    /** Hinweis-Prefix: goldener Blitz + Gelb. */
    public static final String MSG_HINT    = "&#FFD700⚡ &e";

    /** Info-Prefix: dunkler Punkt + Grau. */
    public static final String MSG_INFO    = "&8• &7";

    // ──────────────────────────── Permissions ────────────────────────────

    /** Basis-Permission für Claim-Befehle. */
    public static final String PERM_CLAIM       = "griefprevention.claim";

    /** Admin-Permission für alle Claim-Admin-Operationen. */
    public static final String PERM_CLAIM_ADMIN = "griefprevention.claim.admin";

    /** Basis-Permission für Crystal-Währungsbefehle. */
    public static final String PERM_ECONOMY     = "norvex.economy.use";

    /** Admin-Permission für Crystal-Wirtschaftsbefehle. */
    public static final String PERM_ECONOMY_ADMIN = "norvex.economy.admin";

    // ──────────────────────────── Lore-Stil Helpers ────────────────────────────

    /** Formatiert eine Lore-Typ-Zeile: "&8▸ Typ" */
    public static String loreType(String type)
    {
        return "&8▸ " + type;
    }

    /** Formatiert eine Lore-Detail-Zeile: "&7Detail" */
    public static String loreDetail(String detail)
    {
        return "&7" + detail;
    }

    /** Formatiert eine Lore-Aktions-Zeile: "&8» Aktion" */
    public static String loreAction(String action)
    {
        return "&8» " + action;
    }
}
