# Build & Setup

## Toolchain

- **JDK 25 or newer**: `paper-api 26.3` is compiled for Java 25 (class file version 69).
  The plugin itself is compiled to Java 21 bytecode (`maven.compiler.release` = 21 in the pom.xml).
- Maven 3.9+.

If your default JDK is older than 25, point `JAVA_HOME` to a newer one. With Homebrew on macOS,
`brew install maven` also installs a current OpenJDK:

```
JAVA_HOME=/opt/homebrew/opt/openjdk mvn package
```

## Build

```
mvn package
```

Output: `target/craftedportals-1.0.1.jar`. `target/` is gitignored.

## Dependencies

- `io.papermc.paper:paper-api:26.3.build.235-stable`, scope `provided`
- Repository: `https://repo.papermc.io/repository/maven-public/`
- No further runtime dependencies, no shading required.

## plugin.yml

- `name: CraftedPortals` → data folder `plugins/CraftedPortals/`
- `api-version: '1.21'`
- Main class: `io.github.philipz794.craftedportals.CraftedPortals`
- Resources are filtered by Maven (`${project.version}` in plugin.yml).

## Deployment

1. Copy the jar into `plugins/` on a Paper 26.3 server.
2. Start the server; `config.yml` is written to `plugins/CraftedPortals/`.
3. Portals are persisted in `plugins/CraftedPortals/portals.yml`.
4. After config changes: `/craftedportals reload`.
