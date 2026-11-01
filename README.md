# Clock-Portals

A server-side [Paper](https://papermc.io) plugin that lets players build portals out of **any full blocks**
and link them with a **named clock**. No client mods required; works for Java and Bedrock players
(via Geyser/Floodgate).

## Features

- Build a vertical frame like a nether portal, but from any full blocks. Corners are optional.
- Right-click the frame with a clock renamed on an anvil. The clock's name becomes the network name.
- Portals sharing a name form a network and are visited in a loop: 1 → 2 → 3 → 1.
- Works across dimensions (Overworld ↔ Nether ↔ End).
- Players, mobs, thrown items and leashed animals can travel. Everything is configurable.
- Two visual styles: nether portal blocks or end gateway blocks.
- Frames are protected from pistons and fluids. Breaking a frame block removes only that portal;
  the rest of the network keeps its order.
- Optional: suppress zombified piglin spawns from portal blocks (default: suppressed).

## Requirements

- Paper 26.3 or newer (API version 1.21)
- Java 21 runtime on the server

## Installation

1. Download `clockportals-<version>.jar` or build it yourself (see below).
2. Drop it into your server's `plugins/` folder and start the server.
3. Adjust `plugins/Clock-Portals/config.yml` as needed, then run `/clockportals reload`.

## Usage

1. Build a vertical rectangular frame from full blocks (interior at least 1 wide × 2 high).
2. Include at least 2 emerald blocks in the frame (block type and count are configurable).
3. Rename a clock on an anvil, e.g. `Home`, and right-click the frame with it.
4. Build a second frame anywhere, activate it with a clock named `Home` too.
5. Walk through.

## Commands

| Command | Description |
|---|---|
| `/clockportals list` | List all networks and portals |
| `/clockportals reload` | Reload the configuration |
| `/clockportals create <name> <x> <y> <z> [world]` | Activate the frame at the given block without a clock (also from console) |

Permission: `clockportals.admin` (default: op)

## Configuration

See [`config.yml`](src/main/resources/config.yml) for all options and messages. Highlights:

| Option | Default | Description |
|---|---|---|
| `key-block` / `key-block-count` | `EMERALD_BLOCK` / `2` | Block type and minimum count required in the frame |
| `max-size` | `30` | Maximum outer width and height of the frame |
| `effect` | `NETHER` | `NETHER` or `END` visuals |
| `player-delay-ticks` | `80` | Time a survival player has to stand in the portal |
| `consume-clock` | `true` | Consume the clock on activation (never in creative) |
| `teleport-mobs` / `teleport-items` / `leashed-follow` | `true` | Who else may travel |
| `piglin-spawn` | `false` | Allow zombified piglins to spawn from portal blocks |

All messages support `&` colour codes.

## Building

Requires Maven and JDK 25 or newer (the Paper API is compiled for Java 25; the plugin itself targets Java 21).

```
mvn package
```

The jar is written to `target/`. See [docs/build.md](docs/build.md) for details.

## Documentation

Developer documentation lives in [`docs/`](docs/README.md).

## License

[MIT](LICENSE)
