# HANDOFF s3 — 自建条件注入体系（替代 `me.fallenbreath.conditionalmixin`）

- 日期：2026-10-25
- 本阶段结论：**PASS**
- 工程根：`D:\Minecraft-mod-development\cca移植neoforge\cca-neoforge`
- 工作根（物料 / 工具 / 日志）：`D:\Minecraft-mod-development\cca移植neoforge`

---

## 1. 已落地文件（路径 + 作用 + 行数）

> 路径均相对 `cca-neoforge\src\main\java\crystal0404\crystalcarpetaddition\`（除最后一行资源）。

| 路径 | 作用 | 行数 | 状态 |
|---|---|---|---|
| `utils\condition\ConditionTester.java` | 复刻原库接口；`boolean isSatisfied(String mixinClassName)` | 8 | 新增 |
| `utils\annotation\mixin\Condition.java` | TYPE 级类注解；`Type{MOD,MIXIN,TESTER}` / `value` / `String[] versionPredicates` / `Class<? extends ConditionTester> tester` | 26 | 新增 |
| `utils\annotation\mixin\Restriction.java` | TYPE 级，`@Target(ElementType.TYPE)`；`require[]` / `conflict[]` | 18 | 新增 |
| `utils\condition\MixinRestrictionProcessor.java` | 判定核心：ASM 读注解 → require 全真 → conflict 全假；异常一律 `false` + 日志 | 137 | 新增 |
| `utils\CCAMixinConfigPlugin.java` | `IMixinConfigPlugin` 7 个方法全实现；`shouldApplyMixin` 委托 `MixinRestrictionProcessor` | 45 | 新增 |
| `utils\CCAUtils.java` | 补 `JAVA_PARAMETERS` / `isLoad(String,String...)` / `lookupModVersion` / `tryFindClass` / `matches` / `DisableEasterEggs` / `EnableMagicSetting` | 56 → 209 | 整体重写 |
| `..\resources\cca.mixins.json` | 加 `"plugin": "crystal0404.crystalcarpetaddition.utils.CCAMixinConfigPlugin"`；`mixins: []`、`client: []`、无 `refmap` | 12 | 改 |

未动（与 s2 终态逐字一致）：`CrystalCarpetAdditionMod.java`(28)、`CCAExtension.java`(42)、`CCASettings.java`(67)、
`utils\{Key(21), EmptyClass(4), ModIds(17), LootTableUtils(67)}`、`utils\annotation\{Condition(14), Restriction(14), impl\AnnotationProcessor(41)}`、
`utils\shulkerBoxUtils\{ColourMap(53), ShulkerBoxesSet(27)}`。当前共 **18 个 java**。

临时件已删：`mixins\test\TestConditionMixin.java` 与 `mixins\test\` 目录都不存在了。

新增分析文档：`analysis\DEVIATIONS.md`（s3 的 7 条有意差异 + 1 条通用编码约定）。
新增工具（工作根下，不进 jar）：`_tools\s3_run.ps1`（起服+等 Done+RCON 停服）、`_tools\s3_version_check\VersionCheck.java`（版本谓词独立验证）。

---

## 2. 本轮关键决策（含"为什么"）

### 2.1 mod 版本查找必须**双通道**（`ModList` + `LoadingModList`）

- 决策：`CCAUtils.lookupModVersion(String)` 先看 `ModList.get()`，为 `null` 时走 `LoadingModList.get().getModFileById(modId)`，
  遍历 `ModFileInfo.getMods()` 按 modId 取 `IModInfo.getVersion().toString()`。
- 理由：**`shouldApplyMixin` 在 mixin config 解析期被调用，那时 `ModList.get()` 是 `null`。**
  单通道写法（任务书 §2.6 给的原文）会让**全部**带类级 `@Restriction` 的 mixin 在第一次解析时被误判 SKIP。
  实测证据 `_tools\out\s3_runB2.log`：
  `[CCA-Mixin] SKIP ... (require failed: MOD:carpet)`，同次运行里临时调试行打出 `[CCA][TEMP] isLoad(carpet) ModList==null`。
- 备选（未选）：把 `shouldApplyMixin` 推迟到 `preApply` 再判 —— 那已经不是"跳不跳过应用"的时机了，改不了。

### 2.2 版本谓词**不用** `VersionRange.createFromVersionSpec` 裸串

- 决策：`[`/`(` 开头交给 `VersionRange`；`>= <= > < != == =` 自己用 `DefaultArtifactVersion.compareTo`；
  含 `x`/`*`/空串走前缀或恒真。
- 理由：`VersionRange.createFromVersionSpec(">=1.21.4").containsVersion(new DefaultArtifactVersion("1.21.1"))`
  实测返回 **`true`**（Maven 把不以 `[`/`(` 开头的串当"推荐版本"而非硬约束）。
  照抄任务书写法 = `RemoveVillagerTradeDistanceLimit` 永远留在列表里 = I12 那条证据链断掉。
- 证据：`_tools\s3_version_check\VersionCheck.java` 13 条用例 `FAILURES=0`，
  其中 `1.21.1 >= 1.21.4 → false`、`1.21.1 < 1.21.4 → true`、`[1.21.4,) → false`、
  `1.4.147+v1.0.8-251027 >= 1.4.0 → true`、`1.11.1-neoforge >= 1.11 → true`、`1.21.1 1.21.x → true`。

### 2.3 `DisableEasterEggs.isSatisfied` **不取反**

- 决策：`return JAVA_PARAMETERS.get("cca.disable.EasterEggs").getValue();`
- 理由：`_decomp\mojmap\crystal0404\crystalcarpetaddition\utils\CCAUtils.java:72` 原版就是这样。
  语义：该 `Key` 存的是"彩蛋被禁用"（默认 `true`），而 s9 的 `Happy` 把它放在 **conflict** 里，
  conflict 命中 = 不注入 → 默认状态下彩蛋 mixin 不注入，符合原设计。
- 注意：任务书 §2.6 的示例写法注释里说"彩蛋被禁用 → conflict 成立 → 不注入"，方向与我实现一致；
  但**不要**在这里加 `!`，加了就反了。

### 2.4 `MixinRestrictionProcessor` 里两个显式类型的私有辅助

- 决策：`conditionList(AnnotationNode,String)` 与 `stringList(AnnotationNode,String)`，不用任务书里的
  `private static <T> List<T> list(...)`。
- 理由：泛型方法在 enhanced-for 里不做目标类型推断，编译报
  `MixinRestrictionProcessor.java:42: 不兼容的类型: Object无法转换为AnnotationNode`。

### 2.5 判定失败只返回 `false`，绝不外抛

- 决策：`shouldApply` 整体包 `catch (Throwable t)`，打一条 WARN 后 `return false`。
- 理由：任务书硬约束；且 mixin 框架里一次异常会让整个启动挂掉，跳过顶多少个功能。

### 2.6 测试 mixin 的注入目标换成 `DedicatedServer`

- 决策：验证矩阵的临时 mixin 用 `@Mixin(net.minecraft.server.dedicated.DedicatedServer.class)` +
  `@Inject(method = "initServer", at = @At("HEAD"))`。
- 理由：`MinecraftServer.initServer` 是 `protected abstract boolean initServer()`（javap 确认，无字节码），
  在抽象方法上 `@At("HEAD")` 直接崩（见 §4-1）。
- 备选：不选 `MinecraftServer.tickServer`（每 tick 打日志太吵）、不选 `runServer`（private 探针要额外 AT）。

### 2.7 插件直接实现 `IMixinConfigPlugin`，不照抄 `RestrictiveMixinConfigPlugin` 那层继承

- 决策：插件基类就是 `IMixinConfigPlugin` 直接实现，**没有**照抄 `RestrictiveMixinConfigPlugin` 那层继承。
- 理由：原库那层只多做了 `AnnotationCleaner`（`preApply/postApply` 擦注解）与
  `onRestrictionCheckFailed` 回调；前者单 config 不需要，后者用我们自己的 SKIP 日志替代（详见 `analysis\DEVIATIONS.md` D-S3-1/D-S3-5）。

---

## 3. 验收结果（命令 + 关键输出原文）

### 3.1 编译 / 打包

```
> cmd /c set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11" && gradlew.bat build --console=plain
> Task :compileJava UP-TO-DATE
> Task :jar
> Task :sourcesJar
BUILD SUCCESSFUL in 2s
```

产物：`build\libs\CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar` **64,810 B**、
`…-sources.jar` **53,828 B**（s2 是 52,644 B / 45,237 B）。

### 3.2 验证矩阵 A~E（5 次 `runServer`，日志留在 `_tools\out\`）

| 组 | `@Restriction` | 日志原文（关键行） | 判定 |
|---|---|---|---|
| A | `require = @Condition("definitely_not_a_mod")` | `[23:53:32] [main/INFO] [CCA-Mixin-Restriction/]: [CCA-Mixin] SKIP crystal0404.crystalcarpetaddition.mixins.test.TestConditionMixin (require failed: MOD:definitely_not_a_mod)` + `Done (5.531s)!`，无 `TEST APPLIED` | PASS（`_tools\out\s3_runA2.log`） |
| B | `require = @Condition(ModIds.CARPET)` | `[23:48:25] [Server thread/INFO] [CCA-Mixin-Test/]: [CCA-Mixin] TEST APPLIED` + `Done (5.403s)!`，无 SKIP | PASS（`s3_runB.log`） |
| C | `conflict = @Condition(ModIds.CARPET)` | `[23:49:05] [main/INFO] [CCA-Mixin-Restriction/]: [CCA-Mixin] SKIP … (conflict hit: MOD:carpet)` + `Done (6.109s)!` | PASS（`s3_runC.log`） |
| D | `require = @Condition(type = TESTER, tester = CCAUtils.EnableMagicSetting.class)` + `-Dcca.enable.MagicSettings=true` | `TEST APPLIED` + `Done (5.572s)!`（日志里 2 次 `Picked up JAVA_TOOL_OPTIONS: -Dcca.enable.MagicSettings=true`） | PASS（`s3_runD.log`） |
| E | 同 D，不带 `-D` | `[23:52:02] [main/INFO] [CCA-Mixin-Restriction/]: [CCA-Mixin] SKIP … (require failed: TESTER:crystal0404.crystalcarpetaddition.utils.CCAUtils$EnableMagicSetting)` + `Done (5.518s)!` | PASS（`s3_runE.log`） |

`A` 的第一次尝试（`s3_runA.log`）与 `B` 的第一次尝试（`s3_runB.log` 之前那次）都是失败样本，根因见 §4-2、§4-3。

### 3.3 第 6 次运行（临时 mixin 删净，`mixins: []`）

```
> _tools\s3_run.ps1 -LogName s3_runF
[23:55:40] [Server thread/INFO] [minecraft/DedicatedServer]: Done (5.594s)! For help, type "help"
```
- `Select-String -Path run\logs\latest.log -Pattern 'CCA-Mixin|Mixin apply failed'` → **零输出**
- 同次日志仍有 `[CCA] registered 14 rules`、`[CCA] version=1.11.1-neoforge CCA-category rules=14`
  → 说明 plugin 挂上后对空 `mixins: []` **无副作用**，14 条规则照旧。

### 3.4 任务书 §5 的验收命令逐条结果

```powershell
> Get-ChildItem src\main\java\crystal0404\crystalcarpetaddition -Recurse -File   # 18 个 java
> Select-String -Path src\main\resources\cca.mixins.json -Pattern 'plugin|mixins'
src\main\resources\cca.mixins.json:4:  "package": "crystal0404.crystalcarpetaddition.mixins",
src\main\resources\cca.mixins.json:5:  "plugin": "crystal0404.crystalcarpetaddition.utils.CCAMixinConfigPlugin",
src\main\resources\cca.mixins.json:7:  "mixins": [],
> Test-Path src\main\java\crystal0404\crystalcarpetaddition\mixins\test
False
```

---

## 4. 未解决 / 踩坑（错误原文 + 现象 + 已排查到什么程度）

### 4.1 `@At("HEAD")` 打在抽象方法上会 NPE（**已解决，但会重复踩**）

- 现象：启动崩，`Mixin apply for mod cca failed cca.mixins.json:test.TestConditionMixin from mod cca -> net.minecraft.server.MinecraftServer: … Unexpecteded NullPointerException preparing @Inject annotation on cca$test [...] { target: initServer()Z }`，
  `Caused by: java.lang.NullPointerException: Cannot read field "index" because "insnNode" is null`。
- 根因：`MinecraftServer.initServer` 是 **抽象方法**（`javap` 显示 `protected abstract boolean initServer();`），没有指令列表。
- 已排除：描述符抄错、`shouldApplyMixin` 挡掉了（B 组当时 SKIP 是另一个 bug，§4.3）。
- 修法：改用 `DedicatedServer.initServer`（实现类）。后续阶段凡是要在 MC 类上注入，先 `javap` 确认方法不是 `abstract`/不是接口默认方法。

### 4.2 版本谓词的 Maven 坑（**已解决**，见 §2.2）

- 现象（若照抄任务书）：`>=1.21.4` 在 `1.21.1` 上判为满足 → s4 里规则数不会掉。
- 已排查到：`VersionRange.createFromVersionSpec` 对裸串走 `RESTRICTION`/推荐版本语义，`containsVersion` 只比 `1.21.4` 本身。
  `_tools\s3_version_check` 里保留了这条对照输出：`maven-ish raw >=1.21.4 on 1.21.1 -> true`。

### 4.3 mixin config 解析期 `ModList.get() == null`（**已解决**，见 §2.1）

- 现象：B 组首次运行 `[CCA-Mixin] SKIP ... (require failed: MOD:carpet)`，明明 carpet 是必需依赖。
- 根因：`shouldApplyMixin` 调用时机早于 FML 装载完成。
- 已排除：modId 拼错（`ModIds.CARPET` = `"carpet"`，与 jar 内 modId 一致，`textool` 已验）。

### 4.4 PowerShell 编码坑（**已解决**，写进 DEVIATIONS D-GEN-1）

- 现象：用 `Set-Content -Encoding utf8` 回写 `.java` 后，再用 `Get-Content -Raw` 读出来，中文注释已经变成
  `娉ㄨВ閲屾病鍐?` 这种 mojibake —— 写进去的那一刻就坏了，不是显示问题。
- 根因：本机 `pwsh` 实为 Windows PowerShell 5.1，默认 GBK；`-Encoding utf8` 写出的是 UTF-8 **带 BOM**，混着 GBK 读取的往返必然破坏。
- 修法：所有文本用 write/edit 工具写；PowerShell 只读不写。

### 4.5 `CCAUtils` 里对 `ModList` 的静态依赖

- 现象：无（已用双通道解决）。但注意 `lookupModVersion` 里 **不要**把 `ModList.get()` 的结果缓存到 static 字段——
  它在启动早期是 `null`，缓存下来就永远是 `null`。当前实现每次现取。

### 4.6 遗留：无阻塞项

本阶段没有"未解决的编译错误"或"未找到的注入点"。`lithium\HopperHelperMixin` 的移除是 s2 已定的决策（lithium 无 NeoForge 版），
仍要在 s7 按 HANDOFF-s2 的安排从 `cca.mixins.json` 里排除。

---

## 5. 下一阶段起点

- 第一个待办：**s4 · `SettingsManagerMixin`**（规则级条件注册 + `fancyName` 着色）。
- 必读文件：
  - `prompts\s4-SettingsManagerMixin.md`（任务书，105 行）
  - `_decomp\mojmap\crystal0404\crystalcarpetaddition\mixins\carpet\SettingsManagerMixin.java`（76 行，改造源）
  - `_mappings\refmap_mojmap.md` 的 `## carpet/SettingsManagerMixin` 段（两行注入目标，逐字抄）
  - `_work\carpet_all\carpet\api\settings\SettingsManager.java`（核对 `parseSettingsClass` / `listAllSettings` / `fancyName`）
  - `analysis\DEVIATIONS.md`（s3 差异，尤其 D-S3-3 版本谓词）
- 前置检查命令：
  ```powershell
  cd D:\Minecraft-mod-development\cca移植neoforge\cca-neoforge
  .\gradlew.bat build --console=plain
  javap -cp ..\_ref\neoforge-carpet-1.21.1-1.0.8+v251027.jar -p carpet.api.settings.SettingsManager
  ```
- s4 的**硬证据**：日志从 `[CCA] registered 14 rules` → `[CCA] registered 13 rules`，掉的必须是
  `RemoveVillagerTradeDistanceLimit`（`require minecraft>=1.21.4` 在 1.21.1 恒假）。若还是 14 条，先查
  `CCAUtils.matches` 有没有被改回 `VersionRange` 裸串，再查 `AnnotationProcessor.shouldRegister` 的 `this == CCAExtension.CCASettingsManager` 判定。
- s4 要新增的 mixin 路径：`src\main\java\crystal0404\crystalcarpetaddition\mixins\carpet\SettingsManagerMixin.java`，
  并在 `cca.mixins.json` 的 `mixins` 数组加 `"carpet.SettingsManagerMixin"`（这是第一次让 `mixins` 非空）。
- 起服/停服（已验证可用）：
  ```powershell
  ..\_tools\s3_run.ps1 -LogName s4_run1            # 内部：cmd /c set JAVA_HOME ... gradlew.bat runServer ; 等 Done ( ; RCON stop
  python ..\_tools\rcon_probe.py 127.0.0.1 25575 probe123 "cca list CCA"
  ```

---

## 6. 对 FACTS.md 的修正/新增（下一阶段请按这些覆盖 FACTS）

- **新增事实 A1**：`shouldApplyMixin`（`IMixinConfigPlugin`）在 **mixin config 解析期**被调用，此时
  `net.neoforged.fml.ModList.get()` 返回 `null`。要用 `net.neoforged.fml.loading.LoadingModList.get().getModFileById(modId)`
  （返回 `IModFileInfo`/`ModFileInfo`），再从 `getMods()` 里按 modId 取 `IModInfo.getVersion().toString()`。
  已有实现：`CCAUtils.lookupModVersion`。
- **新增事实 A2**：`org.apache.maven.artifact.versioning.VersionRange.createFromVersionSpec`/
  `DefaultArtifactVersion` 在 `cca-neoforge` 的 compile classpath 上**可直接用**（无需额外依赖），
  但**裸约束串语义错误**：`createFromVersionSpec(">=1.21.4").containsVersion(new DefaultArtifactVersion("1.21.1")) == true`。
  仅 `[`/`(` 开头的区间串可信。
- **新增事实 A3**：`MinecraftServer.initServer` 是 `protected abstract boolean initServer()`，无字节码，
  `@At("HEAD")` 会 NPE。可注入的替代目标：`DedicatedServer.initServer()`、
  `MinecraftServer.tickServer(BooleanSupplier)`、`MinecraftServer.runServer()`、`MinecraftServer.loadLevel()`、`MinecraftServer.stopServer()`。
- **新增事实 A4**：`IMixinConfigPlugin` 在 Mixin 0.8.7 里是 **7 个抽象方法**，**没有** `onSelect`。
- **修正 FACTS 第 8 节第 6 条（Mixin 报错的样子）**：除了 `target method not found`，
  还有一种形态是 `Unexpecteded NullPointerException preparing @Inject annotation …` +
  `Cannot read field "index" because "insnNode" is null` —— 意思是**目标方法没有方法体**（抽象/接口），不是描述符错。
- **修正 FACTS 第 2 节路径表**：`_work\src\` 实际是 **38 个 `.java`**（s2 handoff 摘要里写的 49 个含非 java）。
- **新增工具**：`_tools\s3_run.ps1`（起服+等 `Done (`+RCON 停服，`-JavaOpts` 走 `JAVA_TOOL_OPTIONS`，必须配 `-NoDaemon` 才传给子 JVM）；
  `_tools\s3_version_check\VersionCheck.java`（版本谓词回归，13 用例）。
- **新增文档**：`analysis\DEVIATIONS.md`（原任务书 0 节说"写进 DEVIATIONS"的文件从本阶段开始存在）。

---

## 7. 发现但本阶段未处理

1. **`CCAUtils.isLoad(String ruleName, String modId, String versionPredicate)` 三参 deprecated 版没移植**。
   扫过 `_work\src` 全部 38 个 java，`isLoad\(` 的调用点只有 `AnnotationProcessor.java:11/18`（二参 String 版）与
   `CCAMixinConfigPlugin.java:15`（那是 `isEnableDebug`）。**结论：三参版零调用，暂不需要。** 若后续阶段从 `_src\cca1211`（1.21.4 代码）
   抄来调用点，再补。
2. **`AnnotationProcessor` 的 `versionPredicates` 是 `String`（FIELD 级注解）而不是 `String[]`**。
   s3 加数组重载后它仍解析到二参 String 版，行为不变；两套注解（FIELD 级 `utils.annotation.Condition` 与 TYPE 级
   `utils.annotation.mixin.Condition`）名字相同但包不同，s4 移植时**别 import 错**。
3. **`cca.mixins.json` 的 `mixins` 目前是 `[]`**（符合 s3 约束）。s4 起才会非空；届时若出现
   `[CCA-Mixin] SKIP` 行但没有 `@Restriction` 的 mixin，说明 plugin 的判定链被误触发，先看 `MixinRestrictionProcessor` 的 catch 分支。
4. **`_tools\s3_version_check\` 是裸 javac 工程**（依赖 `maven-artifact-3.8.5.jar` + `commons-lang3-3.12.0.jar`，路径在
   `C:\Users\xiao_\.gradle\caches\modules-2\files-2.1\...`）。它只是证据工具，不进构建；
   若以后改 `CCAUtils.matches`，记得把这里的方法体同步一份再跑。
5. **`CCAUtils` 的 static 初始化块会在 `-D` 值和默认值不同时打 `[CCA] -D{}={}` warn**。
   s9 全量回归时若带 `JAVA_TOOL_OPTIONS`，日志里会多这几行，属预期，不要当错误。
6. **mixin 包的目录**：`src\main\java\crystal0404\crystalcarpetaddition\mixins\` 现在**不存在**（测试 mixin 删净后空目录也被删）。
   s4 要自己建 `mixins\carpet\`。
