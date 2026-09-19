package lain.mods.skins.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.collect.HashMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// 正版：用户名 → UUID，以及 sessionserver 属性补全
// 不依赖 authlib fetchProfile（部分环境下方法签名/类路径不一致）
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

    // 按 UUID 从 sessionserver 拉取 profile（含 textures 属性）
    private static @Nullable GameProfile fetchSessionProfile(@NonNull UUID id) throws IOException {
        String url = String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", undashed(id));
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
        UUID profileId = parseUuid(obj.get("id").getAsString());
        String name = obj.get("name").getAsString();
        PropertyMap properties = new PropertyMap(HashMultimap.create());
        if (obj.has("properties") && obj.get("properties").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("properties");
            for (JsonElement el : arr) {
                if (!el.isJsonObject())
                    continue;
                JsonObject p = el.getAsJsonObject();
                if (!p.has("name") || !p.has("value"))
                    continue;
                String propName = p.get("name").getAsString();
                String value = p.get("value").getAsString();
                String signature = p.has("signature") ? p.get("signature").getAsString() : null;
                if (signature != null)
                    properties.put(propName, new Property(propName, value, signature));
                else
                    properties.put(propName, new Property(propName, value));
            }
        }
        return new GameProfile(profileId, name, properties);
    }

    // 按用户名查正版 UUID
    private static @Nullable GameProfile lookupByName(@NonNull String name) throws IOException {
        String url = String.format("https://api.mojang.com/users/profiles/minecraft/%s", name);
        HttpURLConnection conn = open(url);
        int code = conn.getResponseCode();
        if (code == 204 || code == 404)
            return Shared.DUMMY;
        if (code / 100 != 2)
            return null;
        String body = readBody(conn);
        // authlib 的 GameProfile 是 record，禁止 Gson 反射构造（JSON 常无 properties）
        JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
        if (!obj.has("id") || !obj.has("name"))
            return Shared.DUMMY;
        UUID id = parseUuid(obj.get("id").getAsString());
        String name2 = obj.get("name").getAsString();
        if (Shared.isOfflinePlayer(id, name2))
            return Shared.DUMMY;
        return new GameProfile(id, name2, PropertyMap.EMPTY);
    }

    // 按 UUID 补全 properties（textures 等）
    private static final @NonNull LoadingCache<GameProfile, Optional<GameProfile>> filledProfiles = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofHours(3))
            .refreshAfterWrite(Duration.ofMinutes(30))
            .build(new CacheLoader<>() {
                @Override
                public @NonNull Optional<GameProfile> load(@NonNull GameProfile key) {
                    if (key.id() == null || key.properties() == null || key == Shared.DUMMY)
                        return Optional.empty();
                    if (!key.properties().isEmpty())
                        return Optional.of(key);
                    GameProfile filled = Shared.call(() -> fetchSessionProfile(key.id()), key, null);
                    if (filled == null || filled == key)
                        return Optional.empty();
                    if (filled.properties() == null || filled.properties().isEmpty())
                        return Optional.empty();
                    return Optional.of(filled);
                }

                @Override
                public @NonNull ListenableFuture<Optional<GameProfile>> reload(@NonNull GameProfile key, @NonNull Optional<GameProfile> oldValue) {
                    if (oldValue.isPresent())
                        return Futures.immediateFuture(oldValue);
                    return Shared.submitTask(() -> load(key));
                }
            });

    // 按用户名解析正版 UUID
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

    public static ListenableFuture<GameProfile> fillProfile(@Nullable GameProfile profile) {
        if (profile == null)
            return Futures.immediateFailedFuture(new NullPointerException("profile must not be null"));
        Optional<GameProfile> cached = filledProfiles.getIfPresent(profile);
        if (Objects.nonNull(cached))
            return Futures.immediateFuture(cached.orElse(profile));
        return Shared.submitTask(() -> filledProfiles.getUnchecked(profile).orElse(profile));
    }

    public static ListenableFuture<GameProfile> getProfile(@Nullable String username) {
        if (username == null)
            return Futures.immediateFailedFuture(new NullPointerException("username must not be null"));
        Optional<GameProfile> cached = resolvedProfiles.getIfPresent(username);
        if (Objects.nonNull(cached))
            return Futures.immediateFuture(cached.orElse(Shared.DUMMY));
        return Shared.submitTask(() -> resolvedProfiles.getUnchecked(username).orElse(Shared.DUMMY));
    }

}
