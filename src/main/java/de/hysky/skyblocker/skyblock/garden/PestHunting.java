package de.hysky.skyblocker.skyblock.garden;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import org.jspecify.annotations.Nullable;

import de.hysky.skyblocker.SkyblockerMod;
import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.skyblock.tabhud.util.PlayerListManager;
import de.hysky.skyblocker.utils.Utils;
import de.hysky.skyblocker.utils.render.title.Title;
import de.hysky.skyblocker.utils.render.title.TitleContainer;
import de.hysky.skyblocker.utils.scheduler.Scheduler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Watches the pest cooldown in Tab and shows a title when it drops below a configured number of seconds.
 *
 * <p>The title fires on the <b>edge</b>, not on the condition: only a reading that went from at or above the
 * threshold to below it announces itself. A cooldown that is simply low announces nothing, so the title stays
 * a signal to act on instead of a permanent fixture on the screen.
 *
 * <p>Tab is the only source here and it is refreshed once a second by {@link PlayerListManager}, so this runs
 * on the same cadence - polling faster would re-read a line that cannot have changed.
 */
public class PestHunting {
	/** No usable reading. Not a duration, so it can never satisfy the "was at or above the threshold" side of the edge. */
	private static final int UNKNOWN = -1;

	/** Reading and threshold both live in seconds; the title is shown for three seconds. */
	private static final int TITLE_TICKS = 60;

	/** How far past a {@code Pests} header a bare {@code Cooldown:} line is still taken as the pest one. */
	private static final int PESTS_SECTION_LINES = 6;

	private static final Pattern PEST_COOLDOWN_PATTERN = Pattern.compile("(?i)^pest\\s+cooldown\\s*:\\s*(?<time>.+)$");
	private static final Pattern PESTS_HEADER_PATTERN = Pattern.compile("(?i)^pests?\\s*:.*$");
	private static final Pattern COOLDOWN_PATTERN = Pattern.compile("(?i)^cooldown\\s*:\\s*(?<time>.+)$");

	/** {@code 12m 30s}, {@code 45s}, {@code 1h 2m 3s} - every unit optional, order fixed. */
	private static final Pattern UNIT_PATTERN = Pattern.compile("(?<amount>\\d+)\\s*(?<unit>[hms])");

	/** {@code 1:30} - minutes and seconds without units. */
	private static final Pattern CLOCK_PATTERN = Pattern.compile("^(?<minutes>\\d+):(?<seconds>\\d{1,2})$");

	private static final Pattern PLAIN_PATTERN = Pattern.compile("^\\d+$");

	/** A cooldown that has run out is worded, not written as a number. */
	private static final List<String> READY_WORDS = List.of("available", "ready", "none", "-");

	/** Previous reading, so the drop below the threshold can be told apart from sitting below it. */
	private static int lastSeconds = UNKNOWN;

	@Init
	public static void init() {
		Scheduler.INSTANCE.scheduleCyclic(PestHunting::update, 20);
		ClientCommandRegistrationCallback.EVENT.register(PestHunting::registerCommands);
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
		dispatcher.register(literal(SkyblockerMod.NAMESPACE)
				.then(literal("pestHunting")
						.then(literal("start").executes(source -> setEnabled(source, true)))
						.then(literal("stop").executes(source -> setEnabled(source, false)))
						.then(literal("set")
								.then(argument("seconds", IntegerArgumentType.integer(1))
										.executes(source -> setThreshold(source, IntegerArgumentType.getInteger(source, "seconds")))))));
	}

	private static int setEnabled(CommandContext<FabricClientCommandSource> context, boolean enabled) {
		SkyblockerConfigManager.update(config -> config.farming.pestHunting.enabled = enabled);

		// A run that starts mid-cooldown must not fire on its first reading: without a previous one there is no
		// edge to speak of, only a value that happens to be low.
		lastSeconds = UNKNOWN;

		context.getSource().sendFeedback(Component.translatable(
				enabled ? "skyblocker.pestHunting.started" : "skyblocker.pestHunting.stopped",
				SkyblockerConfigManager.get().farming.pestHunting.thresholdSeconds));
		return 1;
	}

	private static int setThreshold(CommandContext<FabricClientCommandSource> context, int seconds) {
		SkyblockerConfigManager.update(config -> config.farming.pestHunting.thresholdSeconds = seconds);

		// The old reading was measured against the old threshold, so it says nothing about the new one.
		lastSeconds = UNKNOWN;

		context.getSource().sendFeedback(Component.translatable("skyblocker.pestHunting.set", seconds));
		return 1;
	}

	private static void update() {
		if (!SkyblockerConfigManager.get().farming.pestHunting.enabled || !Utils.isOnSkyblock()) {
			lastSeconds = UNKNOWN;
			return;
		}

		int seconds = readCooldown(PlayerListManager.getPlayerStringList());

		// Tab dropped the line - leaving or reloading the Garden should not read as the cooldown running out.
		if (seconds == UNKNOWN) {
			lastSeconds = UNKNOWN;
			return;
		}

		int threshold = SkyblockerConfigManager.get().farming.pestHunting.thresholdSeconds;

		if (lastSeconds >= threshold && seconds < threshold) {
			TitleContainer.addTitle(new Title(Component.translatable("skyblocker.pestHunting.title", threshold)
					.withStyle(ChatFormatting.RED)), TITLE_TICKS);
		}

		lastSeconds = seconds;
	}

	/**
	 * Pest cooldown as read from Tab, or {@link #UNKNOWN} when no line carries it.
	 *
	 * <p>Two shapes are accepted because the line is not labelled the same everywhere: an explicit
	 * {@code Pest Cooldown:} anywhere in the list, or a bare {@code Cooldown:} shortly after a {@code Pests}
	 * header. The bare form is deliberately kept on a short leash - Tab strips indentation, so nesting is
	 * invisible here and an unbounded search would happily pick up a cooldown from the next section.
	 */
	private static int readCooldown(List<String> lines) {
		int sinceHeader = Integer.MAX_VALUE;

		for (String line : lines) {
			String trimmed = line.strip();

			if (trimmed.isEmpty()) continue;

			Matcher labelled = PEST_COOLDOWN_PATTERN.matcher(trimmed);
			if (labelled.matches()) return parseSeconds(labelled.group("time"));

			if (PESTS_HEADER_PATTERN.matcher(trimmed).matches()) {
				sinceHeader = 0;
				continue;
			}

			if (sinceHeader < PESTS_SECTION_LINES) {
				Matcher bare = COOLDOWN_PATTERN.matcher(trimmed);
				if (bare.matches()) return parseSeconds(bare.group("time"));
			}

			if (sinceHeader != Integer.MAX_VALUE) sinceHeader++;
		}

		return UNKNOWN;
	}

	/**
	 * Duration in seconds, or {@link #UNKNOWN} when the text is not one.
	 *
	 * <p>A spent cooldown is worded rather than counted ({@code Available}), and zero is a real reading - the
	 * pest can spawn now - so it goes through as one instead of being dropped as unparsable.
	 */
	private static int parseSeconds(@Nullable String raw) {
		if (raw == null) return UNKNOWN;

		String text = raw.strip().toLowerCase(Locale.ENGLISH);

		if (text.isEmpty()) return UNKNOWN;
		if (READY_WORDS.contains(text)) return 0;

		Matcher clock = CLOCK_PATTERN.matcher(text);
		if (clock.matches()) {
			return Integer.parseInt(clock.group("minutes")) * 60 + Integer.parseInt(clock.group("seconds"));
		}

		if (PLAIN_PATTERN.matcher(text).matches()) return Integer.parseInt(text);

		Matcher units = UNIT_PATTERN.matcher(text);
		int seconds = 0;
		boolean matched = false;

		while (units.find()) {
			int amount = Integer.parseInt(units.group("amount"));

			seconds += switch (units.group("unit")) {
				case "h" -> amount * 3600;
				case "m" -> amount * 60;
				default -> amount;
			};

			matched = true;
		}

		return matched ? seconds : UNKNOWN;
	}
}
