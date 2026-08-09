package de.hysky.skyblocker.skyblock.dwarven.profittrackers.mining;

import com.mojang.brigadier.Command;
import de.hysky.skyblocker.SkyblockerMod;
import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.config.configs.MiningConfig;
import de.hysky.skyblocker.events.SkyblockEvents;
import de.hysky.skyblocker.skyblock.dwarven.profittrackers.AbstractProfitTracker;
import de.hysky.skyblocker.skyblock.item.tooltip.info.TooltipInfoType;
import de.hysky.skyblocker.skyblock.tabhud.config.WidgetsConfigurationScreen;
import de.hysky.skyblocker.skyblock.tabhud.util.PlayerListManager;
import de.hysky.skyblocker.utils.Area;
import de.hysky.skyblocker.utils.Constants;
import de.hysky.skyblocker.utils.Formatters;
import de.hysky.skyblocker.utils.ItemUtils;
import de.hysky.skyblocker.utils.Location;
import de.hysky.skyblocker.utils.NEURepoManager;
import de.hysky.skyblocker.utils.SkyBlockIcons;
import de.hysky.skyblocker.utils.Utils;
import de.hysky.skyblocker.utils.data.ProfiledData;
import de.hysky.skyblocker.utils.scheduler.Scheduler;
import io.github.moulberry.repo.data.NEUItem;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * The Mining Tracker: coins/h, blocks/s, mining XP/h, powder/h and Pristine procs while mining.
 *
 * <p>Scope is mined materials only — metals, vanilla ores, gemstones, Suspicious Scrap — counted in raw units
 * (see {@link MiningItems}). Counting in units is what makes a Personal or Super Compactor accounting-neutral:
 * turning 160 loose Mithril into one Enchanted Mithril is {@code -160} then {@code +160} units, net zero.
 *
 * <p>Two channels feed the item ledger and they are disjoint by game mechanics, not by heuristics: a mined item
 * goes <i>either</i> into a sack (which produces a {@code [Sacks]} message and never touches the inventory) or,
 * when that sack is full, into the inventory (which produces a slot update and no sack message).
 */
public final class MiningTracker extends AbstractProfitTracker {
	public static final MiningTracker INSTANCE = new MiningTracker();
	private static final Logger LOGGER = LoggerFactory.getLogger("Skyblocker Mining Tracker");
	private static final Minecraft CLIENT = Minecraft.getInstance();

	public static final Set<Location> MINING_LOCATIONS = Set.of(
			Location.DWARVEN_MINES, Location.CRYSTAL_HOLLOWS, Location.GLACITE_MINESHAFTS,
			Location.DEEP_CAVERNS, Location.GOLD_MINE
	);

	/** Reads the player's Mining Fortune out of the tab list Stats widget, for the expected value report. */
	private static final Pattern MINING_FORTUNE_PATTERN =
			Pattern.compile("Mining Fortune: " + SkyBlockIcons.MINING_FORTUNE + "([\\d,]+)");

	/** Ticks the inventory diff ignores after joining a server, while the server sends the whole inventory. */
	private static final int LOBBY_CHANGE_DELAY = 60;
	public static final long SHORT_WINDOW_MS = 5 * 60 * 1000L;
	public static final long LONG_WINDOW_MS = 60 * 60 * 1000L;

	private final ProfiledData<MiningSession> allSessions;
	private MiningSession session = new MiningSession();
	private final MiningEventTracker events = new MiningEventTracker();

	private final DualWindow coins = new DualWindow();
	private final DualWindow blocks = new DualWindow();
	private final DualWindow miningXp = new DualWindow();
	private final DualWindow pristine = new DualWindow();
	private final Map<String, DualWindow> powderWindows = Map.of(
			MiningItems.MITHRIL_POWDER, new DualWindow(),
			MiningItems.GEMSTONE_POWDER, new DualWindow(),
			MiningItems.GLACITE_POWDER, new DualWindow()
	);

	private long lastActivityMs;
	private long lastTickMs;
	private boolean manuallyPaused;
	private boolean changingLobby = true;
	private boolean debug;

	/** Last tab list powder readings; {@code -1} means "not read yet", so the first read never counts as a gain. */
	private long lastMithril = -1;
	private long lastGemstone = -1;
	private long lastGlacite = -1;

	private float miningXpPercent;
	private boolean sawSackMessage;

	private MiningTracker() {
		allSessions = new ProfiledData<>(getRewardFilePath("mining-tracker.json"), MiningSession.CODEC);
	}

	// ------------------------------------------------------------------ setup

	@Init
	public static void init() {
		INSTANCE.allSessions.init();

		ClientReceiveMessageEvents.ALLOW_GAME.register(INSTANCE::onChatMessage);
		ClientPlayerBlockBreakEvents.AFTER.register((_, _, _, _) -> INSTANCE.onBlockBroken(1));
		ClientTickEvents.END_CLIENT_TICK.register(_ -> INSTANCE.tick());
		ClientPlayConnectionEvents.JOIN.register((_, _, _) -> {
			INSTANCE.changingLobby = true;
			INSTANCE.resetPowderBaseline();
		});
		SkyblockEvents.LOCATION_CHANGE.register(_ -> {
			INSTANCE.resetPowderBaseline();
			Scheduler.INSTANCE.schedule(() -> INSTANCE.changingLobby = false, LOBBY_CHANGE_DELAY);
		});
		SkyblockEvents.PROFILE_CHANGE.register(INSTANCE::onProfileChange);
		// Cheap insurance against losing a long session to a crash.
		Scheduler.INSTANCE.scheduleCyclic(() -> {
			if (!INSTANCE.session.isEmpty()) INSTANCE.allSessions.save();
		}, 20 * 60);

		registerCommands();
	}

	private static void registerCommands() {
		// @formatter:off
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> dispatcher.register(
			literal(SkyblockerMod.NAMESPACE)
				.then(literal("miningTracker")
					.then(literal("reset").executes(ctx -> {
						INSTANCE.resetSession();
						ctx.getSource().sendFeedback(Constants.PREFIX.get().append(Component.translatable("skyblocker.miningTracker.reset").withStyle(ChatFormatting.GREEN)));
						return Command.SINGLE_SUCCESS;
					}))
					.then(literal("pause").executes(ctx -> {
						INSTANCE.manuallyPaused = true;
						ctx.getSource().sendFeedback(Constants.PREFIX.get().append(Component.translatable("skyblocker.miningTracker.paused").withStyle(ChatFormatting.YELLOW)));
						return Command.SINGLE_SUCCESS;
					}))
					.then(literal("resume").executes(ctx -> {
						INSTANCE.manuallyPaused = false;
						INSTANCE.lastActivityMs = System.currentTimeMillis();
						ctx.getSource().sendFeedback(Constants.PREFIX.get().append(Component.translatable("skyblocker.miningTracker.resumed").withStyle(ChatFormatting.GREEN)));
						return Command.SINGLE_SUCCESS;
					}))
					.then(literal("list").executes(ctx -> {
						for (Component line : INSTANCE.breakdown()) ctx.getSource().sendFeedback(line);
						return Command.SINGLE_SUCCESS;
					}))
					.then(literal("debug").executes(ctx -> {
						INSTANCE.debug = !INSTANCE.debug;
						INSTANCE.debugBlockBaseline = INSTANCE.session.blocks;
						INSTANCE.debugUnitBaseline = INSTANCE.totalUnits();
						ctx.getSource().sendFeedback(Constants.PREFIX.get().append(
								Component.translatable(INSTANCE.debug ? "skyblocker.miningTracker.debugOn" : "skyblocker.miningTracker.debugOff")
										.withStyle(INSTANCE.debug ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
						return Command.SINGLE_SUCCESS;
					}))
					.then(literal("ev").executes(ctx -> {
						for (Component line : INSTANCE.expectedValueReport()) ctx.getSource().sendFeedback(line);
						return Command.SINGLE_SUCCESS;
					}))
					.then(literal("patterns").executes(ctx -> {
						ctx.getSource().sendFeedback(Component.literal("Mining Tracker regexes:").withStyle(ChatFormatting.GOLD));
						for (Map.Entry<String, String> entry : MiningPatterns.all().entrySet()) {
							ctx.getSource().sendFeedback(Component.literal(entry.getKey() + " = ").withStyle(ChatFormatting.AQUA)
									.append(Component.literal(entry.getValue()).withStyle(ChatFormatting.WHITE)));
						}
						return Command.SINGLE_SUCCESS;
					}))
				)
				.then(literal("hud").then(literal("mining")
					.executes(Scheduler.queueOpenScreenCommand(() -> new WidgetsConfigurationScreen(Location.DWARVEN_MINES, MiningTrackerWidget.INTERNAL_ID, null)))))
		));
		// @formatter:on
	}

	// ----------------------------------------------------------------- config

	private static MiningConfig.MiningTracker config() {
		return SkyblockerConfigManager.get().mining.miningTracker;
	}

	public static boolean isEnabled() {
		return config().enabled;
	}

	public static boolean inMiningLocation() {
		return MINING_LOCATIONS.contains(Utils.getLocation())
				|| Utils.getArea() == Area.DwarvenMines.GLACITE_MINESHAFTS
				|| Utils.getArea() == Area.DwarvenMines.GLACITE_TUNNELS;
	}

	private boolean listening() {
		return isEnabled() && Utils.isOnSkyblock() && inMiningLocation();
	}

	// ------------------------------------------------------------- session I/O

	private void onProfileChange(String prevProfileId, String newProfileId) {
		allSessions.save();
		session = Objects.requireNonNullElseGet(allSessions.computeIfAbsent(MiningSession::new), MiningSession::new);
		clearWindows();
		resetPowderBaseline();
		provisional.clear();
		events.reset();
	}

	private MiningSession session() {
		return session;
	}

	public void resetSession() {
		session.clear();
		clearWindows();
		resetPowderBaseline();
		provisional.clear();
		events.reset();
		manuallyPaused = false;
		debugBlockBaseline = 0;
		debugUnitBaseline = 0;
		allSessions.save();
	}

	private void clearWindows() {
		coins.clear();
		blocks.clear();
		miningXp.clear();
		pristine.clear();
		powderWindows.values().forEach(DualWindow::clear);
	}

	private void resetPowderBaseline() {
		lastMithril = -1;
		lastGemstone = -1;
		lastGlacite = -1;
	}

	// -------------------------------------------------------------- the clock

	private void tick() {
		long now = System.currentTimeMillis();
		long previous = lastTickMs;
		lastTickMs = now;
		if (!listening()) return;

		if (previous != 0) {
			long delta = Math.min(now - previous, 1000L);
			if (isActive(now)) session().activeMs += delta;
		}
		if (CLIENT.player != null && CLIENT.player.tickCount % 20 == 0) {
			updatePowderFromTab();
			expireProvisional(now);
		}
	}

	private boolean isActive(long now) {
		if (manuallyPaused || lastActivityMs == 0) return false;
		return now - lastActivityMs <= config().pauseAfterSeconds * 1000L;
	}

	public boolean isPaused() {
		return !isActive(System.currentTimeMillis());
	}

	private long activeMs() {
		return session.activeMs;
	}

	// -------------------------------------------------- channel: BLOCK_BREAK

	private void onBlockBroken(int amount) {
		if (!listening()) return;
		lastActivityMs = System.currentTimeMillis();
		session().blocks += amount;
		blocks.add(activeMs(), amount);
		if (debug) maybeReportBlockProgress();
	}

	// ------------------------------------------------------ channel: TAB_DIFF

	private void updatePowderFromTab() {
		List<String> lines = PlayerListManager.getPlayerStringList();
		if (lines.isEmpty()) return;
		for (String line : lines) {
			Matcher m = MiningPatterns.TAB_MITHRIL.matcher(line);
			if (m.matches()) {
				lastMithril = applyPowder(MiningItems.MITHRIL_POWDER, lastMithril, parse(m.group(1)));
				continue;
			}
			m = MiningPatterns.TAB_GEMSTONE.matcher(line);
			if (m.matches()) {
				lastGemstone = applyPowder(MiningItems.GEMSTONE_POWDER, lastGemstone, parse(m.group(1)));
				continue;
			}
			m = MiningPatterns.TAB_GLACITE.matcher(line);
			if (m.matches()) {
				lastGlacite = applyPowder(MiningItems.GLACITE_POWDER, lastGlacite, parse(m.group(1)));
			}
		}
	}

	/**
	 * The tab list counter already includes every source of powder and every multiplier the game applied,
	 * 2x Powder events included. That is precisely why nothing here multiplies anything.
	 *
	 * @return the new baseline
	 */
	private long applyPowder(String powderId, long previous, long current) {
		long gained = MiningItems.powderGain(previous, current);
		if (gained > 0) {
			session().addPowder(powderId, gained);
			powderWindows.get(powderId).add(activeMs(), gained);
			if (debug) debugLine(ChatFormatting.DARK_AQUA, "TAB   +" + Formatters.INTEGER_NUMBERS.format(gained) + " " + powderId);
		}
		// A drop means the player spent powder in the HOTM tree; that is not a loss of income, just a new baseline.
		return current;
	}

	private static long parse(String number) {
		try {
			return Formatters.parseNumber(number).longValue();
		} catch (NumberFormatException _) {
			return -1;
		}
	}

	// ----------------------------------------------------------- channel: chat

	@SuppressWarnings("SameReturnValue")
	private boolean onChatMessage(Component text, boolean overlay) {
		if (!isEnabled()) return true;
		String message = ChatFormatting.stripFormatting(text.getString());
		if (message == null) return true;

		if (overlay) {
			if (listening()) onActionBar(message);
			return true;
		}
		if (!listening()) return true;

		if (message.startsWith(MiningPatterns.SACK_PREFIX)) {
			onSackMessage(text);
			return true;
		}

		long now = System.currentTimeMillis();
		if (events.onChatMessage(message, now)) return true;

		Matcher pristineMatcher = MiningPatterns.PRISTINE.matcher(message);
		if (pristineMatcher.matches()) {
			int amount = Math.max(pristineMatcher.group("amount") == null ? 1 : (int) parse(pristineMatcher.group("amount")), 1);
			session().pristineProcs++;
			session().pristineGemstones += amount;
			pristine.add(activeMs(), 1);

			// The PRISTINE! line arrives the instant the proc happens, while the [Sacks] message that carries the
			// same gemstones is batched up to half a minute behind. Book it now so the HUD reacts immediately, and
			// remember it so the sack message settles against it instead of counting it a second time.
			String id = resolveId(pristineMatcher.group("item"));
			if (id != null) creditProvisional(id, amount);
			else if (debug) debugLine(ChatFormatting.DARK_GRAY, "PRIST ? unresolved `"
					+ MiningItems.stripIcons(pristineMatcher.group("item")) + "`");
			return true;
		}

		Matcher pickobulus = MiningPatterns.PICKOBULUS_DESTROYED.matcher(message);
		if (pickobulus.matches()) {
			// The client never sees these break, so without this the block counter silently under-reports.
			onBlockBroken((int) parse(pickobulus.group("blocks")));
			return true;
		}

		Matcher corpse = MiningPatterns.CORPSE_HEADER.matcher(message);
		if (corpse.matches()) {
			session().addCorpse(corpse.group(1));
			lastActivityMs = now;
			return true;
		}

		if (message.equals(MiningPatterns.CHEST_LOCKPICKED)
				|| (config().countNaturalChests && message.equals(MiningPatterns.LOOT_CHEST_COLLECTED))) {
			session().chests++;
			lastActivityMs = now;
		}

		return true;
	}

	private void onActionBar(String message) {
		Matcher matcher = MiningPatterns.MINING_XP.matcher(message);
		if (!matcher.find()) return;
		try {
			double xp = Double.parseDouble(matcher.group("xp"));
			session().miningXp += xp;
			miningXp.add(activeMs(), xp);
			if (matcher.group("percent") != null) {
				miningXpPercent = Formatters.parseNumber(matcher.group("percent")).floatValue();
			} else if (matcher.group("current") != null) {
				float max = Formatters.parseNumber(matcher.group("max").replace("k", "")).floatValue();
				if (matcher.group("max").endsWith("k")) max *= 1000;
				if (max > 0) miningXpPercent = Formatters.parseNumber(matcher.group("current")).floatValue() / max * 100;
			}
		} catch (NumberFormatException e) {
			LOGGER.warn("[Skyblocker Mining Tracker] Failed to parse mining xp from `{}`", message, e);
		}
	}

	// ----------------------------------------------------------- channel: SACK

	private void onSackMessage(Component text) {
		if (text.getSiblings().isEmpty()) return;
		HoverEvent hoverEvent = text.getSiblings().getFirst().getStyle().getHoverEvent();
		if (!(hoverEvent instanceof HoverEvent.ShowText(Component hoverText))) return;

		sawSackMessage = true;
		String hover = ChatFormatting.stripFormatting(hoverText.getString());
		if (hover == null) return;

		Matcher matcher = MiningPatterns.SACK_HOVER_LINE.matcher(hover);
		while (matcher.find()) {
			// Removals are items leaving the sack (selling, crafting). They were already counted as income when
			// they arrived, so subtracting them here would double-penalise the session.
			if (!matcher.group(1).equals("+")) continue;
			int amount = (int) parse(matcher.group(2));
			if (amount <= 0) continue;
			String id = resolveId(matcher.group(3));
			if (id == null) {
				if (debug) debugLine(ChatFormatting.DARK_GRAY, "SACK  ? unresolved `" + MiningItems.stripIcons(matcher.group(3)) + "`");
				continue;
			}
			int fresh = settleProvisional(id, amount);
			if (fresh > 0) credit("SACK", id, fresh);
			else if (debug) debugLine(ChatFormatting.DARK_GRAY, "SACK  = " + amount + " " + id + " already booked by PRISTINE");
		}
	}

	// ---------------------------------------------- provisional credit (Pristine)

	/** How long a provisional credit waits for its sack message before it is assumed to have gone elsewhere. */
	private static final long PROVISIONAL_TTL_MS = 90_000L;

	private static final class Provisional {
		int amount;
		long expiresAt;
	}

	/** Exact item id → amount already booked ahead of the sack message. Keyed by exact id, not base material,
	 *  so a batch of Rough cannot settle a Pristine proc's Flawed. */
	private final Object2ObjectOpenHashMap<String, Provisional> provisional = new Object2ObjectOpenHashMap<>();

	private void creditProvisional(String id, int amount) {
		credit("PRIST", id, amount);
		if (!MiningItems.isMaterial(id)) return;
		Provisional entry = provisional.computeIfAbsent(id, _ -> new Provisional());
		entry.amount += amount;
		entry.expiresAt = System.currentTimeMillis() + PROVISIONAL_TTL_MS;
	}

	/**
	 * @return how much of a sack credit is genuinely new, after settling whatever was already booked provisionally
	 */
	private int settleProvisional(String id, int amount) {
		Provisional entry = provisional.get(id);
		if (entry == null) return amount;
		if (System.currentTimeMillis() > entry.expiresAt) {
			// The gemstones never reached the sack — a full sack, or the message was filtered. The provisional
			// booking stands on its own and this sack credit is unrelated.
			provisional.remove(id);
			return amount;
		}
		int settled = Math.min(entry.amount, amount);
		entry.amount -= settled;
		if (entry.amount <= 0) provisional.remove(id);
		return amount - settled;
	}

	private void expireProvisional(long now) {
		provisional.values().removeIf(entry -> now > entry.expiresAt);
	}

	// --------------------------------------------------------- channel: PICKUP

	/**
	 * Called from {@code AbstractContainerMenuMixin} on every slot update.
	 *
	 * <p>Signed on purpose. A full sack pushes mined ore into the inventory, where a Personal Compactor eventually
	 * eats 160 of it and hands back one enchanted item; in raw units those two events are {@code -160} and
	 * {@code +160}, so the compaction costs nothing and earns nothing, which is exactly right — the ore was already
	 * booked when it was picked up.
	 */
	public void onSlotUpdate(AbstractContainerMenu menu, int slot, ItemStack newStack) {
		if (changingLobby || CLIENT.player == null) return;
		// Only the player's own inventory menu. Any other container — the HOTM tree, the bazaar, a sack, the
		// excavator — numbers its slots differently, so reading slot 20 of a chest against inventory index 20
		// compares two unrelated stacks and invents losses out of nothing.
		if (menu != CLIENT.player.inventoryMenu) return;
		if (!listening()) return;
		if (slot < 9 || slot >= 45) return;
		int index = slot >= 36 ? slot - 36 : slot;
		if (index == 8) return; // SkyBlock menu slot

		ItemStack oldStack = CLIENT.player.getInventory().getNonEquipmentItems().get(index);
		String oldId = oldStack.getItem() == Items.AIR ? null : oldStack.getSkyblockApiId();
		String newId = newStack.getItem() == Items.AIR ? null : newStack.getSkyblockApiId();

		// A slot can change identity in one update (the compactor replaces a stack of ore with the enchanted form).
		// Treat that as the old stack leaving and the new one arriving.
		if (oldId != null && !oldId.equals(newId)) {
			pickup(oldId, -oldStack.getCount());
			if (newId != null) pickup(newId, newStack.getCount());
			return;
		}
		if (newId == null) return;
		int diff = newStack.getCount() - oldStack.getCount();
		if (diff != 0) pickup(newId, diff);
	}

	private void pickup(String id, int amount) {
		if (!MiningItems.isMaterial(id)) return;
		long units = MiningItems.toUnits(id, amount);
		if (units == 0) return;
		applyUnits("PICKUP", id, amount, units);
	}

	// ------------------------------------------------------------- accounting

	private void credit(String channel, String id, int amount) {
		if (!MiningItems.isMaterial(id)) {
			if (debug) debugLine(ChatFormatting.DARK_GRAY, channel + "  - not a tracked material: " + id);
			return;
		}
		applyUnits(channel, id, amount, MiningItems.toUnits(id, amount));
	}

	private void applyUnits(String channel, String id, int amount, long units) {
		MiningItems.Tier tier = MiningItems.tier(id);
		if (tier == null) return;

		// A loss can never exceed what this session actually booked. Without this floor a single stray negative —
		// a mis-read slot, an item that was in the inventory before the session started — drags the coin total and
		// the rate windows below zero and keeps them there for the length of the window.
		long held = session().units(tier.rawId());
		long applied = units < 0 ? Math.max(units, -held) : units;
		if (applied == 0) {
			if (debug && units != 0) {
				debugLine(ChatFormatting.DARK_GRAY, String.format("%-6s %+d %s ignored, nothing booked to take from",
						channel, amount, id));
			}
			return;
		}

		session().addUnits(tier.rawId(), applied);
		if (applied > 0) lastActivityMs = System.currentTimeMillis();

		double value = unitPrice(tier.rawId()) * applied;
		if (value != 0) coins.add(activeMs(), value);

		if (debug) {
			debugLine(applied > 0 ? ChatFormatting.GREEN : ChatFormatting.RED,
					String.format("%-6s %+d %s = %+d %s (%s coins)",
							channel, amount, id, applied, tier.rawId(),
							Formatters.INTEGER_NUMBERS.format(value)));
		}
	}

	private @Nullable String resolveId(String rawName) {
		String mapped = MiningItems.idForName(rawName);
		if (mapped != null) return mapped;
		String clean = MiningItems.stripIcons(rawName);
		if (NEURepoManager.isLoading()) return null;
		return NEURepoManager.getItemByName(clean).stream()
				.filter(Objects::nonNull)
				.findFirst()
				.map(NEUItem::getSkyblockItemId)
				.orElse(null);
	}

	// ---------------------------------------------------------------- pricing

	/**
	 * Value of one raw unit, taken as the best per-unit price across the whole family.
	 *
	 * <p>An Enchanted Mithril is usually worth a little more than 160 loose Mithril, and that premium is real
	 * income — you would compact before selling anyway — so the family maximum is the honest number.
	 */
	public static double unitPrice(String rawId) {
		double best = 0;
		for (MiningItems.Tier tier : MiningItems.family(rawId)) {
			double price = formPrice(tier.id());
			if (price <= 0) continue;
			double perUnit = price / tier.units();
			if (perUnit > best) best = perUnit;
		}
		return best;
	}

	private static double formPrice(String id) {
		return switch (config().priceSource) {
			case INSTASELL -> bazaarInstasell(id);
			case NPC -> npcPrice(id);
			case BEST -> Math.max(bazaarInstasell(id), npcPrice(id));
		};
	}

	/** Bazaar sell price: what you actually get right now by selling into the highest buy order. */
	private static double bazaarInstasell(String id) {
		return ItemUtils.getItemPrice(id, false).orElse(0);
	}

	private static double npcPrice(String id) {
		Object2DoubleMap<String> npc = TooltipInfoType.NPC.getData();
		return npc == null ? 0 : npc.getOrDefault(id, 0);
	}

	// ---------------------------------------------------------------- getters

	public MiningSession sessionData() {
		return session;
	}

	public MiningEventTracker eventTracker() {
		return events;
	}

	public boolean isDebug() {
		return debug;
	}

	/** Total coin value of everything mined this session at current prices. */
	public double totalCoins() {
		double total = 0;
		for (Object2LongMap.Entry<String> entry : session.items.object2LongEntrySet()) {
			total += unitPrice(entry.getKey()) * entry.getLongValue();
		}
		if (config().valuePowder) {
			for (String powderId : MiningItems.POWDERS) {
				total += session.powder(powderId) * config().coinsPerPowder;
			}
		}
		return total;
	}

	public long totalUnits() {
		long total = 0;
		for (long units : session.items.values()) total += units;
		return total;
	}

	public double coinsPerHour(boolean shortWindow) {
		return coins.perHour(activeMs(), shortWindow);
	}

	public double blocksPerSecond(boolean shortWindow) {
		double perHour = blocks.perHour(activeMs(), shortWindow);
		return Double.isNaN(perHour) ? Double.NaN : perHour / 3600d;
	}

	public double miningXpPerHour(boolean shortWindow) {
		return miningXp.perHour(activeMs(), shortWindow);
	}

	public float miningXpPercent() {
		return Math.clamp(miningXpPercent, 0, 100);
	}

	public double pristinePerHour(boolean shortWindow) {
		return pristine.perHour(activeMs(), shortWindow);
	}

	public double powderPerHour(String powderId, boolean shortWindow) {
		DualWindow window = powderWindows.get(powderId);
		return window == null ? 0 : window.perHour(activeMs(), shortWindow);
	}

	public boolean sawSackMessage() {
		return sawSackMessage;
	}

	/** The session's most valuable materials, richest first. Amounts are raw units. */
	public List<TopDrop> topDrops(int limit) {
		ObjectArrayList<TopDrop> drops = new ObjectArrayList<>();
		for (Object2LongMap.Entry<String> entry : session.items.object2LongEntrySet()) {
			double value = unitPrice(entry.getKey()) * entry.getLongValue();
			if (value <= 0) continue;
			drops.add(new TopDrop(entry.getKey(), entry.getLongValue(), value));
		}
		drops.sort(Comparator.comparingDouble(TopDrop::value).reversed());
		return drops.size() <= limit ? drops : drops.subList(0, limit);
	}

	// ------------------------------------------------------------------ debug

	private long debugBlockBaseline;
	private long debugUnitBaseline;

	private void debugLine(ChatFormatting colour, String text) {
		if (CLIENT.player == null) return;
		CLIENT.player.sendSystemMessage(Component.literal("[MT] ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal(text).withStyle(colour)));
	}

	/** Every 50 blocks, print how the actual drop rate compares to what Mining Fortune says it should be. */
	private void maybeReportBlockProgress() {
		long sinceBaseline = session.blocks - debugBlockBaseline;
		if (sinceBaseline <= 0 || sinceBaseline % 50 != 0) return;
		for (Component line : expectedValueReport()) {
			if (CLIENT.player != null) CLIENT.player.sendSystemMessage(line);
		}
	}

	/**
	 * @return the player's Mining Fortune from the tab list Stats widget, or empty if it is not shown there
	 */
	public static Optional<Integer> miningFortune() {
		return PlayerListManager.getPlayerStringList().stream()
				.map(MINING_FORTUNE_PATTERN::matcher)
				.filter(Matcher::find)
				.findFirst()
				.map(m -> (int) parse(m.group(1)))
				.filter(fortune -> fortune >= 0);
	}

	/**
	 * Compares measured drops against what Mining Fortune predicts.
	 *
	 * <p>Mining Fortune gives {@code 1 + fortune/100} times the block's base drop. Since the tracker cannot know
	 * which block you just broke, it reports the <i>implied base drop</i> — measured units per block divided by the
	 * fortune multiplier. For plain ore that should land near 1; for gemstone blocks near their base roll. A number
	 * well under that means drops are going somewhere the tracker cannot see.
	 */
	public List<Component> expectedValueReport() {
		ObjectArrayList<Component> lines = new ObjectArrayList<>();
		long blocksSince = session.blocks - debugBlockBaseline;
		long unitsSince = totalUnits() - debugUnitBaseline;

		lines.add(Component.literal("[MT] ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal("blocks " + Formatters.INTEGER_NUMBERS.format(blocksSince)
						+ " | units " + Formatters.INTEGER_NUMBERS.format(unitsSince)).withStyle(ChatFormatting.GRAY)));

		if (blocksSince <= 0) return lines;
		double perBlock = (double) unitsSince / blocksSince;

		Optional<Integer> fortune = miningFortune();
		if (fortune.isEmpty()) {
			lines.add(Component.literal("[MT] ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(String.format("%.2f units/block | Mining Fortune not in /tab (enable the Stats widget)", perBlock))
							.withStyle(ChatFormatting.YELLOW)));
			return lines;
		}

		double multiplier = 1 + fortune.get() / 100d;
		double impliedBase = perBlock / multiplier;
		lines.add(Component.literal("[MT] ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal(String.format("%.2f units/block | fortune %d = x%.2f | implied base drop %.2f",
						perBlock, fortune.get(), multiplier, impliedBase)).withStyle(ChatFormatting.AQUA)));
		lines.add(Component.literal("[MT] ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal(String.format("EV at current prices: %s coins/block, %s coins so far",
						Formatters.INTEGER_NUMBERS.format(coinsPerBlock(unitsSince, blocksSince)),
						Formatters.INTEGER_NUMBERS.format(totalCoins()))).withStyle(ChatFormatting.GOLD)));
		return lines;
	}

	private double coinsPerBlock(long unitsSince, long blocksSince) {
		if (blocksSince <= 0 || totalUnits() <= 0) return 0;
		// Value the measured units at the session's blended price per unit.
		return totalCoins() / totalUnits() * unitsSince / blocksSince;
	}

	private List<Component> breakdown() {
		ObjectArrayList<Component> lines = new ObjectArrayList<>();
		lines.add(Component.translatable("skyblocker.miningTracker.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		lines.add(Component.literal("Active: " + formatDuration(session.activeMs)).withStyle(ChatFormatting.GRAY));
		lines.add(Component.literal("Coins: " + Formatters.INTEGER_NUMBERS.format(totalCoins())).withStyle(ChatFormatting.GOLD));
		lines.add(Component.literal("Blocks: " + Formatters.INTEGER_NUMBERS.format(session.blocks)).withStyle(ChatFormatting.GRAY));
		lines.add(Component.literal("Mining XP: " + Formatters.INTEGER_NUMBERS.format(session.miningXp)).withStyle(ChatFormatting.AQUA));
		lines.add(Component.literal("Pristine procs: " + session.pristineProcs + " (" + session.pristineGemstones + " gemstones)").withStyle(ChatFormatting.LIGHT_PURPLE));
		for (String powderId : List.of(MiningItems.MITHRIL_POWDER, MiningItems.GEMSTONE_POWDER, MiningItems.GLACITE_POWDER)) {
			lines.add(Component.literal("  " + powderId + ": " + Formatters.INTEGER_NUMBERS.format(session.powder(powderId))).withStyle(ChatFormatting.DARK_AQUA));
		}
		for (TopDrop drop : topDrops(15)) {
			lines.add(Component.literal(String.format("  %s %s u = %s", drop.rawId(),
					Formatters.INTEGER_NUMBERS.format(drop.units()),
					Formatters.INTEGER_NUMBERS.format(drop.value()))).withStyle(ChatFormatting.WHITE));
		}
		lines.addAll(expectedValueReport());
		return lines;
	}

	public static String formatDuration(long millis) {
		long totalSeconds = millis / 1000;
		long hours = totalSeconds / 3600;
		long minutes = totalSeconds % 3600 / 60;
		long seconds = totalSeconds % 60;
		return hours > 0
				? String.format("%dh %02dm %02ds", hours, minutes, seconds)
				: String.format("%dm %02ds", minutes, seconds);
	}

	public record TopDrop(String rawId, long units, double value) {}

	/** A short and a long window fed by the same events, so the HUD can show both at once. */
	private static final class DualWindow {
		private final RollingWindow shortWindow = new RollingWindow(SHORT_WINDOW_MS);
		private final RollingWindow longWindow = new RollingWindow(LONG_WINDOW_MS);

		void add(long activeMs, double value) {
			shortWindow.add(activeMs, value);
			longWindow.add(activeMs, value);
		}

		/**
		 * The 5 minute figure is reactive (measured from its oldest surviving sample, so it spikes on a hot vein
		 * and settles), the 1 hour figure is the settled session average. Two different questions, two answers.
		 */
		double perHour(long activeMs, boolean useShort) {
			return useShort ? shortWindow.perHourSinceOldest(activeMs) : longWindow.perHour(activeMs);
		}

		void clear() {
			shortWindow.clear();
			longWindow.clear();
		}
	}
}
