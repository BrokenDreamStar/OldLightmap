package oldlightmap.sodium;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import oldlightmap.OldLightmapConfig;

/**
 * Registers the page through Sodium's official config API when the installed
 * Sodium release provides it. Older Sodium releases use SodiumOptionsMixin.
 */
public final class OldLightmapSodiumConfig implements ConfigEntryPoint {
	private static boolean pendingReload;

	@Override
	public void registerConfigLate(ConfigBuilder builder) {
		OptionPageBuilder page = builder.createOptionPage()
			.setName(Component.translatable("options.oldlightmap.page"));
		ResourceLocation oldLightmapId = ResourceLocation.fromNamespaceAndPath("oldlightmap", "old_lightmap");
		// Sodium renders mod icons through the texture manager, so the icon
		// has to be a texture under assets/oldlightmap/textures/.
		ResourceLocation iconId = ResourceLocation.fromNamespaceAndPath("oldlightmap", "textures/icon.png");

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
			.createBooleanOption(ResourceLocation.fromNamespaceAndPath("oldlightmap", "end_flash"))
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

	private static void afterSave() {
		OldLightmapConfig.get().save();
		if (pendingReload) {
			pendingReload = false;
			Minecraft.getInstance().reloadResourcePacks();
		}
	}
}
