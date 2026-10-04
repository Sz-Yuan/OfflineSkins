package kitejs.skin;

import kitejs.data.SkinData;
import kitejs.profile.PlayerProfile;

@FunctionalInterface
public interface SkinProvider {
	SkinData getSkin(PlayerProfile profile);
}
