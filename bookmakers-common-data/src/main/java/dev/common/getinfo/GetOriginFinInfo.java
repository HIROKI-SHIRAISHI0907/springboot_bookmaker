package dev.common.getinfo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadOrigin;
import dev.common.readfile.dto.MatchKeyItem;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.s3.S3Operator;
import dev.common.util.CsvContentUtil;
import dev.common.util.S3QuarantineUtil;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * 「過去の終了済」データ取得管理クラス
 *
 * 要件:
 * 1) yyyy-MM-dd/ で昇順
 * 2) 同一日付内の mid=... は「S3で見えた順」（＝出現順）を維持
 * 3) 同一 mid 内の seq=...csv は文字列順
 *
 * 返すMap:
 * - Key: S3キー（FinGettingStat が S3のファイルを削除できるように）
 * - Value: DataEntity一覧（DataEntity.file には S3 key をセット）
 *
 * <p>2026-10 修正（ヘッダしかない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>ヘッダのみ（データ行 0 件）のファイルは、読み込みクラスに渡す前に判定し（{@link CsvContentUtil#isHeaderOnly}）、
 *       key = S3 キー、value = 空のリスト で返す。FinGettingStat はこのファイルを登録せずに削除する。
 *       （以前は空のリストを結果に入れず飛ばしていたため、削除されずに毎回読み続けていた）</li>
 *   <li>読み込みに失敗したファイルは結果に入れない。</li>
 *   <li>ログのラベルを [B001] から [B010] に修正。読み込み結果の件数ログを追加。</li>
 * </ul>
 *
 * <p>2026-10 修正（読み込めない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>読み込めないファイル（読み込みクラスがエラーを返した、またはデータ行はあるのに正しい行が 1 件も無い）は、
 *       {@link S3QuarantineUtil} で error/＜元のキー＞.error に退避する。残すと Lambda が「.csv あり」と判定して
 *       ECS を起動し続け、毎回同じファイルで失敗するため。中身は退避先に残るので後から調べられる。</li>
 *   <li>ダウンロード失敗・タイムアウトは一時的なエラーとみなし、退避せず次回もう一度読む。</li>
 *   <li>ReadOrigin は引用符の閉じ忘れなど CSV として壊れているとファイル全体が読み込みエラーになる。104 列未満の行は読み飛ばすだけなので、全行が列数不足だと「正しいデータ行が 0 件」になる。</li>
 * </ul>
 */
@Component
public class GetOriginFinInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetOriginFinInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetOriginFinInfo.class.getName();

	/** LoggerFactory */
	private static final Logger log = LoggerFactory.getLogger(GetOriginFinInfo.class);

	/** 取得対象キー 例: 2026-02-05/mid=d2thPpKD/seq=000035_20260205T000138Z.csv */
	private static final Pattern OUTPUTS_CSV_KEY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}/mid=[^/]+/seq=.*\\.csv$");

	/** S3オペレーター */
	@Autowired
	private S3Operator s3Operator;

	/** パス設定 */
	@Autowired
	private PathConfig config;

	/** ファイル読み込みクラス */
	@Autowired
	private ReadOrigin readOrigin;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 *
	 * @return key = S3キー、value = DataEntity一覧（ヘッダのみなら空のリスト）。
	 *         対象ファイルが無ければ空のMap、想定外のエラーなら null。
	 */
	public Map<String, List<DataEntity>> getData() {
		final String METHOD_NAME = "getData";

		String bucket = config.getS3BucketsOutputsFin();

		// 1) 全走査して matcher に合うkeyだけ抽出（S3OperatorにlistAllKeysが無いのでここでやる）
		List<String> matchedKeys = listAllMatchedKeys(bucket, OUTPUTS_CSV_KEY, null);

		log.info("[B010] S3 bucket={} prefix={} keys.size={} keys(sample)={}",
				bucket, OUTPUTS_CSV_KEY,
				(matchedKeys == null ? -1 : matchedKeys.size()),
				(matchedKeys == null ? null : matchedKeys.stream().limit(5).collect(Collectors.toList())));

		if (matchedKeys == null || matchedKeys.isEmpty()) {
			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP, "データなし(S3)");
			return new LinkedHashMap<>();
		}

		// 2) 要件どおりに並べ替え
		List<String> orderedKeys = orderKeysByDateThenMidEncounterThenSeqString(matchedKeys);

		if (orderedKeys.isEmpty()) {
			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP, "データなし(S3: 並べ替え対象なし)");
			return new LinkedHashMap<>();
		}

		// 3) orderedKeys の順を崩さずに読み込む（invokeAllはtasks順を保持）
		Map<String, List<DataEntity>> resultMap = new LinkedHashMap<>();

		int emptyFiles = 0;
		int failedFiles = 0;

		// 中身が原因で読めないファイル（退避する）
		List<String> brokenKeys = new ArrayList<>();

		int poolSize = Math.min(8, orderedKeys.size());
		ExecutorService executor = Executors.newFixedThreadPool(poolSize);

		try {
			List<Callable<ReadOneResult>> tasks = orderedKeys.stream()
					.map(k -> (Callable<ReadOneResult>) () -> readOne(bucket, k))
					.collect(Collectors.toList());

			List<Future<ReadOneResult>> futures = executor.invokeAll(tasks, 10, TimeUnit.MINUTES);

			// invokeAll の結果は tasks と同じ順番（i 番目 = orderedKeys の i 番目）
			for (int i = 0; i < futures.size(); i++) {
				String s3Key = orderedKeys.get(i);
				Future<ReadOneResult> f = futures.get(i);

				// タイムアウト: 削除せず次回もう一度読む
				if (f.isCancelled()) {
					failedFiles++;
					manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00003E_EXECUTION_SKIP,
							null, "ReadOriginS3 timeout/cancel key=" + s3Key + "（削除せず次回もう一度読む）");
					continue;
				}

				ReadOneResult r;
				try {
					r = f.get();
				} catch (ExecutionException ee) {
					failedFiles++;
					manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00003E_EXECUTION_SKIP,
							ee, "ReadOriginS3 read failed key=" + s3Key + "（削除せず次回もう一度読む）");
					continue;
				}

				// 読み込み失敗
				if (r == null || !r.ok) {
					failedFiles++;

					// 中身が原因（読み込みクラスのエラー・正しい行が0件）なら退避、それ以外（ダウンロード失敗等）は次回もう一度読む
					boolean broken = (r != null && r.broken);
					if (broken) {
						brokenKeys.add(s3Key);
					}

					manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00003E_EXECUTION_SKIP,
							null,
							"ReadOriginS3 failed key=" + s3Key
									+ (broken ? "（error/ に退避します）" : "（削除せず次回もう一度読む）" + r.thrown));
					continue;
				}

				// ヘッダのみ: 空のリストで返し、FinGettingStat で登録せずに削除する
				if (r.entities == null || r.entities.isEmpty()) {
					emptyFiles++;
					resultMap.put(r.s3Key, new ArrayList<>());
					manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00099I_LOG,
							"データ行なし（ヘッダのみ）のため登録せず削除対象にします: key=" + r.s3Key);
					continue;
				}

				// ★Mapキーは本来のS3キー（FinGettingStatがS3から削除できるように）
				resultMap.put(r.s3Key, r.entities);
			}

		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			manageLoggerComponent.createBusinessException(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00004E_THREAD_INTERRUPTION, ie.getCause(), ie);

		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN,
					e, "S3 Origin読み込みエラー");
			return null;

		} finally {
			executor.shutdownNow();
		}

		// 中身が原因で読めないファイルを退避する（残すと Lambda が ECS を起動し続けるため）
		int quarantined = 0;
		for (String brokenKey : brokenKeys) {
			if (S3QuarantineUtil.quarantine(bucket, brokenKey, "読み込みエラー（ReadOrigin）",
					s3Operator, manageLoggerComponent, PROJECT_NAME, CLASS_NAME, METHOD_NAME)) {
				quarantined++;
			}
		}

		log.info("[B010] 読み込み結果: files={} (データあり={}, ヘッダのみ={}, 読み込み失敗={}, うち退避={})",
				orderedKeys.size(), resultMap.size() - emptyFiles, emptyFiles, failedFiles, quarantined);

		return resultMap;
	}

	// =========================================================
	// S3全走査して matcher に合う key を集める
	// さらに items が指定されている場合は、items内キーを含むものだけに絞る
	// =========================================================
	private List<String> listAllMatchedKeys(String bucket, Pattern matcher, List<MatchKeyItem> items) {
		List<String> keys = new ArrayList<>();

		// matcher に合うものだけは S3Operator 側で拾ってもらう
		List<S3Object> objs = s3Operator.listAllDateCsvObjectsSortedByLastModifiedAsc(bucket, matcher);
		if (objs == null) {
			return keys;
		}

		// items が null or 空なら「全取得対象」
		if (items == null || items.isEmpty()) {
			for (S3Object o : objs) {
				keys.add(o.key());
			}
			return keys;
		}

		// items 内の「含まれているキー」を抽出（※ここは MatchKeyItem の実装に合わせて getter を調整）
		List<String> includes = new ArrayList<>();
		for (MatchKeyItem it : items) {
			if (it == null) {
				continue;
			}

			// 例：MatchKeyItem に getMatchKey() がある想定
			String k = it.getMatchKey();
			if (k == null) {
				continue;
			}

			k = k.trim();
			if (k.isEmpty()) {
				continue;
			}

			includes.add(k);
		}

		// 抽出結果が空なら全件扱いにする（必要に応じて「0件返す」に変えてもOK）
		if (includes.isEmpty()) {
			for (S3Object o : objs) {
				keys.add(o.key());
			}
			return keys;
		}

		// items のどれかのキーが S3キー文字列に含まれていれば採用
		for (S3Object o : objs) {
			String s3Key = o.key();
			if (s3Key == null || s3Key.isBlank()) {
				continue;
			}

			boolean hit = false;
			for (String inc : includes) {
				if (s3Key.contains(inc)) {
					hit = true;
					break;
				}
			}

			if (hit) {
				keys.add(s3Key);
			}
		}

		return keys;
	}

	// =========================================================
	// 並び順: date昇順 -> midは出現順維持 -> seqは文字列順
	// =========================================================
	private List<String> orderKeysByDateThenMidEncounterThenSeqString(List<String> keys) {

		// date -> (mid -> list(keys))  ※midはLinkedHashMapで出現順維持
		Map<String, LinkedHashMap<String, List<String>>> grouped = new HashMap<>();

		for (String key : keys) {
			// yyyy-MM-dd/mid=xxx/seq=...
			String[] parts = key.split("/", 3);
			if (parts.length < 3) {
				continue;
			}

			String date = parts[0];
			String mid = parts[1];

			grouped.computeIfAbsent(date, d -> new LinkedHashMap<>())
					.computeIfAbsent(mid, m -> new ArrayList<>())
					.add(key);
		}

		// date を昇順
		List<String> dates = new ArrayList<>(grouped.keySet());
		Collections.sort(dates);

		List<String> ordered = new ArrayList<>();

		for (String date : dates) {
			LinkedHashMap<String, List<String>> midMap = grouped.get(date);
			if (midMap == null) {
				continue;
			}

			// midは「出現順」を維持するため、midMap.entrySet()の順のまま
			for (Map.Entry<String, List<String>> midEntry : midMap.entrySet()) {
				List<String> seqKeys = midEntry.getValue();
				if (seqKeys == null) {
					continue;
				}

				// seq=... を文字列順（key全体で自然順でOK。少なくとも seq 部分はこの順で揃う）
				seqKeys.sort(Comparator.naturalOrder());
				ordered.addAll(seqKeys);
			}
		}

		return ordered;
	}

	// =========================================================
	// 1ファイル処理: ローカルへ保存 -> ヘッダのみ判定 -> readOriginでパース -> DataEntity.file に S3 key
	//
	// ローカル保存先は GetOriginInfo.resolveLocalPath(s3Key) と同じ規則にする。
	// PutOriginBackUpInfo（バックアップ格納）が resolveActualLocalPath() 経由で
	// GetOriginInfo.resolveLocalPath(s3Key) を見に行くため、ここで別の場所
	// （例: config.getOutputCsvFolder() 直下にbasenameのみ）へ保存すると、
	// バックアップ格納時に NoSuchFileException で失敗する。
	// =========================================================
	private ReadOneResult readOne(String bucket, String s3Key) {
		try {
			Path local = GetOriginInfo.resolveLocalPath(s3Key);
			if (local.getParent() != null) {
				Files.createDirectories(local.getParent());
			}

			// S3Operatorに既にある
			s3Operator.downloadToFile(bucket, s3Key, local);

			byte[] bytes = Files.readAllBytes(local);

			// ヘッダのみは読み込みクラスに渡さない（空のリスト = 削除対象）
			if (CsvContentUtil.isHeaderOnly(bytes)) {
				return ReadOneResult.ok(s3Key, local.toString(), new ArrayList<>());
			}

			try (InputStream is = new ByteArrayInputStream(bytes)) {
				ReadFileOutputDTO dto = readOrigin.getFileBodyFromStream(is, s3Key);

				if (dto == null) {
					return ReadOneResult.broken(s3Key, local.toString(), null);
				}

				if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
					return ReadOneResult.broken(s3Key, local.toString(), dto.getThrowAble());
				}

				List<DataEntity> list = dto.getDataList();

				// データ行はあるのに正しい行が 1 件も無い（全行が 104 列未満など。ヘッダのみは上で判定済み）
				if (list == null || list.isEmpty()) {
					return ReadOneResult.broken(s3Key, local.toString(), null);
				}

				// 追跡用にS3 keyを入れる
				if (list != null) {
					for (DataEntity e : list) {
						try {
							e.setFile(s3Key);
						} catch (Exception ignore) {
						}
					}
				}

				return ReadOneResult.ok(s3Key, local.toString(), list);
			}

		} catch (Exception e) {
			return ReadOneResult.fail(s3Key, null, e);
		}
	}

	/**
	 * 1ファイルの読み込み結果
	 */
	private static class ReadOneResult {

		/** 読み込み成功か */
		final boolean ok;

		/** S3キー */
		final String s3Key;

		/** 読み込んだデータ（ヘッダのみなら空のリスト） */
		final List<DataEntity> entities;

		/** 失敗時の例外 */
		final Throwable thrown;

		/** 中身が原因の失敗か（true なら退避する） */
		final boolean broken;

		private ReadOneResult(boolean ok, String s3Key, String localPath, List<DataEntity> entities,
				Throwable thrown, boolean broken) {
			this.ok = ok;
			this.s3Key = s3Key;
			this.entities = entities;
			this.thrown = thrown;
			this.broken = broken;
		}

		static ReadOneResult ok(String s3Key, String localPath, List<DataEntity> entities) {
			return new ReadOneResult(true, s3Key, localPath, entities, null, false);
		}

		/** ダウンロード失敗など（一時的なエラー。次回もう一度読む） */
		static ReadOneResult fail(String s3Key, String localPath, Throwable e) {
			return new ReadOneResult(false, s3Key, localPath, null, e, false);
		}

		/** 中身が原因で読めない（退避する） */
		static ReadOneResult broken(String s3Key, String localPath, Throwable e) {
			return new ReadOneResult(false, s3Key, localPath, null, e, true);
		}
	}
}
