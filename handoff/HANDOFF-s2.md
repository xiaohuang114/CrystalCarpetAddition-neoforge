# HANDOFF s2 — 入口 / CarpetExtension / 设置类（14 条规则进 Carpet）

- 日期：2026-10-25
- 本阶段结论：**PASS**
- 工程根：`D:\Minecraft-mod-development\cca移植neoforge\cca-neoforge`

---

## 1. 已落地文件（路径 + 作用 + 行数）

路径相对 `cca-neoforge\src\main\`。

| 路径 | 行数 | 作用 |
|---|---|---|
| `java/crystal0404/crystalcarpetaddition/CrystalCarpetAdditionMod.java` | 28 | `@Mod` 入口。构造器里**先**读版本再 `CCAExtension.init()` |
| `java/crystal0404/crystalcarpetaddition/CCAExtension.java` | 42 | `CarpetExtension` 实现；`manageExtension` + `onGameStarted` 里 `parseSettingsClass` + 两条统计日志 |
| `java/crystal0404/crystalcarpetaddition/CCASettings.java` | 67 | 14 条 `@Rule` 字段（原样移植）+ `CCA_CATEGORY` 公开常量 |
| `java/crystal0404/crystalcarpetaddition/utils/CCAUtils.java` | 56 | 最小版：`isFalse` / `isEnableDebug` / `isLoad`（含 `minecraft` 特判） |
| `java/crystal0404/crystalcarpetaddition/utils/Key.java` | 21 | 原样移植（记录 rule/分类/选项三连键） |
| `java/crystal0404/crystalcarpetaddition/utils/EmptyClass.java` | 4 | 原样移植（占位） |
| `java/crystal0404/crystalcarpetaddition/utils/ModIds.java` | 17 | modId 常量；`FABRIC_*` 保留并加注释，新增 `MC = "minecraft"` |
| `java/crystal0404/crystalcarpetaddition/utils/LootTableUtils.java` | 67 | `Witch(RegistryLookup<Enchantment>)` 重建女巫战利品表（**按 1.21.1 真实 API 手写**，见 §2.5） |
| `java/crystal0404/crystalcarpetaddition/utils/annotation/Condition.java` | 14 | FIELD 级注解 `value()` + `versionPredicates()` |
| `java/crystal0404/crystalcarpetaddition/utils/annotation/Restriction.java` | 14 | FIELD 级注解 `require()` / `conflict()` |
| `java/crystal0404/crystalcarpetaddition/utils/annotation/impl/AnnotationProcessor.java` | 41 | `shouldRegister(Restriction, String)`：先 conflict 后 require |
| `java/crystal0404/crystalcarpetaddition/utils/shulkerBoxUtils/ColourMap.java` | 53 | `Colour` 枚举 17 值（`NONE` + 16 色），映射 `DyeColor`；常量名/顺序未动 |
| `java/crystal0404/crystalcarpetaddition/utils/shulkerBoxUtils/ShulkerBoxesSet.java` | 27 | `Set<Item>`，用 `Items.*_SHULKER_BOX` |
| `resources/assets/cca/lang/en_us.json` | 2238 B | **从 `_ref\cca-mojmap.jar` 原样抽出**，未改任何键 |
| `resources/assets/cca/lang/zh_cn.json` | 2693 B | 同上 |
| `resources/cca.mixins.json` | 216 B | **未改动**：`"mixins": []`、`"client": []`、无 `plugin` 字段 |
| `resources/META-INF/neoforge.mods.toml` | 822 B | **未改动**（s1 终态，`dependencies.cca` 里 `carpet` 是 `required` + `ordering="BEFORE"`） |
| `resources/META-INF/accesstransformer.cfg` | 315 B | **未改动** |

未写、按计划留给 s3：`mixins\**`、`utils\CCAMixinConfigPlugin.java`、`utils\annotation\mixin\*`（TYPE 级 `Condition`/`Restriction`）、`utils\condition\*`。

---

## 2. 本轮关键决策（含“为什么”）

### 2.1 语言文件用 ZipFile 抽，不手敲
用 `System.IO.Compression.ZipFile` 从 `_ref\cca-mojmap.jar` 里抽取 `assets/cca/lang/{en_us,zh_cn}.json`。理由：硬性约束“不许改键名、不许自己加键”，手抄必然错。抽取后未做任何编辑。

### 2.2 `canHasTranslations` 返回 `Map<String,String>`
按勘误 F1：
```java
return Translations.getTranslationFromResourcePath("assets/cca/lang/%s.json".formatted(lang));
```
实测生效：RCON 里分类显示为 `[MC Tweaks]`、`[Creative Tools]`（lang 键 `carpet.category.MCTweaks` / `carpet.category.Creative` 被套用了），证明 `en_us.json` 真的被 carpet 读到了。

### 2.3 版本号读取与静态初始化顺序
`CrystalCarpetAdditionMod` 构造器里：
```java
String v = container.getModInfo().getVersion().toString();   // "1.11.1-neoforge"
if (v != null && !v.isBlank()) version = v;
CCAExtension.init();                                          // 必须在赋值之后
```
`getModInfo().getVersion()` 实测返回 `org.apache.maven.artifact.versioning.ArtifactVersion`（`javap` 于 fancymodloader `loader-4.0.45.jar`），`toString()` 合法；备用回退常量 `"1.11.1"`（`catch (Throwable)`）。**不要**用 `getQualifier()`（E7/F12）。

实测日志证明顺序正确：`[CCA] version=1.11.1-neoforge CCA-category rules=14`（不是 `0.0.0`）。

### 2.4 `isLoad("minecraft", …)` 的实测与处理（任务书要求实测的点）
- 原始写法（裸 `ModList.isLoaded("minecraft")` + `getModContainerById("minecraft").getModInfo().getVersion()`）实测 **可用**：
  `[CCA] probe raw ModList.isLoaded(minecraft)=true containerVersion=1.21.1`
- 即便如此，`CCAUtils.isLoad` 仍保留特判（更稳，且不依赖 NeoForge 伪 mod 的未来行为）：
```java
if (ModIds.MC.equals(modId)) {
    actual = SharedConstants.getCurrentVersion().getName();     // "1.21.1"
} else { … ModList … }
return VersionRange.createFromVersionSpec(versionPredicate)
        .containsVersion(new DefaultArtifactVersion(actual));
```
- **结论**：`>=1.21` / `>=1.20.5` 全部满足；`>=1.21.4`（`RemoveVillagerTradeDistanceLimit`）不满足。四条 MC 版本相关规则（`EnderDragonPartCanUseEndPortal`、`GatewayCannotLoadingChunks`、`ReIntroduceOldVersionRaid`、`ReIntroduceOldVersionWitchLootTable`）**条件写法不用改**。
- probe 日志已在交付前删除（`SharedConstants` / `CCAUtils` 的 import 一并清掉），最终 `CrystalCarpetAdditionMod.java` 里没有任何 `[CCA] probe` 行。

> 注意：日志时间戳 23:14 那次是“带 probe 的中间版本”，23:15 那次才是最终代码；两次都 `registered 14 rules`。

### 2.5 `LootTableUtils` 按真实 API 重写（不是逐字复制）
反编译产物 + `_mappings/inter_to_mojmap.tiny` 在战利品这块的名字是**错的**，照抄编不过。以 `javap`（mojmap `compiledWithNeoForge_*.jar`）为准手写：

- `LootPool.lootPool()` → `Builder`；`Builder.setRolls(NumberProvider)` / `setBonusRolls(NumberProvider)` / `add(LootPoolEntryContainer.Builder<?>)` / `build()`
- `LootItem.lootTableItem(ItemLike)` → `LootPoolSingletonContainer.Builder<?>`；`setWeight(int)` / `setQuality(int)` / `apply(LootItemFunction.Builder)`
- `SetItemCountFunction.setCount(NumberProvider)`
- `new EnchantedCountIncreaseFunction.Builder(Holder<Enchantment>, NumberProvider)`
- `UniformGenerator.between(float, float)` / `ConstantValue.exactly(float)`
- `LootTable.lootTable().withPool(LootPool.Builder).build()`
- `RegistryLookup<Enchantment>.getOrThrow(Enchantments.LOOTING)` → `Holder<Enchantment>`

保留的语义：7 个条目 `GLOWSTONE_DUST / SUGAR / REDSTONE / SPIDER_EYE / GLASS_BOTTLE / GUNPOWDER / STICK`；每个 `setCount(uniform(0,2))` + `enchantedCountIncrease(looting, uniform(0,1))`；`STICK` 额外 `setWeight(2)`；池 `setRolls(uniform(1,3))`、`setBonusRolls(constant(0))`。

### 2.6 `CCAUtils` 最小化边界
只保留 `isFalse(String)`、`isEnableDebug()`、`isLoad(String,String)`。`isEnableDebug()` 用 `Boolean.getBoolean("cca.enable.debug")` 替代原版的 `DisableEasterEggs`/`EnableMagicSetting` 条件体系（原版那两条实现 `me.fallenbreath.conditionalmixin.api.mixin.ConditionTester`）。移除 `me.fallenbreath.*` 与 `org.spongepowered.asm.service.MixinService` 的 import，不改其它逻辑。

### 2.7 `CCASettings` 的唯一改动（任务 6）
新增一行公开常量，其余 67 行里 14 个字段、默认值、`categories` 字面量全部原样：
```java
/** Public alias of {@link #CCA}, added in s2 so other classes can count CCA-category rules. */
public static final String CCA_CATEGORY = "CCA";
```
用途：`CCAExtension.onGameStarted` 里统计 CCA 分类条数（供 s4 过滤后对比）。**没有**加 `conditions = …`（勘误 F11）。

### 2.8 依赖语义（重申，别在 s3 走偏）
CCA 只做 addon：不 shade、不 fork、不内嵌 carpet。运行时靠 `build.gradle` 的 `implementation files("libs/${project.carpet_jar}")`（`libs\neoforge-carpet-1.21.1-1.0.8+v251027.jar`，1,498,683 B）；元数据里 `carpet` 是 `required` + `ordering="BEFORE"`（F6，不要改）。

### 2.9 没有动的配置
`build.gradle` / `neoforge.mods.toml` / `accesstransformer.cfg` / `cca.mixins.json` 一行未改；未启用 configuration cache（F14）。

---

## 3. 验收结果（命令 + 关键输出原文）

### 3.1 编译 / 打包

```
> .\gradlew.bat build --console=plain
> Task :compileJava
> Task :processResources UP-TO-DATE
> Task :jar
> Task :sourcesJar
> Task :build

BUILD SUCCESSFUL in 3s
5 actionable tasks: 3 executed, 2 up-to-date
```

产物：
```
Name                                                              Length
CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge-sources.jar  45237
CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar          52644
```
jar 内容（`ZipFile` 列举，节选）：`crystal0404/crystalcarpetaddition/{CCAExtension,CCASettings,CrystalCarpetAdditionMod}.class`、`utils/*.class`、`utils/annotation/**`、`utils/shulkerBoxUtils/{ColourMap,ColourMap$Colour,ShulkerBoxesSet}.class`、`assets/cca/lang/en_us.json`、`assets/cca/lang/zh_cn.json`、`cca.mixins.json`、`icon.png (31755)`、`META-INF/{neoforge.mods.toml,accesstransformer.cfg}`。

### 3.2 启动（`_tools\out\s2_runServer.log`，最终代码）

```
		Carpet: NeoForged 1.4.147+v1.0.8-251027 (carpet)
		Crystal Carpet Addition 1.11.1-neoforge (cca)
[23:15:51] [modloading-worker-0/INFO] [cr.cr.CrystalCarpetAdditionMod/]: [CCA] registered 14 rules
[23:15:51] [modloading-worker-0/INFO] [cr.cr.CrystalCarpetAdditionMod/]: [CCA] version=1.11.1-neoforge CCA-category rules=14
[23:16:01] [Server thread/INFO] [minecraft/DedicatedServer]: Done (4.383s)! For help, type "help"
```

错误扫描（`Mixin apply failed|ClassNotFoundException|NoClassDefFoundError|NoSuchMethodError|[ERROR]|[FATAL]`）：**0 命中**。
启动脚本本身 `BUILD SUCCESSFUL in 54s`（含 `createMinecraftArtifacts`），`stop` 后 25565 端口 `port: 0`。

### 3.3 RCON 验证（`_tools\out\s2_rcon.txt` 原文）

```
===== CMD: cca =====
Current Crystal Carpet Addition settings:
Crystal Carpet Addition version: 1.11.1-neoforge
Browse categories:
[CCA] [MC Tweaks] [Creative Tools]
```

```
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
- RemoveVillagerTradeDistanceLimit [true] [false]
- ShulkerBoxPowerOutputExpansion [true] [false]
- ShulkerBoxPowerOutputExpansionColour [none] [white] [light_gray] [gray] [black] [brown] [red] [orange] [yellow] [lime] [green] [cyan] [light_blue] [blue] [purple] [magenta] [pink]
- StackableShulkerBoxesEnhancement [true] [false]
```

14 条，顺序与 `CCASettings` 字段顺序一致；`ShulkerBoxPowerOutputExpansionColour` 的默认值是 `pink`（`cca ShulkerBoxPowerOutputExpansionColour` 回显 `current value: pink (default)`）。

```
===== CMD: carpet =====
Current Carpet Mod settings:
Carpet Mod version: 1.4.147+v1.0.8-251027
Browse categories:
[dispenser] [feature] [optimization] [scarpet] [survival] [client] [experimental] [TNT] [bugfix] [creative] [command]
```
（carpet 主命令只列主 settings manager 的分类，CCA 的分类不在这里 —— 见 §4.1。）

### 3.4 规则可读写（写回验证）

```
===== CMD: cca MagicBox true =====
MagicBox: true, [Change permanently?]

===== CMD: cca MagicBox =====
MagicBox/CCESuppressor (MagicBox)
Control whether CastSuppressor(CCESuppressor) is available
Tags: [CCA], [MC Tweaks]
current value: true (modified value)
Options: [ true false ]

===== CMD: cca MagicBox false =====
MagicBox: false, [Change permanently?]
```
即 `CarpetRule#set` 链路通，s4 的 `SettingsManagerMixin` 有地方挂钩。

---

## 4. 未解决 / 踩坑（现象 + 已排查到什么程度）

### 4.1 任务书里的 `carpet CCA` 不存在 —— 真正的入口是 `/cca`
- 现象：`carpet CCA` → `Unknown rule: CCA`
- 原因（反编译 `carpet/api/settings/SettingsManager.java` 确认）：扩展的 settings manager 会**用自身 identifier 注册独立的命令根**，`/carpet` 只服务主 settings manager。所以 CCA 的命令是 `/cca`、`/cca list <category>`、`/cca <RuleName> [value]`。
- 已核对：`SettingsManager(String version, String identifier, String fancyName)` 的 `identifier` 传的就是 `"cca"`，与此一致。
- **不是 bug**，DoD 用 `cca list CCA` 满足（§3.3）。s9 回归脚本请改用 `/cca`。

### 4.2 `_mappings/inter_to_mojmap.tiny` 的战利品成员名不可信
- 现象：按 tiny 映射写 `LootPool.Builder.add(LootPoolEntryContainer.Builder)` 等，成员名对不上。
- 已排除：不是 jar 缺失——`_ref\mc-1.21.1-client.jar` 是**混淆** jar（无 `LootPool`），查类必须用 `C:\Users\xiao_\.gradle\caches\neoformruntime\intermediate_results\compiledWithNeoForge_*.jar`。
- 结论：凡涉及 MC 内部 API，一律 `javap` 该 jar 为准，§2.5 已落到代码。

### 4.3 `RaiderEntityMixin` 里一处映射歧义（留给 s3）
`_work\src\...\mixins\rule\ReIntroduceOldVersionRaid\RaiderEntityMixin.java` 的 `method_6118` 在映射表里同时对应 `makeBrain` 与 `setItemSlot`，脚本没法自动定。s3 移植该 mixin 时必须 `javap` 真实方法据签名人工判定。

### 4.4 `zh_cn.json` 未做运行时验证
语言切换是客户端行为，专用服务器只走 `en_us`。文件字节数与 jar 内原样一致即视为满足；如要实证，得开 `runClient` 改语言。

---

## 5. 下一阶段起点（s3：mixin 骨架 + 条件体系）

- **第一个待办**：`CCAMixinConfigPlugin`（实现 NeoForge 的 mixin config plugin 接口，替代 `me.fallenbreath.conditionalmixin.api.mixin.RestrictiveMixinConfigPlugin`）+ 把 `cca.mixins.json` 的 `mixins` 列表填上第一组规则 mixin。
- **必读文件**：
  - `handoff\HANDOFF-s1.md`（构建/启动/RCON 工具链）
  - `handoff\HANDOFF-s2.md`（本文件，尤其 §4.1 命令入口、§2.4 版本条件）
  - `_work\src\crystal0404\crystalcarpetaddition\utils\CCAMixinConfigPlugin.java`（原版逻辑）
  - `_work\src\...\utils\CCAUtils.java`（缺的 `tryFindClass` / `JAVA_PARAMETERS` / `DisableEasterEggs` / `EnableMagicSetting`）
  - `prompts\FACTS.md`（叠加本文件 §6）
- **前置检查命令**：
```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.11"
cd "D:\Minecraft-mod-development\cca移植neoforge\cca-neoforge"
.\gradlew.bat build --console=plain      # 应仍 BUILD SUCCESSFUL，14 条规则不变
```

### 5.1 `CCAUtils` 仍缺的符号（s3 必补）
`tryFindClass(String)`、`JAVA_PARAMETERS` 开关打印、`DisableEasterEggs`、`EnableMagicSetting`（后两者原是 `ConditionTester` 实现，1.21.1 构建**不需要** `EnableMagicSetting`，见勘误 F11）。

### 5.2 因 `me.fallenbreath.conditionalmixin.*`（及 fabric）而完全未移植的文件（共 9 个 mixin + 1 个 plugin）
1. `utils\CCAMixinConfigPlugin.java` —— `RestrictiveMixinConfigPlugin`
2. `mixins\carpet\SettingsManagerMixin.java` —— TYPE 级 `@Condition/@Restriction` + `@Coerce`
3. `mixins\rule\ItemShadowing\ScreenHandlerMixin.java`
4. `mixins\rule\MagicBox\BlockEntityMixin.java`
5. `mixins\rule\MagicBox\LecternBlockMixin.java`
6. `mixins\rule\MagicBox\WorldChunkMixin.java`
7. `mixins\rule\RemoveVillagerTradeDistanceLimit\MerchantScreenHandlerMixin.java`
8. `mixins\rule\StackableShulkerBoxesEnhancement\HopperBlockEntityMixin.java`
9. `mixins\rule\StackableShulkerBoxesEnhancement\lithium\HopperHelperMixin.java`
10. `mixins\rule\WhatIsThis\Happy.java` —— 还额外 import 了 `me.fallenbreath.conditionalmixin.api.annotation.Condition.Type`
（`utils\CCAUtils.java` 也已重写为最小版，原版那三个 fallenbreath/spongepowered import 被移除。）

---

## 6. 对 FACTS.md 的修正 / 新增（下一阶段请按这些覆盖 FACTS）

1. **命令入口**：CCA 的设置命令是 **`/cca`**（`/cca list <cat>`、`/cca <Rule> [value]`），**不是** `/carpet CCA`；`/carpet` 的分类列表里也不会出现 `[CCA]`。`CarpetServer.manageExtension` 后每个扩展自带命令根。
2. **`isLoaded("minecraft")`**：NeoForge 下 `ModList.isLoaded("minecraft") == true` 且 `containerVersion = 1.21.1`，**裸用也可行**；本工程仍保留 `SharedConstants.getCurrentVersion().getName()` 特判。
3. **`IModInfo.getVersion()`** 返回 `ArtifactVersion`（不是字符串、不是 `VersionRange`）→ `.toString()` 得 `1.11.1-neoforge`。
4. **战利品 API**（1.21.1 mojmap 实测，覆盖 FACTS 里任何旧写法）：`LootPool.lootPool().setRolls/setBonusRolls/add/build()`、`LootItem.lootTableItem(...).setWeight(int)`、`EnchantedCountIncreaseFunction.Builder(Holder<Enchantment>, NumberProvider)`、`RegistryLookup.getOrThrow(Enchantments.LOOTING)`。
5. **`carpet.api.settings.CarpetRule`**：`categories()` 是 `Collection<String>`；`SettingsManager.getCarpetRules()` 是 `Collection<CarpetRule<?>>`（F2/F3 已实测沿用）。
6. **RCON 客户端**：`_tools\rcon_probe.py 127.0.0.1 25575 probe123 "<cmd>" ["<cmd>" …]` 支持一次多条；`stop` 收尾后端口会释放。
7. **`gradlew` 起服务端的方式**：必须 `cmd /c set JAVA_HOME=... && .\gradlew.bat runServer --console=plain > log 2>&1`，`Start-Process` 里直接写 `$env:` 不生效。

---

## 7. 发现但本阶段未处理

- `_work\src` 里存在 `mixins\rule\WhatIsThis\Happy.java`（彩蛋 mixin），但它**不对应 14 条规则里的任何一条**；s3 决定是否保留、以及由谁的门控开关管理。
- `StackableShulkerBoxesEnhancement\lithium\HopperHelperMixin.java` 依赖 lithium 内部类（`net.caffeinemc`），NeoForge 1.21.1 无对应 lithium 版本 → s3 需要“不存在就跳过”的处理策略，不能硬编进 mixins 列表。
- `RemoveVillagerTradeDistanceLimit` 的 `>=1.21.4` 条件在 1.21.1 恒不满足，s4 过滤后规则数会从 14 变 13（设计如此，s2 的 DoD 是 14）。
- `ShulkerBoxesSet` 的 `Items.*` 常量顺序来自映射表（前 5 个 varargs + 其余 `new Item[]{…}`），s3 使用前建议再 `javap` 一次 `Items` 字段名确认无漏色。
- `icon.png` 在 jar 根（31,755 B）且 `logoFile="icon.png"` 生效；`processResources` 的 `expand` 只作用于 toml，不会碰二进制。
