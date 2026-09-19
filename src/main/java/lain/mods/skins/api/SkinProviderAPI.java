package lain.mods.skins.api;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lain.lib.SharedPool;
import lain.mods.skins.api.interfaces.IPlayerProfile;
import lain.mods.skins.api.interfaces.ISkin;
import lain.mods.skins.api.interfaces.ISkinProvider;
import lain.mods.skins.api.interfaces.ISkinProviderService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ManagedBlocker;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// 对外 API：SKIN / CAPE 两个皮肤服务
public class SkinProviderAPI {

    // 空实现：无数据、不可监听
    public static final @NonNull ISkin DUMMY = new ISkin() {

        @Override
        public @Nullable ByteBuffer getData() {
            return null;
        }

        @Override
        public @Nullable String getSkinType() {
            return null;
        }

        @Override
        public boolean isDataReady() {
            return false;
        }

        @Override
        public void onRemoval() {
        }

        @Override
        public void setRemovalListener(@Nullable Consumer<ISkin> listener) {
        }

        @Override
        public void setSkinFilter(@Nullable Function<ByteBuffer, ByteBuffer> filter) {
        }

    };

    // 身体皮肤服务
    public static final @NonNull ISkinProviderService SKIN = create();
    // 披风服务
    public static final @NonNull ISkinProviderService CAPE = create();

    // 默认实现：按档案聚合各 Provider 的 ISkin；档案更新时最多等 10 秒再替换 Bundle
    public static @NonNull ISkinProviderService create() {
        return new ISkinProviderService() {

            private final @NonNull LoadingCache<SkinBundle, AtomicReference<Object>> reloading;
            private final @NonNull LoadingCache<IPlayerProfile, SkinBundle> cache;
            private final @NonNull List<ISkinProvider> providers;
            private final @NonNull Consumer<IPlayerProfile> profileChangeListener;

            {
                reloading = CacheBuilder.newBuilder().weakKeys().build(new CacheLoader<>() {

                    @Override
                    public @NonNull AtomicReference<Object> load(@NonNull SkinBundle key) {
                        return new AtomicReference<>();
                    }

                });
                cache = CacheBuilder.newBuilder().expireAfterAccess(Duration.ofSeconds(15)).removalListener(
                        (RemovalNotification<IPlayerProfile, SkinBundle> notification) -> {
                            SkinBundle skin = notification.getValue();
                            if (skin != null)
                                skin.onRemoval();
                        }).build(new CacheLoader<>() {

                    @Override
                    public @NonNull SkinBundle load(@NonNull IPlayerProfile key) {
                        key.setUpdateListener(profileChangeListener);
                        return new SkinBundle().set(providers.stream()
                                .map(provider -> provider.getSkin(key))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toCollection(ArrayList::new)));
                    }

                    @Override
                    public @NonNull ListenableFuture<SkinBundle> reload(@NonNull IPlayerProfile key, @NonNull SkinBundle oldValue) {
                        // 重新收集各 Provider 的 ISkin
                        Collection<ISkin> skins = providers.stream()
                                .map(provider -> provider.getSkin(key))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toCollection(ArrayList::new));
                        // 后台最多等待 10 秒，任一数据就绪或超时后更新 Bundle
                        Object token;
                        reloading.getUnchecked(oldValue).set(token = new Object());
                        long deadline = System.currentTimeMillis() + 10000L;
                        Supplier<Boolean> ready = () -> System.currentTimeMillis() > deadline
                                || reloading.getUnchecked(oldValue).get() != token
                                || skins.stream().anyMatch(ISkin::isDataReady);
                        Runnable update = () -> {
                            if (reloading.getUnchecked(oldValue).compareAndSet(token, null))
                                oldValue.set(skins);
                        };
                        if (skins.isEmpty()) {
                            update.run();
                        } else {
                            ManagedBlocker blocker = new ManagedBlocker() {

                                @Override
                                public boolean block() throws InterruptedException {
                                    Thread.sleep(1000L);
                                    return ready.get();
                                }

                                @Override
                                public boolean isReleasable() {
                                    return ready.get();
                                }

                            };
                            SharedPool.execute(() -> {
                                try {
                                    ForkJoinPool.managedBlock(blocker);
                                } catch (InterruptedException e) {
                                    // 中断时仍尝试提交当前结果
                                } finally {
                                    update.run();
                                }
                            });
                        }
                        return Futures.immediateFuture(oldValue);
                    }

                });
                providers = new CopyOnWriteArrayList<>();
                profileChangeListener = profile -> {
                    if (Objects.nonNull(cache.getIfPresent(profile)))
                        cache.refresh(profile);
                };
            }

            @Override
            public void clearProviders() {
                providers.clear();
                cache.invalidateAll();
            }

            @Override
            public @NonNull ISkin getSkin(@Nullable IPlayerProfile profile) {
                if (profile == null)
                    return DUMMY;
                return cache.getUnchecked(profile);
            }

            @Override
            public void registerProvider(@Nullable ISkinProvider provider) {
                if (provider == null || provider == this)
                    return;
                providers.add(provider);
            }

        };
    }

}
