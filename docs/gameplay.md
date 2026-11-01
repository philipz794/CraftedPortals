# Gameplay Specification

This file is the authoritative description of the intended behaviour.
Update it alongside behaviour changes in the code.

## Frame

- Vertical frame like a nether portal, axis X or Z.
- Any **full 1×1 blocks** (no stairs, slabs, snow, fences etc.).
- **Corners are optional.**
- Interior **at least 1 wide × 2 high** (outer frame 3×4).
- Maximum outer size `max-size` (default 30) in width and height.
- The frame must contain at least `key-block-count` (default 2) blocks of type `key-block` (default `EMERALD_BLOCK`).

## Activation

- Right-click a frame block with a **clock renamed on an anvil** → portal becomes active.
- The clock is consumed (`consume-clock`, default true), never in creative mode.
- Clock name = network name. Key is case-insensitive, display name as typed.
- Right-click an already active portal with a named clock → info only ("name, #X of Y"), no renaming.
- Unnamed clock on a valid frame → hint to rename the clock (`messages.no-name`).
- Admin alternative without a clock: `/clockportals create <name> <x> <y> <z> [world]` (also from console).

## Networks & Travel

- Same name = one network. Numbering follows activation order.
- Travel in a loop 1 → 2 → 3 → 1. A network with a single portal → `messages.no-target`.
- Works across dimensions (Overworld ↔ Nether ↔ End).
- Arrival at the bottom centre of the destination portal. Different axes → rotate yaw by 90°, convert velocity.
- After arrival a lock (`justArrived`) applies until the entity has left the portal (at least 20 ticks).

## Delays

- Survival players: `player-delay-ticks` (default 80, like a vanilla nether portal).
- Creative: instant. Mobs and items: instant.
- `effect: END`: always instant.

## Extras (each configurable)

- `leashed-follow`: leashed animals travel along with the player.
- `teleport-mobs`: animals, villagers and monsters walk through on their own.
- `teleport-items`: thrown items fly through.
- `piglin-spawn` (default **false**): may zombified piglins spawn from the nether portal blocks of a
  Clock-Portal? Only affects Clock-Portals; vanilla nether portals are unchanged.

## Visuals

- `effect: NETHER` → nether portal blocks in the interior.
- `effect: END` → end gateway blocks, teleport always instant.

## Destruction & Protection

- Breaking, explosion, fire or an enderman taking a frame block → only this portal drops out of the
  network; the others keep their order.
- Pistons and fluids cannot modify frame or interior.
- Frame becomes invalid externally (e.g. `/setblock`, WorldEdit) → portal is removed by `maintain()`.

## Messages & Commands

- All messages are shown in the action bar, texts in `config.yml` (`&` colour codes). Config,
  player-facing texts and log output are English; server admins can translate the messages.
- Command `/clockportals list | reload | create <name> <x> <y> <z> [world]`.
- Permission `clockportals.admin` (default: op).
