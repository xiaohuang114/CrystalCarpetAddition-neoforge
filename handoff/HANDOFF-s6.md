# HANDOFF s6 — MagicBox 与 ItemShadowing（5 个 mixin）

- 日期：2026-10-26
- 本阶段结论：PASS（带遗留：两条规则的功能性行为未做真人级验证，见 §4）
- 工程根：`D:\Minecraft-mod-development\cca移植neoforge\cca-neoforge`
- 阶段任务书：`prompts\s6-MagicBox与ItemShadowing.md`；s5 验收勘误 L1~L10 未落盘（只在对话里），要点已并入本文件 §3~§4 与 `analysis\DEVIATIONS.md`

## 1. 已落地文件（路径 + 作用 + 行数）

均为相对 `cca-neoforge\`：

| 路径 | 作用 | 行数 |
|---|---|---|
| `src/main/java/crystal0404/crystalcarpetaddition/mixins/rule/MagicBox/BlockEntityMixin.java` | `@WrapWithCondition` 包 `BlockEntity.<init>` 里的 `validateBlockState`；`MagicBox` 开时对 `LecternBlockEntity` 跳过校验 | 24 |
| `src/main/java/crystal0404/crystalcarpetaddition/mixins/rule/MagicBox/LecternBlockMixin.java` | `@WrapWithCondition` 包 `onRemove` 里的 `BaseEntityBlock.onRemove` 调用（MagicBox 开时不走原版清理）+ `@Inject(TAIL)` 补一次 `super.onRemove` | 42 |
| `src/main/java/crystal0404/crystalcarpetaddition/mixins/rule/MagicBox/ShulkerBoxBlockMixin.java` | `@WrapOperation` 包 `getAnalogOutputSignal` 里的 `getRedstoneSignalFromBlockEntity`，MagicBox 开时改走 `getRedstoneSignalFromContainer` | 25 |
| `src/main/java/crystal0404/crystalcarpetaddition/mixins/rule/MagicBox/WorldChunkMixin.java` | `@ModifyExpressionValue` 改 `LevelChunk.setBlockState` 里的 `BlockEntity.isValidBlockState` 结果（讲台 BE 被潜影盒状态接管时判真） | 38 |
| `src/main/java/crystal0404/crystalcarpetaddition/mixins/rule/ItemShadowing/ScreenHandlerMixin.java` | `@WrapWithCondition` 干掉 `doClick` 第 5 处 `Slot.setByPlayer`（ordinal=4）+ `@Inject` 在 `Inventory.setItem`（ordinal=1）处按 `ItemShadowing` 补一次 `slot.setByPlayer(itemStack)` | 46 |
| `src/main/resources/cca.mixins.json` | `mixins` 数组 10 → **15**（新增 `rule.ItemShadowing.ScreenHandlerMixin`、`rule.MagicBox.{BlockEntity,LecternBlock,ShulkerBoxBlock,WorldChunk}Mixin`），其余字段未动 | 28 |
| `src/main/java/crystal0404/crystalcarpetaddition/utils/CCAUtils.java` | **跨阶段修复（非本阶段任务书范围，属 bug 修复）**：`isLoad` 的 `minecraft` 分支改走 `lookupModVersion(ModIds.MC)` 双通道，`SharedConstants` 降为兜底；异常/坏谓词从静默改为 warn | 214 |

参考的既有基线（本阶段一个字节都没改）：`mixins/carpet/SettingsManagerMixin.java` 与 s5 的 9 个 mixin。

## 2. 本轮关键决策（含"为什么"）

- **决策**：`BlockEntityMixin` 的 `method` 写成带完整描述符的 `<init>(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V`。
  理由：`BlockEntity` 只有一个构造器，写死描述符能让"抄错映射"当场变成 `InvalidInjectionException`，而不是悄悄注到别处。
  备选：只写 `<init>`（更短，但失去上面这层自检）。

- **决策**：`LecternBlockMixin` 的第一个 handler 首参写 `BaseEntityBlock`（不是 `LecternBlock`）。
  理由：字节码偏移 43 处 `invokespecial` 的 owner 是 `BaseEntityBlock`，被捕获值的**静态类型是父类**；写成子类会抛 `InvalidInjectionException`。

- **决策**：`WorldChunkMixin` 的注入体里**一个日志字符都没有**。
  理由：`LevelChunk.setBlockState` 是每秒数千次的路径，`LOG.info` 会拖死服务端并撑爆 `latest.log`（勘误 L7 / D-S5-3）。
  备选：debug 分支（也没加，因为开关判断本身也在热点里）。

- **决策**：`ShulkerBoxBlockMixin` **不加**类级 `@Restriction`。
  理由：原库就是靠运行时 `CCASettings.MagicBox` 分支的，加了条件注解反而改变语义。
  备选：与其它三个 MagicBox mixin 保持一致都加 require（没有采用，因为原版行为不是这样）。

- **决策**：`ScreenHandlerMixin` 保留 `ordinal = 4` / `ordinal = 1`，一个都没删。
  理由：`javap -c` 实测 `doClick` 里 `Slot.setByPlayer(ItemStack)V` 出现 7 次（偏移 509/1009/1182/1243/1262/1316/1362），`Inventory.setItem(int,ItemStack)V` 出现 3 次（1164/1255/1355）；删掉 ordinal 会注到第一处，语义完全错。

- **决策**：`@Local(ordinal = 0) Slot` / `@Local(ordinal = 0) ItemStack` 写实类型，不写 `Object`。
  理由：K4 —— 反编译擦掉了 cast；写 `Object` 编译能过，运行期 `ClassCastException`。

- **决策（跨阶段 bug 修复）**：改 `CCAUtils.isLoad` 的 `minecraft` 分支，而**没有**改 `MixinRestrictionProcessor`。
  理由：判定器本身没错，错在"取 MC 版本"这一步在 mixin config 解析期不可用（详见 §3 第 4 条的诊断链）。任务书 §6 明确"这属于 s3 的债，不要在 s6 里改处理器"，故只动 `CCAUtils`，并把静默 `catch` 改成 `warn` 以免下次再被吞。

## 3. 验收结果（命令 + 关键输出原文）

### 3.1 文件与配置基线

```
> powershell: (Get-ChildItem -Recurse .\cca-neoforge\src\main\java -Filter *.java).Count
33                     # s5 是 28
> powershell: (Select-String -Path .\cca-neoforge\src\main\resources\cca.mixins.json -Pattern '^\s+"rule\.|^\s+"carpet\.').Count
15                     # s5 是 10
# Yarn/legacy 名扫描（method_\d+|class_\d+|validateSupports|onStateReplaced|isIn\(|getMaxCount|remap\s*=\s*false）
0 行
```

### 3.2 编译

```
> .\gradlew.bat build --console=plain
BUILD SUCCESSFUL in 1m 36s
> Get-ChildItem .\cca-neoforge\build\libs | Select-Object Name,Length
CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar          83473    # s5 基线 77054
CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge-sources.jar  68629    # s5 基线 63637
```

### 3.3 起服 + 5 条字节码证据（同一次运行内完成）

```
> .\_tools\serve.ps1 -LogName s6_run1.log -JavaOpts '-Dmixin.debug.export=true'
SERVER READY (launcher pid=15568) log=D:\Minecraft-mod-development\cca移植neoforge\_tools\out\s6_run1.log

# 先造加载路径（否则 setBlockState / onRemove 相关类可能没被 load，见勘误 L1 / D-S5-4）
> forceload add 16 16 24 24
> setblock 20 64 20 minecraft:shulker_box
> setblock 20 65 20 minecraft:lectern
> execute if block 20 65 20 minecraft:lectern
Test passed

# javap -p 取证（导出 136 个类）
1 OK  net\minecraft\world\level\block\entity\BlockEntity.class  -> 1 处
2 OK  net\minecraft\world\level\block\LecternBlock.class  -> 2 处
3 OK  net\minecraft\world\level\block\ShulkerBoxBlock.class  -> 1 处
4 OK  net\minecraft\world\level\chunk\LevelChunk.class  -> 1 处
5 OK  net\minecraft\world\inventory\AbstractContainerMenu.class  -> 2 处
```

生成的 handler 签名（`javap -p` 原文节选，证明 `@Local` 真的解析成了实类型，K4 已排除）：

```
private boolean wrapWithCondition$zzi000$cca$removeOnRemoveMixin(net.minecraft.world.level.block.BaseEntityBlock, ...BlockState, net.minecraft.world.level.Level, net.minecraft.core.BlockPos, ...BlockState, boolean);
private void handler$zzi000$cca$afterOnRemoveMixin(...BlockState, ...Level, ...BlockPos, ...BlockState, boolean, org.spongepowered.asm.mixin.injection.callback.CallbackInfo);
private boolean modifyExpressionValue$zzk000$cca$setBlockStateMixin(boolean, net.minecraft.world.level.block.entity.BlockEntity);
private boolean wrapWithCondition$zzg000$cca$doClickWrapMixin(net.minecraft.world.inventory.Slot, net.minecraft.world.item.ItemStack);
private void handler$zzg000$cca$doClickInjectMixin(int, int, net.minecraft.world.inventory.ClickType, net.minecraft.world.entity.player.Player, ...CallbackInfo, net.minecraft.world.inventory.Slot, net.minecraft.world.item.ItemStack);
```

### 3.4 日志判据（致命模式 5 个，沿用 s5 基线）

```
> Select-String -LiteralPath .\_tools\out\s6_run1.log -Pattern 'Mixin apply failed|Critical injection failure|InvalidInjectionException|MixinTransformerError|\[CCA-Mixin\] SKIP'
count = 0
> Select-String -LiteralPath .\_tools\out\s6_run1.log -Pattern 'Done \('
[22:21:28] [Server thread/INFO] [minecraft/DedicatedServer]: Done (4.357s)! For help, type "help"
> Select-String -LiteralPath .\_tools\out\s6_run1.log -Pattern '\[CCA\] version='
[22:21:18] [modloading-worker-0/INFO] [cr.cr.CrystalCarpetAdditionMod/]: [CCA] version=1.11.1-neoforge CCA-category rules=13
```

**关键**：本阶段 4 个带 `require=@Condition("minecraft", ">=1.21.1")` 的 mixin **一条 SKIP 都没有**，即类级 `@Restriction` 的"通过路径"首次被真实走通。

### 3.5 RCON 三项（`_tools\out\s6_rcon1.txt`，28 条命令 = 2 + 13 规则 × true/false）

```
===== CMD: carpet =====
Carpet Mod version: 1.4.147+v1.0.8-251027
Crystal Carpet Addition version: 1.11.1-neoforge

===== CMD: cca list CCA =====
- AnvilCanCrushItemEntities [true] [false]
- ComparatorIgnoresStateUpdatesFromBelow [true] [false]
- EnderDragonPartCanUseEndPortal [true] [false]
- EndermanCannotPickUpBlocksInNether [true] [false]
- GatewayCannotLoadingChunks [true] [false]
- ItemShadowing [true] [false]
- MagicBox/CCESuppressor (MagicBox) [true] [false]
- NoBatSpawn [true] [false]
- ReIntroduceOldVersionRaid [true] [false]
- ReIntroduceOldVersionWitchLootTable [true] [false]
- ShulkerBoxPowerOutputExpansion [true] [false]
- ShulkerBoxPowerOutputExpansionColour [none] [white] … [pink]
- StackableShulkerBoxesEnhancement [true] [false]
```

统计：`- ` 行 = **13**；`===== CMD:` = 28；`Unknown command` = **0**；`Couldn't set value` = **0**。

> **注意 `/cca list CCA` 只有 13 行而 `CCASettings` 有 14 个字段**：`RemoveVillagerTradeDistanceLimit` 在
> `CCASettings.java:55` 带 `@Restriction(require = @Condition(value = "minecraft", versionPredicates = ">=1.21.4"))`，
> 在 1.21.1 上**按设计被排除**（这正是 s3 `D-S3-3` 的版本谓词证据链）。因此我对它发命令得到的是
> （原文）：`Unknown rule: RemoveVillagerTradeDistanceLimit` —— 不是 `Unknown command`，也不是错误。
> 13 行 = 12 条 boolean + 1 条枚举 `ShulkerBoxPowerOutputExpansionColour` + 1 条 `StackableShulkerBoxesEnhancement`。

### 3.5b 13 条**已注册**规则的 toggling 冒烟（`_tools\out\s6_toggle.txt`，`s6_run3.log` 那次运行）

为避免上面那 2 条 `Unknown rule` 混进 DoD 统计，另跑一轮"恰好覆盖 13 条已注册规则"的 26 条命令
（12 boolean × true/false + 枚举取 2 个合法值 `none` / `pink`）：

```
CMD count (should be 26): 26
Unknown rule: 0
Unknown command: 0
Couldn't set value: 0

ItemShadowing: true, [Change permanently?]
ItemShadowing: false, [Change permanently?]
MagicBox: true, [Change permanently?]
MagicBox: false, [Change permanently?]
ShulkerBoxPowerOutputExpansionColour: none, [Change permanently?]
ShulkerBoxPowerOutputExpansionColour: pink, [Change permanently?]
```

该次运行日志 `_tools\out\s6_run3.log`：致命模式 **0** 命中，`Done (4.469s)!`，`[CCA] version=1.11.1-neoforge CCA-category rules=13`。

### 3.6 MagicBox 行为探针（记录现象，不预设结论）

`_tools\out\s6_magicbox_off.txt` / `_tools\out\s6_magicbox_on.txt`（22:16 那一轮，世界内 20,64,20 = shulker_box、20,65,20 = lectern）：

```
############ OFF ############
===== CMD: cca MagicBox false =====
MagicBox: false, [Change permanently?]
===== CMD: execute if block 20 65 20 minecraft:lectern =====
Test passed
===== CMD: data get block 20 65 20 =====
20, 65, 20 has the following block data: {x: 20, y: 65, z: 20, id: "minecraft:lectern"}
===== CMD: data get block 20 64 20 =====
20, 64, 20 has the following block data: {x: 20, y: 64, z: 20, id: "minecraft:shulker_box"}

############ ON ############
===== CMD: cca MagicBox true =====
MagicBox: true, [Change permanently?]
===== CMD: execute if block 20 65 20 minecraft:lectern =====
Test passed
===== CMD: data get block 20 65 20 =====
20, 65, 20 has the following block data: {x: 20, y: 65, z: 20, id: "minecraft:lectern"}
===== CMD: data get block 20 64 20 =====
20, 64, 20 has the following block data: {x: 20, y: 64, z: 20, id: "minecraft:shulker_box"}
```

结论：**两侧完全一致，未观察到可分辨差异**。原因见 §4 的 D-S6-3。按任务书 §4 第 6 步的口径，这不算失败，属于"未覆盖"。

## 4. 未解决 / 踩坑（错误原文 + 现象 + 已排查到什么程度）

### 4.1 【已解决，但必须记录】`isLoad("minecraft")` 在 mixin config 阶段恒抛异常 → 3 个 mixin 被误判 SKIP

- 现象（第一轮 `_tools\out\s6_run1_preversionfix.log`，原文三行）：

```
[22:09:15] [main/INFO] [CCA-Mixin-Restriction/]: [CCA-Mixin] SKIP crystal0404.crystalcarpetaddition.mixins.rule.MagicBox.BlockEntityMixin (require failed: MOD:minecraft@[>=1.21.1])
[22:09:15] [main/INFO] [CCA-Mixin-Restriction/]: [CCA-Mixin] SKIP crystal0404.crystalcarpetaddition.mixins.rule.MagicBox.LecternBlockMixin (require failed: MOD:minecraft@[>=1.21.1])
[22:09:15] [main/INFO] [CCA-Mixin-Restriction/]: [CCA-Mixin] SKIP crystal0404.crystalcarpetaddition.mixins.rule.MagicBox.WorldChunkMixin (require failed: MOD:minecraft@[>=1.21.1])
```

  服务端本身能起来（`Done (4.241s)!`），但那三项字节码取证是 `NOHIT/MISSING`。
- 诊断链：在 `CCAUtils.isLoad` 的 `catch (Throwable)` 分支与谓词失败分支临时插 `[CCA] TEMP ...` warn（`_tools\out\s6_diag1.log`，原件已归档为 `s6_diag1_prefix.log`），拿到原文：

```
[22:12:02] [main/WARN] [cr.cr.CrystalCarpetAdditionMod/]: [CCA] TEMP isLoad(carpet) actual='1.4.147+v1.0.8-251027' predicates=[]
[22:12:03] [main/WARN] [cr.cr.CrystalCarpetAdditionMod/]: [CCA] TEMP isLoad(minecraft) threw java.lang.IllegalStateException: Game version not set      ← ×3，就是那三个 mixin
[22:12:21] [modloading-worker-0/WARN] [cr.cr.CrystalCarpetAdditionMod/]: [CCA] TEMP isLoad(minecraft) actual='1.21.1' predicates=[>=1.21.4]
```

  同一份代码、同一次启动：mixin 阶段（`[main]`）抛 `IllegalStateException`，20 秒后规则注册阶段（`[modloading-worker-0]`）正常拿到 `1.21.1`。
- 根因：`CCAUtils.isLoad` 对 `ModIds.MC` 特判走 `SharedConstants.getCurrentVersion().getName()`，该静态初始化在 mixin config 解析期会抛 `IllegalStateException: Game version not set`，又被 `catch (Throwable) { return false; }` 静默吞掉 → 整条 require 判 false → SKIP。
- 修复（`utils/CCAUtils.java`）：MC 分支改为 `lookupModVersion(ModIds.MC)`（`ModList` / `LoadingModList` 双通道，实测该阶段能拿到 `1.21.1`），仅在返回空时兜底 `SharedConstants.getCurrentVersion().getName()`；`catch` 改成 `LOGGER.warn("[CCA] isLoad({}) could not resolve version: {}", modId, t.toString())`；坏谓词改成 `LOGGER.warn("[CCA] bad version predicate '{}' for {}={}", ...)`。**三处 TEMP 日志已全部删除**（`s6_run1.log` 里 `TEMP` 命中数 = 0）。
- 已验证：修复后 `s6_run1.log` 三条 SKIP 消失、5/5 字节码 OK。
- 为什么 s5 没暴露：s5 的 `@Condition("carpet")` 不带版本谓词，`isLoad` 在拿到 `actual` 之前就用 `versionPredicates.length == 0` 返回 `true`，**永远不会走到抛异常的 MC 分支**；只有 s6 这种 `@Condition("minecraft", ">=1.21.1")` 才踩到。

### 4.2 【未覆盖】`ItemShadowing` 功能性未验证（勘误 L10 已预判）

- 该规则改的是**真人点击 slot** 的路径（`doClick` 第 5 处 `Slot.setByPlayer` + 第 2 处 `Inventory.setItem`），RCON 造不出真实 `ClickType` 序列。
- 本阶段只做了：注入成功（§3.3 第 5 条 `OK`，且 handler 签名里 `Slot`/`ItemStack` 是实类型）+ 开关冒烟（`cca ItemShadowing true/false` 均无异常、服务端不崩）。
- 结论：**功能待真人开服点击验证**，不能据此判 DoD 未过。

### 4.3 【未覆盖】`MagicBox` 在 RCON 探针下无可分辨差异（D-S6-3）

- 已尝试三种探针，全部无法区分 on/off：
  1. `setblock` 潜影盒 + 讲台 + `data get block`：两侧输出完全一致（见 §3.6）。
  2. 比较器读取：`setblock 21 64 20 minecraft:comparator[facing=west]` + 往潜影盒塞物品 → 想读 `OutputSignal`。
  3. 塞物品的两条路都失败，且**是工具链限制、不是 mod 问题**：
     - `data merge block 20 64 20 {Items:[{Slot:0b,id:"minecraft:diamond",count:64b}]}` → `_tools\rcon_probe.py` 把命令里的双引号吞掉，服务端收到 `id:minecraft:diamond` → `Expected '}'`；
     - `item replace block 20 64 20 container.0 minecraft:diamond` → 该服务端恒回 `Incorrect argument for command`（`...ntainer.0 minecraft:diamond<--[HERE]`），槽位参数 `container.N` 在此环境下不被接受。
- 另外从代码语义看，`getRedstoneSignalFromBlockEntity` 与 `getRedstoneSignalFromContainer` 对 `ShulkerBoxBlockEntity`（本身就是 `Container`）算出的值相同，**比较器路径本身就不是好的判别信号**。
- 结论：`MagicBox` 的可观察差异需要 **真人 GUI 交互**（把潜影盒罩在讲台上、打开看内容物）才能验证。已按任务书要求如实记录，不伪造点击。

## 5. 下一阶段起点（s7：箱子规则批）

- 第一个待办：`StackableShulkerBoxesEnhancement.HopperBlockEntityMixin`（`canMergeItems` + `tryMoveInItem`，类级 `@Restriction` require MC ≥1.21.1）。
- 必读文件：`prompts\s7-箱子规则批.md`、`_mappings\refmap_mojmap.md`、`_decomp\mojmap\crystal0404\crystalcarpetaddition\mixins\rule\StackableShulkerBoxesEnhancement\HopperBlockEntityMixin.java`、本文件 §3.4 / §4.1。
- 前置检查命令：

```powershell
cd "D:\Minecraft-mod-development\cca移植neoforge"
(Select-String -Path .\cca-neoforge\src\main\resources\cca.mixins.json -Pattern '^\s+"rule\.|^\s+"carpet\.').Count   # 应 15
(Get-ChildItem -Recurse .\cca-neoforge\src\main\java -Filter *.java).Count                                          # 应 33
Select-String -LiteralPath .\_tools\out\s6_run1.log -Pattern '\[CCA-Mixin\] SKIP'                                   # 应 0
```

- **s7 必须知道的同名冲突**：`ShulkerBoxBlock` 上现在有两个 mixin —— 本阶段的 `rule.MagicBox.ShulkerBoxBlockMixin`（注入 `getAnalogOutputSignal` 里的 `getRedstoneSignalFromBlockEntity`）与 s7 的 `rule.ShulkerBoxPowerOutputExpansion.ShulkerBoxBlockMixin`（同名不同包）。Mixin 允许多个 mixin 指向同一目标类，但**两者不能注入同一行字节码**；若 s7 的注入点也是 `getAnalogOutputSignal`，必须换 `@At` 的 target/局部偏移，不能删本阶段的文件。
- **不要做的事**：不要动 `SettingsManagerMixin` 与 s5 的 9 个 mixin；不要把 `lithium\HopperHelperMixin` 硬塞进 `cca.mixins.json`（lithium 无 NeoForge 端对应物，须记 `D-S7-x`）。

## 6. 对 FACTS.md 的修正 / 新增（下一阶段请按这些覆盖 FACTS）

1. **`D-S3-4` 的最后一句作废**。原文写"`minecraft` 永远走 `SharedConstants.getCurrentVersion().getName()` 特判，不受影响"——**错的**：
   在 mixin config 解析期该调用抛 `IllegalStateException: Game version not set`。现在 MC 与其它 mod 一样走 `lookupModVersion` 双通道，`SharedConstants` 只作兜底。
2. **新增事实**：NeoForge 21.1.265 的 `LoadingModList` 在 mixin config 阶段已可用，并能返回伪 mod `minecraft` 的版本 `1.21.1`（证据：§3.4 与 `s6_diag1_prefix.log`）。因此"带版本谓词的 `@Condition("minecraft", …)`"是本工程**唯一**能验证版本谓词链路的写法。
3. **新增事实**：`@Condition("minecraft", ">=1.21.1")` 的 `describe()` 渲染成 `MOD:minecraft@[>=1.21.1]`（SKIP 行原文如此），s7 排查时按这个字符串 grep。
4. **`_tools\serve.ps1` 用法补充**：`-LogName` 决定 `_tools\out\<log>` 并按**同名覆盖**（本轮 s6_run1.log 被重跑覆盖，旧件已另存 `s6_run1_preversionfix.log`）。要留存失败现场必须先 `Copy-Item` 改名。
5. **RCON 工具限制（新事实）**：`_tools\rcon_probe.py` 会吞双引号（`data merge block … {…id:"minecraft:x"…}` 必失败）；`item replace block <pos> container.N` 在此服务端恒报 `Incorrect argument for command`。想要"往容器塞物品"，要么真人登录，要么换 `_ref` 之外的其它通道；**不要再在这两条上耗时间**。
6. **规则显示名**：`- MagicBox/CCESuppressor (MagicBox) [true] [false]` —— Carpet 的 `- ` 行会把规则名与 `extraInfo` 拼在一起，grep 规则名时别期望整行以 `- MagicBox ` 开头。

## 7. 发现但本阶段未处理

- `analysis\DEVIATIONS.md` 已追加 `D-S6-1`（`isLoad` MC 分支改写，含与 `D-S3-4` 的冲突说明）、`D-S6-2`（`MagicBox`/`ItemShadowing` 功能性未覆盖）、`D-S6-3`（RCON 工具限制与比较器不是好的判别信号）、`D-S6-4`（`s6_run1.log` 被覆盖、旧件归档）。
- `_tools\out\` 本阶段新增文件：
  - `s6_run1.log`（**最终权威日志**，0 致命模式，`Done (4.357s)!`）
  - `s6_run1_preversionfix.log`（修复前现场，含 3 条 SKIP 原文）
  - `s6_diag1.log` / `s6_diag1_prefix.log`（TEMP 日志诊断现场，含 `IllegalStateException: Game version not set`）
  - `s6_run2.log`（中间一次全绿运行，22:20）
  - `s6_rcon1.txt`（28 条命令 / 13 条 `- ` 行；含 2 条 `Unknown rule: RemoveVillagerTradeDistanceLimit`，原因见 §3.5）
  - `s6_toggle.txt` + `s6_run3.log`（13 条已注册规则 × 26 条命令的干净 toggling 冒烟，0 异常）
  - `s6_warmup.txt`、`s6_magicbox_off.txt`、`s6_magicbox_on.txt`、`s6_magicbox_cmp_off.txt`、`s6_magicbox_cmp_on.txt`、`s6_magicbox_cmp2.txt`（MagicBox 探针，含失败尝试）
- 遗留观察（不属于 s6 范围）：`/cca list CCA` 里 `MagicBox` 显示为 `MagicBox/CCESuppressor (MagicBox)`，`extraInfo` 文案是原库带的；s9 若要统一文案再说。
- 世界状态已复原：`20,64,20` / `20,65,20` / `21,64,20` 已 `setblock … air`，`forceload remove all`，`MagicBox`/`ItemShadowing` 已置回 `false`，服务端已 `stop`。
