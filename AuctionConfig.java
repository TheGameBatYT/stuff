package com.donutauction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * config/donutauction.json
 *
 * Each pattern needs two named groups: (?<name>...) and (?<amount>...).
 * Amounts understand 5k / 1.5m / 2b / 1,000,000.
 * If DonutSMP words its payment messages differently from the default, edit "patterns" in the file.
 */
public final class AuctionConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("donutauction.json");

	public static AuctionConfig INSTANCE = new AuctionConfig();

	public int antiSnipeSeconds = 10;
	public List<String> patterns = new ArrayList<>(List.of(
		// Chat bids:  "Steve: bid 5k", "[VIP] Steve » !bid 5000", "<Steve> bid 1.5m"
		"^.*?\\b(?<name>[A-Za-z0-9_]{3,16})\\b\\s*[:»>]+\\s*!?bid\\s+\\$?(?<amount>[\\d.,]+\\s*[kKmMbB]?)\\s*$",
		// Payments:   "Steve paid you $5,000"  (edit this one if the server words it differently)
		"(?i)^\\W*(?<name>[A-Za-z0-9_]{3,16}) (?:has )?(?:paid|sent) you \\$?(?<amount>[\\d.,]+\\s*[kKmMbB]?)"
	));

	public static void load() {
		try {
			if (Files.exists(PATH)) {
				try (Reader r = Files.newBufferedReader(PATH)) {
					AuctionConfig loaded = GSON.fromJson(r, AuctionConfig.class);
					if (loaded != null) INSTANCE = loaded;
				}
			} else {
				save();
			}
		} catch (Exception e) {
			System.err.println("[DonutAuction] Could not read config, using defaults: " + e);
		}
	}

	public static void save() {
		try (Writer w = Files.newBufferedWriter(PATH)) {
			GSON.toJson(INSTANCE, w);
		} catch (Exception e) {
			System.err.println("[DonutAuction] Could not write config: " + e);
		}
	}
}
