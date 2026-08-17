# Lightmap 光照改动调研（1.21.8 → 1.21.9 → 26.1.2）

> 2026-08-09 调研。所有结论均来自官方客户端 jar 的直接 diff / 反编译，而非二手资料。

## TL;DR

- 光贴图（lightmap，16×16 昼夜纹理）自 1.21.5 起在 GPU 上用
  `shaders/core/lightmap.fsh` 每帧生成（顶点由 `screenquad.vsh` 提供）。
- **1.21.9 开发期（25w33a/25w35a/25w36a/25w37a）** 重写了 `lightmap.fsh`：
  - `notGamma` 从逐通道 `1-(1-x)^4` 改为按最大分量缩放 `color*(1-(1-max)^4)/max`
    → 保留色相 → 方块光变暖黄、暗部更黑（MC-301822 "warm/yellow light"，
    Confirmed 未修；MC-301543 夜视变暗，Works As Intended）。
  - 移除末地 `UseBrightLightmap` 分支，新增 `AmbientColor` 与 ambient 混合；
    末地获得紫色闪光（25w33a 新增，End flash）。
  - 25w36a 修 MC-225088（1.17 起光贴图采样偏暗）→ `terrain.vsh` 的 UV 计算
    变化，整体亮度观感改变。
  - 25w37a 全部着色器 GLSL 150 → 330。
- **26.1（2026-03-24）** 再次重构：
  - `LightmapInfo` UBO：删 `AmbientLightFactor`（值预乘进 `AmbientColor`）、
    删 `DarkenWorldFactor`（改名 `BossOverlayWorldDarkeningFactor`）、
    新增 `BlockLightTint`（暖白，方块光按抛物线函数混合色调）与
    `NightVisionColor`。
  - 方块渲染层重构（ChunkSectionLayer）、`position_color_lightmap` /
    `position_color_tex_lightmap` 着色器删除。
- **26.1.2（2026-04-09）本身无渲染改动**（纯热修复）—— 针对 26.1.2 的修复
  在 26.1.x 全系列可用。

## UBO 演变（std140 布局，逐字段）

**1.21.8**（Java: LightTexture `Std140Builder` 8 float + 1 int + 1 vec3）：

| 偏移 | 字段 | 值 |
|---|---|---|
| 0 | `AmbientLightFactor` | 维度 ambient（主世界 0 / 地狱 0.1 / 末地 0） |
| 4 | `SkyFactor` | 时间天空因子（0.05 夜 ~ 1.0 昼） |
| 8 | `BlockFactor` | 闪烁噪声 + **1.5** |
| 12 | `UseBrightLightmap`(int) | 末地 = 1 |
| 16 | `NightVisionFactor` | 夜视/水下视觉强度 |
| 20 | `DarknessScale` | 暗化脉动 × 选项 |
| 24 | `DarkenWorldFactor` | 夜视缓动因子（GameRenderer.c()） |
| 28 | `BrightnessFactor` | max(0, gamma − 暗化混合) |
| 32 | `SkyLightColor` | (skyDarken, skyDarken, 1) lerp 白 35% |

**1.21.9 / 1.21.10 / 25w41a**（7 float + 2 vec3）：去掉 `UseBrightLightmap`，
末尾新增 `AmbientColor`（主世界 (1,1,1)，末地 (0.99,1.12,1.0)）；其余字段
顺序/语义不变。

**26.1.2**（6 float + 4 vec3，LightmapRenderStateExtractor 填充）：

| 偏移 | 字段 | 值 |
|---|---|---|
| 0 | `SkyFactor` | `SKY_LIGHT_FACTOR` 属性（白昼 1.0 / 夜 0.24；末地 0 + 闪光强度） |
| 4 | `BlockFactor` | 闪烁噪声 + **1.4** |
| 8 | `NightVisionFactor` | 夜视强度 |
| 12 | `DarknessScale` | 暗化脉动 |
| 16 | `BossOverlayWorldDarkeningFactor` | 龙战暗化 |
| 20 | `BrightnessFactor` | max(0, gamma − 暗化混合)（与 1.21.8 同公式） |
| 24 | `BlockLightTint` | `BLOCK_LIGHT_TINT` 属性，默认 #ffd8cc（暖白） |
| 32 | `SkyLightColor` | 属性：主世界白昼 #ffffff / 夜 #7a7aff；末地 **#ac60cd**（恒定）；地狱 #7a7aff |
| 48 | `AmbientColor` | 预乘 ambient：主世界黑 / 地狱 #302821 / 末地 #3f473f |
| 64 | `NightVisionColor` | 默认灰 |

26.1.2 算法：`color = max(AmbientColor, NightVisionColor×NV)` + 天空光 +
`mix(BlockLightTint, white, 0.9×parabolic(level))`×块光 + boss 暗化 − 暗化
+ clamp + `mix(color, notGamma(color), BrightnessFactor)`。

## 为什么 pre-1.21.9-lighting 资源包在 26.1.2 上黑屏

该包只覆盖 `lightmap.fsh`，按 1.21.9 的 UBO 偏移读取。26.1 改了布局后：
- 它把 `SkyFactor`（offset 0）当 `AmbientLightFactor`、把 `BrightnessFactor`
  （offset 20）当 `DarkenWorldFactor` 等 —— 全部错位 → 光贴图内容错误/全黑；
- 26.1 起着色器编译/链接失败会强制卸载资源包（失败着色器的物体不可见）。
两者叠加 = 画面全黑。作者仓库 2025-10-11 后无更新（GitHub issue #1 零回复）。

## Mod 修复设计（本仓库）

1. **`assets/minecraft/shaders/core/lightmap.fsh`**：把 1.21.8 的完整算法移植到
   26.1.2 的 UBO：
   - `get_brightness = mix(level/(4-3·level), 1.0, ambient)`，ambient 由预乘的
     `AmbientColor` 取最大分量还原（主世界 0 / 地狱 ~0.19）；末地恒为 0
     （1.21.8 末地 ambient = 0）。
   - 末地检测：`SkyLightColor ≈ #ac60cd`（26.1 末地属性恒定值，与主世界/地狱
     白/蓝不同）→ 走 1.21.8 的 `UseBrightLightmap` 分支（mix 向
     (0.99,1.12,1.0)×0.25，无天空光、无暗化）→ **无末地闪光**（用户选择
     "完全 1.21.8"）。
   - 逐通道 `notGamma = 1-(1-x)^4`；夜视 = 1.21.8 的均匀放大至 1.0；
     最后 `mix(color, 0.75, 0.04)` + clamp。
   - gamma>1 时干净白化（不产生 26.1 的逐通道色相扭曲）。
2. **Mixin** `LightmapRenderStateExtractor`：`blockFactor` 从 `flicker+1.4`
   改回 1.21.8 的 `flicker+1.5`（块光源亮度精确一致）。

## 验证（docs/tools/simulate*.py）

对全部 256 个光贴图格子（16×16）逐格对比 1.21.8 原版着色器与本移植：

| 场景 | 最大差值 |
|---|---|
| 主世界 白昼 / 黄昏 / 夜 | **0.0000** |
| 地狱 | 0.1695（ambient 0.188 vs 0.1，暗处略亮，可接受） |
| 末地 | 有意差异（1.21.8 无闪光；移植按用户选择无闪光） |
| gamma = 1/2/5/15 | 与 1.21.8 完全一致（干净 fullbright） |

## 参考

- Mojira：MC-301822（暖光，Confirmed）、MC-301543（夜视，Works As
  Intended）、MC-301577（光源自阴影，25w36a 修）、MC-225088（低亮度偏暗，
  25w36a 修）
- 官方 changelog：25w31a（screenquad 光贴图管线）、25w33a（End flash +
  去 UseBrightLightmap）、25w35a（notGamma 重写）、25w36a（采样修复）、
  25w37a（GLSL 330 + PER_FACE_LIGHTING）
- 资源包：modrinth.com/resourcepack/pre-1.21.9-lighting（CC BY-NC 4.0，
  GitHub Enchanted-Games/pre-1.21.9-lighting，issue #1）
- 社区：feedback.minecraft.net "Please revert the 1.21.9 Lighting Change"；
  Reddit r/Minecraft 1.21.9 lighting 帖子（"piss filter"）
