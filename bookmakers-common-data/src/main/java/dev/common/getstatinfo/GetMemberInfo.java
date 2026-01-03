package dev.common.getstatinfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.find.dto.FindBookInputDTO;
import dev.common.find.dto.FindBookOutputDTO;
import dev.common.findcsv.FindStat;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadTeamMember;
import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * 選手情報取得管理クラス
 * @author shiraishitoshio
 *
 */
@Component
public class GetMemberInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetMemberInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetMemberInfo.class.getSimpleName();

	/** Configクラス */
	@Autowired
	private PathConfig config;

	/**
	 * パス(/Users/shiraishitoshio/bookmaker/の予定)
	 */
	private String PATH;

	/**
	 * 統計データCsv読み取りクラス
	 */
	@Autowired
	private FindStat findStatCsv;

	/**
	 * ファイル読み込みクラス
	 */
	@Autowired
	private ReadTeamMember readTeamMember;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 */
	public Map<String, List<TeamMemberMasterEntity>> getData() {
		final String METHOD_NAME = "getData";
		// パス
		PATH = config.getTeamCsvFolder();

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
		// 結果構造：Map<"JPN-J1", Map<"HOME", List<BookDataEntity>>>
		Map<String, List<TeamMemberMasterEntity>> resultMap = new HashMap<>();
		if (fileStatList.size() <= 0) {
			String messageCd = "データなし";
			String fillChar = "GetMemberInfo";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar);
			return resultMap;
		}
		// スレッドプールを作成（例：同時に最大4スレッド）
		ExecutorService executor = Executors.newFixedThreadPool(fileStatList.size());
		// タスク送信
		List<Future<ReadFileOutputDTO>> futureList = new ArrayList<>();
		for (String file : fileStatList) {
			Future<ReadFileOutputDTO> future = executor.submit(() -> this.readTeamMember.getFileBody(file));
			futureList.add(future);
		}

		for (Future<ReadFileOutputDTO> future : futureList) {
			try {
				ReadFileOutputDTO dto = future.get();
				List<TeamMemberMasterEntity> entity = dto.getMemberList();
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
		//executor.shutdown();

		// 時間計測終了
		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000; // ミリ秒に変換

		System.out.println("時間: " + durationMs);
		return resultMap;
	}

	/**
	 * 読み取りinputDTOに設定する
	 * @return
	 */
	private FindBookInputDTO setBookInputDTO() {
		FindBookInputDTO findBookInputDTO = new FindBookInputDTO();
		findBookInputDTO.setDataPath(PATH);
		findBookInputDTO.setCopyFlg(false);
		findBookInputDTO.setGetBookFlg(true);
		String[] containsList = new String[6];
		containsList[0] = "breakfile";
		containsList[1] = "all.csv";
		containsList[3] = "conditiondata/";
		containsList[4] = "python_analytics/";
		containsList[5] = "average_stats/";
		findBookInputDTO.setContainsList(containsList);
		findBookInputDTO.setCsvNumber("0");
		findBookInputDTO.setPrefixFile(BookMakersCommonConst.TEAM_MEMBER_DATA_);
		findBookInputDTO.setSuffixFile(BookMakersCommonConst.CSV);
		return findBookInputDTO;
	}

}
