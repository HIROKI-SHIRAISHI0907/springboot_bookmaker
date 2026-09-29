package dev.application.analyze.bm_m006;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m006.CountryLeagueSummaryWriter.CountryLeagueKey;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.CountryLeagueParser;

/**
 * BM_M006統計分析ロジック
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * 国・リーグ・シーズンごとに「データを処理した回数の合計」を country_league_summary.csv_count に積み上げる。
 * 1回の処理で、入力に含まれる試合（試合キー）の数を国,リーグごとに数え、その数を加算する。
 * 同じ試合でも流れてくるたびに数える（試合数ではなく、処理した回数の合計）。
 * </p>
 *
 * <h2>処理の流れ</h2>
 * <ol>
 *   <li>入力のキー（"国: リーグ - ラウンドN"）を {@link CountryLeagueParser} で国・リーグに分ける（形式が違うキーは無視）。</li>
 *   <li>国,リーグごとに試合数（空の試合は除く）を合計する。</li>
 *   <li>{@link CountryLeagueSummaryWriter#addCountsAll} で全リーグ分を1トランザクションで加算保存する
 *       （シーズン・seq は Writer で設定）。</li>
 * </ol>
 *
 * <h2>修正履歴</h2>
 * <ul>
 *   <li><b>国・リーグ名の正規化（NFKC・空白の詰め）を廃止し、前後の空白除去だけにした</b>:
 *       正規化で名前が変わると、country_league_season_master や他のテーブルと結合できず、シーズンも取得できなくなるため。</li>
 *   <li>parallelStream + JVM 内ロック（lockMap）を廃止。加算は Writer の1本の SQL で行うため、ロックは不要
 *       （JVM 内ロックは別プロセスには効かず、lockMap は増え続けていた）。</li>
 *   <li>リーグごとのトランザクション → 全リーグで1トランザクション（途中失敗で一部だけ加算される問題の解消）。</li>
 *   <li>入力・キーの分割結果が null の場合の NullPointerException を解消。</li>
 *   <li>data_count（未使用・常に "0"）を廃止。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>csv_count は試合数ではない</b>: 試合途中から終了まで何度も流れてくる試合は、その回数分数えられる。</li>
 *   <li><b>名前の表記ゆれ</b>: 正規化をやめたため、同じリーグでも表記が違う（全角/半角など）と別の行になる。
 *       元データ側・マスタ側の表記を揃えること。</li>
 *   <li><b>シーズンが取得できない国,リーグ</b>: 加算されない（Writer のログに件数が出る）。</li>
 *   <li><b>保存失敗時</b>: その回の加算はすべてロールバックされる。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Component
public class CountryLeagueSummaryStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueSummaryStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueSummaryStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M006_COUNTRY_LEAGUE_SUMMARY";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M006";

	/** 登録処理 */
	@Autowired
	private CountryLeagueSummaryWriter countryLeagueSummaryWriter;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		int invalidKeyCount = 0;
		int matchTotal = 0;
		try {
			if (entities == null || entities.isEmpty()) {
				debugLog(METHOD_NAME, BM_NUMBER + " 入力データなし");
				return;
			}

			// 国,リーグごとの試合数
			Map<CountryLeagueKey, Integer> counts = new HashMap<>();
			for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
				CountryLeagueKey key = toKey(entry.getKey());
				if (key == null) {
					invalidKeyCount++;
					debugLog(METHOD_NAME, BM_NUMBER + " 国,リーグを分割できないためスキップ: " + entry.getKey());
					continue;
				}
				int add = countMatches(entry.getValue());
				if (add <= 0) {
					continue;
				}
				counts.merge(key, add, Integer::sum);
				matchTotal += add;
			}

			this.countryLeagueSummaryWriter.addCountsAll(counts);
		} finally {
			debugLog(METHOD_NAME, BM_NUMBER + " 試合数合計=" + matchTotal + ", 分割不可キー=" + invalidKeyCount);
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 「国,リーグ」キーを国・リーグに分ける（前後の空白のみ除去。分けられなければ null）。
	 */
	private static CountryLeagueKey toKey(String countryLeague) {
		if (countryLeague == null || countryLeague.isBlank()) {
			return null;
		}
		String[] sp = CountryLeagueParser.parse(countryLeague);
		if (sp == null || sp.length < 2 || sp[0] == null || sp[1] == null) {
			return null;
		}
		String country = sp[0].trim();
		String league = sp[1].trim();
		if (country.isEmpty() || league.isEmpty()) {
			return null;
		}
		return new CountryLeagueKey(country, league);
	}

	/** 行がある試合の数 */
	private static int countMatches(Map<String, List<BookDataEntity>> matchMap) {
		if (matchMap == null) {
			return 0;
		}
		int n = 0;
		for (List<BookDataEntity> rows : matchMap.values()) {
			if (rows != null && !rows.isEmpty()) {
				n++;
			}
		}
		return n;
	}

	private void debugLog(String methodName, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, methodName, MessageCdConst.MCD00099I_LOG, message);
	}
}
