# Crystal Carpet Addition NeoForge · 1.11.1-neoforge

Crystal Carpet Addition 的 **NeoForge 移植**，13 条规则全部落地。

## 版本

| 项 | 版本 |
|---|---|
| Minecraft | **1.21.1**（仅此一个） |
| NeoForge | **21.1.0 ~ 21.1.x** |
| Carpet: NeoForged | **>= 1.0.8**（必需，且需先于本 mod 加载） |

## 安装

把 **Carpet: NeoForged** 与 **本 mod** 两个 jar 一起放进 `mods\`，启动。

日志里应出现：

```
[CCA] version=1.11.1-neoforge CCA-category rules=13
[CCA] registered 13 rules
```

## 13 条规则

用 `/cca` 命令（不是 `/carpet`）：

```
/cca list CCA
/cca <规则名> true|false
```

`AnvilCanCrushItemEntities`、`ComparatorIgnoresStateUpdatesFromBelow`、`EnderDragonPartCanUseEndPortal`、
`EndermanCannotPickUpBlocksInNether`、`GatewayCannotLoadingChunks`、`ItemShadowing`、`MagicBox`、
`NoBatSpawn`、`ReIntroduceOldVersionRaid`、`ReIntroduceOldVersionWitchLootTable`、
`ShulkerBoxPowerOutputExpansion`、`ShulkerBoxPowerOutputExpansionColour`（枚举，如 `pink`）、
`StackableShulkerBoxesEnhancement`

## 这个移植比上游多了什么

**一条 NeoForge 专属补偿 mixin。** NeoForge 在 `HopperBlockEntity.ejectItems` 开头插入
`VanillaInventoryCodeHooks.insertHook`，命中 `IItemHandler` 时直接短路整条原版漏斗路径，
于是上游包的 `canMergeItems` 在 NeoForge 上**永不执行** —— `StackableShulkerBoxesEnhancement` 会静默失效。

本移植新增 `HopperBlockEntityEjectMixin`：当「规则开 + `shulkerBoxStackSize != 1` + 目标是原版容器 +
漏斗里有潜影盒」四条同时成立时返回 `false` 且不调 `original`，把控制权还给原版路径。

使用该规则需要三个前提：`/cca StackableShulkerBoxesEnhancement true`、`/carpet stackableShulkerBoxes true`、
目标是 `instanceof Container` 的原版容器。注意 `carpet stackableShulkerBoxes` 在服务端重启后可能回 `false`，需要重设。

## 没做到的部分（如实说明）

- **`StackableShulkerBoxesEnhancement` 不覆盖纯 `IItemHandler` 的 mod 容器** —— 回退分支要求目标是 `Container`，
  否则不动，避免破坏其他 mod 的漏斗互操作。
- **不移植 lithium 兼容层**（`HopperHelperMixin`）—— lithium 没有 1.21.1 NeoForge 版。
- **`RemoveVillagerTradeDistanceLimit` 在 1.21.1 上不注册** —— 上游声明 `require minecraft >= 1.21.4`，条件注册正常生效。
- **`ReIntroduceOldVersionRaid` 的"击杀巡逻队长得 bad_omen"缺端到端证据**。注入点、条件链、字节码都对
  （`Raider.die` 内偏移 69 的 `handler$…$onDeathMixin`），但构造不出能稳定复现的探针场景，只到字节码级。

## 打包说明

jar 内同时有 `META-INF/neoforge.mods.toml`（NeoForge 读取）和 `META-INF/mods.toml`（老式路径）。
第二份只为让 PCL2 等启动器的老解析器也能显示名称/图标/版本 —— NeoForge 的 `ModFileParser` 只查前者，
不会重复加载，已实测无 `Duplicate mod`。

## 文件

| 文件 | 说明 |
|---|---|
| `…-neoforge.jar` | 放进 `mods\` |
| `…-neoforge-sources.jar` | 源码，仅供阅读 |
| `sha1.txt` | 校验 |

## 许可

**GPL-3.0-only**，与上游一致。移植基于 [Crystal0404/CrystalCarpetAddition](https://github.com/Crystal0404/CrystalCarpetAddition) `1.11.1`。
