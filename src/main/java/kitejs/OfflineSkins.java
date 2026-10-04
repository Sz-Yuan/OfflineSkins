package kitejs;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kitejs.config.OfflineSkinsConfig;
import kitejs.profile.PlayerProfile;
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
	private static boolean skullOverrideDisabled;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(OfflineSkins::onEndTick);

		body = new SkinService();
		cape = new SkinService();

		reload(OfflineSkinsConfig.load());

		LOGGER.info("OfflineSkins 已加载");
	}

	public static void reload(OfflineSkinsConfig config) {
		body.clearProviders();
		cape.clearProviders();

		skullOverrideDisabled = !config.isSkullOverrideEnabled();

		if (config.isMojangSourceEnabled()) {
			body.addProvider(new MojangSkinProvider(SkinKind.SKIN));
			cape.addProvider(new MojangSkinProvider(SkinKind.CAPE));
		}

		body.addProvider(new LocalSkinProvider(SkinKind.SKIN));
		cape.addProvider(new LocalSkinProvider(SkinKind.CAPE));

		SkinRenderCache.init(body, cape);
	}

	public static boolean isSkullOverrideDisabled() {
		return skullOverrideDisabled;
	}

	private static void onEndTick(Minecraft client) {
		if (client.level == null) {
			return;
		}

		ClientPacketListener connection = client.getConnection();

		if (connection == null) {
			return;
		}

		for (PlayerInfo info : connection.getOnlinePlayers()) {
			PlayerProfile profile = PlayerProfile.of(info.getProfile());
			body.getSkin(profile);
			cape.getSkin(profile);
		}
	}
}
