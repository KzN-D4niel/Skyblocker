package de.hysky.skyblocker.skyblock.dwarven.profittrackers.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The window is driven by an explicit active-time value rather than a clock, which is what makes it testable —
 * and is also what makes the auto-pause work in the first place.
 */
class RollingWindowTest {
	private static final long FIVE_MINUTES = 5 * 60 * 1000L;
	private static final long ONE_HOUR = 60 * 60 * 1000L;

	@Test
	void reportsNothingBeforeThereIsEnoughData() {
		RollingWindow window = new RollingWindow(FIVE_MINUTES);
		assertEquals(0, window.perHour(0));
		window.add(0, 100);
		assertTrue(Double.isNaN(window.perHour(1000)), "one second of data is not a rate");
	}

	@Test
	void earlySessionDividesByTheSessionNotTheWindow() {
		// The whole point of min(window, activeTime): after one minute of mining, 100k earned reads as 6M/h,
		// not as 100k spread over an imaginary hour.
		RollingWindow window = new RollingWindow(ONE_HOUR);
		window.add(0, 100_000);
		assertEquals(6_000_000d, window.perHour(60_000), 1d);
	}

	@Test
	void steadyIncomeGivesTheExpectedRate() {
		RollingWindow window = new RollingWindow(ONE_HOUR);
		// 1000 coins every minute for ten minutes.
		for (int minute = 0; minute < 10; minute++) {
			window.add(minute * 60_000L, 1000);
		}
		// 10,000 over 10 minutes of active time = 60,000/h.
		assertEquals(60_000d, window.perHour(10 * 60_000L), 1d);
	}

	@Test
	void samplesFallOutOfTheWindow() {
		RollingWindow window = new RollingWindow(FIVE_MINUTES);
		window.add(0, 1000);
		window.add(60_000, 1000);
		assertEquals(2000d, window.sum());

		// Ten minutes of active time later, both samples are older than the five minute window.
		window.trim(10 * 60_000L);
		assertTrue(window.isEmpty());
		assertEquals(0d, window.sum());
		assertEquals(0d, window.perHour(10 * 60_000L));
	}

	@Test
	void pausingFreezesTheWindowInsteadOfDrainingIt() {
		RollingWindow window = new RollingWindow(FIVE_MINUTES);
		window.add(0, 1000);
		window.add(60_000, 1000);
		double before = window.perHour(120_000);

		// The player walks away for an hour. Active time does not advance, so nothing ages out and the rate the
		// HUD last showed is still the rate it shows on return.
		double afterBreak = window.perHour(120_000);
		assertEquals(before, afterBreak);
		assertFalse(window.isEmpty());
	}

	@Test
	void windowLengthIsRespectedOnceTheSessionIsLongEnough() {
		RollingWindow window = new RollingWindow(FIVE_MINUTES);
		// Earn 600 in the first minute, then nothing for four more.
		window.add(0, 600);
		// 600 over the full five minute window = 7200/h.
		assertEquals(7200d, window.perHour(FIVE_MINUTES), 1d);
	}

	@Test
	void zeroValuesAreNotStored() {
		RollingWindow window = new RollingWindow(FIVE_MINUTES);
		window.add(0, 0);
		assertTrue(window.isEmpty());
	}

	@Test
	void negativeValuesLowerTheRate() {
		// Costs are pushed in as negative samples, so a bad corpse streak really does show as reduced coins/h.
		RollingWindow window = new RollingWindow(ONE_HOUR);
		window.add(0, 1000);
		window.add(1000, -400);
		assertEquals(600d, window.sum(), 0.001);
	}

	@Test
	void reactiveRateSpikesOnAFreshSampleThenSettles() {
		RollingWindow window = new RollingWindow(FIVE_MINUTES);
		// One drop worth 10,000, three seconds into the session. Measured from its own timestamp the span is
		// floored at five seconds, so it reads as 7.2M/h — deliberately absurd, because right now it is.
		window.add(3_000, 10_000);
		assertEquals(7_200_000d, window.perHourSinceOldest(3_000), 1d);

		// Five minutes of active time later the same 10,000 is spread over a 297 second span and reads sanely.
		assertEquals(121_212d, window.perHourSinceOldest(FIVE_MINUTES), 1d);
	}

	@Test
	void reactiveAndSettledRatesDivergeEarlyAndConvergeLater() {
		RollingWindow window = new RollingWindow(FIVE_MINUTES);
		// Nothing for two minutes, then 1,000 arrives.
		window.add(120_000, 1000);

		// Reactive: measured from the drop itself, so it looks enormous.
		double reactive = window.perHourSinceOldest(125_000);
		// Settled: measured over the two minutes actually mined.
		double settled = window.perHour(125_000);
		assertTrue(reactive > settled * 10, "the short window is supposed to be the jumpy one");

		// After the full window has passed, both measure the same five minutes.
		assertEquals(window.perHour(120_000 + FIVE_MINUTES), window.perHourSinceOldest(120_000 + FIVE_MINUTES), 1d);
	}

	@Test
	void reactiveRateNeverDividesByZero() {
		RollingWindow window = new RollingWindow(FIVE_MINUTES);
		window.add(0, 1000);
		double rate = window.perHourSinceOldest(0);
		assertTrue(Double.isFinite(rate), "a sample added this very millisecond must not produce infinity");
	}

	@Test
	void clearResetsEverything() {
		RollingWindow window = new RollingWindow(FIVE_MINUTES);
		window.add(0, 1000);
		window.clear();
		assertTrue(window.isEmpty());
		assertEquals(0d, window.sum());
	}
}
