package de.hysky.skyblocker.skyblock.dwarven.profittrackers.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.hysky.skyblocker.utils.SkyBlockIcons;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

/**
 * Every input string here was copied out of a real {@code latest.log} from a Dwarven Mines / Glacite Mineshafts
 * session on SkyBlock v0.27, private use area icons included — those are written as {@link SkyBlockIcons}
 * constants rather than literals so the test file stays readable.
 */
class MiningPatternsTest {
	private static final String AMETHYST = String.valueOf(SkyBlockIcons.DEFENSE);
	private static final String AQUAMARINE = String.valueOf(SkyBlockIcons.FISHING_SPEED);
	private static final String MAGIC_FIND = String.valueOf(SkyBlockIcons.MAGIC_FIND);

	@Test
	void parsesPristineWithAmount() {
		Matcher matcher = MiningPatterns.PRISTINE.matcher("PRISTINE! You found " + AMETHYST + " Flawed Amethyst Gemstone x15!");
		assertTrue(matcher.matches());
		assertEquals(AMETHYST + " Flawed Amethyst Gemstone", matcher.group("item"));
		assertEquals("15", matcher.group("amount"));
	}

	@Test
	void parsesPristineWithoutAmount() {
		Matcher matcher = MiningPatterns.PRISTINE.matcher("PRISTINE! You found " + AMETHYST + " Flawed Amethyst Gemstone!");
		assertTrue(matcher.matches());
		assertNull(matcher.group("amount"));
	}

	@Test
	void pristineItemNameResolvesToAnId() {
		Matcher matcher = MiningPatterns.PRISTINE.matcher("PRISTINE! You found " + AQUAMARINE + " Flawed Aquamarine Gemstone x20!");
		assertTrue(matcher.matches());
		assertEquals("FLAWED_AQUAMARINE_GEM", MiningItems.idForName(matcher.group("item")));
	}

	@Test
	void parsesRareDropWithMagicFind() {
		Matcher matcher = MiningPatterns.RARE_DROP_MAGIC_FIND.matcher("RARE DROP! Goblin Egg (+180 " + MAGIC_FIND + " Magic Find)");
		assertTrue(matcher.matches());
		assertEquals("Goblin Egg", matcher.group("item"));
		assertEquals("180", matcher.group("magicFind"));
	}

	@Test
	void parsesRareDropDropped() {
		Matcher matcher = MiningPatterns.RARE_DROP_DROPPED.matcher("RARE DROP! You dropped 36x Enchanted Nether Wart!");
		assertTrue(matcher.matches());
		assertEquals("36", matcher.group("amount"));
		assertEquals("Enchanted Nether Wart", matcher.group("item"));
	}

	@Test
	void parsesPickobulusBlockCount() {
		Matcher matcher = MiningPatterns.PICKOBULUS_DESTROYED.matcher("Your Pickobulus destroyed 105 blocks!");
		assertTrue(matcher.matches());
		assertEquals("105", matcher.group("blocks"));
	}

	@Test
	void parsesCentredEventLines() {
		// The real messages are centred with leading spaces, which is why the pattern tolerates them.
		Matcher started = MiningPatterns.EVENT_STATE.matcher("                          2X POWDER STARTED!");
		assertTrue(started.matches());
		assertEquals("2X POWDER", started.group("event"));
		assertEquals("STARTED", started.group("state"));

		Matcher ended = MiningPatterns.EVENT_STATE.matcher("                          GOBLIN RAID ENDED!");
		assertTrue(ended.matches());
		assertEquals("GOBLIN RAID", ended.group("event"));
		assertEquals("ENDED", ended.group("state"));

		assertTrue(MiningPatterns.EVENT_STATE.matcher("                              RAFFLE ENDED!").matches());
	}

	@Test
	void eventStateIgnoresOrdinaryChat() {
		assertFalse(MiningPatterns.EVENT_STATE.matcher("Welcome to Hypixel SkyBlock!").matches());
		assertFalse(MiningPatterns.EVENT_STATE.matcher("  REWARDS").matches());
	}

	@Test
	void parsesEventCountdown() {
		Matcher matcher = MiningPatterns.EVENT_STARTING_SOON.matcher(" ⚑ The 2x Powder event starts in 20 seconds!");
		assertTrue(matcher.find());
		assertEquals("2x Powder", matcher.group("event"));
		assertEquals("20", matcher.group("seconds"));
	}

	@Test
	void parsesFallenStar() {
		Matcher matcher = MiningPatterns.FALLEN_STAR.matcher(
				"✴ A Fallen Star has crashed at Cliffside Veins! Nearby ore and Powder drops are amplified!");
		assertTrue(matcher.find());
		assertEquals("Cliffside Veins", matcher.group("place"));
	}

	@Test
	void parsesMineshaftLines() {
		assertTrue(MiningPatterns.MINESHAFT_SPAWNED.matcher("MINESHAFT! A Mineshaft portal spawned nearby!").matches());
		assertTrue(MiningPatterns.MINESHAFT_FOUND.matcher("WOW! You found a Glacite Mineshaft portal! (1580)").matches());

		Matcher bonus = MiningPatterns.MINESHAFT_LEAVE_BONUS.matcher(
				"BYE! You got a +1,920 Glacite Powder bonus for leaving a Mineshaft before you died!");
		assertTrue(bonus.matches());
		assertEquals("1,920", bonus.group("amount"));
	}

	@Test
	void parsesPowderGhastAndGoblin() {
		Matcher ghast = MiningPatterns.POWDER_GHAST_LOCATION.matcher("Find the Powder Ghast near the Divan's Gateway!");
		assertTrue(ghast.matches());
		assertEquals("Divan's Gateway", ghast.group("place"));

		assertTrue(MiningPatterns.POWDER_GHAST_SPAWNED.matcher(
				"The sound of pickaxes clashing against the rock has attracted the attention of the POWDER GHAST!").find());

		Matcher goblin = MiningPatterns.GOBLIN_SPAWNED.matcher("A Golden Goblin has spawned!");
		assertTrue(goblin.matches());
		assertEquals("Golden", goblin.group("goblin"));
	}

	@Test
	void parsesExcavatorAndCommission() {
		Matcher excavator = MiningPatterns.EXCAVATOR_FOUND.matcher("EXCAVATOR! You found a Suspicious Scrap!");
		assertTrue(excavator.matches());
		assertEquals("Suspicious Scrap", excavator.group("item"));

		Matcher commission = MiningPatterns.COMMISSION_COMPLETE.matcher(
				"SCRAP COLLECTOR Commission Complete! Visit the King to claim your rewards!");
		assertTrue(commission.matches());
		assertEquals("SCRAP COLLECTOR", commission.group("commission"));
	}

	@Test
	void parsesCorpseHeaderAndRewardLines() {
		Matcher header = MiningPatterns.CORPSE_HEADER.matcher("  LAPIS CORPSE LOOT! ");
		assertTrue(header.matches());
		assertEquals("LAPIS", header.group(1));

		Matcher reward = MiningPatterns.REWARD_LINE.matcher("    " + AQUAMARINE + " Flawed Aquamarine Gemstone x20");
		assertTrue(reward.matches());
		assertEquals("20", reward.group(2));
	}

	@Test
	void parsesTabPowderCounters() {
		Matcher mithril = MiningPatterns.TAB_MITHRIL.matcher("Mithril: 1,234,567");
		assertTrue(mithril.matches());
		assertEquals("1,234,567", mithril.group(1));
		assertTrue(MiningPatterns.TAB_GEMSTONE.matcher("Gemstone: 42").matches());
		assertTrue(MiningPatterns.TAB_GLACITE.matcher("Glacite: 0").matches());
	}

	@Test
	void parsesSackHoverEntries() {
		String hover = "Added items:\n"
				+ "+64 Mithril (Mining Sack)\n"
				+ "+1,024 " + AQUAMARINE + " Flawed Aquamarine Gemstone (Gemstone Sack)\n"
				+ "-12 Cobblestone (Mining Sack)";

		List<String> names = new ArrayList<>();
		List<String> signs = new ArrayList<>();
		List<String> amounts = new ArrayList<>();
		Matcher matcher = MiningPatterns.SACK_HOVER_LINE.matcher(hover);
		while (matcher.find()) {
			signs.add(matcher.group(1));
			amounts.add(matcher.group(2));
			names.add(MiningItems.stripIcons(matcher.group(3)));
		}

		assertEquals(List.of("+", "+", "-"), signs);
		assertEquals(List.of("64", "1,024", "12"), amounts);
		assertEquals(List.of("Mithril", "Flawed Aquamarine Gemstone", "Cobblestone"), names);
	}

	@Test
	void sackHoverMatchDoesNotRunPastAnEntry() {
		// A greedy sack group would swallow the following entry whole.
		Matcher matcher = MiningPatterns.SACK_HOVER_LINE.matcher("+1 A (Sack One) +2 B (Sack Two)");
		assertTrue(matcher.find());
		assertEquals("Sack One", matcher.group(4));
		assertTrue(matcher.find());
		assertEquals("Sack Two", matcher.group(4));
	}

	@Test
	void miningXpMatchesBothActionBarShapes() {
		Matcher percent = MiningPatterns.MINING_XP.matcher("+12.5 Mining (34.2%)");
		assertTrue(percent.find());
		assertEquals("12.5", percent.group("xp"));
		assertEquals("34.2", percent.group("percent"));

		Matcher fraction = MiningPatterns.MINING_XP.matcher("+7 Mining (1,234/25k)");
		assertTrue(fraction.find());
		assertEquals("7", fraction.group("xp"));
		assertEquals("1,234", fraction.group("current"));
		assertEquals("25k", fraction.group("max"));
	}

	@Test
	void everyPatternIsListedForTheDumpCommand() {
		// The /skyblocker miningTracker patterns command is only useful if it is complete.
		assertTrue(MiningPatterns.all().size() >= 25);
		assertTrue(MiningPatterns.all().containsKey("PRISTINE"));
		assertTrue(MiningPatterns.all().containsKey("SACK_HOVER_LINE"));
	}

	@Test
	void stripsPrivateUseIcons() {
		assertEquals("Flawed Amethyst Gemstone", MiningItems.stripIcons(AMETHYST + " Flawed Amethyst Gemstone"));
		assertEquals("Suspicious Scrap", MiningItems.stripIcons("Suspicious Scrap"));
	}
}
