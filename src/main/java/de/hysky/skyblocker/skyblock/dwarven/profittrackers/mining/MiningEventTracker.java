package de.hysky.skyblocker.skyblock.dwarven.profittrackers.mining;

import de.hysky.skyblocker.utils.mayor.MayorUtils;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import org.jspecify.annotations.Nullable;

/**
 * Keeps track of which mining event is running, purely so the HUD can label the numbers.
 *
 * <p>Nothing here ever multiplies anything. A 2x Powder event is already baked into the tab list powder counters
 * the {@code TAB_DIFF} channel reads, and a mayor perk is already baked into whatever landed in the sack — applying
 * the multiplier a second time is exactly the bug this design is built to avoid.
 */
public final class MiningEventTracker {
	/**
	 * Events we care about. Anything else that shouts {@code ... STARTED!} in chat (island events, festivals) is
	 * ignored so the HUD does not advertise a Spooky Festival as a mining event.
	 */
	private static final Set<String> KNOWN_EVENTS = Set.of(
			"2X POWDER", "DOUBLE POWDER", "GOBLIN RAID", "RAFFLE", "MINING FIESTA"
	);

	/** Mayor and minister perks worth showing next to the numbers. */
	private static final Set<String> RELEVANT_PERKS = Set.of(
			"Mining Fiesta", "Better Together", "Fortunate Freezing", "Mithril Gourmand",
			"Gone with the Wind", "Molten Forge", "Mining XPBuff", "Perkpocalypse", "Double Powder"
	);

	/** Failsafe: an event whose ENDED message we missed (relog, chat filter) expires on its own. */
	private static final long MAX_EVENT_MS = 70 * 60 * 1000L;

	private final Object2LongOpenHashMap<String> activeEvents = new Object2LongOpenHashMap<>();
	private @Nullable String lastFallenStar;
	private long lastFallenStarAt;

	/**
	 * @param message a formatting-stripped chat line
	 * @return true if the line was an event line
	 */
	public boolean onChatMessage(String message, long now) {
		Matcher state = MiningPatterns.EVENT_STATE.matcher(message);
		if (state.matches()) {
			String event = state.group("event").trim().toUpperCase(Locale.ENGLISH);
			if (!KNOWN_EVENTS.contains(event)) return false;
			if (state.group("state").equals("STARTED")) activeEvents.put(event, now);
			else activeEvents.removeLong(event);
			return true;
		}

		Matcher star = MiningPatterns.FALLEN_STAR.matcher(message);
		if (star.find()) {
			lastFallenStar = star.group("place");
			lastFallenStarAt = now;
			return true;
		}

		return false;
	}

	public void reset() {
		activeEvents.clear();
		lastFallenStar = null;
	}

	/** Whether a 2x Powder event is running. Shown on the HUD; never used as a multiplier. */
	public boolean isDoublePowder(long now) {
		expire(now);
		return activeEvents.containsKey("2X POWDER") || activeEvents.containsKey("DOUBLE POWDER");
	}

	/**
	 * @return human readable labels for everything currently modifying this session, chat events first, then the
	 * 		mayor perks that matter for mining.
	 */
	public List<String> activeLabels(long now) {
		expire(now);
		List<String> labels = new ArrayList<>(activeEvents.keySet());
		if (lastFallenStar != null && now - lastFallenStarAt < 5 * 60 * 1000L) {
			labels.add("FALLEN STAR @ " + lastFallenStar);
		}
		for (String perk : MayorUtils.getActivePerks()) {
			if (RELEVANT_PERKS.contains(perk)) labels.add(perk);
		}
		return labels;
	}

	private void expire(long now) {
		var iterator = activeEvents.object2LongEntrySet().iterator();
		while (iterator.hasNext()) {
			if (now - iterator.next().getLongValue() > MAX_EVENT_MS) iterator.remove();
		}
	}
}
