#version 330
#extension GL_ARB_separate_shader_objects : require

// Old Lightmap - restores the 1.21.8 lightmap algorithm on Minecraft 26.3
// --------------------------------------------------------------------------
// The lightmap (16x16 day/night texture) is generated every frame on the GPU
// by this shader. 1.21.9 rewrote it (saturated max-component notGamma, warm
// ambient mixing, no End bright-lightmap branch) and 26.1 reworked the
// LightmapInfo uniform block (BlockLightTint/NightVisionColor added,
// AmbientLightFactor removed). This file re-implements the 1.21.8 algorithm
// against the 26.1+ uniform block.
//
// 26.3 compiles every shader to SPIR-V (renderpearl/shaderc), which requires
// explicit interface locations, hence the ARB_separate_shader_objects
// extension and the layout(location = 0) qualifiers that the 26.3 vanilla
// shaders also use.
//
// Variant: lightmap_old.fsh - fully 1.21.8 (no End flash, per-channel notGamma
// everywhere).
//
// The uniform block must declare every field the game uploads, in the same
// order as Lightmap.java's Std140Builder (std140 offsets are positional).

layout(std140) uniform LightmapInfo {
    float SkyFactor;
    float BlockFactor;
    float NightVisionFactor;
    float DarknessScale;
    float BossOverlayWorldDarkeningFactor;
    float BrightnessFactor;
    vec3 BlockLightTint;
    vec3 SkyLightColor;
    vec3 AmbientColor;
    vec3 NightVisionColor;
} lightmapInfo;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// The End's sky light colour is fixed at #ac60cd (26.1+ dimension attribute;
// overworld day is #ffffff, night #7a7aff, nether #7a7aff). 1.21.8 used the
// UseBrightLightmap branch for the End. Define before use (GLSL requires
// declarations to precede calls).
bool isEnd() {
    vec3 c = lightmapInfo.SkyLightColor * 255.0;
    return abs(c.r - 172.0) < 8.0 && abs(c.g - 96.0) < 8.0 && abs(c.b - 205.0) < 8.0;
}

// 26.1+ no longer uploads a separate ambient light factor: the value is
// premultiplied into AmbientColor, so the factor is recovered from its max
// component (overworld: 0, nether: ~0.19). The End is handled separately
// (1.21.8's End ambient light was 0).
float getAmbientLightFactor() {
    if (isEnd()) {
        return 0.0;
    }
    return max(max(lightmapInfo.AmbientColor.r, lightmapInfo.AmbientColor.g), lightmapInfo.AmbientColor.b);
}

float get_brightness(float level) {
    float curved_level = level / (4.0 - 3.0 * level);
    return mix(curved_level, 1.0, getAmbientLightFactor());
}

vec3 notGamma(vec3 x) {
    vec3 nx = 1.0 - x;
    return 1.0 - nx * nx * nx * nx;
}

void main() {
    float block_brightness = get_brightness(floor(texCoord.x * 16) / 15) * lightmapInfo.BlockFactor;
    float sky_brightness = get_brightness(floor(texCoord.y * 16) / 15) * lightmapInfo.SkyFactor;

    // 1.21.8 block light curve (grey, with the classic warm dip in the middle)
    vec3 color = vec3(
        block_brightness,
        block_brightness * ((block_brightness * 0.6 + 0.4) * 0.6 + 0.4),
        block_brightness * (block_brightness * block_brightness * 0.6 + 0.4)
    );

    if (isEnd()) {
        // 1.21.8 "bright lightmap" branch: the End is lit uniformly and pale
        color = mix(color, vec3(0.99, 1.12, 1.0), 0.25);
        color = clamp(color, 0.0, 1.0);
    } else {
        color += lightmapInfo.SkyLightColor * sky_brightness;
        color = mix(color, vec3(0.75), 0.04);

        vec3 darkened_color = color * vec3(0.7, 0.6, 0.6);
        color = mix(color, darkened_color, lightmapInfo.BossOverlayWorldDarkeningFactor);
    }

    if (lightmapInfo.NightVisionFactor > 0.0) {
        // 1.21.8 night vision: scale up uniformly until one channel hits 1.0
        float max_component = max(color.r, max(color.g, color.b));
        if (max_component < 1.0) {
            vec3 bright_color = color / max_component;
            color = mix(color, bright_color, lightmapInfo.NightVisionFactor);
        }
    }

    if (!isEnd()) {
        color = clamp(color - vec3(lightmapInfo.DarknessScale), 0.0, 1.0);
    }

    vec3 ng = notGamma(color);
    color = mix(color, ng, lightmapInfo.BrightnessFactor);
    color = mix(color, vec3(0.75), 0.04);
    color = clamp(color, 0.0, 1.0);

    fragColor = vec4(color, 1.0);
}
