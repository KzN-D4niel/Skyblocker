package de.hysky.skyblocker.skyblock.dwarven.profittrackers.mining;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Every regex the mining tracker uses, in one place.
 * <p>
 * Patterns marked {@code (verified)} were taken from a real {@code latest.log} of a Dwarven Mines /
 * Glacite Mineshafts session on SkyBlock v0.27. Patterns marked {@code (unverified)} are best guesses
 * that still need to be confirmed against real chat; nothing depends on them beyond the single feature
 * they drive, so a mismatch degrades gracefully.
 *
 * @see MiningTracker the {@code /skyblocker miningTracker patterns} command dumps {@link #all()}
 */
public final class MiningPatterns {
	private MiningPatterns() {}

	// ---------------------------------------------------------------- sacks

	/** (verified) {@code [Sacks] +1,005 items, -442 items. (Last 25s.)} — the detail lives in the hover event. */
	public static final String SACK_PREFIX = "[Sacks] ";

	/**
	 * (verified) A single entry of the sack hover text, e.g. {@code +64 Mithril (Mining Sack)}.
	 * The sack group forbids brackets and newlines so the match cannot run past the end of one entry.
	 */
	public static final Pattern SACK_HOVER_LINE = Pattern.compile("([+-])([\\d,]+) (.+?) \\(([^()\\n]+)\\)");

	// ------------------------------------------------------------- gemstones

	/** (verified) {@code PRISTINE! You found  Flawed Amethyst Gemstone x15!} — the {@code xN} part is absent for a single item. */
	public static final Pattern PRISTINE = Pattern.compile("^PRISTINE! You found (?<item>.+?)(?: x(?<amount>[\\d,]+))?!$");

	// ------------------------------------------------------------ rare drops

	/** (verified) {@code RARE DROP! Goblin Egg (+180  Magic Find)} */
	public static final Pattern RARE_DROP_MAGIC_FIND = Pattern.compile("^(?:CRAZY )?RARE DROP! (?<item>.+?) \\(\\+(?<magicFind>[\\d,]+) ?\\S? ?Magic Find\\)$");

	/** (verified) {@code RARE DROP! You dropped 36x Enchanted Nether Wart!} */
	public static final Pattern RARE_DROP_DROPPED = Pattern.compile("^(?:CRAZY )?RARE DROP! You dropped (?<amount>[\\d,]+)x (?<item>.+)!$");

	// --------------------------------------------------------- block breaking

	/** (verified) {@code Your Pickobulus destroyed 105 blocks!} — the client never sees these breaks, so they must be added manually. */
	public static final Pattern PICKOBULUS_DESTROYED = Pattern.compile("^Your Pickobulus destroyed (?<blocks>[\\d,]+) blocks!$");

	// ------------------------------------------------------------- action bar

	/**
	 * (unverified) Mining skill XP on the action bar. Mirrors {@code FarmingHud.FARMING_XP}, which is the
	 * same message with a different skill name, so the shape is near certain — but action bar messages are
	 * never written to {@code latest.log}, so it could not be confirmed offline.
	 */
	public static final Pattern MINING_XP = Pattern.compile("\\+(?<xp>\\d+(?:\\.\\d+)?) Mining \\((?:(?<percent>[\\d,]+(?:\\.\\d+)?)%|(?<current>[\\d,]+)/(?<max>[\\d,]+k?))\\)");

	// ----------------------------------------------------------------- events

	/** (verified) {@code 2X POWDER STARTED!}, {@code GOBLIN RAID ENDED!}, {@code RAFFLE STARTED!} — centred, hence the leading whitespace. */
	public static final Pattern EVENT_STATE = Pattern.compile("^\\s*(?<event>[A-Z0-9'\\- ]+?) (?<state>STARTED|ENDED)!\\s*$");

	/** (verified) {@code ⚑ The 2x Powder event starts in 20 seconds!} (formatting stripped first) */
	public static final Pattern EVENT_STARTING_SOON = Pattern.compile("The (?<event>.+?) event starts in (?<seconds>\\d+) seconds?!");

	/** (verified) {@code RAFFLE CLOSING! in 10 seconds} */
	public static final Pattern RAFFLE_CLOSING = Pattern.compile("^\\s*RAFFLE CLOSING! in (?<seconds>\\d+) seconds?\\s*$");

	/** (verified) {@code ✴ A Fallen Star has crashed at Cliffside Veins! Nearby ore and Powder drops are amplified!} */
	public static final Pattern FALLEN_STAR = Pattern.compile("A Fallen Star has crashed at (?<place>.+?)! Nearby ore and Powder drops are amplified!");

	// -------------------------------------------------------------- mineshaft

	/** (verified) {@code MINESHAFT! A Mineshaft portal spawned nearby!} */
	public static final Pattern MINESHAFT_SPAWNED = Pattern.compile("^\\s*MINESHAFT! A Mineshaft portal spawned nearby!\\s*$");

	/** (verified) {@code WOW! You found a Glacite Mineshaft portal! (1580)} */
	public static final Pattern MINESHAFT_FOUND = Pattern.compile("^WOW! You found a Glacite Mineshaft portal!.*$");

	/** (verified) {@code BYE! You got a +1,920 Glacite Powder bonus for leaving a Mineshaft before you died!} */
	public static final Pattern MINESHAFT_LEAVE_BONUS = Pattern.compile("^BYE! You got a \\+(?<amount>[\\d,]+) Glacite Powder bonus for leaving a Mineshaft before you died!$");

	// ------------------------------------------------------------------ misc

	/** (verified) {@code Find the Powder Ghast near the Divan's Gateway!} */
	public static final Pattern POWDER_GHAST_LOCATION = Pattern.compile("^Find the Powder Ghast near the (?<place>.+)!$");

	/** (verified) {@code The sound of pickaxes clashing against the rock has attracted the attention of the POWDER GHAST!} */
	public static final Pattern POWDER_GHAST_SPAWNED = Pattern.compile("attracted the attention of the POWDER GHAST!$");

	/** (verified) {@code A Golden Goblin has spawned!} */
	public static final Pattern GOBLIN_SPAWNED = Pattern.compile("^A (?<goblin>Golden|Diamond) Goblin has spawned!$");

	/** (verified) {@code EXCAVATOR! You found a Suspicious Scrap!} */
	public static final Pattern EXCAVATOR_FOUND = Pattern.compile("^EXCAVATOR! You found a (?<item>.+?)!$");

	/** (verified) {@code SCRAP COLLECTOR Commission Complete! Visit the King to claim your rewards!} */
	public static final Pattern COMMISSION_COMPLETE = Pattern.compile("^(?<commission>[A-Z0-9'\\- ]+) Commission Complete! Visit the King to claim your rewards!$");

	/** (unverified) Sky Mall daily buff line. Only ever used to show a label. */
	public static final Pattern SKY_MALL = Pattern.compile("^\\s*SKY MALL: (?<buff>.+)$");

	// --------------------------------------------------- reused from the mod

	/** (verified, from {@code AbstractProfitTracker}) A reward line inside a chest/corpse block. */
	public static final Pattern REWARD_LINE = Pattern.compile(" {4}(.*?) ?x?([\\d,]*)");

	/** (verified, from {@code CorpseProfitTracker}) */
	public static final Pattern CORPSE_HEADER = Pattern.compile(" {2}(LAPIS|UMBER|TUNGSTEN|VANGUARD) CORPSE LOOT! *");

	/** (verified, from {@code PowderMiningTracker}) The 64-character rule that closes a reward block. */
	public static final String BLOCK_SEPARATOR = "▬".repeat(64);

	/** (verified, from {@code PowderMiningTracker}) */
	public static final String CHEST_LOCKPICKED = "  CHEST LOCKPICKED ";

	/** (verified, from {@code PowderMiningTracker}) */
	public static final String LOOT_CHEST_COLLECTED = "  LOOT CHEST COLLECTED ";

	/** (verified, from {@code PowderWidget}) Tab list powder counters. */
	public static final Pattern TAB_MITHRIL = Pattern.compile("Mithril: ([\\d,]+)");
	public static final Pattern TAB_GEMSTONE = Pattern.compile("Gemstone: ([\\d,]+)");
	public static final Pattern TAB_GLACITE = Pattern.compile("Glacite: ([\\d,]+)");

	/**
	 * Every pattern above, keyed by name, for {@code /skyblocker miningTracker patterns}.
	 * Insertion ordered so the dump stays readable.
	 */
	public static Map<String, String> all() {
		Object2ObjectLinkedOpenHashMap<String, String> map = new Object2ObjectLinkedOpenHashMap<>();
		map.put("SACK_PREFIX (literal prefix)", SACK_PREFIX);
		map.put("SACK_HOVER_LINE", SACK_HOVER_LINE.pattern());
		map.put("PRISTINE", PRISTINE.pattern());
		map.put("RARE_DROP_MAGIC_FIND", RARE_DROP_MAGIC_FIND.pattern());
		map.put("RARE_DROP_DROPPED", RARE_DROP_DROPPED.pattern());
		map.put("PICKOBULUS_DESTROYED", PICKOBULUS_DESTROYED.pattern());
		map.put("MINING_XP (unverified)", MINING_XP.pattern());
		map.put("EVENT_STATE", EVENT_STATE.pattern());
		map.put("EVENT_STARTING_SOON", EVENT_STARTING_SOON.pattern());
		map.put("RAFFLE_CLOSING", RAFFLE_CLOSING.pattern());
		map.put("FALLEN_STAR", FALLEN_STAR.pattern());
		map.put("MINESHAFT_SPAWNED", MINESHAFT_SPAWNED.pattern());
		map.put("MINESHAFT_FOUND", MINESHAFT_FOUND.pattern());
		map.put("MINESHAFT_LEAVE_BONUS", MINESHAFT_LEAVE_BONUS.pattern());
		map.put("POWDER_GHAST_LOCATION", POWDER_GHAST_LOCATION.pattern());
		map.put("POWDER_GHAST_SPAWNED", POWDER_GHAST_SPAWNED.pattern());
		map.put("GOBLIN_SPAWNED", GOBLIN_SPAWNED.pattern());
		map.put("EXCAVATOR_FOUND", EXCAVATOR_FOUND.pattern());
		map.put("COMMISSION_COMPLETE", COMMISSION_COMPLETE.pattern());
		map.put("SKY_MALL (unverified)", SKY_MALL.pattern());
		map.put("REWARD_LINE", REWARD_LINE.pattern());
		map.put("CORPSE_HEADER", CORPSE_HEADER.pattern());
		map.put("BLOCK_SEPARATOR (literal)", "▬ x64");
		map.put("CHEST_LOCKPICKED (literal)", CHEST_LOCKPICKED);
		map.put("LOOT_CHEST_COLLECTED (literal)", LOOT_CHEST_COLLECTED);
		map.put("TAB_MITHRIL", TAB_MITHRIL.pattern());
		map.put("TAB_GEMSTONE", TAB_GEMSTONE.pattern());
		map.put("TAB_GLACITE", TAB_GLACITE.pattern());
		return Collections.unmodifiableMap(map);
	}
}
