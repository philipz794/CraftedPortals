# Spielmechanik (Spezifikation)

Diese Datei ist die maßgebliche Beschreibung des gewünschten Verhaltens.
Bei Verhaltensänderungen im Code hier mitpflegen.

## Rahmen

- Senkrechter Rahmen wie ein Netherportal, Achse X oder Z.
- Beliebige **volle 1×1-Blöcke** (keine Stufen, Slabs, Schnee, Zäune etc.).
- **Ecken optional.**
- Innenraum **mindestens 1 breit × 2 hoch** (Rahmen außen 3×4).
- Maximales Außenmaß `max-size` (Default 30) in Breite und Höhe.
- Im Rahmen mindestens `key-block-count` (Default 2) Blöcke vom Typ `key-block` (Default `EMERALD_BLOCK`).

## Aktivierung

- Rechtsklick mit einer **am Amboss benannten Uhr** auf einen Rahmenblock → Portal aktiv.
- Die Uhr wird verbraucht (`consume-clock`, Default true), nicht im Kreativmodus.
- Uhrname = Netzwerkname. Schlüssel case-insensitive, Anzeige wie getippt.
- Rechtsklick mit benannter Uhr auf ein bereits aktives Portal → nur Info („Name, Nr. X von Y“), kein Umbenennen.
- Unbenannte Uhr auf gültigen Rahmen → Hinweis „Uhr benennen“ (`messages.no-name`).
- Admin-Alternative ohne Uhr: `/clockportals create <name> <x> <y> <z> [welt]` (auch von der Konsole).

## Netzwerke & Reise

- Gleicher Name = ein Netzwerk. Nummerierung nach Aktivierungsreihenfolge.
- Reise im Kreis 1 → 2 → 3 → 1. Portal mit nur einem Mitglied → Meldung `messages.no-target`.
- Funktioniert dimensionsübergreifend (Oberwelt ↔ Nether ↔ End).
- Ankunft unten mittig im Zielportal. Unterschiedliche Achsen → Yaw um 90° drehen, Velocity umrechnen.
- Nach Ankunft Sperre (`justArrived`), bis die Entität das Portal verlassen hat (mind. 20 Ticks).

## Wartezeiten

- Spieler im Survival: `player-delay-ticks` (Default 80, wie Vanilla-Netherportal).
- Kreativ: sofort. Mobs und Items: sofort.
- `effect: END`: immer sofort.

## Extras (jeweils per Config abschaltbar)

- `leashed-follow`: angeleinte Tiere reisen mit dem Spieler mit.
- `teleport-mobs`: Tiere, Villager, Monster laufen selbst durch.
- `teleport-items`: geworfene Items fliegen durch.
- `piglin-spawn` (Default **false**): dürfen aus den Netherportal-Blöcken eines Clock-Portals
  Zombifizierte Piglins spawnen? Betrifft nur Clock-Portals, Vanilla-Netherportale bleiben unverändert.

## Optik

- `effect: NETHER` → Netherportal-Blöcke im Innenraum.
- `effect: END` → End-Gateway-Blöcke, Teleport immer sofort.

## Zerstörung & Schutz

- Abbauen, Explosion, Verbrennen oder Enderman an einem Rahmenblock → nur dieses Portal fällt
  aus dem Netzwerk, die übrigen behalten ihre Reihenfolge.
- Kolben und Flüssigkeiten können Rahmen und Innenraum nicht verändern.
- Rahmen wird extern ungültig (z. B. `/setblock`, WorldEdit) → Portal wird bei `maintain()` entfernt.

## Meldungen & Befehle

- Alle Meldungen in der Actionbar, Texte in `config.yml` (`&`-Farbcodes, deutsch).
- Befehl `/clockportals list | reload | create <name> <x> <y> <z> [welt]`.
- Permission `clockportals.admin` (Default: op).
