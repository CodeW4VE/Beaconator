package xyz.w4ve.beaconator.net;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import xyz.w4ve.beaconator.BeaconatorMod;

/**
 * What the server has on offer, sent on join and again whenever the list changes.
 *
 * <p>Names and sizes only. The plans themselves are hundreds of kilobytes each and nobody wants
 * all of them, so the list is cheap and you pull the one you are working on.
 *
 * @param entries one per shared plan
 */
public record PlanListPayload(List<Entry> entries) implements CustomPacketPayload {
	/**
	 * @param name   the plan's name
	 * @param nodes  how many nodes it has, so the list can say something useful about it
	 * @param author who put it up there
	 */
	public record Entry(String name, int nodes, String author) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(64), Entry::name,
				ByteBufCodecs.VAR_INT, Entry::nodes,
				ByteBufCodecs.stringUtf8(64), Entry::author,
				Entry::new);
	}

	public static final CustomPacketPayload.Type<PlanListPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BeaconatorMod.MOD_ID, "plan_list"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PlanListPayload> CODEC = StreamCodec.composite(
			Entry.CODEC.apply(ByteBufCodecs.list(256)), PlanListPayload::entries,
			PlanListPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
