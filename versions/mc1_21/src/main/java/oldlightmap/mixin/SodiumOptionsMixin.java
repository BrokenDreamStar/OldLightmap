package oldlightmap.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import oldlightmap.sodium.OldLightmapSodiumOptions;

/**
 * Sodium 0.7 has no public config API. Add the page to Sodium's native options
 * screen for that release line. This is the single legacy menu integration path;
 * do not also inject the page into Reese's replacement screen.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI")
public abstract class SodiumOptionsMixin {
	@Shadow
	@Final
	private List<?> pages;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void oldlightmap$addOptionsPage(CallbackInfo ci) {
		OldLightmapSodiumOptions.addPage(this.pages);
	}
}
