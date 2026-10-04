package kitejs.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import kitejs.OfflineSkins;
import kitejs.profile.PlayerProfile;
import kitejs.render.SkinRenderCache;

@Mixin(PlayerSkinRenderCache.class)
public abstract class PlayerSkinRenderCacheMixin {
	@Inject(method = "getOrDefault", at = @At("RETURN"), cancellable = true, require = 0)
	private void offlineskins$getOrDefault(ResolvableProfile profile, CallbackInfoReturnable<PlayerSkinRenderCache.RenderInfo> callback) {
		if (!OfflineSkins.isSkullOverrideEnabled()) {
			return;
		}

		PlayerSkin skin = SkinRenderCache.getSkin(PlayerProfile.of(profile.partialProfile()));

		if (skin == null) {
			return;
		}

		callback.setReturnValue(Minecraft.getInstance().playerSkinRenderCache().new RenderInfo(profile.partialProfile(), skin, profile.skinPatch()));
	}
}
