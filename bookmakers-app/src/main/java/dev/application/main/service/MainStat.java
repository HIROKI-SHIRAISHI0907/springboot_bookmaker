package dev.application.main.service;


import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.application.analyze.bm_m098.CsvSeqManageService;
import dev.application.analyze.common.error.AnalyzeErrorRecorder;
import dev.application.analyze.interf.ServiceIF;
import dev.common.constant.BatchResultConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.error.AnalyzeErrorInfo;
import dev.common.error.AnalyzeErrorType;
import dev.common.getinfo.GetStatInfo;
import dev.common.logger.ManageLoggerComponent;
import lombok.extern.slf4j.Slf4j;

/**
 * 統計バッチの入口。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * 処理する CSV を決めて1ファイルずつ読み込み、{@link CoreStat} に渡して統計を反映する。
 * 通常は csv_seq_manage（{@link CsvSeqManageService}）で「前回の続きの CSV 番号の範囲」を決め、
 * 1ファイル終わるごとに処理済みの番号を記録する（途中で止まっても次回は続きから）。
 * </p>
 * <ul>
 *   <li>通常（2026-11-22 変更）: csv_detail_manage で未反映（check_fin_flg='0'）の csv_id のうち、S3 にある CSV。
 *       以前は csv_seq_manage の CSV 番号の範囲（from〜to）で決めていたが、B011 の CSV 名が
 *       「国-リーグ-ラウンドN/ホーム-アウェー.csv」になって番号が無く、範囲の絞り込みで全部外れていた。
 *       csv_seq_manage は使わない・更新しない。</li>
 *   <li>BM_COUNTRY・BM_LEAGUE 指定: その国・リーグの CSV 全部（csv_seq_manage は使わない・更新しない）。</li>
 *   <li>BM_JOB=B014: 範囲で絞らず全 CSV（csv_seq_manage は使わない・更新しない）。</li>
 * </ul>
 * <p>
 * どのモードでも、同じ試合を2回反映しないかどうかは CoreStat が csv_detail_manage で判定する
 * （反映済みの試合はスキップ。各 Stat の保存は UPSERT なので、仮に流れても行は増えない）。
 * </p>
 *
 * <h2>修正内容</h2>
 * <ul>
 *   <li><b>コンパイルエラー</b>: {@code @Autowireds} → {@code @Autowired}。</li>
 *   <li><b>RankingService（旧 BM_M027）の呼び出しを削除</b>。M027 のランキングはビュー
 *       score_based_feature_ranking / each_team_score_based_feature_ranking に置き換わっている。
 *       また CoreStat が処理後に Map を clear するため、その後に呼ばれる RankingService には常に空の Map が渡っていた。</li>
 *   <li><b>CoreStat の外側での再試行をやめた</b>: CoreStat は終わると Map を clear するので、外側でやり直すと
 *       2回目は空の Map で「対象なし」として正常終了し、その CSV は処理済みとして記録されていた
 *       （統計が入らないまま進む）。Stat ごとの再試行は CoreStat の中で行う。</li>
 *   <li>再試行の処理を CoreStat と共通の {@link DbRetryExecutor} にまとめた。</li>
 *   <li>BM_COUNTRY だけ指定した場合の扱いを明記（国だけでは絞らず、通常モードで動く）。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>1ファイルでも失敗すると、そこで止まって BATCH_ERR</b>。それまでのファイルは処理済みとして記録されているので、
 *       次回は失敗したファイルからやり直す。同じファイルで毎回失敗すると先に進まない（ログの key で確認）。</li>
 *   <li><b>ファイル名から番号が取れない key</b>（"…/123.csv" 形式でない）は処理済み番号を進めない。
 *       範囲の最後まで終わったら、最後に to まで処理済みにする。</li>
 *   <li>手動データ（manualFlg = true）はこのクラスからは流さない（別の入口）。</li>
 * </ul>
 */
@Service
@Slf4j
public class MainStat implements ServiceIF {

	/** 最初からこの本数続けて失敗したら全体を止める（2026-11-27 追加） */
	private static final int SYSTEMIC_FAIL_LIMIT = 5;


	private static final String PROJECT_NAME = MainStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = MainStat.class.getName();

	/** 範囲で絞らずに全 CSV を処理するジョブ */
	private static final String JOB_ALL = "B014";

	/** S3 key のファイル名の番号（"…/123.csv" の 123） */
	private static final Pattern SEQ_FROM_KEY = Pattern.compile("(?:^|.*/)([0-9]+)\\.csv$");

	/** 取り込めなかった CSV を未反映データ参照（analyze_error_match。BM_B006）に出す（2026-11-27 追加） */
	@Autowired
	private AnalyzeErrorRecorder analyzeErrorRecorder;

	/** 未反映データ参照の BM 番号 */
	private static final String ERROR_BM = "BM_B006";

	@Autowired
	private GetStatInfo getStatInfo;

	@Autowired
	private CsvSeqManageService csvSeqManageService;

	/** 2026-11-22: 未反映の CSV を決める */
	@Autowired
	private dev.application.domain.repository.bm.CsvDetailManageRepository csvDetailManageRepository;

	@Autowired
	private CoreStat coreStat;

	@Autowired
	private DbRetryExecutor dbRetryExecutor;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	@Override
	public int execute() throws Exception {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		try {
			String job = safe(System.getenv("BM_JOB")).trim();
			String country = safe(System.getenv("BM_COUNTRY")).trim();
			String league = safe(System.getenv("BM_LEAGUE")).trim();

			if (country.isEmpty() && !league.isEmpty()) {
				throw new IllegalArgumentException("BM_LEAGUE を指定する場合は BM_COUNTRY も指定してください。");
			}

			boolean countryLeagueMode = !country.isEmpty() && !league.isEmpty();
			// 2026-11-22: 通常の実行は csv_detail_manage の未反映の CSV を読む（csv_seq_manage の番号の範囲は使わない）
			boolean pendingMode = !JOB_ALL.equals(job) && !countryLeagueMode;
			CsvSeqManageService.CsvSeqRange range = null;
			log.info("[MainStat] モード: {}（BM_JOB={}, BM_COUNTRY={}, BM_LEAGUE={}）",
					pendingMode ? "未反映の CSV" : countryLeagueMode ? "国・リーグ指定" : "全 CSV", job, country, league);

			List<String> keys = pendingMode ? listPendingKeys() : listTargetKeys(countryLeagueMode, country, league, range);
			if (keys == null || keys.isEmpty()) {
				log.info("[MainStat] target keys is empty");
				return BatchResultConst.BATCH_OK;
			}
			log.info("[MainStat] target keys.size = {}", keys.size());

			int lastProcessed = (range != null) ? range.getFrom() - 1 : -1;
			int process = 0;
			int total = (range != null) ? range.getLastOnDb() : keys.size();

			// 2026-11-27: 1 本の CSV の失敗で全体を止めない（以前は 1 本失敗すると残り全部が翌日回しになり、
			// 同じ CSV で毎日止まり続けた）。失敗した CSV は csv_detail_manage が '0' のまま残り、次の実行で取り直す
			// （統計の保存は UPSERT なので二重にならない）。
			// ただし最初から SYSTEMIC_FAIL_LIMIT 本続けて失敗したら、設定ミスなど全体の問題とみなして止める。
			int failed = 0;
			int okCount = 0;
			for (String key : keys) {
				process++;
				int statResult;
				try {
					statResult = processKey(key);
					okCount++;
					// 前に失敗していた CSV が取り込めたら、未反映データ参照の行を自動解決
					this.analyzeErrorRecorder.resolve(ERROR_BM, errorInfoOfKey(key));
				} catch (Exception ex) {
					failed++;
					log.error("[MainStat] CSV の取り込みに失敗しました（次の実行で取り直します） key={}, e={}", key, ex.toString(), ex);
					String msg = ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
					this.analyzeErrorRecorder.record(ERROR_BM, AnalyzeErrorType.STAT_IMPORT_FAILED,
							AnalyzeErrorType.STAT_IMPORT_FAILED.message(msg), errorInfoOfKey(key), null, ex);
					if (okCount == 0 && failed >= SYSTEMIC_FAIL_LIMIT) {
						throw new IllegalStateException("最初の " + failed + " 本の CSV がすべて失敗したため止めます（設定・環境の問題の可能性）。最後の例外: " + ex, ex);
					}
					continue;
				}

				if (range != null) {
					lastProcessed = Math.max(lastProcessed, extractSeq(key));
					markSuccess(lastProcessed);
				}
				log.info("[MainStat] progress {}/{}, key={}, statResult={}, markSeq={}",
						process, total, key, statResult, range != null ? lastProcessed : "-");
			}

			if (range != null && failed == 0) {
				markSuccess(range.getTo());
				log.info("[MainStat] final mark success. seq={}", range.getTo());
			}

			log.info("[MainStat] 終了 対象={}, 成功={}, 失敗={}", keys.size(), okCount, failed);
			return failed == 0 ? BatchResultConst.BATCH_OK : BatchResultConst.BATCH_ERR;

		} catch (Exception e) {
			log.error("[MainStat] failed", e);
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG, null,
					"MainStat execute failed. message=" + safe(e.getMessage()));
			return BatchResultConst.BATCH_ERR;

		} finally {
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		}
	}

	/**
	 * 未反映（csv_detail_manage.check_fin_flg='0'）の CSV の key 一覧（S3 にあるものだけ。2026-11-22 追加）。
	 * csv_id は統計 CSV バケットの S3 キー（B011 が prefix なしで出力）。S3 に無い csv_id は数だけログに出す。
	 */
	private List<String> listPendingKeys() throws Exception {
		List<String> ids = this.dbRetryExecutor.call("csvDetailManageRepository.findUnfinishedCsvIds",
				() -> this.csvDetailManageRepository.findUnfinishedCsvIds());
		if (ids == null || ids.isEmpty()) {
			log.info("[MainStat] 未反映の CSV はありません（csv_detail_manage.check_fin_flg='0' が 0 件）");
			return java.util.Collections.emptyList();
		}
		List<String> all = this.dbRetryExecutor.call("getStatInfo.listCsvKeysInRange(all)",
				() -> this.getStatInfo.listCsvKeysInRange(null, null));
		java.util.Set<String> onS3 = new java.util.HashSet<>(all == null ? java.util.List.of() : all);
		List<String> keys = new java.util.ArrayList<>();
		int missing = 0;
		for (String id : ids) {
			if (onS3.contains(id)) {
				keys.add(id);
			} else {
				missing++;
			}
		}
		log.info("[MainStat] 未反映の CSV: csv_detail_manage={} 件 / S3 にある={} 件 / S3 に無い={} 件",
				ids.size(), keys.size(), missing);
		return keys;
	}

	/**
	 * 処理する CSV の key 一覧。
	 */
	private List<String> listTargetKeys(boolean countryLeagueMode, String country, String league,
			CsvSeqManageService.CsvSeqRange range) throws Exception {
		if (countryLeagueMode) {
			List<String> keys = this.dbRetryExecutor.call(
					"getStatInfo.listCsvKeysByCountryLeague:" + country + ":" + league,
					() -> this.getStatInfo.listCsvKeysByCountryLeague(country, league));
			log.info("[MainStat] 国・リーグ指定: BM_COUNTRY={}, BM_LEAGUE={}, keys.size={}",
					country, league, keys == null ? 0 : keys.size());
			return keys;
		}
		String from = (range == null) ? null : String.valueOf(range.getFrom());
		String to = (range == null) ? null : String.valueOf(range.getTo());
		List<String> keys = this.dbRetryExecutor.call("getStatInfo.listCsvKeysInRange",
				() -> this.getStatInfo.listCsvKeysInRange(from, to));
		log.info("[MainStat] 範囲指定: from={}, to={}, keys.size={}", from, to, keys == null ? 0 : keys.size());
		return keys;
	}

	/**
	 * 1ファイルを読み込んで CoreStat に渡す。
	 *
	 * @return CoreStat が処理した試合数（ファイルが空なら 0）
	 */
	private int processKey(String key) throws Exception {
		// 読み込みは再試行してよい（読むだけ）
		Map<String, Map<String, List<BookDataEntity>>> oneMap = this.dbRetryExecutor.call(
				"getStatInfo.getStatMapForSingleKey:" + key,
				() -> this.getStatInfo.getStatMapForSingleKey(key));
		if (oneMap == null || oneMap.isEmpty()) {
			log.info("[MainStat] oneMap empty. skip key={}", key);
			return 0;
		}
		log.info("[MainStat] process start key={}, category={}", key, oneMap.keySet());
		// CoreStat は中で Stat ごとに再試行し、終わると oneMap を clear する（ここでは再試行しない）
		return this.coreStat.execute(oneMap, false);
	}

	private void markSuccess(int seq) throws Exception {
		this.dbRetryExecutor.run("csvSeqManageService.markSuccess:" + seq,
				() -> this.csvSeqManageService.markSuccess(seq));
	}

	/** S3 key のファイル名の番号（取れなければ -1） */
	private static int extractSeq(String key) {
		if (key == null) {
			return -1;
		}
		Matcher m = SEQ_FROM_KEY.matcher(key);
		if (!m.find()) {
			return -1;
		}
		try {
			return Integer.parseInt(m.group(1));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	/**
	 * CSV のキー（例: 日本-J1 リーグ-ラウンド4/ガンバ大阪-サンフレッチェ広島.csv）から、未反映データ参照の試合の情報を作る（2026-11-27 追加）。
	 * フォルダ「国-リーグ-ラウンドN」の最初の "-" までを国、最後の "-" までをリーグ、ファイル名の最初の "-" でホーム・アウェーに分ける
	 * （名前に "-" を含むチームは分け方がずれることがあるが、記録と自動解決で同じ分け方なので対応はとれる）。
	 */
	static AnalyzeErrorInfo errorInfoOfKey(String key) {
		String k = key == null ? "" : key.replace('\\', '/');
		int slash = k.lastIndexOf('/');
		String folder = slash > 0 ? k.substring(0, slash) : "";
		int prevSlash = folder.lastIndexOf('/');
		if (prevSlash >= 0) {
			folder = folder.substring(prevSlash + 1);
		}
		String file = slash >= 0 ? k.substring(slash + 1) : k;
		if (file.toLowerCase().endsWith(".csv")) {
			file = file.substring(0, file.length() - 4);
		}
		String country = null;
		String league = null;
		int d1 = folder.indexOf('-');
		int d2 = folder.lastIndexOf('-');
		if (d1 > 0 && d2 > d1) {
			country = folder.substring(0, d1).trim();
			league = folder.substring(d1 + 1, d2).trim();
		}
		String home = file;
		String away = null;
		int t = file.indexOf('-');
		if (t > 0) {
			home = file.substring(0, t).trim();
			away = file.substring(t + 1).trim();
		}
		AnalyzeErrorInfo i = AnalyzeErrorInfo.match(folder.isEmpty() ? null : folder, home, away).detail("csv=" + key);
		if (country != null && league != null) {
			i.countryLeague(country, league);
		}
		return i;
	}
}
