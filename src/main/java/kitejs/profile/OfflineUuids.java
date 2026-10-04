package kitejs.profile;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.UUIDUtil;

public final class OfflineUuids {
	private static final Map<UUID, Boolean> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

	private OfflineUuids() {
	}

	public static UUID of(String name) {
		return UUIDUtil.createOfflinePlayerUUID(name);
	}

	public static boolean isOffline(PlayerProfile profile) {
		if (profile == null) {
			return true;
		}

		UUID uuid = profile.getUuid();
		String name = profile.getName();

		if (uuid == null || name == null || name.isBlank()) {
			return true;
		}

		Boolean cached = CACHE.get(uuid);

		if (cached != null) {
			return cached;
		}

		boolean offline;

		try {
			offline = uuid.equals(of(name));
		} catch (Exception ignored) {
			offline = true;
		}

		CACHE.put(uuid, offline);

		return offline;
	}
}
