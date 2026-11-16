# Releasing

Distribution happens through GitHub releases only.

1. Bump `<version>` in `pom.xml`, update `README.md` / docs if options changed.
2. Build: `JAVA_HOME=/opt/homebrew/opt/openjdk mvn clean package` → `target/craftedportals-<version>.jar`.
3. Commit, then tag and release:
   ```
   git tag -a v<version> -m "CraftedPortals <version>"
   git push origin v<version>
   gh release create v<version> target/craftedportals-<version>.jar --title "CraftedPortals <version>" --notes-file notes.md --latest
   ```
   The release body is the changelog players see, so write it for them.

## Store assets

- Gallery images: full-size originals in `assets/screenshots/originals/` (local only), icon `assets/icon.png` (local only).
  Kept for a possible listing on a plugin platform later.
