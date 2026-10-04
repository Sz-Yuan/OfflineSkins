package kitejs;

import net.fabricmc.api.ClientModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kitejs.config.OfflineSkinsConfig;
import kitejs.render.SkinRenderCache;
import kitejs.skin.SkinKind;
import kitejs.skin.SkinService;
import kitejs.skin.provider.LocalSkinProvider;
import kitejs.skin.provider.MojangSkinProvider;

public class OfflineSkins implements ClientModInitializer {
	public static final String MOD_ID = "offlineskins";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static SkinService body;
	private static SkinService cape;
	private static boolean skullOverride = true;

	@Override
	public void onInitializeClient() {
		body = new SkinService();
		cape = new SkinService();

		reload(OfflineSkinsConfig.load());

		LOGGER.info("OfflineSkins 已加载");
	}

	public static void reload(OfflineSkinsConfig config) {
		body.clearProviders();
		cape.clearProviders();

		skullOverride = config.isSkullOverrideEnabled();

		if (config.isMojangSourceEnabled()) {
			body.addProvider(new MojangSkinProvider(SkinKind.SKIN));
			cape.addProvider(new MojangSkinProvider(SkinKind.CAPE));
		}

		body.addProvider(new LocalSkinProvider(SkinKind.SKIN));
		cape.addProvider(new LocalSkinProvider(SkinKind.CAPE));

		SkinRenderCache.init(body, cape);
	}

	public static boolean isSkullOverrideEnabled() {
		return skullOverride;
	}
}
