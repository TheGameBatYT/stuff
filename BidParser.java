package com.donutauction;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BidParser {
	private BidParser() {}

	public record Bid(String name, double amount) {}

	private static List<Pattern> compiled = null;
	private static List<String> compiledFrom = null;

	private static List<Pattern> patterns() {
		List<String> src = AuctionConfig.INSTANCE.patterns;
		if (compiled == null || compiledFrom != src) {
			List<Pattern> out = new ArrayList<>();
			for (String s : src) {
				try {
					out.add(Pattern.compile(s));
				} catch (Exception e) {
					System.err.println("[DonutAuction] Bad pattern in config: " + s);
				}
			}
			compiled = out;
			compiledFrom = src;
		}
		return compiled;
	}

	public static Bid parse(String line) {
		if (line == null) return null;
		for (Pattern p : patterns()) {
			Matcher m = p.matcher(line);
			if (!m.find()) continue;
			try {
				double amount = parseAmount(m.group("amount"));
				if (!Double.isNaN(amount)) return new Bid(m.group("name"), amount);
			} catch (Exception ignored) {
				// pattern is missing a group; skip it
			}
		}
		return null;
	}

	private static final Pattern AMOUNT = Pattern.compile("^\\$?\\s*(\\d+(?:\\.\\d+)?)\\s*([kmb])?$", Pattern.CASE_INSENSITIVE);

	/** "5k" -> 5000, "1.5m" -> 1500000, "$1,000" -> 1000. Returns NaN if it isn't a number. */
	public static double parseAmount(String raw) {
		if (raw == null) return Double.NaN;
		String s = raw.replace(",", "").trim();
		Matcher m = AMOUNT.matcher(s);
		if (!m.matches()) return Double.NaN;
		double v = Double.parseDouble(m.group(1));
		String suffix = m.group(2);
		if (suffix != null) {
			switch (suffix.toLowerCase()) {
				case "k" -> v *= 1_000d;
				case "m" -> v *= 1_000_000d;
				case "b" -> v *= 1_000_000_000d;
			}
		}
		return v;
	}

	private static final Pattern DURATION = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*(s|sec|secs|m|min|mins|h|hr|hrs)?\\s*$", Pattern.CASE_INSENSITIVE);

	/** "90s", "15m", "1h", or a bare number (minutes). Returns -1 if invalid. */
	public static long parseDurationMs(String raw) {
		if (raw == null) return -1;
		Matcher m = DURATION.matcher(raw);
		if (!m.matches()) return -1;
		double n = Double.parseDouble(m.group(1));
		String u = m.group(2) == null ? "m" : m.group(2).toLowerCase();
		double seconds = u.startsWith("s") ? n : u.startsWith("h") ? n * 3600 : n * 60;
		return (long) (seconds * 1000);
	}
}
