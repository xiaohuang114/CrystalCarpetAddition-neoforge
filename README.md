# Crystal Carpet Addition · NeoForge

把 [Crystal Carpet Addition](https://github.com/Crystal0404/CrystalCarpetAddition)（CCA）从 **Fabric 1.21.1** 移植到 **NeoForge 21.1.x / Minecraft 1.21.1** 的完整移植工程。

上游 CCA 是 [Carpet](https://github.com/gnembon/fabric-carpet) 的扩展，提供 13 条原版行为微调规则。本仓库不是重写，而是**逐阶段、逐文件对照移植**：类名、包名、Mixin 注入点全部与上游保持一致，可逐文件 diff 验证。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-DA8548)](https://neoforged.net)
[![License](https://img.shields.io/badge/License-GPL--3.0--only-blue)](LICENSE)

---

## 支持版本

| 项 | 范围 | 说明 |
|---|---|---|
| **Minecraft** | **只有 `1.21.1`** | jar 内声明 `[1.21.1,1.21.2)`。1.21.2+ 的 `Raid` / `Raider` / `SplashManager` 等注入目标都变了，跨版本必须重新移植 |
| **NeoForge** | **`21.1.0` 及以上任意 `21.1.x`** | 声明 `[21.1.0,)`；开发与验证于 `21.1.265` |
| **Carpet: NeoForged** | **`>= 1.0.8`**（jar 内 mod version `>= 1.4.147`） | `modId = carpet`，**必需**且必须先于本 mod 加载 |

## 安装

1. 用 NeoForge `21.1.x` 装好 1.21.1 实例。
2. 把 **Carpet: NeoForged** 的 jar 和本 mod 的 jar **同时**放进 `mods\`。
3. 启动。日志里应出现：

   ```
   [CCA] version=1.11.1-neoforge CCA-category rules=13
   [CCA] registered 13 rules
   ```

4. 进游戏后 `/carpet` 能列出 `Crystal Carpet Addition version: 1.11.1-neoforge`。

详细的安装与排查见 [`dist/README-安装说明.md`](dist/README-安装说明.md)。

## 13 条规则

全部走 Carpet 的 `/cca` 命令（**不是** `/carpet`）：

```
/cca list CCA
/cca <规则名> true|false
```

| # | 规则 | 作用 |
|---|---|---|
| 1 | `AnvilCanCrushItemEntities` | 铁砧落地能砸碎物品实体 |
| 2 | `ComparatorIgnoresStateUpdatesFromBelow` | 比较器忽略来自下方的状态更新 |
| 3 | `EnderDragonPartCanUseEndPortal` | 末影龙部件可穿末地传送门 |
| 4 | `EndermanCannotPickUpBlocksInNether` | 下界末影人不再搬方块 |
| 5 | `GatewayCannotLoadingChunks` | 折跃门不加载区块 |
| 6 | `ItemShadowing` | 物品影子（同一槽位双份物品） |
| 7 | `MagicBox` | 魔盒（容器内容伪装） |
| 8 | `NoBatSpawn` | 不生成蝙蝠 |
| 9 | `ReIntroduceOldVersionRaid` | 恢复旧版袭击触发逻辑（1.20.1 行为） |
| 10 | `ReIntroduceOldVersionWitchLootTable` | 恢复旧版女巫掉落表（单 pool） |
| 11 | `ShulkerBoxPowerOutputExpansion` | 潜影盒比较器信号扩展 |
| 12 | `ShulkerBoxPowerOutputExpansionColour` | 上一条的按颜色输出（枚举规则） |
| 13 | `StackableShulkerBoxesEnhancement` | 漏斗不再合并潜影盒（配合 Carpet 的可堆叠潜影盒） |

`ShulkerBoxPowerOutputExpansionColour` 是**枚举**规则，`true`/`false` 会被 validator 拒绝：

```
/cca ShulkerBoxPowerOutputExpansionColour pink
```

可选 `-D` 开关：

| 系统属性 | 默认 | 说明 |
|---|---|---|
| `cca.disable.EasterEggs` | **禁用**（传 `false` 才开启） | 开启后 3 月 25 日有 1/3 概率把 splash 换成生日彩蛋 |
| `cca.enable.debug` | 关闭 | 调试日志 |
| `cca.enable.MagicSettings` | 关闭 | `MagicBox` 在 1.20.5–1.20.6 上的依赖项 |

## 已知差异

移植不是无痛的，以下差异都已记录在 [`analysis/DEVIATIONS.md`](analysis/DEVIATIONS.md)（31 条）：

- **`StackableShulkerBoxesEnhancement` 需要额外补偿 mixin**。NeoForge 在 `HopperBlockEntity.ejectItems` 开头插入 `VanillaInventoryCodeHooks.insertHook`，命中 `IItemHandler` 时直接短路整条原版路径，上游包的 `canMergeItems` 在 NeoForge 上永不执行。本移植新增 `HopperBlockEntityEjectMixin` 在条件成立时返回 `false`，把控制权还给原版。
- **`HopperHelperMixin`（lithium 兼容）不移植**：lithium 没有 1.21.1 NeoForge 版。
- **`RemoveVillagerTradeDistanceLimit` 在 1.21.1 不注册**：上游声明 `require minecraft >= 1.21.4`，条件注册正常生效。
- **`RaiderEntityMixin` 的"击杀队长得 bad_omen"只有字节码级证据**，端到端探针未通过（`D-S9-4`）。

13 条规则各自的手测命令与证据等级见 [`dist/RULE-TEST.md`](dist/RULE-TEST.md)。

## 构建

```powershell
# 1. 放依赖（见 cca-neoforge/libs/README.md）
#    cca-neoforge/libs/neoforge-carpet-1.21.1-1.0.8+v251027.jar

cd cca-neoforge
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'   # JDK 21
.\gradlew.bat build --offline
```

产物在 `cca-neoforge/build/libs/`。

> `--offline` 是刻意的：在线首构建约 8 分钟，离线 3–8 秒。首次构建仍需联网拉依赖。

## 项目结构

```
cca-neoforge/src/main/java/crystal0404/crystalcarpetaddition/
├── CrystalCarpetAdditionMod.java      # @Mod 入口（构造器注入 ModContainer）
├── CCAExtension.java                  # CarpetExtension 实现
├── CCASettings.java                   # 13 条规则字段
├── mixins/                            # 22 条 server mixin + 1 条 client mixin
│   ├── carpet/SettingsManagerMixin
│   └── rule/<规则名>/...
└── utils/                             # 条件注入体系、LootTableUtils、注解

analysis/     # 移植矩阵、差异记录、静态检查
handoff/      # s0–s9 逐阶段交接文档
prompts/      # 各阶段任务书（本项目按阶段任务书推进）
dist/         # 发布产物
_tools/       # 探针与构建脚本（serve.ps1 / runclient.ps1 / rcon_probe.py）
```

移植的关键约束：所有 MC / Carpet 的类名与注入目标只能取自 `_mappings/refmap_mojmap.md`
与 `javap` 实测，禁止凭记忆写——否则形式是"编译通过、运行时报 `Mixin apply failed`"。

## 许可证

**GPL-3.0-only**，与上游一致。见 [LICENSE](LICENSE)。

## 致谢

- [Crystal0404](https://github.com/Crystal0404) —— 上游 CCA 与 Carpet: NeoForged
- [gnembon](https://github.com/gnembon) —— Carpet
- [LlamaLad7](https://github.com/LlamaLad7) —— MixinExtras
