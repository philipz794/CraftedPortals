# Architektur & Klassen

Package: `de.philipz.clockportals`

## Klassen

| Klasse | Aufgabe |
|---|---|
| `ClockPortals` | Main-Klasse: Config laden, Befehl, `activate()`, `destroyWithMessage()`, `msg()` (Actionbar) |
| `FrameDetector` | Erkennt den Rahmen per Flood-Fill der Luft in der Portalebene ab den Nachbarn des angeklickten Blocks (inkl. diagonal, damit ein Klick auf eine Ecke funktioniert). Prüft Rechteck, Größe, volle Rahmenblöcke, Schlüsselblöcke. `key(x,y,z)` liefert den Long-Blockschlüssel für die Index-Maps. |
| `Portal` | Innenraum min/max (inklusive), Achse, Welt. `frame()`, `interior()`, `box()`, `arrival()` (unten mittig), `isLoaded()` (alle Chunks des Rahmens geladen). |
| `PortalManager` | Netzwerke (`LinkedHashMap` Name → Liste), Index Welt → Block → Portal für Innenraum und Rahmen. `create/destroy/fill/next`. Persistenz in `plugins/Clock-Portals/portals.yml`. |
| `PortalListener` | Uhr-Klick, Zerstörung, Schutz (Kolben, Flüssigkeiten), Unterdrücken der Vanilla-Teleports, `WorldLoadEvent` → Reindex. |
| `TravelTask` | Läuft jeden Tick: `getNearbyEntities(portal.box())`, Wartezeit pro Spieler, `justArrived`-Sperre, `teleportAsync`, Yaw/Velocity-Umrechnung bei unterschiedlichen Achsen, Leinen-Handling. Alle 10 Ticks `maintain()`. |

## Unterdrückte Vanilla-Events

Damit Vanilla die Portalblöcke nicht selbst als Nether-/End-Portal behandelt, cancelt der Listener:

- `PlayerPortalEvent`, `EntityPortalEvent`, `EntityPortalReadyEvent`
- `PlayerTeleportEvent` mit Cause `NETHER_PORTAL` / `END_GATEWAY`
- `EntityTeleportEndGatewayEvent`

## Teleport-Ablauf (TravelTask)

1. Pro Portal `getNearbyEntities(portal.box())`; Erkennung läuft über die BoundingBox, nicht über Blocktypen.
2. Spieler: Wartezeit zählen (Survival), Kreativ sofort. Mobs/Items sofort (falls aktiviert).
3. Ziel = `PortalManager.next()` im Netzwerk; Ziel muss geladen sein (`isLoaded()`).
4. `teleportAsync` zur `arrival()`-Position. Bei anderer Achse: Yaw um 90° drehen, Velocity umrechnen.
5. Angeleinte Tiere: Leine lösen → Tier teleportieren → nach 2 Ticks wieder anleinen.
6. `justArrived` setzen; wird erst gelöscht, wenn die Entität die Box verlassen hat (min. 20 Ticks).

## Wichtige Eigenheiten

### Vanilla entfernt Netherportal-Blöcke bei Rahmenänderung

Vanilla löscht Netherportal-Blöcke, sobald sich ein Rahmenblock am Innenraum ändert, weil der Rahmen
kein Obsidian ist. `BlockPhysicsEvent` zu canceln reicht **nicht** (getestet).

Lösung: `maintain()` alle 10 Ticks:
- Rahmen noch gültig → Innenraum neu füllen.
- Rahmen ungültig (z. B. `/setblock`, WorldEdit) → Portal entfernen.

Im normalen Spiel betrifft das nur Blöcke, die sich selbst ändern (Erde ↔ Gras, Kupfer oxidiert,
Redstone-Lampe, Ofen an/aus) → kurzes Flackern. Der Teleport hängt nicht davon ab, da die Erkennung
über die BoundingBox läuft.

### Portalblöcke ohne Physik setzen

Portalblöcke immer mit `setBlockData(…, false)` setzen, sonst löst Vanilla Physik-Updates aus.

### END-Effekt

Gateway mit Exit auf sich selbst + `ExactTeleport`, damit Vanilla nichts generiert.
Age hoch gesetzt, damit kein Lichtstrahl erscheint.

### Persistenz

`portals.yml` speichert Netzwerke in Reihenfolge; beim Start und bei `WorldLoadEvent` werden die
Indizes neu aufgebaut (Welten können nach dem Plugin laden).
