package kitejs.profile;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

public class PlayerProfile {
	public static PlayerProfile of(GameProfile profile) {
		throw new UnsupportedOperationException("第 4 步实现");
	}

	public UUID getUuid() {
		throw new UnsupportedOperationException("第 4 步实现");
	}

	public String getName() {
		throw new UnsupportedOperationException("第 4 步实现");
	}

	public interface UpdateListener {
		void onProfileUpdated(PlayerProfile profile);
	}

	public void addUpdateListener(UpdateListener listener) {
		throw new UnsupportedOperationException("第 4 步实现");
	}
}
