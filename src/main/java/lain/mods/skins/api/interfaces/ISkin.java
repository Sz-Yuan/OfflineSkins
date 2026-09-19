package lain.mods.skins.api.interfaces;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;

// 皮肤数据接口：由 Provider 异步填充，游戏侧只读
public interface ISkin {

    // 皮肤图像字节；未就绪时可能为 null
    ByteBuffer getData();

    // "default"=宽臂, "slim"=细臂, "legacy"=旧版格式, "cape"=披风（后两者非官方命名）
    String getSkinType();

    // 数据是否已可提交给游戏
    boolean isDataReady();

    // 清理回调：先通知监听器，再释放资源
    void onRemoval();

    // 注册移除监听；null 或重复注册返回 false
    boolean setRemovalListener(Consumer<ISkin> listener);

    // 注册数据过滤器（链式）；提交前若改过 buffer 状态需 rewind，且最终应为 direct buffer
    boolean setSkinFilter(Function<ByteBuffer, ByteBuffer> filter);

}
