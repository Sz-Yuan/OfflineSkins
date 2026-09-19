package lain.mods.skins.init.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import lain.mods.skins.api.SkinProviderAPI;
import lain.mods.skins.api.interfaces.ISkin;
import lain.mods.skins.impl.ConfigOptions;
import lain.mods.skins.impl.PlayerProfile;
import lain.mods.skins.impl.fabric.ImageUtils;
import lain.mods.skins.providers.MojangProvider;
import lain.mods.skins.providers.UserManagedProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Fabric 客户端入口：配置、Provider 注册、动态贴图
public class FabricOfflineSkins implements ClientModInitializer {

    // ByteBuffer → 已注册的动态贴图 Identifier
    private static final Map<ByteBuffer, Identifier> textures = new WeakHashMap<>();

    // 是否用模组皮肤覆盖玩家头颅渲染
    public static boolean PLAYERHEADS = true;

    private static Identifier generateRandomLocation() {
        return Identifier.fromNamespaceAndPath("offlineskins", String.format("textures/generated/%s", UUID.randomUUID()));
    }

    public static Identifier getLocationCape(GameProfile profile, Identifier result) {
        return locationOf(SkinProviderAPI.CAPE.getSkin(PlayerProfile.wrapGameProfile(profile)));
    }

    public static Identifier getLocationSkin(GameProfile profile, Identifier result) {
        return locationOf(SkinProviderAPI.SKIN.getSkin(PlayerProfile.wrapGameProfile(profile)));
    }

    // 无就绪数据 → null → SkinUtils/mixin 不介入，保持原版
    private static Identifier locationOf(ISkin skin) {
        if (skin == null || !skin.isDataReady())
            return null;
        ByteBuffer data = skin.getData();
        return data == null ? null : getOrCreateTextureNullable(data, skin);
    }

    private static Identifier getOrCreateTexture(ByteBuffer data, ISkin skin) throws IOException {
        if (!textures.containsKey(data)) {
            Identifier location = generateRandomLocation();
            Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(location::toString, NativeImage.read(data)));
            textures.put(data, location);
            if (skin != null) {
                skin.setRemovalListener(s -> {
                    if (data == s.getData()) {
                        Minecraft.getInstance().execute(() -> {
                            Minecraft.getInstance().getTextureManager().release(location);
                            textures.remove(data);
                        });
                    }
                });
            }
        }
        return textures.get(data);
    }

    private static Identifier getOrCreateTextureNullable(ByteBuffer data, ISkin skin) {
        try {
            return getOrCreateTexture(data, skin);
        } catch (IOException e) {
            return null;
        }
    }

    public static String getSkinType(GameProfile profile, String result) {
        Identifier location = getLocationSkin(profile, null);
        if (location == null)
            return null;
        ISkin skin = SkinProviderAPI.SKIN.getSkin(PlayerProfile.wrapGameProfile(profile));
        return skin != null && skin.isDataReady() ? skin.getSkinType() : null;
    }

    @Override
    public void onInitializeClient() {
        // 每 tick 预热世界内玩家的皮肤请求
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level != null) {
                for (AbstractClientPlayer player : mc.level.players()) {
                    SkinProviderAPI.SKIN.getSkin(PlayerProfile.wrapGameProfile(player.getGameProfile()));
                    SkinProviderAPI.CAPE.getSkin(PlayerProfile.wrapGameProfile(player.getGameProfile()));
                }
            }
        });

        reloadConfig();
    }

    public void reloadConfig() {
        ConfigOptions config = loadConfig();

        Path cacheRoot = Paths.get(".", "cachedImages");
        // 注册顺序：本地 png 优先，其次 Mojang（仅正版）
        SkinProviderAPI.SKIN.clearProviders();
        SkinProviderAPI.SKIN.registerProvider(new UserManagedProvider(cacheRoot, UserManagedProvider.Kind.SKIN).withFilter(ImageUtils::legacyFilter));
        if (config.useMojang)
            SkinProviderAPI.SKIN.registerProvider(new MojangProvider(MojangProvider.Kind.SKIN).withFilter(ImageUtils::legacyFilter));

        SkinProviderAPI.CAPE.clearProviders();
        SkinProviderAPI.CAPE.registerProvider(new UserManagedProvider(cacheRoot, UserManagedProvider.Kind.CAPE));
        if (config.useMojang)
            SkinProviderAPI.CAPE.registerProvider(new MojangProvider(MojangProvider.Kind.CAPE));

        PLAYERHEADS = !config.disablePlayerHeads;
    }

    private static ConfigOptions loadConfig() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Path path = Paths.get(".", "config", "offlineskins.json");
        path.toFile().getParentFile().mkdirs();
        if (!path.toFile().exists()) {
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                gson.toJson(new ConfigOptions().defaultOptions(), w);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        try {
            ConfigOptions config = gson.fromJson(Files.readString(path, StandardCharsets.UTF_8), ConfigOptions.class);
            return config != null ? config : new ConfigOptions().defaultOptions();
        } catch (Throwable t) {
            t.printStackTrace();
            return new ConfigOptions().defaultOptions();
        }
    }

}
