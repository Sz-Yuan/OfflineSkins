package lain.mods.skins.api;

import lain.mods.skins.api.interfaces.ISkin;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

// 聚合多个 ISkin：返回集合中第一个 dataReady 的对象；可在运行时替换集合
public class SkinBundle implements ISkin {

    protected final AtomicReference<Collection<ISkin>> ref = new AtomicReference<>(Collections.emptyList());
    protected final Collection<Consumer<ISkin>> listeners = new CopyOnWriteArrayList<>();
    protected final Collection<Function<ByteBuffer, ByteBuffer>> filters = new CopyOnWriteArrayList<>();

    // 找到第一个数据就绪的 ISkin
    protected Optional<ISkin> find() {
        Collection<ISkin> skins = ref.get();
        if (skins.isEmpty())
            return Optional.empty();
        return skins.stream().filter(ISkin::isDataReady).findFirst();
    }

    @Override
    public ByteBuffer getData() {
        return find().orElse(SkinProviderAPI.DUMMY).getData();
    }

    @Override
    public String getSkinType() {
        return find().orElse(SkinProviderAPI.DUMMY).getSkinType();
    }

    @Override
    public boolean isDataReady() {
        return find().orElse(SkinProviderAPI.DUMMY).isDataReady();
    }

    @Override
    public void onRemoval() {
        set(Collections.emptyList());
    }

    // 替换内部集合：新集合挂上监听/过滤器，旧集合触发 onRemoval
    public SkinBundle set(Collection<ISkin> c) {
        Objects.requireNonNull(c);
        if (!c.isEmpty()) {
            for (ISkin e : c) {
                listeners.forEach(e::setRemovalListener);
                filters.forEach(e::setSkinFilter);
            }
        }
        Collection<ISkin> old = ref.getAndSet(c);
        if (!old.isEmpty())
            old.forEach(ISkin::onRemoval);
        return this;
    }

    @Override
    public boolean setRemovalListener(Consumer<ISkin> listener) {
        if (listener == null || listeners.contains(listener))
            return false;
        if (!listeners.add(listener))
            return false;
        Collection<ISkin> skins = ref.get();
        if (!skins.isEmpty())
            skins.forEach(e -> e.setRemovalListener(listener));
        return true;
    }

    @Override
    public boolean setSkinFilter(Function<ByteBuffer, ByteBuffer> filter) {
        if (filter == null || filters.contains(filter))
            return false;
        if (!filters.add(filter))
            return false;
        Collection<ISkin> skins = ref.get();
        if (!skins.isEmpty())
            skins.forEach(e -> e.setSkinFilter(filter));
        return true;
    }

}
