package lain.mods.skins.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.mojang.authlib.GameProfile;
import lain.mods.skins.api.interfaces.IPlayerProfile;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

// 包装 GameProfile；正版解析/属性补全完成后自动更新
public class PlayerProfile implements IPlayerProfile {

    private static final PlayerProfile DUMMY = new PlayerProfile(Shared.DUMMY);

    private static final LoadingCache<GameProfile, PlayerProfile> profiles = CacheBuilder.newBuilder()
            .weakKeys()
            .refreshAfterWrite(Duration.ofMinutes(10))
            .build(new CacheLoader<>() {
                @Override
                public PlayerProfile load(GameProfile key) {
                    if (key.properties() == null || key == Shared.DUMMY)
                        return DUMMY;

                    PlayerProfile profile = new PlayerProfile(key);
                    if (Shared.isBlank(key.name())) {
                        // 名字为空：仅按 UUID 补全 properties
                        if (key.id() != null)
                            Futures.addCallback(MojangService.fillProfile(key), new FutureCallback<>() {
                                @Override
                                public void onFailure(Throwable t) {
                                }

                                @Override
                                public void onSuccess(GameProfile filled) {
                                    if (filled != key)
                                        profile.set(filled);
                                }
                            }, Runnable::run);
                    } else if (Shared.isOfflinePlayer(key.id(), key.name())) {
                        // 离线 UUID：先按名字解析正版，再补全 properties
                        Futures.addCallback(MojangService.getProfile(key.name()), new FutureCallback<>() {
                            @Override
                            public void onFailure(Throwable t) {
                            }

                            @Override
                            public void onSuccess(GameProfile resolved) {
                                if (resolved == Shared.DUMMY)
                                    return;
                                profile.set(resolved);
                                Futures.addCallback(MojangService.fillProfile(resolved), new FutureCallback<>() {
                                    @Override
                                    public void onFailure(Throwable t) {
                                    }

                                    @Override
                                    public void onSuccess(GameProfile filled) {
                                        if (filled != resolved)
                                            profile.set(filled);
                                    }
                                }, Runnable::run);
                            }
                        }, Runnable::run);
                    } else if (key.properties().isEmpty()) {
                        // 疑似在线但未补全：先 fill，失败再按名字解析
                        Futures.addCallback(MojangService.fillProfile(key), new FutureCallback<>() {
                            @Override
                            public void onFailure(Throwable t) {
                            }

                            @Override
                            public void onSuccess(GameProfile filled) {
                                if (filled != key) {
                                    profile.set(filled);
                                    return;
                                }
                                Futures.addCallback(MojangService.getProfile(key.name()), new FutureCallback<>() {
                                    @Override
                                    public void onFailure(Throwable t) {
                                    }

                                    @Override
                                    public void onSuccess(GameProfile resolved) {
                                        if (resolved == Shared.DUMMY)
                                            return;
                                        profile.set(resolved);
                                        Futures.addCallback(MojangService.fillProfile(resolved), new FutureCallback<>() {
                                            @Override
                                            public void onFailure(Throwable t) {
                                            }

                                            @Override
                                            public void onSuccess(GameProfile filled2) {
                                                if (filled2 != resolved)
                                                    profile.set(filled2);
                                            }
                                        }, Runnable::run);
                                    }
                                }, Runnable::run);
                            }
                        }, Runnable::run);
                    }

                    return profile;
                }

                @Override
                public ListenableFuture<PlayerProfile> reload(GameProfile key, PlayerProfile oldValue) {
                    if (oldValue == DUMMY)
                        return Futures.immediateFuture(DUMMY);
                    return Shared.submitTask(() -> {
                        PlayerProfile newValue = load(key);
                        if (oldValue.getOriginal() != newValue.getOriginal())
                            oldValue.set(newValue.getOriginal());
                        return newValue;
                    });
                }
            });

    private final Collection<Consumer<IPlayerProfile>> listeners = new CopyOnWriteArrayList<>();
    private WeakReference<GameProfile> profileRef;

    private PlayerProfile(GameProfile profile) {
        if (profile == null)
            throw new IllegalArgumentException("profile must not be null");
        profileRef = new WeakReference<>(profile);
    }

    public static PlayerProfile wrapGameProfile(GameProfile profile) {
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

    // 更新底层档案并通知监听器
    private synchronized void set(GameProfile profile) {
        if (this == DUMMY)
            return;
        if (profile == null)
            throw new IllegalArgumentException("profile must not be null");
        profileRef = new WeakReference<>(profile);
        for (Consumer<IPlayerProfile> listener : listeners)
            listener.accept(this);
    }

    @Override
    public boolean setUpdateListener(Consumer<IPlayerProfile> listener) {
        if (this == DUMMY || listener == null || listeners.contains(listener))
            return false;
        return listeners.add(listener);
    }

}
