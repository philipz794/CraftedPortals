# Testen

Es gibt keine Unit-Tests. Geprüft wird auf einem Paper-26.3-Testserver, bevorzugt per RCON
(für Konsolenbefehle) und mit einem echten Spieler für Interaktionen.

## Hilfreiche Befehle

- `/clockportals create <name> <x> <y> <z> [welt]` – Portal ohne Uhr aktivieren (auch Konsole).
- `/clockportals list` – alle Netzwerke und Portale.
- `/clockportals reload` – Config neu laden.
- `/summon`, `/setblock`, `/fill`, TNT – für Mob-, Rahmen- und Zerstörungstests.

## Bereits geprüft (per RCON, Paper 26.3)

- Rahmenerkennung: mit/ohne Ecken, fehlende Schlüsselblöcke, ungültige Formen.
- Mob-Reise inkl. Oberwelt → Nether.
- Item-Wurf mit gedrehter Richtung bei unterschiedlichen Achsen.
- Kreis-Durchlauf 1 → 2 → 3 → 1.
- Explosion zerstört nur das getroffene Portal, Reihenfolge der übrigen bleibt.
- Reparatur des Innenraums nach Rahmenänderung (`maintain()`).
- END-Effekt und Zurückschalten auf NETHER.
- Persistenz über Neustart.

## Noch nicht geprüft (braucht echten Spieler) – zuerst im Spiel testen

- Uhr-Rechtsklick inkl. Verbrauch der Uhr (Survival vs. Kreativ).
- Spieler-Wartezeit (`player-delay-ticks`).
- Spieler-Teleport inkl. Yaw-Drehung.
- Angeleinte Tiere mitnehmen (`leashed-follow`).
- Bedrock-Spieler über Geyser/Floodgate (Actionbar, Rechtsklick, Teleport).

## Checkliste nach Änderungen

1. `mvn package`, JAR auf Testserver, Neustart.
2. Zwei Portale per `create` anlegen, Mob durchschicken.
3. Betroffenen Bereich aus der Spezifikation (docs/spielmechanik.md) gezielt prüfen.
4. `portals.yml` nach Neustart kontrollieren.
