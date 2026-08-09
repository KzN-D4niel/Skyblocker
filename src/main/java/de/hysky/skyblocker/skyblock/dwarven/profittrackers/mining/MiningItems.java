package de.hysky.skyblocker.skyblock.dwarven.profittrackers.mining;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What the mining tracker counts, and how it counts it.
 *
 * <p>Everything is measured in <b>raw units</b> of a base material: one Mithril is one unit, one Enchanted Mithril
 * is a hundred and sixty. That single decision is what makes a Personal or Super Compactor invisible to the books.
 * When the sack is full and the compactor turns 160 loose Mithril into one Enchanted Mithril, the inventory diff
 * sees {@code -160 Mithril} and {@code +1 Enchanted Mithril} — which is {@code -160} then {@code +160} units, a net
 * change of zero. No timing window, no correlation, no special case: the arithmetic simply cannot double count.
 *
 * <p>Scope is deliberately narrow: mined materials only. Metals, vanilla ores, gemstones and Suspicious Scrap.
 * Pets, Nucleus loot, armour drops and fossils are out.
 *
 * <p>Free of any Minecraft or Skyblocker runtime dependency so the rules can be unit tested on their own.
 */
public final class MiningItems {
	private MiningItems() {}

	/** Hypixel keeps its stat and gemstone icons in the Unicode private use area. */
	private static final char PRIVATE_USE_START = '\uE000';
	private static final char PRIVATE_USE_END = '\uF8FF';

	/** Raw ore to enchanted, and enchanted to enchanted block. */
	private static final int ENCHANT_RATIO = 160;
	/** Rough to Flawed, Flawed to Fine, Fine to Flawless. */
	private static final int GEM_RATIO = 80;

	// -------------------------------------------------------------- powders

	public static final String MITHRIL_POWDER = "MITHRIL_POWDER";
	public static final String GEMSTONE_POWDER = "GEMSTONE_POWDER";
	public static final String GLACITE_POWDER = "GLACITE_POWDER";

	/** Powders are their own currency; they never enter the coin total unless the user opts in. */
	public static final Set<String> POWDERS = Set.of(MITHRIL_POWDER, GEMSTONE_POWDER, GLACITE_POWDER);

	// ------------------------------------------------------------ materials

	/**
	 * One form of a material.
	 *
	 * @param id     the SkyBlock item id of this form
	 * @param rawId  the base form the whole family normalises to
	 * @param units  how many raw units one of this form is worth
	 */
	public record Tier(String id, String rawId, int units) {}

	private static final Object2ObjectOpenHashMap<String, Tier> TIERS = new Object2ObjectOpenHashMap<>(256);
	private static final Object2ObjectOpenHashMap<String, ObjectArrayList<Tier>> FAMILIES = new Object2ObjectOpenHashMap<>(64);

	private static void defineFamily(String rawId, String... higherForms) {
		register(new Tier(rawId, rawId, 1));
		int units = 1;
		for (String form : higherForms) {
			units *= ENCHANT_RATIO;
			register(new Tier(form, rawId, units));
		}
	}

	private static void gemstoneFamily(String gem) {
		String upper = gem.toUpperCase(Locale.ENGLISH);
		String rawId = "ROUGH_" + upper + "_GEM";
		register(new Tier(rawId, rawId, 1));
		register(new Tier("FLAWED_" + upper + "_GEM", rawId, GEM_RATIO));
		register(new Tier("FINE_" + upper + "_GEM", rawId, GEM_RATIO * GEM_RATIO));
		register(new Tier("FLAWLESS_" + upper + "_GEM", rawId, GEM_RATIO * GEM_RATIO * GEM_RATIO));
	}

	private static void register(Tier tier) {
		TIERS.put(tier.id(), tier);
		FAMILIES.computeIfAbsent(tier.rawId(), _ -> new ObjectArrayList<>()).add(tier);
	}

	static {
		// Dwarven and Glacite metals
		defineFamily("MITHRIL_ORE", "ENCHANTED_MITHRIL");
		defineFamily("TITANIUM_ORE", "ENCHANTED_TITANIUM");
		defineFamily("UMBER", "ENCHANTED_UMBER");
		defineFamily("TUNGSTEN", "ENCHANTED_TUNGSTEN");
		defineFamily("GLACITE", "ENCHANTED_GLACITE");
		defineFamily("HARD_STONE", "ENCHANTED_HARD_STONE");
		// Vanilla ores, with their enchanted block second tier where one exists
		defineFamily("COAL", "ENCHANTED_COAL", "ENCHANTED_COAL_BLOCK");
		defineFamily("IRON_INGOT", "ENCHANTED_IRON", "ENCHANTED_IRON_BLOCK");
		defineFamily("GOLD_INGOT", "ENCHANTED_GOLD", "ENCHANTED_GOLD_BLOCK");
		defineFamily("REDSTONE", "ENCHANTED_REDSTONE", "ENCHANTED_REDSTONE_BLOCK");
		defineFamily("INK_SACK:4", "ENCHANTED_LAPIS_LAZULI", "ENCHANTED_LAPIS_LAZULI_BLOCK");
		defineFamily("EMERALD", "ENCHANTED_EMERALD", "ENCHANTED_EMERALD_BLOCK");
		defineFamily("DIAMOND", "ENCHANTED_DIAMOND", "ENCHANTED_DIAMOND_BLOCK");
		defineFamily("QUARTZ", "ENCHANTED_QUARTZ", "ENCHANTED_QUARTZ_BLOCK");
		defineFamily("OBSIDIAN", "ENCHANTED_OBSIDIAN");
		defineFamily("GLOWSTONE_DUST", "ENCHANTED_GLOWSTONE_DUST", "ENCHANTED_GLOWSTONE");
		defineFamily("SULPHUR_ORE", "ENCHANTED_SULPHUR");
		defineFamily("NETHERRACK", "ENCHANTED_NETHERRACK");
		defineFamily("COBBLESTONE", "ENCHANTED_COBBLESTONE");
		defineFamily("GRAVEL");
		defineFamily("FLINT", "ENCHANTED_FLINT");
		defineFamily("MYCELIUM", "ENCHANTED_MYCELIUM");
		defineFamily("SAND:1", "ENCHANTED_RED_SAND");
		defineFamily("ICE", "ENCHANTED_ICE", "ENCHANTED_PACKED_ICE");
		// Gemstones: 12 types, 4 tiers each
		for (String gem : new String[]{"RUBY", "AMETHYST", "JADE", "SAPPHIRE", "AMBER", "TOPAZ",
				"JASPER", "OPAL", "ONYX", "AQUAMARINE", "CITRINE", "PERIDOT"}) {
			gemstoneFamily(gem);
		}
		// Standalone
		defineFamily("SUSPICIOUS_SCRAP");
	}

	/** @return the tier this id belongs to, or {@code null} if the tracker does not count this item. */
	public static @Nullable Tier tier(String id) {
		return id == null ? null : TIERS.get(id);
	}

	public static boolean isMaterial(String id) {
		return id != null && TIERS.containsKey(id);
	}

	/** Every form of the family this raw id heads, cheapest first. */
	public static List<Tier> family(String rawId) {
		List<Tier> tiers = FAMILIES.get(rawId);
		return tiers == null ? List.of() : Collections.unmodifiableList(tiers);
	}

	/** Every base material the tracker knows, for the debug report. */
	public static Set<String> rawMaterials() {
		return Collections.unmodifiableSet(FAMILIES.keySet());
	}

	/**
	 * @return how many raw units {@code amount} of {@code id} is worth, or {@code 0} for an untracked item
	 */
	public static long toUnits(String id, int amount) {
		Tier tier = tier(id);
		return tier == null ? 0 : (long) tier.units() * amount;
	}

	// ------------------------------------------------------------ name lookup

	/** Fallback name → id map for the names the NEU repo resolves badly. Keys are already icon-stripped. */
	public static final Map<String, String> NAME_TO_ID = buildNameToId();

	private static Map<String, String> buildNameToId() {
		Object2ObjectOpenHashMap<String, String> map = new Object2ObjectOpenHashMap<>();
		map.put("Mithril Powder", MITHRIL_POWDER);
		map.put("Gemstone Powder", GEMSTONE_POWDER);
		map.put("Glacite Powder", GLACITE_POWDER);
		map.put("Mithril", "MITHRIL_ORE");
		map.put("Titanium", "TITANIUM_ORE");
		map.put("Enchanted Mithril", "ENCHANTED_MITHRIL");
		map.put("Enchanted Titanium", "ENCHANTED_TITANIUM");
		map.put("Suspicious Scrap", "SUSPICIOUS_SCRAP");
		map.put("Lapis Lazuli", "INK_SACK:4");
		map.put("Red Sand", "SAND:1");
		map.put("Hard Stone", "HARD_STONE");
		map.put("Sulphur", "SULPHUR_ORE");
		map.put("Glowstone Dust", "GLOWSTONE_DUST");
		for (String gem : new String[]{"Ruby", "Amethyst", "Jade", "Sapphire", "Amber", "Topaz",
				"Jasper", "Opal", "Onyx", "Aquamarine", "Citrine", "Peridot"}) {
			for (String tier : new String[]{"Rough", "Flawed", "Fine", "Flawless"}) {
				map.put(tier + " " + gem + " Gemstone",
						tier.toUpperCase(Locale.ENGLISH) + "_" + gem.toUpperCase(Locale.ENGLISH) + "_GEM");
			}
		}
		return Collections.unmodifiableMap(map);
	}

	/** Removes Hypixel's private-use-area stat and gemstone icons, then collapses the whitespace they leave behind. */
	public static String stripIcons(String name) {
		StringBuilder sb = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c >= PRIVATE_USE_START && c <= PRIVATE_USE_END) continue;
			sb.append(c);
		}
		return sb.toString().replaceAll("\\s{2,}", " ").trim();
	}

	/** @return the SkyBlock id for a chat item name, or {@code null} if this table does not know it. */
	public static @Nullable String idForName(String rawName) {
		return NAME_TO_ID.get(stripIcons(rawName));
	}

	// ---------------------------------------------------------------- powder

	/**
	 * How much powder was gained between two tab list readings.
	 *
	 * <p>Deliberately a pure function of the two readings, with no event or multiplier parameter: the tab counter
	 * already reflects whatever multiplier the server applied, so a 2x Powder event needs no handling here — and
	 * cannot accidentally be applied twice.
	 *
	 * @param previous the last reading, or a negative number if there is no baseline yet
	 * @param current  the current reading
	 * @return the amount gained; {@code 0} for the first reading and for drops (powder spent in the HOTM tree)
	 */
	public static long powderGain(long previous, long current) {
		if (previous < 0 || current <= previous) return 0;
		return current - previous;
	}
}
