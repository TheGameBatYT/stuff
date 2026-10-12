package com.donutauction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class AuctionManager {
	public static final AuctionManager INSTANCE = new AuctionManager();

	public enum State { IDLE, ACTIVE, ENDED }

	public static final long RESULT_SHOW_MS = 10_000;

	public State state = State.IDLE;
	public ItemStack stack = ItemStack.EMPTY;
	public double minPrice;
	public long durationMs;
	public long endMs;
	public long endedAtMs;
	public String topBidder = null;
	public double topBid = 0;
	public int bidCount = 0;
	public long lastBidMs = 0;

	private AuctionManager() {}

	public void start(ItemStack item, long durationMs, double minPrice) {
		this.stack = item.copy();
		this.durationMs = durationMs;
		this.minPrice = minPrice;
		this.endMs = System.currentTimeMillis() + durationMs;
		this.topBidder = null;
		this.topBid = 0;
		this.bidCount = 0;
		this.lastBidMs = 0;
		this.state = State.ACTIVE;
	}

	public void cancel() {
		state = State.IDLE;
	}

	public long remainingMs() {
		return Math.max(0, endMs - System.currentTimeMillis());
	}

	/** 1.0 = just started, 0.0 = over. */
	public float progress() {
		if (state != State.ACTIVE || durationMs <= 0) return 0f;
		return Math.max(0f, Math.min(1f, remainingMs() / (float) durationMs));
	}

	public boolean isActive() {
		return state == State.ACTIVE;
	}

	/** Returns true if the bid was accepted as the new top bid. */
	public boolean placeBid(String name, double amount) {
		if (state != State.ACTIVE) return false;
		if (amount < minPrice) return false;
		if (amount <= topBid) return false;
		topBidder = name;
		topBid = amount;
		bidCount++;
		long now = System.currentTimeMillis();
		lastBidMs = now;

		// Anti-snipe: a late bid pushes the clock back up to the configured window.
		long window = AuctionConfig.INSTANCE.antiSnipeSeconds * 1000L;
		if (window > 0 && endMs - now < window) {
			endMs = now + window;
			if (durationMs < window) durationMs = window;
		}
		return true;
	}

	public void onMessage(String line) {
		if (state != State.ACTIVE) return;
		BidParser.Bid bid = BidParser.parse(line);
		if (bid != null) placeBid(bid.name(), bid.amount());
	}

	public void tick(MinecraftClient client) {
		long now = System.currentTimeMillis();
		if (state == State.ACTIVE && now >= endMs) {
			state = State.ENDED;
			endedAtMs = now;
			if (client.player != null) {
				Text msg;
				if (topBidder == null) {
					msg = Text.literal("[Auction] ").formatted(Formatting.GREEN)
						.append(Text.literal(stack.getName().getString() + " ended with no bids.").formatted(Formatting.GRAY));
				} else {
					msg = Text.literal("[Auction] ").formatted(Formatting.GREEN)
						.append(Text.literal(stack.getName().getString() + " sold to ").formatted(Formatting.GRAY))
						.append(Text.literal(topBidder).formatted(Formatting.WHITE, Formatting.BOLD))
						.append(Text.literal(" for ").formatted(Formatting.GRAY))
						.append(Text.literal(Ui.money(topBid)).formatted(Formatting.GREEN));
				}
				client.player.sendMessage(msg, false);
			}
		} else if (state == State.ENDED && now - endedAtMs > RESULT_SHOW_MS) {
			state = State.IDLE;
		}
	}
}
