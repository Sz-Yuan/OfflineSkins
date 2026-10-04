package kitejs.mixin;

import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import kitejs.OfflineSkins;
import kitejs.profile.PlayerProfile;
import kitejs.render.SkinRenderCache;

@Mixin(SkullBlockRenderer.class)
public abstract class SkullBlockRendererMixin {
	@Inject(method = "resolveSkullRenderType", at = @At("RETURN"), cancellable = true, require = 0)
	private void offlineskins$resolveSkullRenderType(SkullBlock.Type type, SkullBlockEntity entity, CallbackInfoReturnable<RenderType> callback) {
		if (!OfflineSkins.isSkullOverrideEnabled() || type != SkullBlock.Types.PLAYER) {
			return;
		}

		ResolvableProfile owner = entity.getOwnerProfile();

		if (owner == null) {
			return;
		}

		PlayerSkin skin = SkinRenderCache.getSkin(PlayerProfile.of(owner.partialProfile()));

		if (skin == null) {
			return;
		}

		callback.setReturnValue(SkullBlockRenderer.getPlayerSkinRenderType(skin.body().texturePath()));
	}
}
