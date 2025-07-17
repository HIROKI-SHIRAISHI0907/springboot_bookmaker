package dev.application.analyze.bm_m002;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.ConditionResultDataRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.UniairConst;
import dev.common.copy.CopyFileAndGetCsvFile;
import dev.common.exception.BusinessException;
import dev.common.exception.SystemException;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadFile;
import dev.common.util.DateUtil;

/**
 * condition_result_dataテーブルに登録するロジック
 * @author shiraishitoshio
 *
 */
@Component
public class ConditionResultDataLogic {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ConditionResultDataLogic.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ConditionResultDataLogic.class.getSimpleName();

	/** ログ出力 */
	private static final String START_END_FILE = "/Users/shiraishitoshio/bookmaker/csv/seqList_time.txt";

	/** 続きの通番 */
	private int continueSeq = -1;

	/**
	 * ConditionResultDataRepository
	 */
	@Autowired
	private ConditionResultDataRepository conditionResultDataRepository;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/**
	 * 探索パス(外部設定値)
	 */
	@Value("${bmbusiness.aftercopypath:/Users/shiraishitoshio/bookmaker/conditiondata/}")
	private String findPath = "/Users/shiraishitoshio/bookmaker/conditiondata/";

	/**
	 * コピー先パス(外部設定値)
	 */
	@Value("${bmbusiness.aftercopypath:/Users/shiraishitoshio/bookmaker/conditiondata/copyfolder/}")
	private String copyPath = "/Users/shiraishitoshio/bookmaker/conditiondata/copyfolder/";

	/**
	 * 処理実行
	 * @param updateFlg 対象件数更新フラグ
	 * @return 条件分岐通番:正常終了, -1:異常終了
	 */
	public String execute() {
		final String METHOD = "execute";
		// ログ出力
		this.loggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD);

		// ハッシュ値に紐づくcondition_result_dataのBeanを呼び出す

		try {
			File file = new File(START_END_FILE);

			FileWriter filewriter = new FileWriter(file, true);
			filewriter.write(
					"ConditionResultDataLogic start time : " + new Timestamp(System.currentTimeMillis()) + "\r\n");
			filewriter.close();
		} catch (IOException e) {
			System.out.println(e);
		}

		// 条件分岐データとそのハッシュ値を取得
		String condtionData = null;
		String hashData = null;
		try {
			condtionData = getConditionData();
			hashData = extractHash(condtionData);
		} catch (Exception e) {
			throw new BusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD,
					"",
					e.getCause());
		}

		// 条件分岐データのハッシュ値を検索条件にレコード件数を取得する
		List<String> selectList = new ArrayList<String>();
		selectList.add("data_seq");
		String[] selList = new String[selectList.size()];
		for (int i = 0; i < selectList.size(); i++) {
			selList[i] = selectList.get(i);
		}

		List<ConditionResultDataEntity> conditionList = new ArrayList<ConditionResultDataEntity>();
		try {
			conditionList = this.conditionResultDataRepository.findByHash(hashData);
		} catch (Exception e) {
			logger.error("select error -> ", e);
			return "-1";
		}

		// 登録されているレコード件数を取得する
		int cnt = -1;
		try {
			cnt = selectWrapper.executeCountSelect(UniairConst.BM_M001, null);
		} catch (Exception e) {
			throw new SystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD,
					"",
					e.getCause());
		}

		// 今まで何件登録されているか
		List<String> selectConditionsList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M002);
		String[] sel2List = new String[selectConditionsList.size()];
		for (int i = 0; i < selectConditionsList.size(); i++) {
			sel2List[i] = selectConditionsList.get(i);
		}
		StringBuilder stringBuilder = new StringBuilder();
		for (int chk = 1; chk < sel2List.length - 2; chk++) {
			if (stringBuilder.toString().length() > 0) {
				stringBuilder.append(" + ");
			}
			stringBuilder.append(sel2List[chk]);
		}

		// 同一ハッシュデータに紐づく合計件数はいくつか
		String whereCnt = "";
		try {
			String sql = "SELECT (" + stringBuilder.toString() +
					") AS total FROM condition_result_data WHERE hash = '" + hashData + "'";
			whereCnt = select.executeSomethingSelect(sql);
		} catch (Exception e) {
			throw new SystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD,
					"",
					e.getCause());
		}

		// どこからスタートするか
		if (whereCnt != null && !"0.0".equals(whereCnt)) {
			whereCnt = whereCnt.replace(".0", "");
			continueSeq = Integer.parseInt(whereCnt) + 1;
		}

		// 途中で追加されたもののみを更新する場合
		// 合計値格納用リスト
		Integer[] sumList = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		if (updateFlg) {

			// 続き通番が設定されている場合,埋まっていない箇所から合計を算出する
			int seq = 1;
			if (continueSeq != -1) {

				List<ConditionResultDataEntity> conditionsList = new ArrayList<ConditionResultDataEntity>();
				try {
					List<List<String>> selectResultsList = select.executeSelect(null, UniairConst.BM_M002,
							sel2List, null, null, null);
					if (!selectResultsList.isEmpty()) {
						// Entityにマッピングする
						for (List<String> list : selectResultsList) {
							ConditionResultDataEntity mapSelectDestination = mappingConditionAllEntity(0, list);
							conditionsList.add(mapSelectDestination);
						}
					}
				} catch (Exception e) {
					logger.error("select error -> ", e);
					return "-1";
				}
				sumList[0] = Integer.parseInt(conditionsList.get(0).getMailTargetCount());
				sumList[1] = Integer.parseInt(conditionsList.get(0).getMailAnonymousTargetCount());
				sumList[2] = Integer.parseInt(conditionsList.get(0).getMailTargetSuccessCount());
				sumList[3] = Integer.parseInt(conditionsList.get(0).getMailTargetFailCount());
				sumList[4] = Integer.parseInt(conditionsList.get(0).getMailTargetFailToNoResultCount());
				sumList[5] = Integer.parseInt(conditionsList.get(0).getMailFinDataToNoResultCount());
				sumList[6] = Integer.parseInt(conditionsList.get(0).getGoalDelate());
				sumList[7] = Integer.parseInt(conditionsList.get(0).getAlterTargetMailAnonymous());
				sumList[8] = Integer.parseInt(conditionsList.get(0).getAlterTargetMailFail());
				sumList[9] = Integer.parseInt(conditionsList.get(0).getNoResultCount());
				sumList[10] = Integer.parseInt(conditionsList.get(0).getErrData());

				// 続きの通番を設定
				seq = continueSeq;
			}

			// 取得したいカラム(judge)を設定
			List<String> selectsList = new ArrayList<String>();
			selectsList.add("judge");
			selectsList.add("condition_result_data_seq_id");
			String[] selsList = new String[selectsList.size()];
			for (int i = 0; i < selectsList.size(); i++) {
				selsList[i] = selectsList.get(i);
			}

			// 全て集計し終わっている場合,処理を実施しない
			if (cnt > seq) {
				while (true) {
					// 任意の1件取得
					List<List<String>> selectResultssList = null;
					String wheres = "seq = " + seq;
					try {
						selectResultssList = select.executeSelect(null, UniairConst.BM_M001, selsList, wheres, null,
								"1");
					} catch (Exception e) {
						logger.error("select error -> ", e);
						return "-1";
					}

					if (!selectResultssList.isEmpty() && !selectResultssList.get(0).isEmpty()
							&& selectResultssList.get(0).get(0) != null) {
						// 以降の項目で分類
						// 「メール通知対象」「メール非通知対象」「メール通知成功」「メール通知失敗」「元メール通知情報結果不明」「前終了済データ無し結果不明」「結果不明」
						switch (selectResultssList.get(0).get(0)) {
						case BookMakersCommonConst.MAIL_TARGET:
							sumList[0] += 1;
							break;
						case BookMakersCommonConst.MAIL_ANONYMOUS_TARGET:
							sumList[1] += 1;
							break;
						case BookMakersCommonConst.MAIL_TARGET_SUCCESS:
							sumList[2] += 1;
							break;
						case BookMakersCommonConst.MAIL_TARGET_FAIL:
							sumList[3] += 1;
							break;
						case BookMakersCommonConst.MAIL_TARGET_TO_RESULT_UNKNOWN:
							sumList[4] += 1;
							break;
						case BookMakersCommonConst.MAIL_FIN_NO_DATA_TO_RESULT_UNKNOWN:
							sumList[5] += 1;
							break;
						case BookMakersCommonConst.GOAL_DELETE:
							sumList[6] += 1;
							break;
						case BookMakersCommonConst.DUE_TO_GOAL_DELETE_MAIL_TARGET_MAIL_ANONYMOUS_TARGET_ALTER:
							sumList[7] += 1;
							break;
						case BookMakersCommonConst.DUE_TO_GOAL_DELETE_MAIL_TARGET_SUCCESS_MAIL_TARGET_FAIL_ALTER:
							sumList[8] += 1;
							break;
						// 結果不明(時間が「---」,判定結果が未記入)
						default:
							sumList[9] += 1;
						}
						// 何も値が入っていない
					} else if (seq <= cnt) {
						sumList[10] += 1;
					}

					logger.info("sum chk seq counter -> No: {} ", seq);
					logger.info("sum counter -> {},{},{},{},{},{},{},{},{},{},{} ",
							sumList[0], sumList[1], sumList[2], sumList[3], sumList[4],
							sumList[5], sumList[6], sumList[7], sumList[8], sumList[9], sumList[10]);

					if (cnt == seq) {
						break;
					}

					seq++;

				}
			}
		}

		List<String> selectConditionList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M002);
		// 検索に見つかれば更新
		String conditionResultDataSeqResult = null;
		if (!conditionList.isEmpty()) {
			// その際のdata_seqを保持する
			conditionResultDataSeqResult = conditionList.get(0).getDataSeq();

			// 更新せずに戻す
			if ((!updateFlg && Integer.parseInt(whereCnt) > 0)
					|| (updateFlg && (Integer.parseInt(whereCnt) == cnt))) {
				return conditionResultDataSeqResult;
			}

			// 更新カラムのみ抜き出す
			List<String> selectChkConditionList = new ArrayList<String>();
			for (int col = 1; col <= 11; col++) {
				selectChkConditionList.add(selectConditionList.get(col));
			}
			StringBuilder sqlBuilder = new StringBuilder();
			int sumListInd = 0;
			for (String condition : selectChkConditionList) {
				sqlBuilder.append(condition + " = '");
				sqlBuilder.append(String.valueOf(sumList[sumListInd]));
				sqlBuilder.append("' ,");
				sumListInd++;
			}

			// 更新日時も連結
			sqlBuilder.append(" update_time = '" + DateUtil.getSysDate() + "'");
			// 決定した判定結果に更新
			where = "data_seq = " + String.valueOf(conditionList.get(0).getDataSeq()) + "";
			int updateResult = updateWrapper.updateExecute(UniairConst.BM_M002, where,
					sqlBuilder.toString());
			// 結果が異常である場合エラー
			if (updateResult == -1) {
				throw new SystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD,
						"",
						null);
			}
			// 見つからなければ新規登録
		} else {
			List<ConditionResultDataEntity> conditionResultDataInsertList = new ArrayList<ConditionResultDataEntity>();
			ConditionResultDataEntity conditionResultDataInsertEntity = new ConditionResultDataEntity();
			conditionResultDataInsertEntity.setMailTargetCount(String.valueOf(sumList[0]));
			conditionResultDataInsertEntity.setMailAnonymousTargetCount(String.valueOf(sumList[1]));
			conditionResultDataInsertEntity.setMailTargetSuccessCount(String.valueOf(sumList[2]));
			conditionResultDataInsertEntity.setMailTargetFailCount(String.valueOf(sumList[3]));
			conditionResultDataInsertEntity.setMailTargetFailToNoResultCount(String.valueOf(sumList[4]));
			conditionResultDataInsertEntity.setMailFinDataToNoResultCount(String.valueOf(sumList[5]));
			conditionResultDataInsertEntity.setGoalDelate(String.valueOf(sumList[6]));
			conditionResultDataInsertEntity.setAlterTargetMailAnonymous(String.valueOf(sumList[7]));
			conditionResultDataInsertEntity.setAlterTargetMailFail(String.valueOf(sumList[8]));
			conditionResultDataInsertEntity.setNoResultCount(String.valueOf(sumList[9]));
			conditionResultDataInsertEntity.setErrData(String.valueOf(sumList[10]));
			conditionResultDataInsertEntity.setConditionData(condtionData);
			conditionResultDataInsertEntity.setHashData(hashData);
			conditionResultDataInsertList.add(conditionResultDataInsertEntity);

			// DB登録処理
			RegisterWrapper register = new RegisterWrapper();
			int result = register.sceneCardMemberInsert(UniairConst.BM_M002,
					conditionResultDataInsertList, 1, conditionResultDataInsertList.size());
			if (result != 0) {
				// 登録エラーの場合
				throw new SystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD,
						"",
						null);
			}

			// レコード件数取得
			int conditionResultDataCountRecord = 0;
			try {
				conditionResultDataCountRecord = select.executeCountSelect(null, UniairConst.BM_M002, null);
			} catch (Exception e) {
				logger.error("select error -> ", e);
				return "-1";
			}
			conditionResultDataSeqResult = String.valueOf(conditionResultDataCountRecord);
		}

		try {
			File file = new File(START_END_FILE);

			FileWriter filewriter = new FileWriter(file, true);
			filewriter.write(
					"ConditionResultDataLogic end time : " + new Timestamp(System.currentTimeMillis()) + "\r\n");
			filewriter.close();
		} catch (IOException e) {
			System.out.println(e);
		}

		this.loggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD);

		return conditionResultDataSeqResult;
	}

	/**
	 * ListからDTOにマッピングをかける
	 * @param innerSeq 内部通番
	 * @param mapSource list構造
	 * @return BookDataSelectEntity DTO
	 */
	private ConditionResultDataEntity mappingConditionEntity(int innerSeq, List<String> parts) {
		ConditionResultDataEntity mappingDto = new ConditionResultDataEntity();
		mappingDto.setDataSeq(parts.get(0));
		return mappingDto;
	}

	/**
	 * ListからDTOにマッピングをかける
	 * @param innerSeq 内部通番
	 * @param mapSource list構造
	 * @return BookDataSelectEntity DTO
	 */
	private ConditionResultDataEntity mappingConditionAllEntity(int innerSeq, List<String> parts) {
		ConditionResultDataEntity mappingDto = new ConditionResultDataEntity();
		mappingDto.setDataSeq(parts.get(0));
		mappingDto.setMailTargetCount(parts.get(1));
		mappingDto.setMailAnonymousTargetCount(parts.get(2));
		mappingDto.setMailTargetSuccessCount(parts.get(3));
		mappingDto.setMailTargetFailCount(parts.get(4));
		mappingDto.setMailTargetFailToNoResultCount(parts.get(5));
		mappingDto.setMailFinDataToNoResultCount(parts.get(6));
		mappingDto.setGoalDelate(parts.get(7));
		mappingDto.setAlterTargetMailAnonymous(parts.get(8));
		mappingDto.setAlterTargetMailFail(parts.get(9));
		mappingDto.setNoResultCount(parts.get(10));
		mappingDto.setErrData(parts.get(11));
		return mappingDto;
	}

	/**
	 * 条件分岐データをブックから読み取る
	 * @return
	 */
	private String getConditionData() throws Exception {
		// ファイルパス取得
		CopyFileAndGetCsvFile copyFileAndGetCsvFile = new CopyFileAndGetCsvFile();
		List<String> bookList = copyFileAndGetCsvFile.execute(this.findPath, this.copyPath, "conditiondata.csv");

		// ファイル内のデータ取得
		ReadFile readFile = new ReadFile();
		String conditionData = null;
		for (String filePath : bookList) {
			conditionData = readFile.getConditionDataFileBody(filePath);
			if (conditionData != null) {
				return conditionData;
			}
		}
		return "dummy";
	}

	/**
	 * 条件分岐データからハッシュ値を導出する
	 * @param conditionData 条件分岐データ
	 * @return new String(Base64.getEncoder().encodeToString(cipherBytes));
	 * @throws NoSuchAlgorithmException
	 */
	private String extractHash(String conditionData) throws NoSuchAlgorithmException {
		// ハッシュアルゴリズム
		String hashAlgorithm = "SHA-256";

		byte[] cipherBytes = null;
		try {
			MessageDigest md = MessageDigest.getInstance(hashAlgorithm);
			cipherBytes = md.digest(conditionData.getBytes());
		} catch (NoSuchAlgorithmException e) {
			logger.error("hash algorithm error -> ", e);
			throw e;
		}
		return new String(Base64.getEncoder().encodeToString(cipherBytes));
	}

}
