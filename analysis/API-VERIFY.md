# API-VERIFY · Carpet (NeoForge 1.21.1) API 与注入目标亲手核销

> 本文件里的每一行都是**本轮实际跑出来的**，不是查记忆。
> 工具：`C:\Program Files\Java\jdk-21.0.11\bin\javap.exe`（`javap -version` → `21.0.11`）
> 被检 jar：`_ref\neoforge-carpet-1.21.1-1.0.8+v251027.jar`（1498683 B，只读）
> 落盘证据：`_tools\out\resolve2.txt`（token 解析）、`_tools\out\verify_targets.txt`（target 核销）
> 脚本：`_tools\resolve_inter3.py`、`_tools\verify_targets.py`

## 1. 验证方法（三条腿，缺一不可）

| 腿 | 工具 | 回答什么问题 |
|---|---|---|
| A. carpet 侧 API | `javap -p -cp <carpet jar> <class>` | CCA 要调用的/要注入的 carpet 成员在 NeoForge 版里到底叫什么、是不是私有 |
| B. MC 侧符号 | `_mappings/mojang-1.21.1-client.txt`（127877 行官方 dump）+ `_tools/verify_targets.py` | refmap 的 42 条 `@At(target)` 在 1.21.1 里是否真有这个签名 |
| C. Yarn→Mojmap 改名 | `_mappings/intermediary-v2.jar` 的 `mappings/mappings.tiny` + `_tools/resolve_inter3.py` | 源码里 109 个 `method_NNNN/field_NNNN` 各自对应哪个 Mojmap 名 |

**C 腿的关键坑（已解决，勿重犯）**：`mojang-1.21.1-client.txt` 的方法行带 `start:end:` 前缀（`866:872:void initCache() -> a`），字段行不带；且方法重载必须用**完整描述符**做键，不能只用 (owner, obf 名, 参数个数)。第三版脚本结果：`files=19 tokens=109 resolved=109 unresolved=0`。

## 2. carpet 侧：逐类 javap 结论

命令模板（PowerShell）：

```powershell
$j="C:\Program Files\Java\jdk-21.0.11\bin\javap.exe"
$c="_ref\neoforge-carpet-1.21.1-1.0.8+v251027.jar"
& $j -cp $c carpet.CarpetExtension
```

### 2.1 `carpet.CarpetExtension` —— 可用，签名齐全

首行原文：`public interface carpet.CarpetExtension {`

CCA 需要的四个方法都在（且都是 `default`，所以 `CCAExtension` 继续只覆盖需要的）：

```
public default void onGameStarted();
public default carpet.api.settings.SettingsManager extensionSettingsManager();
public default java.lang.String version();
public default java.util.Map<java.lang.String, java.lang.String> canHasTranslations(java.lang.String);
public default carpet.settings.SettingsManager customSettingsManager();
```

→ 结论：`CCAExtension` 不需要因为接口差异改结构。

### 2.2 `carpet.api.settings.SettingsManager` —— **两个 WARN 目标真实存在（私有）**

首行原文：`public class carpet.api.settings.SettingsManager {`

公开成员（节选）：

```
public carpet.api.settings.SettingsManager(java.lang.String, java.lang.String, java.lang.String);
public void parseSettingsClass(java.lang.Class<?>);
public java.lang.String identifier();
public int dumpAllRulesToStream(java.io.PrintStream, java.lang.String);
```

`javap -p` 追加暴露（这是关键，`refmap_mojmap.md` 里两条 `WARN external-or-unmapped` 的答案）：

```
private final java.lang.String fancyName;
private int listAllSettings(net.minecraft.commands.CommandSourceStack);
```

→ 结论：`SettingsManagerMixin` 的两条注入目标（`listAllSettings` + `fancyName`）**在 NeoForge 版 carpet 里都存在**，WARN 只是因为它们在 carpet 命名空间、不参与 MC 重映射。**这个 mixin 可以直接移植，不用换注入点。**

### 2.3 `carpet.api.settings.Rule` / `Rule$Condition` —— 可用

```
public interface carpet.api.settings.Rule extends java.lang.annotation.Annotation {
  public abstract java.lang.String[] categories();
  public abstract java.lang.String[] options();
  public abstract boolean strict();
  public abstract java.lang.String appSource();
  public abstract java.lang.Class<? extends carpet.api.settings.Validator>[] validators();
  public abstract java.lang.Class<? extends carpet.api.settings.Rule$Condition>[] conditions();
}
public interface carpet.api.settings.Rule$Condition { public abstract boolean shouldRegister(); }
```

→ 结论：`CCASettings` 的 `@Rule(categories=…)` 与 `CCAUtils.EnableMagicSetting implements Rule.Condition` 写法原样可用（`shouldRegister()` 是唯一抽象方法）。

### 2.4 `carpet.utils.Messenger` / `carpet.utils.Translations` —— 可用

```
public static void m(net.minecraft.commands.CommandSourceStack, java.lang.Object...);
public static void m(net.minecraft.world.entity.player.Player, java.lang.Object...);
public static net.minecraft.network.chat.Component c(java.lang.Object...);
public static net.minecraft.network.chat.Component s(java.lang.String);
public static java.lang.String tr(java.lang.String);
public static java.util.Map<java.lang.String, java.lang.String> getTranslationFromResourcePath(java.lang.String);
```

→ 结论：`SettingsManagerMixin` 的 `@At target Lcarpet/utils/Messenger;m(Lnet/minecraft/commands/CommandSourceStack;[Ljava/lang/Object;)V` 与描述符**逐字一致**（`Object...` = `[Ljava/lang/Object;`）；`Translations.tr(...)` / `getTranslationFromResourcePath(...)` 可直接调用。

### 2.5 `carpet.CarpetServer` —— NeoForge 版已被改成事件驱动

```
public static void manageExtension(carpet.CarpetExtension);
public carpet.CarpetServer(net.neoforged.bus.api.IEventBus);
public void onGameStarted(net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent);
public static final java.util.List<carpet.CarpetExtension> extensions;
```

→ 结论：`CCAExtension.init()` 里 `CarpetServer.manageExtension(INSTANCE)` 是**静态方法、可直接调**，不要求我们注册事件；`onGameStarted()`（无参）依旧由 carpet 在 `FMLLoadCompleteEvent` 后回调，所以 `CCASettingsManager.parseSettingsClass(CCASettings.class)` 保持原位置。

### 2.6 `carpet.CarpetSettings.shulkerBoxStackSize` —— 存在

```
public static int shulkerBoxStackSize;
```

→ 结论：`StackableShulkerBoxesEnhancement/HopperBlockEntityMixin` 里读 `CarpetSettings.shulkerBoxStackSize` 合法，无需 mixin 到 carpet 取。

### 2.7 `carpet.settings.SettingsManager`（legacy）—— 没有 `listAllSettings`

```
public class carpet.settings.SettingsManager extends carpet.api.settings.SettingsManager {
  public carpet.settings.SettingsManager(java.lang.String, java.lang.String);
  public carpet.settings.SettingsManager(java.lang.String, java.lang.String, java.lang.String);
  public int printAllRulesToLog(java.lang.String);
  public java.util.Collection<carpet.settings.ParsedRule<?>> getRules();
}
```

→ 结论：不要试图把 `SettingsManagerMixin` 的 `@Mixin` 目标改成 legacy 类来"绕开私有方法"——legacy 类里没有 `listAllSettings`，只有父类有。**保持 `@Mixin({SettingsManager.class})` 指向 `carpet.api.settings.SettingsManager`。**

## 3. MC 侧：42 条 refmap target 核销（`_tools/verify_targets.py`）

输出首行原文：

```
refmap targets=42  INHERIT=6  MISSING=6  OK=30
```

### 3.1 30 条 OK（可以直接照抄进 `@At(target=…)`）

`FallingBlockEntity.causeFallDamage(float,float,DamageSource)boolean`、`ComparatorBlock.updateShape(BlockState,Direction,BlockState,LevelAccessor,BlockPos,BlockPos)BlockState`、`Entity.portalProcess:PortalProcessor`、`Entity.handlePortal()void`、`PortalProcessor.portal:Portal`、`Mob.tick()void`、`EnderMan$EndermanTakeBlockGoal.tick()void`、`EndGatewayBlock.getPortalDestination(ServerLevel,Entity,BlockPos)DimensionTransition`、`Inventory.setItem(int,ItemStack)void`、`Slot.setByPlayer(ItemStack)void`、`AbstractContainerMenu.doClick(int,int,ClickType,Player)void`、`BlockEntity.validateBlockState(BlockState)void`、`LecternBlock.onRemove(BlockState,Level,BlockPos,BlockState,boolean)void`、`AbstractContainerMenu.getRedstoneSignalFromBlockEntity(BlockEntity)int`、`ShulkerBoxBlock.getAnalogOutputSignal(BlockState,Level,BlockPos)int`、`BlockEntity.isValidBlockState(BlockState)boolean`、`LevelChunk.setBlockState(BlockPos,BlockState,boolean)BlockState`、`Bat.checkBatSpawnRules(EntityType,LevelAccessor,MobSpawnType,BlockPos,RandomSource)boolean`、`BadOmenMobEffect.applyEffectTick(LivingEntity,int)boolean`、`Raid.absorbRaidOmen(ServerPlayer)boolean`、`Raider.die(DamageSource)void`、`ReloadableServerRegistries$Holder.getLootTable(ResourceKey)LootTable`、`LivingEntity.dropFromLootTable(DamageSource,boolean)void`、`MerchantMenu.stillValid(Player)boolean`、`HopperBlockEntity.canMergeItems(ItemStack,ItemStack)boolean`、`HopperBlockEntity.tryMoveInItem(Container,Container,ItemStack,int,Direction)ItemStack`、`ItemStack.getMaxStackSize()int`、`SplashManager.getSplash()SplashRenderer`

### 3.2 6 条 INHERIT（声明在父类；`@At` 仍照 refmap 写）

| refmap 写的 owner | 真实声明处 | 处理 |
|---|---|---|
| `ComparatorBlock.canSurviveOn(LevelReader,BlockPos,BlockState)Z` | `DiodeBlock` | 照抄 `ComparatorBlock`；unmatched 时改 `DiodeBlock` |
| `BlockState.is(TagKey)Z` | `BlockBehaviour$BlockStateBase` | 照抄 `BlockState`（bytecode 静态类型就是这么写的） |
| `BaseEntityBlock.onRemove(BlockState,Level,BlockPos,BlockState,Z)V` | `BlockBehaviour` | 照抄 `BaseEntityBlock`（`invokespecial` owner） |
| `LevelAccessor.getSeaLevel()I` | `Level` / `LevelReader` / `WorldGenRegion` | 照抄 `LevelAccessor`；unmatched 时改 `LevelReader` |
| `ServerPlayer.getEffect(Holder)MobEffectInstance` | `LivingEntity` | 照抄 `ServerPlayer` |
| `PatrollingMonster.die(DamageSource)V` | `LivingEntity` | 照抄 `PatrollingMonster` |

### 3.3 6 条 MISSING（逐条定性）

| target | 定性 | 依据 |
|---|---|---|
| `carpet/utils/Messenger.m` | 预期（carpet 命名空间） | 不在 MC dump 里；javap 已证存在（§2.4） |
| `carpet/api/settings/SettingsManager.listAllSettings` | 预期（carpet 命名空间） | 同上；javap -p 已证私有存在（§2.2） |
| `net/caffeinemc/.../HopperHelper.tryMoveSingleItem` | 预期（lithium 命名空间） | dump 无；且 lithium 无 NeoForge 1.21.1 版 → 该 mixin 删除 |
| `DimensionTransition.<init>(ServerLevel,Vec3,Vec3,F,F,PostDimensionTransition)V` | 构造器写法差异，**实际存在** | dump `40:41:void <init>(net.minecraft.server.level.ServerLevel,net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3,float,float,net.minecraft.world.level.portal.DimensionTransition$PostDimensionTransition)` |
| `BlockEntity.<init>(BlockEntityType,BlockPos,BlockState)V` | 构造器写法差异，**实际存在** | dump `28:34:void <init>(net.minecraft.world.level.block.entity.BlockEntityType,net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState)` |
| `Merchant.canInteract(Player)boolean` | **真阻塞** | dump 全局无此签名 → `RemoveVillagerTradeDistanceLimit/MerchantScreenHandlerMixin` 删除或降级 |

## 4. 参照物命名确认（避免走错验证路径）

`_ref/mc-1.21.1-client.jar` 是**混淆名 jar**：

```
& javap -cp _ref\mc-1.21.1-client.jar net.minecraft.world.level.block.ComparatorBlock
错误: 找不到符号: net.minecraft.world.level.block.ComparatorBlock
```

→ 结论：**不要**拿它 javap 验 Mojmap 名；MC 侧验名只能走 `_mappings/mojang-1.21.1-client.txt` 或 `_decomp/mojmap/**`。carpet jar 与 `_ref/cca-mojmap.jar` 才是 Mojmap 命名的。

## 5. 工具链版本（任务 6）

| 组件 | 实测值 | 来源 |
|---|---|---|
| JDK | `21.0.11`（另有 25.0.3，不用） | `javap -version` |
| NeoForge | `21.1.265` | `maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml` 里 21.1 线末六版：`21.1.258/259/262/263/264/265` |
| ModDevGradle | `2.0.148`（存在） | 同上路径 `moddev-gradle/maven-metadata.xml` |
| MixinExtras | `0.5.5`（NeoForge 21.1 内置，不声明、不 bootstrap） | FACTS 第 4 节 |
| Python | `3.14`（`C:\Python314\python.exe`，脚本用） | 实跑 |

## 6. 一句话结论

CCA 依赖的 carpet API（`CarpetExtension` / `SettingsManager` / `Rule` / `Messenger` / `Translations` / `CarpetServer` / `CarpetSettings`）在 NeoForge carpet `1.4.147+v1.0.8` 里**全部存在**；refmap 的两条 `WARN` 不是缺失而是命名空间外；真正挡路的只有 `Merchant.canInteract` 一条（规则降级）与 lithium 那条（删 mixin）。
