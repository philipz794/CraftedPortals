# Open Issues

## Known edge cases

- **Shared frame blocks:** frame blocks shared by two portals are indexed for only one of them.
  Destroying such a block may remove only one of the two portals. Open.
- **NoAI mobs** stay inside the destination portal after arrival (`justArrived`) – intended, do not change.
- **Flicker** of the interior when frame blocks change on their own (dirt ↔ grass, copper, lamps),
  see docs/architecture.md. Cosmetic, accepted.

## Untested

See docs/testing.md, section "Not yet verified".

## Ideas / Backlog

- **Horizontal portals (jump in):** third orientation (plane X-Z), always instant. Visuals should be
  `END_PORTAL` blocks (flat, no vanilla frame validation, so no flicker); the vanilla End teleport must
  then be suppressed like the nether one. Open design question: arrival at a horizontal portal.
  Options: (a) horizontal portals are entrances only, exit is always the next vertical portal;
  (b) "pipe" mode: arrive on top and fall through, builder provides a landing zone. Suggested default
  (a) with (b) as a config option. Planned for after the 1.0.0 release.
