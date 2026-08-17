package oldlightmap.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import oldlightmap.OldLightmapConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Two lightmap input adjustments:
 * 1. 26.1.x uploads a block light factor of blockLightFlicker + 1.4 while
 *    1.21.8 used + 1.5; restore the 1.21.8 value when the old lightmap is on.
 * 2. When the old lightmap is on OR the end flash toggle is off, suppress
 *    the End flash at its source (the skyFactor boost) so vanilla lighting
 *    behaves like 1.21.8 in the End as well.
 */
@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {

	@ModifyConstant(
		method = "extract(Lnet/minecraft/client/renderer/state/LightmapRenderState;F)V",
		constant = @Constant(floatValue = 1.4F)
	)
	private static float oldlightmap$blockLightFactor(float constant) {
		return OldLightmapConfig.isOldLightmap() ? 1.5F : constant;
	}

	@Redirect(
		method = "extract(Lnet/minecraft/client/renderer/state/LightmapRenderState;F)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;endFlashState()Lnet/minecraft/client/renderer/EndFlashState;"
		)
	)
	private static EndFlashState oldlightmap$suppressEndFlash(ClientLevel level) {
		if (OldLightmapConfig.isOldLightmap() || !OldLightmapConfig.isEndFlash()) {
			return null;
		}
		return level.endFlashState();
	}
}
