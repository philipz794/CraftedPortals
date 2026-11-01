# Architecture & Classes

Package: `io.github.philipz794.craftedportals`

## Classes

| Class | Responsibility |
|---|---|
| `CraftedPortals` | Main class: load config, command, `activate()`, `destroyWithMessage()`, `msg()` (action bar) |
| `FrameDetector` | Detects the frame via flood fill of the air in the portal plane, starting from the neighbours of the clicked block (including diagonals so clicking a corner works). Any closed shape is accepted. Checks the 2-high column minimum, `max-blocks` (hard limit 1000) and the 64-block span cap, full frame blocks, key blocks. `key(x,y,z)` returns the long block key used by the index maps. |
| `Portal` | Interior bounding box (min/max inclusive) plus a boolean mask of the interior cells, axis, world. `contains()`, `intersects(BoundingBox)`, `frame()` (edge neighbours plus diagonal-only corners, derived from the mask), `interior()`, `box()`, `arrival()` (lowest 2-high column nearest the centre), `isLoaded()`, `maskRows()`/`parseMask()` for persistence. |
| `PortalManager` | Networks (`LinkedHashMap` name → list), index world → block → portal for interior and frame. `create/destroy/fill/next`. Persistence in `plugins/CraftedPortals/portals.yml`. |
| `PortalListener` | Clock click, destruction, protection (pistons, fluids), suppression of vanilla teleports, piglin spawns, `WorldLoadEvent` → reindex. |
| `ConfigUpdater` | On startup, brings an existing `config.yml` up to date from the bundled template: appends missing options with their comment block, inserts missing keys inside existing sections (e.g. `messages`), converts renamed options in place (`RENAMED` map + `convert()`). Never touches values or comments the owner wrote. Pure `merge()` for testing. |
| `TravelTask` | Runs every tick: `getNearbyEntities(portal.box())`, per-player delay, `justArrived` lock, `teleportAsync`, yaw/velocity conversion for different axes, leash handling. Every 10 ticks `maintain()`. |

## Suppressed vanilla events

So that vanilla does not treat the portal blocks as its own nether/end portals, the listener cancels:

- `EntityInsideBlockEvent` for nether portal / end gateway blocks of our portals. This is the earliest
  hook (fired every tick while an entity stands in such a block) and prevents vanilla from even starting
  its portal process. It was added after a bug where a player standing in a portal with no destination
  (1 of 1 in its network) was teleported to the Nether by vanilla, including portal generation; the
  later events below did not catch that case on Paper 26.3.

  Cancelling it also removes the purple portal overlay, which is driven by the server-side portal
  process (verified in-game). A selective cancel that lets vanilla's timer run for a while was tried
  and rejected: it turns the protection into a race with vanilla's 80-tick timer. Instead the plugin
  renders its own waiting effect, see below.
- `PlayerPortalEvent`, `EntityPortalEvent`, `EntityPortalReadyEvent`
- `PlayerTeleportEvent` with cause `NETHER_PORTAL` / `END_GATEWAY`
- `EntityTeleportEndGatewayEvent`

## Teleport flow (TravelTask)

1. Per portal `getNearbyEntities(portal.box())` as a coarse filter, then `portal.intersects(entityBox)`
   so that entities standing in a notch of an L-shaped portal are not teleported. Detection never
   depends on block types.
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

### Waiting effect (TravelTask.showWaiting / endWaiting)

Applied every tick to a survival player waiting inside a portal with a destination, removed when the
player travels or steps out (`endWaiting`, also for players who vanished from the portal):

- `NAUSEA` / `DARKNESS`: potion effect refreshed with 100 ticks (the client only ramps nausea while
  more than 60 ticks remain), no icon or particles; removed at the end so it fades.
- `FROST`: `setFreezeTicks` proportional to progress, capped at 130 (vanilla damages from 140). Vanilla
  thaws 2 ticks per tick, hence the `+2`.
- `BORDER`: a per-player `WorldBorder` centred on the player with radius 300 (outside render distance,
  so no wall is visible); the warning distance grows from 300 to 1200, which fades the red vignette in.
  `setWorldBorder(null)` restores the real border.
- Particles around the player (`travel-particles`), count scaling with progress; the swelling
  `BLOCK_PORTAL_TRIGGER` sound once at the start (stopped on early exit); a ten-segment progress bar in
  the action bar.

### PARTICLE effect

`fill()` removes any portal blocks and leaves the interior as air. `TravelTask.emitParticles()` runs
every `interval-ticks` and, for each loaded portal with a player within 48 blocks, spawns `per-block`
particles per interior cell, spread 0.3 in the portal plane and 0.05 perpendicular so the sheet looks
flat. `maintain()` skips the refill check for this effect. `BlockPlaceEvent` into the interior is
cancelled. Particle types that need data other than `DustOptions` (e.g. `BLOCK`, `ITEM`) fall back to
`PORTAL` with a config warning.

### Piglin spawns from portal blocks

Vanilla spawns zombified piglins from nether portal blocks via `NetherPortalBlock.randomTick`
(`CreatureSpawnEvent` with `SpawnReason.NETHER_PORTAL`). The spawn position is the lowest portal
block of the column, i.e. always inside the interior. With `piglin-spawn: false` the listener cancels
the event when the spawn position lies inside one of our portals (`inOurPortal`). Vanilla nether
portals are unaffected.

### Persistence

`portals.yml` stores networks in order; on startup and on `WorldLoadEvent` the indexes are rebuilt
(worlds may load after the plugin).

Per portal the interior bounding box is stored (`minX`…`maxZ`). Non-rectangular portals additionally
carry `cells`, a list of strings from top to bottom with `#` for interior and `.` for not, e.g.
`["#..", "#..", "###"]` for an L. Entries without `cells` are full rectangles, which also makes
files written by version 1.0.0 load unchanged.

### Data folder migration

Before the first public release the plugin was named Clock-Portals. `migrateDataFolder()` in
`onEnable()` moves `plugins/Clock-Portals/` to `plugins/CraftedPortals/` if the new folder does not
exist yet, so config and portals survive the rename.

### Config warnings

`loadSettings()` collects problems (unknown `key-block`, unknown `activation-item`, clamped
`max-blocks`) in `configWarnings`. They are logged immediately, sent to the command sender after
`/craftedportals reload`, and to players with `craftedportals.admin` two seconds after they join. Values
always fall back to safe defaults so the plugin keeps working.

### Config updates

`ConfigUpdater.update()` runs in `onEnable()` right after `saveDefaultConfig()`. It works on the file as
text, so the owner's comments and ordering survive. Missing top-level options are appended with the
comment lines directly above them in the template (a blank line separates a comment block from the
previous option, so the file header is never copied). Missing keys of an existing section are inserted
at the end of that section. A renamed option is rewritten in place: the old key line and its stale
comment block are replaced by the new key with the converted value and the template's comment.
Additions are logged. Running it twice changes nothing.

To rename an option in a future version: add it to `ConfigUpdater.RENAMED` and, if the meaning
changed, to `convert()`.

### Size limits

`max-blocks` counts interior air cells and is clamped to `FrameDetector.HARD_MAX_BLOCKS` (1000) with a
log warning. Independently, `MAX_SPAN` (64) caps width and height so that a 1×1000 line cannot be
built; such a portal would span dozens of chunks and never be fully loaded. During the flood fill,
exceeding either hard limit means the air leaked out of an open frame → `NO_FRAME`. A closed region
above `max-blocks` → `TOO_BIG`. A legacy `max-size` (outer frame size from 1.0.0) is converted to
`(max-size - 2)²` when `max-blocks` is absent.
