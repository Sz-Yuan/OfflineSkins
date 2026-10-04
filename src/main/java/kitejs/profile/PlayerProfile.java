package kitejs.profile;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.authlib.GameProfile;

import kitejs.OfflineSkins;

public class PlayerProfile {
	public interface UpdateListener {
		void onProfileUpdated(PlayerProfile profile);
	}

	private static final GameProfile SENTINEL_PROFILE = new GameProfile(new UUID(0L, 0L), "");
	private static final Map<GameProfile, PlayerProfile> CACHE = new WeakHashMap<>();

	public static final PlayerProfile SENTINEL = new PlayerProfile(SENTINEL_PROFILE);

	private final WeakReference<GameProfile> profile;
	private final List<UpdateListener> listeners = new CopyOnWriteArrayList<>();

	private PlayerProfile(GameProfile profile) {
		this.profile = new WeakReference<>(profile);
	}

	public static PlayerProfile of(GameProfile profile) {
		if (profile == null) {
			return SENTINEL;
		}

		synchronized (CACHE) {
			PlayerProfile cached = CACHE.get(profile);

			if (cached != null) {
				return cached;
			}

			PlayerProfile wrapper = new PlayerProfile(profile);
			CACHE.put(profile, wrapper);

			return wrapper;
		}
	}

	public UUID getUuid() {
		GameProfile profile = this.profile.get();

		return profile == null ? null : profile.id();
	}

	public String getName() {
		GameProfile profile = this.profile.get();

		return profile == null ? null : profile.name();
	}

	@SuppressWarnings("unused")
	public void addUpdateListener(UpdateListener listener) {
		if (listener == null || containsIdentity(listener)) {
			return;
		}

		listeners.add(listener);
	}

	@SuppressWarnings("unused")
	public void fireUpdateListeners() {
		for (UpdateListener listener : listeners) {
			try {
				listener.onProfileUpdated(this);
			} catch (RuntimeException e) {
				OfflineSkins.LOGGER.warn("档案更新监听器抛出异常", e);
			}
		}
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}

		if (!(other instanceof PlayerProfile otherProfile)) {
			return false;
		}

		GameProfile mine = this.profile.get();
		GameProfile theirs = otherProfile.profile.get();

		return mine != null && mine.equals(theirs);
	}

	@Override
	public int hashCode() {
		GameProfile profile = this.profile.get();

		return profile == null ? System.identityHashCode(this) : profile.hashCode();
	}

	private boolean containsIdentity(UpdateListener listener) {
		for (UpdateListener element : listeners) {
			if (element == listener) {
				return true;
			}
		}

		return false;
	}
}
