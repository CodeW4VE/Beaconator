package xyz.w4ve.beaconator.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import xyz.w4ve.beaconator.BeaconatorMod;

/**
 * A whole shared plan, as the JSON the plan files already use.
 *
 * <p>Server to client on join and whenever the plan is replaced; client to server to publish one.
 * An empty {@code json} means there is no shared plan, which is how the server says "I have
 * nothing" and how a client asks for the plan to be taken down.
 *
 * <p>A client without the mod never registers the channel and is never sent anything, the same
 * scheme Servux uses.
 */
public record PlanPayload(String json) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PlanPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BeaconatorMod.MOD_ID, "plan"));

	/**
	 * A perimeter of a couple of thousand nodes is a big string, so the cap is generous. It is
	 * still a cap: an unbounded string on the wire is how a client crashes a server.
	 */
	public static final StreamCodec<RegistryFriendlyByteBuf, PlanPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(1 << 20), PlanPayload::json, PlanPayload::new);

	public static PlanPayload none() {
		return new PlanPayload("");
	}

	public boolean isEmpty() {
		return json.isBlank();
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
