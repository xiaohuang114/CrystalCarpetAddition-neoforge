# Crystal Carpet Addition · NeoForge 安装说明

**版本**：`1.11.1-neoforge`（对应上游 CCA `1.11.1` / Minecraft `1.21.1`）
**目标平台**：NeoForge `21.1.x` / Minecraft `1.21.1`
**许可证**：**GPL-3.0-only**（与上游一致）
**上游仓库**：<https://github.com/crystal0404/CrystalCarpetAddition>
**本移植仓库**：<https://github.com/xiaohuang114/CrystalCarpetAddition-neoforge>

---

## 1. 依赖（两个 jar 都要装）

| 必需 | 版本 | 说明 |
|---|---|---|
| **NeoForge** | **`21.1.0` 及以上任意 21.1.x**（开发与验证于 `21.1.265`） | 21.1 线对应 Minecraft 1.21.1；jar 内声明 `versionRange = "[21.1.0,)"` |
| **Carpet: NeoForged** | `>= 1.0.8`（jar 内 mod version `>= 1.4.147`） | jar 内 `modId = carpet`，版本形如 `1.4.147+v1.0.8-251027` |
| **Crystal Carpet Addition** | `1.11.1-neoforge` | 本 mod |

**Minecraft 侧是唯一的**：jar 内声明 `minecraft` 依赖为 `[1.21.1,1.21.2)`，**只在 1.21.1 上可用** ——
1.21.2+ 的类结构变了（`Raid` / `Raider` / `SplashManager` 等注入目标都不再相同），1.21.0 也不行；
`1.21.1` 的其他版本号（如 `1.21.1-pre*`）不保证。

把 `Carpet: NeoForged` 的 jar 与本 mod 的 jar **同时**放进 `.minecraft\mods\`。本 mod 的 `neoforge.mods.toml` 已声明
`[[dependencies.cca]] modId="carpet" ordering="BEFORE"`，所以 Carpet 必须存在且先加载。

> 不需要、也不支持 lithium / pca / conditional-mixin / carpet-fixes / carpet-shadow / antishadowpatch。
> 这些 modId 只出现在条件判断字符串里，装了也不会被依赖。

## 2. 安装步骤

1. 用 NeoForge `21.1.x` 的安装器建好 1.21.1 实例。
2. 首次启动一次、退出（生成 `config\` 与 `mods\`）。
3. 把 `Carpet: NeoForged` jar 与本 mod jar 放入 `mods\`。
4. 再次启动。日志里应出现：

   ```
   [CCA] version=1.11.1-neoforge CCA-category rules=13
   [CCA] registered 13 rules
   ```

5. 进游戏后 `/carpet` 应能列出 `Crystal Carpet Addition version: 1.11.1-neoforge`。

## 3. 规则用法

本 mod 注册 **13 条**规则，全部走 Carpet 的 `/cca` 命令（不是 `/carpet`）：

```
/cca list CCA                     # 列出全部规则
/cca <规则名> true                # 开
/cca <规则名> false               # 关
/cca <规则名>                     # 查当前值
```

13 条规则名（区分大小写）：

| 规则名 | 类型 |
|---|---|
| `AnvilCanCrushItemEntities` | boolean |
| `ComparatorIgnoresStateUpdatesFromBelow` | boolean |
| `EnderDragonPartCanUseEndPortal` | boolean |
| `EndermanCannotPickUpBlocksInNether` | boolean |
| `GatewayCannotLoadingChunks` | boolean |
| `ItemShadowing` | boolean |
| `MagicBox` | boolean |
| `NoBatSpawn` | boolean |
| `ReIntroduceOldVersionRaid` | boolean（`Raid using mc1.20.1`） |
| `ReIntroduceOldVersionWitchLootTable` | boolean |
| `ShulkerBoxPowerOutputExpansion` | boolean |
| `ShulkerBoxPowerOutputExpansionColour` | **枚举**：`none` / `white` / `light_gray` / `gray` / `black` / `brown` / `red` / `orange` / `yellow` / `lime` / `green` / `cyan` / `light_blue` / `blue` / `purple` / `magenta` / `pink` |
| `StackableShulkerBoxesEnhancement` | boolean（见 §5 已知差异） |

`RemoveVillagerTradeDistanceLimit` **不在**上述列表里：它要求 `minecraft >= 1.21.4`，在 1.21.1 上条件注册不成立，
`/cca RemoveVillagerTradeDistanceLimit true` 会回 `Unknown rule`。这是**预期行为**，不是安装失败。

## 4. `-D` 系统属性开关

| 属性 | 默认 | 含义 |
|---|---|---|
| `cca.disable.EasterEggs` | **启用禁用**（即彩蛋默认关闭） | 传 `-Dcca.disable.EasterEggs=false` 才**开启**彩蛋（主菜单 3 月 25 日的生日 splash） |
| `cca.enable.debug` | 关闭 | 调试输出 |
| `cca.enable.MagicSettings` | 关闭 | `MagicBox` 在 1.20.5~1.20.6 上依赖它；1.21.1 不需要 |

用法（JVM 参数，加在启动器 JVM args）：

```
-Dcca.disable.EasterEggs=false
```

生效时日志会打一行 `[CCA] -Dcca.disable.EasterEggs=false`。

**彩蛋说明**：触发条件是 **3 月 25 日**（`Calendar.get(2) == 2` 是 0 基 MONTH ⇒ 三月；`get(5) == 25`），
当天有 1/3 概率把主菜单 splash 换成 `Happy birthday Crystal0404!!!`。

## 5. 已知差异

完整清单见项目内 `analysis\DEVIATIONS.md`（含每条的现象 / 原因 / 证据 / 影响面）。装机用户最需要知道的两条：

1. **`StackableShulkerBoxesEnhancement` 需要 Carpet 侧同时开堆叠，且已修好 NeoForge 上的失效问题。**
   NeoForge 在 `HopperBlockEntity.ejectItems` 开头插入了 `VanillaInventoryCodeHooks.insertHook`，
   它对暴露 `IItemHandler` 能力的容器直接接管并 `return true`，使 vanilla 的
   `addItem → tryMoveInItem → canMergeItems` 整段被跳过——而上游 CCA 正是靠 wrap `canMergeItems` 实现该规则的。
   本移植版补了一个 NeonForge 专属补偿 mixin `HopperBlockEntityEjectMixin`，条件成立时让控制权回落 vanilla 路径。
   **使用前提（三条同时满足）**：
   - `/cca StackableShulkerBoxesEnhancement true`
   - `/carpet stackableShulkerBoxes true`（carpet 侧必须先开，内部会把 `shulkerBoxStackSize` 置 64；carpet 配置**重启后可能回到 false，需要重设**）
   - 目标容器是原版箱子/漏斗等 `Container`（对"只暴露 `IItemHandler` 的 mod 容器"不回退，漏斗仍会合并，属已知边界）

   实测：规则关 → 漏斗把两个潜影盒合并成一格 `count:2`；规则开 → 分成两格各 `count:1`。详见 `D-S9-3`。
2. **女巫掉落表规则已验证**，行为与上游一致：规则开时用单 pool 旧表（红石 `set_count 0..2`），
   关时用原版双 pool 表（红石 `set_count 4..8`）。详见 `D-S9-2`。

其余 11 条规则均已落地并至少通过注入级或行为级验证；`ReIntroduceOldVersionRaid` 的三件套中
`BadOmenStatusEffectMixin` 与 `RaidMixin` 有行为级证据，`RaiderEntityMixin` 的"击杀队长得 bad_omen"仅到字节码级（`D-S9-4`）。

## 6. 校验下载完整性

```
sha1sum -c sha1.txt
```

或手动比对（`dist\sha1.txt` 内容）：

```
1C28F0AE915E0A4A83BD0B4B76C4CB582B8793F6  CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar
D256CEB4F89D6317DEE338AA9DEEA05D058E74E3  CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge-sources.jar
```

## 7. 文件清单

```
dist\
├── CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar         130,131 B  ← 放进 mods\
├── CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge-sources.jar 113,094 B  ← 源码，仅供阅读
├── README-安装说明.md                    ← 本文件
├── RULE-TEST.md                          ← 13 条规则的人工手测手册（含每条的证据等级）
└── sha1.txt
```

> jar 内含 mod 图标（`icon.png`，31,755 B，与上游 `assets/cca/icon.png` 同源同哈希）；
> `neoforge.mods.toml` 的 `logoFile = "icon.png"` 指向 jar 根那一份，`assets/cca/icon.png` 是上游原有布局，两处都在。
>
> jar 内同时提供 **两份 mod 元数据**：`META-INF/neoforge.mods.toml`（NeoForge 1.20.5+ 唯一读取的路径）
> 与 `META-INF/mods.toml`（老式 Forge 路径，仅用于让 PCL2 等启动器的老解析器也能识别出名称/图标/版本）。
> NeoForge 21.1 的 `ModFileParser` 只查 `neoforge.mods.toml`（已用 `javap` 确认不含 `mods.toml` 常量），
> 所以第二份**不会**造成 mod 重复加载 —— 已实测 `registered 13 rules`、无 `Duplicate mod`。

### 每条规则怎么自己测？

见 `RULE-TEST.md`。它给出了 13 条规则各自的搭场景命令与判据，并标注证据等级：

- **【E2E】** 端到端行为级已验证：`ReIntroduceOldVersionWitchLootTable`、`ShulkerBoxPowerOutputExpansion`、
  `ShulkerBoxPowerOutputExpansionColour`、`StackableShulkerBoxesEnhancement`、`ReIntroduceOldVersionRaid`（三件套中的两件）
- **【INJ】** 仅注入级/字节码级证据（注入与条件链都对，但没有行为差异证据）
