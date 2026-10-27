# HANDOFF s9 — 收尾与全量回归（**项目总交接**）

- 日期：2026-10-27
- 本阶段结论：**PASS** —— DoD 10 项**全部通过**。原 DoD#8 的「漏斗堆叠 A/B」首轮判 FAIL（根因已定位到 NeoForge 字节码层），
  经用户拍板采用**方案①（补补偿 mixin）**并在本阶段内修复、复测通过（见 §4.1）。修复使 `mixins` 21 → **22**、java 40 → **41**，
  DoD#2 的期望值相应由 21 改为 **22**（已获用户批准）。
- 工程根：`D:\Minecraft-mod-development\cca移植neoforge`；gradle 子工程 `cca-neoforge`
- 上游：CCA `1.11.1`（Fabric / MC 1.21.1）→ 本移植 **NeoForge `21.1.x` 全线（声明 `[21.1.0,)`，开发/验证于 `21.1.265`）** / MC `1.21.1`（声明 `[1.21.1,1.21.2)`）
- 本阶段是**最后一阶段**：产物已可发布（`dist\` 四件齐全），本文档即项目总交接

---

## 1. 已落地文件（路径 + 作用 + 行数）

### 1.1 本阶段新增 / 修改

| 路径 | 作用 | 行数 |
|---|---|---|
| `cca-neoforge\src\main\java\crystal0404\crystalcarpetaddition\mixins\rule\WhatIsThis\Happy.java` | **新增**。client 侧彩蛋 mixin：`@Mixin(SplashManager.class)` + `@Inject(method="getSplash", at=@At(value="INVOKE", target="Ljava/util/Calendar;setTime(Ljava/util/Date;)V", shift=AFTER), cancellable=true)`，3 月 25 日 1/3 概率换 splash 文案。类级 `@Restriction(conflict = @Condition(type = TESTER, tester = CCAUtils.DisableEasterEggs.class))`（**方向不能反**） | 46 |
| `cca-neoforge\src\main\java\crystal0404\crystalcarpetaddition\mixins\rule\StackableShulkerBoxesEnhancement\HopperBlockEntityEjectMixin.java` | **新增（方案① 漏斗修复）**。NeoForge 专属补偿 mixin，上游没有这个类。`@WrapOperation(method="ejectItems", at=@At(value="INVOKE", target="Lnet/neoforged/neoforge/items/VanillaInventoryCodeHooks;insertHook(Lnet/minecraft/world/level/block/entity/HopperBlockEntity;)Z"))`，四条条件（规则开 / `CarpetSettings.shulkerBoxStackSize != 1` / 目标 `instanceof Container` / 漏斗里有潜影盒）同时成立时**返回 `false` 且不调 `original`**，把控制权还给 vanilla 的 `addItem → tryMoveInItem → canMergeItems` 路径 | 133 |
| `cca-neoforge\src\main\resources\cca.mixins.json` | **`client` 数组**：`[]` → `["rule.WhatIsThis.Happy"]`；**`mixins` 数组 21 → 22**（在 `rule.StackableShulkerBoxesEnhancement.HopperBlockEntityMixin,` 之前插入 `...HopperBlockEntityEjectMixin,`） | 37 |
| `_tools\runclient.ps1` | **新增**。客户端启动器：备份 `run\logs\latest.log` → 删 `run\.mixin.out` → `gradlew.bat runClient --offline --console=plain` → 轮询 `Sound engine started\|Narrator library\|OpenAL initialized\|Created: \d+x\d+` 判 READY → 只杀本次新起且非 `GradleDaemon` 的 java → 立刻把 `run\logs\{latest,debug}.log` 拷到 `_tools\out\<LogName>.{latest,debug}.log` | 44 |
| `_tools\rcon_file.py` | **新增**。按 UTF-8 命令文件逐行发 RCON（每行一条，跳过空行与 `#`），解决 PowerShell 吃掉 SNBT 双引号的问题 | 52 |
| `analysis\STATIC-CHECK.md` | **新增**。四项静态检查定稿（含命令原文、输出、误报逐条说明） | 129 |
| `analysis\DEVIATIONS.md` | **追加** s9 小节：`D-S9-1`…`D-S9-6`（258 行 → **382** 行，原有内容一字未改；`D-S9-3` 在方案① 修复后改写为"已修复"） | +124 |
| `cca-neoforge\src\main\resources\assets\cca\icon.png` | **新增（图标补位）**。从上游 `CrystalCarpetAddition-v1.11.1-mc1.21.1.jar` 的 `assets/cca/icon.png` 逐字节复制，31,755 B，SHA1 `0A8062381B1BD10580124BDE3170526B09185224`。**与工程里原有的 `resources\icon.png` 完全同源同哈希** —— 那一份是 `neoforge.mods.toml` 的 `logoFile = "icon.png"` 指向的根路径副本，本行补的是上游原有布局，两处并存 | — |
| `cca-neoforge\src\main\resources\META-INF\mods.toml` | **新增（启动器兼容）**。内容与 `neoforge.mods.toml` 逐字节相同（构建后已验证 `same = True`）。用途：**PCL2 这类启动器的老解析器只认老式 Forge 路径 `META-INF/mods.toml`**，只有 `neoforge.mods.toml` 时它读不到 `displayName`/`logoFile`，列表里只显示文件名 + 默认立方体图标。NeoForge 21.1 的 `ModFileParser` 字面量只有 `"META-INF/neoforge.mods.toml"`（`javap` 确认无 `mods.toml`），所以这份**不会被 FML 读取**、不产生重复 mod —— 已实测 `s9_dualtoml.log`：`Duplicate mod` 命中 **0**、`[CCA] registered 13 rules`、`Done (4.966s)!` | 37 |
| `cca-neoforge\build.gradle` | **改 1 行**：`filesMatching(['META-INF/neoforge.mods.toml'])` → `filesMatching(['META-INF/neoforge.mods.toml', 'META-INF/mods.toml'])`，让新加的那份也走 `${}` 变量展开（否则 PCL2 会读到字面量 `${mod_name}`） | 77 |
| `dist\*` | **新增**：两个 jar + `README-安装说明.md` + `RULE-TEST.md` + `sha1.txt` | — |
| `handoff\HANDOFF-s9.md` | 本文件（项目总交接） | — |

### 1.2 全项目文件分布（41 个 java）

| 目录 | 数量 | 内容 |
|---|---|---|
| 根 | 2 | `CrystalCarpetAdditionMod`（NeoForge `@Mod` 入口）、`CCAExtension`（`CarpetExtension` 实现） |
| 根 | 1 | `CCASettings`（13 条 boolean + 1 条枚举 `ShulkerBoxPowerOutputExpansionColour`） |
| `utils\` | 6 | `CCAUtils`、`CCAMixinConfigPlugin`、`ModIds`、`Key`、`EmptyClass`、`LootTableUtils` |
| `utils\annotation\` + `impl\` + `condition\` | 5 | `Condition`（FIELD 级，给规则）、`Restriction`（FIELD 级）、`AnnotationProcessor`、`ConditionTester`、`MixinRestrictionProcessor` |
| `utils\annotation\mixin\` | 2 | `Condition`（TYPE 级，给 mixin 类）、`Restriction`（TYPE 级） |
| `utils\shulkerBoxUtils\` | 2 | `ColourMap`（含 `enum Colour`）、`ShulkerBoxesSet`（17 种潜影盒） |
| `mixins\`（server，22 条） | 22 | 见 `cca.mixins.json`（含方案① 新增的 `HopperBlockEntityEjectMixin`） |
| `mixins\rule\WhatIsThis\`（client，1 条） | 1 | `Happy` |

### 1.3 关键路径速查

```
工程根           D:\Minecraft-mod-development\cca移植neoforge
gradle 子工程    cca-neoforge            （build.gradle / gradle.properties 在此）
run 目录         cca-neoforge\run        （client 与 server 共用！见 §4.4）
构建产物         cca-neoforge\build\libs
发布产物         dist\
验收日志         _tools\out\s{1..9}_*.log / *.txt
工作文件         _work\
物料（只读）      _ref\  _decomp\  _mappings\  _src\
```

---

## 2. 本轮关键决策（含"为什么"）

### 2.1 `Happy` 放 `client` 数组，不放 `mixins`

- 决策：`cca.mixins.json` 里 `"client": ["rule.WhatIsThis.Happy"]`；`mixins` 数组在本决策当时保持 21 条不动（方案① 后为 22 条，那是另一件事）。
- 理由：`net.minecraft.client.resources.SplashManager` **只存在于客户端**。放进 `mixins`（server 数组）会让专用服务端在
  `NoClassDefFoundError: net/minecraft/client/resources/SplashManager` 上直接崩。s9 服务端回归日志零致命 + 零 SKIP 反证了它没进 server 数组。
- 备选：`@Mixin(targets = "…")` 字符串形式 —— 不需要，`SplashManager.class` 在 client 配置里可正常解析。

### 2.2 `method = "getSplash"` 而非上游的 `"get"`

- 决策：注入方法名写 `getSplash`。
- 理由：`_mappings\refmap_mojmap.md` 里 `rule/WhatIsThis/Happy` 只有一行
  `Lnet/minecraft/client/resources/SplashManager;getSplash()Lnet/minecraft/client/gui/components/SplashRenderer;`。
  上游 Fabric 源码里的 `get` 是 Yarn/中间名，Mojmap 下是 `getSplash`。
- 备选：无。写 `get` 会得到 `Mixin apply failed: target method not found`。

### 2.3 `calendar.get(2)/get(5)` → `Calendar.MONTH/MARCH/DAY_OF_MONTH`

- 决策：改成具名常量，语义等价、可读性更好。
- 理由：`Calendar.get(2)` 是 **0 基 MONTH**（2 = 三月），`get(5)` 是 `DAY_OF_MONTH`。直接写数字极易被误读成"12 月 25 日"（旧稿就错在这）。
- 备选：保留裸数字便于与上游逐字 diff —— 放弃，因为踩过一次坑。

### 2.4 类级注解写**全限定名**，不 import

- 决策：`@crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction(conflict = @crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition(...))`。
- 理由：项目里同时存在 **FIELD 级** `utils.annotation.Restriction`（给规则用）和 **TYPE 级** `utils.annotation.mixin.Restriction`（给 mixin 类用），
  import 二者之一都会让另一个需要全限定名；混用会静默走错注解（`@Target(TYPE)` vs `@Target(FIELD)` 不匹配时编译器报错，但读代码的人极易看错）。
- 备选：import TYPE 级的、把 FIELD 级的写全限定 —— 那样 `CCASettings` 里 13 条规则会变得很长，反向选择。

### 2.5 服务端探针（§4.9/§4.10/§4.11）排在客户端双启动（§4.7/§4.8）**之前**

- 决策：调整了任务书 §4 的执行顺序，先跑完服务端三条探针，再关服做客户端 A/B。
- 理由：客户端启动必须独占 `run\` 且会删 `.mixin.out`；服务端每起一次要 ~20s（含 `Done (7.5s)`）+ RCON 往返。
  服务端已在跑时顺路把三条探针做完，能省一次冷启动。
- 代价：证据文件名仍按任务书命名（`s9_witch_*` / `s9_hopper_*` / `s9_banner.txt`），但**真实时间顺序与本文件 §3 不同**。
- 备选：严格按 §4 顺序 —— 只是多一次冷启动，无功能差异。

### 2.6 漏斗规则先「不静默」，再由用户拍板走方案①

- 首轮决策：`HopperBlockEntityMixin` **保留在 `mixins` 数组里**（21 条），不删、不改、不加诊断代码进发布版。
- 理由：根因是 NeoForge 的 `VanillaInventoryCodeHooks.insertHook` 短路（§4.1），修它要么加第 22 个 mixin（`mixins` 变 22 / java 变 41），
  要么删掉（`mixins` 变 20）。**两种都改变 DoD#2 的计数**，必须由验收方拍板。硬约束 8 明确要求"不许自己删掉了不说"。
- **最终决策（用户已拍板）**：走**方案①** —— 新增 `HopperBlockEntityEjectMixin`，在 `ejectItems` 调用 `insertHook` 的位置
  `@WrapOperation`，条件成立时返回 `false` 让控制权回落 vanilla 路径。
  - 为什么是方案① 而不是②（`IItemHandler.insertItem` 层拦截）：② 会侵入所有 mod 的容器互操作，影响面不可控；① 只在本规则的适用条件下、只对 `instanceof Container` 的目标生效。
  - 为什么不选③（删掉不移植）：用户要求保留上游行为，且① 已验证可行。
  - 代价（已批准）：`mixins` 21 → **22**、`src/main/java` 40 → **41** 个文件；DoD#2 期望值改为 `mixins=22 / client=1`。
- **实现坑（务必记住）**：第一版写成 `boolean hooked = original.call(hopper);` 之后再检查 `hopper.getItem(i)` —— `original.call` **内部已经真的把物品搬走了**，
  检查时漏斗已空 ⇒ 永远返回 `true`。**必须先判条件、需要回退时根本不调用 `original`。**

### 2.7 诊断代码用完必须删干净

- 修复过程中往 mixin 里加过写文件的 `cca$dbg(String)`（写 `_tools/out/hopper_dbg.txt`）。验证通过后**整段删除**并重新构建，
  发布 jar 里不含任何诊断写文件逻辑（`Test-Path _tools\out\hopper_dbg.txt` 复跑后为 `False` 即证明）。
- 理由：发布产物不能带无关 IO；同时避免"诊断代码掩盖真实逻辑"的隐患。
- 备选：留在源码里加 `-Dcca.enable.debug` 开关 —— 不做，宁可不留。

---

## 3. 验收结果（命令 + 关键输出原文）

命令环境统一：

```powershell
$root='D:\Minecraft-mod-development\cca移植neoforge'
$py='C:\Users\xiao_\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
```

### 3.1 DoD#1/#2 计数

```powershell
(Get-ChildItem cca-neoforge\src\main\java -Recurse -Include *.java).Count
$m = Get-Content cca-neoforge\src\main\resources\cca.mixins.json -Raw | ConvertFrom-Json
"mixins=$($m.mixins.Count) client=$($m.client.Count)"
```

```
41
mixins=22 client=1
```

`plugin` / `injectors.defaultRequire` / `compatibilityLevel` 未动：

```
package      = crystal0404.crystalcarpetaddition.mixins
plugin       = crystal0404.crystalcarpetaddition.utils.CCAMixinConfigPlugin
compatibilityLevel = JAVA_21
injectors.defaultRequire = 1
```

### 3.2 DoD#3 构建

```powershell
Set-Location cca-neoforge
.\gradlew.bat build --offline --console=plain
```

```
BUILD SUCCESSFUL in 4s
```

```
Name                                                              Length
CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge-sources.jar  113094
CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar          130131
```

主 jar **130,131 B > 94,176 B**（s8 基线）✔；sources **113,094 B**（s8 `76,102 B`）✔。
（首轮 21 条版本 95,736 / 78,054 B；方案① 新增补偿 mixin 后 97,786 / 80,787 B；
补入上游图标 `icon.png` + `assets/cca/icon.png` 后 129,546 / 112,547 B —— 图标 31,755 B，两份同源同 SHA1；
再加 `META-INF/mods.toml`（PCL2 老解析器兼容）+ `description` 改单行后 130,135 / 113,108 B；
最后把 `neoforge` 依赖下界从 `[21.1.265,)` 放宽到 `[21.1.0,)` 后 130,131 / 113,094 B。）

> `--offline` 关键：在线一次 7m56s，离线 3–8s。**每次构建都加 `--offline`**。

### 3.3 DoD#4/#5 服务端回归 + 13 条规则开关

启动（`-Dmixin.debug.export=true` 只为导出 `.mixin.out`）：

```powershell
& _tools\serve.ps1 -LogName s9_run1.log -JavaOpts '-Dmixin.debug.export=true' -TimeoutSec 300
```

```
SERVER READY (launcher pid=10120) log=...\_tools\out\s9_run1.log
```

日志关键行（`_tools\out\s9_run1.log`）：

```
[23:37:36] [modloading-worker-0/INFO] [cr.cr.CrystalCarpetAdditionMod/]: [CCA] registered 13 rules
[CCA] version=1.11.1-neoforge CCA-category rules=13
[23:37:54] [Server thread/INFO] [minecraft/DedicatedServer]: Done (7.563s)! For help, type "help"
Carpet: NeoForged 1.4.147+v1.0.8-251027 (carpet)
```

致命模式扫描（期望 0）：

```powershell
(Select-String -Path _tools\out\s9_run1.log -Pattern 'Mixin apply failed|Critical injection failure|InvalidInjectionException|MixinTransformerError|NoSuchMethodError|NoSuchFieldError').Count
```

```
0
```

`[CCA-Mixin] SKIP` 行数（服务端期望 0，因为唯一带 TESTER 条件的 `Happy` 在 client 数组）：

```powershell
(Select-String -Path _tools\out\s9_run1.log -Pattern 'CCA-Mixin').Count
```

```
0
```

RCON 基线（`_tools\out\s9_rcon_base.txt`）：

```powershell
& $py _tools\rcon_probe.py 127.0.0.1 25575 probe123 "carpet" "cca list CCA"
```

- `carpet` 回包含 `Crystal Carpet Addition version: 1.11.1-neoforge` ✔
- `cca list CCA` 回 **13 条** `- ` 行 ✔：
  `AnvilCanCrushItemEntities` / `ComparatorIgnoresStateUpdatesFromBelow` / `EnderDragonPartCanUseEndPortal` /
  `EndermanCannotPickUpBlocksInNether` / `GatewayCannotLoadingChunks` / `ItemShadowing` /
  `MagicBox/CCESuppressor (MagicBox)` / `NoBatSpawn` / `ReIntroduceOldVersionRaid` /
  `ReIntroduceOldVersionWitchLootTable` / `ShulkerBoxPowerOutputExpansion` /
  `ShulkerBoxPowerOutputExpansionColour` / `StackableShulkerBoxesEnhancement`

26 条开关（`_tools\out\s9_toggle.txt`）：12 条 boolean × `true`/`false` 全部回显 `<Rule>: true, [Change permanently?]` / `: false, [Change permanently?]`。

枚举规则的两条"正常拒绝"（`D-S5-1` 活证据）：

```
> cca ShulkerBoxPowerOutputExpansionColour true
Couldn't set value for rule ShulkerBoxPowerOutputExpansionColour: Valid values for this rule are:
[none, white, light_gray, gray, black, brown, red, orange, yellow, lime, green, cyan, light_blue, blue, purple, magenta, pink]

> cca ShulkerBoxPowerOutputExpansionColour pink
ShulkerBoxPowerOutputExpansionColour: pink, [Change permanently?]
```

**条件注册仍生效的活证据**（必须保留）：

```
> cca RemoveVillagerTradeDistanceLimit true
Unknown rule: RemoveVillagerTradeDistanceLimit
```

该规则 `@Restriction(require = @Condition(value="minecraft", versionPredicates=">=1.21.4"))`，1.21.1 上条件不成立 ⇒ 不注册。
`/cca list CCA` 里也没有它 ⇒ 13 条而非 14 条。

### 3.4 DoD#6 client 启动 ①（默认参数 → 期望 SKIP）

```powershell
& _tools\runclient.ps1 -LogName s9_client1.log -JavaOpts '-Dmixin.debug.export=true' -TimeoutSec 600
```

```
CLIENT READY  log=...\_tools\out\s9_client1.log
```

SKIP 行（**命中**，`_tools\out\s9_client1.log` 与 `s9_client1.log.latest.log` 两份都有）：

```
[00:14:21] [main/INFO] [CCA-Mixin-Restriction/]: [CCA-Mixin] SKIP crystal0404.crystalcarpetaddition.mixins.rule.WhatIsThis.Happy (conflict hit: TESTER:crystal0404.crystalcarpetaddition.utils.CCAUtils$DisableEasterEggs)
```

```powershell
Test-Path run\.mixin.out\class\net\minecraft\client\resources\SplashManager.class
& 'C:\Program Files\Java\jdk-21.0.11\bin\javap.exe' -p -c run\.mixin.out\class\net\minecraft\client\resources\SplashManager.class | Select-String 'happyMixin'
```

```
False
(无输出)
```

**⚠ 判据偏差**：任务书 DoD#6 期望"`SplashManager.class` **存在**但 javap 无 `happyMixin`"。
实测 SKIP 时该类**不导出**——`shouldApplyMixin` 返回 false 后 Mixin 把 `SplashManager` 从目标列表移除，类不再被 transformer 触碰，
`-Dmixin.debug.export=true` 也就不写这个 `.class`。导出类计数佐证：启动 ① = **122** 个 `.class`，启动 ② = **123** 个，差的正是 `SplashManager.class`。
⇒ 判据改为"SKIP 行 + 导出计数 122"，**语义等价且更强**。详见 `analysis\DEVIATIONS.md` 的 `D-S9-1`。

### 3.5 DoD#7 client 启动 ②（`-Dcca.disable.EasterEggs=false` → 期望 APPLIED）

```powershell
& _tools\runclient.ps1 -LogName s9_client2.log -JavaOpts '-Dmixin.debug.export=true -Dcca.disable.EasterEggs=false' -TimeoutSec 600
```

```
CLIENT READY  log=...\_tools\out\s9_client2.log
```

**无任何 SKIP 行**（`Select-String -Pattern 'CCA-Mixin|WhatIsThis.Happy'` 空输出）✔

`-D` 生效证据：

```
Picked up JAVA_TOOL_OPTIONS: -Dmixin.debug.export=true -Dcca.disable.EasterEggs=false
[00:15:36] [main/WARN] [cr.cr.CrystalCarpetAdditionMod/]: [CCA] -Dcca.disable.EasterEggs=false
```

字节码命中（**关键判据**）：

```powershell
& 'C:\Program Files\Java\jdk-21.0.11\bin\javap.exe' -p -c run\.mixin.out\class\net\minecraft\client\resources\SplashManager.class | Select-String 'happyMixin|Calendar.setTime'
```

```
      12: invokevirtual #206                // Method java/util/Calendar.setTime:(Ljava/util/Date;)V
      29: invokespecial #216                // Method handler$zbc000$cca$happyMixin:(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;Ljava/util/Calendar;)V
  private void handler$zbc000$cca$happyMixin(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable, java.util.Calendar);
```

⇒ `handler$zbc000$cca$happyMixin` 紧跟在 `Calendar.setTime` 之后（`shift = AFTER` 精确命中，**不用写 ordinal**），
且 handler 签名第二参是 `java.util.Calendar` ⇒ `@Local(ordinal=0) Calendar` 解析成功。
常量池编号（`#206`/`#216`/`zbc000`）**每次编译都会变**，不能当判据（勘误 N11）；判据是"`handler$…$cca$happyMixin` 与 `Calendar.setTime` 相邻"。

### 3.6 DoD#8 三条遗留探针

#### 3.6.1 女巫掉落表 A/B —— **PASS**

环境（`_tools\out\s9_witch_setup.txt`）：

```powershell
& $py _tools\rcon_probe.py 127.0.0.1 25575 probe123 `
  "fill -3 -64 -3 3 -50 3 minecraft:air" `
  "setblock 0 -60 0 minecraft:chest" `
  "setblock 0 -59 0 minecraft:hopper[facing=down]" `
  "gamerule doMobLoot true" "difficulty easy"
```

```
Successfully filled 733 block(s)
```

> **必须挖空**：任务书给的 `0 -60 0` 落在 y=-58 的深板岩层，箱子/漏斗会被埋掉。
> 清箱用两步：`setblock 0 -60 0 minecraft:air` → `setblock 0 -60 0 minecraft:chest`（同状态 `setblock` 会回 `Could not set the block` 且箱子内容不清空）。

**A（规则关，`_tools\out\s9_witch_off.txt`）**：

```powershell
& $py _tools\rcon_probe.py 127.0.0.1 25575 probe123 `
  "cca ReIntroduceOldVersionWitchLootTable false" `
  "setblock 0 -60 0 minecraft:chest" `
  "summon minecraft:witch 0.5 -58.0 0.5 {NoAI:1b}" `
  "kill @e[type=minecraft:witch]"
Start-Sleep -Seconds 5
& $py _tools\rcon_probe.py 127.0.0.1 25575 probe123 "data get block 0 -60 0 Items"
```

```
Summoned new Witch
Killed Witch
0 -60 0 has the following block data: [{count: 1, Slot: 0b, id: "minecraft:gunpowder"}, {count: 5, Slot: 1b, id: "minecraft:redstone"}]
```

⇒ 红石 `count = 5 ≥ 4`，**只可能来自原版双 pool 表**（pool2 = `redstone`、`rolls 1.0`、`set_count 4..8`，勘误 N7）。

**B（规则开，`_tools\out\s9_witch_on.txt`，跑 3 次）**：

```
=== run 1 ===
0 -60 0 has the following block data: [{count: 2, Slot: 0b, id: "minecraft:glass_bottle"}]
=== run 2 ===
0 -60 0 has the following block data: [{count: 1, Slot: 0b, id: "minecraft:redstone"}]
=== run 3 ===
0 -60 0 has the following block data: [{count: 2, Slot: 0b, id: "minecraft:sugar"}, {count: 2, Slot: 1b, id: "minecraft:spider_eye"}]
```

⇒ 红石全程 `≤ 3`（或缺席），符合我方 `LootTableUtils.Witch` 的单 pool 结构 ⇒ **规则端到端有效**。

> 勘误 N6 已推翻旧方案 `loot insert … loot minecraft:entities/witch`：`loot` 命令直接取 `LootTable`，
> 不经过 `LivingEntity.dropFromLootTable`，永远返回原版表，判据作废。**必须真打死。**

#### 3.6.2 漏斗堆叠 A/B —— **首轮 FAIL，方案① 后 PASS**

**首轮（21 条 mixins 版本，A/B 无差异）**：

```powershell
& $py _tools\rcon_probe.py 127.0.0.1 25575 probe123 `
  "carpet stackableShulkerBoxes true" `
  "fill 6 -64 -2 10 -55 2 minecraft:air" `
  "setblock 8 -61 0 minecraft:chest" `
  "setblock 8 -60 0 minecraft:hopper[facing=down]" `
  "setblock 8 -59 0 minecraft:hopper[facing=down]"
```

**A（`cca StackableShulkerBoxesEnhancement false`，`_tools\out\s9_hopper_off.txt`）**：

```
Replaced a slot at 8, -59, 0 with [Shulker Box]     ← container.0
Replaced a slot at 8, -59, 0 with [Shulker Box]     ← container.1
（8s 后）8 -61 0 has the following block data: [{count: 2, Slot: 0b, id: "minecraft:shulker_box"}]
```

**B（`cca StackableShulkerBoxesEnhancement true`，`_tools\out\s9_hopper_on.txt`）**：

```
（同套命令）8 -61 0 has the following block data: [{count: 2, Slot: 0b, id: "minecraft:shulker_box"}]
```

⇒ **首轮 A/B 完全一致 = mixin 行为未生效。** 根因与三个候选见 §4.1。

**方案① 修复后的复测（22 条 mixins 版本，`_tools\out\s9_hopper_final_{off,on}.txt`）**：

```powershell
& $py _tools\rcon_file.py 127.0.0.1 25575 probe123 _work\hopfinal_setup.txt
#   carpet stackableShulkerBoxes true
#   forceload add 6 -64 10 3
#   fill 6 -64 -2 10 -55 3 minecraft:air
#   fill 6 -64 -2 10 -64 3 minecraft:stone
#   setblock 8 -61 0 minecraft:chest
#   setblock 8 -60 0 minecraft:hopper[facing=down]
#   setblock 8 -59 0 minecraft:hopper[facing=down]
```

```
=== A: cca StackableShulkerBoxesEnhancement false ===
StackableShulkerBoxesEnhancement: false, [Change permanently?]
8, -61, 0 has the following block data: [{count: 2, Slot: 0b, id: "minecraft:shulker_box"}]

=== B: cca StackableShulkerBoxesEnhancement true ===
StackableShulkerBoxesEnhancement: true, [Change permanently?]
8, -61, 0 has the following block data: [{count: 1, Slot: 0b, id: "minecraft:shulker_box"}, {count: 1, Slot: 1b, id: "minecraft:shulker_box"}]
```

⇒ **A 合并成一格 `count:2`，B 分成两格各 `count:1` —— 规则生效，DoD#8b 判定 PASS。**
`carpet stackableShulkerBoxes` 是独立配置，**每次重启服务端都要重设**（否则 `shulkerBoxStackSize==1`，mixin 直接放行）。

#### 3.6.3 旗帜 → BAD_OMEN —— **FAIL（可选项，已落档 `D-S9-4`）**

4 轮行为探针 + 5 轮构造尝试全部失败，最后一次（y=200 高空避开村庄）原文：

```
> item replace entity @e[type=minecraft:pillager,limit=1] armor.head with minecraft:white_banner[minecraft:banner_patterns=[{pattern:"rhombus",color:"cyan"},{pattern:"stripe_bottom",color:"light_gray"},{pattern:"stripe_center",color:"gray"},{pattern:"border",color:"light_gray"},{pattern:"stripe_middle",color:"black"},{pattern:"half_horizontal",color:"light_gray"},{pattern:"circle",color:"light_gray"},{pattern:"border",color:"black"}],minecraft:hide_additional_tooltip={},minecraft:item_name='{"translate":"block.minecraft.ominous_banner"}']
Replaced a slot on Pillager with [Ominous Banner]

> execute as BOTB at @s run damage @e[type=minecraft:pillager,limit=1] 100 minecraft:player_attack by BOTB
Applied 100.0 damage to Pillager

> data get entity BOTB active_effects
Found no elements matching active_effects
```

`Raider.class` 导出证明 mixin 已应用：

```
69: invokespecial #xxx   // Method handler$zbb000$cca$onDeathMixin:(Lnet/minecraft/world/damagesource/DamageSource;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V
```

（`super.die` 的调用在其后 ⇒ handler 在 HEAD 侧、前置执行。）

### 3.7 DoD#9 四项静态检查

详见 `analysis\STATIC-CHECK.md`，结论：

| # | 检查项 | 命中 | 判定 |
|---|---|---|---|
| 1 | intermediary 残留 `class_\d+\|method_\d+\|field_\d+` | 0 | PASS |
| 2 | `remap = false` | 0 | PASS |
| 3 | `net.fabricmc` / `me.fallenbreath` 非注释 | 0 | PASS |
| 4 | Yarn 名残留非注释 | 0（3 条子串误报，19 条 `World` 全为 `Lnet/minecraft/world/…` 描述符路径或形参名 `world`） | PASS |

第 4 项三处误报原文（**不是缺陷，勿"修"**）：

```
ScreenHandlerMixin.java:24:       public abstract class ScreenHandlerMixin {
BadOmenStatusEffectMixin.java:23: public abstract class BadOmenStatusEffectMixin {
RaidMixin.java:27:                private Holder<MobEffect> startMixin_getStatusEffect(Holder<MobEffect> original) {
```

前两个是**类名**（硬约束 6 要求与 `_decomp/mojmap` 完全一致），第三个是**方法名**；正文类型全部已是 Mojmap。

### 3.8 DoD#10 交付物

```
dist\
├── CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar         130,131 B
├── CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge-sources.jar 113,094 B
├── README-安装说明.md
├── RULE-TEST.md
└── sha1.txt
```

```
1C28F0AE915E0A4A83BD0B4B76C4CB582B8793F6  CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar
D256CEB4F89D6317DEE338AA9DEEA05D058E74E3  CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge-sources.jar
```

### 3.9 干净收尾（原文）

```powershell
& $py _tools\rcon_probe.py 127.0.0.1 25575 probe123 `
  'cca ReIntroduceOldVersionRaid false' 'cca StackableShulkerBoxesEnhancement false' `
  'carpet stackableShulkerBoxes false' 'player BOTB kill' 'forceload remove all' 'stop'
```

```
ReIntroduceOldVersionRaid: false, [Change permanently?]
StackableShulkerBoxesEnhancement: false, [Change permanently?]
stackableShulkerBoxes: false, [Change permanently?]
Unmarked all force loaded chunks in Overworld
Stopping the server
```

```powershell
(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match 'fml.modFolders' }).Count
```

```
0
```

### 3.10 s9 DoD 逐项判定汇总

| # | 判据 | 期望 | 实测 | 判定 |
|---|---|---|---|---|
| 1 | java 数 | 40 → **41**（+方案① 补偿 mixin） | **41** | ✔ |
| 2 | `mixins` / `client` | 21 → **22** / 1 | **22 / 1** | ✔ |
| 3 | `build --offline` | SUCCESSFUL，jar > 94,176 B | **4s，130,131 B**（sources 113,094 B；含图标 31,755 B + 双份 mods.toml） | ✔ |
| 4 | 服务端回归 | 13 rules / version / 13 行 / Done / 致命 0 / SKIP 0 | 全命中（`s9_hopper_final.log`：致命 0、SKIP 0、`[CCA] registered 13 rules`、`CCA-category rules=13`、`Done (7.167s)`） | ✔ |
| 5 | 13 条规则 × true/false | 26 条回显，无 Unknown rule | 全命中（+2 条枚举拒绝 + `Unknown rule: RemoveVillagerTradeDistanceLimit`） | ✔ |
| 6 | client ① 默认 | SKIP 行 + 无 `happyMixin` | SKIP **命中**；`SplashManager.class` **不存在**（判据偏差，导出 122 vs 123 等价证明）；javap 无输出 | ✔（判据按 §3.4 修正） |
| 7 | client ② `-D…=false` | 无 SKIP + 命中 `happyMixin` | 无 SKIP；`29: invokespecial handler$zbc000$cca$happyMixin` 紧跟 `12: Calendar.setTime` | ✔ |
| 8a | 女巫掉落 A/B | 必做 | A 红石 `count=5`，B 三次全 `≤3` | ✔ |
| 8b | 漏斗堆叠 A/B | 必做 | 首轮 A/B 一致（FAIL）→ **方案① 后 A `count:2` 合并 / B 两槽各 `count:1` 不合并** | ✔ **PASS（修复后）** |
| 8c | 旗帜 → BAD_OMEN | 可选 | 未取得端到端证据，记 `D-S9-4` | — 按可选项处理 |
| 9 | 四项静态检查 | 全 0（注释不算） | **41 文件复跑**：0 / 0 / 0 / 0（5 条朴素子串 = 2 条 ported 注释 + 3 条类名/方法名，已逐条说明） | ✔ |
| 10 | 交付物 | DEVIATIONS 补章 + dist 四件 + 总交接 | 全齐（jar SHA1 `1C28F0AE915E0A4A83BD0B4B76C4CB582B8793F6`、sources `D256CEB4F89D6317DEE338AA9DEEA05D058E74E3`） | ✔ |

---

## 4. 未解决 / 踩坑（错误原文 + 现象 + 已排查到什么程度）

### 4.1 `StackableShulkerBoxesEnhancement` 在 NeoForge 上永不生效 —— **已定位根因并用方案① 修复**

- **现象（首轮）**：`/cca StackableShulkerBoxesEnhancement` 开/关，漏斗合并潜影盒的结果完全一致（箱子槽 0 恒为 `count: 2`）。
- **已排除**（三项全部实测）：
  1. 注入没成功 —— **否**。`run\.mixin.out\class\net\minecraft\world\level\block\entity\HopperBlockEntity.class` 的
     `tryMoveInItem` 偏移 **73** 有 `invokestatic wrapOperation$zzm000$cca$transferMixin(ItemStack, ItemStack, Operation)Z`；
     handler 体条件链与源码逐字一致（`getstatic CCASettings.StackableShulkerBoxesEnhancement` → `getstatic CarpetSettings.shulkerBoxStackSize` →
     `iconst_1` → `if_icmpeq` → `ITEMS_SET.contains(first.getItem())` → `iconst_0 ireturn`）。
  2. 规则开关没生效 —— **否**。`/cca StackableShulkerBoxesEnhancement` 回 `current value: true (modified value)`；
     同机制的 `ReIntroduceOldVersionWitchLootTable` 端到端有效 ⇒ `/cca` 命令 → 静态字段链路正常。
  3. `shulkerBoxStackSize == 1` 导致 mixin 直接放行 —— **否**。`carpet stackableShulkerBoxes true` 时
     `CarpetSettings$StackableShulkerBoxValidator` 走 `"true".equalsIgnoreCase` 分支、`bipush 64 putstatic shulkerBoxStackSize`；
     实测 `item replace … container.5 with minecraft:shulker_box 3` 后得到 `count: 5` ⇒ `maxStack ≥ 5`。
- **根因（决定性字节码，`javap -p -c` 于 `run\.mixin.out` 导出的 `HopperBlockEntity.class`）**：
  `ejectItems(Level, BlockPos, HopperBlockEntity)` 偏移
  **`35: aload_2` `36: invokestatic net/neoforged/neoforge/items/VanillaInventoryCodeHooks.insertHook(HopperBlockEntity)Z` `39: ifeq 44` `42: iconst_1` `43: ireturn`**。
  `insertHook` 内部：`getAttachedItemHandler(...).map(hopper -> ...).orElse(Boolean.valueOf(false)).booleanValue()`；
  NeoForge 给所有原版 `Container` 都挂了 `IItemHandler` capability ⇒ 目标容器只要暴露该能力就**返回 true** ⇒ `ejectItems` 直接 `ireturn`。
  vanilla 的后续路径（`44-` `getAttachedContainer` → 循环 → 偏移 **123** `addItem` → `tryMoveInItem` → `canMergeItems`）**整段被跳过**。
  ⇒ 上游 CCA 靠 wrap `canMergeItems` 实现的规则，在 NeoForge 上**没有调用点**。这不是配置问题，是移植缺陷。
- **修复（方案①，用户已拍板，已实施并验证）**：新增
  `cca-neoforge\src\main\java\crystal0404\crystalcarpetaddition\mixins\rule\StackableShulkerBoxesEnhancement\HopperBlockEntityEjectMixin.java`：
  ```java
  @WrapOperation(method = "ejectItems",
          at = @At(value = "INVOKE",
                   target = "Lnet/neoforged/neoforge/items/VanillaInventoryCodeHooks;insertHook(Lnet/minecraft/world/level/block/entity/HopperBlockEntity;)Z"))
  private static boolean cca$fallbackToVanillaPath(HopperBlockEntity hopper, Operation<Boolean> original) {
      if (cca$shouldFallbackToVanilla(hopper)) {
          return false;                              // 不调 original，交给 vanilla addItem 路径
      }
      return original.call(hopper);
  }
  ```
  `cca$shouldFallbackToVanilla` 四条同时成立才回退：① `CCASettings.StackableShulkerBoxesEnhancement`；
  ② `CarpetSettings.shulkerBoxStackSize != 1`；③ 目标方块实体 `instanceof Container`；④ 漏斗 5 槽中确有 `ShulkerBoxesSet.ITEMS_SET` 里的潜影盒。
- **修复过程中踩的坑（错误顺序，必记）**：第一版写成
  ```java
  final boolean hooked = original.call(hopper);   // ← 错：这里已经真的把物品搬走了
  ... if (漏斗里有潜影盒) return false;
  ```
  诊断输出：`EJECT called: hooked=true rule=true stackSize=64 pos=BlockPos{x=8, y=-60, z=0}` / `-> keep true (no shulker box in hopper)`
  ⇒ 检查时漏斗已被搬空，永远返回 `true`。**必须先判条件、需要回退时根本不调用 `original`。**
- **修复后复测（`_tools\out\s9_hopper_final_{off,on}.txt`）**：
  - A（规则关）：`[{count: 2, Slot: 0b, id: "minecraft:shulker_box"}]`（合并，原行为不变）
  - B（规则开）：`[{count: 1, Slot: 0b, ...}, {count: 1, Slot: 1b, ...}]`（不合并）
- **残留风险（已知边界，不进一步侵入）**：目标是"只暴露 `IItemHandler`、不是 `Container`"的 mod 容器时我们不回退，
  这类容器上的漏斗仍会合并潜影盒。已写入 `analysis\DEVIATIONS.md` 的 `D-S9-3`。
- **使用前提**：`/carpet stackableShulkerBoxes true` 必须同时开着（carpet 配置**重启后可能回到 `false`，每次都要重设**），
  否则 `shulkerBoxStackSize == 1`，我们的 mixin 与上游一样直接放行。
- **决策经过（三候选，用户选了①）**：
  1. ✅ **已采用**：补一个 `@WrapOperation` 包 `VanillaInventoryCodeHooks.insertHook`：规则开 + 潜影盒 + `shulkerBoxStackSize != 1`
     ⇒ 返回 `false`，把控制权还给 vanilla 路径，使既有 `transferMixin` 生效。
     - 优点：最小侵入，不动已有 21 条；只加 1 个 mixin 类。
     - 代价（已批准）：`mixins` 21 → **22**、java 40 → **41**。
  2. 未采用：改在 `IItemHandler.insertItem` 层拦截。侵入面最大，影响所有 mod 互操作，与上游语义偏离最远。
  3. 未采用：标记为本版不移植（`mixins` → 20）。会失去一条上游规则的能力。
- **当前状态**：**已修复并验证通过**（见本节上文）。`mixins` = **22**，`HopperBlockEntityMixin`（上游那条）与 `HopperBlockEntityEjectMixin`（补偿那条）**两条都在位**，
  前者负责"禁止 `canMergeItems`"，后者负责"让 vanilla 路径重新可达"。**删掉任何一条规则都会失效。**
- **同步更新过的地方**：`cca.mixins.json` 的 `mixins` 数组、`analysis\DEVIATIONS.md` 的 `D-S9-3`、`analysis\STATIC-CHECK.md`（40 → 41）、
  `dist\README-安装说明.md` §5/§6/§7、`dist\RULE-TEST.md` 第 13 条、`dist\sha1.txt`、本节与 §2.6/§3.2/§3.6.2/§3.10。

### 4.2 `ReIntroduceOldVersionRaid.RaiderEntityMixin` 的旗帜判定只有字节码级证据（`D-S9-4`）

- **现象**：带完整组件（8 层 `banner_patterns` + `hide_additional_tooltip` + `item_name`）的灾厄旗帜装在 `PatrolLeader:1b` 的 pillager 头上，
  玩家击杀后 `data get entity BOTB active_effects` 恒为 `Found no elements matching active_effects`。
- **已排除**：`gamerule disableRaids=false`、`doMobLoot=true`、规则 `true`、6 层版本同样失败、y=200 高空（避开村庄 `getRaidAt`）同样失败、
  `PersistenceRequired:1b` + 玩家 `tp` 到旁边（排掉 despawn，见 §4.3）。
- **handler 条件链（已从字节码逐条核对）**：`CCASettings.ReIntroduceOldVersionRaid` → `isPatrolLeader()` →
  `getCurrentRaid() == null` → `getRaidAt(blockPosition()) == null` → `getItemBySlot(HEAD)` 非空 →
  `cca$getPlayerEntity(damageSource.getEntity())` → `cca$hasBanner(ItemStack)` → `MobEffects.BAD_OMEN` 累加 →
  `GameRules.RULE_DISABLE_RAIDS` → `Player.addEffect`。
- **候选失败原因**：
  (a) `summon … {PatrolLeader:1b}` 是否同步到 `isPatrolLeader()` 读的字段未验证；
  (b) `cca$hasBanner` 用 `ItemStack.isSameItemSameComponents`，而 `item_name` 经 SNBT **只能写成字符串**（`item_name={translate:…}` 报 `Malformed 'minecraft:item_name' component: 'Not a string'`），
      与 `Component.translatable(...)` 的相等性未证实；
  (c) `damage … by <entity>` 构造的 `DamageSource.getEntity()` 是否等于该玩家未证实。
- **下一步建议（若要继续）**：在 `handler$zbb000$cca$onDeathMixin` 里加**临时写文件诊断**（每个条件分支写一行到 `_tools\out\`）。
  注意：本轮验证过，`System.out.println` / log4j / 写文件在 **handler 未被调用**时都是 0 输出——只要 handler 被调用，写文件是可行的。
- **未做**：放宽 `isSameItemSameComponents` 比较去凑结果 —— 明令禁止（会破坏规则语义）。

### 4.3 环境坑（都在本轮踩过，别再踩）

| 坑 | 现象 | 处置 |
|---|---|---|
| **`_ref\mc-1.21.1-client.jar` 只有 assets/data，没有 class** | `jar tf` 23519 条，`net/minecraft/world/entity/raid/` 无输出 | 查 MC 成员一律用 `C:\Users\xiao_\.gradle\caches\neoformruntime\intermediate_results\compiledWithNeoForge_0166c820696cc2f2876873532bdc8ee0a3eded51_output.jar` |
| **勘误 N8 的旗帜 SNBT 不可用** | `pattern:"rhombus_middle"` 报 `Failed to get element ResourceKey[minecraft:banner_pattern / minecraft:rhombus_middle]` | 命令注册名是 **`rhombus` / `circle`**（不是 `BannerPatterns` 常量名） |
| **`item_name` 组件 SNBT 只接受字符串** | `item_name={translate:"…"}` 报 `Malformed 'minecraft:item_name' component: 'Not a string'` | 写 `item_name='{"translate":"block.minecraft.ominous_banner"}'` |
| **carpet 假人名规范成全大写** | `player Botb spawn` 之后 `list` 回 `BOTB`、`data get entity Botb Pos` 回 `BOTB has the following entity data: …` | 引用一律用**全大写**（勘误 N4 说"首字母大写"不准确） |
| **实体 despawn 距离 128** | summon 成功但 `@e[type=minecraft:pillager]` 查不到 | 玩家 `tp` 到旁边，或实体加 `PersistenceRequired:1b` |
| **`fill … -64 … air` 会挖掉基岩层** | 实体掉进虚空被删 | 事后 `fill 6 -64 -2 10 -64 2 minecraft:stone` 铺回地板 |
| **`data get entity <mob> equipment` 在 1.21.1 不存在** | — | 用 `ArmorItems`（索引 3 = HEAD） |
| **`forceload add` + 重操作同批 ⇒ RCON 断连** | `Can't keep up! … Running 10122ms or 202 ticks behind` + `ConnectionAbortedError: [WinError 10053]` | 单批 ≤ 6 条，`forceload` 单独发 |
| **PS 吃掉 SNBT 双引号** | `rcon_probe.py` 收到的命令引号错乱 | 用 `_tools\rcon_file.py <cmdfile>`（新增） |
| **`.mixin.out` 只导出被 mixin 目标命中的类** | SKIP 的 mixin 目标类**不导出** | 判据用"导出计数差 1"代替"文件存在但无方法" |

### 4.4 client 与 server 共用 `run\` 目录（勘误 N10 的实操）

- `cca-neoforge\run\` 下 `logs\`、`.mixin.out\`、`config\`、`mods\`、`world\` 全部共享。
- 客户端启动会**改写** `run\logs\latest.log` 并**重建** `run\.mixin.out`。
- `_tools\runclient.ps1` 已内置处置：先备份 `run\logs\latest.log` → `_tools\out\prev_server_latest.log`，启动完立刻把 `latest.log`/`debug.log` 拷成
  `_tools\out\<LogName>.latest.log` / `.debug.log`（实测两次启动都成功保住了证据）。
- **服务端日志的权威副本**在 `_tools\out\s9_run1.log`（`serve.ps1` 捕获的 stdout），不受客户端影响。

### 4.5 `_tools\rcon_probe.py` 读循环丢包（勘误 N1，仍未修）

- 现象未变：`say` 信标在服务端日志里有、RCON 回包里空。
- **工作法**：RCON 只发命令，判据一律回读日志（`Select-String -Path _tools\out\<log> -Pattern '…'`），关键量用 `data get` 双通道。
- 修脚本仍是可选任务。

---

## 5. 下一阶段起点

> 本阶段是最后一阶段。DoD 10 项**全部通过**，项目处于可发布状态，**没有下一阶段**；以下仅列出"若要继续"时的入口。

- **第一件事（若继续）**：按 §4.2 的方法补旗帜探针的写文件诊断（`RaiderEntityMixin` 的 `cca$onDeathMixin` 分支日志）。
- **必读文件**：
  - `prompts\FACTS.md`（项目事实卡，本轮新增/修正见 §6）
  - `analysis\DEVIATIONS.md`（`D-S9-1` … `D-S9-6`，其中 `D-S9-3` 已改为"已修复"）
  - `analysis\STATIC-CHECK.md`
  - `_mappings\refmap_mojmap.md`（注入目标唯一权威）
- **前置检查命令**（每次开工先跑一遍，确认环境没坏）：

```powershell
cd D:\Minecraft-mod-development\cca移植neoforge
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
(Get-ChildItem cca-neoforge\src\main\java -Recurse -Include *.java).Count          # 41
$m = Get-Content cca-neoforge\src\main\resources\cca.mixins.json -Raw | ConvertFrom-Json
"mixins=$($m.mixins.Count) client=$($m.client.Count)"                              # mixins=22 client=1
Set-Location cca-neoforge; .\gradlew.bat build --offline --console=plain            # BUILD SUCCESSFUL, jar 129546
```

---

## 6. 对 FACTS.md 的修正/新增（下一阶段请按这些覆盖 FACTS）

1. **FACTS §2 物料**：`_ref\mc-1.21.1-client.jar` **没有 class**（只有 assets/data）。查 MC 成员要用
   `C:\Users\xiao_\.gradle\caches\neoformruntime\intermediate_results\compiledWithNeoForge_0166c820696cc2f2876873532bdc8ee0a3eded51_output.jar`。
2. **FACTS §8 已知坑**新增一条：**NeoForge 在 `HopperBlockEntity.ejectItems` 开头插了 `VanillaInventoryCodeHooks.insertHook`，
   命中即 `ireturn`，vanilla 的 `addItem → tryMoveInItem → canMergeItems` 整段不可达。**
   ⇒ 任何 wrap `canMergeItems` / `tryMoveInItem` 的 mixin 在 NeoForge 上都不会被执行。这是本工程最容易"编译过、注入成功、行为零差异"的坑。
   **解法（本工程已用）**：在 `ejectItems` 的 `insertHook` 调用点再 wrap 一次，条件成立时返回 `false` 让控制权回落 vanilla 路径 —— 见 `HopperBlockEntityEjectMixin`。
   **配套注意**：`@WrapOperation` 的 handler 里若先调 `original.call(...)`，原方法可能已经把数据搬走/改掉，**之后再判状态必然失效**；一律先判条件再决定是否调 `original`。
3. **FACTS §4** 补充：`carpet stackableShulkerBoxes` 是**枚举**规则（`false` / `true` / `2..64`），
   `true` 会被 `StackableShulkerBoxValidator` 翻译成 `shulkerBoxStackSize = 64`；`false` 翻译成 `1`。
   `shulkerBoxStackSize` **不是** `/carpet` 可直接设的规则名。
4. **勘误 N4 修正**：carpet 假人名规范成**全大写**（`BotA` → `BOTA`、`Botb` → `BOTB`），不是"首字母大写"。
5. **勘误 N8 修正**：`BannerPatterns` 的常量名（`RHOMBUS_MIDDLE` / `CIRCLE_MIDDLE`）**不是**命令里的 pattern 名；
   命令用 **`rhombus` / `circle`**。且 `getLeaderBannerInstance` 除 8 层图案外还设了
   `DataComponents.HIDE_ADDITIONAL_TOOLTIP`（`Unit.INSTANCE`）与 `DataComponents.ITEM_NAME`（`Component.translatable("block.minecraft.ominous_banner")`），
   共 **3 类组件**，缺一 `isSameItemSameComponents` 即 false。
6. **新增事实**：`-Dmixin.debug.export=true` 导出的 `.mixin.out\class` **只含被 mixin 目标命中的类**。
   mixin 因类级条件 SKIP 时，目标类**不会**被导出（s9 实测 122 vs 123）。别用"文件存在但方法不存在"当判据。
7. **新增事实**：`runClient` 的"主菜单已就绪"可编程判据 = 日志出现 `Sound engine started` / `Narrator library` /
   `OpenAL initialized` / `Created: <w>x<h>` 任一。判据出现后立即杀进程即可，不需要玩。
8. **新增工具**：`_tools\rcon_file.py`（按 UTF-8 命令文件逐行发 RCON）、`_tools\runclient.ps1`（客户端启动 + 证据保全）。

---

## 7. 发现但本阶段未处理

- **`_tools\rcon_probe.py` 的读循环丢包未修**（`D-S9-6` / 勘误 N1）。所有判断已改为回读日志 + `data get` 双通道绕过。
- **`BadOmenStatusEffectMixin` 的 `ServerLevel world` 形参名**、`RaiderEntityMixin` 的 `Level world` 形参名仍叫 `world`。
  不影响任何解析，但严格按 Yarn 洁癖可以改成 `level`。**按硬约束 9（不改 s1–s8 验收文件）本轮没动**，留待下次统一重命名。
- **`_work\` 下有一批探针工作文件**（`banner_cmds{,2..7}.txt`、`banner_probe.txt`、`javap_hopper.txt`、`cca.mixins.json.bak`、
  `hopfix_*.txt`、`hopfinal_setup.txt`）。`cca.mixins.json.bak` 是**21 条版本的备份**（方案① 之前的状态），供回滚用；其余可清理。
- **`_tools\out\` 里有调试期的日志**：`s9_debug_hopper{,2,3,4}.log`、`s9_build_debug.log`、
  `s9_hopper_diag.txt`、`s9_hopper_fix{,2}.log`。它们是 §4.1 排查过程的原始证据，建议保留但不要混入发布包。
  **发布版 jar 内不含任何诊断代码**（`cca$dbg` 已删除，`_tools\out\hopper_dbg.txt` 在最终构建下不再生成）。
- **`dist\RULE-TEST.md` 已补齐**（13 条规则手测手册，含方案① 后的第 13 条【E2E】改判与 `D-S9-5` 的旗帜 SNBT 注意事项、
  `D-S9-1` 的 3 月 25 日条件）。

---

## 附：如何发布

1. **改版本号**：只改 `cca-neoforge\gradle.properties` 的 `mod_version`。
   `META-INF\neoforge.mods.toml` 里用的是 `${mod_version}` 占位，构建时自动带入，**不要手改 toml**。
2. **构建**：

   ```powershell
   cd D:\Minecraft-mod-development\cca移植neoforge\cca-neoforge
   $env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
   .\gradlew.bat build --offline --console=plain
   ```

   必须看到 `BUILD SUCCESSFUL`，产物在 `build\libs\`：
   - `CrystalCarpetAddition-neoforge-1.21.1-<version>-neoforge.jar`（主 jar）
   - `CrystalCarpetAddition-neoforge-1.21.1-<version>-neoforge-sources.jar`
3. **出包**：

   ```powershell
   New-Item -ItemType Directory -Force ..\dist | Out-Null
   Copy-Item build\libs\*.jar ..\dist\ -Force
   Get-FileHash ..\dist\*.jar -Algorithm SHA1 | ForEach-Object { "$($_.Hash)  $([System.IO.Path]::GetFileName($_.Path))" } | Set-Content ..\dist\sha1.txt
   ```

   同步更新 `dist\README-安装说明.md` 的版本号、字节数、SHA1、已知差异条目。
4. **发布前必过的自检**（缺一不可）：
   - `java` 数 = **40**、`mixins` = **21**、`client` = **1**
   - 主 jar **> 94,176 B**
   - 服务端起服日志：`[CCA] registered 13 rules` + `Done (`，致命模式 0，`CCA-Mixin` SKIP 0
   - `/cca list CCA` 的 `- ` 行 = **13**，`cca RemoveVillagerTradeDistanceLimit true` 回 `Unknown rule`（正常）
   - `/carpet` 回包含 `Crystal Carpet Addition version: <version>`
   - 四项静态检查全 0（`analysis\STATIC-CHECK.md`）
5. **许可证**：**GPL-3.0-only**，与上游一致。分发时必须保留 `LICENSE` 与上游出处
   （<https://github.com/crystal0404/CrystalCarpetAddition>），并按 GPL 提供完整源码（`-sources.jar` 或仓库）。

---

## 附：如何跟进上游

上游 tag 源码**混着别的 Minecraft 版本**（`_src\cca1211\` 里就有 1.21.4 的代码），
`javap` 证明 `ReIntroduceOldVersionRaid.RaidMixin` 在 1.21.1 发行 jar 里只有 `startMixin_getStatusEffect` 一个 mixin 方法，
tag 里的 `tickMixin_*` 根本没编进 1.21.1 产物。**所以只信 `_decomp\`，不信 `_src\`。**

标准流程：

1. **取新 tag 的发行 jar**，放到 `_ref\`。
2. **remap 成 Mojmap**：

   ```powershell
   $env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
   java -jar _tools\tiny-remapper-fat.jar <new.jar> _ref\cca-mojmap-new.jar <mappings.tiny> official named
   ```

   （`_mappings\` 里有现成的 mapping 材料；具体命令行以 `_work\` 里上一轮的 remap 记录为准。）
3. **反编译**：

   ```powershell
   java -jar _tools\vineflower-1.10.1.jar _ref\cca-mojmap-new.jar _decomp\mojmap-new
   ```

4. **刷新注入目标表**：跑 `_mappings\refmap_to_moj.py`（若本轮上游改了 mixin 的 `@At target`），
   更新 `_mappings\refmap_mojmap.md` / `.json`。**这张表是唯一权威**，任何 `@At(target=…)` 必须逐字抄它。
5. **只 diff `_decomp\`**：

   ```powershell
   # 逐文件比对 41 个 java 与 _decomp\mojmap-new 的差集，只关注真正变了的类
   ```

   不要去 diff `_src\`（tag 源码不可信）。
6. **按 8 条机械转换规则逐条核对**（见 `prompts\FACTS.md` §7）：删 `remap = false`、
   `@At target` 逐字抄 refmap、conditional-mixin 注解换本项目 `utils.annotation.mixin.*`、
   `net.fabricmc` 只改入口、MixinExtras 原样、`org.spongepowered` 原样、不调 `MixinExtrasBootstrap.init()`、包名类名不动。
7. **重点复核 NeoForge 特有的短路点**：每次上游新增 wrap `canMergeItems` / `tryMoveInItem` /
   `dropFromLootTable` 这类"vanilla 内层方法"的 mixin，都要先问一句**"NeoForge 有没有在更外层接管"**（见 §4.1 的教训）。
