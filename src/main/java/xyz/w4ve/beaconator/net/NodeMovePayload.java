package xyz.w4ve.beaconator.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import xyz.w4ve.beaconator.BeaconatorMod;

/**
 * One node being nudged off the grid, both ways, the same shape as {@link NodePayload}.
 *
 * <p>Kept apart from the state packet on purpose: a node changes state constantly while a crew
 * digs, and is moved once, when someone works out it does not fit. Folding both into one packet
 * would put two coordinates on the wire thousands of times to save a class.
 *
 * @param plan which shared plan this is about
 * @param i    node column in the grid
 * @param j    node row in the grid
 * @param dx   blocks east of where the grid puts it
 * @param dz   blocks south of where the grid puts it
 */
public record NodeMovePayload(String plan, int i, int j, int dx, int dz) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<NodeMovePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BeaconatorMod.MOD_ID, "node_move"));

	public static final StreamCodec<RegistryFriendlyByteBuf, NodeMovePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), NodeMovePayload::plan,
			ByteBufCodecs.VAR_INT, NodeMovePayload::i,
			ByteBufCodecs.VAR_INT, NodeMovePayload::j,
			ByteBufCodecs.VAR_INT, NodeMovePayload::dx,
			ByteBufCodecs.VAR_INT, NodeMovePayload::dz,
			NodeMovePayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
