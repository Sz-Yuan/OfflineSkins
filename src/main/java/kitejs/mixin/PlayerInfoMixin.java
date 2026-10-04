package kitejs.mixin;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import kitejs.profile.PlayerProfile;
import kitejs.render.SkinRenderCache;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
	@Shadow
	public abstract GameProfile getProfile();

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void offlineskins$getSkin(CallbackInfoReturnable<PlayerSkin> callback) {
		PlayerSkin skin = SkinRenderCache.getSkin(PlayerProfile.of(this.getProfile()));

		if (skin != null) {
			callback.setReturnValue(skin);
		}
	}
}
