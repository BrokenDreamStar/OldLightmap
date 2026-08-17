#version 330

// Old Lightmap legacy End-flash shader for Minecraft 1.21.9-1.21.11.
// These versions use the pre-26.1 LightmapInfo UBO layout.

layout(std140) uniform LightmapInfo {
    float AmbientLightFactor;
    float SkyFactor;
    float BlockFactor;
    float NightVisionFactor;
    float DarknessScale;
    float DarkenWorldFactor;
    float BrightnessFactor;
    vec3 SkyLightColor;
    vec3 AmbientColor;
} lightmapInfo;

in vec2 texCoord;
out vec4 fragColor;

bool isEnd() {
    vec3 c = lightmapInfo.AmbientColor;
    return abs(c.r - 0.99) < 0.02
        && abs(c.g - 1.12) < 0.02
        && abs(c.b - 1.0) < 0.02;
}

float get_brightness(float level) {
    float curved_level = level / (4.0 - 3.0 * level);
    return mix(curved_level, 1.0, lightmapInfo.AmbientLightFactor);
}

vec3 notGamma(vec3 x) {
    vec3 nx = 1.0 - x;
    return 1.0 - nx * nx * nx * nx;
}

vec3 endFlashNotGamma(vec3 color) {
    float maxComponent = max(max(color.r, color.g), color.b);
    float maxInverted = 1.0 - maxComponent;
    float maxScaled = 1.0 - maxInverted * maxInverted * maxInverted * maxInverted;
    return color * (maxScaled / maxComponent);
}

void main() {
    float block_brightness = get_brightness(floor(texCoord.x * 16) / 15) * lightmapInfo.BlockFactor;
    float sky_brightness = get_brightness(floor(texCoord.y * 16) / 15) * lightmapInfo.SkyFactor;

    vec3 color = vec3(
        block_brightness,
        block_brightness * ((block_brightness * 0.6 + 0.4) * 0.6 + 0.4),
        block_brightness * (block_brightness * block_brightness * 0.6 + 0.4)
    );

    if (isEnd()) {
        // 1.21.8 bright-lightmap branch plus the 1.21.9 End flash.
        color = mix(color, vec3(0.99, 1.12, 1.0), 0.25);
        color = clamp(color, 0.0, 1.0);
        color += lightmapInfo.SkyLightColor * 1.5 * sky_brightness;
        color = mix(color, vec3(0.75), 0.04);
    } else {
        color += lightmapInfo.SkyLightColor * sky_brightness;
        color = mix(color, vec3(0.75), 0.04);

        vec3 darkened_color = color * vec3(0.7, 0.6, 0.6);
        color = mix(color, darkened_color, lightmapInfo.DarkenWorldFactor);
    }

    if (lightmapInfo.NightVisionFactor > 0.0) {
        float max_component = max(color.r, max(color.g, color.b));
        if (max_component < 1.0) {
            vec3 bright_color = color / max_component;
            color = mix(color, bright_color, lightmapInfo.NightVisionFactor);
        }
    }

    if (!isEnd()) {
        color = clamp(color - vec3(lightmapInfo.DarknessScale), 0.0, 1.0);
    }

    // Keep the normal End identical to 1.21.8. The saturated max-component
    // curve is only part of the newer End flash while the flash is active.
    vec3 ng = isEnd() && lightmapInfo.SkyFactor > 0.0
        ? endFlashNotGamma(color)
        : notGamma(color);
    color = mix(color, ng, lightmapInfo.BrightnessFactor);
    color = mix(color, vec3(0.75), 0.04);
    color = clamp(color, 0.0, 1.0);

    fragColor = vec4(color, 1.0);
}
