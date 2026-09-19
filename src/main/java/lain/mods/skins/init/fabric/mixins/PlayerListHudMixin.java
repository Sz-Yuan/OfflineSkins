package lain.mods.skins.init.fabric.mixins;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// 离线模式下也强制 Tab 列表绘制玩家头像（原版依赖 onlineMode()）
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudMixin {

    @ModifyVariable(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(value = "STORE", opcode = Opcodes.ISTORE, ordinal = 0),
            require = 0
    )
    private boolean onExtractRenderStateForceShowHeads(boolean showHead) {
        return true;
    }

}
