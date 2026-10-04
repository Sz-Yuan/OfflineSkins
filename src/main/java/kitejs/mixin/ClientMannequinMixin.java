package kitejs.mixin;

import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import kitejs.OfflineSkins;
import kitejs.profile.PlayerProfile;
import kitejs.render.SkinRenderCache;

@Mixin(ClientMannequin.class)
public abstract class ClientMannequinMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
	private void offlineskins$getSkin(CallbackInfoReturnable<PlayerSkin> callback) {
		if (OfflineSkins.isSkullOverrideDisabled()) {
			return;
		}

		ResolvableProfile profile = ((ClientMannequin) (Object) this).getProfile();
		PlayerSkin skin = SkinRenderCache.getSkin(PlayerProfile.of(profile.partialProfile()));

		if (skin != null) {
			callback.setReturnValue(skin.with(profile.skinPatch()));
		}
	}
}
