package lain.mods.skins.impl.fabric;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import lain.mods.skins.init.fabric.FabricOfflineSkins;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

// 构造 mixin 用的 PlayerSkin；返回 null 表示模组不介入
public class SkinUtils {

    private static final Function<GameProfile, Identifier> SKIN = profile -> FabricOfflineSkins.getLocationSkin(profile);
    private static final Function<GameProfile, Identifier> CAPE = profile -> FabricOfflineSkins.getLocationCape(profile);
    private static final Function<GameProfile, PlayerModelType> MODEL = profile -> PlayerModelType.byLegacyServicesName(FabricOfflineSkins.getSkinType(profile));

    // 动态贴图已注册在该 Identifier 上
    private static ClientAsset.Texture wrap(Identifier location) {
        return location == null ? null : new ClientAsset.ResourceTexture(location, location);
    }

    private static final LoadingCache<GameProfile, Supplier<PlayerSkin>> suppliers = CacheBuilder
            .newBuilder()
            .expireAfterAccess(Duration.ofSeconds(15))
            .build(new CacheLoader<>() {
                @Override
                public Supplier<PlayerSkin> load(GameProfile profile) {
                    AtomicReference<PlayerSkin> holder = new AtomicReference<>();
                    return () -> {
                        Identifier skinId = SKIN.apply(profile);
                        // 无本地/官方数据 → 保持 null，不覆盖 getSkin
                        if (skinId == null)
                            return null;
                        Identifier capeId = CAPE.apply(profile);
                        PlayerModelType model = MODEL.apply(profile);
                        PlayerSkin current = holder.get();
                        if (current == null
                                || current.body().texturePath() != skinId
                                || (current.cape() == null ? capeId != null : current.cape().texturePath() != capeId)
                                || current.model() != model) {
                            holder.set(new PlayerSkin(wrap(skinId), wrap(capeId), null, model, false));
                            current = holder.get();
                        }
                        return current;
                    };
                }
            });

    public static PlayerSkin textures(GameProfile profile) {
        return suppliers.getUnchecked(profile).get();
    }

}
