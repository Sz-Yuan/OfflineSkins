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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public class SkinUtils {

    private static final Function<GameProfile, Identifier> SKIN = (profile) -> FabricOfflineSkins.getLocationSkin(profile, null);
    private static final Function<GameProfile, Identifier> CAPE = (profile) -> FabricOfflineSkins.getLocationCape(profile, null);
    private static final Function<GameProfile, PlayerModelType> MODEL = (profile) -> PlayerModelType.byLegacyServicesName(FabricOfflineSkins.getSkinType(profile, null));

    private static ClientAsset.Texture textureOrNull(Identifier location) {
        // Dynamic textures are registered at this Identifier; expose it as both id and texturePath.
        return location == null ? null : new ClientAsset.ResourceTexture(location, location);
    }

    private static Identifier pathOrNull(ClientAsset.Texture texture) {
        return texture == null ? null : texture.texturePath();
    }

    private static final LoadingCache<GameProfile, Supplier<PlayerSkin>> textureSuppliers = CacheBuilder
            .newBuilder()
            .expireAfterAccess(15, TimeUnit.SECONDS)
            .build(new CacheLoader<GameProfile, Supplier<PlayerSkin>>() {
                @Override
                public Supplier<PlayerSkin> load(GameProfile profile) {
                    AtomicReference<PlayerSkin> HOLDER = new AtomicReference<>();
                    return () -> {
                        PlayerSkin textures = HOLDER.get();
                        Identifier skinTexture = SKIN.apply(profile);
                        Identifier capeTexture = CAPE.apply(profile);
                        PlayerModelType model = MODEL.apply(profile);
                        if (textures == null) {
                            if (skinTexture != null) {
                                if (!HOLDER.compareAndSet(null, textures = new PlayerSkin(textureOrNull(skinTexture), textureOrNull(capeTexture), null, model, false)))
                                    textures = HOLDER.get();
                            }
                        } else if (skinTexture != null) {
                            if (pathOrNull(textures.body()) != skinTexture || pathOrNull(textures.cape()) != capeTexture || textures.model() != model) {
                                if (!HOLDER.compareAndSet(textures, textures = new PlayerSkin(textureOrNull(skinTexture), textureOrNull(capeTexture), null, model, false)))
                                    textures = HOLDER.get();
                            }
                        }
                        return textures;
                    };
                }
            });

    public static PlayerSkin textures(GameProfile profile) {
        return textureSuppliers.getUnchecked(profile).get();
    }

}
