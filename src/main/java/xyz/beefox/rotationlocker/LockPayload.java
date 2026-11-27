package xyz.beefox.rotationlocker;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Wird vom Client an den (integrierten) Server geschickt, damit der Server weiss,
 * in welche Richtung dieser Spieler gerade sperrt. -1 = keine Sperre.
 */
public record LockPayload(int dir) implements CustomPayload {
	public static final CustomPayload.Id<LockPayload> ID =
			new CustomPayload.Id<>(Identifier.of(RotationLocker.MOD_ID, "lock"));
	public static final PacketCodec<ByteBuf, LockPayload> CODEC =
			PacketCodecs.VAR_INT.xmap(LockPayload::new, LockPayload::dir);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
