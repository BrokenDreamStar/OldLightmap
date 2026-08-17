package oldlightmap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Loads the lightmap shader variants for the target's explicit UBO family.
 * The active variant is selected at shader-compile time (see ShaderManagerMixin).
 */
public final class OldLightmapShaders {
	private static final String GROUP_PATH = "/assets/oldlightmap/shaders/shader_ubo_group.properties";
	private static final String LEGACY_OLD_PATH = "/assets/oldlightmap/shaders/core/lightmap_old_1_21.fsh";
	private static final String LEGACY_OLD_FLASH_PATH = "/assets/oldlightmap/shaders/core/lightmap_old_flash_1_21.fsh";
	private static final String MODERN_OLD_PATH = "/assets/oldlightmap/shaders/core/lightmap_old_26_1.fsh";
	private static final String MODERN_OLD_FLASH_PATH = "/assets/oldlightmap/shaders/core/lightmap_old_flash_26_1.fsh";

	private static final ShaderUboGroup SHADER_UBO_GROUP = loadShaderUboGroup();
	private static String oldSource;
	private static String oldFlashSource;

	private OldLightmapShaders() {
	}

	/**
	 * Returns the source for the target's UBO-compatible shader variant, or null
	 * if the explicitly selected resource could not be read.
	 */
	public static String getSource(boolean endFlash) {
		String path = shaderPath(endFlash);
		String cached = endFlash ? oldFlashSource : oldSource;
		if (cached == null) {
			try (InputStream in = OldLightmapShaders.class.getResourceAsStream(path)) {
				if (in == null) {
					OldLightmap.LOGGER.error("UBO group {} shader {} not found", SHADER_UBO_GROUP.id, path);
					return null;
				}
				cached = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				if (endFlash) {
					oldFlashSource = cached;
				} else {
					oldSource = cached;
				}
			} catch (IOException e) {
				OldLightmap.LOGGER.error("Failed to read {} UBO shader {}", SHADER_UBO_GROUP.id, path, e);
				return null;
			}
		}
		return cached;
	}

	private static String shaderPath(boolean endFlash) {
		if (SHADER_UBO_GROUP == ShaderUboGroup.ONE_TWENTY_ONE) {
			return endFlash ? LEGACY_OLD_FLASH_PATH : LEGACY_OLD_PATH;
		}
		return endFlash ? MODERN_OLD_FLASH_PATH : MODERN_OLD_PATH;
	}

	private static ShaderUboGroup loadShaderUboGroup() {
		try (InputStream in = OldLightmapShaders.class.getResourceAsStream(GROUP_PATH)) {
			if (in == null) {
				OldLightmap.LOGGER.warn("UBO shader group marker {} is missing; using 26.1 group", GROUP_PATH);
				return ShaderUboGroup.TWENTY_SIX_ONE;
			}
			Properties properties = new Properties();
			properties.load(in);
			return ShaderUboGroup.fromId(properties.getProperty("group"));
		} catch (IOException e) {
			OldLightmap.LOGGER.warn("Failed to read UBO shader group marker {}; using 26.1 group", GROUP_PATH, e);
			return ShaderUboGroup.TWENTY_SIX_ONE;
		}
	}

	private enum ShaderUboGroup {
		ONE_TWENTY_ONE("1_21"),
		TWENTY_SIX_ONE("26_1");

		private final String id;

		ShaderUboGroup(String id) {
			this.id = id;
		}

		private static ShaderUboGroup fromId(String id) {
			for (ShaderUboGroup group : values()) {
				if (group.id.equals(id)) {
					return group;
				}
			}
			OldLightmap.LOGGER.warn("Unknown UBO shader group {}; using 26.1 group", id);
			return TWENTY_SIX_ONE;
		}
	}
}
