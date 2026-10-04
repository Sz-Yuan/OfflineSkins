package kitejs.skin.provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import net.fabricmc.loader.api.FabricLoader;

import kitejs.OfflineSkins;
import kitejs.data.SkinData;
import kitejs.profile.OfflineUuids;
import kitejs.profile.PlayerProfile;
import kitejs.skin.SkinKind;
import kitejs.skin.SkinProvider;
import kitejs.util.BackgroundTasks;
import kitejs.util.ImageTools;

public class LocalSkinProvider implements SkinProvider {
	private final SkinKind kind;
	private final Path directory;
	private final Path uuidDirectory;

	public LocalSkinProvider(SkinKind kind) {
		this.kind = kind;
		this.directory = FabricLoader.getInstance()
			.getConfigDir()
			.resolve("offlineskins")
			.resolve(kind == SkinKind.CAPE ? "capes" : "skins");
		this.uuidDirectory = this.directory.resolve("uuid");

		createDirectories(directory);
		createDirectories(uuidDirectory);
	}

	@Override
	public SkinData getSkin(PlayerProfile profile) {
		SkinData data = new SkinData();

		BackgroundTasks.execute(() -> load(profile, data));

		return data;
	}

	private void load(PlayerProfile profile, SkinData data) {
		try {
			byte[] bytes = read(profile);

			if (bytes != null && ImageTools.isValidPng(bytes)) {
				String type = kind == SkinKind.CAPE ? SkinData.TYPE_CAPE : ImageTools.detectSkinType(bytes);

				if (SkinData.TYPE_UNKNOWN.equals(type)) {
					OfflineSkins.LOGGER.warn("Unsupported skin size, ignored (vanilla only accepts 64x32 and 64x64)");
					data.markUnavailable();
				} else {
					data.put(bytes, type);
				}
			} else {
				data.markUnavailable();
			}
		} catch (Exception e) {
			OfflineSkins.LOGGER.debug("Failed to read local skin file", e);
		}
	}

	private byte[] read(PlayerProfile profile) {
		UUID uuid = profile.getUuid();
		String name = profile.getName();

		if (uuid != null && !OfflineUuids.isOffline(profile)) {
			byte[] bytes = readFile(uuidDirectory.resolve(uuid.toString().replace("-", "") + ".png"));

			if (bytes != null) {
				return bytes;
			}
		}

		if (isValidName(name)) {
			return readFile(directory.resolve(name + ".png"));
		}

		return null;
	}

	private static byte[] readFile(Path path) {
		try {
			return Files.readAllBytes(path);
		} catch (IOException e) {
			return null;
		}
	}

	private static boolean isValidName(String name) {
		return name != null && !name.isBlank() && name.indexOf('/') < 0 && name.indexOf('\\') < 0 && name.indexOf(':') < 0;
	}

	private static void createDirectories(Path path) {
		try {
			Files.createDirectories(path);
		} catch (IOException e) {
			OfflineSkins.LOGGER.warn("Failed to create local skin directory: {}", path, e);
		}
	}
}
