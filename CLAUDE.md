# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build und Tests ausführen
mvn --batch-mode verify

# Nur bauen (ohne Tests)
mvn --batch-mode package -DskipTests

# Nur Tests ausführen
mvn --batch-mode test

# Einzelnen Test ausführen
mvn --batch-mode test -Dtest=BoundingBoxTest
```

Java 21 ist erforderlich. Die CI testet ausschließlich auf JDK 21.

## Architektur-Überblick

GriefPrevention ist ein Bukkit/Spigot/Paper-Plugin. Das Projekt besteht aus zwei Java-Paketen mit unterschiedlichen Zuständigkeiten:

### Paketstruktur

- **`me.ryanhamshire.GriefPrevention`** – Legacy-Kerncode (v16-Ursprung). Enthält die Hauptklasse `GriefPrevention` (extends `JavaPlugin`), `DataStore` (abstrakt, Claim-Persistenz), `FlatFileDataStore` / `DatabaseDataStore` (konkrete Implementierungen), `Claim` (Kerndatenmodell), `PlayerData`, sowie alle Event-Handler (`BlockEventHandler`, `PlayerEventHandler`, `EntityEventHandler`, `EntityDamageHandler`).

- **`com.griefprevention`** – Neuerer, refaktorierter Code (v17+). Enthält plattformspezifische Abstraktion (`platform/`), Visualisierung (`visualization/`), Befehlsverarbeitung (`commands/`) und Schutzlogik (`protection/`).

### Zentrale Konzepte

**Claim-Datenspeicherung:** `DataStore` ist die abstrakte Basis für das Laden/Speichern von Claims. `FlatFileDataStore` speichert Claims als einzelne Dateien im Dateisystem; `DatabaseDataStore` nutzt SQLite/MySQL. Claims werden per `Claim`-Objekt repräsentiert, das zwei Eckpunkte (`lesserBoundaryCorner`, `greaterBoundaryCorner`) und eine Permissions-Map (`playerIDToClaimPermissionMap`) enthält.

**Plattformabstraktion:** `PlatformDetection` erkennt zur Laufzeit, ob Paper oder Spigot läuft. Plattformspezifisches Verhalten (z.B. Knockback-Schutz) wird über `KnockbackProtectionHandler`-Implementierungen in `com.griefprevention.platform.knockback` bereitgestellt.

**Visualisierung:** `BoundaryVisualization` (abstrakt) in `com.griefprevention.visualization` zeigt Claim-Grenzen als gefälschte Blöcke an. Implementierungen: `FakeBlockVisualization` (Standard) und `AntiCheatCompatVisualization` (kompatibel mit Anti-Cheat-Plugins). Visualisierungen werden über `BoundaryVisualizationEvent` ausgelöst, damit Addons eigene Visualisierer einsetzen können.

**Events (Public API):** Alle öffentlichen Events liegen unter `me.ryanhamshire.GriefPrevention.events`. Addons können diese Ereignisse abfangen und das Verhalten anpassen. Die unterstützten API-Operationen sind in `src/main/resources/Public Api.txt` dokumentiert.

**Befehle:** `CommandHandler` verarbeitet die meisten Slash-Commands; `ClaimCommand` (`com.griefprevention.commands`) wurde für die Befehle rund um Claims neugeschrieben.

### Custom Items System (`com.griefprevention.customitems`)

Serverseitiges Erweiterungspaket, das das goldene Schaufel-System ersetzt. Alle Klassen liegen unter `src/main/java/com/griefprevention/customitems/`.

**Design-Konstanten (`com.griefprevention.style.NvxStyle`):**
Zentrale Klasse für alle Stil-Konstanten. Immer `NvxStyle.*` verwenden, nie eigene Farb-Strings hardcoden.
- `TITLE_PREFIX` / `TITLE_SUFFIX` – Rahmen für alle Inventory-Titel.
- `MSG_PREFIX`, `MSG_SUCCESS`, `MSG_ERROR`, `MSG_HINT`, `MSG_INFO` – Nachrichten-Prefixe.
- `PERM_CLAIM`, `PERM_CLAIM_ADMIN`, `PERM_ECONOMY`, `PERM_ECONOMY_ADMIN` – Permission-Strings.
- `loreType(String)`, `loreDetail(String)`, `loreAction(String)` – Lore-Zeilen-Helfer.
- GUI-Konvention: Bestätigen = `LIME_CONCRETE` Slot 15, Abbrechen = `RED_CONCRETE` Slot 11, Zurück = `ARROW` letzter Slot letzter Reihe, Seiten = `FEATHER`.

**Claim-Items (PDC-basiert):**
- `CustomItems` – Fabrikmethoden für `ClaimBeacon` (Beacon-Block) und `ClaimCrystal` (Amethyst-Scherbe). Identifikation per `PersistentDataContainer` (Namespace-Key `griefprevention:item_type`). Jedes Item erhält zusätzlich eine eindeutige UUID via PDC-Key `griefprevention:item_uuid` (Anti-Dupe).
- `ClaimBeaconListener` – Platzieren: Event sofort canceln → `ConfirmGui` → bei Bestätigung per 1-Tick-Scheduler auf Chunk-Mitte snappen (`cx*16+8`, `cz*16+8`). Bodenprüfung: Y nach unten suchen bis Block-unten nicht Luft ist, Fallback: `HeightMap.MOTION_BLOCKING`. Rechtsklick auf platzierten Beacon: öffnet `BeaconDetailGui`.
- `ClaimCrystalListener` – Rechtsklick öffnet `CrystalMapGui` (3×3 Chunk-Karte). Voraussetzung: Spieler muss mindestens einen eigenen Claim haben.
- `AntiDupeManager` – Statische Klasse. Verwaltet ein persistentes Set gültiger Item-UUIDs (`antiDupeIds.yml`). `register(item)` beim Erstellen, `consume(item)` beim Platzieren, `isValid(item)` zur Prüfung. Save wird 3 Sekunden debounced (async), `saveNow()` beim Shutdown.
- `AntiDupeListener` – Event-Listener, der `AntiDupeManager.isValid()` prüft und ungültige Custom Items verwirft.

**Persistenz:**
- `ClaimBeaconStorage` – YAML (`claimBeacons.yml`), `ConcurrentHashMap`-Cache, Methoden: `add`, `remove`, `updateOwnerInChunk`, `getOwner`.
- `ClaimFlagsStorage` – YAML (`claimFlags.yml`), Flags je Claim-ID: `pvp` (default `false`), `explosions` (default `false`), `mobSpawning` (default `true`), `hologram` (default `true`), `particles` (default `true`, Partikel-Typ via `getParticleType`/`setParticleType`). Async saves, sync reads aus Cache. Konstanten: `FLAG_PVP`, `FLAG_EXPLOSIONS`, `FLAG_MOB_SPAWNING`, `FLAG_HOLOGRAM`, `FLAG_PARTICLES`.
- `ClaimChunkStorage` – YAML (`claimChunks.yml`). Bi-direktionale Map: Beacon-Claim-ID → Set von Crystal-Claim-IDs (und Rückwärts-Index). Methoden: `registerBeacon`, `unregisterBeacon`, `linkCrystal`, `unlinkCrystal`, `isBeaconClaim`, `getBeaconId`, `getChildren`, `getCrystalClaims`.
- `ClaimDatabase` – HikariCP-Pool (MariaDB), fällt auf YAML zurück wenn nicht konfiguriert. Tabellen: `claim_beacons`, `claim_flags`. Alle Writes async.
- `ClaimFlagPricesConfig` – Singleton. Lädt `claimFlagPrices.yml` (wird aus Resources extrahiert). `getPrice(flagName)` liefert Crystal-Kosten für Flag-Umschalten.

**Crystal-Währung:**
- `CrystalDatabase` – Singleton (`getInstance()`). Speichert Crystal-Kontostände: MariaDB-Modus (`crystal_balances`-Tabelle via HikariCP-Pool) oder YAML-Fallback (`crystalBalances.yml`). DB-Verbindungsdaten werden aus GP's `database.properties` oder `config.yml` gelesen. Methoden: `hasAccount`, `createAccount`, `getBalance`, `withdraw` (synchronized), `deposit` (synchronized), `setBalance`, `getTop(limit)`. Alle Writes async.
- `CrystalEconomy` – Implementiert Vault `Economy`-Interface. Delegiert an `CrystalDatabase`. Währungsname: "Crystal" / "Crystals". Keine Bank-Unterstützung. Wird registriert wenn Vault vorhanden ist.
- `CrystalCommand` – `/crystals`-Befehl. Subcommands: `balance [spieler]`, `pay <spieler> <betrag>`, `top`, `shop`. Admin (`griefprevention.crystals.admin`): `admin give|take|set <spieler> <betrag>`. Öffnet `CrystalShopGui` bei `shop`.

**Holograms (Soft-Depend: DecentHolograms):**
- `ClaimHologramManager` – erstellt/entfernt/aktualisiert Holograms über platzierten Beacons via `DHAPI`. Name-Schema: `"cb_<world>_<x>_<y>_<z>"`. Inhalt: `&8【 &9&l<Name>'s Claim 】` + Chunk-Koordinaten. `setVisible(loc, visible, ownerName)` respektiert `FLAG_HOLOGRAM`.

**GUI-Framework (`gui/`-Unterpaket):**
- `ClaimGui` – abstrakte Basis, implementiert `InventoryHolder`. Konstruktor: `ClaimGui(size, guiKey, fallbackTitle)` – der `guiKey` referenziert einen Eintrag in `guiConfig.yml`. Erbt Titel-Konstanten aus `NvxStyle` (`TITLE_PREFIX`, `TITLE_SUFFIX`). Filler-Items nutzen `NvxStyle.FILLER_NAME` als Display-Name. `fillerMaterial()` ist überschreibbar (default: `GRAY_STAINED_GLASS_PANE`).
- `GuiBackgroundConfig` – Singleton (`init(plugin)` in `onEnable`). Lädt `guiConfig.yml` (aus Resources extrahiert): pro GUI-Key ein ItemsAdder-Font-Image (`image: "namespace:id"`) + Pixel-`offset`. `buildTitle(guiKey, fallback)` liefert den IA-Hintergrund-Titel oder den Fallback-Text-Titel (wenn Image leer/fehlt oder ItemsAdder nicht installiert). Neue GUIs: Key in `guiConfig.yml` ergänzen und im `super()`-Aufruf verwenden.
- `GuiManager` – einmaliger Listener. Fängt `InventoryClickEvent` (delegiert an `ClaimGui.handleClick`), `InventoryDragEvent` (immer canceln) und `AsyncPlayerChatEvent` (einmalige Chat-Eingaben via `awaitInput(Player, Consumer<String>)`) ab. Stellt zudem Inventar-Backups (`InventoryBackup`, `invBackups/<uuid>.yml`) bei Join/Quit wieder her; `closeAllOpen()` wird in `onDisable` aufgerufen.
- `BeaconConfirmIaGui` – Ja/Nein-Bestätigung mit IA-Vollbild (Key `beacon_confirm`): Inventar wird geleert, Buttons liegen in der Hotbar (rawSlots 54–57 = Ablehnen, 58–62 = Bestätigen). Spieler-Inventar wird crash-sicher über `InventoryBackup` gesichert/wiederhergestellt.
- `BeaconDetailGui` – 36 Slots (4 Reihen): Skull @1, Info+Stats @4, Aktivität @7 | Visualisieren @10, Chunk-Map @12, Vertrauen @14, Transfer @16 | Einstellungen @19 (→ `ClaimSettingsGui`), Mitglieder @21 (→ `TrustGui`), Löschen @25 (→ `ConfirmGui`) | Zurück @31.
- `ClaimSettingsGui` – 36 Slots (4 Reihen), Flag-Verwaltung. PvP @10, Explosionen @12, Mob-Spawning @14, Hologram @16 | Regeln @21 | Zurück @30. Filler: `LIGHT_GRAY_STAINED_GLASS_PANE`. Flag-Umschalten zieht Crystal-Kosten aus `ClaimFlagPricesConfig` ab.
- `CrystalShopGui` – 27 Slots (3 Reihen). Slot 11 = Claim Crystal (100 ✦), Slot 13 = Kontostand-Info, Slot 15 = Claim Beacon (500 ✦), Slot 22 = Schließen. Schließt GUI nach Kauf.
- `CrystalMapGui` – 36 Slots (4 Reihen), 3×3 Chunk-Karte. Materialien je Status: `LIME_CONCRETE` (claimbar), `BEACON` (eigen), `RED_CONCRETE` (fremd), `GRAY_CONCRETE` (nicht angrenzend), `ORANGE_CONCRETE` (zu wenig Crystals). Klick auf grün → `ConfirmGui` → **`createClaim()`** – jeder Crystal-Chunk wird als eigenes GP-Claim-Objekt (exakt 1 Chunk = 16×16 Blöcke) erstellt und per `ClaimChunkStorage.linkCrystal()` mit dem Beacon verknüpft. Adjacency-Prüfung basiert auf einem Set echter Chunk-Koordinaten (N/S/E/W, keine Diagonalen). Claim-Block-Kosten werden sofort zurückerstattet (`refundClaimBlocks +256`), Währung sind Crystals.
- `ClaimLookupGui` – Admin-GUI zum Nachschlagen fremder Claims.
- `TrustGui` – 54 Slots: Player-Heads aller vertrauten Spieler (paginiert, 36/Seite). Klick → `ConfirmGui` zum Entfernen. Emerald-Button → Chat-Eingabe → `TrustLevelGui`. Trust-Daten via `claim.getPermissions()` / `claim.setPermission(uuidStr, ClaimPermission)` / `claim.dropPermission(uuidStr)`.
- `TrustLevelGui` – 27 Slots: Auswahl der Stufe (Access/Container/Build/Manage) per Wool-Items. Setzt `claim.setPermission()` + `dataStore.saveClaim()`.
- `ConfirmGui` – 27 Slots, Slot 14 = Bestätigen (LIME_CONCRETE), Slot 12 = Abbrechen (RED_CONCRETE).
- `ClaimRulesGui` – 36 Slots: Titel @4, 8 Regel-Items @10,12,14,16,19,21,23,25, Zurück @31 (Mitte Reihe 3).

**Utilities:**
- `ClaimMessages` – Zentraler Nachrichten-Helper mit Norvex-Prefix. `success/error/hint/info`. Hex-Farben via `LegacyComponentSerializer`; `sendMessage(Component)` erfordert Cast auf `(Audience) player`.
- `ClaimVisualizer` – Gold/Rot-Partikel entlang Claim-Grenzen für 5 Sekunden. `showSuccess(plugin, player, x1, z1, x2, z2)`.
- `CrystalPlaceholderExpansion` – PlaceholderAPI-Expansion für Crystal-Kontostand (`%nxclaim_crystals%`).

**Befehle:**
- `/claim` (`ClaimCommand`) – Subcommands: `list`, `transfer <spieler>`, `visualize`. Admin-only (`griefprevention.claim.admin`): `give <beacon|crystal>`, `admin list <spieler>`, `admin delete`, `admin give <spieler> <beacon|crystal>`.
- `/crystals` (`CrystalCommand`) – Crystal-Währungs-Befehle (siehe Crystal-Währung).
- `ClaimItemCommand` – Interner Admin-Befehl zum direkten Vergeben von Custom Items.

**Nachrichten-Prefix (automatisch in `sendMessage()`):**
- `GriefPrevention.sendMessage(player, TextMode.X, message)` fügt den Norvex-Prefix automatisch voran — **nicht** in `Messages.java`-Einträge schreiben.
- Konsolen-Output enthält keinen Prefix.
- Der `HEX_SERIALIZER` in `GriefPrevention` verarbeitet `&#RRGGBB`-Hex-Codes und `&`-Codes.

**Wichtige API-Kompatibilität (Spigot vs. Paper):**
- `ItemMeta.displayName(Component)` und `ItemMeta.lore(List<Component>)` sind Paper-only → stattdessen deprecated `setDisplayName(String)` / `setLore(List<String>)` mit `@SuppressWarnings("deprecation")` verwenden.
- `CommandSender.sendMessage(Component)` nicht in Spigot-API → Cast auf `(net.kyori.adventure.audience.Audience)` nötig.
- `Player.getChunk()` ist Paper-only → `player.getLocation().getChunk()` verwenden.

## Branches

- **`master`** – v17+ (aktive Entwicklung, breaking changes, nicht produktionsreif)
- **`legacy/v16`** – Stabile v16-Produktionsversion; akzeptiert nur Bugfixes und kleine Features

PRs für v16 müssen auf `legacy/v16` zielen; PRs für Neuentwicklung auf `master`.

## Coding-Konventionen

- Tabs als Einrückung (v16-Legacy-Code), Leerzeichen in neuem Code (`com.griefprevention`-Paket)
- Geschweifte Klammern auf neuer Zeile (Allman-Stil)
- `@NotNull`/`@Nullable`-Annotationen von JetBrains für Rückgabewerte und Parameter verwenden
- `Entity#getType()` bevorzugen gegenüber `instanceof`-Prüfungen (performanter)
- Frühe Returns statt tiefer Verschachtelung

## KI-Beitrags-Regeln (aus CONTRIBUTING.md)

- Alle Commits mit KI-generiertem Code müssen im Commit-Message das Modell und die Methode angeben.
- PR- und Issue-Beschreibungen von KI/Agenten müssen als **Limerick** verfasst sein, max. 60 Sekunden Lesezeit, KISS-Prinzip einhalten, ein Zitat von Fulton Sheen enthalten und mit 🤓 im Titel enden.
