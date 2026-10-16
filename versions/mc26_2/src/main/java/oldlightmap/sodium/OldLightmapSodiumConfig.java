package oldlightmap.sodium;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import oldlightmap.OldLightmapConfig;

/**
 * Registers the Old Lightmap options into Sodium's Video Settings screen
 * (via the official sodium:config_api_user entrypoint).
 *
 * Toggling an option updates the in-memory config, and Sodium's storage
 * handler then flushes it to config/oldlightmap.json and triggers a full
 * resource reload (the same reload F3+T performs). ShaderManager re-reads all
 * shader sources and rebuilds the pipeline cache during that reload, so the
 * shader swap and the flash suppression take effect immediately. (Sodium's
 * built-in REQUIRES_ASSET_RELOAD flag only reloads textures, so we handle the
 * reload ourselves.)
 */
public final class OldLightmapSodiumConfig implements ConfigEntryPoint {

	private static boolean pendingReload = false;

	@Override
	public void registerConfigLate(ConfigBuilder builder) {
		OptionPageBuilder page = builder.createOptionPage()
			.setName(Component.translatable("options.oldlightmap.page"));
		Identifier oldLightmapId = Identifier.fromNamespaceAndPath("oldlightmap", "old_lightmap");
		// Sodium renders mod icons through the texture manager, so the icon
		// has to be a texture under assets/oldlightmap/textures/.
		Identifier iconId = Identifier.fromNamespaceAndPath("oldlightmap", "textures/icon.png");

		BooleanOptionBuilder oldLightmap = builder
			.createBooleanOption(oldLightmapId)
			.setName(Component.translatable("options.oldlightmap.old_lightmap"))
			.setTooltip(Component.translatable("options.oldlightmap.old_lightmap.tooltip"))
			.setDefaultValue(Boolean.TRUE)
			.setStorageHandler(OldLightmapSodiumConfig::afterSave)
			.setBinding(new OptionBinding<Boolean>() {
				@Override
				public void save(Boolean value) {
					OldLightmapConfig config = OldLightmapConfig.get();
					if (config.oldLightmap != value) {
						config.setOldLightmap(value);
						pendingReload = true;
					}
				}

				@Override
				public Boolean load() {
					return OldLightmapConfig.isOldLightmap();
				}
			});

		BooleanOptionBuilder endFlash = builder
			.createBooleanOption(Identifier.fromNamespaceAndPath("oldlightmap", "end_flash"))
			.setName(Component.translatable("options.oldlightmap.end_flash"))
			.setTooltip(Component.translatable("options.oldlightmap.end_flash.tooltip"))
			.setDefaultValue(Boolean.FALSE)
			.setEnabledProvider(state -> !state.readBooleanOption(oldLightmapId), oldLightmapId)
			.setControlHiddenWhenDisabled(false)
			.setStorageHandler(OldLightmapSodiumConfig::afterSave)
			.setBinding(new OptionBinding<Boolean>() {
				@Override
				public void save(Boolean value) {
					OldLightmapConfig config = OldLightmapConfig.get();
					if (config.endFlash != value) {
						config.setEndFlash(value);
						pendingReload = true;
					}
				}

				@Override
				public Boolean load() {
					return OldLightmapConfig.isEndFlash();
				}
			});

		page.addOptionGroup(builder.createOptionGroup()
			.addOption(oldLightmap)
			.addOption(endFlash));

		builder.registerOwnModOptions()
			.setNonTintedIcon(iconId)
			.addPage(page);
	}

	/**
	 * Sodium calls this after option values have been saved to their bindings:
	 * flush the config to disk and, if a toggle actually changed, reload
	 * resources so the shader swap takes effect.
	 */
	private static void afterSave() {
		OldLightmapConfig.get().save();
		if (pendingReload) {
			pendingReload = false;
			Minecraft.getInstance().reloadResourcePacks();
		}
	}
}
