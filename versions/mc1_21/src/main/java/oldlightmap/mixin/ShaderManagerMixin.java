package oldlightmap.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.ResourceLocation;
import oldlightmap.OldLightmapConfig;
import oldlightmap.OldLightmapShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps the lightmap fragment shader source at compile time.
 *
 * ShaderManager pre-reads all shader sources into its CompilationCache during
 * a resource reload; pipelines (re)compile lazily from that cache. The lightmap
 * pipeline compiles "minecraft:core/lightmap", so when the old lightmap is
 * enabled we substitute the vanilla source with the configured variant from
 * this mod. Leaving the return value untouched keeps the vanilla shader.
 */
@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$CompilationCache")
public class ShaderManagerMixin {

	@Inject(
		method = "getShaderSource(Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/shaders/ShaderType;)Ljava/lang/String;",
		at = @At("RETURN"),
		cancellable = true
	)
	private static void oldlightmap$swapLightmapSource(ResourceLocation id, ShaderType type, CallbackInfoReturnable<String> cir) {
		if (type == ShaderType.FRAGMENT
			&& id.getNamespace().equals("minecraft")
			&& id.getPath().equals("core/lightmap")
			&& OldLightmapConfig.isOldLightmap()) {
			String source = OldLightmapShaders.getSource(OldLightmapConfig.isEndFlash());
			if (source != null) {
				cir.setReturnValue(source);
			}
		}
	}
}
