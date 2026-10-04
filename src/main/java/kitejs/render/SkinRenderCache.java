package kitejs.render;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import kitejs.data.SkinData;
import kitejs.profile.PlayerProfile;
import kitejs.skin.SkinService;
import kitejs.texture.DynamicSkinTextures;

public final class SkinRenderCache {
	private static final Cache<PlayerProfile, Supplier<PlayerSkin>> CACHE = CacheBuilder.newBuilder()
		.expireAfterAccess(Duration.ofSeconds(15L))
		.build();

	private static SkinService bodyService;
	private static SkinService capeService;

	private SkinRenderCache() {
	}

	public static void init(SkinService body, SkinService cape) {
		bodyService = body;
		capeService = cape;
	}

	public static void cleanUp() {
		CACHE.cleanUp();
	}

	public static void invalidateAll() {
		CACHE.invalidateAll();
	}

	public static int size() {
		return CACHE.asMap().size();
	}

	public static PlayerSkin getSkin(PlayerProfile profile) {
		if (profile == null || bodyService == null || capeService == null) {
			return null;
		}

		try {
			return CACHE.get(profile, () -> new SkinSupplier(profile)).get();
		} catch (Exception e) {
			return null;
		}
	}

	private static final class SkinSupplier implements Supplier<PlayerSkin> {
		private final PlayerProfile profile;
		private final AtomicReference<PlayerSkin> skin = new AtomicReference<>();

		private Identifier body;
		private Identifier cape;
		private PlayerModelType model;

		private SkinSupplier(PlayerProfile profile) {
			this.profile = profile;
		}

		@Override
		public PlayerSkin get() {
			SkinData bodyData = bodyService.getSkin(profile);
			Identifier bodyId = bodyData == null ? null : DynamicSkinTextures.resolve(bodyData);

			if (bodyId == null) {
				return null;
			}

			SkinData capeData = capeService.getSkin(profile);
			Identifier capeId = capeData == null ? null : DynamicSkinTextures.resolve(capeData);
			PlayerModelType model = PlayerModelType.byLegacyServicesName(bodyData.getType());
			PlayerSkin current = skin.get();

			if (current != null && bodyId.equals(this.body) && Objects.equals(capeId, this.cape) && model == this.model) {
				return current;
			}

			this.body = bodyId;
			this.cape = capeId;
			this.model = model;

			PlayerSkin built = PlayerSkin.insecure(
				new ClientAsset.ResourceTexture(bodyId, bodyId),
				capeId == null ? null : new ClientAsset.ResourceTexture(capeId, capeId),
				null,
				model
			);

			skin.set(built);

			return built;
		}
	}
}
