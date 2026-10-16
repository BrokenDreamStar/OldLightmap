package oldlightmap.mixin;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.resources.Identifier;
import oldlightmap.OldLightmapConfig;
import oldlightmap.OldLightmapShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps the lightmap fragment shader source at compile time.
 *
 * ShaderManager pre-reads all shader sources into its Configs during a
 * resource reload, and 26.3's renderpearl frontend pulls each stage source
 * from that ShaderSource when building the pipeline's SPIR-V module
 * (PipelineBuilder -> ShaderSource.getShader). The lightmap pipeline compiles
 * "minecraft:core/lightmap", so when the old lightmap is enabled we
 * substitute the vanilla source with the configured variant from this mod.
 * Leaving the return value untouched keeps the vanilla shader.
 */
@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$Configs")
public class ShaderManagerMixin {

	@Inject(
		method = "getShader(Lnet/minecraft/resources/Identifier;Lcom/mojang/renderpearl/api/pipeline/ShaderType;)Ljava/lang/String;",
		at = @At("RETURN"),
		cancellable = true
	)
	private static void oldlightmap$swapLightmapSource(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
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
