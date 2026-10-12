package com.donutauction;

import net.minecraft.client.gui.DrawContext;

/** Small drawing + formatting helpers shared by the HUD and the menu. */
public final class Ui {
	private Ui() {}

	public static final int BLACK = 0xFF000000;
	public static final int BORDER = 0xFF2B2B2B;
	public static final int PANEL = 0xFF0D0D0D;
	public static final int HOVER = 0xFF1C1C1C;
	public static final int ACCENT = 0xFF00E0A4;
	public static final int GOLD = 0xFFFFC857;
	public static final int RED = 0xFFFF5470;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFF8A8A8A;
	public static final int DIM = 0xFF555555;

	/** Filled rectangle with 2px rounded corners. Rows never overlap, so translucent colours stay clean. */
	public static void rounded(DrawContext c, int x, int y, int w, int h, int color) {
		c.fill(x + 2, y, x + w - 2, y + 1, color);
		c.fill(x + 1, y + 1, x + w - 1, y + 2, color);
		c.fill(x, y + 2, x + w, y + h - 2, color);
		c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
		c.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
	}

	/** Rounded rectangle with a 1px border. */
	public static void roundedBordered(DrawContext c, int x, int y, int w, int h, int fill, int border) {
		rounded(c, x, y, w, h, border);
		rounded(c, x + 1, y + 1, w - 2, h - 2, fill);
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	public static int lerpColor(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int r = (int) (ar + (br - ar) * t);
		int g = (int) (ag + (bg - ag) * t);
		int bl = (int) (ab + (bb - ab) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	public static String time(long ms) {
		long total = Math.max(0, (ms + 999) / 1000);
		long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
		if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
		return String.format("%d:%02d", m, s);
	}

	public static String money(double v) {
		String s;
		if (v >= 1_000_000_000d) s = trim(v / 1_000_000_000d) + "B";
		else if (v >= 1_000_000d) s = trim(v / 1_000_000d) + "M";
		else if (v >= 1_000d) s = trim(v / 1_000d) + "K";
		else s = trim(v);
		return "$" + s;
	}

	private static String trim(double v) {
		return String.format("%.2f", v).replaceAll("\\.?0+$", "");
	}
}
