package lain.mods.skins.api.interfaces;

// 皮肤服务：管理 Provider 列表并按档案聚合 ISkin
public interface ISkinProviderService extends ISkinProvider {

    // 清空已注册的 Provider
    void clearProviders();

    // 注册 Provider（失败仅忽略该次注册）
    void registerProvider(ISkinProvider provider);

}
