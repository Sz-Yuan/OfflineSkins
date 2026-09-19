package lain.mods.skins.init.fabric.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// 离线模式下也强制 Tab 列表绘制玩家头像（原版依赖 onlineMode()）
@SuppressWarnings("unused")
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudMixin {

    @ModifyExpressionValue(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;onlineMode()Z"
            ),
            require = 0
    )
    private boolean offlineskins$forceShowHeads(boolean onlineMode) {
        return true;
    }

}
