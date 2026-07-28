package xyz.w4ve.beaconator.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.w4ve.beaconator.client.EasyPlace;
import xyz.w4ve.beaconator.client.NodeEditor;

/**
 * Routes clicks to the node editor while edit mode is on.
 *
 * <p>A click that does not land on a node falls through untouched, so nothing is stolen from
 * normal play outside edit mode.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void beaconator$startAttack(CallbackInfoReturnable<Boolean> cir) {
		if (NodeEditor.onLeftClick()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void beaconator$startUseItem(CallbackInfo ci) {
		if (NodeEditor.onRightClick()) {
			ci.cancel();
			return;
		}

		if (EasyPlace.shouldBlockPlacement((Minecraft) (Object) this)) {
			ci.cancel();
		}
	}
}
