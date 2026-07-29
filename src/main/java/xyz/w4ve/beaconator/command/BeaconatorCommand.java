package xyz.w4ve.beaconator.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import xyz.w4ve.beaconator.client.Lang;
import xyz.w4ve.beaconator.client.PlanManager;
import xyz.w4ve.beaconator.client.gui.BeaconatorScreen;
import xyz.w4ve.beaconator.client.net.ClientSync;

/**
 * The {@code /bea} client command, which is deliberately almost nothing.
 *
 * <p>There used to be thirty odd subcommands mirroring the screen: ring, spacing, level, axis,
 * render, style, hud, layer, scan, undo, import, export and the rest. Every one of them had a
 * button that also shows its current value, which is the thing a command cannot do, so they were
 * a second way to do the same job that had to be kept in step and was worse at it.
 *
 * <p>What is left is what the screen cannot be: a way in when no key is bound, and a one liner
 * for sharing without stopping what you are doing.
 */
public final class BeaconatorCommand {
	private BeaconatorCommand() {
	}

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
			register(dispatcher, "bea");
			register(dispatcher, "beaconator");
		});
	}

	private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal(name);

		root.executes(context -> openScreen());
		root.then(ClientCommandManager.literal("gui").executes(context -> openScreen()));

		root.then(ClientCommandManager.literal("share").executes(context -> {
			if (!ClientSync.connected()) {
				return error(context.getSource(), Lang.t("plan.no_server"));
			}

			if (!PlanManager.hasPlan()) {
				return error(context.getSource(), Lang.t("no_plan"));
			}

			ClientSync.share();
			return reply(context.getSource(), Lang.t("share.pushed", PlanManager.plan().name()));
		}));

		dispatcher.register(root);
	}

	private static int openScreen() {
		// Opening a screen from inside command execution leaves the chat screen on top, so it is
		// queued for the next tick instead.
		Minecraft.getInstance().execute(BeaconatorScreen::open);
		return 1;
	}

	private static int reply(FabricClientCommandSource source, String message) {
		source.sendFeedback(Component.literal("[Beaconator] ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
		return 1;
	}

	private static int error(FabricClientCommandSource source, String message) {
		source.sendFeedback(Component.literal("[Beaconator] ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal(message).withStyle(ChatFormatting.RED)));
		return 0;
	}
}
