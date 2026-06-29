# DouM

[![Build](https://github.com/Redstonexs/DouM/actions/workflows/build.yml/badge.svg)](https://github.com/Redstonexs/DouM/actions/workflows/build.yml)

DouM is a Paper plugin for Folia-compatible servers that counts each player's deaths and blocks configured actions until that player has reached the required death count. The first supported gates are block breaking, block placing, and item crafting.

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

- **Every push, pull request, or manual run** compiles the plugin, runs the unit tests, and uploads the built jar as a workflow artifact named `deathgates-plugin`. Download it from the run page under **Summary → Artifacts**.
- **Pushing a `v*` tag** (for example `v1.0.0`) additionally publishes a GitHub Release with the jar attached and auto-generated notes. The release jar is stamped with the tag version (for example `deathgates-1.0.0.jar`) via the `-PreleaseVersion` Gradle property.

To cut a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Configuration

Each operation has independent settings:

```yaml
operations:
  block-break:
    enabled: true
    default-required-deaths: 0
    bypass-permission: deathgates.bypass.block-break
    # Blank deny-message uses the player's client language (English/Chinese). Set a string to override.
    deny-message: ""
    targets:
      "material:minecraft:diamond_ore": 2

  block-place:
    enabled: true
    default-required-deaths: 1
    bypass-permission: deathgates.bypass.block-place
    deny-message: ""
    targets:
      "material:minecraft:obsidian": 3

  craft-item:
    enabled: true
    default-required-deaths: 0
    bypass-permission: deathgates.bypass.craft-item
    deny-message: ""
    targets:
      "recipe:minecraft:oak_planks": 1
      "result:minecraft:oak_planks": 2
```

Target key syntax is exact:

- Block break and block place targets use `material:minecraft:<key>`.
- Craft result targets use `result:minecraft:<key>`.
- Craft recipe targets use `recipe:minecraft:<key>`.

For example, use `material:minecraft:diamond_ore`, `result:minecraft:oak_planks`, or `recipe:minecraft:oak_planks`.

## Gate Resolution

DouM resolves each attempted operation in this order:

1. A disabled operation allows the action.
2. A player with the operation bypass permission or `deathgates.bypass.*` is allowed.
3. A target-specific override is used when the current target key exists in `targets`.
4. Otherwise the operation's `default-required-deaths` value is used.
5. The action is allowed when `playerDeaths >= requiredDeaths`; otherwise the event is cancelled and the operation's denial message is sent in the player's language.

Crafting checks recipe targets before result targets, so `recipe:minecraft:<key>` beats `result:minecraft:<key>` when both are present.

Supported denial placeholders are `{player}`, `{operation}`, `{target}`, `{required}`, and `{actual}`.

## Languages

DouM picks each player's language from their Minecraft client locale and falls back to English for any unsupported locale. English and Simplified Chinese (`zh`) ship inside the plugin jar as `messages/en.properties` and `messages/zh.properties`.

Localization covers both `/doum` command output and the default denial messages. To force specific wording for one operation regardless of language, set that operation's `deny-message` in `config.yml`; leaving it blank uses the player's language.

## Commands

- `/doum reload` reloads configuration. Permission: `deathgates.admin.reload`.
- `/doum deaths <online-player>` shows the online player's stored death count. Permission: `deathgates.admin.view`.
- `/doum setdeaths <online-player> <count>` sets the online player's death count. Permission: `deathgates.admin.set`.

## Permissions

- `deathgates.admin.reload`
- `deathgates.admin.view`
- `deathgates.admin.set`
- `deathgates.bypass.*`
- `deathgates.bypass.block-break`
- `deathgates.bypass.block-place`
- `deathgates.bypass.craft-item`

## Data

Death counts persist by UUID in `plugins/DouM/data.yml`. Stored player names are metadata only, and player names are metadata only for display and troubleshooting; changing a player's name does not create a new death record.

## Limitations

No economy, database, web, GUI, scoreboard, PlaceholderAPI, or update checker is included. DouM does not include metrics, broad version compatibility shims, or extra operation types beyond `block-break`, `block-place`, and `craft-item`. There is No broad multi-version promise.

## QA

The release checks use a clean Gradle build and a real Folia surface QA runner:

```bash
GRADLE_USER_HOME=.gradle-user-home ./gradlew --no-daemon clean test build
python3 scripts/qa/run_folia_surface_qa.py --plugin-jar build/libs/deathgates-*.jar --server-dir .omo/qa/folia-server --evidence .omo/evidence/task-8-folia-death-gates.log
python3 scripts/qa/check_release_docs.py
```

Folia QA starts a live Folia server, installs the built plugin jar, connects Mineflayer clients, and verifies these markers in evidence logs:

- `PASS plugin-load`
- `PASS break-gate`
- `PASS place-gate`
- `PASS craft-gate`
- `PASS death-increment`
- `PASS persistence-restart`
- `PASS cleanup`

Prior surface QA receipts are in `.omo/evidence/task-7-folia-death-gates.log` and `.omo/evidence/task-7-verify-folia-death-gates.log`. Todo 8 release verification is captured in `.omo/evidence/task-8-folia-death-gates.log`.
