package oldlightmap.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.client.renderer.LightTexture;
import oldlightmap.OldLightmapConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.21.9-1.21.11 still computes the lightmap in LightTexture. The old
 * lightmap shader already receives the 1.21.8-compatible +1.5 block factor,
 * so the only input that needs changing is the optional End flash state.
 */
@Mixin(LightTexture.class)
public class LightTextureMixin {

	@Redirect(
		method = "updateLightTexture(F)V",
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
