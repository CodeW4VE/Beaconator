package xyz.w4ve.beaconator.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import xyz.w4ve.beaconator.client.scan.ScanCache;
import xyz.w4ve.beaconator.model.GridNode;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;

/**
 * Turns clicks into node state changes while edit mode is on.
 *
 * <p>Left click drops a node from the plan, right click marks it as outside the perimeter.
 * Both toggle back. Clicks that do not land on a node are left alone, so you can still look
 * up and break something.
 */
public final class NodeEditor {
	private NodeEditor() {
	}

	/** @return true when the click was used for editing and must not reach the game */
	public static boolean onLeftClick() {
		NodeKey key = target();

		if (key == null) {
			return false;
		}

		PerimeterPlan plan = PlanManager.plan();
		PlanHistory.record(plan, key, "node");
		NodeStatus status = plan.toggleRemoved(key);
		announce(plan, key, status);
		return true;
	}

	/** @return true when the click was used for editing and must not reach the game */
	public static boolean onRightClick() {
		NodeKey key = target();

		if (key == null) {
			return false;
		}

		PerimeterPlan plan = PlanManager.plan();
		PlanHistory.record(plan, key, "node");
		NodeStatus status = plan.toggleExcluded(key);
		announce(plan, key, status);
		return true;
	}

	public static void setStatus(NodeKey key, NodeStatus status) {
		PerimeterPlan plan = PlanManager.plan();

		if (plan == null || key == null) {
			return;
		}

		PlanHistory.record(plan, key, "node");
		plan.setStatus(key, status);
		announce(plan, key, status);
	}

	private static NodeKey target() {
		if (!PlanManager.editMode() || !PlanManager.inPlanDimension()) {
			return null;
		}

		return PlanManager.hovered();
	}

	private static void announce(PerimeterPlan plan, NodeKey key, NodeStatus status) {
		PlanManager.markDirty();

		if (status == NodeStatus.REMOVED) {
			ScanCache.clear();
		}

		GridNode node = plan.nodeAt(key);
		ChatFormatting color = switch (status) {
			case PENDING -> ChatFormatting.WHITE;
			case PLACED -> ChatFormatting.GREEN;
			case EXCLUDED -> ChatFormatting.DARK_GRAY;
			case REMOVED -> ChatFormatting.RED;
		};

		String note = switch (status) {
			case EXCLUDED -> plan.placeMarker()
					? " (still built, topped with " + shortName(plan.markerBlock()) + ")"
					: " (still built, marked here only)";
			case REMOVED -> " (dropped from the plan)";
			default -> "";
		};

		PlanManager.actionBar(Component.literal("Node " + key + " at " + node.x() + ", " + node.z() + ": ")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal(status.name()).withStyle(color))
				.append(Component.literal(note).withStyle(ChatFormatting.DARK_GRAY)));
	}

	private static String shortName(String blockId) {
		int colon = blockId.indexOf(':');
		return colon < 0 ? blockId : blockId.substring(colon + 1);
	}
}
