# HANDOFF s0 — 物料核对与移植矩阵

- 日期：2026-10-25
- 本阶段结论：PASS
- 工程根：`D:\Minecraft-mod-development\cca移植neoforge`（`cca-neoforge/` 按 s0 任务书**尚未创建**，s1 才建）

## 1. 已落地文件（路径 + 作用 + 行数）

| 路径 | 作用 | 规模 |
|---|---|---|
| `analysis/RULE-MATRIX.md` | 24 个 mixin 全覆盖的移植决策矩阵（规则名/全类名/目标类/描述符级注入点/生效条件来源/风险/处理方式/备注） | 24 行矩阵，19.8 KB |
| `analysis/CONVERSION-CHECKLIST.md` | 38 个 java 文件逐个改法（删的 import / 改名符号 / 注入目标 / 额外资源 / 备注）+ 开头 8 条全局规则 G1~G8 | 38 个 `###` 段，27.1 KB |
| `analysis/API-VERIFY.md` | javap 亲手验证 carpet API + 42 条 refmap target 核销 + 工具链版本，每条记命令与输出首行 | 6 节 |
| `_tools/resolve_inter3.py` | intermediary→Mojmap 链式解析器（完整描述符规范键），产出 token 改名表 | — |
| `_tools/verify_targets.py` | 把 refmap 的 42 条 target 拿去 1.21.1 官方 dump 核销 | — |
| `_tools/out/resolve2.txt` | 109 个 token 的解析结果（`resolved=109 unresolved=0`） | 154 行 |
| `_tools/out/verify_targets.txt` | 42 条 target 的 OK/INHERIT/MISSING 明细 | 43 行 |
| `_tools/out/resolve_raw_INVALID.txt` | 第一版解析器（错位表）的作废输出，留作反面证据 | 8324 B |

未触碰：`_decomp/`、`_mappings/`、`_ref/`、`_src/`、`prompts/` 全只读，本阶段零改动。

## 2. 本轮关键决策（含"为什么"）

- **决策：注入目标以 `_mappings/refmap_mojmap.md` 为第一权威，源码里的 `method=` / `@At(target=)` 一律视为待替换的 Yarn 串。** 理由：源码取自 GitHub tag，混了 1.21.4 内容（FACTS 第 8 节已记 `RaidMixin` 只有 `startMixin_getStatusEffect`）；refmap 是随 1.11.1 发行 jar 一起产出的重映射表，与运行时一致。备选：以 `_decomp/mojmap` 为准——但它只是反编译产物，不含注入点信息，无法定位 `@At`。
- **决策：不信任 `_mappings/inter_to_moj.tiny`。** 理由：实测它的成员名列整体错位（把 `BlockBehaviour.method_9536(BlockState,Level,BlockPos,BlockState,Z)V` 标成 `codec`、把 `AbstractContainerMenu.method_7618` 标成 `sendAllDataToRemote`、把 `BlockStateBase.method_26164` 标成 `initCache`），用它会批量产出错误的改名字。备选：直接读 tiny 的原始 `mappings/mappings.tiny`（`_mappings/intermediary-v2.jar` 内）走 official 中间层——这是最终采用的路。
- **决策：Mojmap 符号存在性以 `_mappings/mojang-1.21.1-client.txt`（127877 行官方 dump）为准，`_ref/mc-1.21.1-client.jar` 不用来验名。** 理由：该 jar 是混淆名 jar，`javap net.minecraft.world.level.block.ComparatorBlock` 直接报「找不到符号」；而 dump 里是 `Mojmap -> obf` 的可解析文本。
- **决策：`SettingsManagerMixin` 保留原 `@Mixin({SettingsManager.class})`（即 `carpet.api.settings.SettingsManager`），不改注入点。** 理由：refmap 的两条 `WARN external-or-unmapped` 容易被误判成"目标不存在"，但 `javap -p` 实测 `private int listAllSettings(CommandSourceStack)` 与 `private final String fancyName` 都在；而 legacy 类 `carpet.settings.SettingsManager` 反而没有 `listAllSettings`，改过去必崩。
- **决策：`MerchantScreenHandlerMixin` 判"删除或降级"、`lithium/HopperHelperMixin` 判"删除"，都不静默处理（硬约束 8）。** 理由：`Merchant.canInteract(Player)boolean` 在 1.21.1 dump 里全局不存在（不是重载改名，是真的没了）；lithium 无 NeoForge 1.21.1 版。两者都写进矩阵"处理方式"列与清单备注，留 s8 拍板。
- **决策：`itemShadowing` 三处注入点全部改名（`internalOnSlotClick`→`doClick`、`Slot.setStack`→`Slot.setByPlayer`、`PlayerInventory.setStack`→`Inventory.setItem`），并标风险"高"。** 理由：3 个 target 都有 refmap 依据且核销 OK，但 `ordinal = 4` / `ordinal = 1` 无法离线验证，属"编译能过、运行可能 unmatched"的组合，先标高风险。

## 3. 验收结果（命令 + 关键输出原文）

```
> Get-ChildItem analysis\
CONVERSION-CHECKLIST.md  27070
RULE-MATRIX.md           19836
API-VERIFY.md            <见实测>
```
```
> (Select-String -Path analysis\RULE-MATRIX.md -Pattern '^\| `').Count
24
```
```
> (Select-String -Path analysis\CONVERSION-CHECKLIST.md -Pattern '^### ').Count
38
```
```
> Test-Path handoff\HANDOFF-s0.md
True
```
```
> C:\Python314\python.exe _tools\verify_targets.py "D:\Minecraft-mod-development\cca移植neoforge"
refmap targets=42  INHERIT=6  MISSING=6  OK=30
```
```
> （NeoForge maven-metadata.xml，21.1 线末六版）
21.1.258  21.1.259  21.1.262  21.1.263  21.1.264  21.1.265
```

## 4. 未解决 / 踩坑（错误原文 + 现象 + 已排查到什么程度）

- 现象：`analysis/` 下曾出现 3 个 txt 中间产物，违反"只应有 3 个 md"。
  处置：已全部移到 `_tools/out/`，`analysis/` 现在只有 3 个 md。
- 现象：`mojang-1.21.1-client.txt` 第一版解析 0 命中。
  根因（已定位）：**方法行**带 `start:end:` 行号前缀、**字段行**不带；改用 `^ {4}(?:\d+:\d+:)?…` 后正常。
- 现象：第二版解析产生"参数整串"错误（`('Ldtc;Ldcw;Ljd;Ldtc;Z',)`）。
  根因（已定位）：JVM 描述符参数列表**无逗号分隔**，必须按 `[` / `L…;` / 单字符 逐类型扫描。
- 现象：第二版还把返回类型记成 `'void'`/`'float'`，与 tiny 的 `'V'`/`'F'` 对不上。
  根因（已定位）：缺 `void/boolean/byte/char/short/int/long/float/double → V/Z/B/C/S/I/J/F/D` 的双向映射；`verify_targets.py` 里同理需要反查（`F`→`float`），否则 42 条 target 全部误判 MISSING（这个 bug 实际发生过一次）。
- 现象：`verify_targets.py` 初版把 42 条 target 全判 `NO-CLASS`。
  根因（已定位）：refmap 用 JVM 内部名（`net/minecraft/...` 带斜杠），dump 用点号；owner 与返回类型都要归一。
- 遗留（需 s8 拍板）：`Merchant.canInteract` 真缺失 → `RemoveVillagerTradeDistanceLimit` 用哪种降级实现（WrapOperation `stillValid` + `getTradingPlayer()` 比较 / 直接砍规则）。
- 遗留（需 s5 实测）：`ItemShadowing/ScreenHandlerMixin` 的 `ordinal = 4`、`ordinal = 1` 两个 `@At` 偏移，只能在 `runServer` 时证伪。

## 5. 下一阶段起点

- 第一个待办：创建 `cca-neoforge/` 骨架 + `build.gradle`(Groovy DSL) + `settings.gradle` + `gradle.properties`，跑通空工程 `gradlew build`。
- s1 必用的两个值：
  - `neo_version = 21.1.265`
  - carpet 依赖：`implementation files('libs/neoforge-carpet-1.21.1-1.0.8+v251027.jar')`（源文件 `_ref/neoforge-carpet-1.21.1-1.0.8+v251027.jar`，1498683 B，modId `carpet`，mod version `1.4.147+v1.0.8-251027`，许可证 `GPL-3.0-only`）
  - 配套：`moddev-gradle 2.0.148`、Java 21 toolchain、`MixinExtras 0.5.5` 由 NeoForge 内置（**不写进 build.gradle，不调 `MixinExtrasBootstrap.init()`**）
- 必读文件：`prompts/FACTS.md`（第 4/5/7/8 节）、`analysis/RULE-MATRIX.md`、`analysis/CONVERSION-CHECKLIST.md` 的「全局规则 G1~G8」、`analysis/API-VERIFY.md` 第 2 节。
- 前置检查命令：`gradlew --version`（确认走 Java 21）、`Get-ChildItem _ref\`（确认 carpet jar 与 neoforge universal jar 在位）。

## 6. 对 FACTS.md 的修正/新增（下一阶段请按这些覆盖 FACTS）

- `FACTS 第 4 节`「MixinExtras 已内置」——确认无误；新增事实：`carpet.CarpetServer` 的 NeoForge 版构造器已接收 `IEventBus`（`public CarpetServer(net.neoforged.bus.api.IEventBus)`），`onGameStarted` 挂在 `FMLLoadCompleteEvent` 上，因此 CCA 只需 `CarpetServer.manageExtension(INSTANCE)`，不需要自己订阅生命周期。
- `FACTS 第 4 节` 关于 21.1 末版：**实测最新 `21.1.265`**（末六版 `21.1.258/259/262/263/264/265`），`moddev-gradle` 最新可用 `2.0.148`。
- `FACTS 第 5 节` 之外新增：`carpet.CarpetSettings.shulkerBoxStackSize` 是 `public static int`，`StackableShulkerBoxesEnhancement` 可直接读，不必 mixin 取。
- `FACTS 第 8 节` 的「`Merchant.canInteract(Player)` 1.21.1 不存在」——**已用官方 dump 全局核销证实**（不是重载改名）。
- `FACTS 第 8 节` 的「`Mob.tick()` / `BlockEntity.isValidBlockState` 声明在父类」——**部分修正**：`Mob.tick()` 就声明在 `Mob`（核销 OK）；`BlockEntity.isValidBlockState(BlockState)Z` 也声明在 `BlockEntity`（核销 OK）。真正 INHERIT 的是另外 6 条：`ComparatorBlock.canSurviveOn`→`DiodeBlock`、`BlockState.is`→`BlockBehaviour$BlockStateBase`、`BaseEntityBlock.onRemove`→`BlockBehaviour`、`LevelAccessor.getSeaLevel`→`Level`/`LevelReader`、`ServerPlayer.getEffect`→`LivingEntity`、`PatrollingMonster.die`→`LivingEntity`。
- 新增事实：`_mappings/inter_to_moj.tiny` **成员名列错位，禁止使用**；`_ref/mc-1.21.1-client.jar` 是混淆名 jar，**不能**用 javap 验 Mojmap 名。
- 新增事实：`mojang-1.21.1-client.txt` 方法行带 `start:end:` 前缀、字段行不带；重载解析必须用完整描述符做键。

## 7. 发现但本阶段未处理

- `_src/cca1211`（1.21.1 对应的另一份源码）本阶段未逐文件比对；若 s2 发现 `_decomp/mojmap` 与发行 jar 行为不一致，用它交叉验证。
- `mixins/rule/EnderDragonPartCanUseEndPortal` 有两个 accessor（`EntityAccessor`、`PortalManagerAccessor`）加一个 invoker，三者都核销 OK，但 accessor 命中的是 `PortalProcessor.portal` 字段——1.21.1 里该字段是否有 setter 未确认（s5 若需要写入再查）。
- `WhatIsThis/Happy` 属 client mixin，需要单独的 `cca.client.mixins.json`；该文件的建立时机属 s9（客户端资源/打包）任务，本阶段只标出。
- `LootTableUtils.Witch` 的 `EnchantedCountIncreaseFunction.Builder` 链式 API 未用 javap 逐个核销（4 个 `method_*` 已由 tiny 解析给出名字），s6 编译时若报缺方法再补验。
