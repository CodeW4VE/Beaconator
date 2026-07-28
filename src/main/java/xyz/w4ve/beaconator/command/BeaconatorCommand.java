package xyz.w4ve.beaconator.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import xyz.w4ve.beaconator.BeaconatorClient;
import xyz.w4ve.beaconator.client.LayerFilter;
import xyz.w4ve.beaconator.client.NodeEditor;
import xyz.w4ve.beaconator.client.Lang;
import xyz.w4ve.beaconator.client.PlanHistory;
import xyz.w4ve.beaconator.client.PlanManager;
import xyz.w4ve.beaconator.client.gui.BeaconatorScreen;
import xyz.w4ve.beaconator.client.scan.NodeScan;
import xyz.w4ve.beaconator.client.scan.ScanCache;
import xyz.w4ve.beaconator.client.scan.WorldGridDetector;
import xyz.w4ve.beaconator.config.BeaconatorConfig;
import xyz.w4ve.beaconator.config.CoverageStyle;
import xyz.w4ve.beaconator.io.LitematicIO;
import xyz.w4ve.beaconator.io.SchematicFiles;
import xyz.w4ve.beaconator.model.GridNode;
import xyz.w4ve.beaconator.model.GridSide;
import xyz.w4ve.beaconator.model.MaterialTally;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.PyramidCalculator;
import xyz.w4ve.beaconator.model.RowAxis;

/** The {@code /bea} client command. Nothing here is sent to the server. */
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

		root.executes(context -> info(context.getSource()));

		root.then(ClientCommandManager.literal("new")
				.then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
						.executes(context -> newPlan(context, StringArgumentType.getString(context, "name")))));

		root.then(ClientCommandManager.literal("open")
				.then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
						.suggests((context, builder) ->
								SharedSuggestionProvider.suggest(PlanManager.savedPlans(), builder))
						.executes(context -> open(context, StringArgumentType.getString(context, "name")))));

		root.then(ClientCommandManager.literal("list").executes(context -> list(context.getSource())));
		root.then(ClientCommandManager.literal("save").executes(context -> save(context.getSource())));
		root.then(ClientCommandManager.literal("close").executes(context -> close(context.getSource())));
		root.then(ClientCommandManager.literal("info").executes(context -> info(context.getSource())));

		root.then(ClientCommandManager.literal("delete")
				.then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
						.suggests((context, builder) ->
								SharedSuggestionProvider.suggest(PlanManager.savedPlans(), builder))
						.executes(context -> delete(context, StringArgumentType.getString(context, "name")))));

		root.then(ClientCommandManager.literal("edit").executes(context -> {
			if (noPlan(context.getSource())) {
				return 0;
			}

			PlanManager.setEditMode(!PlanManager.editMode());
			return reply(context.getSource(), "Edit mode " + (PlanManager.editMode() ? "on" : "off"));
		}));

		root.then(ClientCommandManager.literal("center")
				.executes(context -> center(context, null))
				.then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
						.then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
								.then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
										.executes(context -> center(context, new int[] {
											IntegerArgumentType.getInteger(context, "x"),
											IntegerArgumentType.getInteger(context, "y"),
											IntegerArgumentType.getInteger(context, "z")
										}))))));

		root.then(ClientCommandManager.literal("ring")
				.then(ClientCommandManager.argument("n", IntegerArgumentType.integer(0, 64))
						.executes(context -> {
							if (noPlan(context.getSource())) {
								return 0;
							}

							PerimeterPlan plan = PlanManager.plan();
							plan.setRing(IntegerArgumentType.getInteger(context, "n"));
							plan.pruneOverrides();
							PlanManager.markDirty();
							return reply(context.getSource(), plan.extents().columns() + " x "
									+ plan.extents().rows() + " nodes (" + plan.extents().nodeCount() + ")");
						})));

		LiteralArgumentBuilder<FabricClientCommandSource> side = ClientCommandManager.literal("side");

		for (GridSide gridSide : GridSide.values()) {
			side.then(ClientCommandManager.literal(gridSide.name().toLowerCase())
					.then(ClientCommandManager.argument("delta", IntegerArgumentType.integer(-64, 64))
							.executes(context -> {
								if (noPlan(context.getSource())) {
									return 0;
								}

								PerimeterPlan plan = PlanManager.plan();
								plan.setExtents(plan.extents()
										.expand(gridSide, IntegerArgumentType.getInteger(context, "delta")));
								plan.pruneOverrides();
								PlanManager.markDirty();
								return reply(context.getSource(), plan.extents().columns() + " x "
										+ plan.extents().rows() + " nodes (" + plan.extents().nodeCount() + ")");
							})));
		}

		root.then(side);

		root.then(ClientCommandManager.literal("beacons")
				.then(ClientCommandManager.argument("n",
								IntegerArgumentType.integer(1, PyramidCalculator.MAX_BEACONS_PER_NODE))
						.executes(context -> {
							if (noPlan(context.getSource())) {
								return 0;
							}

							PerimeterPlan plan = PlanManager.plan();
							plan.setBeaconsPerNode(IntegerArgumentType.getInteger(context, "n"));
							PlanManager.markDirty();
							return reply(context.getSource(), plan.beaconsPerNode() + " beacons per node, "
									+ PyramidCalculator.totalBlocks(plan.beaconsPerNode(), plan.level())
									+ " blocks per pyramid");
						})));

		root.then(ClientCommandManager.literal("level")
				.then(ClientCommandManager.argument("n", IntegerArgumentType.integer(1, PyramidCalculator.MAX_LEVEL))
						.executes(context -> {
							if (noPlan(context.getSource())) {
								return 0;
							}

							PerimeterPlan plan = PlanManager.plan();
							plan.setLevel(IntegerArgumentType.getInteger(context, "n"));
							PlanManager.markDirty();
							return reply(context.getSource(), "Level " + plan.level()
									+ ", reach " + (10 * plan.level() + 10) + " blocks, spacing " + plan.spacing());
						})));

		root.then(ClientCommandManager.literal("axis")
				.then(ClientCommandManager.literal("x").executes(context -> axis(context, RowAxis.X)))
				.then(ClientCommandManager.literal("z").executes(context -> axis(context, RowAxis.Z))));

		root.then(ClientCommandManager.literal("spacing")
				.then(ClientCommandManager.literal("auto").executes(context -> {
					if (noPlan(context.getSource())) {
						return 0;
					}

					PerimeterPlan plan = PlanManager.plan();
					plan.setAutoSpacing(true);
					PlanManager.markDirty();
					return reply(context.getSource(), "Spacing " + plan.spacing() + " (auto)");
				}))
				.then(ClientCommandManager.argument("blocks", IntegerArgumentType.integer(1, 4096))
						.executes(context -> {
							if (noPlan(context.getSource())) {
								return 0;
							}

							PerimeterPlan plan = PlanManager.plan();
							plan.setSpacing(IntegerArgumentType.getInteger(context, "blocks"));
							PlanManager.markDirty();
							return reply(context.getSource(), "Spacing " + plan.spacing() + gapNote(plan));
						})));

		root.then(ClientCommandManager.literal("marker")
				.then(ClientCommandManager.literal("on").executes(context -> marker(context, true)))
				.then(ClientCommandManager.literal("off").executes(context -> marker(context, false))));

		root.then(ClientCommandManager.literal("block")
				.then(ClientCommandManager.literal("pyramid")
						.then(blockArgument(true)))
				.then(ClientCommandManager.literal("marker")
						.then(blockArgument(false))));

		root.then(ClientCommandManager.literal("render")
				.then(ClientCommandManager.literal("on").executes(context -> render(context, true)))
				.then(ClientCommandManager.literal("off").executes(context -> render(context, false))));

		root.then(ClientCommandManager.literal("style")
				.then(ClientCommandManager.literal("slab").executes(context -> style(context, CoverageStyle.SLAB)))
				.then(ClientCommandManager.literal("floor").executes(context -> style(context, CoverageStyle.FLOOR)))
				.then(ClientCommandManager.literal("full").executes(context -> style(context, CoverageStyle.FULL))));

		root.then(ClientCommandManager.literal("hud")
				.then(ClientCommandManager.literal("on").executes(context -> hud(context, true)))
				.then(ClientCommandManager.literal("off").executes(context -> hud(context, false))));

		root.then(ClientCommandManager.literal("gui").executes(context -> {
			// Opening a screen from inside command execution leaves the chat screen on top,
			// so it is queued for the next tick instead.
			Minecraft.getInstance().execute(BeaconatorScreen::open);
			return 1;
		}));

		root.then(ClientCommandManager.literal("scan").executes(context -> scan(context.getSource())));

		root.then(ClientCommandManager.literal("undo").executes(context -> {
			String what = PlanHistory.undo(PlanManager.plan());

			if (what == null) {
				return error(context.getSource(), Lang.t("map.nothing_to_undo"));
			}

			ScanCache.clear();
			return reply(context.getSource(), Lang.t("map.undone", what));
		}));

		root.then(ClientCommandManager.literal("detect")
				.executes(context -> detect(context, "detected", 256))
				.then(ClientCommandManager.argument("name", StringArgumentType.word())
						.executes(context -> detect(context, StringArgumentType.getString(context, "name"), 256))
						.then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(16, 4096))
								.executes(context -> detect(context,
										StringArgumentType.getString(context, "name"),
										IntegerArgumentType.getInteger(context, "radius"))))));

		root.then(ClientCommandManager.literal("move")
				.then(ClientCommandManager.argument("dx", IntegerArgumentType.integer())
						.then(ClientCommandManager.argument("dy", IntegerArgumentType.integer())
								.then(ClientCommandManager.argument("dz", IntegerArgumentType.integer())
										.executes(BeaconatorCommand::move)))));

		root.then(ClientCommandManager.literal("materials")
				.executes(context -> materials(context.getSource(), false))
				.then(ClientCommandManager.literal("node")
						.executes(context -> materials(context.getSource(), true))));

		root.then(ClientCommandManager.literal("state")
				.then(ClientCommandManager.literal("pending").executes(context -> state(context, NodeStatus.PENDING)))
				.then(ClientCommandManager.literal("excluded").executes(context -> state(context, NodeStatus.EXCLUDED)))
				.then(ClientCommandManager.literal("removed").executes(context -> state(context, NodeStatus.REMOVED))));

		root.then(ClientCommandManager.literal("fill")
				.then(ClientCommandManager.argument("status", StringArgumentType.word())
						.suggests((context, builder) ->
								SharedSuggestionProvider.suggest(new String[] {"pending", "excluded", "removed"}, builder))
						.then(ClientCommandManager.argument("fromI", IntegerArgumentType.integer())
								.then(ClientCommandManager.argument("fromJ", IntegerArgumentType.integer())
										.then(ClientCommandManager.argument("toI", IntegerArgumentType.integer())
												.then(ClientCommandManager.argument("toJ", IntegerArgumentType.integer())
														.executes(BeaconatorCommand::fill)))))));

		root.then(ClientCommandManager.literal("layer")
				.then(ClientCommandManager.literal("all").executes(context -> {
					LayerFilter.all();
					return reply(context.getSource(), "Showing " + LayerFilter.describe());
				}))
				.then(ClientCommandManager.literal("here").executes(context -> {
					Minecraft mc = Minecraft.getInstance();

					if (mc.player == null) {
						return 0;
					}

					LayerFilter.single(mc.player.getBlockY());
					return reply(context.getSource(), "Showing " + LayerFilter.describe());
				}))
				.then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
						.executes(context -> {
							LayerFilter.single(IntegerArgumentType.getInteger(context, "y"));
							return reply(context.getSource(), "Showing " + LayerFilter.describe());
						})
						.then(ClientCommandManager.argument("toY", IntegerArgumentType.integer())
								.executes(context -> {
									LayerFilter.range(IntegerArgumentType.getInteger(context, "y"),
											IntegerArgumentType.getInteger(context, "toY"));
									return reply(context.getSource(), "Showing " + LayerFilter.describe());
								}))));

		root.then(ClientCommandManager.literal("easyplace")
				.then(ClientCommandManager.literal("on").executes(context -> easyPlace(context, true)))
				.then(ClientCommandManager.literal("off").executes(context -> easyPlace(context, false))));

		root.then(ClientCommandManager.literal("import")
				.then(ClientCommandManager.argument("file", StringArgumentType.greedyString())
						.suggests((context, builder) ->
								SharedSuggestionProvider.suggest(SchematicFiles.list(), builder))
						.executes(context -> importSchematic(context,
								StringArgumentType.getString(context, "file")))));

		root.then(ClientCommandManager.literal("export")
				.executes(context -> exportSchematic(context, null))
				.then(ClientCommandManager.argument("file", StringArgumentType.greedyString())
						.executes(context -> exportSchematic(context,
								StringArgumentType.getString(context, "file")))));

		dispatcher.register(root);
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, String>
			blockArgument(boolean pyramid) {
		return ClientCommandManager.argument("block", StringArgumentType.string())
				.suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
						BuiltInRegistries.BLOCK.keySet(), builder))
				.executes(context -> {
					if (noPlan(context.getSource())) {
						return 0;
					}

					String id = StringArgumentType.getString(context, "block");

					if (!id.contains(":")) {
						id = "minecraft:" + id;
					}

					PerimeterPlan plan = PlanManager.plan();

					if (pyramid) {
						plan.setPyramidBlock(id);
					} else {
						plan.setMarkerBlock(id);
					}

					PlanManager.markDirty();
					return reply(context.getSource(), (pyramid ? "Pyramid" : "Marker") + " block: " + id);
				});
	}

	// ---------------------------------------------------------------- actions

	private static int newPlan(CommandContext<FabricClientCommandSource> context, String name) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || mc.level == null) {
			return 0;
		}

		PlanManager.autoSave();
		PerimeterPlan plan = new PerimeterPlan(name, mc.level.dimension().location().toString(),
				mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
		PlanManager.setPlan(plan);
		PlanManager.setEditMode(true);
		PlanManager.save();

		return reply(context.getSource(), "Plan \"" + name + "\" centred at "
				+ plan.centerX() + ", " + plan.beaconY() + ", " + plan.centerZ()
				+ ". Scroll to grow the grid, shift scroll for beacons per node.");
	}

	private static int open(CommandContext<FabricClientCommandSource> context, String name) {
		PlanManager.autoSave();

		if (!PlanManager.load(name)) {
			return error(context.getSource(), "No plan called \"" + name + "\" for this world");
		}

		PerimeterPlan plan = PlanManager.plan();
		return reply(context.getSource(), "Opened \"" + plan.name() + "\": "
				+ plan.extents().columns() + " x " + plan.extents().rows() + " nodes");
	}

	private static int list(FabricClientCommandSource source) {
		var names = PlanManager.savedPlans();

		if (names.isEmpty()) {
			return reply(source, "No saved plans for this world yet");
		}

		return reply(source, "Saved plans: " + String.join(", ", names));
	}

	private static int save(FabricClientCommandSource source) {
		if (noPlan(source)) {
			return 0;
		}

		if (!PlanManager.save()) {
			return error(source, "Could not save, check the log");
		}

		return reply(source, "Saved \"" + PlanManager.plan().name() + "\"");
	}

	private static int delete(CommandContext<FabricClientCommandSource> context, String name) {
		try {
			if (!xyz.w4ve.beaconator.io.PlanStore.delete(PlanManager.worldId(), name)) {
				return error(context.getSource(), "No plan called \"" + name + "\"");
			}
		} catch (java.io.IOException e) {
			return error(context.getSource(), "Could not delete: " + e.getMessage());
		}

		if (PlanManager.hasPlan() && PlanManager.plan().name().equals(name)) {
			PlanManager.closePlan();
		}

		return reply(context.getSource(), "Deleted \"" + name + "\"");
	}

	private static int close(FabricClientCommandSource source) {
		if (noPlan(source)) {
			return 0;
		}

		PlanManager.autoSave();
		String name = PlanManager.plan().name();
		PlanManager.closePlan();
		return reply(source, "Closed \"" + name + "\"");
	}

	private static int center(CommandContext<FabricClientCommandSource> context, int[] pos) {
		if (noPlan(context.getSource())) {
			return 0;
		}

		Minecraft mc = Minecraft.getInstance();
		PerimeterPlan plan = PlanManager.plan();

		if (pos == null) {
			if (mc.player == null) {
				return 0;
			}

			plan.setCenter(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
		} else {
			plan.setCenter(pos[0], pos[1], pos[2]);
		}

		PlanManager.markDirty();
		return reply(context.getSource(), "Centre at "
				+ plan.centerX() + ", " + plan.beaconY() + ", " + plan.centerZ());
	}

	private static int axis(CommandContext<FabricClientCommandSource> context, RowAxis axis) {
		if (noPlan(context.getSource())) {
			return 0;
		}

		PlanManager.plan().setRowAxis(axis);
		PlanManager.markDirty();
		return reply(context.getSource(), "Beacon rows run along " + axis);
	}

	private static int marker(CommandContext<FabricClientCommandSource> context, boolean on) {
		if (noPlan(context.getSource())) {
			return 0;
		}

		PerimeterPlan plan = PlanManager.plan();
		plan.setPlaceMarker(on);
		PlanManager.markDirty();
		return reply(context.getSource(), on
				? "Excluded nodes get a real " + plan.markerBlock() + " on top"
				: "Excluded nodes are only marked in the UI");
	}

	private static int render(CommandContext<FabricClientCommandSource> context, boolean on) {
		BeaconatorConfig config = BeaconatorConfig.get();
		config.renderCoverage = on;
		config.renderWireframe = on;
		config.save();
		return reply(context.getSource(), "Render " + (on ? "on" : "off"));
	}

	private static int style(CommandContext<FabricClientCommandSource> context, CoverageStyle style) {
		BeaconatorConfig config = BeaconatorConfig.get();
		config.coverageStyle = style;
		config.save();
		return reply(context.getSource(), "Coverage style: " + style);
	}

	private static int hud(CommandContext<FabricClientCommandSource> context, boolean on) {
		BeaconatorConfig config = BeaconatorConfig.get();
		config.showHud = on;
		config.save();
		return reply(context.getSource(), "HUD " + (on ? "on" : "off"));
	}

	private static int info(FabricClientCommandSource source) {
		if (noPlan(source)) {
			return 0;
		}

		PerimeterPlan plan = PlanManager.plan();
		source.sendFeedback(header("Beaconator: " + plan.name()));
		source.sendFeedback(line("Centre", plan.centerX() + ", " + plan.beaconY() + ", " + plan.centerZ()
				+ " in " + plan.dimension()));
		source.sendFeedback(line("Grid", plan.extents().columns() + " x " + plan.extents().rows()
				+ " = " + plan.extents().nodeCount() + " nodes"
				+ (plan.ring() >= 0 ? " (ring " + plan.ring() + ")" : " (custom sides)")));
		source.sendFeedback(line("Beacons", plan.beaconsPerNode() + " per node on level " + plan.level()
				+ " pyramids, rows along " + plan.rowAxis()));
		source.sendFeedback(line("Spacing", plan.spacing() + (plan.autoSpacing() ? " (auto)" : "") + gapNote(plan)));
		source.sendFeedback(line("Blocks", plan.pyramidBlock()
				+ (plan.placeMarker() ? " + " + plan.markerBlock() + " on excluded" : "")));
		source.sendFeedback(line("States", plan.countByStatus(NodeStatus.PENDING) + " pending, "
				+ plan.countByStatus(NodeStatus.PLACED) + " placed, "
				+ plan.countByStatus(NodeStatus.EXCLUDED) + " excluded, "
				+ plan.countByStatus(NodeStatus.REMOVED) + " removed"));

		MaterialTally tally = plan.tally();

		for (Map.Entry<String, Integer> entry : tally.counts().entrySet()) {
			source.sendFeedback(line(shortName(entry.getKey()), String.format("%,d", entry.getValue())));
		}

		source.sendFeedback(line("Total", String.format("%,d blocks", tally.total())));
		return 1;
	}

	private static int scan(FabricClientCommandSource source) {
		if (noPlan(source)) {
			return 0;
		}

		Minecraft mc = Minecraft.getInstance();

		if (!PlanManager.inPlanDimension()) {
			return error(source, "This plan belongs to " + PlanManager.plan().dimension());
		}

		int[] result = ScanCache.scanAll(mc);
		reply(source, "Scanned " + result[0] + " of " + result[1] + " nodes ("
				+ result[2] + " finished). Nodes outside view distance cannot be read.");
		return materials(source, false);
	}

	private static int detect(CommandContext<FabricClientCommandSource> context, String name, int radius) {
		Minecraft mc = Minecraft.getInstance();
		WorldGridDetector.Result result;

		try {
			result = WorldGridDetector.detect(mc, name, radius);
		} catch (IllegalStateException | IllegalArgumentException e) {
			return error(context.getSource(), e.getMessage());
		}

		PerimeterPlan plan = result.plan();
		PlanManager.autoSave();
		PlanManager.setPlan(plan);
		ScanCache.clear();
		PlanManager.save();

		reply(context.getSource(), "Found " + result.beaconsFound() + " beacons in "
				+ result.chunksRead() + " loaded chunks: "
				+ plan.extents().columns() + " x " + plan.extents().rows() + " nodes, "
				+ plan.beaconsPerNode() + " per node along " + plan.rowAxis()
				+ ", spacing " + plan.spacing() + ", level " + plan.level()
				+ " " + shortName(plan.pyramidBlock()) + " pyramids");
		return reply(context.getSource(), "Only loaded chunks were read. Scroll to grow the grid "
				+ "over the rest of the perimeter, the nodes land where they should.");
	}

	private static int materials(FabricClientCommandSource source, boolean nodeOnly) {
		if (noPlan(source)) {
			return 0;
		}

		PerimeterPlan plan = PlanManager.plan();

		if (nodeOnly) {
			NodeKey key = PlanManager.hovered();

			if (key == null) {
				return error(source, "Point at a node first, with edit mode on");
			}

			GridNode node = plan.nodeAt(key);
			NodeScan scan = ScanCache.get(key);
			source.sendFeedback(header("Node " + key + " at " + node.x() + ", " + node.z()));

			for (Map.Entry<String, Integer> entry : plan.tallyOf(node).counts().entrySet()) {
				source.sendFeedback(line(shortName(entry.getKey()), String.format("%,d", entry.getValue())));
			}

			if (scan != null && scan.loaded()) {
				source.sendFeedback(line("In place", scan.found() + " of " + scan.expected()));

				for (Map.Entry<String, Integer> entry : scan.missing().counts().entrySet()) {
					source.sendFeedback(line("Missing " + shortName(entry.getKey()),
							String.format("%,d", entry.getValue())));
				}
			}

			return 1;
		}

		MaterialTally[] totals = ScanCache.missingTotals();
		MaterialTally missing = totals[0];
		MaterialTally unknown = totals[1];

		source.sendFeedback(header("Beaconator materials: " + plan.name()));

		for (Map.Entry<String, Integer> entry : plan.tally().counts().entrySet()) {
			int total = entry.getValue();
			int left = missing.get(entry.getKey()) + unknown.get(entry.getKey());
			source.sendFeedback(line(shortName(entry.getKey()),
					String.format("%,d needed, %,d still missing", total, left)));
		}

		if (!unknown.isEmpty()) {
			source.sendFeedback(Component.literal(String.format(
					"%,d of those are in nodes too far away to check", unknown.total()))
					.withStyle(ChatFormatting.DARK_GRAY));
		}

		source.sendFeedback(line("Total", String.format("%,d blocks, %,d to go",
				plan.tally().total(), missing.total() + unknown.total())));
		return 1;
	}

	private static int state(CommandContext<FabricClientCommandSource> context, NodeStatus status) {
		if (noPlan(context.getSource())) {
			return 0;
		}

		NodeKey key = PlanManager.hovered();

		if (key == null) {
			return error(context.getSource(), "Point at a node first, with edit mode on");
		}

		NodeEditor.setStatus(key, status);
		return reply(context.getSource(), "Node " + key + " is now " + status);
	}

	private static int fill(CommandContext<FabricClientCommandSource> context) {
		if (noPlan(context.getSource())) {
			return 0;
		}

		NodeStatus status;

		try {
			status = NodeStatus.valueOf(StringArgumentType.getString(context, "status").toUpperCase());
		} catch (IllegalArgumentException e) {
			return error(context.getSource(), "Status must be pending, excluded or removed");
		}

		PerimeterPlan plan = PlanManager.plan();
		int fromI = IntegerArgumentType.getInteger(context, "fromI");
		int fromJ = IntegerArgumentType.getInteger(context, "fromJ");
		int toI = IntegerArgumentType.getInteger(context, "toI");
		int toJ = IntegerArgumentType.getInteger(context, "toJ");
		java.util.List<NodeKey> keys = new java.util.ArrayList<>();

		for (int i = Math.min(fromI, toI); i <= Math.max(fromI, toI); i++) {
			for (int j = Math.min(fromJ, toJ); j <= Math.max(fromJ, toJ); j++) {
				if (plan.extents().contains(i, j)) {
					keys.add(new NodeKey(i, j));
				}
			}
		}

		PlanHistory.record(plan, keys, Lang.t("map.undo_area"));

		for (NodeKey key : keys) {
			plan.setStatus(key, status);
		}

		int changed = keys.size();

		PlanManager.markDirty();
		ScanCache.clear();
		return reply(context.getSource(), changed + " nodes set to " + status);
	}

	private static int easyPlace(CommandContext<FabricClientCommandSource> context, boolean on) {
		BeaconatorConfig config = BeaconatorConfig.get();
		config.easyPlace = on;
		config.save();
		return reply(context.getSource(), on
				? "Easy place on: the right block gets picked for you, and plan blocks will not go anywhere the plan does not ask for"
				: "Easy place off");
	}

	private static int importSchematic(CommandContext<FabricClientCommandSource> context, String file) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null) {
			return 0;
		}

		Path path = SchematicFiles.resolve(file);

		if (!Files.isRegularFile(path)) {
			return error(context.getSource(), "No such file: " + path.getFileName());
		}

		PerimeterPlan plan;
		boolean placedByHand;

		try {
			// Schematics carry no world coordinates. Ours remember the corner they came from;
			// anything else lands where the player is standing, the way you would paste it.
			int[] fallback = mc.player == null ? null
					: new int[] {mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()};
			placedByHand = !LitematicIO.hasOrigin(path);
			plan = LitematicIO.read(path, SchematicFiles.stripExtension(path.getFileName().toString()),
					mc.level.dimension().location().toString(), fallback);
		} catch (IOException | RuntimeException e) {
			BeaconatorClient.LOGGER.warn("Import failed", e);
			return error(context.getSource(), "Could not read it: " + e.getMessage());
		}

		PlanManager.autoSave();
		PlanManager.setPlan(plan);
		ScanCache.clear();
		PlanManager.save();

		reply(context.getSource(), "Imported \"" + plan.name() + "\": "
				+ plan.extents().columns() + " x " + plan.extents().rows() + " nodes, "
				+ plan.beaconsPerNode() + " beacons each, spacing " + plan.spacing()
				+ ", level " + plan.level() + " " + shortName(plan.pyramidBlock()) + " pyramids");

		if (plan.countByStatus(NodeStatus.EXCLUDED) > 0) {
			reply(context.getSource(), plan.countByStatus(NodeStatus.EXCLUDED)
					+ " nodes came in marked as excluded, "
					+ plan.countByStatus(NodeStatus.REMOVED) + " grid slots were empty");
		}

		if (placedByHand) {
			reply(context.getSource(), "Schematics carry no world coordinates, so it was dropped at "
					+ "your feet. Line it up with /bea move <dx> <dy> <dz> or /bea center <x> <y> <z>.");
		}

		return 1;
	}

	private static int move(CommandContext<FabricClientCommandSource> context) {
		if (noPlan(context.getSource())) {
			return 0;
		}

		PerimeterPlan plan = PlanManager.plan();
		plan.setCenter(
				plan.centerX() + IntegerArgumentType.getInteger(context, "dx"),
				plan.beaconY() + IntegerArgumentType.getInteger(context, "dy"),
				plan.centerZ() + IntegerArgumentType.getInteger(context, "dz"));
		PlanManager.markDirty();
		ScanCache.clear();
		return reply(context.getSource(), "Centre at "
				+ plan.centerX() + ", " + plan.beaconY() + ", " + plan.centerZ());
	}

	private static int exportSchematic(CommandContext<FabricClientCommandSource> context, String file) {
		if (noPlan(context.getSource())) {
			return 0;
		}

		PerimeterPlan plan = PlanManager.plan();
		Minecraft mc = Minecraft.getInstance();
		Path path = SchematicFiles.resolve(file == null ? plan.name() : file);

		try {
			LitematicIO.write(path, plan, mc.player == null ? "" : mc.player.getGameProfile().getName());
		} catch (IOException | RuntimeException e) {
			BeaconatorClient.LOGGER.warn("Export failed", e);
			return error(context.getSource(), "Could not write it: " + e.getMessage());
		}

		int[] bounds = plan.schematicBounds();
		return reply(context.getSource(), "Wrote schematics/" + path.getFileName() + " ("
				+ (bounds[3] - bounds[0] + 1) + " x " + (bounds[4] - bounds[1] + 1)
				+ " x " + (bounds[5] - bounds[2] + 1) + ", "
				+ String.format("%,d", plan.tally().total()) + " blocks)");
	}

	// ---------------------------------------------------------------- helpers

	private static String gapNote(PerimeterPlan plan) {
		int gap = Math.max(plan.gapAcrossRow(), plan.gapAlongRow());

		if (gap > 0) {
			return " (" + gap + " blocks with no coverage)";
		}

		if (gap < 0) {
			return " (" + (-gap) + " blocks of overlap)";
		}

		return " (exact fit)";
	}

	private static String shortName(String blockId) {
		int colon = blockId.indexOf(':');
		return colon < 0 ? blockId : blockId.substring(colon + 1);
	}

	private static boolean noPlan(FabricClientCommandSource source) {
		if (PlanManager.hasPlan()) {
			return false;
		}

		error(source, "No plan open. Use /bea new <name> or /bea open <name>");
		return true;
	}

	private static Component header(String text) {
		return Component.literal(text).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
	}

	private static Component line(String label, String value) {
		return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(value).withStyle(ChatFormatting.WHITE));
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
