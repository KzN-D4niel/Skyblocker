package de.hysky.skyblocker.skyblock.dwarven.profittrackers.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Powder is read as a running total from the tab list, which already has every multiplier the server applied
 * baked in. These tests pin down that the tracker never applies one itself.
 */
class PowderMultiplierTest {
	@Test
	void gainIsTheRawDifferenceBetweenReadings() {
		assertEquals(2000, MiningItems.powderGain(1000, 3000));
		assertEquals(1, MiningItems.powderGain(0, 1));
	}

	@Test
	void doublePowderEventDoesNotChangeTheRecordedGain() {
		MiningEventTracker events = new MiningEventTracker();
		long now = 1_000_000L;

		long normal = MiningItems.powderGain(1000, 3000);
		assertFalse(events.isDoublePowder(now));

		events.onChatMessage("                          2X POWDER STARTED!", now);
		assertTrue(events.isDoublePowder(now));

		// The server hands us doubled numbers during the event; the tab delta is the whole truth. Same inputs in,
		// same amount out — the event flag is not part of the arithmetic.
		long duringEvent = MiningItems.powderGain(1000, 3000);
		assertEquals(normal, duringEvent);

		// And 4000 gained really is 4000, not 8000.
		assertEquals(4000, MiningItems.powderGain(10_000, 14_000));
	}

	@Test
	void eventEndsCleanly() {
		MiningEventTracker events = new MiningEventTracker();
		long now = 5_000L;
		events.onChatMessage("                          2X POWDER STARTED!", now);
		assertTrue(events.isDoublePowder(now));
		events.onChatMessage("                           2X POWDER ENDED!", now + 1000);
		assertFalse(events.isDoublePowder(now + 1000));
	}

	@Test
	void staleEventExpiresIfTheEndMessageWasMissed() {
		MiningEventTracker events = new MiningEventTracker();
		events.onChatMessage("                          2X POWDER STARTED!", 0L);
		assertTrue(events.isDoublePowder(60 * 60 * 1000L));
		assertFalse(events.isDoublePowder(3 * 60 * 60 * 1000L), "a missed ENDED message must not pin the flag on forever");
	}

	@Test
	void nonMiningEventsAreIgnored() {
		MiningEventTracker events = new MiningEventTracker();
		assertFalse(events.onChatMessage("                       SPOOKY FESTIVAL STARTED!", 0L));
		assertFalse(events.isDoublePowder(0L));
	}

	@Test
	void firstReadingEstablishesABaselineInsteadOfCountingEverything() {
		// Logging in with 4.2 million mithril must not book 4.2 million as this session's income.
		assertEquals(0, MiningItems.powderGain(-1, 4_200_000));
	}

	@Test
	void spendingPowderInHotmIsNotNegativeIncome() {
		assertEquals(0, MiningItems.powderGain(50_000, 10_000));
	}

	@Test
	void theEventTrackerExposesNoNumericMultiplier() {
		// A structural guard: if someone ever adds a getMultiplier() here, the temptation to use it follows.
		boolean hasNumericGetter = Arrays.stream(MiningEventTracker.class.getDeclaredMethods())
				.filter(m -> Modifier.isPublic(m.getModifiers()))
				.anyMatch(m -> m.getParameterCount() <= 1
						&& (m.getReturnType() == double.class || m.getReturnType() == float.class
						|| m.getReturnType() == int.class || m.getReturnType() == long.class));
		assertFalse(hasNumericGetter, "MiningEventTracker must stay informational, not arithmetic");
	}
}
