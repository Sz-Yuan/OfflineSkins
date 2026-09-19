package lain.mods.skins.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import lain.mods.skins.impl.fabric.MinecraftUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

// 正版解析 + session 纹理源（皮肤显示只用这两条）
public class MojangService {

    // Mojang 返回无横线 UUID 十六进制
    private static UUID parseUuid(@NonNull String id) {
        String s = id.replace("-", "");
        if (s.length() != 32)
            return UUID.fromString(id);
        return new UUID(Long.parseUnsignedLong(s.substring(0, 16), 16), Long.parseUnsignedLong(s.substring(16, 32), 16));
    }

    private static String undashed(@NonNull UUID id) {
        return id.toString().replace("-", "");
    }

    private static @NonNull String readBody(@NonNull HttpURLConnection conn) throws IOException {
        StringBuilder buf = new StringBuilder();
        try (InputStream in = conn.getInputStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!buf.isEmpty())
                    buf.append(System.lineSeparator());
                buf.append(line);
            }
        }
        return buf.toString();
    }

    private static @NonNull HttpURLConnection open(@NonNull String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection(MinecraftUtils.getProxy());
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(10000);
        conn.setUseCaches(false);
        conn.connect();
        return conn;
    }

    // 纹理下载源：url + 模型（skin 才有 model）
    public record TextureSource(@NonNull String url, @NonNull String model) {
    }

    // 按用户名查正版 UUID；JSON 手写解析（authlib record，禁止 Gson 反射）
    private static @Nullable GameProfile lookupByName(@NonNull String name) throws IOException {
        String url = String.format("https://api.mojang.com/users/profiles/minecraft/%s", name);
        HttpURLConnection conn = open(url);
        int code = conn.getResponseCode();
        if (code == 204 || code == 404)
            return Shared.DUMMY;
        if (code / 100 != 2)
            return null;
        String body = readBody(conn);
        JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
        if (!obj.has("id") || !obj.has("name"))
            return Shared.DUMMY;
        UUID id = parseUuid(obj.get("id").getAsString());
        String resolvedName = obj.get("name").getAsString();
        if (Shared.isOfflinePlayer(id, resolvedName))
            return Shared.DUMMY;
        return new GameProfile(id, resolvedName, PropertyMap.EMPTY);
    }

    // 按用户名解析正版 UUID（缓存）
    private static final @NonNull LoadingCache<String, Optional<GameProfile>> resolvedProfiles = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofHours(3))
            .refreshAfterWrite(Duration.ofMinutes(30))
            .build(new CacheLoader<>() {
                @Override
                public @NonNull Optional<GameProfile> load(@NonNull String key) {
                    if (Shared.isBlank(key))
                        return Optional.of(Shared.DUMMY);
                    return Optional.ofNullable(Shared.call(() -> lookupByName(key), null, null));
                }

                @Override
                public @NonNull ListenableFuture<Optional<GameProfile>> reload(@NonNull String key, @NonNull Optional<GameProfile> oldValue) {
                    if (oldValue.isPresent()) {
                        if (oldValue.get() == Shared.DUMMY)
                            return Futures.immediateFuture(Optional.empty());
                        return Futures.immediateFuture(oldValue);
                    }
                    return Shared.submitTask(() -> load(key));
                }
            });

    // 阻塞解析用户名 → 正版档案（线程池内使用；失败为 DUMMY）
    public static @NonNull GameProfile resolveBlocking(@NonNull String username) {
        return resolvedProfiles.getUnchecked(username).orElse(Shared.DUMMY);
    }

    // 阻塞按正版 UUID 拉取 textures 下载源；失败返回 empty
    public static @NonNull Optional<TextureSource> fetchTextureSource(@NonNull UUID id, boolean skin) {
        return Shared.call(() -> {
            String url = String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", undashed(id));
            HttpURLConnection conn = open(url);
            int code = conn.getResponseCode();
            if (code / 100 != 2)
                return Optional.empty();
            String body = readBody(conn);
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!root.has("properties") || !root.get("properties").isJsonArray())
                return Optional.empty();
            JsonArray arr = root.getAsJsonArray("properties");
            for (JsonElement el : arr) {
                if (!el.isJsonObject())
                    continue;
                JsonObject p = el.getAsJsonObject();
                if (!p.has("name") || !p.has("value") || !"textures".equals(p.get("name").getAsString()))
                    continue;
                String encoded = p.get("value").getAsString();
                String json = new String(java.util.Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
                JsonObject texRoot = JsonParser.parseString(json).getAsJsonObject();
                if (!texRoot.has("textures"))
                    return Optional.empty();
                JsonObject textures = texRoot.getAsJsonObject("textures");
                String key = skin ? "SKIN" : "CAPE";
                if (!textures.has(key))
                    return Optional.empty();
                JsonObject node = textures.getAsJsonObject(key);
                if (!node.has("url"))
                    return Optional.empty();
                String texUrl = node.get("url").getAsString();
                String model = "default";
                if (skin && node.has("metadata") && node.getAsJsonObject("metadata").has("model"))
                    model = node.getAsJsonObject("metadata").get("model").getAsString();
                return Optional.of(new TextureSource(texUrl, model));
            }
            return Optional.empty();
        }, Optional.empty(), null);
    }

}
