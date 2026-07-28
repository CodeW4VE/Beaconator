package xyz.w4ve.beaconator.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.w4ve.beaconator.client.Keys;

/**
 * Lets edit mode take over the scroll wheel.
 *
 * <p>Vanilla has no event for this, so the wheel is intercepted before it reaches the hotbar.
 * Outside edit mode nothing is touched.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
	private void beaconator$onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (yOffset != 0.0 && Keys.handleScroll(yOffset)) {
			ci.cancel();
		}
	}
}
