# DEVIATIONS — 移植版与 Fabric 原版的**有意**差异

> 只记录"我知道原版怎么做、但我故意不那么做"的条目。
> 属于"还没做、以后再补"的进 `handoff/HANDOFF-sN.md` 第 4 节，不写这里。
> 每条格式：**原版 → 移植版 → 理由 → 影响面**。

---

## s3 · 条件注入体系（`utils/condition/*`、`utils/CCAMixinConfigPlugin`）

### D-S3-1 `AnnotationCleaner` 不实现

- 原版：`RestrictiveMixinConfigPlugin.applyMixin(...)` 在 `preApply` / `postApply` 里用 ASM 把**目标类**上的
  `me.fallenbreath.conditionalmixin.api.annotation.Restriction` 注解擦掉（`AnnotationCleaner.eraseRestrictionAnnotation`）。
- 移植版：`CCAMixinConfigPlugin.preApply/postApply` 空实现。
- 理由：那是给 **Fabric 多 config 共用注解** 场景防"注解残留到运行时类"的。本工程单 config
  （`cca.mixins.json` 唯一），且我们的 `@Restriction` 是 `utils.annotation.mixin.Restriction`，
  与原库注解不同名不同包，不会和别的 mod 撞。
- 影响面：目标类字节码里会残留 `crystal0404...annotation.mixin.Restriction` 的 RUNTIME 注解。
  只影响反射扫描类注解的第三方工具，对本 mod 行为零影响。

### D-S3-2 `MemorizedRestrictionChecker`（判定缓存）不实现

- 原版：`checkRestriction` 结果按 mixin 类名缓存，避免同一 mixin 被多个目标类触发时重复解析字节码。
- 移植版：每次 `shouldApplyMixin` 都重新 `getClassNode` + 读注解。
- 理由：本工程 mixin 总数 22 个、`shouldApplyMixin` 调用次数在启动期是个位数到几十次，
  缓存省下的时间在噪声范围内；少一层状态就少一类"缓存脏了"的隐性问题。
- 影响面：启动期多几十次 ASM 类读取（微秒级）。

### D-S3-3 `isLoad` 的版本谓词**不用** `VersionRange.createFromVersionSpec` 单串解析

- 原版：`CCAUtils.isLoad(modId, versionPredicate)` → `me.fallenbreath.conditionalmixin.api.util.VersionChecker.doesModVersionSatisfyPredicate(...)`，
  内部用 conditional-mixin 自带的 `VersionPredicateParser`（Fabric 风格的语义化版本谓词解析）。
- 移植版：`CCAUtils.matches(actual, predicate)` 自己判：
  以 `[` / `(` 开头 → 交给 `VersionRange.createFromVersionSpec(...).containsVersion(...)`（区间语法）；
  以 `>=` `<=` `>` `<` `!=` `==` `=` 开头 → 剥前缀后 `DefaultArtifactVersion.compareTo`；
  含 `x` / `*` 或空串 → 前缀匹配 / 恒真。
- 理由（硬证据）：NeoForge 侧能用的 `org.apache.maven.artifact.versioning.VersionRange` 对**裸约束串**的语义是错的 ——
  `VersionRange.createFromVersionSpec(">=1.21.4").containsVersion(new DefaultArtifactVersion("1.21.1"))`
  实测返回 **true**（Maven 把不以 `[`/`(` 开头的串当成"推荐版本"而不是"硬约束"）。
  直接照抄任务书的 `VersionRange` 写法会让 `>=1.21.4` 在 1.21.1 上恒真，
  `RemoveVillagerTradeDistanceLimit` 就不会掉出列表，I12 的证据链直接断掉。
- 影响面：全部 `@Condition(versionPredicates=…)` 与 FIELD 级 `utils.annotation.Condition#versionPredicates`
  的判定结果。已用独立脚本 `_tools/s3_version_check/VersionCheck.java` 跑 13 条用例覆盖
  （`1.21.1 >= 1.21.4 → false`、`1.21.1 < 1.21.4 → true`、`1.4.147+v1.0.8-251027 >= 1.4.0 → true`、
  `[1.21.4,) → false`、`1.21.x → true`、`1.21.1 >= 1.21.1 → true`）。

### D-S3-4 mod 版本查找走**双通道**（`ModList` + `LoadingModList`）

- 原版：`FabricLoader.getInstance().getModContainer(modId)`，一个通道。
- 移植版：`CCAUtils.lookupModVersion(String)`：
  `ModList.get() != null` → `ModList.get().getModContainerById(modId).get().getModInfo().getVersion().toString()`；
  否则 → `net.neoforged.fml.loading.LoadingModList.get().getModFileById(modId)`，
  遍历 `fileInfo.getMods()` 按 modId 取 `IModInfo.getVersion().toString()`。
- 理由（硬证据）：`shouldApplyMixin` 在 **mixin config 解析期**被调用，那个时刻 FML 的 `ModList.get()` 还是 `null`。
  单通道写法会让**所有带 `@Restriction` 的 mixin 在第一次解析时被误判为 SKIP**
  （s3 首次 Group B 实测：`[CCA-Mixin] SKIP ... (require failed: MOD:carpet)` + 临时日志 `ModList==null`，
  证据 `_tools/out/s3_runB2.log`）。
- 影响面：`LoadingModList` 分支拿到的是 mod 文件元数据版本，与 `ModList` 分支同源（都来自 `neoforge.mods.toml` 的 `version`），
  两条通道结果一致；`minecraft` 永远走 `SharedConstants.getCurrentVersion().getName()` 特判，不受影响。

### D-S3-5 判定失败的日志：统一 INFO 打 `[CCA-Mixin] SKIP`

- 原版：`RestrictiveMixinConfigPlugin.onRestrictionCheckFailed` 只在 `CCAUtils.isEnableDebug()` 下打印，
  且用 `reason.matches(".*%s.*".formatted(mixinClassName))` 抑制"理由里已经含类名"的重复输出。
- 移植版：`MixinRestrictionProcessor` 在 require 失败 / conflict 命中 / 判定抛异常三种情况下，
  都打一条 `[CCA-Mixin-Restriction]` logger 的 INFO/WARN 行，格式固定：
  `[CCA-Mixin] SKIP <mixinClassName> (require failed: <describe>)`
  `[CCA-Mixin] SKIP <mixinClassName> (conflict hit: <describe>)`
  `[CCA-Mixin] SKIP <mixinClassName> (check error: <Throwable.toString()>)`
- 理由：s3 验收脚本按 `[CCA-Mixin]` + `CCA-Mixin-Restriction` grep；且 s4~s9 每个阶段都要靠这行确认
  "条件体系真的挡住了某个 mixin"，默认打出来比藏到 debug 后面有用。
  去重逻辑没移植：同一个 mixin 若被多个目标类触发会打多行，属于预期噪声。
- 影响面：日志量（每个被跳过的混入 1~N 行 INFO）。

### D-S3-6 `@Condition.tester()` 的 `Class.forName` 用 CCA 自己的 classloader

- 原版：`ConditionTester` 实例化走 `clazz.getConstructor().newInstance()`，类加载用 conditional-mixin 自己的 loader。
- 移植版：`Class.forName(testerType.getClassName(), true, MixinRestrictionProcessor.class.getClassLoader())`。
- 理由：tester 类（`CCAUtils$DisableEasterEggs` / `CCAUtils$EnableMagicSetting`）都在本 mod 内，
  用 `MixinRestrictionProcessor` 的 loader 必然能找到；显式传 loader 是为了避免在
  mixin 的 `TCL`（transforming classloader）上下文里 `Class.forName` 走到错误的一侧。
- 影响面：无（三个 `Type` 里只有 `TESTER` 走这条路径）。

### D-S3-7 `isLoad(String,String)` 与 `isLoad(String,String...)` 两个重载并存

- 原版：只有 `isLoad(String modId, String versionPredicate)` 和 deprecated 的
  `isLoad(String ruleName, String modId, String versionPredicate)` 三参版。
- 移植版：保留 s2 的二参 String 版（委托给数组版），新增数组版；
  **三参 deprecated 版不移植**。
- 理由：数组版是 s3 任务书要求（`@Condition.versionPredicates` 是 `String[]`）；
  三参版在 `_decomp` / `_work/src` 的 38 个源文件里**零调用点**（`Select-String 'isLoad\('` 全量扫过）。
- 影响面：无。

---

## s4 · `mixins/carpet/SettingsManagerMixin`（规则级条件注册 + 版本行着色）

### D-S4-1 `this` 与 `CCAExtension.CCASettingsManager` 的比较写成 `(Object) this ==`

- 原版：`if (this == CCAExtension.CCASettingsManager)`（反编译产物 `_decomp/mojmap/.../SettingsManagerMixin.java:65` 就是这个形态）。
- 移植版：`if ((Object) this == CCAExtension.CCASettingsManager)`。
- 理由（硬证据）：反编译产物把原始的类型转换擦掉了，照抄**编译不过**：
  `SettingsManagerMixin.java:71: 错误: 不可比较的类型: SettingsManagerMixin和SettingsManager`（javac，`==` 要求两侧可转换）。
  mixin 类本身不继承 `SettingsManager`，编译期只有 `(Object)` 能让 javac 接受；运行时 `this` 就是被织入的
  `SettingsManager` 实例，对象身份比较语义与原版**完全一致**。
- 影响面：无。`parseSettingsClassMixin_shouldRegister` 对 Carpet 自己的 `SettingsManager` 返回 `true`，
  只对 `CCASettingsManager` 走 `AnnotationProcessor.shouldRegister`。实测 `_tools/out/s4_run1.log` 仍然 13 条规则。

### D-S4-2 `CCAExtension.onGameStarted()` 多打 13 行 `[CCA] rule <name>`

- 原版：`onGameStarted()` 只 `parseSettingsClass(...)`，不逐条打印规则名。
- 移植版：`parseSettingsClass` 之后 `getCarpetRules().forEach(rule -> LOGGER.info("[CCA] rule {}", rule.name()))`，
  再打汇总行 `[CCA] registered {} rules`。
- 理由：s4 任务书 §1-5 要求"把 13 个规则名逐个打出来"作为验收硬证据（能看出掉的**具体**是哪一条，
  而不只是数字变化）。
- 影响面：启动日志每阶段多 13 行 INFO。**s9 全量回归时可删**（届时 14 条规则会变 14 行，属于噪声）。

---

## s5 · 规则批 A（6 条规则 / 9 个 mixin）

### D-S5-1 枚举规则不参与 `true`/`false` 冒烟

- 原版：`ShulkerBoxPowerOutputExpansionColour` 是 `ColourMap.Colour` 枚举规则，本来就不吃布尔值。
- 移植版：`_tools/out/s5_toggle.txt` 里保留了任务书 §4-5 的 26 条布尔命令 + 1 条合法枚举命令 `cca ShulkerBoxPowerOutputExpansionColour none`。
  其中枚举的两条 `true`/`false` 回显为 carpet 的校验拒绝：
  `Couldn't set value for rule ShulkerBoxPowerOutputExpansionColour: Valid values for this rule are: [none, white, …]`，
  **不是异常**（无 `Exception`、无 `ERROR`）。
- 理由：任务书 §4-5 的规则清单把 13 条一律写成 `true`/`false`，但枚举规则只有合法值列表；保留这两条是为了"13 条全覆盖"可核对，
  另加一条合法值证明枚举链路本身可写。
- 影响面：无。后续阶段的 toggle 脚本建议按"12 条 boolean × true/false + 1 条枚举取 2 个合法值"写。

### D-S5-2 §4-4 第 5 条的证据必须取 `javap` 文本，不能扫 `.class` 二进制

- 现象：`Select-String -LiteralPath EndGatewayBlock.class -Pattern 'DimensionTransition.DO_NOTHING'` → **MISS**，
  但 `javap -p -c` 输出里明确有 `6: getstatic #285 // Field net/minecraft/world/level/portal/DimensionTransition.DO_NOTHING:…`
  （就在注入生成的 `modify$zzf000$cca$createTeleportTargetMixin` 里）。
- 原因：常量池把"类全名"和"字段名"存成**两个独立 UTF8 项**，`A.B` 这种带点的串在二进制里根本不存在。
- 结论：9 条取证一律走 `& $J -p -c $f | Select-String -SimpleMatch <符号>`；本阶段 9/9 全 OK（`_tools/out/s5_bytecode.txt`）。

### D-S5-3 `-Dmixin.debug=true` 会打崩 carpet 自带的 mixin，只许用 `-Dmixin.debug.export=true`

- 现象（原文）：`carpet.mixins.json:MinecraftServer_pingPlayerSampleLimit` →
  `InvalidInjectionException: Constant modifier method modifyPlayerSampleLimit(I)I … expected 1 invocation(s) but 0 succeeded. Scanned 0 target(s).`
  → `MixinApplyError` → `MixinTransformerError`，服务器起不来。
- 处理：s5 起起服只传 `-Dmixin.debug.export=true`（导出照样完整，`.mixin.out/class` 137 个类）。
- 影响面：查注入失败原因时不能用 verbose/全部 debug 开关，只能靠导出字节码 + `mixin.debug.export`。

### D-S5-4 mixin 导出要求目标类**真的被加载**（PortalProcessor 取证踩坑）

- 现象：首轮导出目录里没有 `net/minecraft/world/entity/PortalProcessor.class`（132 个类），
  且 `execute as @e[type=zombie] run data get entity @s PortalCooldown` 恒为 `0`。
- 根因：当时用 `NoAI` 僵尸塞进 `minecraft:end_portal`，僵尸**不移动** → `Entity.move()` 不被调用 → `checkInsideBlocks()` 不跑
  → `EndPortalBlock.entityInside` 不触发 → `Entity.setAsInsidePortal` 不执行 → `PortalProcessor` 类永不加载 → 无导出。
  （`PortalProcessor.class` 只在实体真正进传送门时才 `<clinit>`/加载。）
- 处理：`forceload add 0 112 16 120` + `setblock 6 64 112 minecraft:nether_portal[axis=x]` + `summon minecraft:zombie 6.5 64.0 112.5`
  （**不带 NoAI**）→ 僵尸被传到下界（`Pos [2.0,59.0,14.5]`、`PortalCooldown 300`）→ 导出目录出现 `PortalProcessor.class`（4326 B），
  `getPortal` 6 处命中。
- 影响面：后续阶段凡 target 是"很少加载"的类（PortalProcessor、LootTable 内部类等），必须造一个真实触发路径，不能只起服。

---

## s6 · MagicBox 与 ItemShadowing（5 个 mixin）

### D-S6-1 `isLoad` 的 `minecraft` 分支改走 mod 版本查询（**与 D-S3-4 末句冲突，以本条为准**）

- 原版 / 移植版 v1（s3 实现）：`ModIds.MC` 特判 `SharedConstants.getCurrentVersion().getName()`。
- 移植版 v2（s6 修复）：`ModIds.MC` 先走 `lookupModVersion(ModIds.MC)`（`ModList` / `LoadingModList` 双通道），
  返回空才兜底 `SharedConstants.getCurrentVersion().getName()`；`catch (Throwable)` 从静默 `return false`
  改成 `LOGGER.warn("[CCA] isLoad({}) could not resolve version: {}", modId, t.toString())`。
- 理由（硬证据）：mixin config 解析期 `SharedConstants.getCurrentVersion()` 抛
  `java.lang.IllegalStateException: Game version not set`（`_tools/out/s6_diag1_prefix.log` 原文
  `[CCA] TEMP isLoad(minecraft) threw java.lang.IllegalStateException: Game version not set`），
  被原 `catch` 吞掉后整条 require 判 false，导致 `MagicBox` 的 `BlockEntityMixin` / `LecternBlockMixin` /
  `WorldChunkMixin` 三个 mixin 被 SKIP（`_tools/out/s6_run1_preversionfix.log` 三行 SKIP 原文）。
  而同一进程 20 秒后的规则注册阶段，同名调用返回 `actual='1.21.1'`——说明只有 mixin 阶段不可用。
- 为什么不改 `MixinRestrictionProcessor`：判定器语义没错，错的是"取 MC 版本"的数据源；任务书 §6 也明确
  写着"属 s3 的债，不要在 s6 里改处理器"。
- 影响面：所有带 `versionPredicates` 的 `@Condition`。s5 的 `@Condition("carpet")` 无谓词，走
  `versionPredicates.length == 0` 提前返回，**从不进入该分支**，所以 s3~s5 一直没暴露此 bug。
- 修订 D-S3-4 末句："`minecraft` 永远走 `SharedConstants…` 特判，不受影响"——该句作废。

### D-S6-2 `MagicBox` / `ItemShadowing` 的功能性行为未做真人级验证

- 原版：两条规则改的都是交互路径（`doClick` 的 `ClickType` 序列、讲台/潜影盒方块实体生命周期）。
- 移植版：只做到"注入成功（`javap` 字节码证据）+ 开关 true/false 冒烟不崩"。
- 理由：RCON 造不出真实 `ClickType`；`MagicBox` 的可观察差异需要开 GUI 看容器内容物。
  比较器读数不是好判别信号——`getRedstoneSignalFromBlockEntity` 与 `getRedstoneSignalFromContainer`
  对 `ShulkerBoxBlockEntity`（本身实现 `Container`）算出的值相同。
- 影响面：**功能未覆盖**，不是"已通过"。见 `handoff/HANDOFF-s6.md` §4.2 / §4.3。

### D-S6-3 `_tools\rcon_probe.py` 的两个硬限制（工具层，不是 mod 层）

- `data merge block … {… id:"minecraft:diamond" …}`：脚本把双引号吞掉，服务端收到无引号的
  资源位置 → `Expected '}'`。
- `item replace block <pos> container.N <item>`：本服务端恒回 `Incorrect argument for command`
  （插入符停在 `container.N` 之后）。因此**无法用 RCON 往容器里塞物品**。
- 影响面：所有"需要容器里有内容物"的探针（比较器信号、漏斗传输）都做不了，改用真人验证或在 s9 引入
  其它通道；不要在这两条上继续排查（不是本项目代码的问题）。

### D-S6-4 `_tools\out\s6_run1.log` 被重跑覆盖，失败现场另存

- `_tools\serve.ps1 -LogName X` 按同名覆盖 `_tools\out\X`。
- 处理：修复前的第一轮日志另存为 `s6_run1_preversionfix.log`，诊断日志另存为 `s6_diag1_prefix.log`，
  最终权威日志仍是 `s6_run1.log`（0 致命模式 / `Done (4.357s)!`）。
- 影响面：只是取证文件命名约定——**要留存失败现场必须先 `Copy-Item` 改名再重跑**。

---

## s7 · 箱子规则批（StackableShulkerBoxesEnhancement / ShulkerBoxPowerOutputExpansion / ReIntroduceOldVersionWitchLootTable）

### D-S7-1 `StackableShulkerBoxesEnhancement.lithium.HopperHelperMixin` **不移植**

- 原版：`mixins/rule/StackableShulkerBoxesEnhancement/lithium/HopperHelperMixin` 类级
  `@Restriction(require = @Condition("lithium"))`，mixin 目标 `net.caffeinemc.mods.lithium.common.util.collections.HopperHelper#tryMoveSingleItem`，
  在 Lithium 优化漏斗的路径上同样禁掉潜影盒合并。
- 移植版：**整个类不建、不写进 `cca.mixins.json`**。源码树里没有 `lithium` 目录（`Get-ChildItem -Filter *.java | ? FullName -match 'lithium'` = 0），
  `cca.mixins.json` 里 `lithium` 0 命中。
- 理由：Lithium 是 Fabric-only，1.21.1 的 NeoForge 侧不存在可用的 `lithium` 构件（FACTS §8.3 已认定），
  `require=@Condition("lithium")` 天然不成立。写进去只会每次启动多一条 `[CCA-Mixin] SKIP … (require failed: MOD:lithium)` 噪声，
  且一旦将来有人装了某个同名 modId 的非兼容 Lithium 分支，反而会去解析不存在的 `HopperHelper` 类。
- 影响面：装了 Lithium 的 Fabric 环境里，"漏斗按单件搬运时绕过合并检查"这条额外保护不存在；**原版漏斗路径由非 lithium 的
  `HopperBlockEntityMixin` 覆盖**（`tryMoveInItem` 内的 `canMergeItems`），NeoForge 侧无 Lithium 加速，行为等价。

### D-S7-2 `StackableShulkerBoxesEnhancement` / `ReIntroduceOldVersionWitchLootTable` 只做到注入级验证

- 原版：两条规则改的是"漏斗搬运合并判定"与"女巫死亡掉落表选择"，都是运行时行为。
- 移植版：`javap -p -c` 字节码证据 + `cca <rule> true/false` 冒烟不崩；**功能级探针未做**。
  `ShulkerBoxPowerOutputExpansion` 例外 —— 已用红石粉 `power=3`(开) / `power=1`(关) 端到端验证（`_tools/out/s7_rcon1.txt`）。
- 理由：① 漏斗需要"漏斗 → 已含同 NBT 潜影盒的容器"的真实传输，且要先核 `CarpetSettings.shulkerBoxStackSize` 默认值；
  ② 女巫掉落是 `setRolls(1..3)` 的随机表，单次击杀无法区分新旧表，判据需先设计（改跑 N 次统计，或退化为 `LootTableUtils.Witch`
  的条目级断言）。两条都已写进 `handoff/HANDOFF-s7.md` §4.1 / §4.2 并列为待拍板项。
- 影响面：**功能未覆盖**，不是"已通过"。s8/s9 不得据此宣称三批箱子规则全部验证完毕。

### D-S7-3 功能探针的判据必须走计分板（`say` 在 RCON 下无回包）+ 比较器 `facing` 是输入侧

- 现象：`execute if block 2 64 0 minecraft:redstone_wire[power=3] run say LEVEL3_ON` 在条件**成立**时 RCON 也只回空字符串，
  被误读成"条件不成立"，导致 s7 前半段反复怀疑注入点/几何朝向。
- 处理：所有布尔判据改为 `execute if … run scoreboard players set <holder> <obj> <value>` + `scoreboard players get`，
  回包形如 `r_on has 3 [s7]`（`_tools/out/s7_rcon1.txt`）。另：`setblock` 到**相同状态**回 `Could not set the block`
  且不触发邻居更新，逼比较器重算必须做真实变更（在上方 `setblock … stone` 再 `… air`）。
- 附带修正：`comparator` 的 `facing` 指向**输入侧** —— `comparator[facing=west]` 读西侧方块、朝东输出。
  实测：`0 64 0 = redstone_block` + `comparator[facing=west]` in `1 64 0` → `OutputSignal: 15` 且 `2 64 0` 红石粉 `power=15`；
  同布局 `facing=east` → `OutputSignal: 0`。
- 影响面：只影响验收脚本写法。**同时修正 D-S6-3 第二条**：`item replace block <pos> container.N with <item>` 是可用的
  （`Replaced a slot at 0, 64, 0 with [Stone]`），s6 那次失败是漏了 `with` 关键字（勘误 L12）。

---

## 通用

### D-GEN-1 PowerShell 不回写源码

- 所有 `.java` / `.json` / `.md` 一律用 write/edit 工具写入。
- 理由：本机 `pwsh` 是 Windows PowerShell 5.1（默认 GBK），`Set-Content -Encoding utf8` +
  `Get-Content -Raw` 往返会把中文注释变成 mojibake（s3 实测 `娉ㄨВ閲屾病鍐?`）。
- 影响面：无（工程内文件编码统一 UTF-8 无 BOM，编译器 `-encoding UTF-8` 已在 build.gradle 里）。

---

## s9 · 收尾与全量回归

### D-S9-1 client mixin（`rule.WhatIsThis.Happy`）的验证边界

- 原版：`SplashManager.getSplash()` 在 **3 月 25 日**当天以 1/3 概率把 splash 文案换成 `Happy birthday Crystal0404!!!`。
  （`calendar.get(2) == 2 && calendar.get(5) == 25`，`Calendar.get(2)` 是 0 基 MONTH ⇒ 2 = 三月；勘误 N12。）
- 移植版：**只验证了"mixin 应用 / 跳过"两种状态可被开关切换**，彩蛋内容本身未手测（需把系统日期改到 3 月 25 日）。
  - 启动 ①（无 `-D`）：日志出现
    `[00:14:21] [main/INFO] [CCA-Mixin-Restriction/]: [CCA-Mixin] SKIP crystal0404.crystalcarpetaddition.mixins.rule.WhatIsThis.Happy (conflict hit: TESTER:crystal0404.crystalcarpetaddition.utils.CCAUtils$DisableEasterEggs)`
  - 启动 ②（`-Dcca.disable.EasterEggs=false`）：**无** SKIP 行；日志有 `[CCA] -Dcca.disable.EasterEggs=false`；
    `javap -p -c run\.mixin.out\class\net\minecraft\client\resources\SplashManager.class` 命中
    `29: invokespecial #216  // Method handler$zbc000$cca$happyMixin:(...CallbackInfoReturnable;Ljava/util/Calendar;)V`，
    紧跟在 `12: invokevirtual #206  // Method java/util/Calendar.setTime:(Ljava/util/Date;)V` 之后 ⇒ `shift = AFTER` 精确命中。
- **判据偏差（必须在验收时知道）**：任务书 DoD#6 期望"`SplashManager.class` 存在但 javap 无 `happyMixin`"。
  实测 SKIP 时**该 `.class` 根本不存在** —— 因为 `shouldApplyMixin` 返回 false 后 Mixin 把 `SplashManager` 从目标列表移除，
  该类不再被 transformer 触碰，`-Dmixin.debug.export=true` 也就不导出它。
  实测导出类计数：启动 ① = **122**，启动 ② = **123**，差的正是 `SplashManager.class`。这是**同义证据**，且比"存在但无方法"更干净。
- 理由：彩蛋默认关闭（`DisableEasterEggs.isSatisfied` 直接返回 `getValue()`，且它在 `@Restriction(conflict = …)` 里，勘误 N9），
  所以"默认必须 SKIP、传 `-D…=false` 才 APPLIED"本身就是开关语义的完整证明。
- 影响面：功能覆盖 = 开关状态可切换（已证）+ 彩蛋文案未渲染验证（未证，需 3 月 25 日手测）。

### D-S9-2 修正 D-S7-2：`ReIntroduceOldVersionWitchLootTable` 已端到端验证通过

- D-S7-2 当时把女巫掉落表列为"只做到注入级验证"。s9 用**真打死女巫 + 漏斗收集**完成了 A/B（勘误 N6 推翻了 `loot insert` 方案）。
- 环境：`fill -3 -64 -3 3 -50 3 minecraft:air` 挖空深板岩；`0 -60 0` 箱子、`0 -59 0` 朝下漏斗；`gamerule doMobLoot true`、`difficulty easy`；
  实体 `summon minecraft:witch 0.5 -58.0 0.5 {NoAI:1b}` → `kill @e[type=minecraft:witch]` → 5s 后 `data get block 0 -60 0 Items`。
- **A（规则关，`_tools/out/s9_witch_off.txt`）**：`[{count: 1, Slot: 0b, id: "minecraft:gunpowder"}, {count: 5, Slot: 1b, id: "minecraft:redstone"}]`
  ⇒ 红石 `count=5 ≥ 4`，只可能来自原版双 pool 表（pool2 为 `redstone`、`rolls 1.0`、`set_count 4..8`，勘误 N7）。
- **B（规则开，跑 3 次，`_tools/out/s9_witch_on.txt`）**：
  run1 `[{count: 2, Slot: 0b, id: "minecraft:glass_bottle"}]`；
  run2 `[{count: 1, Slot: 0b, id: "minecraft:redstone"}]`；
  run3 `[{count: 2, Slot: 0b, id: "minecraft:sugar"}, {count: 2, Slot: 1b, id: "minecraft:spider_eye"}]`
  ⇒ 红石全程 `≤ 3`（或缺席），符合我方 `LootTableUtils.Witch` 的单 pool 结构。
- 影响面：规则有效（端到端）。D-S7-2 关于本条规则的"未验证"表述**自本节起失效**。

### D-S9-3 `StackableShulkerBoxesEnhancement` 在 NeoForge 上需要额外补偿 mixin（**已修复**）

- 原版（Fabric + vanilla 路径）：漏斗吐出物品走 `HopperBlockEntity.ejectItems` → `addItem` → `tryMoveInItem` → `canMergeItems`。
  我们的 mixin `@WrapOperation` 包 `canMergeItems`，规则开 + `CarpetSettings.shulkerBoxStackSize != 1` + 物品是 17 种潜影盒之一 ⇒ 返回 `false` ⇒ 漏斗不合并。
- 移植版初态：**注入成功但运行时零命中**。A/B 完全一致（`_tools/out/s9_hopper_off.txt` / `s9_hopper_on.txt` 均为 `[{count: 2, Slot: 0b, id: "minecraft:shulker_box"}]`）。
  - 注入本身没问题：`run\.mixin.out\class\net\minecraft\world\level\block\entity\HopperBlockEntity.class` 的 `tryMoveInItem` 偏移 **73** 有
    `invokestatic wrapOperation$zzm000$cca$transferMixin(ItemStack, ItemStack, Operation)Z`，其 handler 体条件链与源码逐字一致
    （`getstatic CCASettings.StackableShulkerBoxesEnhancement` → `getstatic CarpetSettings.shulkerBoxStackSize` → `iconst_1` → `if_icmpeq` → `ITEMS_SET.contains(first.getItem())` → `iconst_0 ireturn`）。
  - 三个开关条件实测全部满足：`/cca StackableShulkerBoxesEnhancement` = `current value: true (modified value)`；
    `carpet stackableShulkerBoxes true` ⇒ `StackableShulkerBoxValidator` 的 `"true".equalsIgnoreCase` 分支 `bipush 64 putstatic shulkerBoxStackSize`（默认 1），且实测 `maxStack ≥ 5`。
  - **根因（决定性字节码）**：`HopperBlockEntity.ejectItems(Level, BlockPos, HopperBlockEntity)` 偏移
    **`35: aload_2` `36: invokestatic net/neoforged/neoforge/items/VanillaInventoryCodeHooks.insertHook(HopperBlockEntity)Z` `39: ifeq 44` `42: iconst_1` `43: ireturn`**。
    `VanillaInventoryCodeHooks.insertHook` 内部是 `getAttachedItemHandler(...).map(hopper -> ...).orElse(false)`；
    NeoForge 给所有原版 `Container` 都挂了 `IItemHandler` capability ⇒ 目标容器只要暴露该能力就返回 **true** ⇒ `ejectItems` 直接 `ireturn`。
    vanilla 的 `44-` 之后（`getAttachedContainer` → 循环 → 偏移 **123** `addItem` → `tryMoveInItem` → `canMergeItems`）**整段被跳过**。
  - 因此这不是配置/版本问题，而是**移植缺陷**：Fabric 环境没有 `insertHook`，NeoForge 有。
- **修复（已实施，方案 ①）**：新增 `crystal0404.crystalcarpetaddition.mixins.rule.StackableShulkerBoxesEnhancement.HopperBlockEntityEjectMixin`
  （上游没有这个类，是 NeoForge 专属补偿）：
  ```java
  @WrapOperation(method = "ejectItems",
          at = @At(value = "INVOKE",
                   target = "Lnet/neoforged/neoforge/items/VanillaInventoryCodeHooks;insertHook(Lnet/minecraft/world/level/block/entity/HopperBlockEntity;)Z"))
  private static boolean cca$fallbackToVanillaPath(HopperBlockEntity hopper, Operation<Boolean> original) {
      if (cca$shouldFallbackToVanilla(hopper)) {
          return false;                 // 让 ejectItems 继续走 vanilla 的 addItem 路径
      }
      return original.call(hopper);
  }
  ```
  `cca$shouldFallbackToVanilla` 四条同时成立才回退：① `CCASettings.StackableShulkerBoxesEnhancement`；
  ② `CarpetSettings.shulkerBoxStackSize != 1`；③ 目标方块实体 `instanceof Container`（避免破坏"纯 `IItemHandler` 的 mod 容器"）；
  ④ 漏斗 5 槽中确实有 `ShulkerBoxesSet.ITEMS_SET` 里的潜影盒。
- **实施踩坑（已修，写在这里免得后人再踩）**：第一版写成 `boolean hooked = original.call(hopper);` 再检查 `hopper.getItem(i)`，
  结果 `original.call` 内部**真的把物品搬走了**，检查时漏斗已空 ⇒ 永远判成"没有潜影盒"、永远返回 `true`，A/B 依旧一致。
  诊断原文：`EJECT called: hooked=true rule=true stackSize=64 pos=BlockPos{x=8, y=-60, z=0}` / `-> keep true (no shulker box in hopper)`。
  ⇒ **必须先判条件、需要回退时根本不调用 `original`**。
- **修复后 A/B（`_tools/out/s9_hopper_final_{off,on}.txt`）**：
  - A（`cca StackableShulkerBoxesEnhancement false`）：`[{count: 2, Slot: 0b, id: "minecraft:shulker_box"}]` —— 合并，NeoForge 原行为不变。
  - B（`cca StackableShulkerBoxesEnhancement true`）：`[{count: 1, Slot: 0b, id: "minecraft:shulker_box"}, {count: 1, Slot: 1b, id: "minecraft:shulker_box"}]` —— 不合并，规则生效。
  - 前提 `carpet stackableShulkerBoxes true` 必须同时满足（`server.properties` 之外的 carpet 配置，重启后可能回到 `false`，实测需每次重设）。
- 影响面：`cca.mixins.json` 的 `mixins` 由 **21 → 22**，`src/main/java` 由 **40 → 41** 个文件；DoD#2 的期望值相应改为 `mixins=22 / client=1`。
- 残留风险：对"只暴露 `IItemHandler`、不是 `Container`"的 mod 容器，我们**不回退**（保持 NeoForge 能力派发），
  此时上游那条 `transferMixin` 仍不会触发 ⇒ 这类容器上的漏斗仍会合并潜影盒。属已知边界，不做进一步侵入。

### D-S9-4 `ReIntroduceOldVersionRaid.RaiderEntityMixin` 的"旗帜 → BAD_OMEN"仍只有字节码级证据（取代 D-S8-1）

- 原版：巡逻队长被玩家击杀 → 击杀者获得 `bad_omen`（时长 120000 tick，等级随已有 bad_omen 累加，上限 4）。
- 移植版：`handler$zbb000$cca$onDeathMixin` 确实编译进 `Raider.die`（偏移 **69**，前置于 `super.die` 的调用），
  handler 条件链完整（`CCASettings.ReIntroduceOldVersionRaid` → `isPatrolLeader()` → `getCurrentRaid() == null` →
  `getRaidAt(blockPosition()) == null` → `getItemBySlot(HEAD)` 非空 → `cca$getPlayerEntity(damageSource.getEntity())` →
  `cca$hasBanner(ItemStack)` → `MobEffects.BAD_OMEN` 累加 → `GameRules.RULE_DISABLE_RAIDS` → `Player.addEffect`）。
- **端到端探针失败**（§4.11 有"3 次预算"，实际用掉 4 轮行为探针 + 5 轮构造尝试）。失败点定位在 handler 内某一条件为 `false`：
  - 旗帜构造已做到 NBT 完全等价：`item replace entity @e[type=minecraft:pillager,limit=1] armor.head with minecraft:white_banner[
      minecraft:banner_patterns=[{pattern:"rhombus",color:"cyan"},{pattern:"stripe_bottom",color:"light_gray"},
      {pattern:"stripe_center",color:"gray"},{pattern:"border",color:"light_gray"},{pattern:"stripe_middle",color:"black"},
      {pattern:"half_horizontal",color:"light_gray"},{pattern:"circle",color:"light_gray"},{pattern:"border",color:"black"}],
      minecraft:hide_additional_tooltip={},minecraft:item_name='{"translate":"block.minecraft.ominous_banner"}'`
    → 回 `Replaced a slot on Pillager with [Ominous Banner]`，`data get ... ArmorItems` 三类组件齐全。
  - 已排掉的外部因素：`gamerule disableRaids=false`、`doMobLoot=true`、规则为 `true`、6 层版本同样失败、
    移到 y=200 高空（避开村庄 `getRaidAt` 干扰）同样失败、`PersistenceRequired:1b` + 玩家 `tp` 到旁边排掉 despawn。
  - 候选失败原因：(a) `summon … {PatrolLeader:1b}` 是否同步到 `isPatrolLeader()` 读的字段未验证；
    (b) `cca$hasBanner` 用 `ItemStack.isSameItemSameComponents`，而 `item_name` 经 SNBT 只能写成字符串，
    与 `Component.translatable(...)` 的相等性未证实；(c) `damage … by <entity>` 构造的 `DamageSource.getEntity()` 是否等于该玩家未证实。
- 影响面：规则开关、注入位置、条件链均有证据；**"击杀队长 → 获得 bad_omen"这一条未被端到端证明**。
  未放宽 `isSameItemSameComponents` 比较去凑结果。

### D-S9-5 勘误 N8 的灾厄旗帜 SNBT 在 1.21.1 不可用（pattern 注册名不是常量名）

- 勘误 N8 给的 8 层 SNBT 用 `pattern:"rhombus_middle"` / `"circle_middle"`（照抄 `BannerPatterns.RHOMBUS_MIDDLE` / `CIRCLE_MIDDLE` 常量名）。
  实测报
  `Malformed 'minecraft:banner_patterns' component: 'Failed to get element ResourceKey[minecraft:banner_pattern / minecraft:circle_middle]; Failed to get element ResourceKey[minecraft:banner_pattern / minecraft:rhombus_middle]'`。
- 正确写法：**命令注册名是 `rhombus` / `circle`**（`data get` 回显 `pattern: "minecraft:rhombus"` / `"minecraft:circle"`）。
- 另：`minecraft:item_name` 组件在 SNBT 里**只接受字符串** —— `item_name={translate:"block.minecraft.ominous_banner"}` 报
  `Malformed 'minecraft:item_name' component: 'Not a string'`；必须写 `item_name='{"translate":"block.minecraft.ominous_banner"}'`。
- 影响面：只影响探针脚本。后续写 `RULE-TEST.md` / 复现命令时按本条。

### D-S9-6 `_tools\rcon_probe.py` 的两个输入侧坑（勘误 N1 的补充）

- 读循环丢包（N1 已记）：`say` 信标在服务端日志里有、RCON 回包里空。**判据一律回读日志**。
- **新增**：命令行含 SNBT 双引号（如 `item_name='{"translate":"…"}'`）时，经 PowerShell 作为 argv 传给 `rcon_probe.py` 会被吃掉引号。
  处置：新增 `_tools\rcon_file.py`（读 UTF-8 命令文件，每行一条、跳过空行与 `#` 开头，协议与 `rcon_probe.py` 相同），
  用法 `& $py _tools\rcon_file.py 127.0.0.1 25575 probe123 <cmdfile>`。
- **新增**：`forceload add` 等重操作会让服务端报告 `Can't keep up! … Running 10122ms or 202 ticks behind`，
  触发 RCON 读超时（`ConnectionAbortedError: [WinError 10053]`）。处置：**单批 ≤ 6 条，forceload 不与重操作同批**。
- 影响面：只影响验收脚本写法，不影响 mod 行为。
