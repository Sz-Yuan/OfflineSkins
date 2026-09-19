package lain.mods.skins.init.fabric.mixins;

import com.mojang.authlib.GameProfile;
import lain.mods.skins.init.fabric.FabricOfflineSkins;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkullBlockRenderer.class)
public abstract class SkullBlockEntityRendererMixin {

    @Inject(
            method = "resolveSkullRenderType(Lnet/minecraft/world/level/block/SkullBlock$Type;Lnet/minecraft/world/level/block/entity/SkullBlockEntity;)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void resolveSkullRenderType(SkullBlock.Type type, SkullBlockEntity entity, CallbackInfoReturnable<RenderType> info) {
        if (FabricOfflineSkins.PLAYERHEADS && type == SkullBlock.Types.PLAYER && entity != null) {
            ResolvableProfile ownerProfile = entity.getOwnerProfile();
            if (ownerProfile != null) {
                GameProfile gameProfile = ownerProfile.partialProfile();
                if (gameProfile != null) {
                    Identifier loc = FabricOfflineSkins.getLocationSkin(gameProfile, null);
                    if (loc != null)
                        info.setReturnValue(SkullBlockRenderer.getPlayerSkinRenderType(loc));
                }
            }
        }
    }

}
