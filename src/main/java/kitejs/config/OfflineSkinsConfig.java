package kitejs.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;

import kitejs.OfflineSkins;
import kitejs.util.BackgroundTasks;

public class OfflineSkinsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	boolean mojangSource = true;
	boolean disableSkullOverride;
	int workerThreads = BackgroundTasks.DEFAULT_THREADS;

	public static OfflineSkinsConfig load() {
		Path directory = FabricLoader.getInstance().getConfigDir().resolve("offlineskins");
		Path file = directory.resolve("config.json");
		boolean exists = Files.isRegularFile(file);

		try {
			Files.createDirectories(directory);
		} catch (IOException e) {
			OfflineSkins.LOGGER.warn("Failed to create config directory", e);
		}

		if (!exists) {
			OfflineSkinsConfig config = new OfflineSkinsConfig();
			write(file, GSON.toJsonTree(config).getAsJsonObject());

			return config;
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject stored = GSON.fromJson(reader, JsonObject.class);

			if (stored == null) {
				return new OfflineSkinsConfig();
			}

			if (addMissingKeys(stored)) {
				write(file, stored);
			}

			OfflineSkinsConfig config = GSON.fromJson(stored, OfflineSkinsConfig.class);

			return config == null ? new OfflineSkinsConfig() : config;
		} catch (Exception e) {
			OfflineSkins.LOGGER.warn("Failed to read config, falling back to defaults", e);
			return new OfflineSkinsConfig();
		}
	}

	private static boolean addMissingKeys(JsonObject stored) {
		JsonObject defaults = GSON.toJsonTree(new OfflineSkinsConfig()).getAsJsonObject();
		boolean added = false;

		for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
			if (!stored.has(entry.getKey())) {
				stored.add(entry.getKey(), entry.getValue());
				added = true;
			}
		}

		return added;
	}

	public boolean isMojangSourceEnabled() {
		return mojangSource;
	}

	public boolean isSkullOverrideDisabled() {
		return disableSkullOverride;
	}

	public int getWorkerThreads() {
		return workerThreads;
	}

	private static void write(Path file, JsonObject config) {
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(config, writer);
		} catch (IOException e) {
			OfflineSkins.LOGGER.warn("Failed to write config", e);
		}
	}
}
