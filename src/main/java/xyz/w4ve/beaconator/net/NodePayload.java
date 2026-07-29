package xyz.w4ve.beaconator.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import xyz.w4ve.beaconator.BeaconatorMod;
import xyz.w4ve.beaconator.model.NodeStatus;

/**
 * One node changing state, both ways: a client reporting what it just placed or excluded, and the
 * server passing that on to everyone else.
 *
 * <p>Sending the node rather than the whole plan is what makes this usable while several people
 * dig at once. A perimeter is thousands of nodes and the plan JSON is hundreds of kilobytes.
 *
 * @param plan   which shared plan this is about, so two crews on two perimeters do not edit each
 *               other's nodes
 * @param i      node column in the grid
 * @param j      node row in the grid
 * @param status index into {@link NodeStatus}
 */
public record NodePayload(String plan, int i, int j, int status) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<NodePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BeaconatorMod.MOD_ID, "node"));

	public static final StreamCodec<RegistryFriendlyByteBuf, NodePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), NodePayload::plan,
			ByteBufCodecs.VAR_INT, NodePayload::i,
			ByteBufCodecs.VAR_INT, NodePayload::j,
			ByteBufCodecs.VAR_INT, NodePayload::status,
			NodePayload::new);

	public NodePayload(String plan, int i, int j, NodeStatus status) {
		this(plan, i, j, status.ordinal());
	}

	/** Null when the number on the wire is not a state we know, rather than an exception. */
	public NodeStatus state() {
		NodeStatus[] all = NodeStatus.values();
		return status >= 0 && status < all.length ? all[status] : null;
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
