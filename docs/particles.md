# Particle types for `effect: PARTICLE`

Set `particles.type` in `config.yml` to one of the names below (upper case, as listed). The list is
generated from the Paper 26.3 API; every particle that needs no extra data works. Looks differ
between Java and Bedrock (Geyser maps most common ones).

## Recommended

| Name | Look |
|---|---|
| `PORTAL` | classic purple nether portal swirl (default) |
| `REVERSE_PORTAL` | purple swirl moving inwards |
| `END_ROD` | slow white sparks |
| `SOUL` | blue-grey souls, eerie |
| `SOUL_FIRE_FLAME` | blue flames |
| `FLAME` | orange flames |
| `WITCH` | purple witch sparkles |
| `ENCHANT` | enchanting glyphs |
| `ELECTRIC_SPARK` | cyan sparks |
| `GLOW` | glow squid glow |
| `CHERRY_LEAVES` | pink petals |
| `SCRAPE` | copper-green sparkles |
| `WAX_ON` | orange sparkles |
| `CLOUD` | white smoke |
| `SMOKE` | grey smoke |
| `NAUTILUS` | conduit glyphs |
| `TOTEM_OF_UNDYING` | golden-green confetti |
| `FIREWORK` | firework sparks |
| `HAPPY_VILLAGER` | green sparkles |
| `SNOWFLAKE` | snowflakes |
| `ASH` | falling ash |
| `WHITE_ASH` | falling white ash |
| `CRIMSON_SPORE` | red spores |
| `WARPED_SPORE` | cyan spores |
| `SPORE_BLOSSOM_AIR` | green spore drift |
| `OMINOUS_SPAWNING` | blue ominous glyphs |
| `TRIAL_SPAWNER_DETECTION_OMINOUS` | blue trial spawner cloud |
| `SCULK_SOUL` | sculk souls |

Note: `DUST` is the only data-driven particle the plugin supports; set its colour with `particles.color`.

## All usable names

`POOF`, `EXPLOSION`, `EXPLOSION_EMITTER`, `FIREWORK`, `BUBBLE`, `SPLASH`, `FISHING`, `UNDERWATER`, `CRIT`, `ENCHANTED_HIT`, `SMOKE`, `LARGE_SMOKE`, `WITCH`, `DRIPPING_WATER`, `DRIPPING_LAVA`, `ANGRY_VILLAGER`, `HAPPY_VILLAGER`, `MYCELIUM`, `NOTE`, `PORTAL`, `ENCHANT`, `FLAME`, `LAVA`, `CLOUD`, `ITEM_SNOWBALL`, `ITEM_SLIME`, `HEART`, `RAIN`, `ELDER_GUARDIAN`, `END_ROD`, `DAMAGE_INDICATOR`, `SWEEP_ATTACK`, `TOTEM_OF_UNDYING`, `SPIT`, `SQUID_INK`, `BUBBLE_POP`, `CURRENT_DOWN`, `BUBBLE_COLUMN_UP`, `NAUTILUS`, `DOLPHIN`, `SNEEZE`, `CAMPFIRE_COSY_SMOKE`, `CAMPFIRE_SIGNAL_SMOKE`, `COMPOSTER`, `FALLING_LAVA`, `LANDING_LAVA`, `FALLING_WATER`, `DRIPPING_HONEY`, `FALLING_HONEY`, `LANDING_HONEY`, `FALLING_NECTAR`, `SOUL_FIRE_FLAME`, `ASH`, `CRIMSON_SPORE`, `WARPED_SPORE`, `SOUL`, `DRIPPING_OBSIDIAN_TEAR`, `FALLING_OBSIDIAN_TEAR`, `LANDING_OBSIDIAN_TEAR`, `REVERSE_PORTAL`, `WHITE_ASH`, `FALLING_SPORE_BLOSSOM`, `SPORE_BLOSSOM_AIR`, `SMALL_FLAME`, `SNOWFLAKE`, `DRIPPING_DRIPSTONE_LAVA`, `FALLING_DRIPSTONE_LAVA`, `DRIPPING_DRIPSTONE_WATER`, `FALLING_DRIPSTONE_WATER`, `GLOW_SQUID_INK`, `GLOW`, `WAX_ON`, `WAX_OFF`, `ELECTRIC_SPARK`, `SCRAPE`, `SONIC_BOOM`, `SCULK_SOUL`, `SCULK_CHARGE_POP`, `CHERRY_LEAVES`, `PALE_OAK_LEAVES`, `RED_POPLAR_LEAVES`, `ORANGE_POPLAR_LEAVES`, `YELLOW_POPLAR_LEAVES`, `EGG_CRACK`, `DUST_PLUME`, `WHITE_SMOKE`, `GUST`, `SMALL_GUST`, `GUST_EMITTER_LARGE`, `GUST_EMITTER_SMALL`, `TRIAL_SPAWNER_DETECTION`, `TRIAL_SPAWNER_DETECTION_OMINOUS`, `VAULT_CONNECTION`, `INFESTED`, `ITEM_COBWEB`, `FIREFLY`, `OMINOUS_SPAWNING`, `RAID_OMEN`, `TRIAL_OMEN`, `COPPER_FIRE_FLAME`, `PAUSE_MOB_GROWTH`, `RESET_MOB_GROWTH`, `NOXIOUS_GAS`, `NOXIOUS_GAS_CLOUD`, `SULFUR_CUBE_GOO`, `SULFUR_BUBBLES`

## Not usable (need block, item or entity data)

These fall back to `PORTAL` with a config warning:

`EFFECT`, `INSTANT_EFFECT`, `ENTITY_EFFECT`, `ITEM`, `BLOCK`, `DRAGON_BREATH`, `FALLING_DUST`, `FLASH`, `DUST_COLOR_TRANSITION`, `VIBRATION`, `SCULK_CHARGE`, `SHRIEK`, `TINTED_LEAVES`, `DUST_PILLAR`, `BLOCK_CRUMBLE`, `TRAIL`, `BLOCK_MARKER`, `GEYSER`, `GEYSER_BASE`, `GEYSER_PLUME`, `GEYSER_POOF`

Reference: [Paper Javadoc, org.bukkit.Particle](https://jd.papermc.io/paper/1.21.4/org/bukkit/Particle.html).
