package lain.mods.skins.api;

import com.google.common.cache.*;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lain.lib.SharedPool;
import lain.mods.skins.api.interfaces.IPlayerProfile;
import lain.mods.skins.api.interfaces.ISkin;
import lain.mods.skins.api.interfaces.ISkinProvider;
import lain.mods.skins.api.interfaces.ISkinProviderService;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
    public static final ISkin DUMMY = new ISkin() {

        @Override
        public ByteBuffer getData() {
            return null;
        }

        @Override
        public String getSkinType() {
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
        public boolean setRemovalListener(Consumer<ISkin> listener) {
            return false;
        }

        @Override
        public boolean setSkinFilter(Function<ByteBuffer, ByteBuffer> filter) {
            return false;
        }

    };

    // 身体皮肤服务
    public static final ISkinProviderService SKIN = create();
    // 披风服务
    public static final ISkinProviderService CAPE = create();

    // 默认实现：按档案聚合各 Provider 的 ISkin；档案更新时最多等 10 秒再替换 Bundle
    public static ISkinProviderService create() {
        return new ISkinProviderService() {

            private final LoadingCache<SkinBundle, AtomicReference<Object>> reloading;
            private final LoadingCache<IPlayerProfile, SkinBundle> cache;
            private final List<ISkinProvider> providers;
            private final Consumer<IPlayerProfile> profileChangeListener;

            {
                reloading = CacheBuilder.newBuilder().weakKeys().build(new CacheLoader<>() {

                    @Override
                    public AtomicReference<Object> load(SkinBundle key) {
                        return new AtomicReference<>();
                    }

                });
                cache = CacheBuilder.newBuilder().expireAfterAccess(Duration.ofSeconds(15)).removalListener(new RemovalListener<IPlayerProfile, SkinBundle>() {

                    @Override
                    public void onRemoval(RemovalNotification<IPlayerProfile, SkinBundle> notification) {
                        SkinBundle skin = notification.getValue();
                        if (skin != null)
                            skin.onRemoval();
                    }

                }).build(new CacheLoader<>() {

                    @Override
                    public SkinBundle load(IPlayerProfile key) {
                        key.setUpdateListener(profileChangeListener);
                        return new SkinBundle().set(providers.stream()
                                .map(provider -> provider.getSkin(key))
                                .filter(skin -> skin != null)
                                .collect(Collectors.toCollection(ArrayList::new)));
                    }

                    @Override
                    public ListenableFuture<SkinBundle> reload(IPlayerProfile key, SkinBundle oldValue) {
                        // 重新收集各 Provider 的 ISkin
                        Collection<ISkin> skins = providers.stream()
                                .map(provider -> provider.getSkin(key))
                                .filter(skin -> skin != null)
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
                    if (cache.getIfPresent(profile) != null)
                        cache.refresh(profile);
                };
            }

            @Override
            public void clearProviders() {
                providers.clear();
                cache.invalidateAll();
            }

            @Override
            public ISkin getSkin(IPlayerProfile profile) {
                if (profile == null)
                    return DUMMY;
                return cache.getUnchecked(profile);
            }

            @Override
            public boolean registerProvider(ISkinProvider provider) {
                if (provider == null || provider == this)
                    return false;
                return providers.add(provider);
            }

        };
    }

}
