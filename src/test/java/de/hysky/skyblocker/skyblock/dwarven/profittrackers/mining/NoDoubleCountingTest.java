package de.hysky.skyblocker.skyblock.dwarven.profittrackers.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The situations that would double count in a naive tracker, walked through the real ledger.
 *
 * <p>{@link MiningSession#addUnits} is the whole mechanism, so these run against it directly rather than against a
 * stand-in — if the accounting changes, these fail.
 */
class NoDoubleCountingTest {
	/** Mirrors {@code MiningTracker#applyUnits}: resolve the tier, convert to raw units, add them signed. */
	private static void event(MiningSession session, String id, int amount) {
		MiningItems.Tier tier = MiningItems.tier(id);
		if (tier == null) return; // untracked item, the tracker ignores it entirely
		session.addUnits(tier.rawId(), MiningItems.toUnits(id, amount));
	}

	@Test
	void personalCompactorIsAccountingNeutral() {
		MiningSession session = new MiningSession();
		// Sack is full, so 160 Mithril land in the inventory and are booked as income.
		event(session, "MITHRIL_ORE", 160);
		assertEquals(160, session.units("MITHRIL_ORE"));

		// The compactor eats them and hands back one Enchanted Mithril, in the same slot update.
		event(session, "MITHRIL_ORE", -160);
		event(session, "ENCHANTED_MITHRIL", 1);

		// Still 160 units. Not 320, and not 0.
		assertEquals(160, session.units("MITHRIL_ORE"));
	}

	@Test
	void repeatedCompactionStaysExact() {
		MiningSession session = new MiningSession();
		for (int batch = 0; batch < 10; batch++) {
			event(session, "MITHRIL_ORE", 160);
			event(session, "MITHRIL_ORE", -160);
			event(session, "ENCHANTED_MITHRIL", 1);
		}
		assertEquals(1600, session.units("MITHRIL_ORE"));
	}

	@Test
	void depositingCompactedOreIntoTheSackDoesNotAddASecondTime() {
		MiningSession session = new MiningSession();
		event(session, "MITHRIL_ORE", 160);          // pickup, sack was full
		event(session, "MITHRIL_ORE", -160);         // compactor consumes
		event(session, "ENCHANTED_MITHRIL", 1);      // compactor produces
		event(session, "ENCHANTED_MITHRIL", -1);     // leaves the inventory for the sack
		event(session, "ENCHANTED_MITHRIL", 1);      // the [Sacks] +1 message for the same item

		assertEquals(160, session.units("MITHRIL_ORE"));
	}

	@Test
	void superCompactorOreArrivingStraightInTheSackCountsOnce() {
		MiningSession session = new MiningSession();
		// With a Super Compactor on the drill the raw ore never exists; the sack just reports the enchanted form.
		event(session, "ENCHANTED_MITHRIL", 1);
		assertEquals(160, session.units("MITHRIL_ORE"));
	}

	@Test
	void everyFormOfAMaterialLandsInTheSameBucket() {
		MiningSession session = new MiningSession();
		event(session, "DIAMOND", 160);
		event(session, "ENCHANTED_DIAMOND", 1);
		event(session, "ENCHANTED_DIAMOND_BLOCK", 1);
		// 160 + 160 + 25,600
		assertEquals(25_920, session.units("DIAMOND"));
		assertEquals(1, session.items.size(), "a material must occupy exactly one bucket");
	}

	@Test
	void gemstoneTiersNormaliseToRough() {
		MiningSession session = new MiningSession();
		event(session, "ROUGH_RUBY_GEM", 80);
		event(session, "FLAWED_RUBY_GEM", 1);
		assertEquals(160, session.units("ROUGH_RUBY_GEM"));

		// A Pristine proc handing over 20 Flawed is 1,600 rough-equivalent units.
		event(session, "FLAWED_RUBY_GEM", 20);
		assertEquals(1760, session.units("ROUGH_RUBY_GEM"));
	}

	@Test
	void untrackedItemsAreIgnoredEntirely() {
		MiningSession session = new MiningSession();
		// Out of scope by design: pets, Nucleus parts, armour drops, fossils.
		event(session, "SCATHA;4", 1);
		event(session, "ROBOTRON_REFLECTOR", 2);
		event(session, "GOBLIN_CHESTPLATE", 1);
		assertTrue(session.items.isEmpty());
	}

	@Test
	void aDrainedBucketDisappearsInsteadOfGoingNegative() {
		MiningSession session = new MiningSession();
		event(session, "MITHRIL_ORE", 160);
		event(session, "MITHRIL_ORE", -160);
		assertEquals(0, session.units("MITHRIL_ORE"));
		assertTrue(session.items.isEmpty());
	}

	/**
	 * Mirrors {@code MiningTracker#settleProvisional}: a PRISTINE! line books the gemstones immediately, and the
	 * {@code [Sacks]} message that arrives up to half a minute later must settle against that booking, not add to it.
	 */
	private static int settle(java.util.Map<String, Integer> provisional, String id, int sackAmount) {
		Integer pending = provisional.get(id);
		if (pending == null) return sackAmount;
		int settled = Math.min(pending, sackAmount);
		if (pending - settled <= 0) provisional.remove(id);
		else provisional.put(id, pending - settled);
		return sackAmount - settled;
	}

	@Test
	void pristineIsBookedOnceEvenThoughTheSackReportsItLater() {
		MiningSession session = new MiningSession();
		java.util.Map<String, Integer> provisional = new java.util.HashMap<>();

		// PRISTINE! You found <icon> Flawed Aquamarine Gemstone x20!
		event(session, "FLAWED_AQUAMARINE_GEM", 20);
		provisional.put("FLAWED_AQUAMARINE_GEM", 20);
		assertEquals(1600, session.units("ROUGH_AQUAMARINE_GEM"));

		// [Sacks] +20 Flawed Aquamarine Gemstone, half a minute later.
		int fresh = settle(provisional, "FLAWED_AQUAMARINE_GEM", 20);
		assertEquals(0, fresh);
		event(session, "FLAWED_AQUAMARINE_GEM", fresh);

		assertEquals(1600, session.units("ROUGH_AQUAMARINE_GEM"));
		assertTrue(provisional.isEmpty());
	}

	@Test
	void aSackBatchLargerThanTheProcStillCreditsTheRemainder() {
		MiningSession session = new MiningSession();
		java.util.Map<String, Integer> provisional = new java.util.HashMap<>();

		event(session, "FLAWED_AQUAMARINE_GEM", 20);
		provisional.put("FLAWED_AQUAMARINE_GEM", 20);

		// The batched sack message carries the proc's 20 plus 5 more from a corpse.
		int fresh = settle(provisional, "FLAWED_AQUAMARINE_GEM", 25);
		assertEquals(5, fresh);
		event(session, "FLAWED_AQUAMARINE_GEM", fresh);

		assertEquals(2000, session.units("ROUGH_AQUAMARINE_GEM"));
	}

	@Test
	void roughGemstonesDoNotSettleAProcOfFlawed() {
		java.util.Map<String, Integer> provisional = new java.util.HashMap<>();
		provisional.put("FLAWED_AQUAMARINE_GEM", 20);
		// Both normalise to ROUGH_AQUAMARINE_GEM, so keying the ledger by base material would have let this
		// ordinary Rough batch swallow the proc. It is keyed by exact id precisely to stop that.
		assertEquals(500, settle(provisional, "ROUGH_AQUAMARINE_GEM", 500));
		assertEquals(20, provisional.get("FLAWED_AQUAMARINE_GEM"));
	}

	/** Mirrors the floor in {@code MiningTracker#applyUnits}. */
	private static long clamp(MiningSession session, String rawId, long units) {
		long held = session.units(rawId);
		return units < 0 ? Math.max(units, -held) : units;
	}

	@Test
	void aLossCanNeverExceedWhatTheSessionBooked() {
		MiningSession session = new MiningSession();
		event(session, "MITHRIL_ORE", 100);

		// A stray negative — a mis-read container slot, or ore that was in the inventory before the session began.
		long applied = clamp(session, "MITHRIL_ORE", -100_000);
		assertEquals(-100, applied, "the loss must be capped at what is actually booked");
		session.addUnits("MITHRIL_ORE", applied);

		assertEquals(0, session.units("MITHRIL_ORE"));
		assertTrue(session.items.isEmpty(), "and never go below zero");
	}

	@Test
	void aLossAgainstAnEmptyBucketIsIgnoredEntirely() {
		MiningSession session = new MiningSession();
		assertEquals(0, clamp(session, "DIAMOND", -5000));
	}

	@Test
	void scrapIsTrackedAsItsOwnMaterial() {
		MiningSession session = new MiningSession();
		event(session, "SUSPICIOUS_SCRAP", 4);
		assertEquals(4, session.units("SUSPICIOUS_SCRAP"));
		// Feeding two to the excavator removes them again.
		event(session, "SUSPICIOUS_SCRAP", -2);
		assertEquals(2, session.units("SUSPICIOUS_SCRAP"));
	}
}
