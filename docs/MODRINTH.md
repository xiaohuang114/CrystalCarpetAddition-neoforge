# Crystal Carpet Addition (NeoForge)

**Crystal Carpet Addition 的 NeoForge 移植** —— 一个 [Carpet](https://modrinth.com/mod/carpet) 扩展，提供 13 条原版行为微调规则，给技术向玩家和服务器管理员用。

> 原版是 Fabric 的 [Crystal Carpet Addition](https://modrinth.com/mod/crystal-carpet-addition)。这个移植逐文件对照上游，类名、包名、Mixin 注入点全部保持一致。

## 需要的依赖

| 必需 | 版本 |
|---|---|
| **Carpet: NeoForged** | `>= 1.0.8`（`modId = carpet`），**必须先于本 mod 加载** |
| **NeoForge** | `21.1.0` ~ `21.1.x` 任意 |
| **Minecraft** | `1.21.1` |

两个 jar 一起放进 `mods\`。

## 13 条规则

用 Carpet 的 `/cca` 命令（不是 `/carpet`）：

```
/cca list CCA                  # 看全部
/cca <规则名> true|false       # 开关
```

| 规则 | 作用 |
|---|---|
| `AnvilCanCrushItemEntities` | 铁砧落地能砸碎物品实体 |
| `ComparatorIgnoresStateUpdatesFromBelow` | 比较器忽略来自下方的状态更新 |
| `EnderDragonPartCanUseEndPortal` | 末影龙部件可穿末地传送门 |
| `EndermanCannotPickUpBlocksInNether` | 下界末影人不再搬方块 |
| `GatewayCannotLoadingChunks` | 折跃门不加载区块 |
| `ItemShadowing` | 物品影子（同一槽位双份物品） |
| `MagicBox` | 魔盒（容器内容伪装） |
| `NoBatSpawn` | 不生成蝙蝠 |
| `ReIntroduceOldVersionRaid` | 恢复旧版袭击触发（1.20.1 行为） |
| `ReIntroduceOldVersionWitchLootTable` | 恢复旧版女巫掉落表 |
| `ShulkerBoxPowerOutputExpansion` | 潜影盒比较器信号扩展 |
| `ShulkerBoxPowerOutputExpansionColour` | 上一条的按颜色输出（枚举，如 `pink`） |
| `StackableShulkerBoxesEnhancement` | 漏斗不再合并潜影盒（配合 Carpet 的可堆叠潜影盒） |

## 几条规则的用法提示

**`StackableShulkerBoxesEnhancement`** —— 需要三个前提同时成立才会生效：

1. `/cca StackableShulkerBoxesEnhancement true`
2. `/carpet stackableShulkerBoxes true`（让 `shulkerBoxStackSize > 1`；**服务端重启后可能回 `false`，要重设**）
3. 漏斗指向的目标是原版容器（`instanceof Container`）；纯 `IItemHandler` 的 mod 容器不在覆盖范围

**`ShulkerBoxPowerOutputExpansionColour`** 是枚举规则，`true`/`false` 会被拒绝：

```
/cca ShulkerBoxPowerOutputExpansionColour pink
```

可用值：`none white light_gray gray black brown red orange yellow lime green cyan light_blue blue purple magenta pink`

**`ReIntroduceOldVersionRaid`** —— 规则开时，村庄内玩家的 `bad_omen` 会被直接消耗并建起袭击，不再经过 `raid_omen` 这一层。

## 可选启动参数

| 参数 | 默认 | 说明 |
|---|---|---|
| `-Dcca.disable.EasterEggs=false` | **彩蛋默认关闭** | 传 `false` 才开启；开启后 3 月 25 日有 1/3 概率把 splash 换成生日文案 |
| `-Dcca.enable.debug` | 关 | 调试日志 |
| `-Dcca.enable.MagicSettings` | 关 | 上游为 1.20.5–1.20.6 的 `MagicBox` 准备的开关 |

## 已知差异

- **`StackableShulkerBoxesEnhancement` 需要额外补偿代码**。NeoForge 的 `VanillaInventoryCodeHooks.insertHook` 会短路原版漏斗路径，上游那套 `canMergeItems` 注入在这边根本不执行。本移植补了一个 mixin 把控制权还回去。副作用：不覆盖纯 `IItemHandler` 的 mod 容器。
- **不移植 lithium 兼容层**：lithium 没有 1.21.1 NeoForge 版。
- **`RemoveVillagerTradeDistanceLimit` 在 1.21.1 上不注册**：上游要求 `minecraft >= 1.21.4`。
- **"击杀巡逻队长获得 bad_omen"（`ReIntroduceOldVersionRaid` 的一部分）只有字节码级证据**，端到端探针未通过。

完整 31 条差异记录在仓库的 `analysis/DEVIATIONS.md`。

## 只在 1.21.1 上可用

jar 内声明 `minecraft` 依赖为 `[1.21.1,1.21.2)`。这不是保守设置——`Raid`、`Raider`、`BadOmenMobEffect`、`SplashManager` 这些注入目标在 1.21.2 都改过，跨版本需要重新移植。

## 源码

<https://github.com/xiaohuang114/CrystalCarpetAddition-neoforge> —— 仓库内含完整移植过程文档（`handoff/`）、逐条差异（`analysis/DEVIATIONS.md`）、规则测试手册（`dist/RULE-TEST.md`）。

## 许可

**GPL-3.0-only**，与上游一致。源码：<https://github.com/xiaohuang114/CrystalCarpetAddition-neoforge>
