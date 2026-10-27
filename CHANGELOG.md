# Changelog

本工程是 [Crystal Carpet Addition](https://github.com/Crystal0404/CrystalCarpetAddition) 的 NeoForge 移植，
版本号跟随上游 `<upstream-version>-neoforge`。

## [1.11.1-neoforge] — 2026-10-27

对应上游 CCA `1.11.1` / Minecraft `1.21.1` / NeoForge `21.1.x`。

### 首次发布

- **13 条规则**全部落地并通过注入级或行为级验证（基线：`[CCA] registered 13 rules`）。
- **22 条 server mixin + 1 条 client mixin**（`rule.WhatIsThis.Happy`，仅在 `client` 数组）。
- 自建 conditional-mixin 兼容层：上游依赖 `me.fallenbreath.conditionalmixin`，NeoForge 无此库，
  改为项目内 `utils/annotation/mixin/{Restriction,Condition}` + `utils/condition/MixinRestrictionProcessor`
  + `utils/CCAMixinConfigPlugin`（实现 `IMixinConfigPlugin`）。
- 入口从 `net.fabricmc.api.ModInitializer` 改为 NeoForge `@Mod` + 构造器注入 `ModContainer`，
  并保留上游的静态初始化顺序（先赋 `version`，再触碰 `CCAExtension`）。

### 相比上游的行为补偿

- **新增 `HopperBlockEntityEjectMixin`**（NeoForge 专属，上游没有这个类）。
  NeoForge 在 `HopperBlockEntity.ejectItems` 偏移 35–43 插入
  `VanillaInventoryCodeHooks.insertHook(HopperBlockEntity)Z`，命中 `IItemHandler` 时直接
  `iconst_1 ireturn`，整条 `addItem → tryMoveInItem → canMergeItems` 路径被短路。
  上游包 `canMergeItems` 的 mixin 因此在 NeoForge 上**永不执行**。
  补偿 mixin 在四条条件同时成立时返回 `false` 且不调 `original`，把控制权还给原版路径。
  四条条件：规则开 / `CarpetSettings.shulkerBoxStackSize != 1` / 目标方块 `instanceof Container`
  / 漏斗 5 槽中存在潜影盒。

### 未移植 / 不生效（已记录在 `analysis/DEVIATIONS.md`）

- `StackableShulkerBoxesEnhancement.HopperHelperMixin`（lithium 兼容）—— lithium 无 1.21.1 NeoForge 版。
- `RemoveVillagerTradeDistanceLimit` —— 上游声明 `require minecraft >= 1.21.4`，1.21.1 上条件注册正常不生效。
- `RaiderEntityMixin` 的"击杀巡逻队长获得 bad_omen"仅到字节码级证据（`D-S9-4`），缺端到端行为证据。

### 打包

- 同时提供 `META-INF/neoforge.mods.toml`（NeoForge 唯一读取的路径）与 `META-INF/mods.toml`
  （老式 Forge 路径，仅为了让 PCL2 等启动器的老解析器也能显示名称/图标/版本）。
  NeoForge 21.1 的 `ModFileParser` 只查 `neoforge.mods.toml`，第二份不会被 FML 读取、不产生重复 mod。
- `neoforge` 依赖范围 `[21.1.0,)`（不是 `[21.1.265,)` —— 后者会把开发版本号误当最低要求）。
