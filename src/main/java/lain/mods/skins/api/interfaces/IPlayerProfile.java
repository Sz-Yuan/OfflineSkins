package lain.mods.skins.api.interfaces;

import java.util.UUID;
import java.util.function.Consumer;

// 玩家档案包装：可随正版解析/补全而更新
public interface IPlayerProfile {

    // 底层档案对象（通常为 GameProfile）
    Object getOriginal();

    // 档案 UUID
    UUID getPlayerID();

    // 档案用户名
    String getPlayerName();

    // 档案更新监听；null 或重复注册返回 false，勿大量堆积监听
    boolean setUpdateListener(Consumer<IPlayerProfile> listener);

}
