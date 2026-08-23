# AnvilCraft FE Grid

让 AnvilCraft 电网直接为所有 Forge Energy (FE) 机器供电。
Power every Forge Energy (FE) machine straight from the AnvilCraft power grid.

**Minecraft 1.21.1** · **NeoForge** · **MIT** · 作者 / Author: renzaifei

[中文](#中文) · [English](#english)

---

## 中文

### 简介

AnvilCraft FE Grid 是 AnvilCraft 的附属模组。它把 AnvilCraft 的电网和 Forge Energy 能量系统连通：电网范围内任何能接收 FE 的机器都会被自动识别并供电，不需要额外的方块、导线或转换器，把机器放进电网范围就能用。

FE 机器的负载会计入电网自身的用电统计，所以电网读数、过载判定和原版元件保持一致的行为。

### 配置

配置文件位于 `config/anvilcraftfegrid-common.toml`。

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `transducers` | `1000` | 1 kW 电网功率折算多少 FE（范围 1 ~ 10000） |
| `loss` | `0.05` | 电网电力转 FE 的损耗，`0.0` 为无损，`0.1` 为损耗 10% |
| `max_load_per_machine` | `1024` | 单台 FE 机器每刻最多可抽取的电网负载（kW）（范围 1 ~ 100000） |
| `feed_when_overloaded` | `false` | 电网过载时是否仍尝试给 FE 机器供电 |
| `blocked_blocks` | 见下 | 永不视为 FE 机器的方块，例如线缆和导管 |

实际每 kW 输出的 FE 为 `transducers × (1 - loss)`。例如默认值下 1 kW ≈ 950 FE。

#### `blocked_blocks`

线缆、导管这类方块本身也能接收 FE，如果被当成用电器，电网会试图把整段管网的缓冲一次性灌满。这个列表用来排除它们，默认已包含通用机械的四档通用线缆：

```toml
blocked_blocks = ["mekanism:basic_universal_cable", "mekanism:advanced_universal_cable", "mekanism:elite_universal_cable", "mekanism:ultimate_universal_cable"]
```

支持两种写法：

- 精确方块 id，如 `mekanism:basic_universal_cable`；不写命名空间时按 `minecraft:` 处理
- 命名空间通配，如 `mekanism:*` 表示排除该模组的所有方块

储能方块（如能量方块）没有默认排除。有了 `max_load_per_machine` 限流后，它们每刻最多按该值充电，作为储能被电网充满属于正常行为；若不希望如此，把对应 id 加进这个列表即可。

该项目前只能通过编辑 toml 文件修改，游戏内配置界面会显示为只读文本。

### 安装

需要：

- Minecraft 1.21.1
- NeoForge 21.1.219 或更高
- [AnvilCraft](https://modrinth.com/mod/anvilcraft) 1.6.0 或更高（含其依赖 AnvilLib）

把构建出的 jar 放进 `mods` 文件夹即可，客户端和服务端都需要安装。

### 从源码构建

```bash
./gradlew build          # 产物在 build/libs/
./gradlew runClient      # 启动开发客户端
./gradlew runServer      # 启动开发服务端
```

需要 JDK 21。依赖从 Maven Central、Cjsah Maven（AnvilLib / AnvilCraft）和 Ithundxr Maven（Registrate fork）拉取，已在 `gradle/scripts/repositories.gradle` 中配置。

### 项目结构

```
src/main/java/com/renzaifei/anvilcraftfegrid/
├── AnvilCraftFEGrid.java              模组入口，注册配置
├── config/AnvilCraftFEGridConfig.java 配置定义
├── mixin/PowerGridMixin.java          注入 AnvilCraft 电网
└── power/
    ├── FEGridBridge.java              扫描、需求统计与分配的核心逻辑
    ├── FEGridState.java               单个电网的桥接状态与缓存
    ├── FEGridHolder.java              由 Mixin 实现的状态访问接口
    └── FETarget.java                  一个 FE 接收目标（位置 + 访问面）
```

### 许可证

MIT，详见 [LICENSE](LICENSE)。

---

## English

### Overview

AnvilCraft FE Grid is an AnvilCraft addon that bridges the AnvilCraft power grid and the Forge Energy system. Any machine inside the grid's range that can accept FE is detected and powered automatically — no extra blocks, cables, or converters. Place the machine in range and it works.

FE loads are added to the grid's own consumption figures, so grid readouts and overload behaviour stay consistent with native components.

### Configuration

Config lives at `config/anvilcraftfegrid-common.toml`.

| Key | Default | Description |
| --- | --- | --- |
| `transducers` | `1000` | FE per 1 kW of grid power (range 1 ~ 10000) |
| `loss` | `0.05` | Conversion loss from grid power to FE; `0.0` is lossless, `0.1` loses 10% |
| `max_load_per_machine` | `1024` | Maximum grid load in kW a single FE machine may draw per tick (range 1 ~ 100000) |
| `feed_when_overloaded` | `false` | Keep feeding FE machines while the grid is overloaded |
| `blocked_blocks` | see below | Blocks never treated as FE machines, e.g. cables and conduits |

Effective FE per kW is `transducers × (1 - loss)` — about 950 FE per kW with the defaults.

#### `blocked_blocks`

Cables and conduits accept FE themselves, so treating them as consumers makes the grid try to fill an entire pipe network's buffer at once. This list excludes them. Mekanism's four universal cable tiers are excluded by default:

```toml
blocked_blocks = ["mekanism:basic_universal_cable", "mekanism:advanced_universal_cable", "mekanism:elite_universal_cable", "mekanism:ultimate_universal_cable"]
```

Two forms are accepted:

- An exact block id such as `mekanism:basic_universal_cable`; entries without a namespace are treated as `minecraft:`
- A namespace wildcard such as `mekanism:*`, excluding every block from that mod

Energy storage blocks (energy cubes and the like) are not excluded by default. With `max_load_per_machine` capping the draw, they charge at most that much per tick, and letting the grid fill them is reasonable behaviour — add their ids to this list if you'd rather it didn't.

This option can currently only be changed by editing the toml file; the in-game config screen shows it as read-only text.

### Installation

Requires:

- Minecraft 1.21.1
- NeoForge 21.1.219 or newer
- [AnvilCraft](https://modrinth.com/mod/anvilcraft) 1.6.0 or newer (which brings AnvilLib)

Drop the jar into your `mods` folder. Needed on both client and server.

### Building from source

```bash
./gradlew build          # output in build/libs/
./gradlew runClient      # dev client
./gradlew runServer      # dev server
```

JDK 21 required. Dependencies come from Maven Central, Cjsah Maven (AnvilLib / AnvilCraft), and Ithundxr Maven (Registrate fork), all configured in `gradle/scripts/repositories.gradle`.

### Project layout

```
src/main/java/com/renzaifei/anvilcraftfegrid/
├── AnvilCraftFEGrid.java              mod entrypoint, registers config
├── config/AnvilCraftFEGridConfig.java config definition
├── mixin/PowerGridMixin.java          injects into the AnvilCraft grid
└── power/
    ├── FEGridBridge.java              scanning, demand accounting, distribution
    ├── FEGridState.java               per-grid bridge state and caches
    ├── FEGridHolder.java              state accessor implemented by the Mixin
    └── FETarget.java                  one FE receiver (position + access side)
```

### License

MIT — see [LICENSE](LICENSE).

