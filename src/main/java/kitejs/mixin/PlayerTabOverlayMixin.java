package kitejs.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.gui.components.PlayerTabOverlay;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;onlineMode()Z"), require = 0)
	private boolean offlineskins$showHead(boolean original) {
		return true;
	}
}
