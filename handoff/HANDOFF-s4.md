# HANDOFF s4 — `SettingsManagerMixin`（规则级条件注册 + `/carpet` 版本行着色）

- 日期：2026-10-26
- 本阶段结论：**PASS**
- 工程根：`D:\Minecraft-mod-development\cca移植neoforge\cca-neoforge`
- 工作根（物料 / 工具 / 日志）：`D:\Minecraft-mod-development\cca移植neoforge`
- 前置：s3 PASS（`cca.mixins.json` 已挂 `plugin`，`CCAUtils.matches` 已自实现，规则基线 14）

---

## 1. 已落地文件（路径 + 作用 + 行数）

> 路径均相对 `cca-neoforge\src\main\`。

| 路径 | 作用 | 行数 | 状态 |
|---|---|---|---|
| `java\crystal0404\crystalcarpetaddition\mixins\carpet\SettingsManagerMixin.java` | 第一个业务 mixin：① `@WrapWithCondition` 拦 `parseSettingsClass` 里的 `Map.put`，按字段级 `@Restriction` 决定规则是否注册；② `@Inject` 在 `listAllSettings` 版本行后追加 CCA 着色行 | 78 | **新增**（`mixins\` 与 `mixins\carpet\` 目录同时新建） |
| `resources\cca.mixins.json` | `"mixins": []` → `"mixins": ["carpet.SettingsManagerMixin"]`（**本工程第一次非空**）；其余字段一字未动 | 12 → 14 | 改 |
| `java\crystal0404\crystalcarpetaddition\CCAExtension.java` | `onGameStarted()` 在 `parseSettingsClass` 后追加逐条打印 `[CCA] rule <name>`（新增 2 行，在 20 行之后） | 42 → 44 | 改 |

未动（与 s3 终态逐字一致）：`CrystalCarpetAdditionMod.java`(28)、`CCASettings.java`(67)、`utils\CCAUtils.java`(225)、
`utils\CCAMixinConfigPlugin.java`(45)、`utils\condition\{ConditionTester(8), MixinRestrictionProcessor(137)}`、
`utils\annotation\{Condition(14), Restriction(14), impl\AnnotationProcessor(41), mixin\{Condition(31), Restriction(22)}}`、
`utils\{Key, EmptyClass, ModIds, LootTableUtils}`、`utils\shulkerBoxUtils\{ColourMap, ShulkerBoxesSet}`、
`META-INF\neoforge.mods.toml`（`[[mixins]] config="cca.mixins.json"` 早在 s2 就写好）、`META-INF\accesstransformer.cfg`。

当前共 **19 个 java**（s3 是 18）。产物：`build\libs\CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar` **67,122 B**
（s3 是 64,810 B）、`…-sources.jar` **55,621 B**（s3 是 53,828 B）。

jar 内新增条目（`jar tf` 实测）：
```
crystal0404/crystalcarpetaddition/mixins/
crystal0404/crystalcarpetaddition/mixins/carpet/
crystal0404/crystalcarpetaddition/mixins/carpet/SettingsManagerMixin.class
```

新增分析记录：`analysis\DEVIATIONS.md` 追加 **D-S4-1**（`(Object) this` 强制转换）与 **D-S4-2**（多打的 13 行 rule 日志）。

---

## 2. 本轮关键决策（含"为什么"）

### 2.1 `this == CCAExtension.CCASettingsManager` 必须写成 `(Object) this == …`

- 决策：`if ((Object) this == CCAExtension.CCASettingsManager)`。
- 理由（硬证据，照抄反编译产物编译不过）：
  ```
  D:\…\mixins\carpet\SettingsManagerMixin.java:71: 错误: 不可比较的类型: SettingsManagerMixin和SettingsManager
          if (this == CCAExtension.CCASettingsManager) {
                   ^
  1 个错误
  ```
  反编译产物把原始类型转换擦掉了；`SettingsManagerMixin` 并不继承 `SettingsManager`，javac 的 `==` 两侧必须可转换。
  运行时 `this` 就是被织入的 `SettingsManager` 实例，`(Object) this` 的对象身份比较与原版语义完全一致。
- 备选（未选）：`((SettingsManager)(Object) this) == …` —— 效果相同，多一层 cast 只会误导"这一定是个子类"。
- 已记录为 `analysis\DEVIATIONS.md` D-S4-1。

### 2.2 `@Inject` 的 `ordinal = 0` **不能省**：`Messenger.m` 在 `listAllSettings` 里有 **两处**

- 决策：保留 `ordinal = 0`。
- 理由（`javap -p -c -l` 实测，比任务书 §2-2 更细）：
  ```
  64: invokestatic  #772  // Method carpet/utils/Messenger.m:(Lnet/minecraft/commands/CommandSourceStack;[Ljava/lang/Object;)V
  275: invokestatic  #772  // Method carpet/utils/Messenger.m:(Lnet/minecraft/commands/CommandSourceStack;[Ljava/lang/Object;)V
  ```
  偏移 64 的那次在 `if (version != null)`（`LineNumberTable` line 769~772）里，就是 "Carpet Mod version: …" 那一行；
  偏移 275 的那次在 `for` 循环里逐条打分类。`ordinal = 0` 命中的是版本行，正是我们要紧跟的位置。
  任务书只说了"只被调用一次"——**实际是两次**，若省掉 `ordinal`，Mixin 会因为二义性按"第一个"处理（碰巧也对），
  但任何一次 carpet 版本调整都可能让语义漂移，所以显式写死。
- 影响面：无。

### 2.3 `@Local(ordinal = 0) Field field` 可用，**没有**用兜底方案

- 决策：直接用 MixinExtras 的 `@Local` 取 `parseSettingsClass` 的 `Field` 局部变量，不做 `CCASettings.class.getDeclaredField((String) k)` 兜底。
- 理由（`javap -p -c -l` 实测，carpet 1.0.8 的 jar 没 strip LVT）：
  ```
  LocalVariableTable:
    220      19    10 parsed         Lcarpet/api/settings/CarpetRule;
    40      199     6 field          Ljava/lang/reflect/Field;
    0       246     0 this           Lcarpet/api/settings/SettingsManager;
    0       246     1 settingsClass  Ljava/lang/Class;
    15      231     2 warned         Z
  ```
  整个方法里 `Ljava/lang/reflect/Field;` 类型的局部变量只有 `field` 一个（slot 6），`ordinal = 0` 无歧义；
  且 `Map.put` 的调用点在偏移 233，落在 `field` 的存活区间 `40 + 199 = 239` 之内。
  实证：本轮真跑出 13 条规则，说明注解真的读到了（兜底方案会走 `getDeclaredField` 分支，那时连反射异常都不会有，
  但也没必要多一条代码路径）。
- 影响面：**换 carpet 版本时需重新 `javap` 确认 LVT 未被 strip**，否则会退化成
  `Critical injection failure: LVT in … could not find local …`。

### 2.4 `CarpetRule.name()` 存在（任务书 §4-3 的"先 javap 查，不许猜"已执行）

```
> javap -cp _ref\neoforge-carpet-1.21.1-1.0.8+v251027.jar carpet.api.settings.CarpetRule
public interface carpet.api.settings.CarpetRule<T> {
  public abstract java.lang.String name();
  …
}
```
`SettingsManager.getCarpetRules()` 返回 `java.util.Collection<CarpetRule<?>>`（javap `-p` 确认），
故 `CCASettingsManager.getCarpetRules().forEach(rule -> LOGGER.info("[CCA] rule {}", rule.name()))` 直接成立。

### 2.5 逐条打印走 `CCAExtension`，不另开调试命令

- 决策：把 13 行 `[CCA] rule <name>` 放进 `onGameStarted()`，不做 `/cca rules` 子命令。
- 理由：任务书 §4-3 明确要求；且 `onGameStarted` 是规则注册刚结束的唯一时点，日志顺序能直接证明"parse 完成 → 13 条"。
- 备选（未选）：用 RCON `/cca list CCA` 代替 —— 已经是 DoD 第 6 条，两个证据互相独立，都要。

---

## 3. 验收结果（命令 + 关键输出原文）

### 3.1 编译 / 打包

```
> cmd /c 'set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11" && gradlew.bat build --console=plain'
> Task :compileJava
> Task :processResources
> Task :jar
> Task :sourcesJar
BUILD SUCCESSFUL in 4s
5 actionable tasks: 4 executed, 1 up-to-date
```

（注：本机 `pwsh` 是 Windows PowerShell 5.1，**不支持 `&&`**。必须整串交给 `cmd /c '...'`，
或在 `pwsh` 里用 `;` + 手动 `$env:JAVA_HOME`。第一次写成 `cmd /c set ... && gradlew ...` 会报
`标记“&&”不是此版本中的有效语句分隔符`。）

### 3.2 `runServer` → 13 条规则 + 逐条规则名

```
> $env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
> cmd /c 'gradlew.bat runServer --console=plain > ..\_tools\out\s4_run1.log 2>&1'   # 后台
[00:07:52] [Server thread/INFO] [minecraft/DedicatedServer]: Done (5.670s)! For help, type "help"
```

`_tools\out\s4_run1.log` 关键行（原文照抄，`[CCA] rule` 13 行 + 汇总）：

```
[00:07:39] [modloading-worker-0/INFO] [cr.cr.CrystalCarpetAdditionMod/]: [CCA] rule StackableShulkerBoxesEnhancement
[00:07:39] … [CCA] rule AnvilCanCrushItemEntities
[00:07:39] … [CCA] rule ReIntroduceOldVersionRaid
[00:07:39] … [CCA] rule NoBatSpawn
[00:07:39] … [CCA] rule ShulkerBoxPowerOutputExpansion
[00:07:39] … [CCA] rule EndermanCannotPickUpBlocksInNether
[00:07:39] … [CCA] rule EnderDragonPartCanUseEndPortal
[00:07:39] … [CCA] rule MagicBox
[00:07:39] … [CCA] rule ItemShadowing
[00:07:39] … [CCA] rule ReIntroduceOldVersionWitchLootTable
[00:07:39] … [CCA] rule GatewayCannotLoadingChunks
[00:07:39] … [CCA] rule ComparatorIgnoresStateUpdatesFromBelow
[00:07:39] … [CCA] rule ShulkerBoxPowerOutputExpansionColour
[00:07:39] [modloading-worker-0/INFO] [cr.cr.CrystalCarpetAdditionMod/]: [CCA] registered 13 rules
```

`RemoveVillagerTradeDistanceLimit` **不在**这 13 行里。

失败扫描（期望空，实测空）：
```
> Select-String -LiteralPath _tools\out\s4_run1.log -Pattern 'Mixin apply failed|Critical injection failure|CCA-Mixin|Exception|ERROR'
（零输出）
```

### 3.3 `/carpet` 版本行（DoD 第 6 条硬证据）

```
> python _tools\rcon_probe.py 127.0.0.1 25575 probe123 "carpet" "cca list CCA"
AUTH OK
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

全文本地备份：`_tools\out\s4_rcon.txt`。`/cca list CCA` 恰好 **13 行**，无 `RemoveVillagerTradeDistanceLimit`。

### 3.4 判定链的"因果"证据（额外做的，任务书没要求）

带 `-Dcca.enable.debug=true` 再跑一次（`_tools\out\s4_run2_debug.log`），拿到 `AnnotationProcessor` 的原始动机说明：

```
Picked up JAVA_TOOL_OPTIONS: -Dcca.enable.debug=true
[00:09:27] [modloading-worker-0/INFO] [cr.cr.ut.an.im.AnnotationProcessor/]: [CCA] Rule RemoveVillagerTradeDistanceLimit is disabled, Because of the require with minecraft(>=1.21.4).
[00:09:27] [modloading-worker-0/INFO] [cr.cr.CrystalCarpetAdditionMod/]: [CCA] registered 13 rules
```

→ 掉的那条**确实**是 `RemoveVillagerTradeDistanceLimit`，**确实**是因为 `minecraft(>=1.21.4)` 在 `1.21.1` 上判假，
**不是**因为 `Map.put` 被整体拦住（否则会连其它 13 条一起掉）。复现命令：

```powershell
cd D:\Minecraft-mod-development\cca移植neoforge
.\_tools\s3_run.ps1 -LogName s4_run2_debug.log -JavaOpts '-Dcca.enable.debug=true' -NoDaemon
# -NoDaemon 必须带，否则 JAVA_TOOL_OPTIONS 传不到子 JVM
```

### 3.5 DoD 逐条核对

| # | DoD | 证据 | 判定 |
|---|---|---|---|
| 1 | mixin 文件存在 | 78 行，见 §1 | ✔ |
| 2 | `mixins` 数组 = `["carpet.SettingsManagerMixin"]` | `cca.mixins.json:7-9` | ✔ |
| 3 | `build` 绿 + `runServer` 到 `Done` + 无注入失败 | §3.1 / §3.2，失败扫描零输出 | ✔ |
| 4 | 14 → **13** | `[CCA] registered 13 rules` | ✔ |
| 5 | 13 个规则名逐个打印且集合正确 | §3.2 | ✔ |
| 6 | `/carpet` 含 `Crystal Carpet Addition version: 1.11.1-neoforge`；`/cca list CCA` 13 行 | §3.3 | ✔ |
| 7 | `handoff\HANDOFF-s4.md` | 本文件 | ✔ |

---

## 4. 未解决 / 踩坑（错误原文 + 现象 + 已排查到什么程度）

### 4.1 反编译产物丢类型转换导致编译失败（**已解决**，见 §2.1）

- 现象：`SettingsManagerMixin.java:71: 错误: 不可比较的类型: SettingsManagerMixin和SettingsManager`。
- 已排除：`CCASettingsManager` 的类型写错（确实是 `carpet.api.settings.SettingsManager`，
  `CCAExtension.java:10-11` 声明无误）。
- 修法：`(Object) this == …`。
- **教训（对 s5~s9 通用）**：`_decomp\mojmap` 是 CFR/Fernflower 产物，**类型转换、显式 `this` 限定、泛型参数会被擦掉**。
  凡是在反编译产物里看到 `this == 某个父类型之外的实例`、或 `((X) y).foo()` 这种缺 cast 的写法，
  编译一报错就优先补 cast，不要去怀疑注入目标。

### 4.2 `Messenger.m` 在 `listAllSettings` 里其实有两处（**已解决**，见 §2.2）

- 现象：无（因为显式写了 `ordinal = 0`）。
- 风险：任务书 §2-2 说"只被调用一次"，实测是两处（偏移 64 / 275）。若后续有人为了"简化"删掉 `ordinal = 0`，
  Mixin 仍能应用（按首处命中），但语义变成隐式的；一旦 carpet 改实现就会静默漂到分类循环那一行。
- 建议：`ordinal` 保留。

### 4.3 PowerShell 5.1 不支持 `&&`（**已解决**，新踩的一坑，s3 handoff 未记录）

- 现象：
  ```
  标记“&&”不是此版本中的有效语句分隔符。
      + CategoryInfo          : ParserError: (:) [], ParentContainsErrorRecordException
  ```
- 根因：`pwsh.exe` 实为 Windows PowerShell 5.1，解析器没有 `&&`。
- 修法：`cmd /c 'set "JAVA_HOME=…" && gradlew.bat build --console=plain'`（整串作为**一个**参数交给 cmd），
  或 `$env:JAVA_HOME='…'; cmd /c 'gradlew.bat …'`。

### 4.4 用 `runServer` 后台 + RCON 探针（本轮实际操作方式，与 s3 的 `s3_run.ps1` 不同）

`_tools\s3_run.ps1` 会在看到 `Done (` 后**立即 RCON stop**，因此它拿不到 `/carpet` 的回显。
要抓命令输出必须自己起服：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
cmd /c 'gradlew.bat runServer --console=plain > ..\_tools\out\s4_run1.log 2>&1'   # run_in_background
# 轮询 _tools\out\s4_run1.log 出现 'Done (' 后：
python _tools\rcon_probe.py 127.0.0.1 25575 probe123 "carpet" "cca list CCA" | Tee-Object _tools\out\s4_rcon.txt
python _tools\rcon_probe.py 127.0.0.1 25575 probe123 "stop"
```

`stop` 那次探针会抛 `ConnectionError`（服务器关连接），**属正常**；判定标准是日志里出现
`[Server thread/INFO] [minecraft/MinecraftServer]: Stopping server`。
`rcon_probe.py` 支持一次传多条命令（`sys.argv[4:]`），不要每条命令起一个进程。

### 4.5 遗留：无阻塞项

本阶段没有未解决的编译错误、没有未找到的注入点、没有 `[CCA-Mixin] SKIP`。
`lithium\HopperHelperMixin` 的移除仍是 s2 既定决策，s7 时从 `cca.mixins.json` 排除（当前它本来就不在数组里）。

---

## 5. 下一阶段起点

- 第一个待办：**s5 · 6 个规则 mixin**（Anvil / Comparator / NoBat / Enderman / Gateway / EnderDragonPart×4）。
- 必读文件：
  - `prompts\s5-*.md`（任务书，若尚无则先等 YG 给）
  - `_mappings\refmap_mojmap.md` 的对应段（每个 mixin 一行 `@At target`，逐字抄）
  - `_decomp\mojmap\crystal0404\crystalcarpetaddition\mixins\rule\**`（改造源）
  - `analysis\RULE-MATRIX.md`（规则 → mixin 对照）
  - `analysis\DEVIATIONS.md`（D-S4-1 的类型擦除教训）
- 前置检查命令：
  ```powershell
  cd D:\Minecraft-mod-development\cca移植neoforge\cca-neoforge
  $env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'; cmd /c 'gradlew.bat build --console=plain'
  # mixins 数组现在非空，任何新增条目后必须确认不出现 [CCA-Mixin] SKIP
  ```
- s5 起点状态：**规则基线 = 13**（不是 14）。s5 的 6 个 mixin **都不带类级 `@Restriction`**，
  它们的开关在方法体里读 `CCASettings.<规则名>`，**不要**给它们加注解。
- `MixinRestrictionProcessor.shouldApply` 已稳定：s5 起给任何 `rule.*` mixin 加
  `@crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction(...)` 就自动门控，**不用改 plugin**。
- `SettingsManagerMixin` 是"规则级"，**不受** `/cca <rule>` 开关影响（它管规则**是否注册**，不是规则值）。

---

## 6. 对 FACTS.md 的修正/新增（下一阶段请按这些覆盖 FACTS）

- **修正 FACTS 第 3 节 / s3 handoff §1**：`cca.mixins.json` 的 `mixins` **已非空**，现在是
  `["carpet.SettingsManagerMixin"]`；文件 14 行。新增 mixin 只需往该数组追加，`plugin` / `client` / `injectors` 不动。
- **新增事实 K1**：`carpet.api.settings.SettingsManager#listAllSettings(CommandSourceStack)I` 里
  `Messenger.m(CommandSourceStack,Object[])V` 有**两处**（偏移 64 = 版本行，在 `if (version != null)` 内；偏移 275 = 分类循环）。
  注入版本行必须写 `ordinal = 0`。
- **新增事实 K2**：`SettingsManager#parseSettingsClass(Class)` 的 LVT 完整保留，局部变量布局
  （`javap -p -c -l` 原文）：`slot 1 settingsClass Ljava/lang/Class;`、`slot 2 warned Z`、
  `slot 6 field Ljava/lang/reflect/Field;`（`40 199`）、`slot 8 newAnnotation Lcarpet/api/settings/Rule;`、
  `slot 9 oldAnnotation Lcarpet/settings/Rule;`、`slot 10 parsed Lcarpet/api/settings/CarpetRule;`。
  方法内唯一的 `Map.put` 在偏移 233，唯一符合条件的 `Field` 局部变量是 slot 6 → `@Local(ordinal = 0) Field` 安全。
- **新增事实 K3**：`carpet.api.settings.CarpetRule` 有 `name() / categories() / value() / type() / defaultValue() / suggestions() / settingsManager()`；
  `SettingsManager.getCarpetRules()` 返回 `Collection<CarpetRule<?>>`（不是 `Map`）。
- **新增事实 K4**：反编译产物不可直接编译 —— `this == <非父类型实例>` 必须写成 `(Object) this == …`。
- **新增事实 K5**：`gradlew.bat build` 的 `compileJava` 会打印 grader 的 UTF-8 中文错误，但 `pwsh` 会把
  整行包进 `NativeCommandError`；抓错误时看 `cmd : … SettingsManagerMixin.java:71: 错误: …` 这一段原文。
- **新增工具事实**：`_tools\s3_run.ps1` 会自动 stop，**抓不到 RCON 回显**；要抓回显用 §4.4 的后台起服方式。
- **修正 s3 handoff §2.5 的路径**：`CCAUtils.java` 现在 225 行（s3 handoff 写 209 是当时值）。

---

## 7. 发现但本阶段未处理

1. **`/cca list CCA` 里 `MagicBox` 显示成 `MagicBox/CCESuppressor (MagicBox)`**。
   这是 carpet 的 `strict`/别名机制（`CarpetRule.name()` 返回 `MagicBox`，显示层拼了兼容别名），
   **不是本 mod 的 bug**，也不是本阶段该动的。若 s9 验收要求"显示名干净"，届时单独讨论是否要
   `@Rule(strict = true)` 或加 `suggestions`。当前 DoD 只要求"13 行且无 RemoveVillagerTradeDistanceLimit"，已满足。
2. **`CCAExtension` 的 13 行 `[CCA] rule` 日志**：s9 全量回归时会变成噪声（届时规则数若变化，行数也跟着变）。
   已记 `analysis\DEVIATIONS.md` D-S4-2，s9 可删。
3. **`SettingsManagerMixin` 的 `instance` 参数未被使用**（`Map<String, CarpetRule<?>> instance`）。
   保留它是为了让方法签名与被拦截的 `Map.put` 接收者匹配；若以后想用，可直接 `instance` 判身份而不必 `(Object) this`。
   本阶段不改成 `instance == CCAExtension.CCASettingsManager.getRules()` 之类，因为 `rules` 是 private 且 `@Shadow` 要额外声明。
4. **`_work\src\...\mixins\carpet\SettingsManagerMixin.java` 与 `_decomp\mojmap` 版本逐行相同**
   （`Compare-Object` 零差异），说明 `_work\src` 不是 1.21.4 变体，后续可放心用它做交叉核对。
5. **`cca.accesswidener` 的 AT 尚未生效验证**：`BadOmenMobEffect` / `EnderMan$EndermanTakeBlockGoal` 的 AT
   现在写在 `META-INF\accesstransformer.cfg`，但**没有任何 mixin 用到它们**（s4 的 mixin 不需要）。
   s8（ReIntroduceOldVersionRaid）× s9 之前必须实测一次 AT 生效，否则届时会以"编译过、运行 NoSuchField/Method"的形态爆出来。
6. **`run\log\latest.log` 已被 s4 的两次运行覆盖**：要看 s3 证据只能去 `_tools\out\s3_*.log`。
   本阶段的证据文件：`_tools\out\s4_run1.log`（13 条 + `/carpet` 前的起服日志）、
   `_tools\out\s4_rcon.txt`（RCON 回显）、`_tools\out\s4_run2_debug.log`（debug 判定链）。
