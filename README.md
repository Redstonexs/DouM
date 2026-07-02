# DouM

[![Build](https://github.com/Redstonexs/DouM/actions/workflows/build.yml/badge.svg)](https://github.com/Redstonexs/DouM/actions/workflows/build.yml)

[English](README.en.md)

## 简介

DouM 是一个适用于兼容 Folia 的 Paper 服务器插件，用于统计每位玩家的死亡次数，并在玩家达到所需死亡次数之前阻止已配置的操作。首批支持的门槛操作包括破坏方块、放置方块和合成物品。插件还提供默认关闭的生存难度规则，可按需开启合成失败、炉子故障、禁止大箱子、睡眠/钓鱼/摔落/低血量/群系惩罚和方块反击等机制。

插件描述文件包含 `folia-supported: true`。DouM 直接使用 Paper/Folia 的事件接口，不使用 Bukkit 调度器 API 访问世界或实体。当前构建目标为 Java 25，以及本项目使用的 Paper/Folia API；不承诺广泛的多版本兼容。

### 安装

1. 构建插件：

   ```bash
   GRADLE_USER_HOME=.gradle-user-home ./gradlew --no-daemon clean test build
   ```

2. 将构建产物 `build/libs/deathgates-0.1.0-SNAPSHOT.jar` 复制到服务器的 `plugins/` 目录。
3. 启动一次服务器，使其生成 `plugins/DouM/config.yml`。
4. 编辑 `plugins/DouM/config.yml` 配置门槛规则。
5. 配置变更后，由服务器 OP 或拥有权限的管理员账户执行 `/doum reload`。

### 持续集成与下载

GitHub Actions 会自动构建并测试插件（`.github/workflows/build.yml`）：

- **每次 push、pull request 或手动运行** 都会编译插件、运行单元测试，并将构建出的 jar 作为名为 `deathgates-plugin` 的工作流产物上传。可在运行页面的 **Summary -> Artifacts** 下下载。
- **推送 `v*` 标签**（例如 `v1.0.0`）还会额外发布一个 GitHub Release，附带 jar 文件和自动生成的发布说明。发布 jar 会通过 Gradle 的 `-PreleaseVersion` 属性写入标签版本号（例如 `deathgates-1.0.0.jar`）。

发布版本：

```bash
git tag v1.0.0
git push origin v1.0.0
```

### 配置

顶层的 `message-prefix` 用于设置每条 DouM 消息前显示的标签样式；每种操作都可以独立配置：

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

目标键语法是精确匹配：

- 破坏方块和放置方块目标使用 `material:minecraft:<key>`。
- 合成结果目标使用 `result:minecraft:<key>`。
- 合成配方目标使用 `recipe:minecraft:<key>`。

例如可使用 `material:minecraft:diamond_ore`、`result:minecraft:oak_planks` 或 `recipe:minecraft:oak_planks`。

### 门槛判定

DouM 按以下顺序判定每次尝试的操作：

1. 如果该操作已禁用，则允许执行。
2. 如果玩家拥有该操作的绕过权限，或拥有 `doum.deathnum.bypass.*`，则允许执行。
3. 如果当前目标键存在于 `targets` 中，则使用目标专属覆盖值。
4. 否则使用该操作的 `default-required-deaths` 值。
5. 当 `playerDeaths >= requiredDeaths` 时允许执行；否则取消事件，并用玩家的语言发送该操作的拒绝消息。

合成检查会先匹配配方目标，再匹配结果目标。因此当两者同时存在时，`recipe:minecraft:<key>` 优先于 `result:minecraft:<key>`。

支持的拒绝消息占位符包括 `{player}`、`{operation}`、`{target}`、`{required}` 和 `{actual}`。`{target}` 会显示物品或方块名称（不是 ID），`{operation}` 会显示本地化后的操作名称。拒绝消息和自定义 `deny-message` 可以使用 [MiniMessage](https://docs.advntr.dev/minimessage/format.html) 颜色标签，例如 `<red>` 和 `<gradient:...>`。

### 生存难度规则

`hardship-rules` 下的所有规则默认关闭，开启后不受死亡次数门槛影响：

- `crafting` 可按概率取消合成，并让合成出的可损坏物品保留配置范围内的随机耐久。
- `furnace` 可延长烧炼时间、缩短燃料燃烧时间，并按概率让食物烧糊为无产出。
- `storage.prevent-double-chests` 会阻止同类箱子组成大箱子，并阻止打开已有大箱子。
- `sleep.prevent-night-skip` 可禁止通过睡觉跳过夜晚；`sleep.require-full-sleep-respawn` 会要求达到完整睡眠后才保留床重生点。
- `fishing.wait-time-percent` 会按百分比延长鱼钩等待时间。
- `fall` 会提高达到配置摔落距离时的最低摔落伤害。
- `health` 会在生命值低于阈值时施加迟缓和失明。
- `biomes` 会在沼泽施加中毒，在高原/山地类群系施加虚弱作为缺氧表现。
- `block-retaliation` 会在挖方块时按概率生成戴着该方块的小僵尸，并按玩家设置冷却。

### 语言

DouM 会根据玩家的 Minecraft 客户端语言环境选择消息语言，并在遇到不支持的语言环境时回退到英语。英语和简体中文（`zh`）随插件 jar 内置，分别位于 `messages/en.properties` 和 `messages/zh.properties`。

本地化覆盖 `/doum` 命令输出和默认拒绝消息。消息中的物品和方块名称使用 Minecraft 自带翻译，因此每位玩家都会看到自己客户端语言中的名称（例如 “Stone” 或“石头”），不受消息正文语言影响。若要让某个操作始终使用指定措辞，可在 `config.yml` 中设置该操作的 `deny-message`；留空则使用玩家语言。

### 命令

- `/doum reload` 重新加载配置。权限：`doum.admin.reload`。
- `/doum deaths <online-player>` 查看在线玩家已记录的死亡次数。权限：`doum.deathnum.admin.view`。
- `/doum setdeaths <online-player> <count>` 设置在线玩家的死亡次数。权限：`doum.deathnum.admin.set`。

### 权限

- `doum.admin.reload`
- `doum.deathnum.admin.view`
- `doum.deathnum.admin.set`
- `doum.deathnum.bypass.*`
- `doum.deathnum.bypass.block-break`
- `doum.deathnum.bypass.block-place`
- `doum.deathnum.bypass.craft-item`

### 数据

死亡次数会按 UUID 持久化保存到 `plugins/DouM/data.yml`。保存的玩家名称只是元数据，仅用于显示和排查问题；玩家改名不会创建新的死亡记录。

### 限制

DouM 不包含经济、数据库、网页、GUI、计分板、PlaceholderAPI 或更新检查器功能。DouM 不包含指标统计、广泛版本兼容适配，也不包含物品腐败、负重、隐藏耐久度、瓶装水减免摔伤或复杂掉落物拾取难度等客户端/持久化/物理改造。不承诺广泛的多版本兼容。
