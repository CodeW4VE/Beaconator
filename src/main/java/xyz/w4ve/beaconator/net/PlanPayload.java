package xyz.w4ve.beaconator.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import xyz.w4ve.beaconator.BeaconatorMod;

/**
 * One shared plan, as the JSON the plan files already use.
 *
 * <p>Server to client when you ask for a plan from the list; client to server to put one up there.
 * An empty {@code json} going up means "take this one down".
 *
 * <p>A client without the mod never registers the channel and is never sent anything, the same
 * scheme Servux uses.
 *
 * @param name the plan's name, which is also how it is filed on the server
 * @param json the plan itself, empty to delete
 */
public record PlanPayload(String name, String json) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PlanPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BeaconatorMod.MOD_ID, "plan"));

	/**
	 * A perimeter of a couple of thousand nodes is a big string, so the cap is generous. It is
	 * still a cap: an unbounded string on the wire is how a client crashes a server.
	 */
	public static final StreamCodec<RegistryFriendlyByteBuf, PlanPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), PlanPayload::name,
			ByteBufCodecs.stringUtf8(1 << 20), PlanPayload::json,
			PlanPayload::new);

	public boolean isDelete() {
		return json.isBlank();
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
