package com.donutauction;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class AuctionMod implements ClientModInitializer {
	public static final String MOD_ID = "donutauction";

	public static KeyBinding openKey;

	@Override
	public void onInitializeClient() {
		AuctionConfig.load();

		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.donutauction.open",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_BACKSLASH,
			KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.wasPressed()) {
				if (client.currentScreen == null && client.player != null) {
					client.setScreen(new AuctionScreen());
				}
			}
			AuctionManager.INSTANCE.tick(client);
		});

		// Bids arrive as either system messages or signed chat depending on how the server sends them.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) AuctionManager.INSTANCE.onMessage(message.getString());
		});
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, receptionTimestamp) ->
			AuctionManager.INSTANCE.onMessage(message.getString()));

		AuctionHud.register();

		// /auction cancel  and  /auction bid <name> <amount>  (handy for testing without viewers)
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(ClientCommandManager.literal("auction")
				.then(ClientCommandManager.literal("cancel").executes(ctx -> {
					AuctionManager.INSTANCE.cancel();
					ctx.getSource().sendFeedback(Text.literal("[Auction] Cancelled."));
					return 1;
				}))
				.then(ClientCommandManager.literal("bid")
					.then(ClientCommandManager.argument("name", StringArgumentType.word())
						.then(ClientCommandManager.argument("amount", StringArgumentType.word())
							.executes(ctx -> {
								String name = StringArgumentType.getString(ctx, "name");
								double amt = BidParser.parseAmount(StringArgumentType.getString(ctx, "amount"));
								if (Double.isNaN(amt)) {
									ctx.getSource().sendFeedback(Text.literal("[Auction] Invalid amount."));
									return 0;
								}
								boolean ok = AuctionManager.INSTANCE.placeBid(name, amt);
								ctx.getSource().sendFeedback(Text.literal(ok
									? "[Auction] Bid accepted: " + name + " " + Ui.money(amt)
									: "[Auction] Bid rejected (no auction, under minimum, or not higher)."));
								return ok ? 1 : 0;
							}))))));
	}
}
