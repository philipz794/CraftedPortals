# Gameplay Specification

This file is the authoritative description of the intended behaviour.
Update it alongside behaviour changes in the code.

## Frame

- Vertical frame in one plane, axis X or Z. **Any closed shape**: rectangle, L, T, cross, heart, arrow ...
- The interior is one connected region of air (connected through edges, not diagonals).
- Every block that touches the interior **through an edge** must read as a **solid cube**: one
  collision box over the whole footprint, at least 0.8 blocks high. That is every full block plus soul
  sand, mud, farmland, dirt paths and end portal frames; not stairs, slabs, snow layers, fences, walls,
  panes, chests or enchanting tables. Blocks that touch the interior **only diagonally**
  ("corners") are optional and may be anything, including air.
- Minimum: somewhere in the interior **two blocks of air stacked vertically** (a player must fit).
  A horizontal 2×1 slit is invalid (`messages.too-small`).
- Maximum: `max-blocks` air blocks inside (default 200, hard limit 1000). Width and height of the
  interior are additionally capped at 64 blocks each (not configurable).
- The frame must contain at least `key-block-count` (default 2) blocks of type `key-block` (default `EMERALD_BLOCK`).
- A frame with a dividing pillar encloses two air regions and therefore forms **two portals**;
  the clicked side is the one that gets activated.

## Activation

- Right-click a frame block with the **activation item renamed on an anvil** → portal becomes active.
  The item is `activation-item` (default `CLOCK`, any item from the registry). An unknown name falls
  back to `CLOCK`; the problem is logged and shown to admins (`craftedportals.admin`) on join and after
  `/craftedportals reload`.
- The item is consumed (`consume-item`, default true), never in creative mode.
- Item name = network name. Key is case-insensitive, display name as typed.
- A trailing hex colour in the item name (`Home #3366FF`) is stripped from the name and stored as the
  network's colour. It recolours the PARTICLE sheet and DUST particles of every portal in that network,
  also existing ones. A name that is only a colour stays a plain name.
- Right-click an already active portal with a named item → info only ("name, #X of Y"), no renaming.
- Unnamed item on a valid frame → hint to rename it (`messages.no-name`, placeholder `%item%`).
- Admin alternative without a clock: `/craftedportals create <name> <x> <y> <z> [world]` (also from console).

## Networks & Travel

- Same name = one network. Numbering follows activation order.
- Travel in a loop 1 → 2 → 3 → 1. A network with a single portal → `messages.no-target`.
- Works across dimensions (Overworld ↔ Nether ↔ End).
- Arrival in the lowest two-block-high column closest to the centre of the destination shape, standing
  in the middle of that block. For a rectangle this is the bottom centre. Different axes → rotate yaw
  by 90°, convert velocity.
- After arrival a lock (`justArrived`) applies until the entity has left the portal (at least 20 ticks).

## Delays and the waiting effect

- Survival players: `player-delay-ticks` (default 60; a vanilla nether portal takes 80).
- Creative: instant. Mobs and items: instant.
- `effect: END`: always instant.
- A portal without destination tells the player immediately (`messages.no-target`), no waiting.
- While waiting, `travel-effect` decides what the player sees: NAUSEA (default), FROST, DARKNESS or
  NONE, independent of the portal look. Vanilla's purple overlay is never shown: with NETHER blocks the
  touched portal blocks are hidden from that player, so the client cannot draw it. Independently: `travel-particles` around the player (own type/colour),
  `travel-sound` (swelling portal trigger sound) and `travel-bar` (progress bar in the action bar,
  `messages.travel-bar`). Everything stops when the player teleports or steps out early.
- After arriving, players see `messages.arrived` (name, #X of Y) in the action bar.

## Extras (each configurable)

- `leashed-follow`: leashed animals travel along with the player.
- `teleport-mobs`: animals, villagers and monsters walk through on their own.
- `teleport-items`: thrown items fly through.
- `piglin-spawn` (default **false**): may zombified piglins spawn from the nether portal blocks of a
  CraftedPortal? Only affects CraftedPortals; vanilla nether portals are unchanged.

## Visuals

- `effect: NETHER` → nether portal blocks in the interior.
- Default is `effect: PARTICLE` with a royal-blue sheet at 50 % and steel-blue TRAIL particles streaking
  into the centre.
- `effect: END` → end gateway blocks, teleport always instant.
- `effect: PARTICLE` → the interior stays air and is filled with particles (`particles.*`: any type of
  the game with its options `color`, `color-to`, `block`, `item`, `value`; density, interval, ambient sound) plus, if `particles.sheet.enabled`, a translucent coloured
  sheet (`color`, `opacity` in percent; Java clients only). Players cannot place blocks into the interior.
  Normal player delay applies. No vanilla portal logic is involved at all.
- All effects can be switched live with `/craftedportals reload`.

## Destruction & Protection

- Breaking, explosion, fire or an enderman taking a frame block → only this portal drops out of the
  network; the others keep their order.
- Pistons and fluids cannot modify frame or interior.
- Frame becomes invalid externally (e.g. `/setblock`, WorldEdit) → portal is removed by `maintain()`.

## Messages & Commands

- All messages are shown in the action bar, texts in `config.yml` (`&` colour codes). Config,
  player-facing texts and log output are English; server admins can translate the messages.
- Command `/craftedportals list | reload | create <name> <x> <y> <z> [world]`.
- Permission `craftedportals.admin` (default: op).
