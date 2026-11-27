package xyz.beefox.rotationlocker;

import net.fabricmc.fabric.api.tag.client.v1.ClientTags;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;



public class RotationLocker implements ModInitializer {

	public static final TagKey<Item> LOCK_RESETERS = TagKey.of(
			RegistryKeys.ITEM,
			Identifier.of("rotationlocker", "lock_reseters")
	);

	public static final String MOD_ID = "rotationlocker";
	public static final Logger LOGGER = LoggerFactory.getLogger("rotationlocker");

	private static KeyBinding rotationLockKeyBinding;
	@Nullable
	public static Direction lockDirection;
	private static Boolean enableKey;

	/** Server-Seite: welche Sperre hat welcher Spieler (per UUID). */
	private static final Map<UUID, Direction> SERVER_LOCKS = new ConcurrentHashMap<>();
	private static Integer lastSent = null;

	/**
	 * Gibt die Sperr-Richtung des angegebenen Spielers zurueck.
	 * Server-Seite: aus der Map (vom Client per Paket gemeldet).
	 * Client-Seite: nur fuer den lokalen Spieler.
	 */
	@Nullable
	public static Direction getLockFor(@Nullable PlayerEntity player) {
		if (player == null) return null;
		if (player instanceof ServerPlayerEntity) {
			Direction d = SERVER_LOCKS.get(player.getUuid());
			if (d != null) return d;
			// Fallback fuer den Host, falls das Paket noch nicht angekommen ist
			PlayerEntity local = MinecraftClient.getInstance().player;
			if (lockDirection != null && local != null && local.getUuid().equals(player.getUuid())) return lockDirection;
			return null;
		}
		if (lockDirection == null) return null;
		PlayerEntity local = MinecraftClient.getInstance().player;
		if (local == null || !local.getUuid().equals(player.getUuid())) return null;
		return lockDirection;
	}

	@Override
	public void onInitialize() {

		PayloadTypeRegistry.playC2S().register(LockPayload.ID, LockPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(LockPayload.ID, (payload, context) -> {
			UUID id = context.player().getUuid();
			if (payload.dir() < 0 || payload.dir() >= Direction.values().length) {
				SERVER_LOCKS.remove(id);
			} else {
				SERVER_LOCKS.put(id, Direction.values()[payload.dir()]);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> lastSent = null);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;
			int cur = lockDirection == null ? -1 : lockDirection.ordinal();
			if ((lastSent == null || lastSent != cur) && ClientPlayNetworking.canSend(LockPayload.ID)) {
				ClientPlayNetworking.send(new LockPayload(cur));
				lastSent = cur;
			}
		});

		rotationLockKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.rotationlocker.lock",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_K,
				KeyBinding.Category.create(Identifier.of(MOD_ID))));

		enableKey = true;
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
					if(client.player != null) {
						if (rotationLockKeyBinding.isPressed() && enableKey == true) {
							enableKey = false;
							if(lockDirection == null){
								lockDirection = Direction.getEntityFacingOrder(client.player)[0];
								client.player.sendMessage(Text.translatable("text.rotationlocker.lock", lockDirection.asString()).formatted(Formatting.WHITE), false);
							} else {
								if(client.options.sneakKey.isPressed()){
									lockDirection = lockDirection.getOpposite();
									client.player.sendMessage(Text.translatable("text.rotationlocker.flip", lockDirection.asString()).formatted(Formatting.GRAY), false);
								} else {
									lockDirection = null;
									client.player.sendMessage(Text.translatable("text.rotationlocker.unlock").formatted(Formatting.WHITE), false);
								}
							}
						}
						if (!rotationLockKeyBinding.isPressed() && enableKey == false){
							enableKey = true;
						}

						PlayerEntity player = MinecraftClient.getInstance().player;

						if(player != null) {

							if(lockDirection == Direction.UP && player.isHolding(stack -> ClientTags.isInWithLocalFallback(LOCK_RESETERS, stack.getRegistryEntry()))) {
								lockDirection = null;
								client.player.sendMessage(Text.translatable("text.rotationlocker.invalidlock").formatted(Formatting.DARK_RED), false);
							}
							if(lockDirection == Direction.DOWN && player.isHolding(stack -> ClientTags.isInWithLocalFallback(LOCK_RESETERS, stack.getRegistryEntry()))) {
								lockDirection = null;
								client.player.sendMessage(Text.translatable("text.rotationlocker.invalidlock").formatted(Formatting.DARK_RED), false);
							}
						}
					}
				}
		);
	}
}