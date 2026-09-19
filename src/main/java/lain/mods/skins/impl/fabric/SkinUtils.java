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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

// 构造 mixin 用的 PlayerSkin；返回 null 表示模组不介入
// Guava CacheLoader 为 @NullMarked；限定/泛型类型用类型注解
public class SkinUtils {

    private static final @NonNull Function<GameProfile, @Nullable Identifier> SKIN = FabricOfflineSkins::getLocationSkin;
    private static final @NonNull Function<GameProfile, @Nullable Identifier> CAPE = FabricOfflineSkins::getLocationCape;
    // byLegacyServicesName(null) 时返回 WIDE，此处保持非 null
    private static final @NonNull Function<GameProfile, PlayerModelType> MODEL =
            profile -> PlayerModelType.byLegacyServicesName(FabricOfflineSkins.getSkinType(profile));

    // 动态贴图已注册在该 Identifier 上
    private static ClientAsset.@Nullable Texture wrap(@Nullable Identifier location) {
        return location == null ? null : new ClientAsset.ResourceTexture(location, location);
    }

    private static final @NonNull LoadingCache<GameProfile, Supplier<@Nullable PlayerSkin>> suppliers = CacheBuilder
            .newBuilder()
            .expireAfterAccess(Duration.ofSeconds(15))
            .build(new CacheLoader<>() {
                @Override
                public @NonNull Supplier<@Nullable PlayerSkin> load(@NonNull GameProfile profile) {
                    AtomicReference<@Nullable PlayerSkin> holder = new AtomicReference<>();
                    return () -> {
                        Identifier skinId = SKIN.apply(profile);
                        // 无本地/官方数据 → 保持 null，不覆盖 getSkin
                        if (skinId == null)
                            return null;
                        Identifier capeId = CAPE.apply(profile);
                        PlayerModelType model = MODEL.apply(profile);
                        if (model == null)
                            model = PlayerModelType.WIDE;
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

    public static @Nullable PlayerSkin textures(@NonNull GameProfile profile) {
        return suppliers.getUnchecked(profile).get();
    }

}
