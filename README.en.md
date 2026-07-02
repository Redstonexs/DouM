# DouM

[![Build](https://github.com/Redstonexs/DouM/actions/workflows/build.yml/badge.svg)](https://github.com/Redstonexs/DouM/actions/workflows/build.yml)

[简体中文](README.md)

DouM is a Paper plugin for Folia-compatible servers that counts each player's deaths and blocks configured actions until that player has reached the required death count. The first supported gates are block breaking, block placing, and item crafting. DouM also includes disabled-by-default hardship rules for crafting failure, furnace trouble, no double chests, sleep/fishing/fall/low-health/biome penalties, and block retaliation.

The plugin descriptor includes `folia-supported: true`. DouM uses Paper/Folia event surfaces directly and does not use Bukkit scheduler APIs for world or entity access. The current build targets Java 25 and the Paper/Folia API used by this project; there is No broad multi-version promise.

## Install

1. Build the plugin:

   ```bash
   GRADLE_USER_HOME=.gradle-user-home ./gradlew --no-daemon clean test build
   ```

2. Copy the built jar from `build/libs/deathgates-0.1.0-SNAPSHOT.jar` into your server's `plugins/` directory.
3. Start the server once so `plugins/DouM/config.yml` is created.
4. Edit `plugins/DouM/config.yml` for your gates.
5. Run `/doum reload` from an operator or permissioned admin account after config changes.

## Continuous integration & downloads

GitHub Actions builds and tests the plugin automatically (`.github/workflows/build.yml`):

- **Every push, pull request, or manual run** compiles the plugin, runs the unit tests, and uploads the built jar as a workflow artifact named `deathgates-plugin`. Download it from the run page under **Summary -> Artifacts**.
- **Pushing a `v*` tag** (for example `v1.0.0`) additionally publishes a GitHub Release with the jar attached and auto-generated notes. The release jar is stamped with the tag version (for example `deathgates-1.0.0.jar`) via the `-PreleaseVersion` Gradle property.

To cut a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Configuration

A top-level `message-prefix` styles the tag shown before every DouM message, and each operation has independent settings:

```yaml
# MiniMessage prefix before every DouM message; set to "" to disable it.
message-prefix: "<dark_gray>[<aqua>DouM</aqua>]</dark_gray> "

operations:
  block-break:
    enabled: true
    default-required-deaths: 0
    bypass-permission: doum.deathnum.bypass.block-break
    # Blank deny-message uses the player's client language (English/Chinese). Set a string to override.
    deny-message: ""
    targets:
      "material:minecraft:diamond_ore": 2

  block-place:
    enabled: true
    default-required-deaths: 1
    bypass-permission: doum.deathnum.bypass.block-place
    deny-message: ""
    targets:
      "material:minecraft:obsidian": 3

  craft-item:
    enabled: true
    default-required-deaths: 0
    bypass-permission: doum.deathnum.bypass.craft-item
    deny-message: ""
    targets:
      "recipe:minecraft:oak_planks": 1
      "result:minecraft:oak_planks": 2

hardship-rules:
  crafting:
    enabled: false
    fail-chance-percent: 0
    tool-durability-min-percent: 70
    tool-durability-max-percent: 95
  furnace:
    enabled: false
    jam-chance-percent: 0
    jam-cook-time-percent: 300
    burnt-food-chance-percent: 0
    fuel-burn-time-percent: 75
  storage:
    prevent-double-chests: false
  sleep:
    prevent-night-skip: false
    require-full-sleep-respawn: false
    full-sleep-ticks: 100
  fishing:
    enabled: false
    wait-time-percent: 150
  fall:
    enabled: false
    minimum-fall-distance-blocks: 3
    minimum-fall-damage: 1
  health:
    enabled: false
    threshold-health: 6
    effect-duration-ticks: 100
    amplifier: 0
  biomes:
    enabled: false
    effect-duration-ticks: 100
    amplifier: 0
  block-retaliation:
    enabled: false
    chance-percent: 0
    cooldown-ticks: 20
```

Target key syntax is exact:

- Block break and block place targets use `material:minecraft:<key>`.
- Craft result targets use `result:minecraft:<key>`.
- Craft recipe targets use `recipe:minecraft:<key>`.

For example, use `material:minecraft:diamond_ore`, `result:minecraft:oak_planks`, or `recipe:minecraft:oak_planks`.

## Gate Resolution

DouM resolves each attempted operation in this order:

1. A disabled operation allows the action.
2. A player with the operation bypass permission or `doum.deathnum.bypass.*` is allowed.
3. A target-specific override is used when the current target key exists in `targets`.
4. Otherwise the operation's `default-required-deaths` value is used.
5. The action is allowed when `playerDeaths >= requiredDeaths`; otherwise the event is cancelled and the operation's denial message is sent in the player's language.

Crafting checks recipe targets before result targets, so `recipe:minecraft:<key>` beats `result:minecraft:<key>` when both are present.

Supported denial placeholders are `{player}`, `{operation}`, `{target}`, `{required}`, and `{actual}`. `{target}` renders the item or block's name (not its id), and `{operation}` renders a localized action name. Denial messages and a custom `deny-message` may use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) colour tags such as `<red>` and `<gradient:...>`.

## Hardship Rules

All `hardship-rules` entries are disabled by default and run independently from death-count gates when enabled:

- `crafting` can cancel crafting by chance and randomize crafted damageable item durability within the configured remaining range.
- `furnace` can lengthen cook time, shorten fuel burn time, and consume edible smelt results as burnt food.
- `storage.prevent-double-chests` blocks same-type double chest formation and opening existing double chests.
- `sleep.prevent-night-skip` can block bed-based night skipping; `sleep.require-full-sleep-respawn` requires reaching full sleep before the bed respawn point is kept.
- `fishing.wait-time-percent` lengthens fishing hook wait windows.
- `fall` raises the minimum fall damage once the configured fall distance is reached.
- `health` applies slowness and blindness when health is at or below the configured threshold.
- `biomes` applies poison in swamps and weakness in highland/mountain biomes as hypoxia.
- `block-retaliation` can spawn a baby zombie wearing the broken block, with a per-player cooldown.

## Languages

DouM picks each player's language from their Minecraft client locale and falls back to English for any unsupported locale. English and Simplified Chinese (`zh`) ship inside the plugin jar as `messages/en.properties` and `messages/zh.properties`.

Localization covers both `/doum` command output and the default denial messages. Item and block names inside messages use Minecraft's own translations, so each player sees them in their own client language (for example "Stone" or "石头") regardless of the surrounding message language. To force specific wording for one operation regardless of language, set that operation's `deny-message` in `config.yml`; leaving it blank uses the player's language.

## Commands

- `/doum reload` reloads configuration. Permission: `doum.admin.reload`.
- `/doum deaths <online-player>` shows the online player's stored death count. Permission: `doum.deathnum.admin.view`.
- `/doum setdeaths <online-player> <count>` sets the online player's death count. Permission: `doum.deathnum.admin.set`.

## Permissions

- `doum.admin.reload`
- `doum.deathnum.admin.view`
- `doum.deathnum.admin.set`
- `doum.deathnum.bypass.*`
- `doum.deathnum.bypass.block-break`
- `doum.deathnum.bypass.block-place`
- `doum.deathnum.bypass.craft-item`

## Data

Death counts persist by UUID in `plugins/DouM/data.yml`. Stored player names are metadata only, and player names are metadata only for display and troubleshooting; changing a player's name does not create a new death record.

## Limitations

No economy, database, web, GUI, scoreboard, PlaceholderAPI, or update checker is included. DouM does not include metrics, broad version compatibility shims, food spoilage, carrying weight, hidden durability UI, bottled-water fall mitigation, or complex pickup difficulty changes. There is No broad multi-version promise.

## QA

The release checks shipped in the repository use a clean Gradle build:

```bash
GRADLE_USER_HOME=.gradle-user-home ./gradlew --no-daemon clean test build
```

The Folia surface QA scripts are no longer tracked by the repository. `scripts/qa/` is ignored by `.gitignore` and kept only as local maintainer tooling. Maintainers who have local scripts can run their own Folia surface QA after building the jar to verify:

- The plugin loads on a Folia server.
- Block break gates respect death counts.
- Block place gates respect death counts.
- Crafting gates respect death counts.
- Player deaths increment persisted death counts.
- Death counts are restored after a server restart.
- Local QA artifacts are cleaned up after the run.

QA evidence logs are local `.omo/evidence/` artifacts and are not published with the repository.
