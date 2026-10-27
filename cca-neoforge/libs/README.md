# `libs/` —— 本地依赖放置处

本工程对 **Carpet: NeoForged** 的依赖是 `implementation files("libs/${carpet_jar}")`
（见 `build.gradle:51`），即**不从 Maven 拉取**，需要你手动把 jar 放在这里。

## 需要放的文件

```
cca-neoforge/libs/neoforge-carpet-1.21.1-1.0.8+v251027.jar
```

文件名必须与 `gradle.properties` 的 `carpet_jar` 完全一致。若你拿到的是别的构建，改
`gradle.properties` 里那一行即可，不要改 `build.gradle`。

## 从哪里拿

- Modrinth：<https://modrinth.com/mod/carpet-neoforged> （筛选 `1.21.1` + `NeoForge`）
- CurseForge：<https://www.curseforge.com/minecraft/mc-mods/carpet-neoforged>
- 上游源码：<https://github.com/Crystal0404/Carpet-NeoForged>

## 为什么不在仓库里

1. 它是第三方 mod 的二进制，重新分发要走它自己的许可（GPL-3.0，但版本滚动）。
2. 1.5 MB 的二进制放进 git 没有意义，clone 后照样要更新。

`.gitignore` 里是 `cca-neoforge/libs/*.jar`，本说明文件本身会被提交。
