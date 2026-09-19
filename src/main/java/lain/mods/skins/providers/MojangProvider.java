package lain.mods.skins.providers;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import lain.lib.SharedPool;
import lain.mods.skins.api.interfaces.IPlayerProfile;
import lain.mods.skins.api.interfaces.ISkin;
import lain.mods.skins.api.interfaces.ISkinProvider;
import lain.mods.skins.impl.Shared;
import lain.mods.skins.impl.SkinData;
import lain.mods.skins.impl.fabric.ImageUtils;
import lain.mods.skins.impl.fabric.MinecraftUtils;

import java.nio.ByteBuffer;
import java.util.function.Function;

// 仅 Mojang 官方源；非正版不请求，数据保持为空 → 走原版
public class MojangProvider implements ISkinProvider {

    public enum Kind { SKIN, CAPE }

    private final Kind kind;
    private Function<ByteBuffer, ByteBuffer> filter;

    public MojangProvider(Kind kind) {
        this.kind = kind;
    }

    @Override
    public ISkin getSkin(IPlayerProfile profile) {
        SkinData skin = new SkinData();
        if (filter != null)
            skin.setSkinFilter(filter);
        SharedPool.execute(() -> {
            // 仅正版；离线名不得请求 Mojang
            if (Shared.isOfflinePlayer(profile.getPlayerID(), profile.getPlayerName()))
                return;
            MinecraftProfileTextures textures = MinecraftUtils.getSessionService()
                    .getTextures((GameProfile) profile.getOriginal());
            if (textures == null)
                return;
            MinecraftProfileTexture texture = kind == Kind.SKIN ? textures.skin() : textures.cape();
            if (texture == null)
                return;
            Shared.downloadSkin(texture.getUrl(), Runnable::run).thenAccept(opt -> {
                byte[] data = opt.orElse(null);
                if (data == null || !ImageUtils.validateData(data))
                    return;
                if (kind == Kind.CAPE)
                    skin.put(data, "cape");
                else
                    skin.put(data, "slim".equals(texture.getMetadata("model")) ? "slim" : "default");
            });
        });
        return skin;
    }

    public MojangProvider withFilter(Function<ByteBuffer, ByteBuffer> filter) {
        this.filter = filter;
        return this;
    }

}
