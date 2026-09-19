package lain.mods.skins.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import lain.mods.skins.impl.fabric.MinecraftUtils;

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

// 正版：用户名 → UUID 查询，以及 session 属性补全
public class MojangService {

    // Mojang 返回无横线 UUID 十六进制
    private static UUID parseUuid(String id) {
        String s = id.replace("-", "");
        if (s.length() != 32)
            return UUID.fromString(id);
        return new UUID(Long.parseUnsignedLong(s.substring(0, 16), 16), Long.parseUnsignedLong(s.substring(16, 32), 16));
    }

    // 按 UUID 补全 profile 的 textures 等 properties
    private static final LoadingCache<GameProfile, Optional<GameProfile>> filledProfiles = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofHours(3))
            .refreshAfterWrite(Duration.ofMinutes(30))
            .build(new CacheLoader<>() {
                @Override
                public Optional<GameProfile> load(GameProfile key) {
                    if (key.id() == null || key.properties() == null || key == Shared.DUMMY)
                        return Optional.empty();
                    if (!key.properties().isEmpty())
                        return Optional.of(key);
                    GameProfile filled = Shared.call(() -> MinecraftUtils.getSessionService().fetchProfile(key.id(), false).profile(), key, null);
                    if (filled == key || filled == null || filled.properties() == null || filled.properties().isEmpty())
                        return Optional.empty();
                    return Optional.of(filled);
                }

                @Override
                public ListenableFuture<Optional<GameProfile>> reload(GameProfile key, Optional<GameProfile> oldValue) {
                    if (oldValue.isPresent())
                        return Futures.immediateFuture(oldValue);
                    return Shared.submitTask(() -> load(key));
                }
            });

    // 按用户名解析正版 UUID
    private static final LoadingCache<String, Optional<GameProfile>> resolvedProfiles = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofHours(3))
            .refreshAfterWrite(Duration.ofMinutes(30))
            .build(new CacheLoader<>() {
                @Override
                public Optional<GameProfile> load(String key) {
                    if (Shared.isBlank(key))
                        return Optional.of(Shared.DUMMY);
                    return Optional.ofNullable(Shared.call(() -> makeRequest(String.format("https://api.mojang.com/users/profiles/minecraft/%s", key)), null, null));
                }

                @Override
                public ListenableFuture<Optional<GameProfile>> reload(String key, Optional<GameProfile> oldValue) {
                    if (oldValue.isPresent()) {
                        if (oldValue.get() == Shared.DUMMY)
                            return Futures.immediateFuture(Optional.empty());
                        return Futures.immediateFuture(oldValue);
                    }
                    return Shared.submitTask(() -> load(key));
                }
            });

    private static GameProfile makeRequest(String request) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(request).toURL().openConnection(MinecraftUtils.getProxy());
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(10000);
        conn.setUseCaches(false);
        conn.connect();

        int code = conn.getResponseCode();
        if (code == 204 || code == 404)
            return Shared.DUMMY;
        if (code / 100 != 2)
            return null;
        try (InputStream in = conn.getInputStream()) {
            StringBuilder buf = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (buf.length() > 0)
                        buf.append(System.lineSeparator());
                    buf.append(line);
                }
            }
            // authlib 的 GameProfile 是 record，禁止 Gson 反射构造（JSON 常无 properties）
            JsonObject obj = JsonParser.parseString(buf.toString()).getAsJsonObject();
            if (!obj.has("id") || !obj.has("name"))
                return Shared.DUMMY;
            UUID id = parseUuid(obj.get("id").getAsString());
            String name = obj.get("name").getAsString();
            if (Shared.isOfflinePlayer(id, name))
                return Shared.DUMMY;
            return new GameProfile(id, name, PropertyMap.EMPTY);
        }
    }

    public static ListenableFuture<GameProfile> fillProfile(GameProfile profile) {
        if (profile == null)
            return Futures.immediateFailedFuture(new NullPointerException("profile must not be null"));
        Optional<GameProfile> cached = filledProfiles.getIfPresent(profile);
        if (cached != null)
            return Futures.immediateFuture(cached.orElse(profile));
        return Shared.submitTask(() -> filledProfiles.getUnchecked(profile).orElse(profile));
    }

    public static ListenableFuture<GameProfile> getProfile(String username) {
        if (username == null)
            return Futures.immediateFailedFuture(new NullPointerException("username must not be null"));
        Optional<GameProfile> cached = resolvedProfiles.getIfPresent(username);
        if (cached != null)
            return Futures.immediateFuture(cached.orElse(Shared.DUMMY));
        return Shared.submitTask(() -> resolvedProfiles.getUnchecked(username).orElse(Shared.DUMMY));
    }

}
