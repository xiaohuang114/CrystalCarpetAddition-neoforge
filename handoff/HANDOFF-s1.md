# HANDOFF s1 — ModDevGradle 工程骨架

- 日期：2026-10-25
- 本阶段结论：PASS
- 工程根：`D:\Minecraft-mod-development\cca移植neoforge\cca-neoforge`

## 1. 已落地文件（路径 + 作用 + 规模）

| 路径 | 作用 | 规模 |
|---|---|---|
| `settings.gradle` | `pluginManagement`（gradlePluginPortal + `maven.neoforged.net/releases` + mavenCentral）、foojay-resolver 0.9.0、`rootProject.name='CrystalCarpetAddition-neoforge'` | 15 行 |
| `gradle.properties` | 环境（`minecraft_version=1.21.1`、`neo_version=21.1.265`、parchment `2024.11.17`）+ mod 元数据（`mod_id=cca`、`mod_version=1.11.1-neoforge`、`archives_base_name=CrystalCarpetAddition`）+ `carpet_jar=neoforge-carpet-1.21.1-1.0.8+v251027.jar` | 26 行 |
| `build.gradle` | `java-library` + `net.neoforged.moddev 2.0.148`；`neoForge { version; parchment; runs{client, server(--nogui)}; mods{cca} }`；`implementation files("libs/${carpet_jar}")`；`processResources` 展开 `neoforge.mods.toml` | 95 行 |
| `gradle/wrapper/gradle-wrapper.properties` | `distributionUrl` 由 9.2.1 改为 **9.5.1**（见 §2 决策 1） | 7 行 |
| `gradle/wrapper/gradle-wrapper.jar` + `gradlew` + `gradlew.bat` + `gradle/` | 取自 MDK-1.21.1-ModDevGradle main 分支（`gradle-wrapper.jar` 43764 B） | — |
| `libs/neoforge-carpet-1.21.1-1.0.8+v251027.jar` | Carpet NeoForged 只读依赖（1498683 B，复制自 `_ref/`，原件未改） | 1498683 B |
| `src/main/resources/META-INF/neoforge.mods.toml` | `modLoader="javafml"`、`modId=cca`、依赖 neoforge/minecraft/carpet（carpet `ordering="BEFORE"`）、`[[mixins]] config="cca.mixins.json"` | 42 行 |
| `src/main/resources/META-INF/accesstransformer.cfg` | 对应 Fabric accesswidener 两条：`public net.minecraft.world.effect.BadOmenMobEffect`、`public net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal` | 4 行 |
| `src/main/resources/cca.mixins.json` | `required:true` / `minVersion:"0.8"` / `package:"crystal0404.crystalcarpetaddition.mixins"` / `compatibilityLevel:"JAVA_21"` / `mixins:[]` / `client:[]` / `injectors.defaultRequire:1`；**无 `plugin` 字段**（s3 加） | 10 行 |
| `src/main/java/crystal0404/crystalcarpetaddition/CrystalCarpetAdditionMod.java` | `@Mod("cca")` 空壳，构造器 `(IEventBus modBus, ModContainer container)`，只打 `Crystal Carpet Addition bootstrap (skeleton)`；s2 重写 | 18 行 |
| `src/main/resources/icon.png` | 31755 B，复制自 `_src/cca1211/src/main/resources/assets/cca/icon.png`。**任务书未列但必需**：`neoforge.mods.toml` 的 `logoFile="icon.png"` 指的资源必须在 jar 根，否则日志刷 `Unable to load logo` | 31755 B |
| `_tools/probe_run_server.ps1` | 辅助探针（非工程源文件）：以 `cmd /c gradlew.bat runServer` 启动、逐行落盘。**结论是 stdin 方案不可行**，见 §4 | 44 行 |
| `_tools/rcon_probe.py` | 辅助探针：最小 Source RCON 客户端（AUTH + EXECCOMMAND + 多包读取），`python _tools/rcon_probe.py 127.0.0.1 25575 probe123 "carpet"` | 70 行 |
| `_tools/out/runServer_probe.log` | 探针落盘的启动日志（s9 清理） | 63 行 |

构建产物（未入库）：`build/libs/CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar`（34275 B）、`…-sources.jar`（33936 B）。

## 2. 本轮关键决策（含"为什么"）

- **决策：Gradle 发行版用 9.5.1，不用 MDK 配对的 9.2.1。** 理由：`services.gradle.org` 到本机链路极慢，`gradle-9.2.1-bin.zip` 下载 10 分钟零字节；而 `C:\Users\xiao_\.gradle\wrapper\dists\gradle-9.5.1-bin\iq79hdu3mqx29lgffhp8bfmx\` 里已有**完整解包**的发行版（含 `gradle-9.5.1\bin\gradle.bat`、`gradle-9.5.1-bin.zip.ok`）。wrapper 的安装目录 hash 只由 `distributionUrl` 决定，把 URL 换成 `gradle-9.5.1-bin.zip` 即命中同一目录，零下载。备选（换回 9.2.1 硬等下载）没选，因为无意义地阻塞 10 分钟以上。实测 MDG 2.0.148 + Gradle 9.5.1 全流程通过（`createMinecraftArtifacts` 到 `build` 全绿），**不需要**回退。
- **决策：所有 gradle 调用显式 `$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.11"`。** 理由：系统默认 `java` 是 JDK 25（`C:\Program Files\Common Files\Oracle\Java\javapath\java.exe`），`options.release = 21` + JDK 25 daemon 会踩 `Unsupported class file major version` / toolchain 解析歧义。
- **决策：`/carpet` 验证走 RCON，不走控制台 stdin。** 理由：实测 Gradle daemon 的 `runServer` 不转发 stdin —— 向 `cmd /c gradlew.bat runServer` 的 stdin 写入 `carpet` 后，`DedicatedServer` 无任何回显（`run/logs/latest.log` 与 stdout 均无该命令输出），`Done` 之后再无日志行。故把 `cca-neoforge/run/server.properties` 置 `enable-rcon=true` / `rcon.port=25575` / `rcon.password=probe123`，用 `_tools/rcon_probe.py` 发命令。**`run/` 是 dev 运行目录，不进 jar、不是源码**，后续阶段可直接复用这条通道验证 carpet 规则开关。
- **决策：额外落 `src/main/resources/icon.png`。** 理由：任务书 `neoforge.mods.toml` 写了 `logoFile = "icon.png"`，但资源清单里没有图标文件；缺了不会崩，但日志会报规则缺失。图标来源是源项目 `assets/cca/icon.png`。
- **决策：AT 保留。** 理由：Fabric 侧 `cca.accesswidener` 有这两条，1.21.1 里两个目标类经 `javap` 实测**已是 public**（见 §3），AT 属空操作；保留是为了与源工程对齐，且 `META-INF/accesstransformer.cfg` 已被 FML 接受（启动无任何 AT 报错）。没有为了"干净"删掉它——删了反而要在 s5 重新判断。

## 3. 验收结果（命令 + 关键输出原文）

```
> .\gradlew.bat build --console=plain
BUILD SUCCESSFUL in 7m 42s
5 actionable tasks: 5 executed
```
（首次 246.43s 全花在 `createMinecraftArtifacts`：`decompile` 94.02s / `recompile` 58.88s / `applyNeoforgePatches` 12.42s …）

```
> Get-ChildItem build\libs
CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge-sources.jar   33936
CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar           34275
```

```
> .\gradlew.bat runServer --console=plain      （run/logs 与控制台摘录）
[22:31:09] [main/INFO] [ne.ne.fm.lo.mo.ModDiscoverer/]:
     Mod List:
		Name Version (Mod Id)

		Carpet: NeoForged 1.4.147+v1.0.8-251027 (carpet)
		Crystal Carpet Addition 1.11.1-neoforge (cca)
		Minecraft 1.21.1 (minecraft)
		NeoForge 21.1.265 (neoforge)
[22:31:12] [main/INFO] [mixin/]: Compatibility level set to JAVA_21
[22:31:14] [main/INFO] [MixinExtras|Service/]: Initializing MixinExtras via com.llamalad7.mixinextras.service.MixinExtrasServiceImpl(version=0.5.5).
[22:31:32] [modloading-worker-0/INFO] [cr.cr.CrystalCarpetAdditionMod/]: Crystal Carpet Addition bootstrap (skeleton)
[22:31:49] [Server thread/INFO] [minecraft/DedicatedServer]: Done (11.040s)! For help, type "help"
[22:31:49] [Server thread/INFO] [minecraft/RconThread]: RCON running on 0.0.0.0:25575
```

```
> C:\Python314\python.exe _tools\rcon_probe.py 127.0.0.1 25575 probe123 "carpet"
AUTH OK
===== CMD: carpet =====
Current Carpet Mod settings:
Carpet Mod version: 1.4.147+v1.0.8-251027
Browse categories:
[dispenser] [feature] [optimization] [scarpet] [survival] [client] [experimental] [TNT] [bugfix] [creative] [command]
```
（随后 `stop` → `Stopping the server`，25565 端口释放，gradle 任务 exit code 0）

jar 内容（`System.IO.Compression.ZipFile` 列出）：

```
META-INF/MANIFEST.MF                                             25
META-INF/neoforge.mods.toml                                     884
META-INF/accesstransformer.cfg                                  325
cca.mixins.json                                                 216
icon.png                                                      31755
crystal0404/crystalcarpetaddition/CrystalCarpetAdditionMod.class 1073
```
`neoforge.mods.toml` 展开后无 `${}` 残留，关键行：`version = "1.11.1-neoforge"`、`versionRange = "[21.1.265,)"`、`versionRange = "[1.21.1,1.21.2)"`、`versionRange = "[1.4.147,)"`、`ordering = "BEFORE"`（carpet）、`[[mixins]] config = "cca.mixins.json"`。

`run/logs/latest.log` 扫描（11920 B）：

```
> Select-String -Path run\logs\latest.log -Pattern 'Mixin apply failed|ClassNotFoundException|NoClassDefFoundError|NoSuchMethodError|\[(ERROR|FATAL)\]'
no mixin/class errors
none
```

AT 目标类实测（`javap -cp build\moddev\artifacts\neoforge-21.1.265.jar`）：

```
public class net.minecraft.world.effect.BadOmenMobEffect extends net.minecraft.world.effect.MobEffect
public class net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal extends net.minecraft.world.entity.ai.goal.Goal
```
→ 两个 AT 目标在 1.21.1 已是 public，AT 为空操作。

`gradlew --version`：

```
Gradle 9.5.1
Launcher JVM:  21.0.11 (Oracle Corporation 21.0.11+9-LTS-211)
Daemon JVM:    C:\Program Files\Java\jdk-21.0.11
```

## 4. 未解决 / 踩坑（错误原文 + 现象 + 已排查到什么程度）

- 现象：`.\gradlew --version` 卡在 `Downloading https://services.gradle.org/distributions/gradle-9.2.1-bin.zip`，10 分钟无字节进展（后台任务被 kill）。
  已排除：不是 toolchain / MDG 版本问题（换成已解包的 9.5.1 后 `createMinecraftArtifacts` 一次过），是到 `services.gradle.org` 的链路问题。
  处置：见 §2 决策 1。**不需要拍板。**
- 现象：`powershell -NoProfile -File _tools\probe_run_server.ps1` 首跑抛 `Win32Exception`，`[System.Diagnostics.Process]::Start($psi)` 返回 null，紧接着 `$p.StandardOutput.ReadLine()` 报 `You cannot call a method on a null-valued expression.`
  根因（已定位）：**Windows PowerShell 5.1 对无 BOM 的 `.ps1` 按 ANSI 解码**，脚本里字面量 `D:\Minecraft-mod-development\cca移植neoforge` 被解成乱码（控制台回显为 `cca绉绘neoforge`）→ `WorkingDirectory` 不存在 → `Win32Exception`。
  修复：脚本内**不写非 ASCII 字面量**，路径全部由 `Split-Path $PSScriptRoot -Parent` 推导。（另注：本机**没有** pwsh 7，`pwsh` 实际是 PowerShell 5.1。）
- 现象：probe 脚本跑到工具 10 分钟超时而卡死。
  根因（已定位）：① `StandardOutput.ReadLine()` 阻塞期间 `while ((Get-Date) -lt $deadline)` 的 deadline 永不判定；② 更根本的是 gradle daemon 不转发 stdin，`Done (…)` 之后再无任何输出行。
  结论（长期有效）：**headless dev server 的交互必须走 RCON**。
- 遗留（s2 注意）：`run/server.properties` 现在是 `enable-rcon=true` / `rcon.password=probe123` / `rcon.port=25575`。若不想留本地端口，改回 `enable-rcon=false` 即可，不影响构建。
- 遗留（s9 清理）：`_tools/out/runServer_probe.log`、`cca-neoforge/run/`（world/logs/eula.txt）是探测与 dev 产物。

## 5. 下一阶段起点

- 第一个待办：s2 —— 重写 `CrystalCarpetAdditionMod`（构造器内**先**赋 `version`，**再**触碰 `CCAExtension`），新建 `CCAExtension implements carpet.CarpetExtension` 与 `CCASettings`。
- 必读文件：`prompts/s2-入口与设置管理.md`、`prompts/FACTS.md` 第 3/4/8 节、`analysis/CONVERSION-CHECKLIST.md` 里 `CrystalCarpetAdditionMod` / `CCAExtension` / `CCASettings` 三段、`handoff/HANDOFF-s0.md` 的勘误 E5/E7。
- 已就绪、s2 可直接依赖的事实：
  - `carpet` 已随 dev 运行时 classpath 加载（**勘误 E8 的 `run/mods` 兜底未用上**），`Carpet: NeoForged 1.4.147+v1.0.8-251027 (carpet)`。
  - MixinExtras 0.5.5 由 NeoForge 自动初始化，`build.gradle` 里没有也不需要声明。
  - `cca.mixins.json` 空表被正常接受；`Compatibility level set to JAVA_21` 已出现，说明 `[[mixins]] config=` 声明链路通。
  - 交互验证通道：`enable-rcon=true` + `_tools/rcon_probe.py`。
- 前置检查命令：
  ```powershell
  $env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.11"
  cd "D:\Minecraft-mod-development\cca移植neoforge\cca-neoforge"
  .\gradlew.bat compileJava --console=plain
  ```
- 版本号写法（E7 复核）：`container.getModInfo().getVersion().toString()`，任何异常/空值回退常量 `"1.11.1"`；不要用 `getQualifier()`。

## 6. 对 FACTS.md 的修正/新增（下一阶段请按这些覆盖 FACTS）

- `FACTS 第 1 节`「工具链 MDG 2.0.148」补充：**Gradle 发行版用 9.5.1**（MDK 官方给 9.2.1，但本机 9.2.1 下载不可用；9.5.1 与 MDG 2.0.148 实测全通）。`gradlew --version` → `Gradle 9.5.1` / `Launcher JVM 21.0.11`。
- 新增事实：**`gradle-wrapper.properties` 的 dists 目录 hash 只由 `distributionUrl` 决定**；把 URL 改成已解包的版本可零下载复用（本机已有 `gradle-9.5.1-bin\iq79hdu3mqx29lgffhp8bfmx\`）。
- 新增事实：本机 **JDK 21 路径 `C:\Program Files\Java\jdk-21.0.11`；系统默认 `java` 是 JDK 25**；每个新 shell 都要先设 `JAVA_HOME`。本机**无 pwsh 7**，`pwsh` 即 PowerShell 5.1（.ps1 里禁止非 ASCII 字面量）。
- 新增事实：**Gradle daemon 的 `runServer` 不转发 stdin**，headless 下无法用控制台发命令；验证走 RCON（`enable-rcon=true`、`rcon.port=25575`、`rcon.password=probe123`、`_tools/rcon_probe.py`）。
- 新增事实：**`net.minecraft.world.effect.BadOmenMobEffect` 与 `net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal` 在 1.21.1 已是 `public`**，`META-INF/accesstransformer.cfg` 那两条 AT 是空操作（保留无害，别为了"干净"删掉）。
- 新增事实：carpet 通过 `implementation files("libs/…")` 直接进 dev 运行时 classpath 即被 FML 加载，**不需要**复制到 `run/mods`（E8 兜底未触发）。`implementation` 而非 `compileOnly` 是关键。
- `FACTS 第 4 节`末「NeoForge 的 mixin 配置要在 `META-INF/neoforge.mods.toml` 里声明 `[[mixins]] config=`」——**已实测生效**（空 `cca.mixins.json` 被正常解析，`Compatibility level set to JAVA_21`）。
- 新增事实：`gradle.properties` 未启用 configuration cache（MDK 模板默认开启），本阶段刻意不开，避免与 MDG 2.0.148 交互问题；构建耗时主因是 `createMinecraftArtifacts`（首跑 246s），后续增量构建为秒级。

## 7. 发现但本阶段未处理

- `build.gradle` 的 `expandProps` 不含 `issueTrackerURL`（`neoforge.mods.toml` 里是硬编码 URL）。要版本化发布时再补。
- AT 只存在于 `META-INF/accesstransformer.cfg`，**dev 编译期不生效**（`build/moddev/serverRunVmArgs.txt` 里没有 `--accessTransformer`；由 FML 在运行期从 mod 资源路径读取）。s2/s5 若真要访问非 public 成员，需在 `build.gradle` 加 `neoForge { accessTransformers.from 'src/main/resources/META-INF/accesstransformer.cfg' }` 才能同时作用于编译期。本轮两个 AT 目标本来就是 public，故未加。
- `cca.mixins.json` 目前 `mixins: []` 且无 `plugin` 字段；`client: []` 已预留（s9 的 `WhatIsThis/Happy` 客户端 mixin 届时进这里或单独配置）。
- `run/` 下已有 `world/`、`logs/`、`eula.txt`、`server.properties`，未加 `.gitignore`（s9 打包/发布时处理）。
- `_tools/probe_run_server.ps1` 的 stdin 方案已被证否，保留仅作反面证据；后续交互一律用 `_tools/rcon_probe.py`。
