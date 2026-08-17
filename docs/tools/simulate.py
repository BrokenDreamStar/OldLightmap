#!/usr/bin/env python3
"""Simulate the three lightmap.fsh algorithms (1.21.8 / pack(1.21.9-era) / 26.1.2)
and compare lightmaps across the day-night cycle and gamma values.
All implementations are direct ports of the actual shader sources.
"""
import math


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def lerp(a, b, t):
    return a + (b - a) * t


def lerp3(c, d, t):
    return (lerp(c[0], d[0], t), lerp(c[1], d[1], t), lerp(c[2], d[2], t))


# ---------------------------------------------------------------- 1.21.8 ---
def shader_1218(block_level, sky_level, ubo):
    def get_brightness(level):
        curved = level / (4.0 - 3.0 * level)
        return lerp(curved, 1.0, ubo["AmbientLightFactor"])

    block_brightness = get_brightness(block_level) * ubo["BlockFactor"]
    sky_brightness = get_brightness(sky_level) * ubo["SkyFactor"]

    b = block_brightness
    color = (
        b,
        b * ((b * 0.6 + 0.4) * 0.6 + 0.4),
        b * (b * b * 0.6 + 0.4),
    )
    if ubo["UseBrightLightmap"] != 0:
        color = lerp3(color, (0.99, 1.12, 1.0), 0.25)
        color = tuple(clamp(c) for c in color)
    else:
        slc = ubo["SkyLightColor"]
        color = (color[0] + slc[0] * sky_brightness,
                 color[1] + slc[1] * sky_brightness,
                 color[2] + slc[2] * sky_brightness)
        color = lerp3(color, (0.75, 0.75, 0.75), 0.04)
        darkened = tuple(c * 0.7 if i != 1 else c * 0.6 for i, c in enumerate(color))
        color = lerp3(color, darkened, ubo["DarkenWorldFactor"])

    nvf = ubo["NightVisionFactor"]
    if nvf > 0.0:
        mc = max(color)
        if mc < 1.0:
            bright = tuple(c / mc for c in color)
            color = lerp3(color, bright, nvf)

    if ubo["UseBrightLightmap"] == 0:
        color = tuple(clamp(c - ubo["DarknessScale"]) for c in color)

    def notGamma(x):
        nx = 1.0 - x
        return 1.0 - nx * nx * nx * nx

    ng = tuple(notGamma(c) for c in color)
    color = lerp3(color, ng, ubo["BrightnessFactor"])
    color = lerp3(color, (0.75, 0.75, 0.75), 0.04)
    color = tuple(clamp(c) for c in color)
    return color


# ------------------------------------------------------- pack (1.21.9 era) ---
def shader_pack(block_level, sky_level, ubo):
    def adj_ambient():
        f = ubo["AmbientLightFactor"]
        return 0.0 if 0.24 < f < 0.26 else f

    def is_end():
        r, g, b = ubo["SkyLightColor"]
        return (int(r * 255) << 16) + (int(g * 255) << 8) + int(b * 255) == 0xE57FFF

    def get_brightness(level):
        curved = level / (4.0 - 3.0 * level)
        return lerp(curved, 1.0, adj_ambient())

    block_brightness = get_brightness(block_level) * ubo["BlockFactor"]
    sky_brightness = get_brightness(sky_level) * ubo["SkyFactor"]

    b = block_brightness
    color = (
        b,
        b * ((b * 0.6 + 0.4) * 0.6 + 0.4),
        b * (b * b * 0.6 + 0.4),
    )
    if is_end():
        color = lerp3(lerp3(color, ubo["AmbientColor"], ubo["AmbientLightFactor"]), color, sky_brightness)
        slc = ubo["SkyLightColor"]
        color = (color[0] + slc[0] * 1.5 * sky_brightness,
                 color[1] + slc[1] * 1.5 * sky_brightness,
                 color[2] + slc[2] * 1.5 * sky_brightness)
        color = lerp3(color, (0.75, 0.75, 0.75), 0.04)
    else:
        slc = ubo["SkyLightColor"]
        color = (color[0] + slc[0] * sky_brightness,
                 color[1] + slc[1] * sky_brightness,
                 color[2] + slc[2] * sky_brightness)
        color = lerp3(color, (0.75, 0.75, 0.75), 0.04)
        darkened = tuple(c * 0.7 if i != 1 else c * 0.6 for i, c in enumerate(color))
        color = lerp3(color, darkened, ubo["DarkenWorldFactor"])

    nvf = ubo["NightVisionFactor"]
    if nvf > 0.0:
        mc = max(color)
        if mc < 1.0:
            bright = tuple(c / mc for c in color)
            color = lerp3(color, bright, nvf)

    color = tuple(clamp(c - (ubo["DarknessScale"] if is_end() else 0.0)) for c in color)

    if is_end():
        def notGamma(c):
            m = max(c)
            mi = 1.0 - m
            ms = 1.0 - mi * mi * mi * mi
            return c * (ms / m)
        ng = tuple(notGamma(c) for c in color)
    else:
        ng = tuple(1.0 - (1.0 - c) ** 4 for c in color)
    color = lerp3(color, ng, ubo["BrightnessFactor"])
    color = lerp3(color, (0.75, 0.75, 0.75), 0.04)
    color = tuple(clamp(c) for c in color)
    return color


# -------------------------------------------------------------- 26.1.2 ---
def shader_2612(block_level, sky_level, ubo):
    def get_brightness(level):
        return level / (4.0 - 3.0 * level)

    def notGamma(color):
        m = max(color)
        mi = 1.0 - m
        ms = 1.0 - mi * mi * mi * mi
        return tuple(c * (ms / m) for c in color)

    def parabolic(level):
        return (2.0 * level - 1.0) ** 2

    block_brightness = get_brightness(block_level) * ubo["BlockFactor"]
    sky_brightness = get_brightness(sky_level) * ubo["SkyFactor"]

    nvc = ubo["NightVisionColor"]
    nvf = ubo["NightVisionFactor"]
    nv = tuple(c * nvf for c in nvc)
    color = tuple(max(ubo["AmbientColor"][i], nv[i]) for i in range(3))

    slc = ubo["SkyLightColor"]
    color = (color[0] + slc[0] * sky_brightness,
             color[1] + slc[1] * sky_brightness,
             color[2] + slc[2] * sky_brightness)

    tint = ubo["BlockLightTint"]
    blc = lerp3(tint, (1.0, 1.0, 1.0), 0.9 * parabolic(block_level))
    color = (color[0] + blc[0] * block_brightness,
             color[1] + blc[1] * block_brightness,
             color[2] + blc[2] * block_brightness)

    color = lerp3(color, tuple(c * 0.7 if i != 1 else c * 0.6 for i, c in enumerate(color)),
                  ubo["BossOverlayWorldDarkening"])
    color = tuple(c - ubo["DarknessScale"] for c in color)
    color = tuple(clamp(c) for c in color)
    color = lerp3(color, notGamma(color), ubo["BrightnessFactor"])
    return color


# ----------------------------------------------------------------- utils ---
def render(shader, ubo, name, sky_factor, gamma, block_levels=(0.0, 0.4, 0.8, 1.0),
           note=""):
    print(f"### {name}  (skyFactor={sky_factor}, gamma={gamma}) {note}")
    skyDarken = 1.0 - sky_factor  # rough: 0 day .. 1 night
    skycol = lerp3((skyDarken, skyDarken, 1.0), (1.0, 1.0, 1.0), 0.35)
    u = dict(ubo)
    u["SkyFactor"] = sky_factor
    u["BrightnessFactor"] = gamma
    u["SkyLightColor"] = skycol
    row = []
    for bl in block_levels:
        r, g, b = shader(bl, 1.0, u)  # sky level = 1.0 (fully lit sky cell)
        row.append(f"B{bl:.1f}: ({r:.3f},{g:.3f},{b:.3f})")
    print("   sky cell:", "  ".join(row))
    row = []
    for bl in block_levels:
        r, g, b = shader(bl, 0.05, u)  # sky level 0 (dark sky cell)
        row.append(f"B{bl:.1f}: ({r:.3f},{g:.3f},{b:.3f})")
    print("   dark cell:", "  ".join(row))
    print()


def main():
    # common UBO values (overworld, no effects)
    common1218 = dict(AmbientLightFactor=0.0, BlockFactor=1.5, UseBrightLightmap=0,
                      NightVisionFactor=0.0, DarknessScale=0.0, DarkenWorldFactor=0.0)
    common_pack = dict(AmbientLightFactor=0.0, BlockFactor=1.4, NightVisionFactor=0.0,
                       DarknessScale=0.0, DarkenWorldFactor=0.0,
                       AmbientColor=(0.0, 0.0, 0.0))
    common2612 = dict(BlockFactor=1.4, NightVisionFactor=0.0, DarknessScale=0.0,
                      BossOverlayWorldDarkening=0.0,
                      BlockLightTint=(1.0, 0.847, 0.8),
                      AmbientColor=(0.0, 0.0, 0.0),
                      NightVisionColor=(0.6, 0.6, 0.6))

    for sf, label in [(1.0, "NOON"), (0.5, "DUSK"), (0.05, "NIGHT")]:
        render(shader_1218, common1218, "1.21.8", sf, 0.5)
        render(shader_pack, common_pack, "pack (1.21.9-era)", sf, 0.5)
        render(shader_2612, common2612, "26.1.2", sf, 0.5)

    print("=" * 70)
    print("GAMMA SWEEP (night, skyFactor=0.05)")
    for gamma in (1.0, 2.0, 5.0, 15.0):
        render(shader_1218, common1218, "1.21.8", 0.05, gamma)
        render(shader_pack, common_pack, "pack (1.21.9-era)", 0.05, gamma)
        render(shader_2612, common2612, "26.1.2", 0.05, gamma)


if __name__ == "__main__":
    main()
