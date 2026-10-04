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
					OfflineSkins.LOGGER.warn("皮肤尺寸不受支持，已忽略（原版只接受 64x32 与 64x64）");
				} else {
					data.put(bytes, type);
				}
			}
		} catch (Exception e) {
			OfflineSkins.LOGGER.warn("读取本地皮肤文件失败", e);
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
			OfflineSkins.LOGGER.warn("创建本地皮肤目录失败: {}", path, e);
		}
	}
}
