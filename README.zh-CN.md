# Old Lightmap

<p align="center">
  <a href="README.md">English</a> ·
  <b>简体中文</b>
</p>

Old Lightmap 是一个客户端 Fabric 模组，用于在 Minecraft 1.21.9 及更高版本中恢复 Minecraft 1.21.8 的光贴图算法。

## 光照对比

以下截图对比了在time set 11800下 Minecraft 1.21.8 原版光照、Minecraft 1.21.9 原版光照，以及安装 Old Lightmap 后恢复的效果。

<p align="center">
  <img src="assets/readme/lightmap-comparison/1.21.8-vanilla.png" alt="Minecraft 1.21.8 原版光照" width="32%">
  <img src="assets/readme/lightmap-comparison/1.21.9-vanilla.png" alt="Minecraft 1.21.9 原版光照" width="32%">
  <img src="assets/readme/lightmap-comparison/1.21.9-oldlightmap.png" alt="Minecraft 1.21.9 安装 Old Lightmap" width="32%">
</p>

<p align="center">
  <sub>Minecraft 1.21.8 原版</sub> &nbsp;&nbsp;&nbsp;
  <sub>Minecraft 1.21.9 原版</sub> &nbsp;&nbsp;&nbsp;
  <sub>Minecraft 1.21.9 + Old Lightmap</sub>
</p>

## 安装

1. 安装对应版本的 [Fabric Loader](https://fabricmc.net/)。
2. 根据 Minecraft 版本下载对应的 JAR 文件。
3. 将 JAR 文件放入 `.minecraft/mods` 目录。
4. 启动 Minecraft。

Sodium 是可选依赖。如果安装了 Sodium，模组会在 Sodium 设置页面增加配置选项卡；如果未安装 Sodium，则无法配置本模组。

## 从源码构建

环境要求：

- Minecraft 1.21.9–1.21.11 需要 Java 21。
- Minecraft 26.1.x、26.2 和 26.3 需要 Java 25。

构建全部发行版：

```bash
./gradlew clean build
```

构建单个发行版：

```bash
./gradlew :mc1_21:build
./gradlew :mc26_1:build
./gradlew :mc26_2:build
./gradlew :mc26_3:build
```

生成的 JAR 文件位于对应的 `versions/*/build/libs/` 目录中。

## 许可证

Old Lightmap 采用 [GNU Lesser General Public License v3.0（LGPLv3）](LICENSE) 授权。
