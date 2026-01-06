package dev.batch.bm_b001;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.builder.ProcessRunner;
import dev.batch.constant.BatchConstant;
import dev.batch.constant.PythonConstant;
import dev.batch.interf.BatchIF;
import dev.batch.repository.master.CountryLeagueSeasonMasterRepository;
import dev.common.config.PathConfig;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.getstatinfo.GetMemberInfo;
import dev.common.getstatinfo.GetSeasonInfo;
import dev.common.getstatinfo.GetTeamMasterInfo;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.DateUtil;

/**
 * 国リーグシーズンマスタ（終了日・関連CSV）を更新するバッチ（B001）。
 *
 * <p>
 * 本バッチは次の2系統の処理を行う。
 * </p>
 * <ol>
 *   <li>
 *     終了日が「---」の国・リーグ（終了日未確定）について、対象の国・リーグ一覧を
 *     {@code b001_country_league.json} として出力する。
 *   </li>
 *   <li>
*     終了日がシステム日付を超過したレコード（期限切れ）について、必要に応じて Python スクリプトを実行し
*     CSV を生成（または既存CSVを再利用）し、DB登録・更新を行う。
*   </li>
* </ol>
*
* <h3>再実行設計（重要）</h3>
* <p>
* Python 実行後に DB 登録・更新で失敗した場合でも、生成済み CSV が残っていれば
* 次回実行時には Python を再実行せず、CSV を再利用して DB 処理のみ再実行できることを想定する。
* </p>
*
* <h3>JSON の扱い</h3>
* <p>
* {@code b001_country_league.json} は Python 実行時の入力として利用し、処理完了後に削除する。
* </p>
*
* <h3>トランザクション</h3>
* <p>
* DB更新を伴うため、部分更新を避ける目的でトランザクション境界を本バッチに設定する。
* </p>
*/
@Service("B001")
@Transactional
public class UpdateTimesCountryLeagueMasterBatch implements BatchIF {

	 /** 実行ログに出力するプロジェクト識別子（クラスの配置パス）。 */
    private static final String PROJECT_NAME = UpdateTimesCountryLeagueMasterBatch.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    /** 実行ログに出力するクラス名。 */
    private static final String CLASS_NAME = UpdateTimesCountryLeagueMasterBatch.class.getSimpleName();

    /** 運用向けのエラーコード。 */
    private static final String ERROR_CODE = "BM_B001_ERROR";

    /** 国リーグシーズンマスタの参照・更新を行うリポジトリ。 */
    @Autowired
    private CountryLeagueSeasonMasterRepository countryLeagueSeasonMasterRepository;

    /** パスや外部実行設定（Python/S3等）を保持する設定クラス。 */
    @Autowired
    private PathConfig pathConfig;

    /** season_date 系CSVの読み取り（存在確認含む）を行う。 */
    @Autowired
    private GetSeasonInfo getSeasonInfo;

    /** teamData 系CSVの読み取り（存在確認含む）を行う。 */
    @Autowired
    private GetTeamMasterInfo getTeamMasterInfo;

    /** teamMemberData 系CSVの読み取り（存在確認含む）を行う。 */
    @Autowired
    private GetMemberInfo getMemberInfo;

    /** バッチ共通ログ出力を行う。 */
    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    /** JSON 生成に利用する ObjectMapper。 */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * バッチ処理を実行する。
     *
     * <p>
     * 処理の概要：
     * </p>
     * <ol>
     *   <li>システム日付を取得する。</li>
     *   <li>終了日が「---」の対象（国・リーグ）を抽出し、JSONとして出力する。</li>
     *   <li>
     *     終了日超過レコードがある場合、season_date.csv が未生成なら Python を実行して CSV を生成する。
     *     生成済みであれば Python 実行はスキップする（再実行設計）。
     *   </li>
     *   <li>
     *     開幕10日前の国・リーグのみを抽出し、対象がある場合に限り teamData/teamMemberData の Python を
     *     CSV未生成時のみ実行する（再実行設計）。
     *   </li>
     *   <li>生成済みCSV（または既存CSV）を用いて DB 登録・更新を行う（実装箇所は TODO）。</li>
     *   <li>処理完了後、入力用JSONを削除する。</li>
     * </ol>
     *
     * <p>
     * 例外は内部で捕捉し、異常終了コードを返却する。
     * </p>
     *
     * @return
     * <ul>
     *   <li>{@link BatchConstant#BATCH_SUCCESS}：正常終了</li>
     *   <li>{@link BatchConstant#BATCH_ERROR}：異常終了</li>
     * </ul>
     */
	@Override
	public int execute() {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// python実行準備
		final String pythonRoot = pathConfig.getPythonRoot();
		final String pythonBin = pathConfig.getPythonBin();

		// 将来S3アップロードするならここを使う（現状未使用なら消してOK）
		// final String region = pathConfig.getS3Region();
		// final String bucketOfTeamSeasonDateData = pathConfig.getS3BucketsTeamSeasonDateData();

		try {
			final String nowDate = DateUtil.getSysDate();

			// JSONパス
			final String jsonFolder = pathConfig.getB001JsonFolder();
			final String jsonPath = jsonFolder + "b001_country_league.json";
			final Path jsonFilePath = Paths.get(jsonPath);

			// フォルダ作成（mkdirsより安全）
			try {
				Files.createDirectories(Paths.get(jsonFolder));
			} catch (IOException e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e,
						"doesnt make folder (" + jsonFolder + ")");
				return BatchConstant.BATCH_ERROR;
			}

			// 「---」(終了日不明) のレコード
			List<CountryLeagueSeasonMasterEntity> findHyphen = this.countryLeagueSeasonMasterRepository.findHyphen();

			// 終了日がシステム日付を超えたレコード
			List<CountryLeagueSeasonMasterEntity> expiredDate = this.countryLeagueSeasonMasterRepository
					.findExpiredByEndDate(nowDate);

			if (findHyphen.isEmpty()) {
				// 「---」がない場合は、expired のみを処理
				if (!expiredDate.isEmpty()) {
					return clearExpiredEndSeasonDate(expiredDate, METHOD_NAME);
				}
				this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, "処理対象なし");
				return BatchConstant.BATCH_SUCCESS;
			}

			// 1) まず「---」対象を json 化（Python側が読む前提）
			Map<String, Set<String>> hyphenMap = buildCountryLeagueMap(findHyphen);
			try {
				makeJson(jsonPath, hyphenMap);
			} catch (Exception e) {
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
				return BatchConstant.BATCH_ERROR;
			}

			// 2) CSVが既にあるなら Python は回さず “DB更新だけ” に使う
			//    ※ fileSeasonDateCsvReads() は「読めたら存在」扱い
			boolean seasonDateCsvExists = !fileSeasonDateCsvReads().isEmpty();

			// 3) expiredDate がある場合のみ season_date を取り直す（ただしCSVが無ければ）
			boolean teamSeasonDateDataReady = seasonDateCsvExists;
			if (!expiredDate.isEmpty() && !seasonDateCsvExists) {
				int exit = ProcessRunner.run(
						pythonBin,
						Paths.get(pythonRoot),
						PythonConstant.TEAM_SEASON_DATE_DATA_PY,
						List.of());
				if (exit != 0) {
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
							PythonConstant.TEAM_SEASON_DATE_DATA_PY + " failed. exit=" + exit);
					return BatchConstant.BATCH_ERROR;
				}
				teamSeasonDateDataReady = true;
			}

			// 4) 開幕10日前の国リーグだけを収集して json 化（team/teamMember の対象）
			Map<String, Set<String>> within10DaysMap = buildWithin10DaysMap(findHyphen);
			boolean hasWithin10DaysTarget = !within10DaysMap.isEmpty();

			// within10Days の対象があるときだけ、jsonを上書きして team 系 python に渡す
			if (hasWithin10DaysTarget) {
				try {
					makeJson(jsonPath, within10DaysMap);
				} catch (Exception e) {
					this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
					return BatchConstant.BATCH_ERROR;
				}
			}

			// 5) teamData/teamMemberData も “CSVがあるならPythonスキップ” の思想で
			boolean teamDataCsvExists = !fileTeamDataCsvReads().isEmpty();
			boolean teamMemberDataCsvExists = !fileTeamMemberDataCsvReads().isEmpty();

			boolean teamDataReady = teamDataCsvExists;
			boolean teamMemberDataReady = teamMemberDataCsvExists;

			if (hasWithin10DaysTarget && !teamDataCsvExists) {
				int exit1 = ProcessRunner.run(
						pythonBin,
						Paths.get(pythonRoot),
						PythonConstant.TEAM_DATA_PY,
						List.of());
				if (exit1 != 0) {
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
							PythonConstant.TEAM_DATA_PY + " failed. exit=" + exit1);
					return BatchConstant.BATCH_ERROR;
				}
				teamDataReady = true;
			}

			if (hasWithin10DaysTarget && !teamMemberDataCsvExists) {
				int exit2 = ProcessRunner.run(
						pythonBin,
						Paths.get(pythonRoot),
						PythonConstant.TEAM_MEMBER_DATA_PY,
						List.of());
				if (exit2 != 0) {
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
							PythonConstant.TEAM_MEMBER_DATA_PY + " failed. exit=" + exit2);
					return BatchConstant.BATCH_ERROR;
				}
				teamMemberDataReady = true;
			}

			// 6) DB登録・更新（ここはあなたの実装を入れる）
			//    「Python成功→DB失敗→CSV残る→次回DBだけ再実行」を満たすなら
			//    ready フラグ条件でCSVから読み直して登録処理を走らせる形が良いです。
			if (teamSeasonDateDataReady) {
				// TODO: season_date.csv 読み込み → DB登録/更新
			}
			if (teamDataReady) {
				// TODO: teamData_XX.csv 読み込み → DB登録/更新
			}
			if (teamMemberDataReady) {
				// TODO: teamMemberData_XX.csv 読み込み → DB登録/更新
			}

			// 7) json は使い捨てなので “ファイルだけ” 削除
			try {
				Files.deleteIfExists(jsonFilePath);
			} catch (IOException e) {
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e,
						"failed to delete json: " + jsonPath);
				return BatchConstant.BATCH_ERROR;
			}

			return BatchConstant.BATCH_SUCCESS;

		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
			return BatchConstant.BATCH_ERROR;

		} finally {
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		}
	}

	/**
     * 期限切れ（終了日がシステム日付を超過）のレコードについて、
     * 終了日をクリアする更新処理を行う。
     *
     * <p>
     * 期待する更新件数は 1 件であり、更新件数が 1 でない場合は異常とする。
     * </p>
     *
     * @param expiredDate 終了日が期限切れとなった国・リーグのリスト
     * @param methodName ログ出力用のメソッド名
     * @return 正常時は {@link BatchConstant#BATCH_SUCCESS}、異常時は {@link BatchConstant#BATCH_ERROR}
     */
	private int clearExpiredEndSeasonDate(List<CountryLeagueSeasonMasterEntity> expiredDate, String methodName) {
		for (CountryLeagueSeasonMasterEntity entity : expiredDate) {
			String country = entity.getCountry();
			String league = entity.getLeague();
			int result = this.countryLeagueSeasonMasterRepository.clearEndSeasonDate(country, league);
			if (result != 1) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, methodName, ERROR_CODE, null,
						"clearEndSeasonDate result!=1 ( " + country + ", " + league + " )");
				return BatchConstant.BATCH_ERROR;
			}
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, methodName, null,
					"clearEndSeasonDate ok ( " + country + ", " + league + " )");
		}
		return BatchConstant.BATCH_SUCCESS;
	}

	/**
     * 国・リーグの組み合わせを {@code Map<国, Set<リーグ>>} 形式に変換する。
     *
     * <p>
     * JSON出力用の内部表現として利用する。リーグ順序を保持したい場合に備えて
     * {@link LinkedHashSet} を使用する。
     * </p>
     *
     * @param entities 変換元（国・リーグ情報を持つエンティティリスト）
     * @return 国をキー、リーグ集合を値とするマップ
     */
	private Map<String, Set<String>> buildCountryLeagueMap(List<CountryLeagueSeasonMasterEntity> entities) {
		Map<String, Set<String>> map = new HashMap<>();
		for (CountryLeagueSeasonMasterEntity entity : entities) {
			map.computeIfAbsent(entity.getCountry(), k -> new LinkedHashSet<>())
					.add(entity.getLeague());
		}
		return map;
	}

	 /**
     * 「開幕10日前に開幕戦が控える」国・リーグの組み合わせのみを抽出し、
     * {@code Map<国, Set<リーグ>>} 形式に変換する。
     *
     * <p>
     * teamData/teamMemberData の対象選定のために使用する。
     * </p>
     *
     * @param findHyphen 終了日が「---」の国・リーグ候補リスト
     * @return 開幕10日前に該当する国・リーグのマップ（該当なしの場合は空）
     */
	private Map<String, Set<String>> buildWithin10DaysMap(List<CountryLeagueSeasonMasterEntity> findHyphen) {
		Map<String, Set<String>> map = new HashMap<>();
		for (CountryLeagueSeasonMasterEntity entity : findHyphen) {
			String country = entity.getCountry();
			String league = entity.getLeague();

			List<CountryLeagueSeasonMasterEntity> withinDays = this.countryLeagueSeasonMasterRepository
					.findCountryLeagueStartingWithin10Days(country, league);

			for (CountryLeagueSeasonMasterEntity w : withinDays) {
				map.computeIfAbsent(w.getCountry(), k -> new LinkedHashSet<>())
						.add(w.getLeague());
			}
		}
		return map;
	}

	/**
     * 指定のパスへ {@code b001_country_league.json} を作成する。
     *
     * <p>
     * 出力形式は pretty print とし、Python 側が読みやすい形式で保存する。
     * </p>
     *
     * @param jsonPath 作成先JSONパス（ファイルパス）
     * @param countryLeagueMap 国をキー、リーグ集合を値とするマップ
     * @throws StreamWriteException JSON書き込みに失敗した場合
     * @throws DatabindException    変換に失敗した場合
     * @throws IOException          ファイルI/Oで失敗した場合
     */
	private void makeJson(String jsonPath, Map<String, Set<String>> countryLeagueMap)
			throws StreamWriteException, DatabindException, IOException {
		this.objectMapper.writerWithDefaultPrettyPrinter()
				.writeValue(new File(jsonPath), countryLeagueMap);
	}

	/**
     * season_date.csv 相当のCSVを読み込み、内容を取得する。
     *
     * <p>
     * 本メソッドは「CSVが存在する（かつ読み取り可能である）」ことの判定にも利用する。
     * DB登録処理で失敗した場合でも CSV が残っていれば、本メソッドがデータを返却し、
     * Python を再実行せずに再登録が可能となる。
     * </p>
     *
     * @return CSVから取得したデータ（読み取り不可の場合は空リスト）
     */
	private List<CountryLeagueSeasonMasterEntity> fileSeasonDateCsvReads() {
		final String METHOD_NAME = "fileSeasonDateCsvReads";
		try {
			return this.getSeasonInfo.getData();
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
			return new ArrayList<>();
		}
	}

	/**
     * teamData_XX.csv 相当のCSVを読み込み、内容を取得する。
     *
     * <p>
     * 「CSVが存在する（かつ読み取り可能である）」ことの判定にも利用する。
     * </p>
     *
     * @return CSVから取得したデータ（読み取り不可の場合は空リスト）
     */
	private List<List<CountryLeagueMasterEntity>> fileTeamDataCsvReads() {
		final String METHOD_NAME = "fileTeamDataCsvReads";
		try {
			return this.getTeamMasterInfo.getData();
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
			return new ArrayList<>();
		}
	}

	/**
     * teamMemberData_XX.csv 相当のCSVを読み込み、内容を取得する。
     *
     * <p>
     * 「CSVが存在する（かつ読み取り可能である）」ことの判定にも利用する。
     * </p>
     *
     * @return CSVから取得したデータ（読み取り不可の場合は空Map）
     */
	private Map<String, List<TeamMemberMasterEntity>> fileTeamMemberDataCsvReads() {
		final String METHOD_NAME = "fileTeamMemberDataCsvReads";
		try {
			return this.getMemberInfo.getData();
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
			return new HashMap<>();
		}
	}
}
