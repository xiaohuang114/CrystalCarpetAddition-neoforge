# HANDOFF s5 — 规则批 A（Anvil / Comparator / NoBat / Enderman / Gateway / EnderDragonPart）

- 日期：2026-10-26
- 本阶段结论：**PASS**（12/12 DoD 全绿，无遗留未解决问题）
- 工程根：`D:\Minecraft-mod-development\cca移植neoforge\cca-neoforge`
- 证据目录：`D:\Minecraft-mod-development\cca移植neoforge\_tools\out\`
  - `s5_run1b.log`（带 `-Dmixin.debug.export=true` 的起服日志，导出 137 个类）
  - `s5_rcon.txt`（`/carpet` + `/cca list CCA` 回显）
  - `s5_bytecode.txt`（§4-4 的 9 行取证输出原文）
  - `s5_run2.log` + `s5_toggle.txt`（13 条规则 toggle 冒烟，27 条命令）

## 1. 已落地文件（路径 + 作用 + 行数）

工程内 java 总数 **19 → 28**（本轮 +9）。`cca.mixins.json` 的 `mixins` **1 → 10**。

| 路径（相对 `cca-neoforge\src\main\java\crystal0404\crystalcarpetaddition\`） | 作用 | 行数 |
|---|---|---|
| `mixins\rule\AnvilCanCrushItems\FallingBlockEntityMixin.java` | `@WrapOperation` 把 `causeFallDamage` 里的 `Predicate.and` 换成 `EntitySelector.NO_CREATIVE_OR_SPECTATOR`，实现"铁砧砸死掉落物" | 36 |
| `mixins\rule\ComparatorIgnoresUpdatesFromBelow\ComparatorBlockMixin.java` | `@ModifyExpressionValue` 改 `updateShape` 里的 `canSurviveOn(...)` 结果 → `ComparatorIgnoresStateUpdatesFromBelow` | 22 |
| `mixins\rule\NoBatSpwan\BatEntityMixin.java` | `@ModifyExpressionValue`（**static** 处理器）改 `checkBatSpawnRules` 里的 `LevelAccessor.getSeaLevel()I` → `Integer.MIN_VALUE` | 22 |
| `mixins\rule\EndermanCannotPickUpBlocksInNether\EndermanEntity$PickUpBlockGoalMixin.java` | `@ModifyExpressionValue` + `@Local(ordinal = 0) Level` 改 `EndermanTakeBlockGoal.tick` 里的 `BlockState.is(TagKey)Z` | 26 |
| `mixins\rule\GatewayCannotLoadingChunks\EndGatewayBlockMixin.java` | `@ModifyArg(index = 5)` 把 `DimensionTransition.<init>` 的 `PostDimensionTransition` 换成 `DimensionTransition.DO_NOTHING` | 25 |
| `mixins\rule\EnderDragonPartCanUseEndPortal\EntityAccessor.java` | `@Accessor("portalProcess")` → `PortalProcessor getPortalManager()` | 13 |
| `mixins\rule\EnderDragonPartCanUseEndPortal\EntityInvoker.java` | `@Invoker("handlePortal")` → `void invokeTickPortalTeleportation()` | 12 |
| `mixins\rule\EnderDragonPartCanUseEndPortal\MobEntityMixin.java` | `@Inject(method = "tick", at = @At("TAIL"))`：末影龙本体 tick 时，把"正站在末地门里的每个 `EnderDragonPart`"各推一次 `handlePortal()` | 37 |
| `mixins\rule\EnderDragonPartCanUseEndPortal\PortalManagerAccessor.java` | `@Accessor("portal")` → `Portal getPortal()` | 13 |
| `src\main\resources\cca.mixins.json`（改） | `mixins` 数组 1 → 10 条；`client` / `plugin` / `injectors` / `compatibilityLevel` / `minVersion` / `required` / `package` **一字未动** | 24 |

工程外新增工具：

| 路径 | 作用 |
|---|---|
| `_tools\serve.ps1` | 起服到日志出现 `Done (` 就返回、**不停服**（后续靠 `rcon_probe.py` 发包）。参数 `-LogName` / `-JavaOpts` / `-TimeoutSec`。全 ASCII；root 由 `Split-Path -Parent $PSScriptRoot` 推导；每次起服前先删 `cca-neoforge\run\.mixin.out` |

文档：

| 路径 | 变化 |
|---|---|
| `analysis\DEVIATIONS.md` | 新增 D-S5-1 ~ D-S5-4（枚举规则冒烟、取证必须走 javap、`-Dmixin.debug=true` 会打崩 carpet、导出要求目标类真被加载） |

## 2. 本轮关键决策（含"为什么"）

- **决策：9 个 mixin 一个类级 `@Restriction` 都不加。** 理由：这 6 条规则在 1.21.1 上条件全满足（`EnderDragonPartCanUseEndPortal` / `GatewayCannotLoadingChunks` 的 `minecraft>=1.21` 只在 FIELD 级 `@Restriction` 上），加了只会多一层 `[CCA-Mixin] SKIP` 噪声。备选（给它们加类级 `@Restriction(require=@Condition("minecraft","*"))`）没选。
- **决策：`BatEntityMixin.canSpawnMixin` 写 `static`。** 理由：`Bat.checkBatSpawnRules(...)` 在 1.21.1 是 `public static`（`javap` 实测），`@ModifyExpressionValue` 的处理器必须与被注入方法同静态性，否则 `InvalidInjectionException: … expected static`。
- **决策：Comparator 的 `@At target` owner 写 `ComparatorBlock`（不是 `DiodeBlock`）。** 理由：`ComparatorBlock.updateShape` 的字节码里是 `invokevirtual Method canSurviveOn:(…)Z`（javap 省略 owner = 当前类），`canSurviveOn` 虽声明在 `DiodeBlock`，但字节码 owner 记的是 `ComparatorBlock`。实测 1 处命中。
- **决策：`@ModifyArg index = 5` 保持 5。** 理由：`DimensionTransition.<init>(ServerLevel,Vec3,Vec3,float,float,PostDimensionTransition)` 里 `PostDimensionTransition` 是第 6 个参数，`index` 从 0 数且 `this` 不占位；改 4 会打到第二个 `float`。实测命中（注入方法名 `modify$zzf000$cca$createTeleportTargetMixin`）。
- **决策：`MobEntityMixin` 类名保留 Yarn 名（`MobEntityMixin`），目标是 Mojmap `Mob`。** 理由：硬约束 6 —— 类名与 `_decomp/mojmap` 完全一致才能逐文件 diff。同理 `EntityAccessor`/`EntityInvoker`/`PortalManagerAccessor` 名字都保留（哪怕 `PortalManager` 在 Mojmap 里叫 `PortalProcessor`）。
- **决策：`if (!((Object) this instanceof EnderDragon dragon))` 用 `(Object)` 前置转换。** 理由：`Mob` 与 `EnderDragon` 无继承关系（`EnderDragon extends Mob`，反方向不成立），javac 直接 `instanceof` 会报"不可转换的类型"；`(Object)` 是编译期唯一合法写法，运行时语义不变（K4 同一类问题的第三次出现）。
- **决策：末影龙分身用 `dragon.getSubEntities()`，不用 `getParts()`。** 理由：`EnderDragon` 同时有 `public EnderDragonPart[] getSubEntities()`（原版 Yarn `method_5690`）与 NeoForge 的 `public PartEntity<?>[] getParts()`；后者是 PartEntity 体系，语义不同（且任务书 §2-9 明令禁止）。
- **决策：起服只用 `-Dmixin.debug.export=true`，绝不用 `-Dmixin.debug=true`。** 理由：后者会让 carpet 自带的 `MinecraftServer_pingPlayerSampleLimit` 崩掉（原文见 §4-1 / D-S5-3）。
- **决策：`MobEntityMixin` 注入体里 `continue` 掉 `portalProcessor == null` 的分身。** 理由：原版源码就是 `if (portalManager != null)` 卫语句；分身只有在真正踩进传送门后才有 `PortalProcessor`，为 null 是常态，不能 NPE。
- **决策：`serve.ps1` 起服前清空 `run\.mixin.out`。** 理由：Mixin 的导出目录是**追加/覆盖**写，不清空会把上一轮的旧类混进来，"9 条取证都 OK"可能来自旧字节码。代价：每次起服都要重新导出（见 §4-4 备注）。

## 3. 验收结果（命令 + 关键输出原文）

### 3-1 编译（§4-3）

```
> cd D:\Minecraft-mod-development\cca移植neoforge\cca-neoforge
> cmd /c 'set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11" && gradlew.bat build --console=plain'
BUILD SUCCESSFUL in 2s
5 actionable tasks: 5 up-to-date
--- artifacts ---
CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge-sources.jar  63637 B
CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar          77054 B
```
（对照 s4：jar 67,122 B / sources 55,621 B → 本轮 +9 个类后 77,054 B / 63,637 B）

### 3-2 起服 + 导出（§4-4）

```
> .\_tools\serve.ps1 -LogName s5_run1b.log -JavaOpts '-Dmixin.debug.export=true'
SERVER READY (launcher pid=…) log=D:\Minecraft-mod-development\cca移植neoforge\_tools\out\s5_run1b.log

[00:28:54] [Server thread/INFO] [minecraft/DedicatedServer]: Done (5.582s)! For help, type "help"
导出类总数（run\.mixin.out\class 递归文件数）：137
```

`_tools\out\s5_run1b.log` 致命模式扫描（均为 0）：

```
0	Mixin apply failed
0	Critical injection failure
0	[CCA-Mixin] SKIP
0	InvalidInjectionException
0	MixinTransformerError
```

### 3-3 逐 mixin 字节码取证（§4-4，9/9 OK，原文见 `_tools\out\s5_bytecode.txt`）

```
1 FallingBlockEntity.class                       handleFallDamageMixin            OK (2)
2 ComparatorBlock.class                          getStateForNeighborUpdateMixin   OK (2)
3 Bat.class                                      canSpawnMixin                    OK (2)
4 EnderMan$EndermanTakeBlockGoal.class           tickMixin                        OK (2)
5 EndGatewayBlock.class                          DimensionTransition.DO_NOTHING   OK (1)
6 Entity.class                                   getPortalManager                 OK (1)
7 Entity.class                                   invokeTickPortalTeleportation    OK (1)
8 Mob.class                                      tickMixin                        OK (2)
9 PortalProcessor.class                          getPortal                        OK (6)
```

第 5 条与第 9 条的原始 `javap` 片段（证明常量/访问器真的被织进去）：

```
  # EndGatewayBlock.getPortalDestination（导出后）
  55: getstatic     #230  // Field …DimensionTransition.PLACE_PORTAL_TICKET:…$PostDimensionTransition;
  63: invokespecial #234  // Method modify$zzf000$cca$createTeleportTargetMixin:(…)…$PostDimensionTransition;
  private …$PostDimensionTransition modify$zzf000$cca$createTeleportTargetMixin(…$PostDimensionTransition);
       6: getstatic     #285  // Field …DimensionTransition.DO_NOTHING:…$PostDimensionTransition;
```
→ **AT 已被证明生效**：`EnderMan$EndermanTakeBlockGoal` 是包私有嵌套类，AT 失效时第 4 条会在编译期就报 private 访问，且导出目录里不会有这个类（K7 的债在本轮结清）。

### 3-4 `/carpet` + `/cca list CCA`（§4-4 探针，原文见 `_tools\out\s5_rcon.txt`）

```
> python _tools\rcon_probe.py 127.0.0.1 25575 probe123 "carpet" "cca list CCA"
===== CMD: carpet =====
Current Carpet Mod settings:
Carpet Mod version: 1.4.147+v1.0.8-251027
Crystal Carpet Addition version: 1.11.1-neoforge
Browse categories:
[dispenser] [feature] [optimization] [scarpet] [survival] [client] [experimental] [TNT] [bugfix] [creative] [command]

===== CMD: cca list CCA =====
Crystal Carpet Addition settings matching "CCA" :
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
- ShulkerBoxPowerOutputExpansionColour [none] [white] [light_gray] [gray] [black] [brown] [red] [orange] [yellow] [lime] [green] [cyan] [light_blue] [blue] [purple] [magenta] [pink]
- StackableShulkerBoxesEnhancement [true] [false]
```
`- ` 开头行数 = **13**（13 → 13，本轮不增不减）。`MagicBox/CCESuppressor (MagicBox)` 是 K8 的已知显示别名，不是 bug。

### 3-5 13 条规则 toggle 冒烟（§4-5，`_tools\out\s5_toggle.txt`）

```
> .\_tools\serve.ps1 -LogName s5_run2.log
[00:34:35] [modloading-worker-0/INFO] [cr.cr.CrystalCarpetAdditionMod/]: [CCA] registered 13 rules
[00:34:47] [Server thread/INFO] [minecraft/DedicatedServer]: Done (5.541s)! For help, type "help"

CMD count     : 27
responses     : 25
invalid-value : 2
Unknown hits  : 0
0	Mixin apply failed
0	Critical injection failure
0	InvalidInjectionException
0	Exception
0	ERROR
0	[CCA-Mixin] SKIP
```
两条 `invalid-value` 都是枚举规则 `ShulkerBoxPowerOutputExpansionColour` 收到 `true`/`false` 时 carpet 的**正常校验回显**
（`Couldn't set value for rule …: Valid values for this rule are: [none, white, …]`），随后用合法值 `none` 设置成功
（`ShulkerBoxPowerOutputExpansionColour: none, [Change permanently?]`）→ 见 D-S5-1。

### 3-6 结构验收

```
cca.mixins.json 的 mixins 条目数 = 10（顺序见文件，client=[] / plugin 未动）
java 文件总数 = 28（s4 是 19）
```

## 4. 未解决 / 踩坑（错误原文 + 现象 + 已排查到什么程度）

### 4-1 【已解决】`-Dmixin.debug=true` 打崩 carpet 自带 mixin

- 现象：加了 `-Dmixin.debug=true` 后服务器在 mixin 阶段直接崩，`runServer` 永远到不了 `Done`。
- 原文：
  ```
  InvalidInjectionException: Constant modifier method modifyPlayerSampleLimit(I)I in carpet.mixins.json:MinecraftServer_pingPlayerSampleLimit
  was expected to find 1 invocation(s) but 0 succeeded. Scanned 0 target(s). No refMap loaded.
  → org.spongepowered.asm.mixin.throwables.MixinApplyError → MixinTransformerError
  ```
- 已排除：与 CCA 的 9 个新 mixin 无关（把 `mixins` 回退到 1 条同样复现）；也不是 classpath 问题（`No refMap loaded` 在 NeoForge 下是正常提示）。
- 结论：**只用 `-Dmixin.debug.export=true`**，导出完整性不受影响（137 个类）。见 D-S5-3。

### 4-2 【已解决】`PortalProcessor.class` 一开始没被导出（第 9 条取证 MISS/NO-FILE）

- 现象：导出目录 132 个类里没有 `net\minecraft\world\entity\PortalProcessor.class`；同时
  `execute as @e[type=zombie] run data get entity @s PortalCooldown` 恒为 `0`、`data get entity @s Pos` 恒停在被 tp 的坐标。
- 排查过程：
  1. 确认方块在：`data get block 0 64 112` → `{… id: "minecraft:end_portal"}`；
  2. 确认 `EndPortalBlock.entityInside` 存在且在偏移 103 调 `Entity.setAsInsidePortal`（`javap -p -c EndPortalBlock`）；
  3. 确认实体确实在 tick：`summon minecraft:tnt 8.5 64.5 112.5 {fuse:200s}` 后 3 秒读 `fuse` = `67s`；
  4. 定位差异：那只僵尸是 **`NoAI`** 的 → 不移动 → `Entity.move()` 不被调用 → `checkInsideBlocks()` 不跑 → `entityInside` 不触发。
- 修复：`forceload add 0 112 16 120`（先确认 `forceload query` 本来是 `No force loaded chunks were found in Overworld`）
  + `setblock 6 64 112 minecraft:nether_portal[axis=x]` + `summon minecraft:zombie 6.5 64.0 112.5`（**不带 NoAI**）。
  结果：`data get entity @s Pos` → `[2.0d, 59.0d, 14.5d]`、`PortalCooldown` → `300`（真的被传到下界），
  导出目录出现 `PortalProcessor.class`（4326 B）。见 D-S5-4。

### 4-3 【已解决】第 5 条取证扫二进制 MISS，扫 javap 文本 OK

- 现象：`Select-String -LiteralPath EndGatewayBlock.class -Pattern 'DimensionTransition.DO_NOTHING'` → `MISS`。
- 根因：常量池把类全名与字段名存成两个独立 UTF8 项，带点的串在 `.class` 里不存在。
- 处理：9 条取证统一改成 `& $J -p -c $f | Select-String -SimpleMatch <符号>` → 9/9 OK。见 D-S5-2。

### 4-4 备注（不是问题，但会咬人）

- `_tools\serve.ps1` 每次起服前删 `run\.mixin.out`，所以 **s5_run2 之后导出目录已空**（run2 没带 `-Dmixin.debug.export`）。
  要看导出字节码必须重跑：`.\_tools\serve.ps1 -LogName sN_run1.log -JavaOpts '-Dmixin.debug.export=true'`。
- `rcon_probe.py` 收到 `stop` 时抛 `ConnectionError: connection closed by server` 是**正常**（服务器已经关连接），判定看日志里 `Stopping the server`。
- `rcon_probe.py` 传参不要带内层双引号 NBT（`{Item:{id:"minecraft:stone"}}` 会被 PowerShell 拆碎报 `Invalid escape sequence '\,'`）；
  用不带引号的 SNBT 或干脆避开物品 NBT。

## 5. 下一阶段起点

- 第一个待办（s6）：5 个 mixin —— `rule\MagicBox\{BlockEntityMixin,LecternBlockMixin,ShulkerBoxBlockMixin,WorldChunkMixin}` + `rule\ItemShadowing\ScreenHandlerMixin`，`mixins` 数组 10 → 15。
- 必读文件：
  - `prompts\FACTS.md`（§4 Carpet/NeoForge 已核实 API、§8 已知坑）
  - `prompts\s4 验收结论与接口勘误`（K1~K12，尤其 K4 cast 擦除、K5 pwsh 5.1、K6/K11 取证手法）
  - `handoff\HANDOFF-s5.md`（本文件）+ `analysis\DEVIATIONS.md`（D-S5-1~4）
  - `_decomp\mojmap\crystal0404\crystalcarpetaddition\mixins\rule\MagicBox\*`、`…\ItemShadowing\ScreenHandlerMixin.java`
- 前置检查命令（确认起点没漂）：
  ```powershell
  (Get-Content cca-neoforge\src\main\resources\cca.mixins.json -Raw | ConvertFrom-Json).mixins.Count   # 期望 10
  (Get-ChildItem cca-neoforge\src\main\java -Recurse -Filter *.java | Measure-Object).Count            # 期望 28
  ```
- 起服/取证工具：`_tools\serve.ps1 -LogName s6_run1.log -JavaOpts '-Dmixin.debug.export=true'`；
  `python _tools\rcon_probe.py 127.0.0.1 25575 probe123 "<cmd1>" "<cmd2>"`（一次多条）+ `"stop"`。

## 6. 对 FACTS.md 的修正/新增（下一阶段请按这些覆盖 FACTS）

- `FACTS 第 4 节`（MixinExtras/调试）：**新增** —— `-Dmixin.debug=true` 会让 carpet 自带的
  `MinecraftServer_pingPlayerSampleLimit`（`@ModifyConstant`）0 匹配而抛 `InvalidInjectionException` → 崩服；
  起服只能用 `-Dmixin.debug.export=true`。（D-S5-3）
- `FACTS 第 4 节`：**新增** —— Mixin 导出只覆盖"本轮真的被加载过"的类。极少加载的类（`PortalProcessor` 等）
  必须造真实触发路径（实体真的进传送门并移动过），否则导出目录里没有它，取证会误判成 "mixin 没生效"。（D-S5-4）
- `K7`（AT 从未验证）：**本轮结清** —— `EnderMan$EndermanTakeBlockGoal` 编译通过 + 导出类里命中 `tickMixin`，
  证明 `src\main\resources\META-INF\accesstransformer.cfg` 两行在当前 MDG 配置下生效（`build.gradle` 仍未加 `accessTransformers.from`，**不需要加**）。
- `K11`（导出取证）：**修正** —— 不能用 `Select-String` 直接扫 `.class` 二进制找 `A.B` 形式的符号（常量池拆成两项）；
  必须 `javap -p -c` 后扫文本。（D-S5-2）
- `K12`（不必改 build 配置）：确认，`-Dmixin.debug.export` 通过 `serve.ps1 -JavaOpts` → `JAVA_TOOL_OPTIONS` 传进子 JVM 即可。
- **新增事实**：`Bat.checkBatSpawnRules` 的 `@ModifyExpressionValue` 处理器**必须 `static`**；`ComparatorBlock.updateShape`
  里 `canSurviveOn` 的字节码 owner 是 `ComparatorBlock`（不是声明类 `DiodeBlock`）；`EndGatewayBlock.getPortalDestination`
  的 `@ModifyArg` 索引仍是 `5`。
- **新增事实（工程状态）**：s5 结束时 java = 28，`mixins` = 10，jar 77,054 B / sources 63,637 B，规则基线 13。

## 7. 发现但本阶段未处理

- `_tools\serve.ps1` 会清空 `run\.mixin.out`，与 s4 任务书 §7 里"导出类会留着给下一阶段用"的预期不符 —— 视为**有意的**设计（避免旧字节码污染取证），已写进 §4-4 备注；若后续希望保留，需要改成按阶段子目录导出。
- 验证 `PortalProcessor` 时在下界生成了一只僵尸（`Pos [2.0,59.0,14.5]`），`run\saves\world` 里留下痕迹；对本工程无影响，s9 若要求干净存档需删 `cca-neoforge\run\saves`。
- 枚举规则在 toggle 脚本里会被 boolean 撞一下（D-S5-1）：s6 起写脚本时请按"12 boolean × true/false + 枚举合法值"分开处理。
- `CrystalCarpetAddition.onGameStarted()` 里那 13 行 `[CCA] rule <name>` 还在（D-S4-2），s9 全量回归时删。
- `run\.mixin.out` 当前为空（见 §4-4），s6 取证前需要重新导出。
