package de.hysky.skyblocker.skyblock.dwarven.profittrackers.mining;

import java.util.ArrayDeque;

/**
 * A per-hour rate over a sliding window measured in <b>active</b> time.
 *
 * <p>Using active time rather than wall time is what makes the auto-pause work: while the player is not breaking
 * blocks the active clock stops, so the window freezes instead of quietly draining to zero. Coming back after a
 * ten minute break resumes exactly where the rate left off.
 *
 * <p>The denominator is {@code min(window, totalActiveTime)} — early in a session that is the whole session, so a
 * one hour window is useful after two minutes of mining instead of reading a twelfth of the truth.
 *
 * <p>Not thread safe; everything the tracker does happens on the client thread.
 */
public final class RollingWindow {
	/** Below this much active time the rate is meaningless and {@link #perHour} reports nothing. */
	private static final long MIN_SPAN_MS = 5_000L;

	private final long windowMs;
	private final ArrayDeque<Sample> samples = new ArrayDeque<>();
	private double sum;

	public RollingWindow(long windowMs) {
		if (windowMs <= 0) throw new IllegalArgumentException("window must be positive");
		this.windowMs = windowMs;
	}

	/**
	 * @param activeMs the session's total active time when this value was earned
	 */
	public void add(long activeMs, double value) {
		if (value == 0) return;
		samples.addLast(new Sample(activeMs, value));
		sum += value;
		trim(activeMs);
	}

	public void trim(long activeMs) {
		long cutoff = activeMs - windowMs;
		while (!samples.isEmpty() && samples.peekFirst().activeMs() < cutoff) {
			sum -= samples.pollFirst().value();
		}
		if (samples.isEmpty()) sum = 0; // kill accumulated floating point drift
	}

	/**
	 * The settled rate: everything in the window divided by how much active time the window actually covers.
	 *
	 * <p>Early in a session that denominator is the whole session, so the number is a true average from the first
	 * minute onwards rather than a twelfth of the truth.
	 *
	 * @param activeMs the session's total active time right now
	 * @return the rate per hour, or {@link Double#NaN} when there is not enough data yet
	 */
	public double perHour(long activeMs) {
		trim(activeMs);
		if (samples.isEmpty()) return 0;
		long span = Math.min(windowMs, activeMs);
		if (span < MIN_SPAN_MS) return Double.NaN;
		return sum / span * 3_600_000d;
	}

	/**
	 * The reactive rate: everything in the window divided by the time since its <b>oldest surviving sample</b>.
	 *
	 * <p>This spikes hard at the start of a session and after a dry spell — one Flawless three seconds in reads as
	 * billions per hour — and then settles as the span grows toward the full window. That volatility is the point:
	 * it answers "how good is this vein, right now" rather than "how has the session gone".
	 *
	 * <p>The span is floored at five seconds so a fresh sample cannot divide by zero.
	 */
	public double perHourSinceOldest(long activeMs) {
		trim(activeMs);
		if (samples.isEmpty()) return 0;
		long span = Math.max(activeMs - samples.peekFirst().activeMs(), MIN_SPAN_MS);
		return sum / span * 3_600_000d;
	}

	public double sum() {
		return sum;
	}

	public boolean isEmpty() {
		return samples.isEmpty();
	}

	public void clear() {
		samples.clear();
		sum = 0;
	}

	public long windowMs() {
		return windowMs;
	}

	private record Sample(long activeMs, double value) {}
}
