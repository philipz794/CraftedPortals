# Build & Setup

## Toolchain

- **JDK 25 oder neuer nötig**: `paper-api 26.3` ist mit Java 25 kompiliert (Classfile 69).
  Erzeugt wird trotzdem Java-21-Bytecode (`maven.compiler.release` = 21 in der pom.xml).
- Maven 3.9+.

Auf Philips Mac: Maven und OpenJDK 27 über Homebrew (`brew install maven`). Das System-Java ist
Temurin 24 und reicht nicht, deshalb beim Bauen `JAVA_HOME` setzen:

```
JAVA_HOME=/opt/homebrew/opt/openjdk mvn package
```

## Build

```
mvn package
```

Ergebnis: `target/clockportals-1.0.0.jar`. `target/` ist gitignored.

## Dependencies

- `io.papermc.paper:paper-api:26.3.build.235-stable`, Scope `provided`
- Repository: `https://repo.papermc.io/repository/maven-public/`
- Keine weiteren Laufzeit-Abhängigkeiten, kein Shading nötig.

## plugin.yml

- `name: Clock-Portals` → Datenordner `plugins/Clock-Portals/`
- `api-version: '1.21'`
- Main: `de.philipz.clockportals.ClockPortals`
- Ressourcen werden von Maven gefiltert (`${project.version}` in plugin.yml).

## Deployment

1. JAR nach `plugins/` auf dem Paper-26.3-Server kopieren.
2. Server starten; `config.yml` wird nach `plugins/Clock-Portals/` geschrieben.
3. Portale werden in `plugins/Clock-Portals/portals.yml` gespeichert.
4. Nach Config-Änderungen: `/clockportals reload`.
