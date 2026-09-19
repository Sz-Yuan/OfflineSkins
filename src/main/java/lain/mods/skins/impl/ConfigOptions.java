package lain.mods.skins.impl;

// 配置：仅官方 Mojang + 本地缓存；无自定义服/Crafatar
public class ConfigOptions {

    // 是否启用 Mojang 官方皮肤源
    public boolean useMojang;
    // true 时不覆盖玩家头颅渲染
    public boolean disablePlayerHeads;

    public ConfigOptions defaultOptions() {
        useMojang = true;
        disablePlayerHeads = false;
        return this;
    }

    public void validate() {
        // 无强制校验；json 中的旧字段会被 Gson 忽略
    }

}
