package oldlightmap.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import oldlightmap.sodium.OldLightmapSodiumOptions;

/** Adds the same page to Reese's replacement screen when it is installed. */
@Mixin(targets = "me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen")
public abstract class ReesesSodiumOptionsMixin {
	@Shadow
	@Final
	private List<?> pages;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void oldlightmap$addOptionsPage(CallbackInfo ci) {
		OldLightmapSodiumOptions.addPage(this.pages);
	}
}
