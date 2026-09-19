package lain.mods.skins.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import lain.mods.skins.api.interfaces.IPlayerProfile;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

// 包装 GameProfile：正版解析在 MojangProvider 内完成，此处不发起网络补全
public class PlayerProfile implements IPlayerProfile {

    private static final @NonNull PlayerProfile DUMMY = new PlayerProfile(Shared.DUMMY);

    private static final @NonNull LoadingCache<GameProfile, PlayerProfile> profiles = CacheBuilder.newBuilder()
            .weakKeys()
            .build(new CacheLoader<>() {
                @Override
                public @NonNull PlayerProfile load(@NonNull GameProfile key) {
                    return key == Shared.DUMMY ? DUMMY : new PlayerProfile(key);
                }
            });

    private final CopyOnWriteArrayList<Consumer<IPlayerProfile>> listeners = new CopyOnWriteArrayList<>();
    private final WeakReference<GameProfile> profileRef;

    private PlayerProfile(@NonNull GameProfile profile) {
        profileRef = new WeakReference<>(profile);
    }

    public static PlayerProfile wrapGameProfile(@Nullable GameProfile profile) {
        if (profile == null)
            return DUMMY;
        return profiles.getUnchecked(profile);
    }

    @Override
    public boolean equals(Object o) {
        GameProfile p = profileRef.get();
        if (p == null)
            return false;
        return o instanceof PlayerProfile other && p.equals(other.profileRef.get());
    }

    @Override
    public GameProfile getOriginal() {
        GameProfile p = profileRef.get();
        return p == null ? Shared.DUMMY : p;
    }

    @Override
    public UUID getPlayerID() {
        return getOriginal().id();
    }

    @Override
    public String getPlayerName() {
        return getOriginal().name();
    }

    @Override
    public int hashCode() {
        GameProfile p = profileRef.get();
        return p == null ? 0 : p.hashCode();
    }

    @Override
    public void setUpdateListener(@Nullable Consumer<IPlayerProfile> listener) {
        // 档案不再异步替换；保留接口以兼容 SkinProviderAPI
        if (this == DUMMY || listener == null || listeners.contains(listener))
            return;
        listeners.add(listener);
    }

}
