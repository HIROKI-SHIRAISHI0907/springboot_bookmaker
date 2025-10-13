package dev.common.getstatinfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.DataEntity;
import dev.common.find.dto.FindBookInputDTO;
import dev.common.find.dto.FindBookOutputDTO;
import dev.common.findcsv.FindStat;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadOrigin;
import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * 起源データ取得管理クラス
 * @author shiraishitoshio
 *
 */
@Component
public class GetOriginInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetOriginInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetOriginInfo.class.getSimpleName();

	/** OUTPUT */
	private static Pattern OUTPUT_Y;

	/** Configクラス */
	@Autowired
	private PathConfig config;

	/**
	 * 統計データCsv読み取りクラス
	 */
	@Autowired
	private FindStat findStatCsv;

	/**
	 * ファイル読み込みクラス
	 */
	@Autowired
	private ReadOrigin readOrigin;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 */
	public Map<String, List<DataEntity>> getData() {
		final String METHOD_NAME = "getData";

		OUTPUT_Y = Pattern.compile(config.getOutputCsvFolder() + "output_(\\d+)\\.csv$");

		// 時間計測開始
		long startTime = System.nanoTime();

		// 設定
		FindBookInputDTO findBookInputDTO = setBookInputDTO();

		// 統計データCsv読み取りクラス
		FindBookOutputDTO findBookOutputDTO = this.findStatCsv.execute(findBookInputDTO);
		// エラーの場合,戻り値の例外を業務例外に集約してスロー
		if (!BookMakersCommonConst.NORMAL_CD.equals(findBookOutputDTO.getResultCd())) {
			this.manageLoggerComponent.createBusinessException(
					findBookOutputDTO.getExceptionProject(),
					findBookOutputDTO.getExceptionClass(),
					findBookOutputDTO.getExceptionMethod(),
					findBookOutputDTO.getErrMessage(),
					findBookOutputDTO.getThrowAble());
		}

		// 読み込んだパスからデータ取得
		List<String> fileStatList = findBookOutputDTO.getBookList();
		// 結果構造：Map<"JPN-J1", Map<"HOME", List<DataEntity>>>
		Map<String, List<DataEntity>> resultMap = new HashMap<>();
		if (fileStatList.size() <= 0) {
			String messageCd = "データなし";
			String fillChar = "GetStatInfo";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar);
			return resultMap;
		}
		// スレッドプールを作成（例：同時に最大4スレッド）
		ExecutorService executor = Executors.newFixedThreadPool(4);
		// タスク送信
		List<Future<ReadFileOutputDTO>> originList = new ArrayList<>();
		for (String file : fileStatList) {
			Future<ReadFileOutputDTO> future = executor.submit(() -> this.readOrigin.getFileBody(file));
			originList.add(future);
		}

		for (Future<ReadFileOutputDTO> future : originList) {
			try {
				ReadFileOutputDTO dto = future.get();
				List<DataEntity> entity = dto.getDataList();
				// null または 空チェック
				if (entity == null || entity.isEmpty()) {
					continue;
				}
				String file = entity.get(0).getFile();
				resultMap
						.computeIfAbsent(file, s -> new ArrayList<>())
						.addAll(entity);
			} catch (Exception e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e);
				this.manageLoggerComponent.createBusinessException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						"InterruptedException|ExecutionException: エラー",
						e);
			}
		}
		executor.shutdown();

		// 時間計測終了
		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000; // ミリ秒に変換

		System.out.println("時間: " + durationMs);

		// ファイルごとの List<DataEntity> を安定ソート（時刻→ゴール時間文字列→末尾タイブレーク）
		for (var e : resultMap.entrySet()) {
			List<DataEntity> lst = e.getValue();
			lst.sort(Comparator
					.comparing(DataEntity::getMatchId, Comparator.nullsLast(String::compareTo))
					.thenComparing(DataEntity::getTimeSortSeconds, Comparator.nullsLast(Integer::compare))
					.thenComparing(d -> safeString(d.getGoalTime())) // "90+2'"など文字比較の安定化
					.thenComparingInt(Object::hashCode) // 完全同値のタイブレーク
			);
		}
		// output_yを昇順に。
		return sortByCsvNumber(resultMap);
	}

	/**
	 * 読み取りinputDTOに設定する
	 * @return
	 */
	private FindBookInputDTO setBookInputDTO() {
		FindBookInputDTO findBookInputDTO = new FindBookInputDTO();
		findBookInputDTO.setDataPath(config.getOutputCsvFolder());
		findBookInputDTO.setCopyFlg(false);
		findBookInputDTO.setGetBookFlg(true);
		String[] containsList = new String[6];
		containsList[0] = "breakfile";
		containsList[1] = "all.csv";
		containsList[3] = "conditiondata/";
		containsList[4] = "python_analytics/";
		containsList[5] = "average_stats/";
		findBookInputDTO.setContainsList(containsList);
		findBookInputDTO.setPrefixFile(BookMakersCommonConst.OUTPUT_);
		findBookInputDTO.setSuffixFile(BookMakersCommonConst.CSV);
		return findBookInputDTO;
	}

	/**
	 * Map を y 昇順（小さい順）に整列し、順序保持の LinkedHashMap で返す
	 * @param <V>
	 * @param map
	 * @return
	 */
	private static <V> Map<String, V> sortByCsvNumber(Map<String, V> map) {
		return map.entrySet().stream()
				.sorted(
						Comparator
								.comparingInt((Map.Entry<String, V> e) -> extractY(e.getKey()))
								.thenComparing(Map.Entry::getKey) // y が同じ時の安定化
				)
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						Map.Entry::getValue,
						(a, b) -> a,
						LinkedHashMap::new));
	}

	/**
	 * パス "XXXX/XXXX/output_y.csv" の y を抜き出して数値で返す
	 * @param path
	 * @return
	 */
	private static int extractY(String path) {
		var normalized = path.replace('\\', '/'); // Windows対策
		var m = OUTPUT_Y.matcher(normalized);
		if (m.find())
			return Integer.parseInt(m.group(1));
		// マッチしないものは末尾に回す
		return Integer.MAX_VALUE;
	}

	/** 安定ソート */
	private static String safeString(String s) { return s == null ? "" : s; }

}
