package lain.mods.skins.init.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

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

    private static final Logger LOGGER = LogUtils.getLogger();

    // ByteBuffer → 已注册的动态贴图 Identifier
    private static final Map<ByteBuffer, Identifier> textures = new WeakHashMap<>();

    // 是否用模组皮肤覆盖玩家头颅渲染
    public static boolean PLAYERHEADS = true;

    private static Identifier generateRandomLocation() {
        return Identifier.fromNamespaceAndPath("offlineskins", String.format("textures/generated/%s", UUID.randomUUID()));
    }

    public static Identifier getLocationCape(GameProfile profile) {
        return locationOf(SkinProviderAPI.CAPE.getSkin(PlayerProfile.wrapGameProfile(profile)));
    }

    public static Identifier getLocationSkin(GameProfile profile) {
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

    public static String getSkinType(GameProfile profile) {
        Identifier location = getLocationSkin(profile);
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
        // 优先级：正版名先走 Mojang，失败再本地；仅本地有 png 时用本地；皆无则空数据 → 原版
        SkinProviderAPI.SKIN.clearProviders();
        if (config.useMojang)
            SkinProviderAPI.SKIN.registerProvider(new MojangProvider(MojangProvider.Kind.SKIN).withFilter(ImageUtils::legacyFilter));
        SkinProviderAPI.SKIN.registerProvider(new UserManagedProvider(cacheRoot, UserManagedProvider.Kind.SKIN).withFilter(ImageUtils::legacyFilter));

        SkinProviderAPI.CAPE.clearProviders();
        if (config.useMojang)
            SkinProviderAPI.CAPE.registerProvider(new MojangProvider(MojangProvider.Kind.CAPE));
        SkinProviderAPI.CAPE.registerProvider(new UserManagedProvider(cacheRoot, UserManagedProvider.Kind.CAPE));

        PLAYERHEADS = !config.disablePlayerHeads;
    }

    private static ConfigOptions loadConfig() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Path path = Paths.get(".", "config", "offlineskins.json");
        Path parent = path.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                LOGGER.error("[OfflineSkins] 无法创建配置目录 {}", parent, e);
            }
        }
        if (!path.toFile().exists()) {
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                gson.toJson(new ConfigOptions().defaultOptions(), w);
            } catch (Throwable t) {
                LOGGER.error("[OfflineSkins] 写入默认配置失败", t);
            }
        }
        try {
            ConfigOptions config = gson.fromJson(Files.readString(path, StandardCharsets.UTF_8), ConfigOptions.class);
            if (config == null)
                config = new ConfigOptions().defaultOptions();
            config.validate();
            return config;
        } catch (Throwable t) {
            LOGGER.error("[OfflineSkins] 读取配置失败，使用默认值", t);
            return new ConfigOptions().defaultOptions();
        }
    }

}
