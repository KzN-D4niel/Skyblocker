package de.hysky.skyblocker.skyblock.dwarven.profittrackers.mining;

/**
 * The single mechanism that is allowed to account for a given thing.
 *
 * <p>The mining tracker sees the same item arrive through several mechanisms at once — a Flawed Ruby out of a
 * Powder Chest shows up in the chest chat block, in the {@code [Sacks]} message, and (if the sack is full) as an
 * inventory diff. Rather than trying to correlate them, every tracked item is routed to exactly one channel and
 * events from any other channel are dropped on the floor. {@code SourceChannelDisjointTest} enforces this.
 */
public enum SourceChannel {
	/** {@code [Sacks]} chat messages. Canonical for everything that has a sack. */
	SACK,
	/** Inventory slot diffs. Canonical for the non-sackable valuables in {@link MiningItems#PICKUP_INCOME}, and for costs. */
	PICKUP,
	/** Powder/loot chest chat blocks. Counts chests and attributes loot; contributes <b>no</b> coins. */
	CHEST_MSG,
	/** Frozen corpse chat blocks. Counts corpses, charges the key, attributes loot; contributes no coins. */
	CORPSE_MSG,
	/** Tab list powder counters. Canonical — and only — source for all three powders. */
	TAB_DIFF,
	/** One-off chat lines (PRISTINE!, RARE DROP!, event start/end). Informational, never coins. */
	CHAT_PATTERN,
	/** Client block break events plus {@code Your Pickobulus destroyed N blocks!}. */
	BLOCK_BREAK,
	/** Action bar skill XP. */
	ACTION_BAR
}
