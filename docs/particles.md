# Particle types

123 of the 128 particle types of Minecraft 26.3 can be used (the five block-texture particles `BLOCK`, `FALLING_DUST`, `DUST_PILLAR`, `BLOCK_CRUMBLE`, `BLOCK_MARKER` are left out) for `particles.type` (portal visuals) and
`travel-particles.type` (around the waiting player). Names in upper case. Looks differ between Java and
Bedrock (Geyser maps most common ones).

## Options

| Option | Used by |
|---|---|
| `color` | colourable types below; overridden per network by the item name suffix (`Home #3366FF`) |
| `color-to` | `DUST_COLOR_TRANSITION` (fade target) |
| `item` | `ITEM` |
| `value` | size for `DUST`/`DUST_COLOR_TRANSITION`, power for `DRAGON_BREATH`/`EFFECT`/`INSTANT_EFFECT`, roll for `SCULK_CHARGE`, delay ticks for `SHRIEK`, water blocks for `GEYSER*`, travel seconds for `TRAIL`/`VIBRATION` |

## Recommended for portals

| Name | Look |
|---|---|
| `PORTAL` | classic purple nether portal swirl (default) |
| `REVERSE_PORTAL` | purple swirl moving inwards |
| `END_ROD` | slow white sparks |
| `SOUL` | blue-grey souls, eerie |
| `SOUL_FIRE_FLAME` | blue flames |
| `WITCH` | purple witch sparkles |
| `ENCHANT` | enchanting glyphs |
| `ELECTRIC_SPARK` | cyan sparks |
| `GLOW` | glow squid glow |
| `DRAGON_BREATH` | purple dragon breath cloud |
| `CHERRY_LEAVES` | pink petals |
| `TOTEM_OF_UNDYING` | golden-green confetti |
| `DUST` | coloured dust, colour via `color` |
| `DUST_COLOR_TRANSITION` | dust fading from `color` to `color-to` |
| `TRAIL` | coloured streaks flying into the portal centre |
| `ENTITY_EFFECT` | coloured potion swirls |
| `TINTED_LEAVES` | coloured falling leaves |
| `ITEM` | crumbs of the configured item |

## Colourable

`DUST`, `DUST_COLOR_TRANSITION`, `ENTITY_EFFECT`, `FLASH`, `TINTED_LEAVES`, `TRAIL`, `EFFECT`, `INSTANT_EFFECT`

## Item-based (`item`)

`ITEM`

## Using `value`

`DRAGON_BREATH`, `SCULK_CHARGE`, `SHRIEK`, `GEYSER`, `GEYSER_BASE`, `GEYSER_PLUME`, `GEYSER_POOF`, `VIBRATION`

## No options needed

`ANGRY_VILLAGER`, `ASH`, `BUBBLE`, `BUBBLE_COLUMN_UP`, `BUBBLE_POP`, `CAMPFIRE_COSY_SMOKE`, `CAMPFIRE_SIGNAL_SMOKE`, `CHERRY_LEAVES`, `CLOUD`, `COMPOSTER`, `COPPER_FIRE_FLAME`, `CRIMSON_SPORE`, `CRIT`, `CURRENT_DOWN`, `DAMAGE_INDICATOR`, `DOLPHIN`, `DRIPPING_DRIPSTONE_LAVA`, `DRIPPING_DRIPSTONE_WATER`, `DRIPPING_HONEY`, `DRIPPING_LAVA`, `DRIPPING_OBSIDIAN_TEAR`, `DRIPPING_WATER`, `DUST_PLUME`, `EGG_CRACK`, `ELDER_GUARDIAN`, `ELECTRIC_SPARK`, `ENCHANT`, `ENCHANTED_HIT`, `END_ROD`, `EXPLOSION`, `EXPLOSION_EMITTER`, `FALLING_DRIPSTONE_LAVA`, `FALLING_DRIPSTONE_WATER`, `FALLING_HONEY`, `FALLING_LAVA`, `FALLING_NECTAR`, `FALLING_OBSIDIAN_TEAR`, `FALLING_SPORE_BLOSSOM`, `FALLING_WATER`, `FIREFLY`, `FIREWORK`, `FISHING`, `FLAME`, `GLOW`, `GLOW_SQUID_INK`, `GUST`, `GUST_EMITTER_LARGE`, `GUST_EMITTER_SMALL`, `HAPPY_VILLAGER`, `HEART`, `INFESTED`, `ITEM_COBWEB`, `ITEM_SLIME`, `ITEM_SNOWBALL`, `LANDING_HONEY`, `LANDING_LAVA`, `LANDING_OBSIDIAN_TEAR`, `LARGE_SMOKE`, `LAVA`, `MYCELIUM`, `NAUTILUS`, `NOTE`, `NOXIOUS_GAS`, `NOXIOUS_GAS_CLOUD`, `OMINOUS_SPAWNING`, `ORANGE_POPLAR_LEAVES`, `PALE_OAK_LEAVES`, `PAUSE_MOB_GROWTH`, `POOF`, `PORTAL`, `RAID_OMEN`, `RAIN`, `RED_POPLAR_LEAVES`, `RESET_MOB_GROWTH`, `REVERSE_PORTAL`, `SCRAPE`, `SCULK_CHARGE_POP`, `SCULK_SOUL`, `SMALL_FLAME`, `SMALL_GUST`, `SMOKE`, `SNEEZE`, `SNOWFLAKE`, `SONIC_BOOM`, `SOUL`, `SOUL_FIRE_FLAME`, `SPIT`, `SPLASH`, `SPORE_BLOSSOM_AIR`, `SQUID_INK`, `SULFUR_BUBBLES`, `SULFUR_CUBE_GOO`, `SWEEP_ATTACK`, `TOTEM_OF_UNDYING`, `TRIAL_OMEN`, `TRIAL_SPAWNER_DETECTION`, `TRIAL_SPAWNER_DETECTION_OMINOUS`, `UNDERWATER`, `VAULT_CONNECTION`, `WARPED_SPORE`, `WAX_OFF`, `WAX_ON`, `WHITE_ASH`, `WHITE_SMOKE`, `WITCH`, `YELLOW_POPLAR_LEAVES`

Reference: [Paper Javadoc, org.bukkit.Particle](https://jd.papermc.io/paper/1.21.4/org/bukkit/Particle.html).
