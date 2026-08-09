package de.hysky.skyblocker.skyblock.dwarven.profittrackers.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Sanity of the raw-unit table itself: ratios, family membership, and the scope boundary. */
class MiningUnitsTest {
	@Test
	void enchantedFormsAreWorthOneHundredSixty() {
		assertEquals(1, MiningItems.toUnits("MITHRIL_ORE", 1));
		assertEquals(160, MiningItems.toUnits("ENCHANTED_MITHRIL", 1));
		assertEquals(160, MiningItems.toUnits("ENCHANTED_TITANIUM", 1));
		assertEquals(25_600, MiningItems.toUnits("ENCHANTED_DIAMOND_BLOCK", 1));
	}

	@Test
	void gemstoneTiersUseEighty() {
		assertEquals(1, MiningItems.toUnits("ROUGH_JADE_GEM", 1));
		assertEquals(80, MiningItems.toUnits("FLAWED_JADE_GEM", 1));
		assertEquals(6_400, MiningItems.toUnits("FINE_JADE_GEM", 1));
		assertEquals(512_000, MiningItems.toUnits("FLAWLESS_JADE_GEM", 1));
	}

	@Test
	void everyFormPointsAtItsBaseMaterial() {
		assertEquals("MITHRIL_ORE", MiningItems.tier("ENCHANTED_MITHRIL").rawId());
		assertEquals("ROUGH_ONYX_GEM", MiningItems.tier("FLAWLESS_ONYX_GEM").rawId());
		assertEquals("DIAMOND", MiningItems.tier("ENCHANTED_DIAMOND_BLOCK").rawId());
	}

	@Test
	void allTwelveGemstonesAreCoveredInAllFourTiers() {
		for (String gem : new String[]{"RUBY", "AMETHYST", "JADE", "SAPPHIRE", "AMBER", "TOPAZ",
				"JASPER", "OPAL", "ONYX", "AQUAMARINE", "CITRINE", "PERIDOT"}) {
			for (String tier : new String[]{"ROUGH", "FLAWED", "FINE", "FLAWLESS"}) {
				String id = tier + "_" + gem + "_GEM";
				assertTrue(MiningItems.isMaterial(id), id + " is missing from the unit table");
			}
		}
	}

	@Test
	void metalsAndScrapAreCovered() {
		for (String id : new String[]{"MITHRIL_ORE", "TITANIUM_ORE", "UMBER", "TUNGSTEN", "GLACITE",
				"HARD_STONE", "SUSPICIOUS_SCRAP", "COAL", "IRON_INGOT", "GOLD_INGOT", "REDSTONE",
				"INK_SACK:4", "EMERALD", "DIAMOND", "QUARTZ", "OBSIDIAN", "GLOWSTONE_DUST",
				"SULPHUR_ORE", "NETHERRACK", "GRAVEL", "MYCELIUM", "SAND:1"}) {
			assertTrue(MiningItems.isMaterial(id), id + " should be tracked");
		}
	}

	@Test
	void outOfScopeItemsAreNotMaterials() {
		// The narrowed scope: no pets, no Nucleus loot, no armour, no fossils, no keys, no fuel.
		for (String id : new String[]{"SCATHA;4", "ROBOTRON_REFLECTOR", "PREHISTORIC_EGG", "TREASURITE",
				"GOBLIN_CHESTPLATE", "HELIX_FOSSIL", "TUNGSTEN_KEY", "VOLTA", "OIL_BARREL"}) {
			assertFalse(MiningItems.isMaterial(id), id + " is out of scope and must be ignored");
		}
	}

	@Test
	void powdersAreNotItemsAndCarryNoUnits() {
		for (String powder : MiningItems.POWDERS) {
			assertFalse(MiningItems.isMaterial(powder), powder + " must never reach the item ledger");
		}
	}

	@Test
	void everyFormBelongsToExactlyOneFamily() {
		Set<String> seen = new HashSet<>();
		for (String raw : MiningItems.rawMaterials()) {
			for (MiningItems.Tier tier : MiningItems.family(raw)) {
				assertTrue(seen.add(tier.id()), tier.id() + " appears in more than one family");
				assertEquals(raw, tier.rawId());
			}
		}
	}

	@Test
	void chatNamesResolveToTrackedIds() {
		assertEquals("MITHRIL_ORE", MiningItems.idForName("Mithril"));
		assertEquals("ENCHANTED_MITHRIL", MiningItems.idForName("Enchanted Mithril"));
		assertEquals("SUSPICIOUS_SCRAP", MiningItems.idForName("Suspicious Scrap"));
		assertEquals("FLAWED_AQUAMARINE_GEM", MiningItems.idForName("Flawed Aquamarine Gemstone"));
		assertNotNull(MiningItems.tier(MiningItems.idForName("Titanium")));
	}
}
