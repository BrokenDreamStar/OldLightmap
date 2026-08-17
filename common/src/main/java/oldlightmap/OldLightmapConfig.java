package oldlightmap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod configuration, stored as JSON at config/oldlightmap.json.
 * Defaults: old lightmap ON, end flash OFF because the two effects are incompatible.
 * Fields are written to disk via {@link #save()} (Sodium's storage handler).
 */
public final class OldLightmapConfig {
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("oldlightmap.json");
	private static final OldLightmapConfig INSTANCE = load();

	/** Use the 1.21.8 lightmap algorithm; when false the target version's vanilla lightmap is used. */
	public boolean oldLightmap = true;
	/** Keep the target version's End flash effect when the vanilla lightmap is active. */
	public boolean endFlash = false;

	private OldLightmapConfig() {
	}

	public static OldLightmapConfig get() {
		return INSTANCE;
	}

	public static boolean isOldLightmap() {
		return INSTANCE.oldLightmap;
	}

	public static boolean isEndFlash() {
		return !INSTANCE.oldLightmap && INSTANCE.endFlash;
	}

	/**
	 * Changes the lightmap mode and disables End flash whenever old lighting is selected.
	 */
	public void setOldLightmap(boolean enabled) {
		this.oldLightmap = enabled;
		if (enabled) {
			this.endFlash = false;
		}
	}

	/**
	 * Changes End flash only when it is compatible with the selected lightmap mode.
	 */
	public void setEndFlash(boolean enabled) {
		this.endFlash = !this.oldLightmap && enabled;
	}

	private void normalize() {
		if (this.oldLightmap) {
			this.endFlash = false;
		}
	}

	public void save() {
		this.normalize();
		try {
			Files.createDirectories(PATH.getParent());
			JsonObject json = new JsonObject();
			json.addProperty("oldLightmap", this.oldLightmap);
			json.addProperty("endFlash", this.endFlash);
			Files.writeString(PATH, json.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			OldLightmap.LOGGER.error("Failed to save config to {}", PATH, e);
		}
	}

	private static OldLightmapConfig load() {
		OldLightmapConfig config = new OldLightmapConfig();
		if (Files.exists(PATH)) {
			try {
				JsonObject json = JsonParser.parseString(Files.readString(PATH)).getAsJsonObject();
				if (json.has("oldLightmap")) {
					config.oldLightmap = json.get("oldLightmap").getAsBoolean();
				}
				if (json.has("endFlash")) {
					config.endFlash = json.get("endFlash").getAsBoolean();
				}
			} catch (Exception e) {
				OldLightmap.LOGGER.error("Failed to load config from {}, using defaults", PATH, e);
			}
		}
		config.normalize();
		return config;
	}
}
