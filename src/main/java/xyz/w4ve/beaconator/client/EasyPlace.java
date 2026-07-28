package xyz.w4ve.beaconator.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import xyz.w4ve.beaconator.client.scan.WorldScanner;
import xyz.w4ve.beaconator.config.BeaconatorConfig;
import xyz.w4ve.beaconator.model.PerimeterPlan;

/**
 * Assisted placement.
 *
 * <p>Rather than faking placement packets, this picks the right block out of your hotbar for
 * whatever spot you are aiming at and refuses to let you place a schematic block anywhere the
 * plan does not ask for one. Vanilla still does the actual placing, so there is nothing here a
 * server could object to and nothing to keep in sync with how placement works.
 */
public final class EasyPlace {
	private static String wantedBlock;
	private static BlockPos wantedPos;

	private EasyPlace() {
	}

	public static String wantedBlock() {
		return wantedBlock;
	}

	public static BlockPos wantedPos() {
		return wantedPos;
	}

	public static void tick(Minecraft mc) {
		wantedBlock = null;
		wantedPos = null;

		BeaconatorConfig config = BeaconatorConfig.get();
		PerimeterPlan plan = PlanManager.plan();

		if (!config.easyPlace || plan == null || mc.player == null || mc.level == null
				|| PlanManager.editMode() || !PlanManager.inPlanDimension()) {
			return;
		}

		BlockPos target = targetPosition(mc);

		if (target == null) {
			return;
		}

		String wanted = plan.blockAt(target.getX(), target.getY(), target.getZ());

		if (wanted == null || !LayerFilter.allows(target.getY())) {
			return;
		}

		if (!mc.level.getBlockState(target).canBeReplaced()) {
			return;
		}

		wantedBlock = wanted;
		wantedPos = target;
		selectInHotbar(mc, wanted);
	}

	/** Where the block the player is about to place would land. */
	private static BlockPos targetPosition(Minecraft mc) {
		if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
			return null;
		}

		BlockHitResult hit = (BlockHitResult) mc.hitResult;
		return hit.getBlockPos().relative(hit.getDirection());
	}

	private static void selectInHotbar(Minecraft mc, String blockId) {
		Block block = WorldScanner.block(blockId);

		if (block == null || mc.player == null) {
			return;
		}

		var inventory = mc.player.getInventory();

		if (inventory.getSelected().is(block.asItem())) {
			return;
		}

		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (stack.is(block.asItem())) {
				inventory.selected = slot;
				return;
			}
		}
	}

	/**
	 * Whether a right click should be blocked: the player is holding one of the plan's blocks
	 * but aiming somewhere the plan wants nothing. Keeps the perimeter clean without getting in
	 * the way of doors, chests or eating.
	 */
	public static boolean shouldBlockPlacement(Minecraft mc) {
		BeaconatorConfig config = BeaconatorConfig.get();
		PerimeterPlan plan = PlanManager.plan();

		if (!config.easyPlace || !config.strictPlacement || plan == null || mc.player == null
				|| mc.level == null || PlanManager.editMode() || !PlanManager.inPlanDimension()) {
			return false;
		}

		ItemStack held = mc.player.getMainHandItem();

		if (held.isEmpty() || !isPlanBlock(plan, held)) {
			return false;
		}

		BlockPos target = targetPosition(mc);

		if (target == null) {
			return false;
		}

		if (!LayerFilter.allows(target.getY())) {
			return true;
		}

		return plan.blockAt(target.getX(), target.getY(), target.getZ()) == null;
	}

	private static boolean isPlanBlock(PerimeterPlan plan, ItemStack stack) {
		return matches(stack, plan.pyramidBlock())
				|| matches(stack, PerimeterPlan.BEACON_BLOCK)
				|| matches(stack, plan.markerBlock());
	}

	private static boolean matches(ItemStack stack, String blockId) {
		Block block = WorldScanner.block(blockId);
		return block != null && stack.is(block.asItem());
	}
}
