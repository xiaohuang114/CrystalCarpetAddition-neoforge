# HANDOFF s7 — 箱子规则批（StackableShulkerBoxesEnhancement / ShulkerBoxPowerOutputExpansion / ReIntroduceOldVersionWitchLootTable）

- 日期：2026-10-26
- 本阶段结论：**PASS(带遗留)** —— 3 个 mixin 全部注入成功、字节码可证、`ShulkerBoxPowerOutputExpansion` 功能已用红石粉 `power` 值端到端验证；
  遗留：`StackableShulkerBoxesEnhancement`（漏斗）与 `ReIntroduceOldVersionWitchLootTable`（女巫掉落）只做到"注入 + 开关冒烟"，未做功能级验证（见 §4.1 / §4.2）。
- 工程根：`D:\Minecraft-mod-development\cca移植neoforge\cca-neoforge`
- 上级目录（脚本/日志）：`D:\Minecraft-mod-development\cca移植neoforge`

## 1. 已落地文件（路径 + 作用）

| 路径 | 作用 |
|---|---|
| `src/main/java/crystal0404/crystalcarpetaddition/mixins/rule/StackableShulkerBoxesEnhancement/HopperBlockEntityMixin.java`（36 行） | `@WrapOperation` 包 `HopperBlockEntity.tryMoveInItem` 里的 `canMergeItems` 调用；规则开 + `CarpetSettings.shulkerBoxStackSize != 1` + 顶层物品在 `ShulkerBoxesSet.ITEMS_SET` 时返回 `false`（禁止堆叠合并）。**handler 必须 `private static`**（目标方法是 `private static`）。类级 `@Restriction(conflict = {@Condition("pca")})` |
| `src/main/java/crystal0404/crystalcarpetaddition/mixins/rule/ShulkerBoxPowerOutputExpansion/ShulkerBoxBlockMixin.java` | `@WrapOperation` 包 `ShulkerBoxBlock.getAnalogOutputSignal` 里 `AbstractContainerMenu.getRedstoneSignalFromBlockEntity(BlockEntity)I` 这一条 `invokestatic`（偏移 5）。规则开且 `shulkerBox.getColor() == ColourMap.getSettingColour()` 时返回"非空槽位数"（上限 15），否则走 `original` |
| `src/main/java/crystal0404/crystalcarpetaddition/mixins/rule/ReIntroduceOldVersionWitchLootTable/LivingEntityMixin.java` | `@WrapOperation` 包 `LivingEntity.dropFromLootTable` 里 `ReloadableServerRegistries$Holder.getLootTable(ResourceKey)LootTable;`（偏移 16，全类唯一）。`(Object) this instanceof Witch` + 规则开时返回 `LootTableUtils.Witch(lookup)`。**无类级 `@Restriction`**（勘误 L5） |
| `src/main/resources/cca.mixins.json` | `mixins` 数组由 15 → **18**（3 条追加在末尾，其余字段未动）；`client` 仍为空数组（`WhatIsThis.Happy` 留 s9） |
| `_tools/out/s7_run1.log` | 本轮唯一权威启动日志（`Done (4.425s)!`，0 致命模式） |
| `_tools/out/s7_rcon1.txt` | 全部 RCON 探针输出（含功能探针、规则基线、清理命令） |
| `_tools/out/s7_bytecode.txt` | 3 个 handler 的 `javap -p -c` 证据 + `getAnalogOutputSignal` 叠序体 |
| `analysis/DEVIATIONS.md` | 追加 s7 节：D-S7-1（lithium 不移植）、D-S7-2（两条规则功能未覆盖）、D-S7-3（探针方法论 + 修正 D-S6-3） |

源码树 java 总数 **36**（s6 的 33 + 3）；`src` 下 `lithium` 目录 0 个，`cca.mixins.json` 里 `lithium` 0 命中。

## 2. 本轮关键决策（含"为什么"）

- **决策：`transferMixin` 写成 `private static boolean`**；理由：目标 `HopperBlockEntity.tryMoveInItem` 是 `private static`，
  Mixin 要求 `@WrapOperation` handler 与目标方法静态性一致，否则 runServer 期
  `InvalidInjectionException: Handler method must be static`（勘误 L2，任务书 §6 首行）。字节码里 `wrapOperation$zzm000$cca$transferMixin` 已是 `private static`。
- **决策：`@At` 不写 `ordinal`**；理由：`canMergeItems` 在 `HopperBlockEntity` 全类只有 1 处调用（偏移 60，javap 已证），
  写 `ordinal=0` 不报错但会把"s7 之后是否出现第二个调用点"这个判断永久掩盖（勘误 L3）。
- **决策：与 s6 的 `MagicBox.ShulkerBoxBlockMixin` 叠加在同一行字节码上，两份文件都不动**；理由：MixinExtras 的 `@WrapOperation`
  支持同一指令挂多个 wrapper 并嵌套调用（勘误 L4，纠正 HANDOFF-s6 §5 的错误说法）。实测导出字节码证明两条都在（见 §3.3）。
- **决策：`getAnalogOutputSignal` 里用 `shulkerBox.getColor() == ColourMap.getSettingColour()` 而不是 `Objects.equals(...)`**；
  理由：`ColourMap.getSettingColour()` 在 `Colour.NONE` 时返回 `null`，`Objects.equals(null, null)` 判真会让**所有颜色**的潜影盒
  都走本规则分支、恒输出槽位数（勘误 L9）。`ShulkerBoxBlockEntity.getColor()` 永远不为 `null`，裸 `==` 语义安全。
- **决策：`LivingEntityMixin` 不加类级 `@Restriction`**；理由：原库这条规则的条件写在方法体里读 `CCASettings.ReIntroduceOldVersionWitchLootTable`，
  加 `@Condition(versionPredicates=">=1.21.1")` 只会让它在别的路径变化时被整类 SKIP（勘误 L5）。
- **决策：不移植 `StackableShulkerBoxesEnhancement.lithium.HopperHelperMixin`**；理由：Lithium 是 Fabric-only，NeoForge 21.1 无对应版本，
  `@Restriction(require=@Condition("lithium"))` 天然不成立。**不写进 `cca.mixins.json`**（写了也只会每次启动刷一条 SKIP 噪声）。
  记入 `analysis/DEVIATIONS.md` D-S7-1。
- **决策：不再往 `catch` 里塞 `return false`，也不碰 `CCAUtils.isLoad`**；理由：s6 的 `minecraft` 分支双通道修复（D-S6-1）是本阶段
  `ShulkerBoxPowerOutputExpansion`/其他规则能注册的前提，本阶段 0 行改动（`git diff` 无该文件）。
- **决策：功能探针的判据从 `say <TOKEN>` 改成 `/scoreboard players set/get`**；理由见 §4.3 —— `say` 在 RCON 下的回包是**空字符串**，
  条件为真也看不到任何输出，s6/s7 前半段所有 `execute if … run say` 探针的"空回显"全是假阴性。

## 3. 验收结果（命令 + 关键输出原文）

### 3.1 文件/配置基线

```
> (Get-ChildItem -Recurse -File cca-neoforge\src\main\java -Filter *.java).Count
36
> (Select-String -Path cca-neoforge\src\main\resources\cca.mixins.json -Pattern 'rule\.' | Measure-Object).Count
17
> (Get-ChildItem -Recurse -File ... -Filter *.java | Where-Object { $_.FullName -match 'lithium' }).Count
0
```

`mixins` 数组 18 条，末三条依次为：
`rule.StackableShulkerBoxesEnhancement.HopperBlockEntityMixin` / `rule.ShulkerBoxPowerOutputExpansion.ShulkerBoxBlockMixin` /
`rule.ReIntroduceOldVersionWitchLootTable.LivingEntityMixin`；`plugin`、`injectors.defaultRequire:1`、`compatibilityLevel:"JAVA_21"` 均在。

### 3.2 构建

```
> cmd /c "set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11&& gradlew.bat build --console=plain"
BUILD SUCCESSFUL in 14m 10s
7 actionable tasks: 1 executed, 6 up-to-date

> Get-ChildItem build\libs | Select-Object Name,Length
CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge-sources.jar   72320   (s6: 68,629)
CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar           88544   (s6: 83,473)
```

jar 内新类 3 个（`jar tf` 命中）：`.../StackableShulkerBoxesEnhancement/HopperBlockEntityMixin.class`、
`.../ShulkerBoxPowerOutputExpansion/ShulkerBoxBlockMixin.class`、`.../ReIntroduceOldVersionWitchLootTable/LivingEntityMixin.class`。

### 3.3 启动 + 字节码取证（`-Dmixin.debug.export=true`）

```
> .\_tools\serve.ps1 -LogName s7_run1.log -JavaOpts '-Dmixin.debug.export=true'
[22:44:21] [modloading-worker-0/INFO] [cr.cr.CrystalCarpetAdditionMod/]: [CCA] registered 13 rules
[22:44:31] [Server thread/INFO] [minecraft/DedicatedServer]: Done (4.425s)! For help, type "help"
```

致命模式计数（`Select-String -SimpleMatch`）：`Mixin apply failed`=0、`Critical injection failure`=0、
`InvalidInjectionException`=0、`MixinTransformerError`=0、`NoSuchMethodError`=0、`TEMP`（区分大小写）=0。
（大小写不敏感搜 `temp` 有 2 命中，是 `Attempting` / `attempt` 的子串，属误报。）

导出目录 `cca-neoforge\run\.mixin.out\class` 存在，`javap -p -c` 三条判据全中：

```
net/minecraft/world/level/block/entity/HopperBlockEntity.class
  73: invokestatic  #498  // Method wrapOperation$zzm000$cca$transferMixin:(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Z
  private static boolean wrapOperation$zzm000$cca$transferMixin(net.minecraft.world.item.ItemStack, net.minecraft.world.item.ItemStack, com.llamalad7.mixinextras.injector.wrapoperation.Operation);

net/minecraft/world/level/block/ShulkerBoxBlock.class
  31: invokespecial #593  // Method wrapOperation$zzn000$cca$getComparatorOutputMixin:(Lnet/minecraft/world/level/block/entity/BlockEntity;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)I
  private int wrapOperation$zzj000$cca$getAnalogOutputSignalMixin(net.minecraft.world.level.block.entity.BlockEntity, com.llamalad7.mixinextras.injector.wrapoperation.Operation);   // ← s6 MagicBox
  private int wrapOperation$zzn000$cca$getComparatorOutputMixin(net.minecraft.world.level.block.entity.BlockEntity, com.llamalad7.mixinextras.injector.wrapoperation.Operation);      // ← s7

net/minecraft/world/entity/LivingEntity.class
  30: invokespecial #2616 // Method wrapOperation$zzo000$cca$dropLootMixin:(Lnet/minecraft/server/ReloadableServerRegistries$Holder;Lnet/minecraft/resources/ResourceKey;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Lnet/minecraft/world/level/storage/loot/LootTable;
```

**`getAnalogOutputSignal` 的叠层顺序（§7 第 4 条要的答案）**：s7 的 `getComparatorOutputMixin` 在**外层**，s6 的
`getAnalogOutputSignalMixin` 在**内层**。方法体原文（`s7_bytecode.txt`）：

```
  5: invokevirtual Level.getBlockEntity
  7: aload_0 ; 8: aload 4
 10: invokedynamic #2 call:()L…/Operation;      // 最内层：原始 AbstractContainerMenu.getRedstoneSignalFromBlockEntity
 20: aload_0 ; 21: aload 4 ; 23: aload_0 ; 24: aload 5
 26: invokedynamic #3 call:(L…/ShulkerBoxBlock;L…/Operation;)L…/Operation;
 31: invokespecial wrapOperation$zzn000$cca$getComparatorOutputMixin:(…)I
 34: ireturn
```

即：规则开且颜色匹配 → s7 handler 直接返回，**不会**调用 s6 MagicBox 的 wrapper；不匹配 → `original.call(blockEntity)`
→ MagicBox wrapper → 原版 `getRedstoneSignalFromBlockEntity`。

### 3.4 功能探针（红石粉 `power` 值，`_tools/out/s7_rcon1.txt`）

场景：`forceload add -1 -1 3 3`；地面 `fill … y=63 stone`；潜影盒 `0 64 0`（3 个槽各 1 个 `minecraft:stone`，用
`item replace block 0 64 0 container.N with minecraft:stone`）；比较器 `1 64 0 [facing=west]`；红石粉 `2 64 0`。

```
> cca ShulkerBoxPowerOutputExpansionColour pink
ShulkerBoxPowerOutputExpansionColour: pink, [Change permanently?]
> cca ShulkerBoxPowerOutputExpansion true
ShulkerBoxPowerOutputExpansion: true, [Change permanently?]
> setblock 1 65 0 minecraft:stone   /   setblock 1 65 0 minecraft:air     # 制造邻居更新，逼比较器重算
> data get block 1 64 0
1, 64, 0 has the following block data: {… id: "minecraft:comparator", OutputSignal: 3}
> execute if block 2 64 0 minecraft:redstone_wire[power=3] run scoreboard players set r_on s7 3
Set [s7] for r_on to 3
> scoreboard players get r_on s7
r_on has 3 [s7]

> cca ShulkerBoxPowerOutputExpansion false
ShulkerBoxPowerOutputExpansion: false, [Change permanently?]
> (同上制造更新)
> data get block 1 64 0 → OutputSignal: 1
> execute if block 2 64 0 minecraft:redstone_wire[power=1] run scoreboard players set r_off s7 1
Set [s7] for r_off to 1
> scoreboard players get r_off s7
r_off has 1 [s7]
```

判定：规则开 → 比较器/红石粉 **3**（= 非空槽位数 3，本规则语义）；规则关 → **1**（原版 `getRedstoneSignalFromContainer`
对 3/27 格的结果）。这就是 s7 任务书 §4.7 的 `LEVEL3_ON` 判据，用计分板落地（`say` 无回显，见 §4.3）。

### 3.5 规则基线回归

```
> cca list CCA
Crystal Carpet Addition settings matching "CCA" :
- 13 条 -
…
- ShulkerBoxPowerOutputExpansionColour [none] [white] [light_gray] … [pink]
> carpet
Carpet Mod version: 1.4.147+v1.0.8-251027
Crystal Carpet Addition version: 1.11.1-neoforge
```

`- ` 行 = **13**；版本行格式正确；`RemoveVillagerTradeDistanceLimit` 仍不注册（按设计）。

### 3.6 污点扫描

```
> Select-String -Pattern 'class_\d+|method_\d+|field_\d+|remap\s*=\s*false|net\.fabricmc|me\.fallenbreath'
  (排除注释行后)
<无输出>
```

### 3.7 世界状态收尾

`cca ShulkerBoxPowerOutputExpansion false` → `scoreboard objectives remove s7` → `fill -1 64 -1 3 65 1 minecraft:air`
（`Successfully filled 4 block(s)`）→ `forceload remove all` → `stop`；日志尾行为
`ThreadedAnvilChunkStorage: All dimensions are saved` + `Thread RCON Listener stopped`。

## 4. 未解决 / 踩坑（错误原文 + 现象 + 已排查到什么程度）

### 4.1 未做功能级验证：`StackableShulkerBoxesEnhancement`（漏斗合并）

- 现状：注入已证（`wrapOperation$zzm000$cca$transferMixin` 在 `tryMoveInItem` 偏移 73），`cca StackableShulkerBoxesEnhancement true/false`
  冒烟不崩。**未**构造"漏斗 → 潜影盒堆叠"的真实传输场景。
- 已排查到的边界：ROOT 在于 `canMergeItems` 只在 `tryMoveInItem` 内 1 处调用，且需要 `CarpetSettings.shulkerBoxStackSize != 1` 才生效；
  默认 `shulkerBoxStackSize` 值未在 s7 核对（`javap` 只见字段是 `public static int`）。
- 候选方案：① RCON 造"漏斗上方箱子装满 + 漏斗指向潜影盒 + 潜影盒已有同 NBT 潜影盒"后读 `data get block <hopper>` 的 `TransferCooldown`
  与目标容器 `Items` 槽位数；② s9 真人开 GUI 验证；③ 只保留字节码证据 + 开关冒烟，写进 DEVIATIONS。
- **需要拍板**：选 ① 还是 ②。

### 4.2 未做功能级验证：`ReIntroduceOldVersionWitchLootTable`（女巫旧掉落表）

- 现状：注入已证（`wrapOperation$zzo000$cca$dropLootMixin`，目标 `ReloadableServerRegistries$Holder.getLootTable`）。
- 阻碍：需要真的击杀女巫并观察掉落物。`summon minecraft:witch` + `damage` 可行，但掉落是随机 1~3 次 roll + STICK 权重 2，
  单次观测无法区分"旧表"与"新表"。判据设计缺失。
- 候选方案：① `kill @e[type=witch]` 跑 N 次统计掉落集合（旧表必出 `glowstone_dust/sugar/redstone/spider_eye/glass_bottle/gunpowder/stick`，
  新表含 `glowstone_dust/sugar/redstone/spider_eye/glass_bottle/gunpowder/stick` 之外的 `potion` 类？需先读 `LootTableUtils.Witch` 的实际条目再定判据）；
  ② 直接读 `LootTableUtils.Witch` 逐条目断言（单元级）；③ 记为未覆盖。
- **需要拍板**：① / ② / ③。

### 4.3 s7 前半段所有 `execute if … run say` 探针都是假阴性（**方法论错误，已修正**）

- 现象：`execute if block 2 64 0 minecraft:redstone_wire[power=3] run say LEVEL3_ON` 回显为空，误判为"条件不成立"，
  于是连续几轮在"朝向/更新/几何"上打转（浪费约 40 分钟）。
- 根因：`say` 是广播命令，**RCON 的响应体是空字符串**（`rcon_probe.py` 打印空行），条件真假都看不到。
- 修正：判据一律改成 `execute if … run scoreboard players set <holder> <obj> <value>` + `scoreboard players get`，
  后者有确定文本回包（`r_off has 1 [s7]`）。**s8 起所有布尔判据都按这个写**。
- 附带发现（同样是方法论）：`setblock` 到**完全相同的方块状态**会回 `Could not set the block` 且**不产生邻居更新**，
  所以"用 `setblock` 逼比较器重算"必须真的改变状态；可靠做法是在目标方块上方 `setblock … stone` 再 `… air`（两次都是真变更）。

### 4.4 比较器 `facing` 的语义在本版本是"输入侧"（**实测结论，与直觉相反**）

- 实测：`comparator[facing=west]` 在 `1 64 0` 时**读它西侧**（`0 64 0` 的潜影盒/红石块）、**朝东**输出（`2 64 0` 的红石粉被点亮到 15）；
  `comparator[facing=east]` 读东侧。
- 佐证（同一进程内跑的对照）：`0 64 0 = redstone_block` + `comparator[facing=west]` → `OutputSignal: 15` 且 `2 64 0` 红石粉 `power=15`；
  同样布局换成 `facing=east` → `OutputSignal: 0`。
- 结论：写探针时"潜影盒在西 → 比较器 `facing=west` → 红石粉在东"。**不要**按"箭头朝外"的直觉写 `facing=east`（那会 0 命中条件）。
- 另一条：比较器刚被 `setblock` 放置时**不会**自行重算，必须有一次真实的邻居变更（§4.3 末段）。

### 4.5 `ShulkerBoxPowerOutputExpansionColour` 的选项首项是 `none`

- `cca list CCA` 显示 `[none] [white] … [pink]`，`none` 对应 `ColourMap.Colour.NONE` → `getSettingColour()` 返回 `null`，
  此时本规则对任何潜影盒都**不触发**（因为 `getColor()` 非 null）。做功能探针前必须先 `cca ShulkerBoxPowerOutputExpansionColour pink`，
  否则永远拿到原版值。默认值是 `PINK`，所以正常启动即可用；`none` 只在有人手动切过时出现。

## 5. 下一阶段起点（s8）

- 第一个待办：s8 任务书的 3 条规则 mixin，`mixins` 数组 18 → **21**，java 36 → 39。
- 必读文件：`prompts\FACTS.md`、`_mappings\refmap_mojmap.md`、`handoff\HANDOFF-s7.md`（本文件）、`analysis\DEVIATIONS.md`、
  `_decomp\mojmap\crystal0404\crystalcarpetaddition\mixins\rule\**`。
- 前置检查命令：
  ```powershell
  cd D:\Minecraft-mod-development\cca移植neoforge
  (Get-ChildItem -Recurse -File cca-neoforge\src\main\java -Filter *.java).Count     # 期望 36
  (Select-String -Path cca-neoforge\src\main\resources\cca.mixins.json -Pattern 'rule\.' | Measure-Object).Count   # 期望 17
  ```
- `LivingEntity` 共存提醒：s7 已把 `dropLootMixin` 挂在 `LivingEntity.dropFromLootTable`；s8 若在 `RaiderEntity`/`LivingEntity`
  子树再加注入，按 §3.3 的方式确认两条 wrapper 的叠层顺序（外层先跑，内层是 `original`）。
- 探针模板（照抄）：
  ```powershell
  execute if <条件> run scoreboard players set <h> <obj> <v>
  scoreboard players get <h> <obj>
  ```

## 6. 对 FACTS.md 的修正/新增（下一阶段请按这些覆盖 FACTS）

- **新增事实（比较器朝向）**：`ComparatorBlock` 的 `facing` **指向输入侧**；`comparator[facing=west]` 读西侧、朝东输出。
  实测证据见 §4.4（`OutputSignal: 15` / 红石粉 `power=15`）。
- **新增事实（RCON 判据）**：`say` 在 RCON 下回包为空，**不可用作判据**；用 `scoreboard players set/get`。`execute if block …`
  本身在 RCON 下也是空回包。
- **新增事实（`setblock`）**：目标状态与现状相同时回 `Could not set the block`，且**不触发**邻居/比较器重算；需要真变更才能刷新。
- **修正 s6 HANDOFF §4.3 / DEVIATIONS D-S6-3 第二条**：`item replace block <pos> container.N with <item>` **可用**
  （`Replaced a slot at 0, 64, 0 with [Stone]`）。s6 报 `Incorrect argument for command` 是因为漏了 `with`（勘误 L12 已认定）。
  D-S6-3 第一条（`data merge block` 被吞双引号）仍成立，未复测。
- **新增事实（导出目录）**：本轮 `.mixin.out\class` 由 `serve.ps1 -JavaOpts '-Dmixin.debug.export=true'` 正常生成；
  `serve.ps1` 每次启动会先删掉再重建，s6 的旧导出已不存在（勘误 L1 属实）。
- **新增事实（`forceload` / `fill` 回包）**：`forceload add` 对已加载区块回 `No chunks were marked for force loading`，
  `fill` 目标与现状全同回 `No blocks were filled` —— 都不是错误，别当失败重试。

## 7. 发现但本阶段未处理

1. `cca.mixins.json` 的 `"client": []` 仍为空；`rule.WhatIsThis.Happy`（client mixin）留 s9（任务书 §5-8 明确）。
2. `StackableShulkerBoxesEnhancement` / `ReIntroduceOldVersionWitchLootTable` 功能级验证缺失（§4.1 / §4.2），需 s8 或 s9 拍板。
3. `CarpetSettings.shulkerBoxStackSize` 的默认值未核（本阶段只按任务书用 `!= 1` 判空转）；不影响注入，但做漏斗探针前要查。
4. `analysis/DEVIATIONS.md` 的 D-S6-3 已被勘误 L12 部分推翻，本阶段已在 §6 记录并把新条目写成 D-S7-3；
   若后续要"清扫历史误判"，把 D-S6-3 标注为 superseded 即可（未改，避免动 s6 的结论页）。
5. 本轮 `gradlew build` 用时 14m10s（含 6 个 up-to-date），远高于 s6；若 s8 延续这种耗时，建议先 `--offline` 试一次再判断是否 loombox/网络抖动。
