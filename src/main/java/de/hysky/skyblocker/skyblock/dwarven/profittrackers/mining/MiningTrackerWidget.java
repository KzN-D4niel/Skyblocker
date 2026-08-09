package de.hysky.skyblocker.skyblock.dwarven.profittrackers.mining;

import de.hysky.skyblocker.annotations.RegisterWidget;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.config.configs.MiningConfig;
import de.hysky.skyblocker.skyblock.itemlist.ItemRepository;
import de.hysky.skyblocker.skyblock.tabhud.util.Ico;
import de.hysky.skyblocker.skyblock.tabhud.widget.ElementBasedWidget;
import de.hysky.skyblocker.skyblock.tabhud.widget.element.PlainTextElement;
import de.hysky.skyblocker.utils.FlexibleItemStack;
import de.hysky.skyblocker.utils.Formatters;
import de.hysky.skyblocker.utils.Location;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The Mining Tracker HUD. Every line is individually toggleable; rates are shown for both windows at once
 * ({@code 5m} first because that is the one that reacts, {@code 1h} second because that is the one that is true).
 */
@RegisterWidget
public class MiningTrackerWidget extends ElementBasedWidget {
	public static final String INTERNAL_ID = "mining_tracker";
	private static final MutableComponent TITLE = Component.literal("Mining").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
	private static final Set<Location> AVAILABLE_LOCATIONS = MiningTracker.MINING_LOCATIONS;

	public MiningTrackerWidget() {
		super(TITLE, ChatFormatting.AQUA.getColor(), INTERNAL_ID);
		update();
	}

	@Override
	public boolean shouldUpdateBeforeRendering() {
		return true;
	}

	@Override
	public void updateContent() {
		MiningConfig.MiningTracker config = SkyblockerConfigManager.get().mining.miningTracker;
		MiningTracker tracker = MiningTracker.INSTANCE;
		MiningSession session = tracker.sessionData();

		if (config.showEvent) {
			List<String> labels = tracker.eventTracker().activeLabels(System.currentTimeMillis());
			if (!labels.isEmpty()) {
				addSimpleIcoText(Ico.NTAG, "Event: ", ChatFormatting.LIGHT_PURPLE, String.join(", ", labels));
			}
		}

		if (config.showCoinsPerHour) {
			addSimpleIcoText(Ico.GOLD, "Coins/h ", ChatFormatting.GOLD, rates(tracker.coinsPerHour(false), tracker.coinsPerHour(true)));
		}
		if (config.showCoinsTotal) {
			addSimpleIcoText(Ico.GOLD, "Coins ", ChatFormatting.GOLD, Formatters.SHORT_FLOAT_NUMBERS.format(tracker.totalCoins()));
		}
		if (config.showBlocksPerSecond) {
			double longRate = tracker.blocksPerSecond(false);
			double shortRate = tracker.blocksPerSecond(true);
			addSimpleIcoText(Ico.STONE_SWORD, "Blocks/s ", ChatFormatting.GRAY,
					format(longRate, Formatters.DOUBLE_NUMBERS::format) + " / " + format(shortRate, Formatters.DOUBLE_NUMBERS::format));
		}
		if (config.showBlocksTotal) {
			addSimpleIcoText(Ico.STONE_SWORD, "Blocks ", ChatFormatting.GRAY, Formatters.INTEGER_NUMBERS.format(session.blocks));
		}
		if (config.showMiningXp) {
			addSimpleIcoText(Ico.LANTERN, "Mining XP/h ", ChatFormatting.AQUA, rates(tracker.miningXpPerHour(false), tracker.miningXpPerHour(true)));
		}

		if (config.showMithrilPowder) {
			addPowderLine("Mithril", MiningItems.MITHRIL_POWDER, ItemRepository.getItemStack("MITHRIL_ORE", Ico.MITHRIL), ChatFormatting.DARK_GREEN, tracker, session);
		}
		if (config.showGemstonePowder) {
			addPowderLine("Gemstone", MiningItems.GEMSTONE_POWDER, ItemRepository.getItemStack("GEMSTONE_COLLECTION", Ico.GEMSTONE), ChatFormatting.LIGHT_PURPLE, tracker, session);
		}
		if (config.showGlacitePowder) {
			addPowderLine("Glacite", MiningItems.GLACITE_POWDER, ItemRepository.getItemStack("GLACITE", Ico.ICE), ChatFormatting.AQUA, tracker, session);
		}
		if (config.showPowderTotal) {
			long total = session.powder(MiningItems.MITHRIL_POWDER)
					+ session.powder(MiningItems.GEMSTONE_POWDER)
					+ session.powder(MiningItems.GLACITE_POWDER);
			addSimpleIcoText(Ico.B_POWDER, "Powder total ", ChatFormatting.WHITE, Formatters.INTEGER_NUMBERS.format(total));
		}

		if (config.showPristine) {
			addSimpleIcoText(Ico.GEMSTONE, "Pristine/h ", ChatFormatting.LIGHT_PURPLE,
					rates(tracker.pristinePerHour(false), tracker.pristinePerHour(true))
							+ " (" + session.pristineProcs + ")");
		}

		if (config.showTopDrops) {
			List<MiningTracker.TopDrop> top = tracker.topDrops(5);
			for (MiningTracker.TopDrop drop : top) {
				FlexibleItemStack icon = ItemRepository.getItemStack(drop.rawId());
				addSimpleIcoText(icon == null ? Ico.CHEST : icon,
						displayName(drop.rawId()) + " x" + Formatters.SHORT_INTEGER_NUMBERS.format(drop.units()) + " ",
						ChatFormatting.YELLOW,
						Formatters.SHORT_FLOAT_NUMBERS.format(drop.value()));
			}
		}

		if (config.showSessionTime) {
			String time = MiningTracker.formatDuration(session.activeMs);
			if (tracker.isPaused()) time += " (paused)";
			addSimpleIcoText(Ico.CLOCK, "Session ", ChatFormatting.GRAY, time);
		}

		if (session.blocks > 200 && !tracker.sawSackMessage()) {
			addComponent(new PlainTextElement(Component.translatable("skyblocker.miningTracker.noSackMessages").withStyle(ChatFormatting.RED)));
		}

		if (isEmpty()) {
			addComponent(new PlainTextElement(Component.translatable("skyblocker.miningTracker.waiting").withStyle(ChatFormatting.GRAY)));
		}
	}

	private void addPowderLine(String label, String powderId, FlexibleItemStack icon, ChatFormatting color,
			MiningTracker tracker, MiningSession session) {
		String value = rates(tracker.powderPerHour(powderId, false), tracker.powderPerHour(powderId, true))
				+ " (" + Formatters.SHORT_INTEGER_NUMBERS.format(session.powder(powderId)) + ")";
		addSimpleIcoText(icon, label + "/h ", color, value);
	}

	/** {@code "940k / 1.2M"} — the steady 1 hour figure first, the reactive 5 minute one second. */
	private static String rates(double longRate, double shortRate) {
		return format(longRate, Formatters.SHORT_FLOAT_NUMBERS::format) + " / " + format(shortRate, Formatters.SHORT_FLOAT_NUMBERS::format);
	}

	private static String format(double value, java.util.function.DoubleFunction<String> formatter) {
		return Double.isNaN(value) ? "—" : formatter.apply(value);
	}

	private static String displayName(String id) {
		FlexibleItemStack stack = ItemRepository.getItemStack(id);
		if (stack == null) return id;
		try {
			return stack.getStackOrThrow().getHoverName().getString();
		} catch (RuntimeException _) {
			return id;
		}
	}

	@Override
	public boolean shouldRender(Location location) {
		return super.shouldRender(location) && MiningTracker.isEnabled();
	}

	@Override
	public Set<Location> availableLocations() {
		return AVAILABLE_LOCATIONS;
	}

	@Override
	public void setEnabledIn(Location location, boolean enabled) {
		if (!AVAILABLE_LOCATIONS.contains(location)) return;
		SkyblockerConfigManager.update(config -> config.mining.miningTracker.enabled = enabled);
	}

	@Override
	public boolean isEnabledIn(Location location) {
		return AVAILABLE_LOCATIONS.contains(location) && SkyblockerConfigManager.get().mining.miningTracker.enabled;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("skyblocker.miningTracker.title");
	}
}
