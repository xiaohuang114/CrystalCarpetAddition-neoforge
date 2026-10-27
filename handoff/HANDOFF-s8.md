# HANDOFF s8 — 旧版袭击三件套（ReIntroduceOldVersionRaid）

- 日期：2026-10-26
- 本阶段结论：**PASS**（DoD 8/8 全绿；额外拿到"规则开/关行为可区分"的实证，见 §3.6）
- 工程根：`D:\Minecraft-mod-development\cca移植neoforge\cca-neoforge`
- 本阶段产出物：3 个新 mixin（ReIntroduceOldVersionRaid 包）+ `cca.mixins.json` 追加 3 条

---

## 1. 已落地文件（路径 + 作用 + 行数）

| 路径 | 作用 | 行数 |
|---|---|---|
| `src/main/java/crystal0404/crystalcarpetaddition/mixins/rule/ReIntroduceOldVersionRaid/RaiderEntityMixin.java` | `@Mixin(Raider.class)`；在 `die` 的 `PatrollingMonster.die` 调用点前注入，旧版袭击逻辑：巡逻队长被玩家（或玩家驯服的狼）击杀、且身上戴灾厄旗帜 → 给击杀者 `BAD_OMEN`(120000t, amplifier=clamp(旧等级+1,0,4))，`GameRules.RULE_DISABLE_RAIDS` 为真时不给 | 102 |
| `src/main/java/crystal0404/crystalcarpetaddition/mixins/rule/ReIntroduceOldVersionRaid/RaidMixin.java` | `@Mixin(Raid.class)`；`@ModifyArg` 改 `absorbRaidOmen` 里 `ServerPlayer.getEffect(Holder)` 的实参 `RAID_OMEN`→`BAD_OMEN`（让 1.20.5+ 的袭击吸收逻辑回退成旧版"吸收后仍是 Bad Omen"） | 29 |
| `src/main/java/crystal0404/crystalcarpetaddition/mixins/rule/ReIntroduceOldVersionRaid/BadOmenStatusEffectMixin.java` | `@Mixin(BadOmenMobEffect.class)`；`applyEffectTick` @HEAD+cancellable，规则开时自己实现旧版语义：非旁观 + 非 PEACEFUL + `ServerLevel.isVillage(pos)` → `getRaids().createOrExtendRaid(player,pos)`，返回 `raid==null`（建成即返回 false ⇒ 效果被消耗） | 46 |
| `src/main/resources/cca.mixins.json` | `mixins` 18 → **21**，末尾追加 `rule.ReIntroduceOldVersionRaid.{BadOmenStatusEffectMixin,RaiderEntityMixin,RaidMixin}`；前 18 条顺序未动；`plugin` / `injectors.defaultRequire:1` / `compatibilityLevel:"JAVA_21"` / `client: []` 原样 | — |

三个类都带**全限定名**类级注解（避免与同包字段级 `utils.annotation.Restriction` 撞 import）：

```java
@crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction(
        require = @crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition(
                value = "minecraft", versionPredicates = {">=1.20.5"}))
```

---

## 2. 本轮关键决策（含"为什么"）

- **`RaidMixin` 只保留 `startMixin_getStatusEffect` 一个方法**。理由：发行 jar javap 实证 1.21.1 的 `Raid` 只有 `absorbRaidOmen(ServerPlayer)Z` 这一个目标方法；tag 源码里的 `tickMixin_findRandomRaidersSpawnLocation` 等属 1.21.4，抄进来必然 `Injection failure`。备选（按 refmap 硬译 tag 源码）已排除。
- **`RaiderEntityMixin.onDeathMixin` 不带 `cancellable`**。理由：注入点是 `INVOKE PatrollingMonster.die` 而不是方法边界，只做旁路副作用，取消语义无意义且会破坏 `super.die` 链。
- **`removeEffectNoUpdate` 而非 `removeEffect`**。理由：后者会发客户端同步包，与 Yarn `removeStatusEffectInternal` 语义不同（勘误 M7）。
- **`BadOmenStatusEffectMixin` 用 @HEAD + `cir.setReturnValue(...)`**，不用 `@At("RETURN")`/`@ModifyReturnValue`。理由：vanilla 体内会 `addEffect(RAID_OMEN)`（见 §3.4 字节码），必须整段跳过；RETURN 注入会让 vanilla 先造出 `RAID_OMEN` 实例。这一条在 §3.6 拿到了行为级证据。
- **`--offline` 构建**。理由：勘误 M12；实测 8s 完成（对照在线 7m56s），后续阶段一律先 `--offline`。
- **帮助方法用 `@Unique` + `cca$` 前缀**。理由：与既有 mixin 命名风格一致，避免与 1.21.1 未来成员撞名。

---

## 3. 验收结果（命令 + 关键输出原文）

### 3.1 DoD1/DoD2 计数

```powershell
> (Get-ChildItem src\main\java\crystal0404\crystalcarpetaddition -Recurse -File -Filter *.java | Measure-Object).Count
39
> $j=Get-Content src\main\resources\cca.mixins.json -Raw | ConvertFrom-Json
> "mixins=$($j.mixins.Count) client=$($j.client.Count) plugin=$($j.plugin) defaultRequire=$($j.injectors.defaultRequire) compat=$($j.compatibilityLevel)"
mixins=21 client=0 plugin=crystal0404.crystalcarpetaddition.utils.CCAMixinConfigPlugin defaultRequire=1 compat=JAVA_21
> $j.mixins | Select-Object -Last 5
rule.ShulkerBoxPowerOutputExpansion.ShulkerBoxBlockMixin
rule.ReIntroduceOldVersionWitchLootTable.LivingEntityMixin
rule.ReIntroduceOldVersionRaid.BadOmenStatusEffectMixin
rule.ReIntroduceOldVersionRaid.RaiderEntityMixin
rule.ReIntroduceOldVersionRaid.RaidMixin
```

### 3.2 DoD3 构建

```powershell
> $env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'; .\gradlew.bat build --offline
BUILD SUCCESSFUL in 8s
7 actionable tasks: 7 executed
> Get-ChildItem build\libs | Select-Object Name,Length
CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge-sources.jar  76102
CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar          94176
```
主 jar **94,176 B**（基线 88,544 → 增 5,632）、sources **76,102 B**（基线 72,320）。`_tools\out\s8_build.log` 无 `error:` / `warning:`。

### 3.3 DoD4 致命模式扫描（整份 `_tools\out\s8_run1.log`，含停机段）

```powershell
> $pat='Mixin apply failed|Critical injection failure|InvalidInjectionException|MixinTransformerError|NoSuchMethodError|NoSuchFieldError|\[CCA-Mixin\] SKIP'
> (Select-String -Path ..\_tools\out\s8_run1.log -Pattern $pat).Count
0
```

### 3.4 DoD6 三条字节码判据（3/3，导出目录 `run\.mixin.out\class`）

`javap -p -c` 必须用**反斜杠**路径（任务书 §4.6 里 `"$ex\net.minecraft…"` 这种写法 PowerShell 会把 `\n` 当普通字符拼进路径，取不到类）：

```powershell
> & javap -p -c run\.mixin.out\class\net\minecraft\world\entity\raid\Raider.class | Select-String 'onDeathMixin'
69: invokespecial #225  // Method handler$zba000$cca$onDeathMixin:(Lnet/minecraft/world/damagesource/DamageSource;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V
72: invokespecial #228  // Method net/minecraft/world/entity/monster/PatrollingMonster.die:(Lnet/minecraft/world/damagesource/DamageSource;)V

> & javap -p -c run\.mixin.out\class\net\minecraft\world\effect\BadOmenMobEffect.class | Select-String 'applyUpdateEffectMixin'
17: invokespecial #39   // Method handler$zzp000$cca$applyUpdateEffectMixin:(Lnet/minecraft/world/entity/LivingEntity;ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V

> & javap -p -c run\.mixin.out\class\net\minecraft\world\entity\raid\Raid.class | Select-String 'startMixin_getStatusEffect'
7:  invokespecial #468  // Method modify$zbb000$cca$startMixin_getStatusEffect:(Lnet/minecraft/core/Holder;)Lnet/minecraft/core/Holder;
10: invokevirtual #472  // Method net/minecraft/server/level/ServerPlayer.getEffect:(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/effect/MobEffectInstance;
private net.minecraft.core.Holder modify$zbb000$cca$startMixin_getStatusEffect(net.minecraft.core.Holder);
   0: getstatic #1421  // Field crystal0404/crystalcarpetaddition/CCASettings.ReIntroduceOldVersionRaid:Z
   3: ifeq 12
   6: getstatic #1424  // Field net/minecraft/world/effect/MobEffects.BAD_OMEN:Lnet/minecraft/core/Holder;
```

`applyEffectTick` 头部完整形态（证明是 HEAD 且 cancellable 生效、vanilla 体被跳到 34）：

```
0: new   #31  // class org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable
4: ldc   #32  // String applyEffectTick
7: invokespecial #35  // CallbackInfoReturnable."<init>":(Ljava/lang/String;Z)V
15: aload 6
17: invokespecial #39  // Method handler$zzp000$cca$applyUpdateEffectMixin:(...)
22: invokevirtual #43  // CallbackInfoReturnable.isCancelled:()Z
25: ifeq 34
28-33: getReturnValueZ / ireturn        ← 我们的 setReturnValue 在这里短路返回
34: aload_1; 35: instanceof ServerPlayer ← vanilla 原体，规则开时永不执行
```

### 3.5 DoD5/DoD7 规则基线与开关

```
> rcon_probe.py … "carpet" "cca list CCA" "cca ReIntroduceOldVersionRaid true" "cca ReIntroduceOldVersionRaid false"
[Rcon: Carpet Mod version: 1.4.147+v1.0.8-251027]
[Rcon: Crystal Carpet Addition version: 1.11.1-neoforge]
[Rcon: - ReIntroduceOldVersionRaid [true] [false]]      ← CCA 分类 13 条之一
[Rcon: ReIntroduceOldVersionRaid: true, [Change permanently?]]
[Rcon: ReIntroduceOldVersionRaid: false, [Change permanently?]]
```
日志侧：`[CCA] registered 13 rules` + `[CCA] version=1.11.1-neoforge CCA-category rules=13`，规则数无回归。

### 3.6 功能探针（★ 本阶段最强证据：规则开/关**可区分**）

村庄坐标由 `locate structure minecraft:village_plains` 得到：**`[384, ~, -784]`（974 格）**；`forceload add 376 -792 392 -776` → `Marked 4 chunks in Overworld from [23, -50] to [24, -49]`；假人名被 carpet 规范化成 **`Bota`**（`@a[name=BotA]` 匹配不到）。

**A. 规则关 + 不在村庄（基线）** → 效果存活
```
> "player BotA spawn" "effect give BotA minecraft:bad_omen 600 0" "execute if entity @a[nbt={active_effects:[{id:'minecraft:bad_omen'}]}] run say S8_OFF_EFFECT_ALIVE"
[23:19:42] [Not Secure] [Rcon] S8_OFF_EFFECT_ALIVE
```

**B. 规则开 + 村庄内（384,96,-784）** → 效果被消耗、且**从不出现 raid_omen**、袭击真的建起来了
```
> "cca ReIntroduceOldVersionRaid true" "scoreboard players set Bota s8 0" "effect give Bota minecraft:bad_omen 600 0"
  "execute if entity @a[nbt={active_effects:[{id:'minecraft:bad_omen'}]}] run scoreboard players set Bota s8 1"
  "scoreboard players get Bota s8" "data get entity Bota active_effects"
[Rcon: Applied effect Bad Omen to Bota]
Bota has 0 [s8]                                            ← 效果已被消耗
Bota has the following entity data: [{duration: 5177, show_icon: 1b, "neoforge:cures": ["protected_by_totem","milk"], amplifier: 4b, id: "minecraft:resistance"}]
                                                           ← 只剩 probe 用的 resistance，没有 bad_omen、也没有 raid_omen
> "execute if entity @e[type=minecraft:pillager] run say S8_RAID_PILLAGERS"
[23:23:20] [Not Secure] [Rcon] S8_RAID_PILLAGERS           ← 袭击已生成（Easy 难度第 1 波只有掠夺者，无卫道士/劫掠兽）
```

**C. 规则关 + 同一村庄（关键对照）** → vanilla 路径，**出现 `raid_omen`**
```
> "cca ReIntroduceOldVersionRaid false" … "effect give Bota minecraft:bad_omen 600 0" … "data get entity Bota active_effects"
Bota has 0 [s8]
Bota has the following entity data: [{… "id": "minecraft:resistance"}, {duration: 390, show_icon: 1b, "neoforge:cures": ["protected_by_totem","milk"], id: "minecraft:raid_omen"}]
```

结论：**规则开 = 直接 `createOrExtendRaid` 并消耗 BAD_OMEN、全程不产生 `RAID_OMEN` 实例；规则关 = vanilla 先转成 `RAID_OMEN`(600t) 再建袭击。** 这条 A/B/C 三连就是 `BadOmenStatusEffectMixin` 真正生效的行为级判据（不是"注入成功"这种弱证据）。

### 3.7 DoD8 卫生扫描

```powershell
> (Get-ChildItem cca-neoforge\src -Recurse -Include *.java | Select-String -Pattern 'class_\d+|method_\d+|field_\d+|remap\s*=\s*false|net\.fabricmc|me\.fallenbreath').Count
5
```
5 条全部是 javadoc 注释（逐条核对：`utils/annotation/mixin/Condition.java:8`、`utils/annotation/mixin/Restriction.java:9`、`utils/condition/ConditionTester.java:4`、`utils/condition/MixinRestrictionProcessor.java:17`、`utils/CCAMixinConfigPlugin.java:11`，均为 ` * 复刻 me.fallenbreath…` 说明文字）→ **非注释命中 = 0**。

### 3.8 收尾

```
> "cca ReIntroduceOldVersionRaid false" "player Bota kill" "forceload remove all" "scoreboard objectives remove s8" "stop"
ReIntroduceOldVersionRaid: false, [Change permanently?]
Unmarked all force loaded chunks in Overworld
Removed objective [s8]
ConnectionError: connection closed by server        ← stop 立即断连，正常（勘误 M11）
[23:24:58] Thread RCON Listener stopped
```
停机后 `Get-CimInstance … *fml.modFolders*` 计数 = 0（无残留 java 进程）。

---

## 4. 未解决 / 踩坑（错误原文 + 现象 + 已排查到什么程度）

1. **`rcon_probe.py` 的读循环会串位/丢包**（不是游戏问题）。
   现象：`execute if entity … run say S8_RAID_PILLAGERS` 的 RCON 回包是空的，但日志里明明有 `[23:23:20] [Not Secure] [Rcon] S8_RAID_PILLAGERS`；`locate` 的回包会串到前一条命令的块里。
   已排除：`say` 不可达 RCON（错，可达）、carpet 假人不可用（错，可用）。
   **现用工作法**：RCON 只用来发命令；**判据一律回读日志**（`Select-String -Path _tools\out\<log> -Pattern 'S8_|Rcon:'`），并对关键量用 `scoreboard` + `data get entity` 双通道交叉。
   **待修（s9 可选）**：`_tools\rcon_probe.py` 的 `read_packet` 应按 req_id 配对、逐命令收一条包，而不是靠 3s 超时聚合。

2. **PowerShell 引号**：`"execute if entity @a[name=\"BotA\"…]"` 会让 `\"` 变成参数分隔，命令被截断。
   **正确写法**：外层双引号 + SNBT 内层单引号 —— `"execute if entity @a[nbt={active_effects:[{id:'minecraft:bad_omen'}]}] run say X"`。

3. **carpet 假人名规范化**：`player BotA spawn` 后实体名是 `Bota`（首字母大写化）。选择器带 `name=BotA` 永远匹配不到。

4. **任务书 §4.6 的 javap 路径写法有误**：`"$ex\net.minecraft.world.entity.raid.Raider.class"` 里的 `\n` 不会被当转义，路径拼错 javap 静默无输出。用 `"$ex\net…"`→ 改为 `run\.mixin.out\class\net\…`（单反斜杠直接写进字符串）。

5. **第三条字节码判据需要 `Raid` 类被加载才存在**。首次 `javap run\.mixin.out\class\net\minecraft\world\entity\raid\` 只有 `Raider.class`（35KB 之外缺失 `Raid.class`）；跑完 §3.6-B（村庄内触发袭击）后才出现 `Raid.class`（35,407 B）。**顺序不能颠倒**。

6. **服务器严重卡顿**：`locate structure` 6.6s + 区块生成触发 `Can't keep up! Is the server overloaded? Running 12939ms or 258 ticks behind`，期间 RCON 批次会 `ConnectionError: connection closed by server`。**这是掉线不是崩溃**（回探 `list` 立刻恢复）。重探前先 `list` 确认存活。

7. **`effect give … 600 0` 的 600 是秒**（=12000 tick），不是 tick；对照 vanilla 记录的 600 tick 时别搞混。

---

## 5. 下一阶段起点（s9 = 收尾与全量回归）

- 第一个待办：`client` 端 mixin `rule.WhatIsThis.Happy`（`SplashManager.getSplash`），`cca.mixins.json` 的 `client` 从 `[]` → 1 条，`@Condition(type=Type.TESTER, tester=CCAUtils.DisableEasterEggs.class)` 放在 **conflict**（方向不能反）。
- 第二个待办：`RemoveVillagerTradeDistanceLimit` 默认保持"不注册"（方案 A）并写进 `analysis\DEVIATIONS.md`；方案 B（改 `MerchantMenu.stillValid(Player)`）可选。
- 第三个待办：s7 遗留 `D-S7-2` 的两条功能探针（**勘误 M1 已推翻"没法验"**，用 `/player` 假人）：
  - 女巫掉落表判别器：`setblock 0 -60 0 minecraft:chest` / `loot insert 0 -60 0 loot minecraft:entities/witch` / `data get block 0 -60 0 Items` → `redstone.count>=4` ⇒ 原版表；`<=3` 或缺 ⇒ 我们的旧表（需 `cca ReIntroduceOldVersionWitchLootTable true` 后真打死女巫）。
  - 漏斗堆叠：`data get block <hopper> Items` 的 `count > 1`。
- 必读文件：`prompts/s9-收尾与全量回归.md`、`prompts/FACTS.md`、`_mappings/refmap_mojmap.md`、本文件 §3.6 的三连探针写法。
- 前置检查命令：
  ```powershell
  cd cca-neoforge; $env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'; .\gradlew.bat build --offline
  ..\_tools\serve.ps1 -LogName s9_run1.log -JavaOpts '-Dmixin.debug.export=true'
  ```
- 基线（s9 不能回退）：java **39** / `mixins` **21** / 规则 **13** / 主 jar **94,176 B** / 致命模式 **0** / `client` 当前 **0**（s9 要变 1）。

---

## 6. 对 FACTS.md 的修正 / 新增（下一阶段按这些覆盖 FACTS）

- `FACTS 第 4 节`"carpet 自带 `/player` 假人"：**确认可用**，但**名字会被规范化**（`BotA`→`Bota`），选择器要按规范化后的名字写或不写 `name=`。
- 新增事实：**`say` 的输出会进 RCON 回包与服务端日志**（`[Not Secure] [Rcon] <msg>`），可作探针信标；但 `_tools\rcon_probe.py` 的读循环会丢包/串位，判据请回读日志。
- 新增事实：`ServerLevel.isVillage(BlockPos)` 是**区块级 POI 判定**，假人站在村里屋顶（y=96）也判定通过 → 假人探针不必精确落地，`tp <x> 100 <z>` 即可。
- 新增事实：`effect give <p> <effect> <duration> <amp>` 的 duration 单位是**秒**。
- 新增事实：1.21.1 `RaidOmenMobEffect` 路径的可见指纹是临时 `raid_omen` 实例（600 tick、无 amplifier），**规则开时该实例完全不出现** —— 这是 `BadOmenStatusEffectMixin` 的行为级判据。
- 新增事实：`.mixin.out` 只有**类被加载过**才导出；`Raid` 需先触发一次袭击才会出现 `Raid.class`。

---

## 7. 发现但本阶段未处理

- `_tools\rcon_probe.py` 的协议 bug（见 §4.1）：建议 s9 顺手修成按 req_id 配对读取。**未改**（不在 s8 范围）。
- `BadOmenStatusEffectMixin` 的 `cca$tryStartRaid` 在 `Difficulty.PEACEFUL` 分支返回 `true`（保留效果），与旧版一致；但 vanilla 该分支也返回 true，因此**该分支无法用于区分**，s9 若要做负向回归别把它当判据。
- `RaiderEntityMixin` 的 `cca$hasBanner` 依赖 `Raid.getLeaderBannerInstance(registryAccess().lookupOrThrow(Registries.BANNER_PATTERN))`；**未做端到端探针**（需要给假人戴 Ominous Banner 再杀巡逻队长，造队长需 `Raid` 内部状态），本阶段只有字节码级证据 + 编译期正确性。**候选方案**：① s9 用 `/summon pillager ~ ~ ~ {PatrolLeader:1b,HandItems:[…ominous_banner…]}` 造队长；② 用 `Raid` 生成自然队长；③ 砍到注入级并记 `D-S8-x`。**留给 s9 拍板**。
- `RaidMixin`（`absorbRaidOmen` 的 RAID_OMEN→BAD_OMEN）同样只到字节码级：它的触发需要"玩家带 `RAID_OMEN` 被现存袭击吸收"，与 §3.6-C 的 vanilla 路径相冲突（规则开时 `RAID_OMEN` 根本不会产生），**端到端探针需要手动 `effect give <p> raid_omen` + 已存在袭击**。未做。
- `analysis\DEVIATIONS.md` 本阶段**无新增**条目（`lithium.HopperHelperMixin` 仍是唯一弃移植项，s7 已落档）。
