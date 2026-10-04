package kitejs.skin.provider;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.util.UndashedUuid;

import kitejs.OfflineSkins;
import kitejs.data.SkinData;
import kitejs.profile.OfflineUuids;
import kitejs.profile.PlayerProfile;
import kitejs.skin.SkinKind;
import kitejs.skin.SkinProvider;
import kitejs.util.BackgroundTasks;
import kitejs.util.Downloader;
import kitejs.util.HttpQueries;
import kitejs.util.ImageTools;

public class MojangSkinProvider implements SkinProvider {
	private static final Gson GSON = new Gson();
	private static final GameProfile NOT_PREMIUM = new GameProfile(new UUID(0L, 0L), "");
	private static final String NAME_URL = "https://api.mojang.com/users/profiles/minecraft/";
	private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
	private static final long REFRESH_INTERVAL = 30L * 60L * 1000L;
	private static final Cache<String, Resolution> RESOLUTIONS = CacheBuilder.newBuilder()
		.expireAfterAccess(Duration.ofHours(3L))
		.maximumSize(4096L)
		.build();
	private static final Cache<UUID, ProfileTextures> TEXTURES = CacheBuilder.newBuilder()
		.expireAfterWrite(Duration.ofMinutes(5L))
		.maximumSize(512L)
		.build();

	private final SkinKind kind;

	public MojangSkinProvider(SkinKind kind) {
		this.kind = kind;
	}

	public static void clearCache() {
		RESOLUTIONS.invalidateAll();
		TEXTURES.invalidateAll();
	}

	@Override
	public SkinData getSkin(PlayerProfile profile) {
		SkinData data = new SkinData();

		BackgroundTasks.execute(() -> load(profile, data));

		return data;
	}

	private void load(PlayerProfile profile, SkinData data) {
		try {
			String name = profile.getName();

			if (name == null || name.isBlank()) {
				data.markUnavailable();

				return;
			}

			GameProfile premium = OfflineUuids.isOffline(profile) ? resolve(name) : new GameProfile(profile.getUuid(), name);

			if (premium == null) {
				return;
			}

			if (premium == NOT_PREMIUM) {
				data.markUnavailable();

				return;
			}

			ProfileTextures textures = textures(premium);

			if (textures == null) {
				return;
			}

			Texture texture = kind == SkinKind.CAPE ? textures.cape() : textures.skin();

			if (texture == null) {
				data.markUnavailable();

				return;
			}

			Optional<byte[]> bytes = Downloader.download(texture.url());

			if (bytes.isPresent() && ImageTools.isValidPng(bytes.get())) {
				data.put(bytes.get(), texture.type());
			}
		} catch (Exception e) {
			OfflineSkins.LOGGER.debug("Failed to fetch skin from Mojang", e);
		}
	}

	private static GameProfile resolve(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		Resolution resolution = RESOLUTIONS.asMap().computeIfAbsent(key, ignored -> {
			GameProfile profile = query(name);

			return profile == null ? null : new Resolution(profile);
		});

		if (resolution == null) {
			return null;
		}

		if (resolution.profile != null && resolution.profile != NOT_PREMIUM) {
			return resolution.profile;
		}

		if (System.currentTimeMillis() - resolution.writtenAt > REFRESH_INTERVAL) {
			BackgroundTasks.execute(() -> refresh(name, key));
		}

		return resolution.profile;
	}

	private static void refresh(String name, String key) {
		GameProfile profile = query(name);
		Resolution resolution = RESOLUTIONS.getIfPresent(key);

		if (resolution == null || profile == null) {
			return;
		}

		resolution.profile = profile;
		resolution.writtenAt = System.currentTimeMillis();
	}

	private static GameProfile query(String name) {
		Optional<HttpQueries.QueryResult> result = HttpQueries.query(NAME_URL + name);

		if (result.isEmpty()) {
			return null;
		}

		HttpQueries.QueryResult response = result.get();

		if (response.status() == HttpURLConnection.HTTP_NOT_FOUND || response.status() == HttpURLConnection.HTTP_NO_CONTENT) {
			return NOT_PREMIUM;
		}

		try {
			JsonObject json = GSON.fromJson(response.body(), JsonObject.class);

			if (json == null || !json.has("id") || !json.has("name")) {
				return NOT_PREMIUM;
			}

			String resolvedName = json.get("name").getAsString();
			UUID uuid = UndashedUuid.fromStringLenient(json.get("id").getAsString());

			if (resolvedName.isEmpty() || OfflineUuids.of(resolvedName).equals(uuid)) {
				return NOT_PREMIUM;
			}

			return new GameProfile(uuid, resolvedName);
		} catch (Exception ignored) {
			return NOT_PREMIUM;
		}
	}

	private static ProfileTextures textures(GameProfile premium) {
		return TEXTURES.asMap().computeIfAbsent(premium.id(), MojangSkinProvider::queryTextures);
	}

	private static ProfileTextures queryTextures(UUID id) {
		Optional<HttpQueries.QueryResult> result = HttpQueries.query(PROFILE_URL + UndashedUuid.toString(id));

		if (result.isEmpty()) {
			return null;
		}

		try {
			JsonObject response = GSON.fromJson(result.get().body(), JsonObject.class);

			if (response == null || !response.has("properties")) {
				return null;
			}

			JsonArray properties = response.getAsJsonArray("properties");

			for (JsonElement element : properties) {
				JsonObject property = element.getAsJsonObject();

				if (!"textures".equals(string(property, "name"))) {
					continue;
				}

				JsonObject decoded = decode(string(property, "value"));

				if (decoded == null || !decoded.has("textures")) {
					return null;
				}

				JsonObject textures = decoded.getAsJsonObject("textures");

				return new ProfileTextures(read(textures, SkinKind.SKIN), read(textures, SkinKind.CAPE));
			}
		} catch (Exception ignored) {
			return null;
		}

		return null;
	}

	private static Texture read(JsonObject textures, SkinKind kind) {
		String key = kind == SkinKind.CAPE ? "CAPE" : "SKIN";

		if (!textures.has(key)) {
			return null;
		}

		JsonObject entry = textures.getAsJsonObject(key);
		String url = string(entry, "url");

		if (url == null) {
			return null;
		}

		return new Texture(url, kind == SkinKind.CAPE ? SkinData.TYPE_CAPE : model(entry));
	}

	private static String model(JsonObject entry) {
		if (!entry.has("metadata")) {
			return SkinData.TYPE_DEFAULT;
		}

		return SkinData.TYPE_SLIM.equals(string(entry.getAsJsonObject("metadata"), "model")) ? SkinData.TYPE_SLIM : SkinData.TYPE_DEFAULT;
	}

	private static JsonObject decode(String value) {
		if (value == null) {
			return null;
		}

		try {
			return GSON.fromJson(new String(Base64.getMimeDecoder().decode(value), StandardCharsets.UTF_8), JsonObject.class);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String string(JsonObject object, String key) {
		if (!object.has(key)) {
			return null;
		}

		JsonElement element = object.get(key);

		return element.isJsonPrimitive() ? element.getAsString() : null;
	}

	private record Texture(String url, String type) {
	}

	private record ProfileTextures(Texture skin, Texture cape) {
	}

	private static final class Resolution {
		private volatile GameProfile profile;
		private volatile long writtenAt;

		private Resolution(GameProfile profile) {
			this.profile = profile;
			this.writtenAt = System.currentTimeMillis();
		}
	}
}
