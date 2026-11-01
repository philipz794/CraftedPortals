# Releasing

1. Bump `<version>` in `pom.xml`, update `README.md` / docs if options changed.
2. Build: `JAVA_HOME=/opt/homebrew/opt/openjdk mvn clean package` → `target/craftedportals-<version>.jar`.
3. Commit, then tag and release:
   ```
   git tag -a v<version> -m "CraftedPortals <version>"
   git push origin v<version>
   gh release create v<version> target/craftedportals-<version>.jar --title "CraftedPortals <version>" --notes-file notes.md --latest
   ```
   The release body is used as the Modrinth changelog, so write it for players.
4. The workflow `.github/workflows/modrinth.yml` picks the release up and creates the Modrinth version
   (loader Paper, game versions from the list in the workflow; keep that list to what was tested).
   To republish an existing release, run the workflow by hand with its tag.

## Modrinth

- Project: https://modrinth.com/project/craftedplugins
- Secret `MODRINTH_TOKEN` (repository secret): Modrinth PAT with Read projects, Create versions, Write versions.
- Description text: `~/Downloads/craftedportals-modrinth.md` (keep in sync with the README).
- Gallery images: full-size originals in `assets/screenshots/originals/` (local only), icon `assets/icon.png` (local only).
