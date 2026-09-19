package lain.mods.skins.impl.fabric;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.Minecraft;

import java.net.Proxy;

// Minecraft 客户端工具
public class MinecraftUtils {

    public static Proxy getProxy() {
        return Minecraft.getInstance().getProxy();
    }

    public static MinecraftSessionService getSessionService() {
        return Minecraft.getInstance().services().sessionService();
    }

}
