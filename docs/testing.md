# Testing

There are no unit tests. Verification happens on a Paper 26.3 test server, preferably via RCON
(for console commands) and with a real player for interactions.

## Useful commands

- `/craftedportals create <name> <x> <y> <z> [world]` – activate a portal without a clock (also from console).
- `/craftedportals list` – all networks and portals.
- `/craftedportals reload` – reload the config.
- `/summon`, `/setblock`, `/fill`, TNT – for mob, frame and destruction tests.

## Verified (RCON, Paper 26.3)

- Frame detection: with/without corners, missing key blocks, invalid shapes.
- Mob travel including Overworld → Nether.
- Thrown items with rotated direction for different axes.
- Loop traversal 1 → 2 → 3 → 1.
- Explosion removes only the hit portal; the others keep their order.
- Interior repair after frame changes (`maintain()`).
- END effect and switching back to NETHER.
- Persistence across restarts.

## Verified in-game (real player)

- Clock right-click including clock consumption.
- Player delay, player teleport and yaw rotation.

## Not yet verified

- Single-portal network (1 of 1), player in creative and survival: must only show `no-target`, no
  vanilla Nether teleport and no portal generated in the Nether (regression, fixed via
  `EntityInsideBlockEvent`).
- PARTICLE effect: switch via reload from NETHER (blocks vanish), particle sheet visible, teleport
  works, placing a block into the interior is refused, switch back to NETHER refills. Try DUST with
  a colour and an invalid type (warning for admins).
- Free-form portals: L, T, cross and a frame with a dividing pillar (two portals). Check that
  standing in a notch does not teleport, arrival in the tall column, `cells` in portals.yml,
  `too-small` for a horizontal slit, `too-big` above `max-blocks`, legacy `max-size` conversion.

- Leashed animals travelling along (`leashed-follow`).
- Bedrock players via Geyser/Floodgate (action bar, right-click, teleport).
- `piglin-spawn: true` actually allowing spawns (vanilla spawn chance is low; never on peaceful).

## Config update on a server running 1.0.0

Expected log lines on first start with the new jar: `renamed option max-size -> max-blocks` and
`added new option(s) piglin-spawn, messages.too-small`. The file keeps all owner values and comments;
`max-blocks` is `(max-size - 2)²`, i.e. 784 for the default 30.

## Checklist after changes

1. `mvn package`, copy the jar to the test server, restart.
2. Create two portals via `create`, send a mob through.
3. Check the affected section of the specification (docs/gameplay.md) specifically.
4. Inspect `portals.yml` after a restart.
