package kitejs.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import kitejs.OfflineSkins;
import kitejs.util.BackgroundTasks;

public final class OfflineSkinsConfigScreen {
	private OfflineSkinsConfigScreen() {
	}

	public static Screen create(Screen parent) {
		OfflineSkinsConfig config = OfflineSkins.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("offlineskins.config.title"))
			.setSavingRunnable(() -> save(config));

		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory general = builder.getOrCreateCategory(Component.translatable("offlineskins.config.category.general"));

		general.addEntry(entries.startBooleanToggle(Component.translatable("offlineskins.config.mojangSource"), config.isMojangSourceEnabled())
			.setDefaultValue(true)
			.setTooltip(Component.translatable("offlineskins.config.mojangSource.tooltip"))
			.setSaveConsumer(config::setMojangSource)
			.build());

		general.addEntry(entries.startBooleanToggle(Component.translatable("offlineskins.config.disableSkullOverride"), config.isSkullOverrideDisabled())
			.setDefaultValue(false)
			.setTooltip(Component.translatable("offlineskins.config.disableSkullOverride.tooltip"))
			.setSaveConsumer(config::setSkullOverrideDisabled)
			.build());

		general.addEntry(entries.startIntSlider(Component.translatable("offlineskins.config.workerThreads"), config.getWorkerThreads(), 1, 32)
			.setDefaultValue(BackgroundTasks.DEFAULT_THREADS)
			.setTooltip(Component.translatable("offlineskins.config.workerThreads.tooltip"))
			.setSaveConsumer(config::setWorkerThreads)
			.build());

		return builder.build();
	}

	private static void save(OfflineSkinsConfig config) {
		if (config.save()) {
			OfflineSkins.reload(config);

			return;
		}

		OfflineSkins.reload(OfflineSkinsConfig.load());
		OfflineSkins.LOGGER.warn("Config changes were not applied because the config file could not be written");
		Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(
			Component.translatable("offlineskins.config.saveFailed").withStyle(ChatFormatting.RED)
		);
	}
}
