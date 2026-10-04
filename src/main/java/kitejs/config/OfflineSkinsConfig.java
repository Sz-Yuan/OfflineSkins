package kitejs.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import kitejs.OfflineSkins;

public class OfflineSkinsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	boolean mojangSource = true;
	boolean disableSkullOverride;

	public static OfflineSkinsConfig load() {
		Path directory = FabricLoader.getInstance().getConfigDir().resolve("offlineskins");
		Path file = directory.resolve("config.json");

		try {
			Files.createDirectories(directory);
		} catch (IOException e) {
			OfflineSkins.LOGGER.warn("创建配置目录失败", e);
		}

		if (!Files.isRegularFile(file)) {
			OfflineSkinsConfig config = new OfflineSkinsConfig();
			write(file, config);

			return config;
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			OfflineSkinsConfig config = GSON.fromJson(reader, OfflineSkinsConfig.class);

			return config == null ? new OfflineSkinsConfig() : config;
		} catch (Exception e) {
			OfflineSkins.LOGGER.warn("读取配置失败，已回落默认值", e);
			return new OfflineSkinsConfig();
		}
	}

	public boolean isMojangSourceEnabled() {
		return mojangSource;
	}

	public boolean isSkullOverrideEnabled() {
		return !disableSkullOverride;
	}

	private static void write(Path file, OfflineSkinsConfig config) {
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(config, writer);
		} catch (IOException e) {
			OfflineSkins.LOGGER.warn("写入默认配置失败", e);
		}
	}
}
