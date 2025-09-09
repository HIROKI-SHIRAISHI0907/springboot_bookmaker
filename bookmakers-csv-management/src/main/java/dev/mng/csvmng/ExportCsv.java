package dev.mng.csvmng;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m097.CsvMngEntity;
import dev.application.analyze.bm_m097.CsvMngInputDTO;
import dev.application.analyze.bm_m097.SubInfo;
import dev.application.domain.repository.CsvMngRepository;
import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.DataEntity;
import dev.common.filemng.FileMngWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.mng.domain.repository.BookCsvDataRepository;

/**
 * StatデータCSV出力ロジック
 * - グルーピング: data_category, home_team_name, away_team_name 単位で seq をまとめる
 * - 既存テキスト(seqList.txt)と照合して、再作成/新規を分類
 * - CSV名は <連番番号>.csv （連番=グループ内の最小seq）
 * - 生成は並列、書き込みは順序保証で直列
 */
@Component
//@Transactional
public class ExportCsv {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ExportCsv.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ExportCsv.class.getSimpleName();

	/** 新規でCSV作成をするときのダミー文字列 */
	private static final String CSV_NEW_PREFIX = "mk";

	/** Configクラス */
	@Autowired
	private PathConfig config;

	/** ReaderCurrentCsvInfoBeanクラス */
	@Autowired
	private ReaderCurrentCsvInfoBean bean;

	/** BookCsvDataRepositoryレポジトリクラス */
	@Autowired
	private BookCsvDataRepository bookCsvDataRepository;

	/** CsvMngRepositoryレポジトリクラス */
	@Autowired
	private CsvMngRepository csvMngRepository;

	/** ログ管理 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * CSV作成処理
	 */
	public void execute(CsvMngInputDTO inputDTO) {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 連番組み合わせリスト（過去のグルーピングを保存）
		final String SEQ_LIST = config.getCsvFolder() + "seqList.txt";

		// パス
		final Path CSV_FOLDER = Paths.get(config.getCsvFolder());

		// 1) 現在のグルーピングをDBから作る
		List<List<Integer>> currentGroups = sortSeqs();

		// 2) 既存の seqList を読み込み or 初回作成
		FileMngWrapper fileIO = new FileMngWrapper();
		Path seqListPath = Paths.get(SEQ_LIST);
		List<List<Integer>> textGroups;

		boolean firstRun = !Files.exists(seqListPath);
		if (firstRun) {
			// 初回：全て新規対象。先に現在の定義を書き出しておく（異常終了でも痕跡が残る）
			fileIO.write(SEQ_LIST, currentGroups.toString());
			textGroups = Collections.emptyList();
		} else {
			// 1つ前のアプリケーション起動時に記入したseqList(作成されたCSV番号と同組み合わせとは限らない)
			textGroups = fileIO.readSeqBuckets(SEQ_LIST);
		}

		// 3) 照合して「完全一致なら終了」/「新規・再作成」に分類
		CsvBuildPlan plan = null;
		if (firstRun) {
			CsvBuildPlan plans = new CsvBuildPlan();
			for (List<Integer> curr : currentGroups) {
				plans.onlyNew(CSV_NEW_PREFIX, curr);
			}
			plan = plans;
		} else {
			plan = matchSeqCombPlan(textGroups, currentGroups);
		}

		if (plan == null) {
			// 完全一致 → 作業不要
			String messageCd = "追加レコードなし";
			String fillChar = "既存CSV数: " + currentGroups.size() + "件";
			endLog(METHOD_NAME, messageCd, fillChar);
			return;
		}

		// ここまでで作成するCSVグルーピングとものによっては再作成するCSVの番号が確定しているはず

		// 4) 生成キューを作成（再作成→新規の順で、連番昇順に処理）
		List<SimpleEntry<String, List<Integer>>> ordered = new ArrayList<>();
		for (Map.Entry<Integer, List<Integer>> rt : plan.recreateByCsvNo.entrySet()) {
			String path = CSV_FOLDER.resolve(rt.getKey() + BookMakersCommonConst.CSV).toString();
			ordered.add(new SimpleEntry<>(path, rt.getValue()));
		}

		// 新規（Map<String, List<List<Integer>>> を二重ループ）csv番号を最大採番から連番でつける
		int maxNumber = searchMaxCsvFileNumber(CSV_FOLDER.toString()) + 1;
		int diff = 0;
		for (Map.Entry<String, List<Integer>> entry : plan.newTargets.entrySet()) {
			List<Integer> newList = entry.getValue();
			if (newList == null || newList.isEmpty())
				continue;

			int key = maxNumber + diff;
			String path = CSV_FOLDER.resolve(key + BookMakersCommonConst.CSV).toString();
			ordered.add(new SimpleEntry<>(path, newList));
			diff++;
		}

		// 新規作成,既存CSVの書き換えを設定済

		// 5) 並列でCSV内容生成 → 連番順に直列書き込み
		ensureDir(CSV_FOLDER.toString());
		if (ordered.isEmpty()) {
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, "CSV作成結果", null,
					"対象なし (0件)");
		} else {
			int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
			ExecutorService pool = Executors.newFixedThreadPool(threads);

			// ordered（<csvNo, group>）の順序で Future を作成
			List<CompletableFuture<CsvArtifact>> futures = new ArrayList<>(
					ordered.size());
			for (SimpleEntry<String, List<Integer>> e : ordered) {
				final String path = e.getKey();
				final List<Integer> group = e.getValue();
				futures.add(CompletableFuture.supplyAsync(() -> buildCsvArtifact(path, group), pool));
			}

			int success = 0, failed = 0;
			// join も ordered と同じ順番で行う → 書き込み順序が連番昇順で保証される
			for (int i = 0; i < futures.size(); i++) {
				try {
					CsvArtifact art = futures.get(i).join(); // 生成完了待ち
					if (art == null) {
						failed++;
						continue;
					}
					writeCsvArtifact(art);
					success++;
					// エラーの場合は次のアプリケーション起動時に作成される想定
				} catch (Exception ex) {
					failed++;
					String messageCd = "CSV作成失敗";
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, ex);
				}
			}
			pool.shutdown();
			try {
				pool.awaitTermination(1, TimeUnit.MINUTES);
			} catch (InterruptedException ignore) {
				Thread.currentThread().interrupt();
			}

			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, "CSV作成結果", null,
					"成功: " + success + "件, 失敗: " + failed + "件, 合計: " + (success + failed) + "件");
		}

		// 6) 処理完了後、seqList.txt を最新状態で上書き（将来の差分計算基準）
		if (!firstRun)
			fileIO.write(SEQ_LIST, currentGroups.toString());

		// 7) ステータス更新
		updateStatus(inputDTO);

		endLog(METHOD_NAME, null, null);
	}

	/**
	 * DB から同一データの組み合わせに並び替える
	 */
	private List<List<Integer>> sortSeqs() {
		List<SeqWithKey> rows = this.bookCsvDataRepository.findAllSeqsWithKey();
		List<List<Integer>> result = new ArrayList<>();
		List<Integer> bucket = new ArrayList<>();
		String prevCat = null, prevHome = null, prevAway = null;

		for (SeqWithKey r : rows) {
			boolean newGroup = prevCat == null
					|| !prevCat.equals(r.getDataCategory())
					|| !prevHome.equals(r.getHomeTeamName())
					|| !prevAway.equals(r.getAwayTeamName());

			if (newGroup) {
				if (!bucket.isEmpty()) {
					bucket.sort(Comparator.naturalOrder()); // ★ ここで昇順ソート
					result.add(bucket);
				}
				bucket = new ArrayList<>();
				prevCat = r.getDataCategory();
				prevHome = r.getHomeTeamName();
				prevAway = r.getAwayTeamName();
			}
			// null 予防を入れるなら以下の if でガード
			if (r.getSeq() != null) {
				bucket.add(r.getSeq());
			}
		}
		if (!bucket.isEmpty()) {
			result.add(bucket);
		}
		return result;
	}

	// ======== 照合・分類 ========

	/**
	 * text と db の“組み合わせ集合（順不同）”が完全一致なら null（早期終了）。
	 * 一致しない場合は、db を「再作成」/「新規」に分類し返す。
	 * 「再作成」＝グループ内の通番のうち1つでも text で登場したら該当。
	 * その際、どの旧CSV連番(<連番>.csv)に載っていたか（テキスト側グループの最小seq）を保持。
	 */
	/**
	 * text と db の“組み合わせ集合（順不同）”が完全一致なら null（早期終了）。
	 * 一致しない場合は、db を「再作成」/「新規」に分類し返す。
	 * 再作成判定：ReaderCurrentCsvInfoBean#getCsvInfo() の情報を用い、
	 *             グループ内の通番が 1 つでも既存CSVに含まれれば該当。
	 * その際、どの <CSV番号>.csv に載っていたかを保持（最小CSV番号を採用）。
	 */
	private CsvBuildPlan matchSeqCombPlan(List<List<Integer>> textSeqs, List<List<Integer>> dbSeqs) {
		final String METHOD_NAME = "matchSeqCombPlan";

		textSeqs = (textSeqs != null) ? textSeqs : Collections.emptyList();
		dbSeqs = (dbSeqs != null) ? dbSeqs : Collections.emptyList();

		// 1) 完全一致なら早期終了（順不同で比較）
		Set<String> textKeys = toKeySet(textSeqs);
		Set<String> dbKeys = toKeySet(dbSeqs);
		if (textKeys.equals(dbKeys) && textKeys.size() == dbKeys.size())
			return null;

		// 2) 組み合わせに入っていない通番を判定する。ただしdbKeysの方が少なければスキップ
		List<String> onlyInDb = new ArrayList<>(dbKeys);
		if (onlyInDb.size() > textKeys.size()) {
			onlyInDb.removeAll(textKeys);
			String messageCd = "追加通番件数";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
					onlyInDb.size() + "件");
		}

		// 2) 既存CSV(すでに作成されている対戦チーム-CSV番号キー,通番リスト)
		Map<String, List<Integer>> csvInfoRow = (bean != null ? bean.getCsvInfo() : null);

		CsvBuildPlan plan = new CsvBuildPlan();
		// 3) DB側の組み合わせと既存CSV側の組み合わせを比較し存在する通番リスト群に紐づくCSV番号を取得
		int same = 0;
		int contains = 0;
		int news = 0;
		for (List<Integer> comb : dbSeqs) {
			Integer priorCsvNo = null;
			// 既存のCSVのCSV番号キーと組み合わせ通番
			String flg = "";
			for (Map.Entry<String, List<Integer>> e : csvInfoRow.entrySet()) {
				String versus_csvNo = e.getKey();
				List<Integer> existed = e.getValue();
				// 通番組み合わせが含まれている場合は対象
				flg = chkComb(comb, existed);
				// 一致
				if ("S".equals(flg)) {
					same++;
					break;
					// 含まれている
				} else if ("T".equals(flg)) {
					priorCsvNo = Integer.parseInt(versus_csvNo.split("-")[0]);
					contains++;
					break;
				}
			}

			if ("F".equals(flg)) {
				news++;
			}

			if (priorCsvNo != null) {
				plan.onlyRecreate(priorCsvNo, comb); // 再作成
			} else if ("F".equals(flg)) {
				plan.onlyNew(CSV_NEW_PREFIX, comb); // 新規
			}
		}

		this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                String.format("同一: %d, 含有: %d, 新規: %d, 全体: %d",
                		same, contains, news, dbSeqs.size())
        );
		return plan;
	}

	/** null/重複除去 → 昇順でキー化 */
	private static String keyOfSorted(Collection<Integer> nums) {
		if (nums == null || nums.isEmpty())
			return "";
		List<Integer> list = new ArrayList<>(nums.size());
		for (Integer n : nums)
			if (n != null)
				list.add(n);
		Collections.sort(list); // 昇順
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			if (i > 0)
				sb.append(',');
			sb.append(list.get(i));
		}
		return sb.toString();
	}

	/** groups を正規化して“昇順キー集合”にする */
	private static Set<String> toKeySet(List<List<Integer>> groups) {
		Set<String> res = new LinkedHashSet<>(); // 返却セットの見た目順を安定させたいなら TreeSet<> でも可
		if (groups == null)
			return res;
		for (List<Integer> g : groups) {
			if (g == null || g.isEmpty())
				continue;
			// 重複は消したい → distinct() か Set化してから keyOfSorted へ
			String key = keyOfSorted(new HashSet<>(g));
			if (!key.isEmpty())
				res.add(key);
		}
		return res;
	}

	/**
	 * 変換リスト
	 * @param dList
	 * @return
	 */
	private static Set<Integer> convList(List<Integer> dList) {
		Set<Integer> out = new HashSet<>();
		if (dList != null)
			for (Integer v : dList)
				if (v != null)
					out.add(v);
		return out;
	}

	/**
	 * 通番リストが完全に含まれているかを調査
	 * @param dbComb DB側の通番リスト
	 * @param existedCsvComb 既存CSV側の通番リスト
	 * @return S: 同一データ, T: 含まれる, F: 含まれていない
	 */
	private String chkComb(List<Integer> dbComb, List<Integer> existedCsvComb) {
		String dbCombData = "";
		for (Integer d : dbComb) {
			if (dbCombData.length() > 0) {
				dbCombData += "-";
			}
			dbCombData += d;
		}
		String existedCsvCombData = "";
		for (Integer e : existedCsvComb) {
			if (existedCsvCombData.length() > 0) {
				existedCsvCombData += "-";
			}
			existedCsvCombData += e;
		}

		if (dbCombData.equals(existedCsvCombData)) {
			return "S";
		}

		return (dbCombData.contains(existedCsvCombData)
				|| existedCsvCombData.contains(dbCombData)) ? "T" : "F";
	}

	// ======== CSV 生成・書き込み ========

	/**
	 * 並列ステージ：CSVの中身を構築して返す（重い処理はここで）
	 * @param path
	 * @param seqGroup
	 * @return
	 */
	private CsvArtifact buildCsvArtifact(String path, List<Integer> seqGroup) {
		final String METHOD_NAME = "buildCsvArtifact";
		if (seqGroup == null || seqGroup.isEmpty())
			return null;

		List<Integer> ids = new ArrayList<>(seqGroup.size());
		for (Integer n : new TreeSet<>(seqGroup)) { // 昇順
			if (n != null)
				ids.add(n);
		}
		try {
			List<DataEntity> result = this.bookCsvDataRepository.findBySeq(ids);
			return new CsvArtifact(path, result);
		} catch (Exception e) {
			String messageCd = "DBエラー";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
		}
		return null;

	}

	/** 直列ステージ：ファイルへ書き込み（上書き可・順序保証） */
	private void writeCsvArtifact(CsvArtifact art) {
		FileMngWrapper fw = new FileMngWrapper();
		fw.csvWrite(art.getFilePath(), art.getContent());
	}

	/** ディレクトリ作成（存在すれば何もしない） */
	private static void ensureDir(String dir) {
		try {
			Files.createDirectories(Paths.get(dir));
		} catch (Exception ignore) {
			/* no-op */ }
	}

	/**
	 * ステータス新規登録・更新
	 */
	private void updateStatus(CsvMngInputDTO inputDTO) {
		final String METHOD_NAME = "updateStatus";
		if (inputDTO == null || inputDTO.getSubInfo() == null || inputDTO.getSubInfo().isEmpty()) {
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, "updateStatus", null, "対象なし");
			return;
		}

		int totalAffected = 0;
		int insertAffected = 0;
		int updateAffected = 0;
		int noUpdateAffected = 0;

		for (SubInfo subInfo : inputDTO.getSubInfo()) {
			String country = subInfo.getCountry();
			String league = subInfo.getLeague();
			String status = subInfo.getStatus();

			List<CsvMngEntity> resultList = this.csvMngRepository.findByData(country, league);
			if (!resultList.isEmpty()) {
				String exStatus = resultList.get(0).getStatus();
				if (!exStatus.equals(status)) {
					String exId = resultList.get(0).getId();
					int result = this.csvMngRepository.updateById(exId, status);
					if (result != 1) {
						String messageCd = "更新エラー";
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
						this.manageLoggerComponent.createSystemException(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
					}
					this.manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, "更新件数", null, "BM_M097 更新件数: 1件");
					updateAffected++;
				} else {
					noUpdateAffected++;
				}
			} else {
				CsvMngEntity ent = new CsvMngEntity();
				ent.setCountry(country);
				ent.setLeague(league);
				ent.setStatus(status);
				int result = this.csvMngRepository.insert(ent);
				if (result != 1) {
					String messageCd = "新規登録エラー";
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
					this.manageLoggerComponent.createSystemException(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
				}
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, "登録件数", null, "BM_M097 登録件数: 1件");
				insertAffected++;
			}
			totalAffected++;
		}

		String messageCd = "全体更新件数";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
				"BM_M097 対象件数: " + totalAffected + "件, 全体登録件数: "
						+ insertAffected + "件, 全体更新件数: " + updateAffected + "件, "
						+ "全体失敗件数: " + noUpdateAffected + "件");
	}

	/**
	 * 最大の数字を持つCSV番号を探す（現仕様では未使用。必要なら利用可能）
	 */
	private int searchMaxCsvFileNumber(String dirs) {
		final String METHOD_NAME = "searchMaxCsvFileNumber";
		File directory = new File(dirs);
		File[] files = directory.listFiles((dir, name) -> name.matches("\\d+\\.csv"));
		int maxFileNumber = Integer.MIN_VALUE;
		if (files != null && files.length > 0) {
			for (File f : files) {
				String name = f.getName();
				int dot = name.indexOf('.');
				int num = Integer.parseInt(name.substring(0, dot));
				if (num > maxFileNumber)
					maxFileNumber = num;
			}
			if (maxFileNumber != Integer.MIN_VALUE) {
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, "", null,
						"最大のファイル番号は: " + maxFileNumber + ".csv");
			} else {
				String messageCd = "CSVファイルが見つかりませんでした。";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
			}
		} else {
			return 0;
		}
		return maxFileNumber;
	}

	/** 終了ログ */
	private void endLog(String method, String messageCd, String fillChar) {
		if (messageCd != null && fillChar != null) {
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, method, messageCd, fillChar);
		}
		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, method);
	}
}
