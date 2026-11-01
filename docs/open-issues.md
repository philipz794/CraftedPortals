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

- (empty – add wishes here)
