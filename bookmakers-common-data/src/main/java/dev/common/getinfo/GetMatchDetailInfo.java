package dev.common.getinfo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadMatchDetail;
import dev.common.readfile.dto.MatchDetailDataDTO;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.s3.S3Operator;
import dev.common.util.CsvContentUtil;

/**
 * 試合詳細データ取得管理クラス
 *
 * <p>試合詳細スクレイピング（B016: MatchDetailData_aws_S3.py）が aws-s3-detail-data-csv に出力した CSV を、
 * 1 試合（= 1 フォルダ）ごとにまとめて読む。</p>
 *
 * <pre>
 * &lt;試合日&gt;/&lt;ホーム&gt;_vs_&lt;アウェー&gt;_&lt;試合ID&gt;/
 *     match_*.csv / momentum_*.csv / momentumIncident_*.csv / summary_*.csv / goal_*.csv
 *     formation_*.csv / lineup_*.csv / missing_*.csv / memberStat_*.csv
 *     memberStat_&lt;タブ&gt;_*.csv / memberStatHeader_*.csv   … memberStat と重複するので読まない
 *     manifest_*.json                                       … スクレイパーが最後に置く（これがあれば全ファイルそろっている）
 * バケット直下の b016_game_detail_path.json（スクレイパーの入力）と _done/（取得済みマーカー）は対象外。
 * </pre>
 *
 * <ul>
 *   <li>manifest が無いフォルダはアップロード途中とみなして読まない（次回読む）。</li>
 *   <li>ファイルは並行に読む（スレッド数は最大 8。{@link GetTeamMemberInfo} と同じ）。</li>
 *   <li>ヘッダのみのファイルは空のリストとして扱う（例: ゴールが無い試合の goal）。</li>
 *   <li>フォルダ内に 1 つでも読めないファイル（ダウンロード失敗・タイムアウト・読み込みエラー）があれば、
 *       そのフォルダ（試合）は結果に入れない。中途半端な試合を登録しないため。S3 のファイルはそのまま残し、次回もう一度読む。</li>
 * </ul>
 * <p>戻り値: key = フォルダのキー（例: 2026-10-25/ナポリ_vs_ローマ_SzXZwanC。試合日→フォルダ名の昇順）、
 * value = その試合のデータ。対象が無ければ空の Map、S3 の一覧取得・スレッドの失敗時は null。</p>
 * @author shiraishitoshio
 *
 */
@Component
public class GetMatchDetailInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetMatchDetailInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetMatchDetailInfo.class.getName();

	/** LoggerFactory */
	private static final Logger log = LoggerFactory.getLogger(GetMatchDetailInfo.class);

	/** 対象キー: &lt;試合日&gt;/&lt;フォルダ名（末尾 _試合ID）&gt;/&lt;ファイル名&gt; */
	private static final Pattern DETAIL_KEY = Pattern.compile(
			"^(\\d{4}-\\d{2}-\\d{2})/([^/]+_([A-Za-z0-9]+))/([^/]+)$");

	/** manifest のファイル名 */
	private static final Pattern MANIFEST_FILE = Pattern.compile("^manifest_.*\\.json$");

	/** JSON 読み込み */
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/** S3オペレーター */
	@Autowired
	private S3Operator s3Operator;

	/** パス設定 */
	@Autowired
	private PathConfig config;

	/** ファイル読み込みクラス */
	@Autowired
	private ReadMatchDetail readMatchDetail;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド（バケット内の全試合）
	 * @return key = フォルダのキー、value = その試合のデータ
	 */
	public Map<String, MatchDetailDataDTO> getData() {
		return getData(null);
	}

	/**
	 * 取得メソッド（試合日を指定）
	 * @param matchDate 試合日（yyyy-MM-dd）。null・空なら全試合
	 * @return key = フォルダのキー、value = その試合のデータ
	 */
	public Map<String, MatchDetailDataDTO> getData(String matchDate) {
		final String METHOD_NAME = "getData";

		String bucket = config.getS3BucketsDetailData();
		String prefix = (matchDate == null || matchDate.isBlank()) ? "" : matchDate.trim() + "/";

		List<String> keys;
		try {
			keys = s3Operator.listKeys(bucket, prefix);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN, e, "S3 detailData list bucket=" + bucket);
			return null;
		}

		// 1) フォルダごとにまとめる（試合日→フォルダ名の昇順）
		Map<String, MatchDetailDataDTO> folders = new TreeMap<>();
		for (String key : keys) {
			Matcher m = DETAIL_KEY.matcher(key);
			if (!m.matches()) {
				continue;
			}
			String folderKey = m.group(1) + "/" + m.group(2);
			MatchDetailDataDTO d = folders.get(folderKey);
			if (d == null) {
				d = new MatchDetailDataDTO();
				d.setFolderKey(folderKey);
				d.setMatchDate(m.group(1));
				d.setMatchId(m.group(3));
				folders.put(folderKey, d);
			}
			d.getS3Keys().add(key);
			if (MANIFEST_FILE.matcher(m.group(4)).matches()) {
				d.setManifestKey(key);
			}
		}

		log.info("[B016] S3 bucket={} prefix={} keys.size={} folders={} folders(sample)={}",
				bucket, prefix, keys.size(), folders.size(),
				folders.keySet().stream().limit(5).collect(Collectors.toList()));

		// 2) manifest が無いフォルダ（アップロード途中）は読まない
		List<MatchDetailDataDTO> targets = new ArrayList<>();
		int incomplete = 0;
		for (MatchDetailDataDTO d : folders.values()) {
			if (d.getManifestKey() == null) {
				incomplete++;
				this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099I_LOG, "manifest が無いため次回読みます（アップロード途中）: folder=" + d.getFolderKey());
				continue;
			}
			targets.add(d);
		}

		Map<String, MatchDetailDataDTO> resultMap = new LinkedHashMap<>();
		if (targets.isEmpty()) {
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP, "データなし(S3)");
			return resultMap;
		}

		// 3) 読むファイル（CSV と manifest）を並行に読む
		List<String> taskKeys = new ArrayList<>();
		List<MatchDetailDataDTO> taskOwners = new ArrayList<>();
		for (MatchDetailDataDTO d : targets) {
			for (String key : d.getS3Keys()) {
				if (key.equals(d.getManifestKey()) || ReadMatchDetail.kindOf(key) != null) {
					taskKeys.add(key);
					taskOwners.add(d);
				}
			}
		}

		Set<String> failedFolders = new HashSet<>();
		int poolSize = Math.max(1, Math.min(8, taskKeys.size()));
		ExecutorService executor = Executors.newFixedThreadPool(poolSize);
		try {
			List<Callable<Object>> tasks = new ArrayList<>(taskKeys.size());
			for (int i = 0; i < taskKeys.size(); i++) {
				final String key = taskKeys.get(i);
				final boolean isManifest = key.equals(taskOwners.get(i).getManifestKey());
				tasks.add(() -> isManifest ? readManifest(bucket, key) : readCsv(bucket, key));
			}

			// invokeAll の結果は tasks と同じ順番（i 番目 = taskKeys の i 番目）
			List<Future<Object>> futures = executor.invokeAll(tasks, 10, TimeUnit.MINUTES);

			for (int i = 0; i < futures.size(); i++) {
				String s3Key = taskKeys.get(i);
				MatchDetailDataDTO owner = taskOwners.get(i);
				Future<Object> f = futures.get(i);

				if (f.isCancelled()) {
					failedFolders.add(owner.getFolderKey());
					this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00003E_EXECUTION_SKIP, null,
							"ReadMatchDetail timeout/cancel key=" + s3Key + "（この試合は次回もう一度読む）");
					continue;
				}

				Object res;
				try {
					res = f.get();
				} catch (ExecutionException e) {
					failedFolders.add(owner.getFolderKey());
					this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00003E_EXECUTION_SKIP, e,
							"ReadMatchDetail read failed key=" + s3Key + "（この試合は次回もう一度読む）");
					continue;
				}

				// manifest
				if (res instanceof JsonNode) {
					JsonNode node = (JsonNode) res;
					owner.setScrapedAtJst(node.path("scrapedAtJst").asText(""));
					for (JsonNode err : node.path("errors")) {
						owner.getScrapeErrors().add(err.asText());
					}
					continue;
				}

				ReadFileOutputDTO dto = (ReadFileOutputDTO) res;
				if (dto == null || BookMakersCommonConst.ERR_CD_ERR_FILE_READS.equals(dto.getResultCd())) {
					failedFolders.add(owner.getFolderKey());
					this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00003E_EXECUTION_SKIP, null,
							"ReadMatchDetail read error key=" + s3Key + " "
									+ (dto == null ? "no result" : dto.getErrMessage() + " " + dto.getThrowAble())
									+ "（この試合は登録しない）");
					continue;
				}

				// データ異常（一部の行の列数違い）: 読めた行は使う
				if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
					log.warn("[B016] 一部の行にデータ異常あり（読めた行は使います） key={} resultCd={} detail={}",
							s3Key, dto.getResultCd(), dto.getErrMessage());
				}
				owner.merge(dto.getMatchDetailData());
			}

		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00004E_THREAD_INTERRUPTION, ie, "S3 detailData");
			return null;

		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN, e, "S3 detailData");
			return null;

		} finally {
			executor.shutdown();
			try {
				if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
			} catch (InterruptedException ie) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}

		// 4) 1 つでも読めなかった試合は入れない
		for (MatchDetailDataDTO d : targets) {
			if (failedFolders.contains(d.getFolderKey())) {
				continue;
			}
			resultMap.put(d.getFolderKey(), d);
		}

		log.info("[B016] 読み込み結果: folders={} (読み込み={}, アップロード途中={}, 読み込み失敗={})",
				folders.size(), resultMap.size(), incomplete, failedFolders.size());
		return resultMap;
	}

	/**
	 * CSV を 1 つ読む（ヘッダのみなら中身が空の結果）
	 */
	private ReadFileOutputDTO readCsv(String bucket, String key) throws Exception {
		byte[] bytes;
		try (InputStream is = s3Operator.download(bucket, key)) {
			bytes = is.readAllBytes();
		}
		if (CsvContentUtil.isHeaderOnly(bytes)) {
			ReadFileOutputDTO empty = new ReadFileOutputDTO();
			empty.setResultCd(BookMakersCommonConst.NORMAL_CD);
			empty.setMatchDetailData(new MatchDetailDataDTO());
			return empty;
		}
		try (InputStream is = new ByteArrayInputStream(bytes)) {
			return readMatchDetail.getFileBodyFromStream(is, key);
		}
	}

	/**
	 * manifest（JSON）を読む
	 */
	private JsonNode readManifest(String bucket, String key) throws Exception {
		String text = s3Operator.downloadTextUtf8(bucket, key);
		if (text == null || text.isBlank()) {
			return OBJECT_MAPPER.createObjectNode();
		}
		return OBJECT_MAPPER.readTree(text);
	}
}
