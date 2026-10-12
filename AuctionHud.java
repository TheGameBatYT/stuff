package com.donutauction;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** The sleek bar at the top of the screen. */
public final class AuctionHud {
	private AuctionHud() {}

	private static final int W = 250;
	private static final int H = 44;

	public static void register() {
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.MISC_OVERLAYS,
			Identifier.of(AuctionMod.MOD_ID, "auction_bar"),
			AuctionHud::render
		);
	}

	private static void render(DrawContext c, RenderTickCounter tickCounter) {
		AuctionManager a = AuctionManager.INSTANCE;
		if (a.state == AuctionManager.State.IDLE) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.options.hudHidden) return;
		TextRenderer tr = mc.textRenderer;

		long now = System.currentTimeMillis();
		boolean ended = a.state == AuctionManager.State.ENDED;
		int x = (c.getScaledWindowWidth() - W) / 2;
		int y = 8;

		Ui.roundedBordered(c, x, y, W, H, Ui.BLACK, Ui.BORDER);

		// Item tile
		Ui.rounded(c, x + 8, y + 8, 24, 24, 0xFF141414);
		c.drawItem(a.stack, x + 12, y + 12);

		// Title
		String title = tr.trimToWidth(a.stack.getName().getString(), 130);
		c.drawText(tr, title, x + 40, y + 9, Ui.WHITE, false);

		// Second line: top bid + bidder
		int line2 = y + 22;
		boolean flash = !ended && now - a.lastBidMs < 700 && a.topBidder != null;
		if (a.topBidder == null) {
			String s = ended ? "No bids" : "No bids yet  \u00b7  min " + Ui.money(a.minPrice);
			c.drawText(tr, s, x + 40, line2, Ui.GRAY, false);
		} else {
			String amt = Ui.money(a.topBid);
			int amtColor = ended ? Ui.GOLD : (flash ? Ui.WHITE : Ui.ACCENT);
			c.drawText(tr, amt, x + 40, line2, amtColor, false);
			int nx = x + 40 + tr.getWidth(amt) + 6;
			String name = tr.trimToWidth(a.topBidder, x + W - 70 - nx);
			c.drawText(tr, name, nx, line2, ended ? Ui.GOLD : Ui.WHITE, false);
		}

		// Right side: countdown or result tag
		String right;
		int rightColor;
		if (ended) {
			right = a.topBidder == null ? "ENDED" : "SOLD";
			rightColor = a.topBidder == null ? Ui.GRAY : Ui.GOLD;
		} else {
			long rem = a.remainingMs();
			right = Ui.time(rem);
			rightColor = rem <= 10_000 ? ((now / 400) % 2 == 0 ? Ui.RED : Ui.WHITE) : Ui.WHITE;
		}
		Text rightText = Text.literal(right).formatted(Formatting.BOLD);
		c.drawText(tr, rightText, x + W - 10 - tr.getWidth(rightText), y + 14, rightColor, false);

		// Progress bar
		int bx = x + 10, bw = W - 20, by = y + H - 8;
		c.fill(bx, by, bx + bw, by + 3, 0xFF1F1F1F);
		float p = ended ? 1f : a.progress();
		int barColor = ended ? Ui.GOLD : Ui.lerpColor(Ui.RED, Ui.ACCENT, Math.min(1f, p * 2f));
		int fillW = Math.round(bw * p);
		if (fillW > 0) c.fill(bx, by, bx + fillW, by + 3, barColor);
	}
}
