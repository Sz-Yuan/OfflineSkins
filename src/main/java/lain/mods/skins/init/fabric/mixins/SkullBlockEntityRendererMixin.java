package lain.mods.skins.init.fabric.mixins;

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

// 玩家头颅：有模组皮肤且未禁用时覆盖渲染层
@SuppressWarnings("unused")
@Mixin(SkullBlockRenderer.class)
public abstract class SkullBlockEntityRendererMixin {

    @Inject(
            method = "resolveSkullRenderType(Lnet/minecraft/world/level/block/SkullBlock$Type;Lnet/minecraft/world/level/block/entity/SkullBlockEntity;)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void resolveSkullRenderType(SkullBlock.Type type, SkullBlockEntity entity, CallbackInfoReturnable<RenderType> info) {
        if (!FabricOfflineSkins.PLAYERHEADS || type != SkullBlock.Types.PLAYER || entity == null)
            return;
        ResolvableProfile ownerProfile = entity.getOwnerProfile();
        if (ownerProfile == null)
            return;
        // partialProfile() 由构造器保证非 null
        Identifier loc = FabricOfflineSkins.getLocationSkin(ownerProfile.partialProfile());
        if (loc != null)
            info.setReturnValue(SkullBlockRenderer.getPlayerSkinRenderType(loc));
    }

}
