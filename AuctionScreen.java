package com.donutauction;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AuctionScreen extends Screen {
	private enum Stage { SEARCH, CONFIG, ACTIVE }

	private record Entry(ItemStack stack, String name, String nameLower, String id) {}

	private static List<Entry> ALL = null;

	private static List<Entry> allItems() {
		if (ALL == null) {
			List<Entry> list = new ArrayList<>();
			for (Item item : Registries.ITEM) {
				if (item == net.minecraft.item.Items.AIR) continue;
				ItemStack st = new ItemStack(item);
				String name = st.getName().getString();
				list.add(new Entry(st, name, name.toLowerCase(), Registries.ITEM.getId(item).getPath()));
			}
			list.sort(Comparator.comparing(Entry::nameLower));
			ALL = list;
		}
		return ALL;
	}

	// Layout
	private static final int PW = 330, PH = 250;
	private static final int COLS = 9, CELL = 32, ROWS = 5;
	private static final int[] CHIP_SECONDS = {30, 60, 120, 300, 600, 1800};
	private static final String[] CHIP_LABELS = {"30s", "1m", "2m", "5m", "10m", "30m"};

	private int px, py;
	private Stage stage;

	// State kept across resizes
	private String searchText = "";
	private String customDuration = "";
	private String priceText = "";
	private int chip = 1;
	private int scrollRows = 0;
	private Entry selected = null;
	private String error = null;
	private List<Entry> filtered = new ArrayList<>();

	private TextFieldWidget searchField, durationField, priceField;

	public AuctionScreen() {
		super(Text.literal("Auction"));
		stage = AuctionManager.INSTANCE.isActive() ? Stage.ACTIVE : Stage.SEARCH;
		refilter();
	}

	@Override
	protected void init() {
		px = (width - PW) / 2;
		py = (height - PH) / 2;

		searchField = makeField(px + 20, py + 28, PW - 40, "Search any item...", searchText, s -> {
			searchText = s;
			scrollRows = 0;
			refilter();
		});
		durationField = makeField(px + 18, py + 108, PW - 36, "Custom duration  (90s, 15m, 1h)", customDuration, s -> {
			customDuration = s;
			error = null;
		});
		priceField = makeField(px + 18, py + 152, PW - 36, "e.g. 5k, 1.5m, 250000", priceText, s -> {
			priceText = s;
			error = null;
		});
		applyStage();
	}

	private TextFieldWidget makeField(int x, int y, int w, String placeholder, String initial, java.util.function.Consumer<String> onChange) {
		TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(placeholder));
		f.setDrawsBackground(false);
		f.setMaxLength(32);
		f.setPlaceholder(Text.literal(placeholder).formatted(Formatting.DARK_GRAY));
		f.setText(initial);
		f.setChangedListener(onChange);
		addDrawableChild(f);
		return f;
	}

	private void applyStage() {
		searchField.setVisible(stage == Stage.SEARCH);
		durationField.setVisible(stage == Stage.CONFIG);
		priceField.setVisible(stage == Stage.CONFIG);
		if (stage == Stage.SEARCH) setInitialFocus(searchField);
		else if (stage == Stage.CONFIG) setInitialFocus(priceField);
		else setFocused(null);
	}

	private void setStage(Stage s) {
		stage = s;
		error = null;
		applyStage();
	}

	private void refilter() {
		String q = searchText.toLowerCase().trim();
		List<Entry> out = new ArrayList<>();
		for (Entry e : allItems()) {
			if (q.isEmpty() || e.nameLower.contains(q) || e.id.contains(q.replace(' ', '_'))) out.add(e);
		}
		if (!q.isEmpty()) {
			out.sort(Comparator.comparing((Entry e) -> !e.nameLower.startsWith(q)).thenComparing(Entry::nameLower));
		}
		filtered = out;
	}

	private int maxScroll() {
		int rows = (filtered.size() + COLS - 1) / COLS;
		return Math.max(0, rows - ROWS);
	}

	private int gridX() { return px + (PW - COLS * CELL) / 2; }
	private int gridY() { return py + 58; }

	// ------------------------------------------------------------------ rendering

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
		// Intentionally empty: we paint our own backdrop (no vanilla blur).
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		c.fill(0, 0, width, height, 0xB0000000);
		Ui.roundedBordered(c, px, py, PW, PH, Ui.BLACK, Ui.BORDER);

		switch (stage) {
			case SEARCH -> renderSearch(c, mx, my);
			case CONFIG -> renderConfig(c, mx, my);
			case ACTIVE -> renderActive(c, mx, my);
		}
		super.render(c, mx, my, delta);
	}

	private void renderSearch(DrawContext c, int mx, int my) {
		c.drawText(textRenderer, "NEW AUCTION", px + 14, py + 11, Ui.ACCENT, false);
		String count = filtered.size() + " items";
		c.drawText(textRenderer, count, px + PW - 14 - textRenderer.getWidth(count), py + 11, Ui.DIM, false);

		Ui.roundedBordered(c, px + 12, py + 28, PW - 24, 22, Ui.PANEL, Ui.BORDER);

		int gx = gridX(), gy = gridY();
		Entry hovered = null;
		int start = scrollRows * COLS;
		for (int i = 0; i < COLS * ROWS; i++) {
			int idx = start + i;
			if (idx >= filtered.size()) break;
			int cx = gx + (i % COLS) * CELL;
			int cy = gy + (i / COLS) * CELL;
			Entry e = filtered.get(idx);
			boolean hover = Ui.inside(mx, my, cx, cy, CELL, CELL);
			if (hover) {
				hovered = e;
				Ui.roundedBordered(c, cx + 1, cy + 1, CELL - 2, CELL - 2, Ui.HOVER, Ui.ACCENT);
			}
			c.drawItem(e.stack, cx + 8, cy + 8);
		}

		if (filtered.isEmpty()) {
			String s = "No items match \"" + searchText + "\"";
			c.drawText(textRenderer, s, px + (PW - textRenderer.getWidth(s)) / 2, gy + 70, Ui.GRAY, false);
		}

		// Scrollbar
		int max = maxScroll();
		if (max > 0) {
			int trackX = px + PW - 9, trackH = ROWS * CELL;
			c.fill(trackX, gy, trackX + 2, gy + trackH, 0xFF1A1A1A);
			int thumbH = Math.max(14, trackH * ROWS / (ROWS + max));
			int thumbY = gy + (trackH - thumbH) * scrollRows / max;
			c.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, Ui.ACCENT);
		}

		// Footer
		String foot = hovered != null ? hovered.name : "Scroll to browse  \u00b7  click an item to auction it";
		c.drawText(textRenderer, foot, px + 14, py + PH - 20, hovered != null ? Ui.WHITE : Ui.DIM, false);
	}

	private void renderConfig(DrawContext c, int mx, int my) {
		boolean backHover = Ui.inside(mx, my, px + 10, py + 8, 46, 14);
		c.drawText(textRenderer, "\u2039 Back", px + 14, py + 11, backHover ? Ui.WHITE : Ui.GRAY, false);

		// Selected item
		Ui.roundedBordered(c, px + 14, py + 30, 28, 28, Ui.PANEL, Ui.BORDER);
		c.drawItem(selected.stack, px + 20, py + 36);
		c.drawText(textRenderer, textRenderer.trimToWidth(selected.name, PW - 80), px + 52, py + 36, Ui.WHITE, false);
		c.drawText(textRenderer, "minecraft:" + selected.id, px + 52, py + 47, Ui.DIM, false);

		// Duration
		c.drawText(textRenderer, "DURATION", px + 14, py + 70, Ui.GRAY, false);
		boolean custom = !customDuration.trim().isEmpty();
		for (int i = 0; i < CHIP_LABELS.length; i++) {
			int cx = px + 12 + i * 52, cy = py + 82;
			boolean sel = !custom && i == chip;
			boolean hover = Ui.inside(mx, my, cx, cy, 46, 18);
			int fill = sel ? Ui.ACCENT : (hover ? Ui.HOVER : Ui.PANEL);
			int border = sel ? Ui.ACCENT : (hover ? Ui.DIM : Ui.BORDER);
			Ui.roundedBordered(c, cx, cy, 46, 18, fill, border);
			String l = CHIP_LABELS[i];
			c.drawText(textRenderer, l, cx + (46 - textRenderer.getWidth(l)) / 2, cy + 5, sel ? Ui.BLACK : Ui.WHITE, false);
		}
		Ui.roundedBordered(c, px + 12, py + 108, PW - 24, 22, Ui.PANEL, custom ? Ui.ACCENT : Ui.BORDER);

		// Price
		c.drawText(textRenderer, "MINIMUM PRICE", px + 14, py + 140, Ui.GRAY, false);
		Ui.roundedBordered(c, px + 12, py + 152, PW - 24, 22, Ui.PANEL, Ui.BORDER);
		c.drawText(textRenderer, "$", px + 18, py + 158, Ui.ACCENT, false);
		// text field sits just right of the "$"
		priceField.setX(px + 28);
		priceField.setWidth(PW - 46);

		if (error != null) c.drawText(textRenderer, error, px + 14, py + 182, Ui.RED, false);

		// Start button
		boolean startHover = Ui.inside(mx, my, px + 12, py + 204, PW - 24, 30);
		Ui.rounded(c, px + 12, py + 204, PW - 24, 30, startHover ? 0xFF33F0BA : Ui.ACCENT);
		String s = "START AUCTION";
		c.drawText(textRenderer, Text.literal(s).formatted(Formatting.BOLD), px + (PW - textRenderer.getWidth(s) - 8) / 2, py + 216, Ui.BLACK, false);
	}

	private void renderActive(DrawContext c, int mx, int my) {
		AuctionManager a = AuctionManager.INSTANCE;
		c.drawText(textRenderer, "AUCTION IN PROGRESS", px + 14, py + 11, Ui.ACCENT, false);

		Ui.roundedBordered(c, px + 14, py + 40, 36, 36, Ui.PANEL, Ui.BORDER);
		c.drawItem(a.stack, px + 24, py + 50);
		c.drawText(textRenderer, a.stack.getName().getString(), px + 62, py + 46, Ui.WHITE, false);
		c.drawText(textRenderer, a.isActive() ? Ui.time(a.remainingMs()) + " left" : "Finished", px + 62, py + 60, Ui.GRAY, false);

		String top = a.topBidder == null ? "No bids yet  \u00b7  min " + Ui.money(a.minPrice)
			: "Top bid " + Ui.money(a.topBid) + " by " + a.topBidder;
		c.drawText(textRenderer, top, px + 14, py + 100, Ui.WHITE, false);
		c.drawText(textRenderer, a.bidCount + " bid" + (a.bidCount == 1 ? "" : "s"), px + 14, py + 114, Ui.DIM, false);

		boolean h1 = Ui.inside(mx, my, px + 12, py + 170, PW - 24, 28);
		Ui.roundedBordered(c, px + 12, py + 170, PW - 24, 28, h1 ? 0xFF2A0F16 : Ui.PANEL, Ui.RED);
		String cancel = "CANCEL AUCTION";
		c.drawText(textRenderer, cancel, px + (PW - textRenderer.getWidth(cancel)) / 2, py + 180, Ui.RED, false);

		boolean h2 = Ui.inside(mx, my, px + 12, py + 204, PW - 24, 28);
		Ui.roundedBordered(c, px + 12, py + 204, PW - 24, 28, h2 ? Ui.HOVER : Ui.PANEL, Ui.BORDER);
		String close = "CLOSE";
		c.drawText(textRenderer, close, px + (PW - textRenderer.getWidth(close)) / 2, py + 214, Ui.WHITE, false);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (stage == Stage.SEARCH) {
			scrollRows = Math.max(0, Math.min(maxScroll(), scrollRows - (int) Math.signum(verticalAmount)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) return true;
		if (click.button() != 0) return false;
		double mx = click.x(), my = click.y();

		switch (stage) {
			case SEARCH -> {
				int gx = gridX(), gy = gridY();
				if (Ui.inside(mx, my, gx, gy, COLS * CELL, ROWS * CELL)) {
					int col = (int) ((mx - gx) / CELL);
					int row = (int) ((my - gy) / CELL);
					int idx = (scrollRows + row) * COLS + col;
					if (idx >= 0 && idx < filtered.size()) {
						selected = filtered.get(idx);
						setStage(Stage.CONFIG);
						return true;
					}
				}
			}
			case CONFIG -> {
				if (Ui.inside(mx, my, px + 10, py + 8, 46, 14)) {
					setStage(Stage.SEARCH);
					return true;
				}
				for (int i = 0; i < CHIP_LABELS.length; i++) {
					if (Ui.inside(mx, my, px + 12 + i * 52, py + 82, 46, 18)) {
						chip = i;
						durationField.setText("");
						return true;
					}
				}
				if (Ui.inside(mx, my, px + 12, py + 204, PW - 24, 30)) {
					tryStart();
					return true;
				}
			}
			case ACTIVE -> {
				if (Ui.inside(mx, my, px + 12, py + 170, PW - 24, 28)) {
					AuctionManager.INSTANCE.cancel();
					close();
					return true;
				}
				if (Ui.inside(mx, my, px + 12, py + 204, PW - 24, 28)) {
					close();
					return true;
				}
			}
		}
		return false;
	}

	private void tryStart() {
		long ms;
		if (!customDuration.trim().isEmpty()) {
			ms = BidParser.parseDurationMs(customDuration);
			if (ms <= 0) { error = "Duration should look like 90s, 15m or 1h."; return; }
		} else {
			ms = CHIP_SECONDS[chip] * 1000L;
		}
		if (ms > 24L * 3600_000L) { error = "Max duration is 24 hours."; return; }

		double price = BidParser.parseAmount(priceText);
		if (Double.isNaN(price)) { error = "Enter a minimum price like 5k or 1.5m."; return; }

		AuctionManager.INSTANCE.start(selected.stack, ms, price);
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("[Auction] ").formatted(Formatting.GREEN)
				.append(Text.literal("Started " + selected.name + "  \u00b7  " + Ui.time(ms) + "  \u00b7  min " + Ui.money(price)
					+ ". Viewers bid with \"bid <amount>\" in chat.").formatted(Formatting.GRAY)), false);
		}
		close();
	}
}
