package kitejs;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kitejs.command.OfflineSkinsCommand;
import kitejs.config.OfflineSkinsConfig;
import kitejs.profile.PlayerProfile;
import kitejs.render.SkinRenderCache;
import kitejs.skin.SkinKind;
import kitejs.skin.SkinService;
import kitejs.skin.provider.LocalSkinProvider;
import kitejs.skin.provider.MojangSkinProvider;
import kitejs.texture.DynamicSkinTextures;
import kitejs.util.BackgroundTasks;
import kitejs.util.MixinChecks;

public class OfflineSkins implements ClientModInitializer {
	public static final String MOD_ID = "offlineskins";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final long CLEANUP_INTERVAL = 1_000L;

	private static SkinService body;
	private static SkinService cape;
	private static OfflineSkinsConfig config;
	private static boolean skullOverrideDisabled;
	private static boolean audited;
	private static long lastCleanUp;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(OfflineSkins::onEndTick);
		OfflineSkinsCommand.register();

		body = new SkinService();
		cape = new SkinService();

		reload(OfflineSkinsConfig.load());

		LOGGER.info("OfflineSkins loaded");
	}

	public static void reload(OfflineSkinsConfig loaded) {
		config = loaded;
		BackgroundTasks.configure(config.getWorkerThreads());
		body.clearProviders();
		cape.clearProviders();

		skullOverrideDisabled = config.isSkullOverrideDisabled();

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

	public static OfflineSkinsConfig config() {
		return config;
	}

	public static List<Component> statusLines() {
		List<Component> lines = new ArrayList<>();

		lines.add(Component.translatable("offlineskins.status.header").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		lines.add(Component.translatable("offlineskins.status.config", config.isMojangSourceEnabled(), config.isSkullOverrideDisabled()).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("offlineskins.status.workers", BackgroundTasks.threads()).withStyle(ChatFormatting.GRAY));
		lines.add(serviceLine("offlineskins.status.skinService", body));
		lines.add(serviceLine("offlineskins.status.capeService", cape));
		lines.add(Component.translatable("offlineskins.status.renderCache", SkinRenderCache.size()).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("offlineskins.status.textures", DynamicSkinTextures.size()).withStyle(ChatFormatting.GRAY));

		for (MixinChecks.Result result : MixinChecks.audit()) {
			lines.add(Component.translatable("offlineskins.status.hook", Component.translatable(result.descriptionKey()), Component.translatable(
				result.applied() ? "offlineskins.status.hook.applied" : "offlineskins.status.hook.missing"
			)).withStyle(result.applied() ? ChatFormatting.GREEN : ChatFormatting.RED));
		}

		return lines;
	}

	private static Component serviceLine(String key, SkinService service) {
		int retrying = service.retryCount();

		return Component.translatable(key, service.size(), service.readyCount(), service.unavailableCount(), retrying)
			.withStyle(retrying > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
	}

	public static void clearCaches() {
		SkinRenderCache.invalidateAll();
		body.invalidateAll();
		cape.invalidateAll();
		MojangSkinProvider.clearCache();
	}

	private static void onEndTick(Minecraft client) {
		if (!audited) {
			audited = true;
			auditInjections();
		}

		if (client.level == null) {
			cleanUp();

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

	private static void auditInjections() {
		List<MixinChecks.Result> results = MixinChecks.audit();
		int failed = 0;

		for (MixinChecks.Result result : results) {
			if (!result.applied()) {
				failed++;
				LOGGER.warn("Injection self-check: {} is not applied, this feature is disabled", result.hook());
			}
		}

		if (failed == 0) {
			LOGGER.info("Injection self-check: all {} hooks applied", results.size());
		} else {
			LOGGER.warn("Injection self-check: {} of {} hooks are not applied", failed, results.size());
		}
	}

	private static void cleanUp() {
		long now = System.currentTimeMillis();

		if (now - lastCleanUp < CLEANUP_INTERVAL) {
			return;
		}

		lastCleanUp = now;

		SkinRenderCache.cleanUp();
		body.cleanUp();
		cape.cleanUp();
	}
}
