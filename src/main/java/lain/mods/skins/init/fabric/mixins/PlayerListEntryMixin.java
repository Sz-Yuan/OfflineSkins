package lain.mods.skins.init.fabric.mixins;

import com.mojang.authlib.GameProfile;
import lain.mods.skins.impl.fabric.SkinUtils;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 有模组皮肤数据时覆盖 PlayerInfo#getSkin；null 则保持原版
// 由 offlineskins-mixins.json → fabric.mod.json 加载，IDE「未使用」为误报
@SuppressWarnings("unused")
@Mixin(PlayerInfo.class)
public abstract class PlayerListEntryMixin {

    @Final
    @Shadow
    private GameProfile profile;

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void getSkin(CallbackInfoReturnable<PlayerSkin> info) {
        PlayerSkin textures = SkinUtils.textures(profile);
        if (textures != null)
            info.setReturnValue(textures);
    }

}
