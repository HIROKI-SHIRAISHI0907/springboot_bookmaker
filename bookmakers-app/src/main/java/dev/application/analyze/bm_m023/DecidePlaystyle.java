package dev.application.analyze.bm_m023;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.application.analyze.bm_m022.StatsDiffEntity;
import dev.application.analyze.common.entity.ThresHoldEntity;
import dev.application.analyze.common.util.ExecuteMainUtil;
import dev.common.constant.UniairConst;
import dev.common.exception.BusinessException;

/**
 * プレースタイル決定部品<br>
 * 以下のプレースタイルを決定する
 * <p>
 * 1.ポゼッション型（パス主体）<br>
 * 2.堅守速攻型（カウンターアタック型）<br>
 * 3.ハイプレス型（アグレッシブプレス型）<br>
 * 4.リトリート型（守備型）<br>
 * </p>
 * @author shiraishitoshio
 *
 */
public class DecidePlaystyle {

	/**
	 * プレースタイルMap
	 */
	public static final Map<Integer, String> PLAYSTYLE_ALL_MAP;
	static {
		HashMap<Integer, String> PLAYSTYLE_MAP = new LinkedHashMap<>();
		PLAYSTYLE_MAP.put(0, PlayStyleConst.POSESSION);
		PLAYSTYLE_MAP.put(1, PlayStyleConst.SOLID_DEFENSE_QUICK_ATTACK);
		PLAYSTYLE_MAP.put(2, PlayStyleConst.HIGHPRESS);
		PLAYSTYLE_MAP.put(3, PlayStyleConst.RETREAT);
		PLAYSTYLE_ALL_MAP = Collections.unmodifiableMap(PLAYSTYLE_MAP);
	}

	/**
	 * 全体データ
	 */
	private static final String ALL_DATA = "ALL";

	/**
	 * 閾値決定Map
	 */
	private Map<String, Map<String, DecidePlaystyleMapping>> thresHoldMap;

	/**
	 * 閾値決定
	 */
	public DecidePlaystyle() {
		setThreshold();
	}

	/**
	 * ポゼッション型
	 * <p>
	 * ホーム支配率 / アウェー支配率: 高い支配率（60%以上）
	 * ホームパス数 / アウェーパス数: 高いパス数（特にショートパスやクロスパスを多用）
	 * ホームファイナルサードパス数 / アウェーファイナルサードパス数: 高いファイナルサードパス数（攻撃の結びつき）
	 * ホームクロス数 / アウェークロス数: 適度なクロス数（パスを通すことを重視）
	 * ホームビックチャンス / アウェービックチャンス: 高いビッグチャンス数（チャンスを作り出すことを重視）
	 * </p>
	 * 堅守速攻型
	 * <p>
	 * ホームインターセプト数 / アウェーインターセプト数: 高いインターセプト数（守備から攻撃への転換を重視）
	 * ホームタックル数 / アウェータックル数: 高いタックル数（守備が主体）
	 * ホームシュート数 / アウェーシュート数: 高いシュート数（速攻の際にはシュート数が多くなる）
	 * ホーム枠内シュート / アウェー枠内シュート: 高い枠内シュート数（速攻からのフィニッシュ精度）
	 * </p>
	 * ハイプレス型
	 * <p>
	 * ホームタックル数 / アウェータックル数: 高いタックル数（プレッシャーをかけてボールを奪う）
	 * ホームインターセプト数 / アウェーインターセプト数: 高いインターセプト数（相手のボールを素早く奪う）
	 * ホームシュート数 / アウェーシュート数: 高いシュート数（素早く攻撃に転じ、シュートを多く放つ）
	 * ホームオフサイド数 / アウェーオフサイド数: 高いオフサイド数（プレッシャーをかけて相手の攻撃ラインを早期に崩す）
	 * ホームファール数 / アウェーファール数: 高いファール数（攻撃的な守備で、相手を止めるためのファールが増える）
	 * </p>
	 * リトリート型
	 * <p>
	 * ホームクリア数 / アウェークリア数: 高いクリア数（守備においてボールを大きくクリアすることが多い）
	 * ホームタックル数 / アウェータックル数: 高いタックル数（守備が主体）
	 * ホームインターセプト数 / アウェーインターセプト数: 高いインターセプト数（相手の攻撃を防ぐことが多い）
	 * ホームシュート数 / アウェーシュート数: 低めのシュート数（攻撃より守備を重視）
	 * ホームオフサイド数 / アウェーオフサイド数: 低いオフサイド数（守備的な立ち位置を取る）
	 * ホームゴールポスト / アウェーゴールポスト: 低いゴールポストの回数（守備を重視するため、得点のチャンスが少ない）
	 * </p>
	 *
	 * @param entities 差分Entity
	 * @param exEntities 元Entity
	 */
	public List<String> execute(StatsDiffEntity entities, ThresHoldEntity exEntities) {

		// 国カテゴリ,スコアキー
		String[] key = ExecuteMainUtil.splitLeagueInfo(exEntities.getDataCategory());
		String dataKey = key[0] + "-" + key[1];
		String scoreKey = ALL_DATA;

		// パラメータチェック(統計データがなければプレースタイルチェックはしない)
		List<String> playStyle = new ArrayList<>();
		if (!chkInParameter(dataKey, scoreKey)) {
			playStyle.add("-");
			playStyle.add("-");
			return playStyle;
		}

		// 閾値Map取得
		DecidePlaystyleMapping thresHoldMap = this.thresHoldMap.get(dataKey).get(scoreKey);

		// プレースタイル決定配列
		List<Integer> home_playStyle = new ArrayList<Integer>(Arrays.asList(0, 0, 0, 0));
		List<Integer> away_playStyle = new ArrayList<Integer>(Arrays.asList(0, 0, 0, 0));

		//ポゼッション型
		//ホーム支配率 / アウェー支配率: 高い支配率（60%以上）
		//ホームパス数 / アウェーパス数: 高いパス数（特にショートパスやクロスパスを多用）
		//ホームファイナルサードパス数 / アウェーファイナルサードパス数: 高いファイナルサードパス数（攻撃の結びつき）
		//ホームクロス数 / アウェークロス数: 適度なクロス数（パスを通すことを重視）
		//ホームビックチャンス / アウェービックチャンス: 高いビッグチャンス数（チャンスを作り出すことを重視）
		String homePossesion = exEntities.getHomeDonation().replace("%", "");
		String awayPossesion = exEntities.getAwayDonation().replace("%", "");
		List<String> homePassList = ExecuteMainUtil.splitGroup(exEntities.getHomePassCount());
		List<String> awayPassList = ExecuteMainUtil.splitGroup(exEntities.getAwayPassCount());
		List<String> homeFinalThirdPassList = ExecuteMainUtil.splitGroup(exEntities.getHomeFinalThirdPassCount());
		List<String> awayFinalThirdPassList = ExecuteMainUtil.splitGroup(exEntities.getAwayFinalThirdPassCount());
		List<String> homeCrossList = ExecuteMainUtil.splitGroup(exEntities.getHomeCrossCount());
		List<String> awayCrossList = ExecuteMainUtil.splitGroup(exEntities.getAwayCrossCount());
		String homeBigChance = exEntities.getHomeBigChance();
		String awayBigChance = exEntities.getAwayBigChance();

		if (!"".equals(homePossesion) && homePossesion.compareTo("60.0") > 0) {
			home_playStyle.set(0, home_playStyle.get(0) + 1);
		}
		if (!"".equals(awayPossesion) && awayPossesion.compareTo("60.0") > 0) {
			away_playStyle.set(0, away_playStyle.get(0) + 1);
		}
		// TODO 高いパス数とは?(閾値はどうする?)
		String homeHighPassInfo = thresHoldMap.getHomePassCountInfo().getThresHoldMaxInAvePlusOneSigma();
		List<String> homeHighPassList = ExecuteMainUtil.splitGroup(homeHighPassInfo);
		if (!homePassList.get(1).isBlank()) {
			String homeHighPass = homeHighPassList.get(1);
			if (homePassList.get(1).compareTo(homeHighPass) > 0) {
				home_playStyle.set(0, home_playStyle.get(0) + 1);
			}
		}
		String awayHighPassInfo = thresHoldMap.getAwayPassCountInfo().getThresHoldMaxInAvePlusOneSigma();
		List<String> awayHighPassList = ExecuteMainUtil.splitGroup(awayHighPassInfo);
		if (!awayPassList.get(1).isBlank()) {
			String awayHighPass = awayHighPassList.get(1);
			if (awayPassList.get(1).compareTo(awayHighPass) > 0) {
				away_playStyle.set(0, away_playStyle.get(0) + 1);
			}
		}
		// TODO 高いファイナルサードパス数とは?(閾値はどうする?)
		String homeHighFinalInfo = thresHoldMap.getHomeFinalThirdPassCountInfo().getThresHoldMaxInAvePlusOneSigma();
		List<String> homeHighFinalList = ExecuteMainUtil.splitGroup(homeHighFinalInfo);
		if (!homeFinalThirdPassList.get(1).isBlank()) {
			String homeHighFinal = homeHighFinalList.get(1);
			if (homeFinalThirdPassList.get(1).compareTo(homeHighFinal) > 0) {
				home_playStyle.set(0, home_playStyle.get(0) + 1);
			}
		}
		String awayHighFinalInfo = thresHoldMap.getAwayFinalThirdPassCountInfo().getThresHoldMaxInAvePlusOneSigma();
		List<String> awayHighFinalList = ExecuteMainUtil.splitGroup(awayHighFinalInfo);
		if (!awayFinalThirdPassList.get(1).isBlank()) {
			String awayHighFinal = awayHighFinalList.get(1);
			if (awayFinalThirdPassList.get(1).compareTo(awayHighFinal) > 0) {
				away_playStyle.set(0, away_playStyle.get(0) + 1);
			}
		}
		// TODO 高いクロス数とは?(閾値はどうする?)
		String homeHighCrossInfo = thresHoldMap.getHomeCrossCountInfo().getThresHoldMaxInAvePlusOneSigma();
		List<String> homeHighCrossList = ExecuteMainUtil.splitGroup(homeHighCrossInfo);
		if (!homeCrossList.get(1).isBlank()) {
			String homeHighCross = homeHighCrossList.get(1);
			if (homeCrossList.get(1).compareTo(homeHighCross) > 0) {
				home_playStyle.set(0, home_playStyle.get(0) + 1);
			}
		}
		String awayHighCrossInfo = thresHoldMap.getAwayCrossCountInfo().getThresHoldMaxInAvePlusOneSigma();
		List<String> awayHighCrossList = ExecuteMainUtil.splitGroup(awayHighCrossInfo);
		if (!awayCrossList.get(1).isBlank()) {
			String awayHighCross = awayHighCrossList.get(1);
			if (awayCrossList.get(1).compareTo(awayHighCross) > 0) {
				away_playStyle.set(0, away_playStyle.get(0) + 1);
			}
		}
		// TODO 高いビッグチャンス数とは?(閾値はどうする?)
		String homeHighBigChanceInfo = thresHoldMap.getHomeBigChanceInfo().getThresHoldMaxInAvePlusOneSigma();
		if (!"".equals(homeBigChance) && homeBigChance.compareTo(homeHighBigChanceInfo) > 0) {
			home_playStyle.set(0, home_playStyle.get(0) + 1);
		}
		String awayHighBigChanceInfo = thresHoldMap.getAwayBigChanceInfo().getThresHoldMaxInAvePlusOneSigma();
		if (!"".equals(awayBigChance) && awayBigChance.compareTo(awayHighBigChanceInfo) > 0) {
			away_playStyle.set(0, away_playStyle.get(0) + 1);
		}

		//堅守速攻型
		//ホームインターセプト数 / アウェーインターセプト数: 高いインターセプト数（守備から攻撃への転換を重視）
		//ホームタックル数 / アウェータックル数: 高いタックル数（守備が主体）
		//ホームシュート数 / アウェーシュート数: 高いシュート数（速攻の際にはシュート数が多くなる）
		//ホーム枠内シュート / アウェー枠内シュート: 高い枠内シュート数（速攻からのフィニッシュ精度）
		String homeIntercept = exEntities.getHomeInterceptCount();
		String awayIntercept = exEntities.getAwayInterceptCount();
		List<String> homeTackleList = ExecuteMainUtil.splitGroup(exEntities.getHomeTackleCount());
		List<String> awayTackleList = ExecuteMainUtil.splitGroup(exEntities.getAwayTackleCount());
		String homeShoot = exEntities.getHomeShootAll();
		String awayShoot = exEntities.getAwayShootAll();
		String homeShootIn = exEntities.getHomeShootIn();
		String awayShootIn = exEntities.getAwayShootIn();
		// TODO 高いインターセプト数とは?(閾値はどうする?)
		String homeHighInterceptInfo = thresHoldMap.getHomeInterceptCountInfo().getThresHoldMaxInAvePlusOneSigma();
		if (!"".equals(homeIntercept) && homeIntercept.compareTo(homeHighInterceptInfo) > 0) {
			home_playStyle.set(1, home_playStyle.get(0) + 1);
		}
		String awayHighInterceptInfo = thresHoldMap.getAwayInterceptCountInfo().getThresHoldMaxInAvePlusOneSigma();
		if (!"".equals(awayIntercept) && awayIntercept.compareTo(awayHighInterceptInfo) > 0) {
			away_playStyle.set(1, away_playStyle.get(0) + 1);
		}
		// TODO 高いタックル数とは?(閾値はどうする?)
		String homeHighTackleInfo = thresHoldMap.getHomeTackleCountInfo().getThresHoldMaxInAvePlusOneSigma();
		List<String> homeHighTackleList = ExecuteMainUtil.splitGroup(homeHighTackleInfo);
		if (!homeTackleList.get(1).isBlank()) {
			String homeHighTackle = homeHighTackleList.get(1);
			if (homeTackleList.get(1).compareTo(homeHighTackle) > 0) {
				home_playStyle.set(1, home_playStyle.get(0) + 1);
			}
		}
		String awayHighTackleInfo = thresHoldMap.getAwayTackleCountInfo().getThresHoldMaxInAvePlusOneSigma();
		List<String> awayHighTackleList = ExecuteMainUtil.splitGroup(awayHighTackleInfo);
		if (!awayTackleList.get(1).isBlank()) {
			String awayHighTackle = awayHighTackleList.get(1);
			if (awayTackleList.get(1).compareTo(awayHighTackle) > 0) {
				away_playStyle.set(1, away_playStyle.get(0) + 1);
			}
		}
		// TODO 高いシュート数とは?(閾値はどうする?)
		String homeHighShootInfo = thresHoldMap.getHomeShootAllInfo().getThresHoldMaxInAvePlusOneSigma();
		if (!"".equals(homeShoot) && homeShoot.compareTo(homeHighShootInfo) > 0) {
			home_playStyle.set(1, home_playStyle.get(0) + 1);
		}
		String awayHighShootInfo = thresHoldMap.getAwayShootAllInfo().getThresHoldMaxInAvePlusOneSigma();
		if (!"".equals(awayShoot) && awayShoot.compareTo(awayHighShootInfo) > 0) {
			away_playStyle.set(1, away_playStyle.get(0) + 1);
		}
		// TODO 高い枠内シュート数とは?(閾値はどうする?)
		String homeHighShootInInfo = thresHoldMap.getHomeShootInInfo().getThresHoldMaxInAvePlusOneSigma();
		if (!"".equals(homeShootIn) && homeShootIn.compareTo(homeHighShootInInfo) > 0) {
			home_playStyle.set(1, home_playStyle.get(0) + 1);
		}
		String awayHighShootInInfo = thresHoldMap.getAwayShootInInfo().getThresHoldMaxInAvePlusOneSigma();
		if (!"".equals(awayShootIn) && awayShootIn.compareTo(awayHighShootInInfo) > 0) {
			away_playStyle.set(1, away_playStyle.get(0) + 1);
		}

		//ハイプレス型
		//ホームタックル数 / アウェータックル数: 高いタックル数（プレッシャーをかけてボールを奪う）
		//ホームインターセプト数 / アウェーインターセプト数: 高いインターセプト数（相手のボールを素早く奪う）
		//ホームシュート数 / アウェーシュート数: 高いシュート数（素早く攻撃に転じ、シュートを多く放つ）
		//ホームオフサイド数 / アウェーオフサイド数: 高いオフサイド数（プレッシャーをかけて相手の攻撃ラインを早期に崩す）
		//ホームファール数 / アウェーファール数: 高いファール数（攻撃的な守備で、相手を止めるためのファールが増える）
		String homeOffside = exEntities.getHomeOffside();
		String awayOffside = exEntities.getAwayOffside();
		String homeFoul = exEntities.getHomeFoul();
		String awayFoul = exEntities.getAwayFoul();
		// TODO 高いタックル数とは?(閾値はどうする?)
		if (!homeTackleList.get(1).isBlank()) {
			String homeHighTackle = homeHighTackleList.get(1);
			if (homeTackleList.get(1).compareTo(homeHighTackle) > 0) {
				home_playStyle.set(2, home_playStyle.get(0) + 1);
			}
		}
		if (!awayTackleList.get(1).isBlank()) {
			String awayHighTackle = awayHighTackleList.get(1);
			if (awayTackleList.get(1).compareTo(awayHighTackle) > 0) {
				away_playStyle.set(2, away_playStyle.get(0) + 1);
			}
		}
		// TODO 高いインターセプト数とは?(閾値はどうする?)
		if (!"".equals(homeIntercept) && homeIntercept.compareTo(homeHighInterceptInfo) > 0) {
			home_playStyle.set(2, home_playStyle.get(0) + 1);
		}
		if (!"".equals(awayIntercept) && awayIntercept.compareTo(awayHighInterceptInfo) > 0) {
			away_playStyle.set(2, away_playStyle.get(0) + 1);
		}
		// TODO 高いシュート数とは?(閾値はどうする?)
		if (!"".equals(homeShoot) && homeShoot.compareTo(homeHighShootInfo) > 0) {
			home_playStyle.set(2, home_playStyle.get(0) + 1);
		}
		if (!"".equals(awayShoot) && awayShoot.compareTo(awayHighShootInfo) > 0) {
			away_playStyle.set(2, away_playStyle.get(0) + 1);
		}
		String homeHighOffsideInfo = thresHoldMap.getHomeOffsideInfo().getThresHoldMaxInAvePlusOneSigma();
		// TODO 高いオフサイド数とは?(閾値はどうする?)
		if (!"".equals(homeOffside) && homeOffside.compareTo(homeHighOffsideInfo) > 0) {
			home_playStyle.set(2, home_playStyle.get(0) + 1);
		}
		String awayHighOffsideInfo = thresHoldMap.getAwayOffsideInfo().getThresHoldMaxInAvePlusOneSigma();
		if (!"".equals(awayOffside) && awayOffside.compareTo(awayHighOffsideInfo) > 0) {
			away_playStyle.set(2, away_playStyle.get(0) + 1);
		}
		// TODO 高いファール数とは?(閾値はどうする?)
		String homeHighFoulInfo = thresHoldMap.getHomeFoulInfo().getThresHoldMaxInAvePlusOneSigma();
		if (!"".equals(homeFoul) && homeFoul.compareTo(homeHighFoulInfo) > 0) {
			home_playStyle.set(2, home_playStyle.get(0) + 1);
		}
		String awayHighFoulInfo = thresHoldMap.getAwayFoulInfo().getThresHoldMaxInAvePlusOneSigma();
		if (!"".equals(awayFoul) && awayFoul.compareTo(awayHighFoulInfo) > 0) {
			away_playStyle.set(2, away_playStyle.get(0) + 1);
		}

		//リトリート型
		//ホームクリア数 / アウェークリア数: 高いクリア数（守備においてボールを大きくクリアすることが多い）
		//ホームタックル数 / アウェータックル数: 高いタックル数（守備が主体）
		//ホームインターセプト数 / アウェーインターセプト数: 高いインターセプト数（相手の攻撃を防ぐことが多い）
		//ホームシュート数 / アウェーシュート数: 低めのシュート数（攻撃より守備を重視）
		//ホームオフサイド数 / アウェーオフサイド数: 低いオフサイド数（守備的な立ち位置を取る）
		//ホームゴールポスト / アウェーゴールポスト: 低いゴールポストの回数（守備を重視するため、得点のチャンスが少ない）
		String homeClear = exEntities.getHomeClearCount();
		String awayClear = exEntities.getAwayClearCount();
		String homeGoalPost = exEntities.getHomeGoalPost();
		String awayGoalPost = exEntities.getAwayGoalPost();
		// TODO 高いクリア数とは?(閾値はどうする?)
		String homeHighClearInfo = thresHoldMap.getHomeClearCountInfo().getThresHoldMaxInAvePlusOneSigma();
		if (!"".equals(homeClear) && homeClear.compareTo(homeHighClearInfo) > 0) {
			home_playStyle.set(3, home_playStyle.get(0) + 1);
		}
		String awayHighClearInfo = thresHoldMap.getAwayClearCountInfo().getThresHoldMaxInAvePlusOneSigma();
		if (!"".equals(awayClear) && awayClear.compareTo(awayHighClearInfo) > 0) {
			away_playStyle.set(3, away_playStyle.get(0) + 1);
		}
		// TODO 高いタックル数とは?(閾値はどうする?)
		if (!homeTackleList.get(1).isBlank()) {
			String homeHighTackle = homeHighTackleList.get(1);
			if (homeTackleList.get(1).compareTo(homeHighTackle) > 0) {
				home_playStyle.set(3, home_playStyle.get(0) + 1);
			}
		}
		if (!awayTackleList.get(1).isBlank()) {
			String awayHighTackle = awayHighTackleList.get(1);
			if (awayTackleList.get(1).compareTo(awayHighTackle) > 0) {
				away_playStyle.set(3, away_playStyle.get(0) + 1);
			}
		}
		// TODO 高いインターセプト数とは?(閾値はどうする?)
		if (!"".equals(homeIntercept) && homeIntercept.compareTo(homeHighInterceptInfo) > 0) {
			home_playStyle.set(3, home_playStyle.get(0) + 1);
		}
		if (!"".equals(awayIntercept) && awayIntercept.compareTo(awayHighInterceptInfo) > 0) {
			away_playStyle.set(3, away_playStyle.get(0) + 1);
		}
		// TODO 低めのシュート数とは?(閾値はどうする?)
		String homeLowShootInfo = thresHoldMap.getHomeShootAllInfo().getThresHoldMinInAvePlusOneSigma();
		if (!"".equals(homeShoot) && homeShoot.compareTo(homeLowShootInfo) < 0) {
			home_playStyle.set(3, home_playStyle.get(0) + 1);
		}
		String awayLowShootInfo = thresHoldMap.getAwayShootAllInfo().getThresHoldMinInAvePlusOneSigma();
		if (!"".equals(awayShoot) && awayShoot.compareTo(awayLowShootInfo) < 0) {
			away_playStyle.set(3, away_playStyle.get(0) + 1);
		}
		// TODO 低いオフサイド数とは?(閾値はどうする?)
		String homeLowOffsideInfo = thresHoldMap.getHomeOffsideInfo().getThresHoldMinInAvePlusOneSigma();
		if (!"".equals(homeOffside) && homeOffside.compareTo(homeLowOffsideInfo) < 0) {
			home_playStyle.set(3, home_playStyle.get(0) + 1);
		}
		String awayLowOffsideInfo = thresHoldMap.getAwayOffsideInfo().getThresHoldMinInAvePlusOneSigma();
		if (!"".equals(awayOffside) && awayOffside.compareTo(awayLowOffsideInfo) < 0) {
			away_playStyle.set(3, away_playStyle.get(0) + 1);
		}
		// TODO 低いゴールポスト数とは?(閾値はどうする?)
		String homeLowGoalPostInfo = thresHoldMap.getHomeGoalPostInfo().getThresHoldMinInAvePlusOneSigma();
		if (!"".equals(homeGoalPost) && homeGoalPost.compareTo(homeLowGoalPostInfo) < 0) {
			home_playStyle.set(3, home_playStyle.get(0) + 1);
		}
		String awayLowGoalPostInfo = thresHoldMap.getAwayGoalPostInfo().getThresHoldMinInAvePlusOneSigma();
		if (!"".equals(awayGoalPost) && awayGoalPost.compareTo(awayLowGoalPostInfo) < 0) {
			away_playStyle.set(3, away_playStyle.get(0) + 1);
		}

		// 最大の値を持つindexを決定する
		int max_home_playStyle = Collections.max(home_playStyle);
		// 最大値と一致するすべての要素を取得
		List<String> max_home_playStyle_values = new ArrayList<>();
		for (Integer value : home_playStyle) {
			if (value.equals(max_home_playStyle)) {
				int ind = home_playStyle.indexOf(max_home_playStyle);
				max_home_playStyle_values.add(containsPlayStyle(ind));
			}
		}
		int max_away_playStyle = Collections.max(away_playStyle);
		// 最大値と一致するすべての要素を取得
		List<String> max_away_playStyle_values = new ArrayList<>();
		for (Integer value : away_playStyle) {
			if (value.equals(max_away_playStyle)) {
				int ind = away_playStyle.indexOf(max_away_playStyle);
				max_away_playStyle_values.add(containsPlayStyle(ind));
			}
		}

		// 最終決定したプレースタイルを格納。全て満たしていない場合「-」を返す
		if (max_home_playStyle_values.size() == 4) {
			playStyle.add("-");
		} else {
			playStyle.add(max_home_playStyle_values.get(0));
		}
		if (max_away_playStyle_values.size() == 4) {
			playStyle.add("-");
		} else {
			playStyle.add(max_away_playStyle_values.get(0));
		}

		return playStyle;
	}

	/**
	 * プレースタイルMapに含まれた値を取得する
	 * @param index
	 * @return
	 */
	private String containsPlayStyle(int index) {
		if (PLAYSTYLE_ALL_MAP.containsKey(index)) {
			return PLAYSTYLE_ALL_MAP.get(index);
		}
		throw new BusinessException("", "", "", "存在しないプレースタイルindex: " + index);
	}

	/**
	 * 閾値決定
	 */
	private void setThreshold() {
		String[] selList = new String[2];
		selList[0] = "country";
		selList[1] = "league";

		SqlMainLogic select = new SqlMainLogic();
		List<List<String>> selectResultList = null;
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M006, selList, null, null, null);
		} catch (Exception e) {
			throw new BusinessException("", "", "", "set BM_M006 err: " + e);
		}

		if (selectResultList.isEmpty()) {
			throw new BusinessException("", "", "", "M006にデータがありません");
		}

		List<String> selectSubList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M023);
		String[] selSubList = new String[selectSubList.size()];
		for (int i = 0; i < selectSubList.size(); i++) {
			selSubList[i] = selectSubList.get(i);
		}

		// 国とリーグをキーにしてMapに閾値データを格納する。今回は全体データを対象にする
		Map<String, Map<String, DecidePlaystyleMapping>> dataMap = new HashMap<String, Map<String, DecidePlaystyleMapping>>();
		for (List<String> key : selectResultList) {
			String country = key.get(0);
			String league = key.get(1);
			String data_key = country + "-" + league;

			String where = "country = '" + country + "' and league = '" + league + "' and "
					+ "score = '" + ALL_DATA + "'";
			// 保持
			select = new SqlMainLogic();
			List<List<String>> selectResultStatList = null;
			try {
				selectResultStatList = select.executeSelect(null, UniairConst.BM_M023, selSubList,
						where, null, null);
			} catch (Exception e) {
				throw new BusinessException("", "", "", "set BM_M023 err: " + e);
			}

			// 平均+1標準偏差,平均-1標準偏差を特徴量ごとに保持
			Map<String, DecidePlaystyleMapping> returnMap = convertMapping(selectResultStatList);

			dataMap.put(data_key, returnMap);
		}
		this.thresHoldMap = dataMap;
	}

	/**
	 * AverageStatisticsEntityをString型のリストに変換
	 * @param entity AverageStatisticsEntity型のリスト
	 * @return String型のリスト
	 */
	private Map<String, DecidePlaystyleMapping> convertMapping(
			List<List<String>> entityList) {
		Map<String, DecidePlaystyleMapping> dataMap = new HashMap<String, DecidePlaystyleMapping>();
		for (List<String> list : entityList) {
			String score = list.get(2);
			DecidePlaystyleMapping decidePlaystyleMapping = new DecidePlaystyleMapping();
			decidePlaystyleMapping.setHomeExpInfo(splitSummaryData(list.get(5)));
			decidePlaystyleMapping.setAwayExpInfo(splitSummaryData(list.get(6)));
			decidePlaystyleMapping.setHomeDonationInfo(splitSummaryData(list.get(7)));
			decidePlaystyleMapping.setAwayDonationInfo(splitSummaryData(list.get(8)));
			decidePlaystyleMapping.setHomeShootAllInfo(splitSummaryData(list.get(9)));
			decidePlaystyleMapping.setAwayShootAllInfo(splitSummaryData(list.get(10)));
			decidePlaystyleMapping.setHomeShootInInfo(splitSummaryData(list.get(11)));
			decidePlaystyleMapping.setAwayShootInInfo(splitSummaryData(list.get(12)));
			decidePlaystyleMapping.setHomeShootOutInfo(splitSummaryData(list.get(13)));
			decidePlaystyleMapping.setAwayShootOutInfo(splitSummaryData(list.get(14)));
			decidePlaystyleMapping.setHomeBlockShootInfo(splitSummaryData(list.get(15)));
			decidePlaystyleMapping.setAwayBlockShootInfo(splitSummaryData(list.get(16)));
			decidePlaystyleMapping.setHomeBigChanceInfo(splitSummaryData(list.get(17)));
			decidePlaystyleMapping.setAwayBigChanceInfo(splitSummaryData(list.get(18)));
			decidePlaystyleMapping.setHomeCornerInfo(splitSummaryData(list.get(19)));
			decidePlaystyleMapping.setAwayCornerInfo(splitSummaryData(list.get(20)));
			decidePlaystyleMapping.setHomeBoxShootInInfo(splitSummaryData(list.get(21)));
			decidePlaystyleMapping.setAwayBoxShootInInfo(splitSummaryData(list.get(22)));
			decidePlaystyleMapping.setHomeBoxShootOutInfo(splitSummaryData(list.get(23)));
			decidePlaystyleMapping.setAwayBoxShootOutInfo(splitSummaryData(list.get(24)));
			decidePlaystyleMapping.setHomeGoalPostInfo(splitSummaryData(list.get(25)));
			decidePlaystyleMapping.setAwayGoalPostInfo(splitSummaryData(list.get(26)));
			decidePlaystyleMapping.setHomeGoalHeadInfo(splitSummaryData(list.get(27)));
			decidePlaystyleMapping.setAwayGoalHeadInfo(splitSummaryData(list.get(28)));
			decidePlaystyleMapping.setHomeKeeperSaveInfo(splitSummaryData(list.get(29)));
			decidePlaystyleMapping.setAwayKeeperSaveInfo(splitSummaryData(list.get(30)));
			decidePlaystyleMapping.setHomeFreeKickInfo(splitSummaryData(list.get(31)));
			decidePlaystyleMapping.setAwayFreeKickInfo(splitSummaryData(list.get(32)));
			decidePlaystyleMapping.setHomeOffsideInfo(splitSummaryData(list.get(33)));
			decidePlaystyleMapping.setAwayOffsideInfo(splitSummaryData(list.get(34)));
			decidePlaystyleMapping.setHomeFoulInfo(splitSummaryData(list.get(35)));
			decidePlaystyleMapping.setAwayFoulInfo(splitSummaryData(list.get(36)));
			decidePlaystyleMapping.setHomeYellowCardInfo(splitSummaryData(list.get(37)));
			decidePlaystyleMapping.setAwayYellowCardInfo(splitSummaryData(list.get(38)));
			decidePlaystyleMapping.setHomeRedCardInfo(splitSummaryData(list.get(39)));
			decidePlaystyleMapping.setAwayRedCardInfo(splitSummaryData(list.get(40)));
			decidePlaystyleMapping.setHomeSlowInInfo(splitSummaryData(list.get(41)));
			decidePlaystyleMapping.setAwaySlowInInfo(splitSummaryData(list.get(42)));
			decidePlaystyleMapping.setHomeBoxTouchInfo(splitSummaryData(list.get(43)));
			decidePlaystyleMapping.setAwayBoxTouchInfo(splitSummaryData(list.get(44)));
			decidePlaystyleMapping.setHomePassCountInfo(splitSummaryData(list.get(45)));
			decidePlaystyleMapping.setAwayPassCountInfo(splitSummaryData(list.get(46)));
			decidePlaystyleMapping.setHomeFinalThirdPassCountInfo(splitSummaryData(list.get(47)));
			decidePlaystyleMapping.setAwayFinalThirdPassCountInfo(splitSummaryData(list.get(48)));
			decidePlaystyleMapping.setHomeCrossCountInfo(splitSummaryData(list.get(49)));
			decidePlaystyleMapping.setAwayCrossCountInfo(splitSummaryData(list.get(50)));
			decidePlaystyleMapping.setHomeTackleCountInfo(splitSummaryData(list.get(51)));
			decidePlaystyleMapping.setAwayTackleCountInfo(splitSummaryData(list.get(52)));
			decidePlaystyleMapping.setHomeClearCountInfo(splitSummaryData(list.get(53)));
			decidePlaystyleMapping.setAwayClearCountInfo(splitSummaryData(list.get(54)));
			decidePlaystyleMapping.setHomeInterceptCountInfo(splitSummaryData(list.get(55)));
			decidePlaystyleMapping.setAwayInterceptCountInfo(splitSummaryData(list.get(56)));
			dataMap.put(score, decidePlaystyleMapping);
		}
		return dataMap;
	}

	/**
	 * 統計データを分割しSummaryに設定する。この時点で平均+1標準偏差,平均-1標準偏差を導出する
	 * @param listStr
	 * @return
	 */
	private DecidePlaystyleSummary splitSummaryData(String listStr) {
		String[] split = listStr.split(",");
		String mean = split[2];
		String sigma = split[3];
		//3分割データ
		if (mean.contains("/") && sigma.contains("/")) {
			List<String> meanList = ExecuteMainUtil.splitGroup(mean);
			List<String> sigmaList = ExecuteMainUtil.splitGroup(sigma);
			List<String> uelist = new ArrayList<String>();
			List<String> shitalist = new ArrayList<String>();
			for (int ind = 0; ind < meanList.size(); ind++) {
				String remarks = "";
				String means = meanList.get(ind);
				String sigmas = sigmaList.get(ind);
				if (means.contains("%")) {
					remarks = "%";
					means = means.replace("%", "");
				}
				if (sigmas.contains("%")) {
					remarks = "%";
					sigmas = sigmas.replace("%", "");
				}
				String ue = String.valueOf(Double.parseDouble(means) +
						Double.parseDouble(sigmas)) + remarks;
				String shita = String.valueOf(Double.parseDouble(means) -
						Double.parseDouble(sigmas)) + remarks;
				uelist.add(ue);
				shitalist.add(shita);
			}
			return new DecidePlaystyleSummary(
					uelist.get(0) + " (" + uelist.get(1) + "/" + uelist.get(2) + ")",
					shitalist.get(0) + " (" + shitalist.get(1) + "/" + shitalist.get(2) + ")");
		} else {
			String remarks = "";
			if (mean.contains("%")) {
				remarks = "%";
				mean = mean.replace("%", "");
			}
			if (sigma.contains("%")) {
				remarks = "%";
				sigma = sigma.replace("%", "");
			}
			String ue = String.valueOf(Double.parseDouble(mean) +
					Double.parseDouble(sigma)) + remarks;
			String shita = String.valueOf(Double.parseDouble(mean) -
					Double.parseDouble(sigma)) + remarks;
			return new DecidePlaystyleSummary(ue, shita);
		}
	}

	/**
	 * パラメータチェック
	 * @param dataKey 国カテゴリキー
	 * @param scoreKey スコアキー
	 * @return boolean
	 */
	private boolean chkInParameter(String dataKey, String scoreKey) {
		if (this.thresHoldMap == null) {
			throw new BusinessException("", "", "", "thresHoldMapにデータがありません");
		}

		// 国カテゴリキーから必要Mapを取得できるか
		if (!this.thresHoldMap.containsKey(dataKey)) {
			throw new BusinessException("", "", "", "thresHoldMapに国カテゴリデータがありません。: " + dataKey);
		}

		Map<String, DecidePlaystyleMapping> map = this.thresHoldMap.get(dataKey);

		// スコアキーから必要Mapを取得できるか,できなければ統計データが存在しないためreturn
		if (!map.containsKey(scoreKey)) {
			return false;
		}
		return true;
	}

}
