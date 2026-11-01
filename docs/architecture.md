# Architecture & Classes

Package: `de.philipz.clockportals`

## Classes

| Class | Responsibility |
|---|---|
| `ClockPortals` | Main class: load config, command, `activate()`, `destroyWithMessage()`, `msg()` (action bar) |
| `FrameDetector` | Detects the frame via flood fill of the air in the portal plane, starting from the neighbours of the clicked block (including diagonals so clicking a corner works). Checks rectangle, size, full frame blocks, key blocks. `key(x,y,z)` returns the long block key used by the index maps. |
| `Portal` | Interior min/max (inclusive), axis, world. `frame()`, `interior()`, `box()`, `arrival()` (bottom centre), `isLoaded()` (all chunks of the frame loaded). |
| `PortalManager` | Networks (`LinkedHashMap` name → list), index world → block → portal for interior and frame. `create/destroy/fill/next`. Persistence in `plugins/Clock-Portals/portals.yml`. |
| `PortalListener` | Clock click, destruction, protection (pistons, fluids), suppression of vanilla teleports, piglin spawns, `WorldLoadEvent` → reindex. |
| `TravelTask` | Runs every tick: `getNearbyEntities(portal.box())`, per-player delay, `justArrived` lock, `teleportAsync`, yaw/velocity conversion for different axes, leash handling. Every 10 ticks `maintain()`. |

## Suppressed vanilla events

So that vanilla does not treat the portal blocks as its own nether/end portals, the listener cancels:

- `PlayerPortalEvent`, `EntityPortalEvent`, `EntityPortalReadyEvent`
- `PlayerTeleportEvent` with cause `NETHER_PORTAL` / `END_GATEWAY`
- `EntityTeleportEndGatewayEvent`

## Teleport flow (TravelTask)

1. Per portal `getNearbyEntities(portal.box())`; detection uses the bounding box, not block types.
2. Players: count delay (survival), creative instant. Mobs/items instant (if enabled).
3. Destination = `PortalManager.next()` in the network; destination must be loaded (`isLoaded()`).
4. `teleportAsync` to the `arrival()` position. Different axis: rotate yaw by 90°, convert velocity.
5. Leashed animals: unleash → teleport animal → re-leash after 2 ticks.
6. Set `justArrived`; cleared only once the entity has left the box (at least 20 ticks).

## Important quirks

### Vanilla removes nether portal blocks on frame changes

Vanilla deletes nether portal blocks as soon as a frame block adjacent to the interior changes,
because the frame is not obsidian. Cancelling `BlockPhysicsEvent` is **not** enough (tested).

Solution: `maintain()` every 10 ticks:
- Frame still valid → refill the interior.
- Frame invalid (e.g. `/setblock`, WorldEdit) → remove the portal.

In normal gameplay this only affects blocks that change on their own (dirt ↔ grass, copper oxidising,
redstone lamps, furnaces lit/unlit) → a short flicker. Teleportation is unaffected since detection
uses the bounding box.

### Place portal blocks without physics

Always place portal blocks with `setBlockData(…, false)`, otherwise vanilla triggers physics updates.

### END effect

Gateway with exit pointing to itself plus `ExactTeleport`, so vanilla generates nothing.
Age set high so no beam is rendered.

### Piglin spawns from portal blocks

Vanilla spawns zombified piglins from nether portal blocks via `NetherPortalBlock.randomTick`
(`CreatureSpawnEvent` with `SpawnReason.NETHER_PORTAL`). The spawn position is the lowest portal
block of the column, i.e. always inside the interior. With `piglin-spawn: false` the listener cancels
the event when the spawn position lies inside one of our portals (`inOurPortal`). Vanilla nether
portals are unaffected.

### Persistence

`portals.yml` stores networks in order; on startup and on `WorldLoadEvent` the indexes are rebuilt
(worlds may load after the plugin).
