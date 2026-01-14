package dev.common.getstatinfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
	    PATH = config.getTeamCsvFolder();

	    long startTime = System.nanoTime();

	    FindBookInputDTO findBookInputDTO = setBookInputDTO();
	    FindBookOutputDTO findBookOutputDTO = this.findStatCsv.execute(findBookInputDTO);

	    if (!BookMakersCommonConst.NORMAL_CD.equals(findBookOutputDTO.getResultCd())) {
	        this.manageLoggerComponent.createBusinessException(
	            findBookOutputDTO.getExceptionProject(),
	            findBookOutputDTO.getExceptionClass(),
	            findBookOutputDTO.getExceptionMethod(),
	            findBookOutputDTO.getErrMessage(),
	            findBookOutputDTO.getThrowAble());
	    }

	    List<String> fileStatList = findBookOutputDTO.getBookList();
	    Map<String, List<TeamMemberMasterEntity>> resultMap = new HashMap<>();

	    if (fileStatList.isEmpty()) {
	        this.manageLoggerComponent.debugInfoLog(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, "データなし", "GetMemberInfo");
	        return resultMap;
	    }

	    // ★上限を付ける（例：最大8）
	    int poolSize = Math.min(8, fileStatList.size());
	    ExecutorService executor = Executors.newFixedThreadPool(poolSize);

	    try {
	        // invokeAll にすると、まとめて投げてまとめて待てる（future.get ループより安全）
	        List<Callable<ReadFileOutputDTO>> tasks = new ArrayList<>();
	        for (String file : fileStatList) {
	            tasks.add(() -> this.readTeamMember.getFileBody(file));
	        }

	        // タイムアウトも付けられる（例：60秒）
	        List<Future<ReadFileOutputDTO>> futures = executor.invokeAll(tasks, 60, TimeUnit.SECONDS);

	        for (Future<ReadFileOutputDTO> f : futures) {
	            if (f.isCancelled()) {
	                // タイムアウト等でキャンセルされたタスク
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, "ReadTeamMember timeout/cancel", null);
	                continue;
	            }
	            ReadFileOutputDTO dto = f.get();
	            List<TeamMemberMasterEntity> entity = dto.getMemberList();
	            if (entity == null || entity.isEmpty()) continue;

	            String file = entity.get(0).getFile();
	            resultMap.computeIfAbsent(file, k -> new ArrayList<>()).addAll(entity);
	        }

	    } catch (Exception e) {
	        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e);
	        this.manageLoggerComponent.createBusinessException(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, "GetMemberInfo: read error", e);
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

	    long endTime = System.nanoTime();
	    long durationMs = (endTime - startTime) / 1_000_000;
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
