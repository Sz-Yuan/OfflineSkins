package lain.mods.skins.api.interfaces;

// 皮肤来源：getSkin 在主线程调用，实现内不得做重阻塞 IO
public interface ISkinProvider {

    // 返回该档案对应的 ISkin；不可用时返回 null
    ISkin getSkin(IPlayerProfile profile);

}
