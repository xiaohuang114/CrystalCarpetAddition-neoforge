# STATIC-CHECK · s9 四项静态检查定稿

> 检查对象：`cca-neoforge\src` 下全部 **41** 个 `.java`（含 resources 无 java）。
> 判定口径：**只统计非注释行**；`ScreenHandler` / `StatusEffect` 作为**类名/方法名的子串**出现时不算 Yarn 残留（上游类名按硬约束 6 原样保留）。
> 执行时间：首轮 2026-10-27 00:17，**方案① 修复后复跑 2026-10-27 00:39**（Asia/Shanghai）。
> 执行命令环境：`$root='D:\Minecraft-mod-development\cca移植neoforge'`，`Set-Location $root`。
>
> **版本说明**：本文件首版按 40 文件（`mixins` 21 条）统计。用户拍板采用漏斗修复方案 ① 后新增
> `mixins\rule\StackableShulkerBoxesEnhancement\HopperBlockEntityEjectMixin.java`，检查对象变为 **41** 文件（`mixins` 22 条）。
> 下表数字均为**41 文件版本**的复跑结果；新增文件没有引入任何一项命中。

## 0. 检查对象计数

```powershell
$src = (Get-ChildItem cca-neoforge\src -Recurse -Include *.java).FullName
$src.Count
```

```
41
```

## 1. intermediary 残留（`class_<n>` / `method_<n>` / `field_<n>`）

```powershell
(Select-String -Path $src -Pattern 'class_\d+|method_\d+|field_\d+').Count
```

```
0
```

**判定：PASS。** 编译期与运行时都用 Mojang 官方名，无 intermediary 标识符残留。

> 注：`_decomp\mojmap\...\HopperBlockEntityMixin.java` 里出现过 `first.method_7909()`（Yarn 名），那是**上游 Fabric 源码**，不是我们的产物；我们的 `cca-neoforge\src` 里已是 `first.getItem()`。

## 2. `remap = false`

```powershell
(Select-String -Path $src -Pattern 'remap\s*=\s*false').Count
```

```
0
```

**判定：PASS。** NeoForge 1.21.1 运行时就是官方名，没有 refmap 层，全部 `remap = false` 已删（机械转换规则 1）。

## 3. `net.fabricmc` / `me.fallenbreath` 非注释命中

```powershell
(Select-String -Path $src -Pattern 'net\.fabricmc|me\.fallenbreath' |
    Where-Object { $_.Line -notmatch '^\s*(\*|//|/\*)' }).Count
```

```
0
```

**判定：PASS。** 入口已改为 NeoForge `@Mod` 构造器注入；conditional-mixin 的 `Restriction` / `Condition` / `Condition.Type` / `ConditionTester` / `MixinRestrictionProcessor` / `CCAMixinConfigPlugin` 全部自建于 `crystal0404.crystalcarpetaddition.utils.*`。

## 4. Yarn 名残留（非注释）

第一遍（朴素子串，含注释与误报）：

```powershell
(Select-String -Path $src -Pattern 'getMaxCount\(|isIn\(|onStateReplaced|ServerWorld|MathHelper|ScreenHandler|BlockWithEntity|WorldAccess|StatusEffect|getWorld\(\)').Count
```

```
5
```

五处原文（41 文件版本）：

```
ScreenHandlerMixin.java:24:       public abstract class ScreenHandlerMixin {
ScreenHandlerMixin.java:46:       // ported from .../mixins/rule/ItemShadowing/ScreenHandlerMixin.java (s6)
BadOmenStatusEffectMixin.java:23: public abstract class BadOmenStatusEffectMixin {
BadOmenStatusEffectMixin.java:49: // ported from .../mixins/rule/ReIntroduceOldVersionRaid/BadOmenStatusEffectMixin.java (s8)
RaidMixin.java:27:                private Holder<MobEffect> startMixin_getStatusEffect(Holder<MobEffect> original) {
```

去掉 2 条 `// ported from` 注释行后为 **3** 条，**三处均为子串误报**，理由：

| 命中 | 为什么不是 Yarn 残留 |
|---|---|
| `ScreenHandlerMixin` | 是**类名**（上游同名，硬约束 6 要求原样保留）。它 mixin 的目标方法体内用的是 `Slot` / `Inventory` / `ItemStack`，无 `ScreenHandler` 类型引用。 |
| `BadOmenStatusEffectMixin` | 同上，类名。正文用的是 `MobEffect` / `ServerPlayer` / `ServerLevel`（已是 Mojmap）。 |
| `startMixin_getStatusEffect` | 是**方法名**（上游命名）。参数与返回值已是 `Holder<MobEffect>`，`getEffect` 的注入目标是 `Lnet/minecraft/...;getEffect(...)`. |

第二遍（加词边界 + 排除注释行）：

```powershell
(Select-String -Path $src -Pattern '\bgetMaxCount\(|\bisIn\(|\bonStateReplaced\b|\bServerWorld\b|\bMathHelper\b|\bScreenHandler\b|\bBlockWithEntity\b|\bWorldAccess\b|\bStatusEffect\b|\bgetWorld\(\)' |
    Where-Object { $_.Line -notmatch '^\s*(\*|//|/\*)' }).Count
```

```
0
```

**判定：PASS（真实残留 0）。**

补充：用 `\bWorld\b(?!\s*\.)` 得到的 19 条命中，逐条核对后**全部**属于以下两类，均非 Yarn 类型引用：

1. **描述符包路径** `Lnet/minecraft/world/...`（17 条）——例如

   ```
   HopperBlockEntityMixin.java:24: target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;canMergeItems(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"
   EndermanEntity$PickUpBlockGoalMixin.java:18: target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"
   RaidMixin.java:24: target = "Lnet/minecraft/server/level/ServerPlayer;getEffect(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/effect/MobEffectInstance;"
   ```

2. **形参名 `world`**（2 条，类型本身已是 `Level` / `ServerLevel`）：

   ```
   RaiderEntityMixin.java:38: protected RaiderEntityMixin(EntityType<? extends PatrollingMonster> entityType, Level world) {
   BadOmenStatusEffectMixin.java:41: private boolean cca$tryStartRaid(ServerPlayer player, ServerLevel world) {
   ```

   `world` 只是局部变量名，不构成类型解析，不影响编译与运行。按硬约束 9（不改 s1–s8 已验收文件）**不动**。

## 结论汇总

| # | 检查项 | 命中 | 判定 |
|---|---|---|---|
| 1 | intermediary 残留 | 0 | PASS |
| 2 | `remap = false` | 0 | PASS |
| 3 | `net.fabricmc` / `me.fallenbreath` 非注释 | 0 | PASS |
| 4 | Yarn 名残留非注释 | 0（5 条朴素子串命中 = 2 条 ported 注释 + 3 条类名/方法名；19 条 `World` 全为描述符路径/形参名） | PASS |

**41 文件版本复跑结论：四项仍全 0 真实命中。** 新增的 `HopperBlockEntityEjectMixin.java` 只用到
`net.minecraft.core.Direction` / `net.minecraft.world.Container` / `net.minecraft.world.item.ItemStack` /
`net.minecraft.world.level.Level` / `net.minecraft.world.level.block.HopperBlock` /
`net.minecraft.world.level.block.entity.{BlockEntity,HopperBlockEntity}` / `net.minecraft.world.level.block.state.BlockState`，
全部 Mojmap 名；无 `remap = false`；类级注解用 `crystal0404.crystalcarpetaddition.utils.annotation.mixin.*` 全限定名。
