# RULE-TEST · 13 条规则手测手册

> 版本：`1.11.1-neoforge`（NeoForge 21.1.x / MC 1.21.1）
> 用途：给**人工验收**用。每条给出"怎么搭场景、发什么命令、看什么"。命令里的坐标是示例，可换。
> 证据等级标记：
> - **【E2E】** 端到端行为级已验（s9 及以前实测通过，附证据文件）
> - **【INJ】** 仅注入级 / 字节码级证据（注入点正确、条件链正确，但没跑出行为差异）
> - **【FAIL】** 实测无行为差异，原因已定位（见 `analysis/DEVIATIONS.md`）
>
> **s9 终态：13 条规则中无一条为【FAIL】**（`StackableShulkerBoxesEnhancement` 经方案① 补偿 mixin 后已转【E2E】）。

## 通用前置

```powershell
# 服务端
cd D:\Minecraft-mod-development\cca移植neoforge
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
.\_tools\serve.ps1 -LogName mytest.log -TimeoutSec 300     # 等到 SERVER READY

# RCON（单批 ≤6 条，forceload 单独发，否则会 Can't keep up! 断连）
$py='C:\Users\xiao_\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
& $py _tools\rcon_probe.py 127.0.0.1 25575 probe123 "carpet" "cca list CCA"

# 带 SNBT 双引号的命令必须走命令文件（PS 会吃掉引号）
& $py _tools\rcon_file.py 127.0.0.1 25575 probe123 _work\mycmds.txt
```

规则开关一律 `/cca <规则名> true|false`，查询 `/cca <规则名>`。
**判据回读服务端日志**（`_tools\out\<log>`），RCON 回包会丢（勘误 N1）。
假人名会被 carpet 规范化成**全大写**（`player Botb spawn` → 引用要写 `BOTB`）。

---

## 1. `AnvilCanCrushItemEntities` —【INJ】

- **原版行为**：铁砧下落砸到实体时，把实体压死/伤害。
- **手测**：
  ```
  /fill 0 70 0 0 74 0 minecraft:air
  /setblock 0 74 0 minecraft:anvil
  /summon minecraft:pig 0.5 70.0 0.5 {NoAI:1b}
  /cca AnvilCanCrushItemEntities true
  /setblock 0 73 0 minecraft:air          ← 触发铁砧下落
  ```
- **看**：`data get entity @e[type=minecraft:pig,limit=1] Health`。
  规则开 → 猪被压死（实体消失）；规则关 → 猪存活。
- **注入点**：`FallingBlockEntity`（`_mappings/refmap_mojmap.md` 对应行）。

## 2. `ComparatorIgnoresStateUpdatesFromBelow` —【INJ】

- **原版行为**：比较器不因下方方块的**状态更新**（而非信号变化）而重新计算。
- **手测**：
  ```
  /fill 0 64 0 2 66 0 minecraft:air
  /setblock 0 64 0 minecraft:redstone_block
  /setblock 1 64 0 minecraft:comparator[facing=west]
  /setblock 2 64 0 minecraft:redstone_wire
  /cca ComparatorIgnoresStateUpdatesFromBelow false
  /setblock 1 64 -1 minecraft:stone      ← 只改下方/侧方状态，不改信号
  ```
- **看**：`/data get block 1 64 0 OutputSignal`。
  - 规则关：比较器被邻居更新打断脉冲（`OutputSignal` 掉 0）。
  - 规则开：`OutputSignal` 保持 15。
- **注意**（D-S7-3）：比较器 `facing` 指向**输入侧**，`facing=west` 读西侧、朝东输出。
  `setblock` 到**相同状态**会回 `Could not set the block` 且不触发邻居更新，必须先改成别的再改回来。

## 3. `EnderDragonPartCanUseEndPortal` —【INJ】

- **原版行为**：末影龙的身体部件（`EnderDragonPart`）也能穿过末地传送门。
- **手测**：需要一条真末影龙，成本高。建议的替代判据：
  ```
  /execute in minecraft:the_end run summon minecraft:ender_dragon 0.5 64.0 0.5
  /cca EnderDragonPartCanUseEndPortal true
  ```
  在龙的位置放末地传送门方块，观察龙是否被传送。
- **注入点**：4 个类（`EntityAccessor` / `EntityInvoker` / `MobEntityMixin` / `PortalManagerAccessor`），全部是 accessor/invoker + 一处 `MobEntity` 注入。

## 4. `EndermanCannotPickUpBlocksInNether` —【INJ】

- **原版行为**：下界的末影人不再搬方块。
- **手测**（下界更省事）：
  ```
  /execute in minecraft:the_nether run fill 0 64 0 3 66 3 minecraft:netherrack
  /execute in minecraft:the_nether run summon minecraft:enderman 1.5 65.0 1.5
  /cca EndermanCannotPickUpBlocksInNether true
  ```
  等 30s+，观察末影人是否手里出现方块（`data get entity @e[type=minecraft:enderman,limit=1] CarriedBlockState`）。
- **判据细则**：规则开 → `CarriedBlockState` 恒为 `{}`；规则关 → 有机会出现方块。
- **注入点**：`EndermanEntity$PickUpBlockGoal` 的 `canUse`，target `BlockState.is(TagKey)`。

## 5. `GatewayCannotLoadingChunks` —【INJ】

- **原版行为**：末地折跃门（End Gateway）传送时不再加载目标区块。
- **手测**：需要末地折跃门实体（`minecraft:end_gateway`）。
  ```
  /execute in minecraft:the_end run summon minecraft:end_gateway 100.0 64.0 100.0
  /cca GatewayCannotLoadingChunks true
  ```
  然后穿过。**看**：`/forceload query` 在穿过后是否多出区块；日志里 `Chunk…` 加载行。
- **注入点**：`EndGatewayBlock`，target `DimensionTransition` 构造。

## 6. `ItemShadowing` —【INJ】

- **原版行为**：物品"影子"——同一 ItemStack 被多个容器持有时的同步/复制行为修正。
- **手测**：
  ```
  /setblock 0 64 0 minecraft:chest
  /item replace block 0 64 0 container.0 with minecraft:diamond 64
  /cca ItemShadowing true
  ```
  然后用漏斗/容器搬运该物品，观察是否出现数量不同步。
- **注入点**：`ScreenHandlerMixin`，target `Slot.setByPlayer` 与 `Inventory.setItem`。
- **提示**：这条最贴近"复制 bug"语义，手测时**同时检查源容器与目标容器的 count 是否守恒**。

## 7. `MagicBox` —【INJ】

- **原版行为**：开启后箱子/潜影盒/讲台等方块实体具有"魔术箱"式的跨维度/跨位置访问能力。
- **手测**：
  ```
  /cca MagicBox true
  /setblock 0 64 0 minecraft:shulker_box
  /setblock 0 64 1 minecraft:lectern
  ```
  按原版说明配合 `CCESuppressor` 使用（`/cca list CCA` 里显示为 `MagicBox/CCESuppressor (MagicBox)`）。
- **注入点**：3 个 —— `WorldChunkMixin`（`BlockEntity.isValidBlockState`）、`LecternBlockMixin`（`BaseEntityBlock.onRemove`）、
  `BlockEntityMixin`（`BlockEntity.validateBlockState`）、`ShulkerBoxBlockMixin`。
- **已知**：`-Dcca.enable.MagicSettings` 只在 1.20.5~1.20.6 上需要，1.21.1 不需要。

## 8. `NoBatSpawn` —【INJ】

- **原版行为**：禁止蝙蝠生成。
- **手测**：
  ```
  /cca NoBatSpawn true
  /execute in minecraft:overworld run summon minecraft:bat 0.5 70.0 0.5
  ```
  并用 `/gamerule doMobSpawning true` + 洞穴搞自然生成观察。
- **判据细则**：规则开 → `summon` 的蝙蝠仍会存在（mixin 挡的是 `getSeaLevel` 相关的生成判定），
  真正判据是**自然生成时不再刷出蝙蝠**（`execute if entity @e[type=minecraft:bat] run say BAT` 在长时间空跑后恒不触发）。
- **注入点**：`BatEntity`，target `LevelAccessor.getSeaLevel()`。

## 9. `ReIntroduceOldVersionRaid` —【E2E（部分）】

- **原版行为**：回到 MC 1.20.1 的袭击规则（`Raid using mc1.20.1`）。
- **三件套与证据等级**：
  | 子 mixin | 注入点 | 证据 |
  |---|---|---|
  | `BadOmenStatusEffectMixin` | `BadOmenMobEffect.applyEffectTick` @HEAD + cancellable | **【E2E】** s8 行为三连 |
  | `RaidMixin` | `Raid.absorbRaidOmen` 的 `ServerPlayer.getEffect` 实参 `RAID_OMEN`→`BAD_OMEN` | **【E2E】** 字节码 + s8 行为三连 |
  | `RaiderEntityMixin` | `Raider.die` 内 `INVOKE PatrollingMonster.die` 之前（偏移 69） | **【INJ】** 见下 |

- **已验证的行为三连**（s9 之前 s8 拿到，原文见 `_tools/out/s8_run1.log`）：
  1. 规则关 + 不在村庄 → `bad_omen` 存活；
  2. 规则开 + 村庄内 → `bad_omen` 被消耗、**从不产生 `raid_omen`**、袭击真生成（日志 `S8_RAID_PILLAGERS`）；
  3. 规则关 + 同一村庄 → 出现 `raid_omen`（390t）的 vanilla 路径。
- **未验的一条**（`D-S9-4`）：击杀带灾厄旗帜的巡逻队长 → 击杀者获得 `bad_omen`。
  9 轮尝试（4 轮行为 + 5 轮构造）全部未触发。要复现请注意：
  ```
  /cca ReIntroduceOldVersionRaid true
  /player BOTB spawn
  /tp BOTB 8.5 199.0 0.5                       ← 别离实体 128 格以上，否则 despawn
  /execute in minecraft:overworld run summon minecraft:pillager 8.5 200.0 0.5 {PatrolLeader:1b,NoAI:1b,NoGravity:1b,PersistenceRequired:1b}
  ```
  旗帜 SNBT 必须**逐字**如下（`pattern` 用 `rhombus` / `circle`，**不是** `rhombus_middle` / `circle_middle`）：
  ```
  /item replace entity @e[type=minecraft:pillager,limit=1] armor.head with minecraft:white_banner[minecraft:banner_patterns=[{pattern:"rhombus",color:"cyan"},{pattern:"stripe_bottom",color:"light_gray"},{pattern:"stripe_center",color:"gray"},{pattern:"border",color:"light_gray"},{pattern:"stripe_middle",color:"black"},{pattern:"half_horizontal",color:"light_gray"},{pattern:"circle",color:"light_gray"},{pattern:"border",color:"black"}],minecraft:hide_additional_tooltip={},minecraft:item_name='{"translate":"block.minecraft.ominous_banner"}']
  ```
  （`item_name` 只能写成**字符串**，写成 `{translate:…}` 会报 `Malformed 'minecraft:item_name' component: 'Not a string'`。）
  然后：
  ```
  /execute as BOTB at @s run damage @e[type=minecraft:pillager,limit=1] 100 minecraft:player_attack by BOTB
  /data get entity BOTB active_effects
  ```
  期望出现 `id:"minecraft:bad_omen"`。**s9 实测恒为 `Found no elements matching active_effects`。**
  若要继续排查，在 `RaiderEntityMixin` 的 handler 里加临时的写文件诊断（`new FileOutputStream(..., true)`），
  因为 `println` / log4j 在 handler 未调用时不留痕，而这次 handler **是被调用的**（`Raider.die` 偏移 69 有 handler 调用）。

## 10. `ReIntroduceOldVersionWitchLootTable` —【E2E】

- **原版行为**：女巫用旧版（单 pool）掉落表。
- **判别器**（勘误 N7）：原版 1.21.1 有两个 pool，pool2 是 `redstone`、`rolls 1.0`、`set_count 4..8`；
  我们的旧表只有一个 pool，红石与其他 6 项同级 `0..2`（+looting ≤1）。
  ⇒ **红石 `count ≥ 4` 只可能来自原版表；`≤3` 或缺席 ⇒ 旧表。单次击杀即可判定。**
- **手测**（s9 实测通过的完整序列）：
  ```
  /fill -3 -64 -3 3 -50 3 minecraft:air                       ← 必须挖空，否则箱子被埋
  /setblock 0 -60 0 minecraft:chest
  /setblock 0 -59 0 minecraft:hopper[facing=down]
  /gamerule doMobLoot true
  /difficulty easy

  /cca ReIntroduceOldVersionWitchLootTable false              ← A：规则关
  /setblock 0 -60 0 minecraft:air
  /setblock 0 -60 0 minecraft:chest                           ← 清箱要两步
  /summon minecraft:witch 0.5 -58.0 0.5 {NoAI:1b}
  /kill @e[type=minecraft:witch]
  ```
  等 5s，然后 `/data get block 0 -60 0 Items`。
  - **A 期望**：出现 `id: "minecraft:redstone", count: 4..8`。
    s9 实测：`[{count: 1, Slot: 0b, id: "minecraft:gunpowder"}, {count: 5, Slot: 1b, id: "minecraft:redstone"}]`
  - **B 期望**（换 `cca … true`，至少跑 3 次）：红石 `≤3` 或缺席。
    s9 实测三次：`glass_bottle ×2` / `redstone ×1` / `sugar ×2 + spider_eye ×2`
- **⚠ 不要用** `loot insert … loot minecraft:entities/witch` 当判据（勘误 N6）：该命令直接取 `LootTable`，
  不经过 `LivingEntity.dropFromLootTable`，而我们的 mixin 包的正是后者 ⇒ 永远返回原版表。
- **证据文件**：`_tools/out/s9_witch_off.txt`、`s9_witch_on.txt`。

## 11. `ShulkerBoxPowerOutputExpansion` —【E2E】

- **原版行为**：潜影盒用比较器读出时，信号强度按**储存量**放大。
- **手测**（s7 已验）：
  ```
  /cca ShulkerBoxPowerOutputExpansion true
  /setblock 0 64 0 minecraft:shulker_box
  /setblock 1 64 0 minecraft:comparator[facing=west]
  /setblock 2 64 0 minecraft:redstone_wire
  /item replace block 0 64 0 container.0 with minecraft:redstone 3     ← 装 3 个红石粉（约 1/9 满）
  ```
  **看**：`/data get block 2 64 0 power`。
  - 规则开：`power=3`（放大后的值）。
  - 规则关：`power=1`（vanilla 按槽位占比）。
- **注意**：`setblock` 相同状态不触发更新，要让比较器重算得做真实变更（先在别处 `stone` 再 `air`）。
- **注入点**：`ShulkerBoxBlockMixin`，target `AbstractContainerMenu.getRedstoneSignalFromBlockEntity`。

## 12. `ShulkerBoxPowerOutputExpansionColour` —【E2E（枚举）】

- **原版行为**：配合上一条，把比较器输出强度乘以**颜色权重**（不同颜色的潜影盒权重不同）。
- **手测**：先按第 11 条搭好，然后
  ```
  /cca ShulkerBoxPowerOutputExpansionColour pink
  ```
  再读 `power`，与 `none` 对比应不同。
- **注意**：这是**枚举**规则，`true`/`false` 会被 validator 拒绝（**这是正常行为**，`D-S5-1`）：
  ```
  > cca ShulkerBoxPowerOutputExpansionColour true
  Couldn't set value for rule ShulkerBoxPowerOutputExpansionColour: Valid values for this rule are:
  [none, white, light_gray, gray, black, brown, red, orange, yellow, lime, green, cyan, light_blue, blue, purple, magenta, pink]
  ```
  可选值 17 个：`none` + 16 色。

## 13. `StackableShulkerBoxesEnhancement` —【E2E】

- **原版行为**：配合 carpet 的 `stackableShulkerBoxes`，**禁止漏斗把潜影盒合并成一堆**（允许堆叠但禁止漏斗自动合并）。
- **手测**：
  ```
  /carpet stackableShulkerBoxes true           ← 必须开，否则 shulkerBoxStackSize==1，直接放行
  /fill 6 -64 -2 10 -55 3 minecraft:air
  /fill 6 -64 -2 10 -64 3 minecraft:stone      ← 补回基岩层上方地板，否则实体掉虚空
  /setblock 8 -61 0 minecraft:chest
  /setblock 8 -60 0 minecraft:hopper[facing=down]
  /setblock 8 -59 0 minecraft:hopper[facing=down]
  /cca StackableShulkerBoxesEnhancement false  ← A：规则关（期望合并）
  /setblock 8 -61 0 minecraft:air
  /setblock 8 -61 0 minecraft:chest
  /item replace block 8 -59 0 container.0 with minecraft:shulker_box
  /item replace block 8 -59 0 container.1 with minecraft:shulker_box
  ```
  等 8s，`/data get block 8 -61 0 Items`。
  - **A（规则关）**：`[{count: 2, Slot: 0b, id: "minecraft:shulker_box"}]`（合并）
  - **B（`… true`）**：`[{count: 1, Slot: 0b, ...}, {count: 1, Slot: 1b, ...}]`（两槽各 1，不合并）
- **实测结果（s9，方案① 修复后）**：
  - A：`[{count: 2, Slot: 0b, id: "minecraft:shulker_box"}]`
  - B：`[{count: 1, Slot: 0b, id: "minecraft:shulker_box"}, {count: 1, Slot: 1b, id: "minecraft:shulker_box"}]`
  - 证据文件：`_tools/out/s9_hopper_final_off.txt` / `s9_hopper_final_on.txt`
- **为什么需要补偿 mixin**（`D-S9-3`，字节码级）：NeoForge 在 `HopperBlockEntity.ejectItems` 偏移 **36** 插入了
  `invokestatic VanillaInventoryCodeHooks.insertHook(HopperBlockEntity)Z`，**偏移 42 就 `iconst_1 ireturn`**；
  目标容器只要暴露 `IItemHandler` 能力（NeoForge 给所有原版 `Container` 都挂了）就直接接管，
  vanilla 的 `addItem`（偏移 123）→ `tryMoveInItem` → `canMergeItems` **整段不可达**，
  而上游 CCA 正是 wrap `canMergeItems` ⇒ 永无调用点。
  因此在 `ejectItems` 的同一调用点上 wrap `insertHook`：规则开 + carpet 堆叠已开 + 目标 `instanceof Container` + 漏斗里确有潜影盒
  ⇒ 返回 `false` 回落 vanilla 路径。实现见
  `cca-neoforge/src/main/java/crystal0404/crystalcarpetaddition/mixins/rule/StackableShulkerBoxesEnhancement/HopperBlockEntityEjectMixin.java`。
- **已知边界**：目标是"只暴露 `IItemHandler`、不是 `Container`"的 mod 容器时不回退，这类容器上的漏斗仍会合并。
- **注意**：carpet 的 `stackableShulkerBoxes` 是**独立配置**，服务端重启后可能回到 `false`，每次测试前都要重设。

---

## 附：client 侧彩蛋（不是规则，但同属本 mod）

- **触发条件**：**3 月 25 日**（`Calendar.get(2) == 2`，0 基 MONTH ⇒ 三月；`get(5) == 25`），当天 1/3 概率把主菜单 splash
  换成 `Happy birthday Crystal0404!!!`。**不是 12 月 25 日。**
- **开关**：`-Dcca.disable.EasterEggs=false` 才**开启**（默认关闭，勘误 N9）。
- **验证状态**（`D-S9-1`）：只验了"mixin 应用/跳过两种状态可切换"，彩蛋文案本身未渲染验证（需把系统日期改到 3 月 25 日）。
  - 默认启动 → 日志有 `[CCA-Mixin] SKIP …WhatIsThis.Happy (conflict hit: TESTER:…CCAUtils$DisableEasterEggs)`
  - `-Dcca.disable.EasterEggs=false` 启动 → 无 SKIP；`javap` 该 `SplashManager.class` 能看到 `handler$…$cca$happyMixin`
