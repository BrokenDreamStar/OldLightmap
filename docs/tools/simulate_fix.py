#!/usr/bin/env python3
"""Verify the proposed 'pre-1.21.9 lighting for 26.1.2' shader port:
1.21.8's lightmap algorithm implemented against 26.1.2's LightmapInfo UBO.
Compare cell-by-cell against the exact 1.21.8 shader across the day cycle and gamma.
"""
import math
from simulate import (lerp, lerp3, clamp, shader_1218)

# ------------------------------------------------------------------- port ---
def shader_fix(block_level, sky_level, ubo):
    """1.21.8 algorithm, 26.1.2 UBO fields. End detected via skyLightColor #ac60cd."""
    def ambient_factor():
        # 26.1.2 has no separate ambient factor; AmbientColor is premultiplied.
        # maxComponent recovers the level (overworld 0, nether ~0.19, end ~0.28).
        f = max(ubo["AmbientColor"])
        return 0.0 if 0.24 < f < 0.26 else f  # keep pack's end-0.25 safeguard

    def is_end():
        r, g, b = ubo["SkyLightColor"]
        return abs(r * 255 - 172) < 8 and abs(g * 255 - 96) < 8 and abs(b * 255 - 205) < 8

    def get_brightness(level):
        curved = level / (4.0 - 3.0 * level)
        return lerp(curved, 1.0, ambient_factor())

    block_brightness = get_brightness(block_level) * ubo["BlockFactor"]
    sky_brightness = get_brightness(sky_level) * ubo["SkyFactor"]

    b = block_brightness
    color = (
        b,
        b * ((b * 0.6 + 0.4) * 0.6 + 0.4),
        b * (b * b * 0.6 + 0.4),
    )
    if is_end():
        # 1.21.8 UseBrightLightmap branch (baked bright colour), plus 26.1.2's
        # end flash (purple sky light boosted by skyFactor)
        color = lerp3(color, (0.99, 1.12, 1.0), 0.25)
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
        color = lerp3(color, darkened, ubo["BossOverlayWorldDarkening"])

    nvf = ubo["NightVisionFactor"]
    if nvf > 0.0:
        mc = max(color)
        if mc < 1.0:
            bright = tuple(c / mc for c in color)
            color = lerp3(color, bright, nvf)

    if not is_end():
        color = tuple(clamp(c - ubo["DarknessScale"]) for c in color)

    if is_end():
        def notGamma(c):
            m = max(c)
            mi = 1.0 - m
            ms = 1.0 - mi * mi * mi * mi
            return tuple(x * (ms / m) for x in c)
        ng = notGamma(color)
    else:
        ng = tuple(1.0 - (1.0 - c) ** 4 for c in color)
    color = lerp3(color, ng, ubo["BrightnessFactor"])
    color = lerp3(color, (0.75, 0.75, 0.75), 0.04)
    color = tuple(clamp(c) for c in color)
    return color


def maxdiff(a, b):
    return max(abs(x - y) for x, y in zip(a, b))


def main():
    levels = [i / 15 for i in range(16)]
    scenarios = [
        ("overworld day",   dict(AmbientLightFactor=0.0, BlockFactor=1.5, UseBrightLightmap=0, NightVisionFactor=0.0,
                                 DarknessScale=0.0, DarkenWorldFactor=0.0, SkyFactor=1.0,
                                 SkyLightColor=(0.35, 0.35, 1.0), BrightnessFactor=0.5)),
        ("overworld dusk",  dict(AmbientLightFactor=0.0, BlockFactor=1.5, UseBrightLightmap=0, NightVisionFactor=0.0,
                                 DarknessScale=0.0, DarkenWorldFactor=0.0, SkyFactor=0.5,
                                 SkyLightColor=(0.35, 0.35, 1.0), BrightnessFactor=0.5)),
        ("overworld night", dict(AmbientLightFactor=0.0, BlockFactor=1.5, UseBrightLightmap=0, NightVisionFactor=0.0,
                                 DarknessScale=0.0, DarkenWorldFactor=0.0, SkyFactor=0.05,
                                 SkyLightColor=(1.0, 1.0, 1.0), BrightnessFactor=0.5)),
        ("nether",          dict(AmbientLightFactor=0.1, BlockFactor=1.5, UseBrightLightmap=0, NightVisionFactor=0.0,
                                 DarknessScale=0.0, DarkenWorldFactor=0.0, SkyFactor=0.0,
                                 SkyLightColor=(0.48, 0.48, 1.0), BrightnessFactor=0.5)),
        ("end (flash 0.5)", dict(AmbientLightFactor=0.25, BlockFactor=1.5, UseBrightLightmap=1, NightVisionFactor=0.0,
                                 DarknessScale=0.0, DarkenWorldFactor=0.0, SkyFactor=0.5,
                                 SkyLightColor=(0.675, 0.376, 0.804), BrightnessFactor=0.5)),
    ]
    print(f"{'scenario':<20} {'max cell diff (1.21.8 vs port)':>30}")
    for name, u18 in scenarios:
        ufix = {
            "BlockFactor": u18["BlockFactor"],
            "NightVisionFactor": 0.0,
            "DarknessScale": u18["DarknessScale"],
            "BossOverlayWorldDarkening": u18["DarkenWorldFactor"],
            "BrightnessFactor": u18["BrightnessFactor"],
            "BlockLightTint": (1.0, 0.847, 0.8),
            "SkyLightColor": u18["SkyLightColor"],
            "AmbientColor": (0.0, 0.0, 0.0) if name == "overworld day" or name == "overworld dusk" or name == "overworld night"
                              else ((0.188, 0.157, 0.129) if name == "nether" else (0.247, 0.278, 0.247)),
            "NightVisionColor": (0.6, 0.6, 0.6),
            "SkyFactor": u18["SkyFactor"],
            "UseBrightLightmap": u18["UseBrightLightmap"],
            "AmbientLightFactor": u18.get("AmbientLightFactor", 0.0),
        }
        worst = 0.0
        for bl in levels:
            for sl in levels:
                a = shader_1218(bl, sl, u18)
                b = shader_fix(bl, sl, ufix)
                worst = max(worst, maxdiff(a, b))
        print(f"{name:<20} {worst:>30.4f}")

    print("\nGamma sweep (overworld night, skyFactor=0.05):")
    for gamma in (1.0, 2.0, 5.0, 15.0):
        u18 = dict(AmbientLightFactor=0.0, BlockFactor=1.5, UseBrightLightmap=0, NightVisionFactor=0.0,
                   DarknessScale=0.0, DarkenWorldFactor=0.0, SkyFactor=0.05,
                   SkyLightColor=(1.0, 1.0, 1.0), BrightnessFactor=gamma)
        ufix = dict(BlockFactor=1.4, NightVisionFactor=0.0, DarknessScale=0.0,
                    BossOverlayWorldDarkening=0.0, BrightnessFactor=gamma,
                    BlockLightTint=(1.0, 0.847, 0.8), SkyLightColor=(1.0, 1.0, 1.0),
                    AmbientColor=(0.0, 0.0, 0.0), NightVisionColor=(0.6, 0.6, 0.6),
                    SkyFactor=0.05)
        p18 = shader_1218(0.4, 0.05, u18)
        pfix = shader_fix(0.4, 0.05, ufix)
        pfix2 = shader_fix(0.4, 0.05, dict(ufix, BlockFactor=1.5))
        print(f"gamma={gamma:<4} 1.21.8 B0.4: ({p18[0]:.3f},{p18[1]:.3f},{p18[2]:.3f})"
              f"  port(bf=1.4): ({pfix[0]:.3f},{pfix[1]:.3f},{pfix[2]:.3f})"
              f"  port(bf=1.5): ({pfix2[0]:.3f},{pfix2[1]:.3f},{pfix2[2]:.3f})")


if __name__ == "__main__":
    main()
