package lain.mods.skins.impl.fabric;

import net.minecraft.client.Minecraft;

import java.net.Proxy;

// Minecraft 客户端工具（网络代理）
public class MinecraftUtils {

    public static Proxy getProxy() {
        return Minecraft.getInstance().getProxy();
    }

}
