package oldlightmap.sodium;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import oldlightmap.OldLightmap;
import oldlightmap.OldLightmapConfig;

/**
 * Builds a Sodium 0.7 options page without linking Old Lightmap to Sodium or
 * Reese's Sodium Options at class-load time. Sodium 0.7 has no public config
 * API, so the page is added through Sodium's native options screen by an
 * optional mixin and the Sodium option objects are created reflectively.
 */
public final class OldLightmapSodiumOptions {
	private static final String OPTION_PAGE = "net.caffeinemc.mods.sodium.client.gui.options.OptionPage";
	private static final String OPTION_GROUP = "net.caffeinemc.mods.sodium.client.gui.options.OptionGroup";
	private static final String OPTION = "net.caffeinemc.mods.sodium.client.gui.options.Option";
	private static final String OPTION_IMPL = "net.caffeinemc.mods.sodium.client.gui.options.OptionImpl";
	private static final String OPTION_BINDING = "net.caffeinemc.mods.sodium.client.gui.options.binding.OptionBinding";
	private static final String OPTION_STORAGE = "net.caffeinemc.mods.sodium.client.gui.options.storage.OptionStorage";
	private static final String TICK_BOX_CONTROL = "net.caffeinemc.mods.sodium.client.gui.options.control.TickBoxControl";

	private OldLightmapSodiumOptions() {
	}

	@SuppressWarnings("unchecked")
	public static void addPage(List<?> pages) {
		if (hasOfficialConfigApi()) {
			return;
		}
		try {
			((List<Object>) (List<?>) pages).add(createPage());
		} catch (ReflectiveOperationException | RuntimeException e) {
			OldLightmap.LOGGER.error("Failed to add the Old Lightmap page to Sodium's options", e);
		}
	}

	private static boolean hasOfficialConfigApi() {
		try {
			Class.forName("net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint", false,
				OldLightmapSodiumOptions.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	private static Object createPage() throws ReflectiveOperationException {
		ClassLoader loader = OldLightmapSodiumOptions.class.getClassLoader();
		Class<?> optionStorage = Class.forName(OPTION_STORAGE, true, loader);
		Class<?> optionBinding = Class.forName(OPTION_BINDING, true, loader);
		Object storage = createStorage(loader, optionStorage);

		Object oldLightmap = createBooleanOption(loader, optionStorage, optionBinding, storage,
			"options.oldlightmap.old_lightmap", "options.oldlightmap.old_lightmap.tooltip",
			config -> config.oldLightmap,
			(config, value) -> config.setOldLightmap(value));
		Object endFlash = createBooleanOption(loader, optionStorage, optionBinding, storage,
			"options.oldlightmap.end_flash", "options.oldlightmap.end_flash.tooltip",
			config -> OldLightmapConfig.isEndFlash(),
			(config, value) -> config.setEndFlash(value));

		Class<?> optionGroup = Class.forName(OPTION_GROUP, true, loader);
		Object groupBuilder = optionGroup.getMethod("createBuilder").invoke(null);
		groupBuilder.getClass().getMethod("add", Class.forName(OPTION, true, loader)).invoke(groupBuilder, oldLightmap);
		groupBuilder.getClass().getMethod("add", Class.forName(OPTION, true, loader)).invoke(groupBuilder, endFlash);
		Object group = groupBuilder.getClass().getMethod("build").invoke(groupBuilder);

		Class<?> immutableList = Class.forName("com.google.common.collect.ImmutableList", true, loader);
		Object groups = immutableList.getMethod("of", Object.class).invoke(null, group);
		Constructor<?> pageConstructor = Class.forName(OPTION_PAGE, true, loader)
			.getConstructor(Component.class, immutableList);
		return pageConstructor.newInstance(Component.translatable("options.oldlightmap.page"), groups);
	}

	private static Object createBooleanOption(
		ClassLoader loader,
		Class<?> optionStorage,
		Class<?> optionBinding,
		Object storage,
		String nameKey,
		String tooltipKey,
		Function<OldLightmapConfig, Boolean> getter,
		java.util.function.BiConsumer<OldLightmapConfig, Boolean> setter
	) throws ReflectiveOperationException {
		Class<?> optionImpl = Class.forName(OPTION_IMPL, true, loader);
		Object builder = optionImpl.getMethod("createBuilder", Class.class, optionStorage)
			.invoke(null, Boolean.class, storage);
		builder.getClass().getMethod("setName", Component.class)
			.invoke(builder, Component.translatable(nameKey));
		builder.getClass().getMethod("setTooltip", Component.class)
			.invoke(builder, Component.translatable(tooltipKey));

		InvocationHandler bindingHandler = (proxy, method, args) -> switch (method.getName()) {
			case "hashCode" -> System.identityHashCode(proxy);
			case "equals" -> proxy == args[0];
			case "toString" -> "OldLightmapOptionBinding";
			case "getValue" -> getter.apply((OldLightmapConfig) args[0]);
			case "setValue" -> {
				setter.accept((OldLightmapConfig) args[0], (Boolean) args[1]);
				yield null;
			}
			default -> throw new UnsupportedOperationException(method.getName());
		};
		Object binding = Proxy.newProxyInstance(loader, new Class<?>[]{optionBinding}, bindingHandler);
		builder.getClass().getMethod("setBinding", optionBinding).invoke(builder, binding);

		Class<?> option = Class.forName(OPTION, true, loader);
		Constructor<?> tickBoxConstructor = Class.forName(TICK_BOX_CONTROL, true, loader)
			.getConstructor(option);
		Function<Object, Object> controlFactory = optionInstance -> {
			try {
				return tickBoxConstructor.newInstance(optionInstance);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Failed to create Sodium tick-box control", e);
			}
		};
		builder.getClass().getMethod("setControl", Function.class).invoke(builder, controlFactory);
		return builder.getClass().getMethod("build").invoke(builder);
	}

	private static Object createStorage(ClassLoader loader, Class<?> optionStorage) {
		OldLightmapConfig config = OldLightmapConfig.get();
		boolean[] saved = {config.oldLightmap, config.endFlash};
		InvocationHandler storageHandler = (proxy, method, args) -> switch (method.getName()) {
			case "hashCode" -> System.identityHashCode(proxy);
			case "equals" -> proxy == args[0];
			case "toString" -> "OldLightmapOptionStorage";
			case "getData" -> config;
			case "save" -> {
				boolean changed = saved[0] != config.oldLightmap || saved[1] != config.endFlash;
				config.save();
				if (changed) {
					Minecraft.getInstance().reloadResourcePacks();
				}
				saved[0] = config.oldLightmap;
				saved[1] = config.endFlash;
				yield null;
			}
			default -> throw new UnsupportedOperationException(method.getName());
		};
		return Proxy.newProxyInstance(loader, new Class<?>[]{optionStorage}, storageHandler);
	}
}
