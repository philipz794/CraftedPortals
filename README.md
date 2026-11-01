# Clock-Portals

Server-only Paper-Plugin (keine Client-Mods) für Philips Minecraft-Server
(Paper 26.3, Java + Bedrock via Geyser/Floodgate).

Portale aus beliebigen vollen Blöcken, aktiviert mit einer am Amboss benannten Uhr.
Gleicher Uhrname = gemeinsames Netzwerk, Reise im Kreis 1 → 2 → 3 → 1, auch dimensionsübergreifend.

## Schnellstart

- Build: `mvn package` (JDK 25 nötig) → `target/clockportals-1.0.0.jar`
- JAR nach `plugins/` auf den Paper-Server kopieren
- Konfiguration: `plugins/Clock-Portals/config.yml`
- Befehl: `/clockportals list | reload | create <name> <x> <y> <z> [welt]` (Permission `clockportals.admin`)

## Dokumentation

Siehe [docs/](docs/README.md):

- [Build & Setup](docs/build.md)
- [Spielmechanik (Spezifikation)](docs/spielmechanik.md)
- [Architektur & Klassen](docs/architektur.md)
- [Testen](docs/testen.md)
- [Offene Punkte](docs/offene-punkte.md)
