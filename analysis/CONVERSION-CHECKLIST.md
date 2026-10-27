# CONVERSION-CHECKLIST · 38 个 java 文件逐个改法（s2~s9 唯一执行清单）

> 基准：`_mappings/refmap_mojmap.md`（注入目标）+ `_mappings/mojang-1.21.1-client.txt`（符号存在性）+ `_ref/neoforge-carpet-1.21.1-1.0.8+v251027.jar`（carpet API javap）。
> 逐符号解析结果：`_tools/out/resolve2.txt`（109 个 intermediary token 全部解析成功，脚本 `_tools/resolve_inter3.py`）。
> target 存在性核销：`_tools/out/verify_targets.txt`（42 条 refmap target：OK 30 / INHERIT 6 / MISSING 6）。
> 目录根缩写：`SRC = _decomp/mojmap/crystal0404/crystalcarpetaddition/`。

## 全局规则（38 个文件全都适用，逐条执行）

- **G1 删 `remap = false`**：出现在 `carpet/SettingsManagerMixin`（`@Shadow` 与 `@WrapWithCondition` 两处）。NeoForge 1.21.1 运行时就是 Mojang 官方名，留着会破坏解析链。其余文件无此参数，但改文件时不要新增。
- **G2 `me.fallenbreath.conditionalmixin.*` 全部换成自建注解包** `crystal0404.crystalcarpetaddition.utils.annotation.mixin.{Restriction, Condition, Condition.Type}`（s3 新建）。原 `utils/annotation/{Condition,Restriction}.java` 与 `utils/annotation/impl/AnnotationProcessor.java` 迁到 `utils/annotation/mixin/` 下并改造。
- **G3 `net.fabricmc.*` 只允许出现在入口类**：`CrystalCarpetAdditionMod`。其余文件（含 `CCAUtils`）不得 import fabric。
- **G4 `com.llamalad7.mixinextras.*` 与 `org.spongepowered.asm.*` 原样保留**，不改成 `@Redirect`，不加 `MixinExtrasBootstrap.init()`（NeoForge 21.1 内置 0.5.5）。
- **G5 所有 `method_NNNN` / `field_NNNN` / `class_NNNN` 必须换成 Mojmap 名**，本清单逐个列出；清单里写「无」的地方，用 `Select-String -Path <文件> -Pattern 'method_\d+|field_\d+|class_\d+'` 自检必须为 0 命中。
- **G6 包名与类名一律不动**（含内部类 `EnderMan$EndermanTakeBlockGoal` 对应文件名 `EndermanEntity$PickUpBlockGoalMixin.java`），这样可用 `diff` 逐文件验收。
- **G7 `@At(target = …)` 一律照抄 `refmap_mojmap.md` 的 Mojmap 原文**，不照抄源码里的 Yarn 串。
- **G8 资源**：`assets/cca/lang/en_us.json`、`assets/cca/lang/zh_cn.json`（翻译键由 `CCASettings` 的 `@Rule` 名 + `carpet.settings.command.version` + `cca.rule.*` 组成），`META-INF/neoforge.mods.toml`，`cca.mixins.json`（不含 client 段）+ `cca.client.mixins.json`。

## A. 入口 / 扩展 / 设置（4 个文件）

### CrystalCarpetAdditionMod.java
- import 删除：`net.fabricmc.api.ModInitializer`
- import 新增：`net.neoforged.fml.common.Mod`、`net.neoforged.bus.api.IEventBus`、`net.neoforged.fml.ModContainer`
- 符号改名：`implements ModInitializer` + `onInitialize()` → `@Mod("cca")` 类 + 构造器注入 `CrystalCarpetAdditionMod(IEventBus modBus, ModContainer container)`；`FabricLoader.getInstance().getModContainer("cca")…getFriendlyString()` → `container.getModInfo().getVersion().getQualifier()`（失败回退 `"1.11.1"`）
- 注入目标：无
- 额外资源：无
- 备注：必须先给 `public static String version` 赋值再触碰 `CCAExtension`（`CCASettingsManager` 是静态字段，构造时就要 version，FACTS 第 8 节坑）。`LOGGER` 保留 `LoggerFactory.getLogger("Crystal Carpet Addition")`

### CCAExtension.java
- import 删除：无
- import 新增：无
- 符号改名：无
- 注入目标：无
- 额外资源：无
- 备注：`CarpetExtension` 全部方法签名已用 javap 核销（`onGameStarted()`、`extensionSettingsManager()`、`version()`、`canHasTranslations(String)` 均存在）；`Translations.getTranslationFromResourcePath(String)`、`CarpetServer.manageExtension(CarpetExtension)` 存在

### CCASettings.java
- import 删除：`crystal0404.crystalcarpetaddition.utils.annotation.{Condition,Restriction}`（旧包）
- import 新增：`crystal0404.crystalcarpetaddition.utils.annotation.mixin.{Condition,Restriction}`；`carpet.api.settings.Rule`
- 符号改名：无（14 个 `@Rule` 字段名与默认值一字不动，见 FACTS 第 5 节）
- 注入目标：无
- 额外资源：两条语言文件必须覆盖 14 个规则键 + `ShulkerBoxPowerOutputExpansionColour` 的 17 个枚举选项
- 备注：`Rule$Condition` 接口实测为 `boolean shouldRegister()`；`Rule` 注解字段为 `categories/options/strict/appSource/validators/conditions`

### utils/CCAUtils.java
- import 删除：`me.fallenbreath.conditionalmixin.api.mixin.ConditionTester`
- import 新增：自建 `utils.annotation.mixin.ConditionTester`（s3 新建）
- 符号改名：`VersionChecker.doesModVersionSatisfyPredicate` → 自建版本比较（s3，`MavenVersionAdapter` 或 `ComparableVersion`）；`MixinService.getService().getBytecodeProvider().getClassNode` 保留（`org.spongepowered` 原样）
- 注入目标：无
- 额外资源：无
- 备注：`JAVA_PARAMETERS` 三个 key（`cca.disable.EasterEggs` / `cca.enable.debug` / `cca.enable.MagicSettings`）保留；`DisableEasterEggs`、`EnableMagicSetting` 两个内部类随 `ConditionTester` 迁包

## B. utils（10 个文件）

### utils/CCAMixinConfigPlugin.java
- import 删除：`me.fallenbreath.conditionalmixin.api.mixin.RestrictiveMixinConfigPlugin`、`com.llamalad7.mixinextras.MixinExtrasBootstrap`
- import 新增：`org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin`（自实现）
- 符号改名：`onLoad` 内 `MixinExtrasBootstrap.init()` 整句删除（NeoForge 已内置，FACTS 第 4 节）
- 注入目标：无
- 额外资源：`cca.mixins.json` 的 `"plugin"` 指向本类
- 备注：`getRefMapperConfig()` 直接 `return null`（NeoForge 无 refmap 层）；`acceptTargets` 保留；条件过滤改由自建 `AnnotationProcessor.shouldRegister`

### utils/annotation/Condition.java
- import 删除：无
- import 新增：无
- 符号改名：包 `utils.annotation` → `utils.annotation.mixin`；新增 `Type` 枚举（`MOD`、`MC`、`TESTER`）与 `Class<? extends ConditionTester> tester() default ConditionTester.class`
- 注入目标：无
- 额外资源：无
- 备注：`@Target({FIELD, TYPE})`（`Happy` 是类级使用）；`versionPredicates` 默认 `"*"`

### utils/annotation/Restriction.java
- import 删除：无
- import 新增：无
- 符号改名：包迁 `utils.annotation.mixin`
- 注入目标：无
- 额外资源：无
- 备注：`require()` / `conflict()` 保持 `Condition[]` + 空默认

### utils/annotation/impl/AnnotationProcessor.java
- import 删除：无
- import 新增：`utils.annotation.mixin.{Restriction,Condition}`
- 符号改名：包迁 `utils.annotation.mixin.impl`；`shouldRegister(Restriction, String)` 签名不动
- 注入目标：无
- 额外资源：无
- 备注：遍历顺序先 `conflict()` 后 `require()`，任一不满足即 `false`；日志仅在 `CCAUtils.isEnableDebug()` 时输出

### utils/ModIds.java
- import 删除：无
- import 新增：无
- 符号改名：无
- 注入目标：无
- 额外资源：无
- 备注：`FABRIC_*` 三个常量保留（只作字符串比较，不产生依赖）；新增 `NEOFORGE="neoforge"` 供 `Condition.Type.MC` 判断

### utils/Key.java
- import 删除：无 / import 新增：无 / 符号改名：无 / 注入目标：无 / 额外资源：无
- 备注：package-private 类，包路径不变即可

### utils/EmptyClass.java
- import 删除：无 / import 新增：无 / 符号改名：无 / 注入目标：无 / 额外资源：无
- 备注：`public abstract class EmptyClass {}` 原样，`MerchantScreenHandlerMixin` 降级后用它占位

### utils/LootTableUtils.java
- import 删除：无（无 fabric / conditional 依赖）
- import 新增：无
- 符号改名（全部已解析）：`method_46747`→`getOrThrow`、`Enchantments.field_9110`→`LOOTING`、`LootPool.method_347`→`lootPool`、`method_35509`→`setBonusRolls`、`ConstantValue.method_32448`→`exactly`、`method_351`→`add`、`LootItem.method_411`→`lootTableItem`、`Items.field_8601`→`GLOWSTONE_DUST`、`method_438`→`apply`、`SetItemCountFunction.method_621`→`setCount`、`UniformGenerator.method_32462`→`between`、`method_437`→`setWeight`、`method_352`→`setRolls`、`LootTable.method_324`→`lootTable`、`method_336`→`withPool`、`method_338`→`build`；物品常量 `field_8479`→`SUGAR`、`field_8725`→`REDSTONE`、`field_8680`→`SPIDER_EYE`、`field_8469`→`GLASS_BOTTLE`、`field_8054`→`GUNPOWDER`、`field_8600`→`STICK`
- 注入目标：无
- 额外资源：无
- 备注：`EnchantedCountIncreaseFunction.Builder` 写法保留；这是 `ReIntroduceOldVersionWitchLootTable` 的唯一依赖，必须先编过

### utils/shulkerBoxUtils/ColourMap.java
- import 删除：无 / import 新增：无
- 符号改名：`DyeColor.field_7954`→`PINK`、`field_7952`→`WHITE`、`field_7967`→`LIGHT_GRAY`、`field_7944`→`GRAY`、`field_7963`→`BLACK`、`field_7957`→`BROWN`、`field_7964`→`RED`、`field_7946`→`ORANGE`、`field_7947`→`YELLOW`、`field_7961`→`LIME`、`field_7942`→`GREEN`、`field_7955`→`CYAN`、`field_7951`→`LIGHT_BLUE`、`field_7966`→`BLUE`、`field_7945`→`PURPLE`、`field_7958`→`MAGENTA`
- 注入目标：无
- 额外资源：无
- 备注：`enum Colour {NONE,WHITE,…,PINK}` 顺序不变（语言键按 `name().toLowerCase()` 生成）

### utils/shulkerBoxUtils/ShulkerBoxesSet.java
- import 删除：无 / import 新增：无
- 符号改名：`Items.field_8545`→`SHULKER_BOX`、`field_8722`→`WHITE_SHULKER_BOX`、`field_8380`→`ORANGE_SHULKER_BOX`、`field_8050`→`MAGENTA_SHULKER_BOX`、`field_8829`→`LIGHT_BLUE_SHULKER_BOX`、`field_8271`→`YELLOW_SHULKER_BOX`、`field_8548`→`LIME_SHULKER_BOX`、`field_8520`→`PINK_SHULKER_BOX`、`field_8627`→`GRAY_SHULKER_BOX`、`field_8451`→`LIGHT_GRAY_SHULKER_BOX`、`field_8213`→`CYAN_SHULKER_BOX`、`field_8816`→`PURPLE_SHULKER_BOX`、`field_8350`→`BLUE_SHULKER_BOX`、`field_8584`→`BROWN_SHULKER_BOX`、`field_8461`→`GREEN_SHULKER_BOX`、`field_8676`→`RED_SHULKER_BOX`、`field_8268`→`BLACK_SHULKER_BOX`
- 注入目标：无
- 额外资源：无
- 备注：17 项 `ImmutableSet<Item> ITEMS_SET`，被两个 shulker 规则共用

## C. mixins（24 个文件）

### mixins/carpet/SettingsManagerMixin.java
- import 删除：`me.fallenbreath.conditionalmixin.api.annotation.{Condition, Restriction}`
- import 新增：`utils.annotation.mixin.{Condition, Restriction}`
- 符号改名：无（`fancyName`、`Messenger.m`、`listAllSettings`、`parseSettingsClass` 全部照 refmap 与 javap 原文）
- 注入目标：`listAllSettings`（`@Inject` INVOKE `Lcarpet/utils/Messenger;m(Lnet/minecraft/commands/CommandSourceStack;[Ljava/lang/Object;)V`，ordinal=0，`Shift.AFTER`）；`parseSettingsClass`（`@WrapWithCondition` INVOKE `Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;`）+ `@Coerce Object k, v` + `@Local(ordinal=0) Field field`
- 额外资源：`Translations.tr("carpet.settings.command.version")` 键需进两条 lang
- 备注：删掉两处 `remap = false`（G1）；`@Shadow @Final private String fancyName` 保留

### mixins/rule/AnvilCanCrushItems/FallingBlockEntityMixin.java
- import 删除：无 / import 新增：无
- 符号改名：`field_7188`→`blockState`（`@Shadow`）、`method_26164`→`is`、`BlockTags.field_15486`→`ANVIL`、`EntitySelector.field_6156`→`NO_CREATIVE_OR_SPECTATOR`
- 注入目标：`method="causeFallDamage"`（refmap `FallingBlockEntity.causeFallDamage(FFLDamageSource;)Z`）；`@At` INVOKE `Ljava/util/function/Predicate;and(Ljava/util/function/Predicate;)Ljava/util/function/Predicate;`
- 额外资源：无
- 备注：`@WrapOperation` 的 `Operation<Predicate<Entity>>` 泛型保留

### mixins/rule/ComparatorIgnoresUpdatesFromBelow/ComparatorBlockMixin.java
- import 删除：无 / import 新增：无
- 符号改名：类字段 `CCASettings.ComparatorIgnoresStateUpdatesFromBelow` 不变
- 注入目标：`method="updateShape"`（refmap `ComparatorBlock.updateShape(BlockState,Direction,BlockState,LevelAccessor,BlockPos,BlockPos)BlockState`）；`@At` INVOKE `Lnet/minecraft/world/level/block/ComparatorBlock;canSurviveOn(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z`
- 额外资源：无
- 备注：源码的 `getStateForNeighborUpdate` / `canPlaceAbove` 全是 1.20 名；`canSurviveOn` 实声明在 `DiodeBlock`，若运行时 unmatched 改用 `DiodeBlock` owner

### mixins/rule/EnderDragonPartCanUseEndPortal/EntityAccessor.java
- import 删除：无 / import 新增：无 / 符号改名：无
- 注入目标：`@Accessor` 字段 `Lnet/minecraft/world/entity/Entity;portalProcess:Lnet/minecraft/world/entity/PortalProcessor;`（方法名 `getPortalManager()` 保留）
- 额外资源：无 / 备注：核销 OK，字段名就是 `portalProcess`

### mixins/rule/EnderDragonPartCanUseEndPortal/EntityInvoker.java
- import 删除：无 / import 新增：无
- 符号改名：`@Invoker("tickPortalTeleportation")` → `@Invoker("handlePortal")`；方法名 `invokeTickPortalTeleportation` → `invokeHandlePortal`
- 注入目标：`Lnet/minecraft/world/entity/Entity;handlePortal()V`
- 额外资源：无 / 备注：`MobEntityMixin` 里调用点同步改名，否则编译不过

### mixins/rule/EnderDragonPartCanUseEndPortal/PortalManagerAccessor.java
- import 删除：无 / import 新增：无 / 符号改名：无
- 注入目标：`@Accessor` 字段 `Lnet/minecraft/world/entity/PortalProcessor;portal:Lnet/minecraft/world/level/block/Portal;`（方法名 `getPortal()` 保留）
- 额外资源：无 / 备注：核销 OK

### mixins/rule/EnderDragonPartCanUseEndPortal/MobEntityMixin.java
- import 删除：无 / import 新增：`net.minecraft.world.level.block.EndPortalBlock`
- 符号改名：`method_5690`→`getSubEntities`；`((EntityInvoker)part).invokeTickPortalTeleportation()`→`invokeHandlePortal()`
- 注入目标：`method="tick"`（refmap `Lnet/minecraft/world/entity/Mob;tick()V`），`@At("TAIL")`
- 额外资源：无 / 备注：遍历 `getSubEntities()` 判断 `getPortalManager().getPortal() instanceof EndPortalBlock`

### mixins/rule/EndermanCannotPickUpBlocksInNether/EndermanEntity$PickUpBlockGoalMixin.java
- import 删除：无 / import 新增：无
- 符号改名：`method_27983`→`dimension`、`Level.field_25180`→`NETHER`
- 注入目标：`method="tick"`（refmap `EnderMan$EndermanTakeBlockGoal.tick()V`）；`@At` INVOKE `Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z`
- 额外资源：无
- 备注：`isIn`→`is`；`@Local(ordinal=0) Level world` 保留；`is(TagKey)` 实声明在 `BlockBehaviour$BlockStateBase`

### mixins/rule/GatewayCannotLoadingChunks/EndGatewayBlockMixin.java
- import 删除：无 / import 新增：无
- 符号改名：`DimensionTransition.field_52245`→`DO_NOTHING`；`PostDimensionTransition` 导入路径 `net.minecraft.world.level.portal.DimensionTransition.PostDimensionTransition` 保留
- 注入目标：`method="getPortalDestination"`；`@At` INVOKE `Lnet/minecraft/world/level/portal/DimensionTransition;<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;FFLnet/minecraft/world/level/portal/DimensionTransition$PostDimensionTransition;)V`，`index = 5`
- 额外资源：无 / 备注：源码 `createTeleportTarget` / `TeleportTarget` 是 1.20 名，全换；构造器 6 参版本 dump 实测存在

### mixins/rule/ItemShadowing/ScreenHandlerMixin.java
- import 删除：`me.fallenbreath.conditionalmixin.api.annotation.{Condition, Restriction}`
- import 新增：`utils.annotation.mixin.{Condition, Restriction}`
- 符号改名：`method_53512`→`setByPlayer`
- 注入目标：`method="doClick"`（refmap `AbstractContainerMenu.doClick(II,ClickType,Player)V`）；`@WrapWithCondition` INVOKE `Lnet/minecraft/world/inventory/Slot;setByPlayer(Lnet/minecraft/world/item/ItemStack;)V` `ordinal = 4`；`@Inject` INVOKE `Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V` `ordinal = 1`
- 额外资源：无
- 备注：`internalOnSlotClick`→`doClick`、`Slot.setStack`→`setByPlayer`、`PlayerInventory.setStack`→`Inventory.setItem`；两个 `@Local` 保留；ordinal 若 unmatched 改 TAIL 取值

### mixins/rule/MagicBox/BlockEntityMixin.java
- import 删除：`me.fallenbreath.conditionalmixin.api.annotation.{Condition, Restriction}`
- import 新增：`utils.annotation.mixin.{Condition, Restriction}`
- 符号改名：无
- 注入目标：`method="<init>"`；`@At` INVOKE `Lnet/minecraft/world/level/block/entity/BlockEntity;validateBlockState(Lnet/minecraft/world/level/block/state/BlockState;)V`
- 额外资源：无
- 备注：源码 `validateSupports` 在 1.21.1 不存在（dump hits=0），`validateBlockState` 才是真名

### mixins/rule/MagicBox/LecternBlockMixin.java
- import 删除：`me.fallenbreath.conditionalmixin.api.annotation.{Condition, Restriction}`
- import 新增：`utils.annotation.mixin.{Condition, Restriction}`
- 符号改名：`super.method_9536(...)`→`super.onRemove(...)`
- 注入目标：`method="onRemove"`（refmap `LecternBlock.onRemove(BlockState,Level,BlockPos,BlockState,Z)V`）；`@At` INVOKE `Lnet/minecraft/world/level/block/BaseEntityBlock;onRemove(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V`；第二个注入 `@Inject(method="onRemove", at=@At("TAIL"))`
- 额外资源：无
- 备注：类上 `extends BaseEntityBlock` + 构造器 `super(settings)` 必须保留（Mixin 目标类构造器占位）

### mixins/rule/MagicBox/ShulkerBoxBlockMixin.java
- import 删除：无 / import 新增：无
- 符号改名：`method_7618`→`getRedstoneSignalFromContainer`
- 注入目标：`method="getAnalogOutputSignal"`；`@At` INVOKE `Lnet/minecraft/world/inventory/AbstractContainerMenu;getRedstoneSignalFromBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)I`
- 额外资源：无
- 备注：注入目标吃 `BlockEntity`，方法体内调用的是 `getRedstoneSignalFromContainer`（吃 `Container`），两个名字别混

### mixins/rule/MagicBox/WorldChunkMixin.java
- import 删除：`me.fallenbreath.conditionalmixin.api.annotation.{Condition, Restriction}`
- import 新增：`utils.annotation.mixin.{Condition, Restriction}`
- 符号改名：`@Shadow method_8320`→`getBlockState`、`method_11016`→`getBlockPos`、`method_26204`→`getBlock`
- 注入目标：`method="setBlockState"`（refmap `LevelChunk.setBlockState(BlockPos,BlockState,Z)BlockState`）；`@At` INVOKE `Lnet/minecraft/world/level/block/entity/BlockEntity;isValidBlockState(Lnet/minecraft/world/level/block/state/BlockState;)Z`
- 额外资源：无
- 备注：源码 `BlockEntity.supports` 是 1.20 名；`@Local(ordinal=0) BlockEntity blockEntity` 保留

### mixins/rule/NoBatSpwan/BatEntityMixin.java
- import 删除：无 / import 新增：无
- 符号改名：无
- 注入目标：`method="checkBatSpawnRules"`（refmap `Bat.checkBatSpawnRules(EntityType,LevelAccessor,MobSpawnType,BlockPos,RandomSource)Z`）；`@At` INVOKE `Lnet/minecraft/world/level/LevelAccessor;getSeaLevel()I`
- 额外资源：无
- 备注：源码 `canSpawn` / `WorldAccess` 均为旧名；返回 `Integer.MIN_VALUE` 表示"永不生成"；注入 owner 若 unmatched 改 `LevelReader`

### mixins/rule/ReIntroduceOldVersionRaid/BadOmenStatusEffectMixin.java
- import 删除：无 / import 新增：无
- 符号改名：`method_7325`→`isSpectator`、`method_51469`→`serverLevel`、`method_24515`→`blockPosition`、`method_8407`→`getDifficulty`、`Difficulty.field_5801`→`PEACEFUL`、`method_19500`→`isVillage`、`method_19495`→`getRaids`、`method_16540`→`createOrExtendRaid`
- 注入目标：`method="applyEffectTick"`（refmap `BadOmenMobEffect.applyEffectTick(LivingEntity,I)Z`），`@At("HEAD")`、`cancellable = true`、`CallbackInfoReturnable<Boolean>`
- 额外资源：无
- 备注：源码 `applyUpdateEffect` 是旧名；`cca$tryStartRaid` 里三元表达式结构保留

### mixins/rule/ReIntroduceOldVersionRaid/RaidMixin.java
- import 删除：无 / import 新增：无
- 符号改名：`MobEffects.field_16595`→`BAD_OMEN`
- 注入目标：`method="absorbRaidOmen"`（refmap `Raid.absorbRaidOmen(ServerPlayer)Z`）；`@At` INVOKE `Lnet/minecraft/server/level/ServerPlayer;getEffect(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/effect/MobEffectInstance;`
- 额外资源：无
- 备注：源码 `method="start"` + `ServerPlayerEntity.getStatusEffect(RegistryEntry)` 是 1.20 名；`@ModifyArg` 参数类型 `Holder<MobEffect>` 保留。FACTS 第 8 节记录的 tag 源码混版问题在此文件收口

### mixins/rule/ReIntroduceOldVersionRaid/RaiderEntityMixin.java
- import 删除：无 / import 新增：无（`@Unique`/`@Shadow` 保留）
- 符号改名：`@Shadow method_16478`→`getCurrentRaid`、`method_37908`→`level`、`method_16219`→`isPatrolLeader`、`method_19502`→`getRaidAt`、`method_24515`→`blockPosition`、`method_6118`→`getItemBySlot`、`EquipmentSlot.field_6169`→`HEAD`、`method_5529`→`getEntity`、`method_7960`→`isEmpty`、`method_6112`→`getEffect`、`MobEffects.field_16595`→`BAD_OMEN`、`method_5578`→`getAmplifier`、`method_6111`→`removeEffectNoUpdate`、`Mth.method_15340`→`clamp`、`method_8450`→`getGameRules`、`method_8355`→`getBoolean`、`GameRules.field_19422`→`RULE_DISABLE_RAIDS`、`method_6092`→`addEffect`、`method_35057`→`getOwner`、`method_6181`→`isTame`、`ItemStack.method_7973`→`matches`、`Raid.method_16515`→`getLeaderBannerInstance`、`method_56673`→`registryAccess`、`method_46762`→`lookupOrThrow`、`Registries.field_41252`→`BANNER_PATTERN`
- 注入目标：`method="die"`（refmap `Raider.die(DamageSource)V`）；`@At` INVOKE `Lnet/minecraft/world/entity/monster/PatrollingMonster;die(Lnet/minecraft/world/damagesource/DamageSource;)V`
- 额外资源：无
- 备注：源码 `onDeath` / `PatrolEntity` 为旧名；类 `extends PatrollingMonster` 的占位构造器必须保留；本文件符号最多，改完必须单独 `compileJava`

### mixins/rule/ReIntroduceOldVersionWitchLootTable/LivingEntityMixin.java
- import 删除：无 / import 新增：无
- 符号改名：`method_37908`→`level`、`method_30349`→`registryAccess`、`method_46762`→`lookupOrThrow`、`Registries.field_41265`→`ENCHANTMENT`
- 注入目标：`method="dropFromLootTable"`（refmap `LivingEntity.dropFromLootTable(DamageSource,Z)V`）；`@At` INVOKE `Lnet/minecraft/server/ReloadableServerRegistries$Holder;getLootTable(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/level/storage/loot/LootTable;`
- 额外资源：无
- 备注：源码 `dropLoot` / `ReloadableRegistries$Lookup` 均为旧名；类 `extends Entity` 的占位构造器保留

### mixins/rule/RemoveVillagerTradeDistanceLimit/MerchantScreenHandlerMixin.java
- import 删除：`me.fallenbreath.conditionalmixin.api.annotation.{Condition, Restriction}`（若保留文件）
- import 新增：`utils.annotation.mixin.{Condition, Restriction}`（若保留文件）
- 符号改名：`@Shadow field_7863`→`trader`、`method_8257`→`getTradingPlayer`
- 注入目标：源码 `method="canUse"` + `Merchant.canInteract(Player)Z`；refmap 可用的只有 `MerchantMenu.stillValid(Player)Z`
- 额外资源：无
- 备注：**删除或降级**。`Merchant.canInteract` 在 1.21.1 不存在，refmap 未重映射可证（`WARN external-or-unmapped`，且 dump 全局无 `canInteract(Player)boolean`）。处理：文件不写进 `cca.mixins.json`，规则开关与 lang 键保留，`utils/EmptyClass` 占位；s8 任务书给出降级实现（WrapOperation `stillValid` + `getTradingPlayer()` 比较）

### mixins/rule/ShulkerBoxPowerOutputExpansion/ShulkerBoxBlockMixin.java
- import 删除：无 / import 新增：无
- 符号改名：`method_11320`→`getColor`、`method_5439`→`getContainerSize`、`method_5438`→`getItem`、`method_7960`→`isEmpty`
- 注入目标：`method="getAnalogOutputSignal"`；`@At` INVOKE `Lnet/minecraft/world/inventory/AbstractContainerMenu;getRedstoneSignalFromBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)I`
- 额外资源：`ShulkerBoxPowerOutputExpansionColour` 的 17 个枚举选项进 lang
- 备注：与 `MagicBox/ShulkerBoxBlockMixin` 同目标同注入点，两个 Mixin 类可共存；颜色不符时走 `original.call`

### mixins/rule/StackableShulkerBoxesEnhancement/HopperBlockEntityMixin.java
- import 删除：`me.fallenbreath.conditionalmixin.api.annotation.{Condition, Restriction}`
- import 新增：`utils.annotation.mixin.{Condition, Restriction}`
- 符号改名：`method_7909`→`getItem`
- 注入目标：`method="tryMoveInItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;"`（refmap `HopperBlockEntity.tryMoveInItem(Container,Container,ItemStack,I,Direction)ItemStack`）；`@At` INVOKE `Lnet/minecraft/world/level/block/entity/HopperBlockEntity;canMergeItems(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z`
- 额外资源：无
- 备注：`carpet.CarpetSettings.shulkerBoxStackSize` javap 实测存在（`public static int`），直接引用即可；`transfer`→`tryMoveInItem`，参数类型 `Inventory`→`Container`

### mixins/rule/StackableShulkerBoxesEnhancement/lithium/HopperHelperMixin.java
- import 删除：整个 `net.caffeinemc.mods.lithium.*` import（文件不再编译进 jar）
- import 新增：无
- 符号改名：`method_7909`→`getItem`（若日后恢复）
- 注入目标：`ItemStack.getMaxStackSize()I`（refmap 该条为 `WARN external-or-unmapped`，dump 全局无 `HopperHelper.tryMoveSingleItem(...)Z`）
- 额外资源：无
- 备注：**删除**。lithium 无 NeoForge 1.21.1 版；`lithium` 只作字符串 modId 出现在 `@Condition` 里。s0 结论：文件不进 `cca.mixins.json`，源码不迁；如需保留目录结构，用 `utils/EmptyClass` 占位

### mixins/rule/WhatIsThis/Happy.java
- import 删除：`me.fallenbreath.conditionalmixin.api.annotation.{Condition, Restriction}`
- import 新增：`utils.annotation.mixin.{Condition, Restriction}`；`net.minecraft.client.gui.components.SplashRenderer`
- 符号改名：注入方法 `get` → `getSplash`
- 注入目标：`method="getSplash"`（refmap `SplashManager.getSplash()Lnet/minecraft/client/gui/components/SplashRenderer;`）；`@At` INVOKE `Ljava/util/Calendar;setTime(Ljava/util/Date;)V`，`shift = Shift.AFTER`，`cancellable = true`；`@Local(ordinal=0) Calendar`
- 额外资源：本文件属 client 段，必须写进 `cca.client.mixins.json`（不要放 `cca.mixins.json`，否则服务端启动崩）
- 备注：源码↔构建不一致（源码 `method="get"`，refmap `getSplash`），以 refmap 为准；`CCAUtils.DisableEasterEggs` 作为 `Condition.tester`
