package lain.mods.skins.impl;

import lain.mods.skins.api.interfaces.ISkin;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

// 可变皮肤数据容器：Provider 异步 put，游戏侧读取
public class SkinData implements ISkin {

    private final Collection<Consumer<ISkin>> listeners = new CopyOnWriteArrayList<>();
    private final Collection<Function<ByteBuffer, ByteBuffer>> filters = new CopyOnWriteArrayList<>();

    private ByteBuffer data;
    private String type;

    // 字节数组 → direct buffer（游戏要求 direct）
    public static ByteBuffer toBuffer(byte[] data) {
        ByteBuffer buf = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder());
        buf.put(data);
        buf.rewind();
        return buf;
    }

    @Override
    public ByteBuffer getData() {
        return data;
    }

    @Override
    public String getSkinType() {
        return type;
    }

    @Override
    public boolean isDataReady() {
        return data != null;
    }

    @Override
    public synchronized void onRemoval() {
        for (Consumer<ISkin> listener : listeners)
            listener.accept(this);
        data = null;
        type = null;
    }

    // 写入图像与类型；依次经过已注册过滤器
    public synchronized void put(byte[] data, String type) {
        ByteBuffer buf = null;
        if (data != null) {
            buf = toBuffer(data);
            for (Function<ByteBuffer, ByteBuffer> filter : filters)
                if ((buf = filter.apply(buf)) == null)
                    break;
        }
        this.data = buf;
        this.type = type;
    }

    @Override
    public boolean setRemovalListener(Consumer<ISkin> listener) {
        if (listener == null || listeners.contains(listener))
            return false;
        return listeners.add(listener);
    }

    @Override
    public boolean setSkinFilter(Function<ByteBuffer, ByteBuffer> filter) {
        if (filter == null || filters.contains(filter))
            return false;
        return filters.add(filter);
    }

}
