package kitejs;

import net.fabricmc.api.ClientModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kitejs.render.SkinRenderCache;
import kitejs.skin.SkinKind;
import kitejs.skin.SkinService;
import kitejs.skin.provider.LocalSkinProvider;

public class OfflineSkins implements ClientModInitializer {
	public static final String MOD_ID = "offlineskins";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		SkinService body = new SkinService();
		SkinService cape = new SkinService();

		body.addProvider(new LocalSkinProvider(SkinKind.SKIN));
		cape.addProvider(new LocalSkinProvider(SkinKind.CAPE));

		SkinRenderCache.init(body, cape);

		LOGGER.info("OfflineSkins 已加载");
	}
}
