package lain.mods.skins.providers;

import com.mojang.authlib.GameProfile;
import lain.lib.SharedPool;
import lain.mods.skins.api.interfaces.IPlayerProfile;
import lain.mods.skins.api.interfaces.ISkin;
import lain.mods.skins.api.interfaces.ISkinProvider;
import lain.mods.skins.impl.MojangService;
import lain.mods.skins.impl.Shared;
import lain.mods.skins.impl.SkinData;
import lain.mods.skins.impl.fabric.ImageUtils;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

// 仅 Mojang 官方源
// 离线服上的正版名：先按用户名解析正版 UUID，再查 sessionserver，不依赖 GameProfile.properties
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
            String name = profile.getPlayerName();
            if (Shared.isBlank(name))
                return;
            UUID id = profile.getPlayerID();
            // 离线 UUID：按用户名解析正版（缓存）；非正版则放弃
            if (id == null || Shared.isOfflinePlayer(id, name)) {
                GameProfile resolved = MojangService.resolveBlocking(name);
                if (resolved == Shared.DUMMY || resolved.id() == null)
                    return;
                if (Shared.isOfflinePlayer(resolved.id(), resolved.name() == null ? name : resolved.name()))
                    return;
                id = resolved.id();
            }
            Optional<MojangService.TextureSource> source = MojangService.fetchTextureSource(id, kind == Kind.SKIN);
            if (source.isEmpty())
                return;
            String url = source.get().url();
            String model = source.get().model();
            Shared.downloadSkin(url, Runnable::run).thenAccept(opt -> {
                byte[] data = opt.orElse(null);
                if (data == null || !ImageUtils.validateData(data))
                    return;
                if (kind == Kind.CAPE)
                    skin.put(data, "cape");
                else
                    skin.put(data, "slim".equals(model) ? "slim" : "default");
            });
        });
        return skin;
    }

    public MojangProvider withFilter(Function<ByteBuffer, ByteBuffer> filter) {
        this.filter = filter;
        return this;
    }

}
