# CraftedPortals – Notes for Claude

Paper plugin (Java 21 bytecode, built with JDK 25+) for Paper 26.3. Public Paper API only, no NMS.

Language: everything in this repository is English (code, comments, config, player-facing texts,
docs, commit messages). The project is meant to be released publicly; do not mention the author's
real name anywhere.

## Commands

- Build: `mvn package` → `target/craftedportals-1.0.1.jar`. Needs JDK 25+ on `JAVA_HOME`
  (Homebrew on macOS: `JAVA_HOME=/opt/homebrew/opt/openjdk mvn package`). See docs/build.md.
- There are no automated tests. Verification happens on a Paper test server (docs/testing.md).
- Releases: see docs/release.md. A GitHub release triggers the Modrinth publish workflow.

## Layout

- `src/main/java/io/github/philipz794/craftedportals/` – all classes (main, FrameDetector, Portal, PortalManager, PortalListener, TravelTask)
- `src/main/resources/` – `plugin.yml`, `config.yml` (defaults and messages)
- `docs/` – developer documentation. New knowledge goes there, not into this file.

## Rules

- No client mods, no NMS. Bedrock players (Geyser/Floodgate) must work.
- Vanilla portal teleports (nether / end gateway) are suppressed in the listener; when changing the
  portal visuals, make sure that still holds.
- Always place portal blocks with `setBlockData(…, false)` (no physics), see docs/architecture.md.
- Configurable values and player-facing texts come from `config.yml`, never hard-coded. New options
  need a comment block directly above them in the bundled `config.yml`; `ConfigUpdater` copies it into
  existing server configs. Renamed options go into `ConfigUpdater.RENAMED`.
- Read docs/gameplay.md before touching frame detection or teleport logic; the specification there
  is authoritative and must be updated alongside behaviour changes.

## Keeping docs

Keep this file short. Put details, decisions and quirks into `docs/` as separate Markdown files and
link them from `docs/README.md`.
