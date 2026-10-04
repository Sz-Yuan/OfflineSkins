package kitejs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import kitejs.OfflineSkins;
import kitejs.config.OfflineSkinsConfig;

public final class OfflineSkinsCommand {
	private OfflineSkinsCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ignored) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(
			ClientCommands.literal("offlineskins")
				.then(ClientCommands.literal("status").executes(OfflineSkinsCommand::status))
				.then(ClientCommands.literal("reload").executes(OfflineSkinsCommand::reload))
				.then(ClientCommands.literal("clear").executes(OfflineSkinsCommand::clear))
		);
	}

	private static int status(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();

		for (Component line : OfflineSkins.statusLines()) {
			source.sendFeedback(line);
		}

		return 1;
	}

	private static int reload(CommandContext<FabricClientCommandSource> context) {
		OfflineSkins.reload(OfflineSkinsConfig.load());
		context.getSource().sendFeedback(Component.translatable("offlineskins.command.reloaded"));

		return 1;
	}

	private static int clear(CommandContext<FabricClientCommandSource> context) {
		OfflineSkins.clearCaches();
		context.getSource().sendFeedback(Component.translatable("offlineskins.command.cleared"));

		return 1;
	}
}
