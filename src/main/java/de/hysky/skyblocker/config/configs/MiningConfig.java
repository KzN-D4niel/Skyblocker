package de.hysky.skyblocker.config.configs;

import de.hysky.skyblocker.annotations.EnumDisabledValue;
import net.minecraft.client.resources.language.I18n;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class MiningConfig {
	public boolean enableDrillFuel = true;

	public boolean commissionHighlight = true;

	public boolean callMismyla = true;

	public boolean redialOnBadSignal = true;

	/**
	 * TODO: Move into {@link PickobulusHelper} in next config version.
	 */
	public boolean enablePickobulusHelper = true;

	public PickobulusHelper pickobulusHelper = new PickobulusHelper();

	public DwarvenMines dwarvenMines = new DwarvenMines();

	@Deprecated
	public transient DwarvenHud dwarvenHud = new DwarvenHud();

	public CrystalHollows crystalHollows = new CrystalHollows();

	public MiningTracker miningTracker = new MiningTracker();

	public CrystalsHud crystalsHud = new CrystalsHud();

	public CrystalsWaypoints crystalsWaypoints = new CrystalsWaypoints();

	public CommissionWaypoints commissionWaypoints = new CommissionWaypoints();

	public Glacite glacite = new Glacite();

	public BlockBreakPrediction blockBreakPrediction = new BlockBreakPrediction();

	public static class PickobulusHelper {
		public boolean enablePickobulusHud = true;

		public boolean hideHudOnCooldown = false;
	}

	public static class DwarvenMines {
		public boolean solveFetchur = true;

		public boolean solvePuzzler = true;

		public boolean enableCarpetHighlighter = true;

		public Color carpetHighlightColor = new Color(255, 0, 0, 76);
	}

	@Deprecated
	public static class DwarvenHud {
		@Deprecated
		public boolean enabledCommissions = true;

		@Deprecated
		public boolean enabledPowder = true;

		@Deprecated
		public DwarvenHudStyle style = DwarvenHudStyle.SIMPLE;

		@Deprecated
		public int commissionsX = 10;

		@Deprecated
		public int commissionsY = 10;

		@Deprecated
		public int powderX = 10;

		@Deprecated
		public int powderY = 70;
	}

	public static class CrystalHollows {
		public boolean metalDetectorHelper = true;

		public boolean nucleusWaypoints = false;

		public boolean chestHighlighter = true;

		public Color chestHighlightColor = new Color(0, 0, 255, 128);

		public boolean enablePowderTracker = true;

		public boolean countNaturalChestsInTracker = true;

		public List<String> powderTrackerFilter = new ArrayList<>();
	}

	public static class MiningTracker {
		public boolean enabled = true;

		/** Session clock and rate windows freeze after this many seconds without a block break. */
		public int pauseAfterSeconds = 60;

		public PriceSource priceSource = PriceSource.BEST;

		/**
		 * Powder is not sellable, so counting it as coins inflates coins/h. Off unless the user really wants it.
		 */
		public boolean valuePowder = false;

		/** Only used when {@link #valuePowder} is on — there is no market price for powder to look up. */
		public int coinsPerPowder = 0;

		public boolean countNaturalChests = true;

		public boolean showEvent = true;
		public boolean showCoinsPerHour = true;
		public boolean showCoinsTotal = true;
		public boolean showBlocksPerSecond = true;
		public boolean showBlocksTotal = true;
		public boolean showMiningXp = true;
		public boolean showMithrilPowder = true;
		public boolean showGemstonePowder = true;
		public boolean showGlacitePowder = true;
		public boolean showPowderTotal = false;
		public boolean showPristine = true;
		public boolean showTopDrops = true;
		public boolean showSessionTime = true;
	}

	/**
	 * Which price to value a drop at.
	 */
	public enum PriceSource {
		/** The higher of the two below, per item. Almost always what you actually want. */
		BEST,
		/** Bazaar sell price: what you get right now by selling into the highest buy order. */
		INSTASELL,
		/** NPC sell price. */
		NPC;

		@Override
		public String toString() {
			return I18n.get("skyblocker.config.mining.miningTracker.priceSource." + name());
		}
	}

	public static class CrystalsHud {
		public boolean enabled = true;

		public boolean showLocations = true;

		public int locationSize = 8;

		public int x = 10;

		public int y = 130;

		public float mapScaling = 1f;
	}

	public static class CrystalsWaypoints {
		public boolean enabled = true;

		@Deprecated
		public transient float textScale = 1;

		public boolean findInChat = true;

		public boolean wishingCompassSolver = true;

		public boolean shareFairyGrotto = true;
	}

	public static class CommissionWaypoints {
		public CommissionWaypointMode mode = CommissionWaypointMode.BOTH;

		@Deprecated
		public transient float textScale = 1;

		public boolean useColor = true;

		public boolean showBaseCamp = false;

		public boolean showEmissary = true;

		public boolean hideEmissaryOnPigeon = true;
	}

	public enum CommissionWaypointMode {
		@EnumDisabledValue
		OFF, DWARVEN, GLACITE, BOTH;

		@Override
		public String toString() {
			return I18n.get("skyblocker.config.mining.commissionWaypoints.mode." + name());
		}
	}

	public static class Glacite {
		public boolean coldOverlay = true;

		public boolean fossilSolver = true;

		public boolean solveFossilMuncher = true;

		public boolean enableCorpseFinder = true;

		public boolean enableParsingChatCorpseFinder = true;

		public boolean autoShareCorpses = false;

		public boolean enableCorpseProfitTracker = true;

		public boolean forceEnglishCorpseProfitTracker = true;
	}

	public static class BlockBreakPrediction {
		public boolean enabled = false;

		public boolean playSound = false;


	}

	/**
	 * @deprecated See {@link UIAndVisualsConfig.TabHudStyle}.
	 */
	@Deprecated
	public enum DwarvenHudStyle {
		@Deprecated
		SIMPLE,
		@Deprecated
		FANCY,
		@Deprecated
		CLASSIC;

		@Deprecated
		@Override
		public String toString() {
			return switch (this) {
				case SIMPLE -> "Simple";
				case FANCY -> "Fancy";
				case CLASSIC -> "Classic";
			};
		}
	}
}
