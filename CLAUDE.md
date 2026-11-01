# Clock-Portals – Hinweise für Claude

Paper-Plugin (Java 21 Bytecode, gebaut mit JDK 25) für Paper 26.3.
Nur öffentliche Paper-API, kein NMS. Sprache im Projekt (Kommentare, Texte, Docs): Deutsch.

## Kommandos

- Build: `JAVA_HOME=/opt/homebrew/opt/openjdk mvn package` → `target/clockportals-1.0.0.jar` (JDK 25+, siehe docs/build.md)
- Es gibt keine automatisierten Tests. Prüfung erfolgt auf einem Paper-Testserver (docs/testen.md).

## Struktur

- `src/main/java/de/philipz/clockportals/` – alle Klassen (Main, FrameDetector, Portal, PortalManager, PortalListener, TravelTask)
- `src/main/resources/` – `plugin.yml`, `config.yml` (Defaults + deutsche Meldungen)
- `docs/` – Dev-Dokumentation. Neues Wissen gehört dorthin, nicht in diese Datei.

## Regeln

- Keine Client-Mods, keine NMS-Zugriffe. Bedrock-Spieler (Geyser/Floodgate) müssen funktionieren.
- Vanilla-Portal-Teleports (Nether/End-Gateway) werden im Listener unterdrückt; beim Ändern der Portal-Optik prüfen, dass das weiterhin gilt.
- Portalblöcke immer mit `setBlockData(…, false)` setzen (keine Physik), siehe docs/architektur.md.
- Konfigurierbare Werte und Spielertexte kommen aus `config.yml`, nicht hartkodiert.
- Vor Änderungen an Rahmenerkennung oder Teleport-Logik docs/spielmechanik.md lesen; die Spezifikation dort ist maßgeblich und bei Verhaltensänderungen mit zu pflegen.

## Docs pflegen

Diese Datei kurz halten. Details, Entscheidungen und Eigenheiten in `docs/` als eigene Markdown-Dateien
ablegen und in `docs/README.md` verlinken.
