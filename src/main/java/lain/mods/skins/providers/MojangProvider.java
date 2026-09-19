package lain.mods.skins.providers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import lain.lib.SharedPool;
import lain.mods.skins.api.interfaces.IPlayerProfile;
import lain.mods.skins.api.interfaces.ISkin;
import lain.mods.skins.api.interfaces.ISkinProvider;
import lain.mods.skins.impl.Shared;
import lain.mods.skins.impl.SkinData;
import lain.mods.skins.impl.fabric.ImageUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Function;

// 仅 Mojang 官方源：解析 profile 的 textures 属性后下载
// 非正版不请求，数据保持为空 → 走原版
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
            GameProfile original = (GameProfile) profile.getOriginal();
            if (original == null || original.properties() == null)
                return;
            // 属性名为 textures 的 value 是 base64 JSON
            String encoded = null;
            for (Property property : original.properties().values()) {
                if ("textures".equals(property.name())) {
                    encoded = property.value();
                    break;
                }
            }
            if (encoded == null || encoded.isEmpty())
                return;
            String url;
            String model = "default";
            try {
                String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                if (!root.has("textures"))
                    return;
                JsonObject textures = root.getAsJsonObject("textures");
                String key = kind == Kind.SKIN ? "SKIN" : "CAPE";
                if (!textures.has(key))
                    return;
                JsonObject node = textures.getAsJsonObject(key);
                if (!node.has("url"))
                    return;
                url = node.get("url").getAsString();
                if (kind == Kind.SKIN && node.has("metadata") && node.getAsJsonObject("metadata").has("model"))
                    model = node.getAsJsonObject("metadata").get("model").getAsString();
            } catch (RuntimeException e) {
                return;
            }
            String skinModel = model;
            Shared.downloadSkin(url, Runnable::run).thenAccept(opt -> {
                byte[] data = opt.orElse(null);
                if (data == null || !ImageUtils.validateData(data))
                    return;
                if (kind == Kind.CAPE)
                    skin.put(data, "cape");
                else
                    skin.put(data, "slim".equals(skinModel) ? "slim" : "default");
            });
        });
        return skin;
    }

    public MojangProvider withFilter(Function<ByteBuffer, ByteBuffer> filter) {
        this.filter = filter;
        return this;
    }

}
