package xyz.w4ve.beaconator.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import xyz.w4ve.beaconator.BeaconatorMod;

/** "Send me this one." The server answers with a {@link PlanPayload}. */
public record PlanRequestPayload(String name) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PlanRequestPayload> TYPE =
			new CustomPacketPayload.Type<>(
					ResourceLocation.fromNamespaceAndPath(BeaconatorMod.MOD_ID, "plan_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PlanRequestPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.stringUtf8(64), PlanRequestPayload::name, PlanRequestPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
