package de.hysky.skyblocker.skyblock.dwarven.profittrackers.mining;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.hysky.skyblocker.utils.CodecUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/**
 * The persisted half of a mining session: running totals for one player on one profile.
 *
 * <p>Items are stored as <b>raw units of a base material</b> rather than as item stacks — see {@link MiningItems}.
 * That is what makes compaction free: 160 Mithril and 1 Enchanted Mithril are the same 160 units, so turning one
 * into the other moves nothing.
 *
 * <p>Rolling windows are deliberately not stored. Totals survive a relog and a lobby change; the per-hour rates
 * warm back up over the next few minutes.
 */
public final class MiningSession {
	public static final Codec<MiningSession> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			CodecUtils.object2LongMapCodec(Codec.STRING).optionalFieldOf("units", new Object2LongOpenHashMap<>()).forGetter(s -> s.items),
			CodecUtils.object2LongMapCodec(Codec.STRING).optionalFieldOf("powder", new Object2LongOpenHashMap<>()).forGetter(s -> s.powder),
			CodecUtils.object2IntMapCodec(Codec.STRING).optionalFieldOf("corpses", new Object2IntOpenHashMap<>()).forGetter(s -> s.corpses),
			Codec.LONG.optionalFieldOf("blocks", 0L).forGetter(s -> s.blocks),
			Codec.LONG.optionalFieldOf("activeMs", 0L).forGetter(s -> s.activeMs),
			Codec.DOUBLE.optionalFieldOf("miningXp", 0d).forGetter(s -> s.miningXp),
			Codec.INT.optionalFieldOf("pristineProcs", 0).forGetter(s -> s.pristineProcs),
			Codec.INT.optionalFieldOf("pristineGemstones", 0).forGetter(s -> s.pristineGemstones),
			Codec.INT.optionalFieldOf("chests", 0).forGetter(s -> s.chests)
	).apply(instance, MiningSession::new));

	/** Base material id → raw units gained this session. Fed by the {@code SACK} and {@code PICKUP} channels. */
	public final Object2LongMap<String> items;
	/** Powder id → amount gained. Fed exclusively by the {@code TAB_DIFF} channel. */
	public final Object2LongMap<String> powder;
	/** Corpse type name → count. */
	public final Object2IntMap<String> corpses;

	public long blocks;
	public long activeMs;
	public double miningXp;
	/** Number of {@code PRISTINE!} messages. */
	public int pristineProcs;
	/** Total gemstones produced by those procs — one proc can yield 15-60 at once. */
	public int pristineGemstones;
	public int chests;

	public MiningSession() {
		this(new Object2LongOpenHashMap<>(), new Object2LongOpenHashMap<>(), new Object2IntOpenHashMap<>(),
				0L, 0L, 0d, 0, 0, 0);
	}

	private MiningSession(Object2LongMap<String> items, Object2LongMap<String> powder, Object2IntMap<String> corpses,
			long blocks, long activeMs, double miningXp, int pristineProcs, int pristineGemstones, int chests) {
		// The codec hands back immutable maps, so copy them into something we can actually mutate.
		this.items = new Object2LongOpenHashMap<>(items);
		this.powder = new Object2LongOpenHashMap<>(powder);
		this.corpses = new Object2IntOpenHashMap<>(corpses);
		this.blocks = blocks;
		this.activeMs = activeMs;
		this.miningXp = miningXp;
		this.pristineProcs = pristineProcs;
		this.pristineGemstones = pristineGemstones;
		this.chests = chests;
	}

	/** Signed on purpose: a negative delta is how compaction cancels the raw ore it consumed. */
	public void addUnits(String rawId, long units) {
		long total = items.getOrDefault(rawId, 0L) + units;
		if (total <= 0) items.removeLong(rawId);
		else items.put(rawId, total);
	}

	public long units(String rawId) {
		return items.getOrDefault(rawId, 0L);
	}

	public void addPowder(String id, long amount) {
		powder.mergeLong(id, amount, Long::sum);
	}

	public void addCorpse(String type) {
		corpses.mergeInt(type, 1, Integer::sum);
	}

	public long powder(String id) {
		return powder.getOrDefault(id, 0L);
	}

	public void clear() {
		items.clear();
		powder.clear();
		corpses.clear();
		blocks = 0;
		activeMs = 0;
		miningXp = 0;
		pristineProcs = 0;
		pristineGemstones = 0;
		chests = 0;
	}

	public boolean isEmpty() {
		return items.isEmpty() && powder.isEmpty() && blocks == 0 && activeMs == 0;
	}
}
