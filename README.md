# Clock-Portals

<img src="assets/icon.png" alt="Clock-Portals icon" width="128" align="right">

A server-side [Paper](https://papermc.io) plugin that lets players build portals out of **any full blocks**
and link them with a **named clock**. No client mods required; works for Java and Bedrock players
(via Geyser/Floodgate).

## Features

- Build a vertical frame from any full blocks, in **any closed shape**: rectangle, L, cross, heart ... Corners are optional.
- Right-click the frame with a clock renamed on an anvil. The clock's name becomes the network name.
  The item is configurable, any item works as the key.
- Portals sharing a name form a network and are visited in a loop: 1 → 2 → 3 → 1.
- Works across dimensions (Overworld ↔ Nether ↔ End).
- Players, mobs, thrown items and leashed animals can travel. Everything is configurable.
- Three visual styles: nether portal blocks, end gateway blocks or pure particles (type and colour configurable).
- Frames are protected from pistons and fluids. Breaking a frame block removes only that portal;
  the rest of the network keeps its order.
- Optional: suppress zombified piglin spawns from portal blocks (default: suppressed).

## Why Clock-Portals?

The idea of building gates from any blocks and linking them with a named clock comes from
[CreativeGates](https://github.com/JeremyFail/CreativeGates) by MassiveCraft, a classic that is being
continued today. Clock-Portals is an independent, from-scratch implementation of that concept with a
different focus:

- **Lightweight.** One jar, no library plugin required, six classes.
- **Zero dependencies.** Public Paper API only, no NMS, so updates to new Minecraft versions are cheap.
- **Bedrock friendly.** Everything players see is a right-click and an action bar message, which works
  through Geyser/Floodgate. No chat UIs or dialogs.
- **Server owner first.** A single `config.yml` with comments, updated automatically when new options
  arrive; portals survive restarts in a plain YAML file.

If you need per-player permissions, management dialogs or many gate fill types, CreativeGates is the
more complete choice. If you want portals that just work on a small or medium server, this is for you.

## Requirements

- Paper 26.3 or newer (API version 1.21)
- Java 21 runtime on the server

## Installation

1. Download `clockportals-<version>.jar` or build it yourself (see below).
2. Drop it into your server's `plugins/` folder and start the server.
3. Adjust `plugins/Clock-Portals/config.yml` as needed, then run `/clockportals reload`.

## Usage

1. Build a vertical frame from full blocks in any closed shape. Somewhere inside there must be two blocks of air stacked vertically so a player fits.
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
| `activation-item` | `CLOCK` | Item that activates portals when renamed |
| `key-block` / `key-block-count` | `EMERALD_BLOCK` / `2` | Block type and minimum count required in the frame |
| `max-blocks` | `200` | Maximum number of air blocks inside a portal (hard limit 1000) |
| `effect` | `NETHER` | `NETHER`, `END` or `PARTICLE` visuals (`particles.*` for the latter) |
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
