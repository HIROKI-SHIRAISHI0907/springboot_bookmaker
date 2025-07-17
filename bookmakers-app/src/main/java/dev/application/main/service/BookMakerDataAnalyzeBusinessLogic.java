package dev.application.main.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.domain.repository.MatchNotificationJudgementEntity;
import dev.application.domain.repository.MatchNotificationJudgementRepository;
import dev.common.constant.UniairConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.SystemException;

/**
 * BMデータ分析業務ロジック
 * @author shiraishitoshio
 *
 */
@Transactional
@Component
public class BookMakerDataAnalyzeBusinessLogic {

	/** Logger */
	private static final Logger logger = LoggerFactory.getLogger(BookMakerDataRegisterBusinessLogic.class);

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BookMakerDataAnalyzeBusinessLogic.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BookMakerDataAnalyzeBusinessLogic.class.getSimpleName();

	/** ログ出力 */
	private static final String START_END_FILE = "/Users/shiraishitoshio/bookmaker/csv/seqList_time.txt";

	/** */
	@Autowired
	private MatchNotificationJudgementRepository matchNotificationJudgementRepository;


	/**
	 * 処理実行
	 * @param conditionResultDataSeqResult condition_result_dataテーブルID
	 * <p>
	 * 1. ファイル内のデータ取得</br>
	 * 2. DB登録処理</br>
	 * 3. ファイル削除処理</br>
	 * @return 0:正常終了, 4:警告終了, 9:異常終了
	 */
	public int execute(String conditionResultDataSeqResult) {
		final String METHOD = "execute";
		logger.info(" analyze businessLogic start : {} ", CLASS_NAME);

		Path p1 = Paths.get(START_END_FILE);

		if (Files.exists(p1)) {
			try {
				File file = new File(START_END_FILE);

				FileWriter filewriter = new FileWriter(file, true);
				filewriter.write("BookMakerDataAnalyzeBusinessLogic start time : "
						+ new Timestamp(System.currentTimeMillis()) + "\r\n");
				filewriter.close();
			} catch (IOException e) {
				System.out.println(e);
			}
		}

		BookDataSelectWrapper selectWrapper = new BookDataSelectWrapper();
		// レコード件数を取得する
		int cnt = -1;
		try {
			cnt = selectWrapper.executeCountSelect(UniairConst.BM_M001, null);
			//cnt = this.bookDataSelectWrapper.executeCountSelect(UniairConst.BM_M001, null);
		} catch (Exception e) {
			throw new SystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD,
					"",
					e.getCause());
		}

		// condition_result_data_seq_id,judgeが書かれていないレコードから更新を始める
		int seq = 1;
		try {
			seq = selectWrapper.executeMinSeqChkNoUpdateRecord();
			//seq = this.bookDataSelectWrapper.executeMinSeqChkNoUpdateRecord();
		} catch (Exception e) {
			throw new SystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD,
					"",
					e.getCause());
		}

		// すでに項目が埋まっているならreturn
		if (seq == -1) {
			return 0;
		}

		UpdateWrapper updateWrapper = new UpdateWrapper();
		String noticeResult = "";
		// 保存
		int updateResult = 0;
		int reUpdateResult = 0;
		while (true) {

			logger.info(" {} 件目のレコードです。", seq);

			List<BookDataEntity> selectList = null;
			try {
				// sortを使って並び替えてから取得する
				selectList = selectWrapper.executeSelect(seq, null, false);
				//selectList = this.bookDataSelectWrapper.executeSelect(seq, null, false);
			} catch (Exception e) {
				logger.error(" select analyze businesslogic err -> ", e);
				selectList = new ArrayList<BookDataEntity>();
			}

			// 空の場合,更新対象のレコードがないため処理を行わない
			if (!selectList.isEmpty()) {
				// 判定結果に情報を追加
				// 通知フラグ判定結果更新情報と同一対戦の中での得点非得点情報を組み合わせて更新情報を決定する
				// 「終了済」となったものがない場合→未通知,通知済となっているレコード全て「結果不明」
				// 通知済になっているものが「終了済」となったものからスコアが変わらなかった場合→通知済となっているレコード全て「メール通知失敗」
				// 通知済になっているものが「終了済」となったものからスコアが変わった場合→通知済となっているレコード全て「メール通知成功」
				// 未通知になっているもので「終了済」となったものからスコアが変わらなかった場合→未通知となっているレコード全て「メール非通知対象」
				// 未通知になっているもので「終了済」となったものからスコアが変わった場合→未通知となっているレコード全て「メール通知対象」

				// 同一レコード連番リスト
				List<String> sameSeqRecordList = new ArrayList<String>();

				// 時間がわからない場合はチェック済みに登録しておく
				for (BookDataEntity noTimesEntity : selectList) {
					if ("---".equals(noTimesEntity.getTime())) {
						sameSeqRecordList.add(noTimesEntity.getSeq());
					}
				}

				// 終了済レコードが存在するかをチェックする
				boolean finRecordExistsFlg = false;
				for (BookDataEntity finRecordEntity : selectList) {
					if (BookMakersCommonConst.FIN.equals(finRecordEntity.getTime())) {
						finRecordExistsFlg = true;
					}
				}

				// 取り消し振り分けデータ(初期化)
				List<Boolean> goalDelFlgListAll = new ArrayList<Boolean>();
				for (int cnts = 0; cnts < selectList.size(); cnts++) {
					goalDelFlgListAll.add(false);
				}
				// ホームアウェースコアデータ
				StringBuilder homeAwayScoreDataAll = new StringBuilder();
				// judgelist
				List<String> judgeList = new ArrayList<String>();

				int sumScore = 0;
				int entityRecordCounter = 0;
				for (BookDataEntity entity : selectList) {

					boolean goalDelFlg = false;

					// 時間がわからない場合は結果不明
					if ("---".equals(entity.getTime()) ||
							BookMakersCommonConst.HOUR_DEAD.equals(entity.getTime()) ||
							BookMakersCommonConst.SUPENDING_GAME.equals(entity.getTime()) ||
							BookMakersCommonConst.ABANDONED_MATCH.equals(entity.getTime()) ||
							BookMakersCommonConst.WAITING_UPDATE.equals(entity.getTime()) ||
							BookMakersCommonConst.WAITING_UPDATE_KANJI.equals(entity.getTime()) ||
							BookMakersCommonConst.POSTPONED.equals(entity.getTime())) {
						noticeResult = "'" + BookMakersCommonConst.RESULT_UNKNOWN + "'";
						// 未通知,通知済のレコード場合,そのレコードより後にスコアが変動するようなレコードが来ているかチェック
					} else if (BookMakersCommonConst.NO_NOTIFICATION.equals(entity.getNoticeFlg()) ||
							BookMakersCommonConst.FIN_NOTIFICATION.equals(entity.getNoticeFlg())) {
						// 同一レコードリストに追加
						sameSeqRecordList.add(entity.getSeq());

						StringBuilder subScoreInfo = new StringBuilder();
						subScoreInfo.append(entity.getGameTeamCategory());
						subScoreInfo.append(",");
						subScoreInfo.append(entity.getHomeTeamName());
						subScoreInfo.append(",");
						subScoreInfo.append(entity.getHomeScore());
						subScoreInfo.append(",");
						subScoreInfo.append(entity.getAwayTeamName());
						subScoreInfo.append(",");
						subScoreInfo.append(entity.getAwayScore());
						String subTargetRecordBefore = subScoreInfo.toString();

						sumScore = 0;
						if (!entity.getHomeScore().isBlank() || !entity.getAwayScore().isBlank()) {
							sumScore = Integer.parseInt(entity.getHomeScore()) +
									Integer.parseInt(entity.getAwayScore());
						}

						// 最後まで調べても同一スコアだった場合,そのレコードは未通知なら「メール非通知対象」,通知済なら「メール通知失敗」
						noticeResult = (BookMakersCommonConst.NO_NOTIFICATION.equals(entity.getNoticeFlg()))
								? "'" + BookMakersCommonConst.MAIL_ANONYMOUS_TARGET + "'"
								: "'" + BookMakersCommonConst.MAIL_TARGET_FAIL + "'";

						// ホームアウェースコアデータ
						StringBuilder homeAwayScoreData = new StringBuilder();
						String befConnectScore = "";
						for (BookDataEntity subEntity : selectList) {

							// ホームアウェースコアデータ
							if (homeAwayScoreData.toString().length() > 0) {
								homeAwayScoreData.append(",");
							}
							// 空データが含まれるのを防ぐ
							if (!subEntity.getHomeScore().isBlank() || !subEntity.getAwayScore().isBlank()) {
								homeAwayScoreData.append(subEntity.getHomeScore() + subEntity.getAwayScore());
							} else if (!"".equals(befConnectScore)) {
								homeAwayScoreData.append(befConnectScore);
							} else {
								homeAwayScoreData.append("00");
							}
							befConnectScore = subEntity.getHomeScore() + subEntity.getAwayScore();

							// 同一レコードリストに入っている連番は調査しない(subTargetRecordBeforeよりも前のレコードのため)
							if (sameSeqRecordList.contains(subEntity.getSeq())) {
								continue;
							}

							subScoreInfo = new StringBuilder();
							subScoreInfo.append(subEntity.getGameTeamCategory());
							subScoreInfo.append(",");
							subScoreInfo.append(subEntity.getHomeTeamName());
							subScoreInfo.append(",");
							subScoreInfo.append(subEntity.getHomeScore());
							subScoreInfo.append(",");
							subScoreInfo.append(subEntity.getAwayTeamName());
							subScoreInfo.append(",");
							subScoreInfo.append(subEntity.getAwayScore());

							int subSumScore = 0;
							if (!subEntity.getHomeScore().isBlank() || !subEntity.getAwayScore().isBlank()) {
								subSumScore = Integer.parseInt(subEntity.getHomeScore()) +
										Integer.parseInt(subEntity.getAwayScore());
							}

							// 等しくないものがある場合,そのレコードは未通知なら「メール通知対象」,通知済なら「メール通知成功」
							if (!subTargetRecordBefore.equals(subScoreInfo.toString()) &&
									!noticeResult.contains(BookMakersCommonConst.MAIL_TARGET) &&
									!noticeResult.contains(BookMakersCommonConst.MAIL_TARGET_SUCCESS)) {
								noticeResult = (BookMakersCommonConst.NO_NOTIFICATION.equals(entity.getNoticeFlg()))
										? "'" + BookMakersCommonConst.MAIL_TARGET + "'"
										: "'" + BookMakersCommonConst.MAIL_TARGET_SUCCESS + "'";
								// レコードの予期せぬエラー(誤ってゴールしてしまい,ゴール取り消し前のデータが取得できてしまった場合)
								// ゴール取り消しデータを設定
								if (sumScore > subSumScore) {
									goalDelFlg = true;
									goalDelFlgListAll.set(entityRecordCounter, true);
								}
							}
						}
						homeAwayScoreDataAll = homeAwayScoreData;

					}

					// ホームスコアとアウェースコアの最大値を調べる
					if (homeAwayScoreDataAll.toString().length() > 0) {
						String[] sumScoreList = homeAwayScoreDataAll.toString().split(",");
						int maxSum = Integer.MIN_VALUE;
						String maxScoreAll = "";
						for (int i = 0; i < sumScoreList.length; i++) {
							int digitSum = (sumScoreList[i].charAt(0) - '0') +
									(sumScoreList[i].charAt(1) - '0'); // 各桁を数値に変換して足す
							if (digitSum > maxSum && !goalDelFlgListAll.get(i)) {
								maxSum = digitSum;
								maxScoreAll = sumScoreList[i];
							}
						}

						if ("".equals(maxScoreAll)) {
							continue;
						}

						String maxScore = String.valueOf(maxScoreAll.charAt(0));
						String minScore = String.valueOf(maxScoreAll.charAt(1));

						// この時点でnoticeResultは「結果不明」「メール通知対象」「メール通知成功」「メール非通知対象」「メール通知失敗」
						// のいずれかに決定しているが「終了済」レコードがない状態で決定している場合、決定したnoticeResultで言い切れない

						// 終了済レコードがないかつホームスコアとアウェースコアの合計値が最大のものは以下の値に変更する
						//1.「メール非通知対象」→「前終了済データ無し結果不明」
						//2.「メール通知成功」→「前メール通知情報結果不明」
						//3.「メール通知失敗」→「前メール通知情報結果不明」
						if (!finRecordExistsFlg && maxScore.equals(entity.getHomeScore())
								&& minScore.equals(entity.getAwayScore())) {
							String subNoticeResult = noticeResult.replace("'", "");
							switch (subNoticeResult) {
							case BookMakersCommonConst.MAIL_ANONYMOUS_TARGET:
								noticeResult = "'" + BookMakersCommonConst.MAIL_FIN_NO_DATA_TO_RESULT_UNKNOWN + "'";
								break;
							case BookMakersCommonConst.MAIL_TARGET_SUCCESS:
							case BookMakersCommonConst.MAIL_TARGET_FAIL:
								noticeResult = "'" + BookMakersCommonConst.MAIL_TARGET_TO_RESULT_UNKNOWN + "'";
								break;
							}
						}
					}

					// goalDelFlgがtrueならゴール取り消し
					if (goalDelFlg) {
						noticeResult = "'" + BookMakersCommonConst.GOAL_DELETE + "'";
					}

					MatchNotificationJudgementEntity matchNotificationJudgementEntity
						= new MatchNotificationJudgementEntity();

					// condition_result_data_seq_idが付与されていなければ,先頭にconditionResultDataSeqResultを付与
					StringBuilder noticeBuilder = new StringBuilder();
					if (entity.getConditionResultDataSeqId() == null
							|| "".equals(entity.getConditionResultDataSeqId()))
						matchNotificationJudgementEntity.setConditionResultDataSeqId(conditionResultDataSeqResult);

					// 先頭にjudgeを付与(noticeResultがなければ更新しない)
					if (noticeResult != null) {
						matchNotificationJudgementEntity.setJudge(noticeResult);
					}

					matchNotificationJudgementEntity.setSeq(entity.getSeq());
					matchNotificationJudgementEntity.setConditionResultDataSeqId(conditionResultDataSeqResult);

					// 更新日時も連結
					matchNotificationJudgementEntity.setUpdateTime("");

					// 決定した判定結果に更新
					int updateSubResult = this.matchNotificationJudgementRepository
							.updateJudgement(matchNotificationJudgementEntity);

					if (updateSubResult != -1) {
						updateResult += updateSubResult;
					}

					// 決定したjudgeを格納
					judgeList.add(noticeResult);

					entityRecordCounter++;
				}

				// 取り消しデータに関する更新処理
				String[] homeAwayDataListAll = homeAwayScoreDataAll.toString().split(",");

				// 同一データが取り消しデータ以外に出てくるかを確認
				// 取り消しデータが複数ある場合は,その取り消しデータの1つ前以降のみ抜き出して確認すること
				if (goalDelFlgListAll.contains(true)) {
					List<Boolean> originGoalDelFlgList = goalDelFlgListAll;
					int[] trueIndices = IntStream.range(0, originGoalDelFlgList.size())
							.filter(i -> originGoalDelFlgList.get(i)).toArray();
					List<Integer> originGoalDelFlgIndList = new ArrayList<Integer>();
					for (int ind : trueIndices) {
						originGoalDelFlgIndList.add(ind);
					}
					do {
						int delRecord = goalDelFlgListAll.indexOf(true);
						// 先頭が取得されると1つ前のレコードが存在しないためスキップ
						if (delRecord != 0) {
							String befData = homeAwayDataListAll[delRecord - 1];
							String[] homeAwayDataListSpAll = Arrays.copyOfRange(homeAwayDataListAll,
									delRecord - 1, homeAwayDataListAll.length);
							// 分割用取り消し振り分けデータ
							List<Boolean> goalDelFlgListSpAll = new ArrayList<Boolean>();
							for (int i = 0; i < goalDelFlgListAll.size(); i++) {
								if (i >= delRecord - 1) {
									goalDelFlgListSpAll.add(goalDelFlgListAll.get(i));
								}
							}

							boolean sameScoreFlg = true;
							for (int i = 0; i < goalDelFlgListSpAll.size(); i++) {
								if (!goalDelFlgListSpAll.get(i) && befData != "" &&
										!befData.equals(homeAwayDataListSpAll[i])) {
									sameScoreFlg = false;
									break;
								}
							}

							// 「メール通知対象」「メール通知成功」を判断した対象データがゴール取り消しデータのみだった場合
							// 1つ前のレコードをゴール取り消しによる通知非通知変更、ゴール取り消しによる成功失敗変更に変更
							if (sameScoreFlg) {
								// 該当のjudgeと通番を調べる
								Integer seqUpd = originGoalDelFlgIndList.get(0);
								String judge = judgeList.get(delRecord - 1);

								StringBuilder noticeBuilder = new StringBuilder();
								if (judge.contains(BookMakersCommonConst.MAIL_TARGET)) {
									noticeResult = "'"
											+ BookMakersCommonConst.DUE_TO_GOAL_DELETE_MAIL_TARGET_MAIL_ANONYMOUS_TARGET_ALTER
											+ "'";
								} else if (judge.contains(BookMakersCommonConst.MAIL_TARGET_SUCCESS)) {
									noticeResult = "'"
											+ BookMakersCommonConst.DUE_TO_GOAL_DELETE_MAIL_TARGET_SUCCESS_MAIL_TARGET_FAIL_ALTER
											+ "'";
								}

								// ゴール取り消しを誤って更新しないため,if文をつける
								if (!judgeList.get(seqUpd - 1).contains(BookMakersCommonConst.GOAL_DELETE)) {
									MatchNotificationJudgementEntity matchNotificationJudgementEntity
									= new MatchNotificationJudgementEntity();
									// 先頭にjudgeを付与
									matchNotificationJudgementEntity.setSeq(String.valueOf(seqUpd));
									matchNotificationJudgementEntity.setJudge(noticeResult);
									// 更新日時も連結
									matchNotificationJudgementEntity.setUpdateTime("");


									int updateSubResult = this.matchNotificationJudgementRepository
											.updateJudgement(matchNotificationJudgementEntity);

									if (updateSubResult != -1) {
										reUpdateResult += updateSubResult;
									}
								}
							}
						}

						// チェックしたindexにあるtrueを削除
						goalDelFlgListAll.remove(delRecord);
						String[] homeAwayDataListAllNew = new String[homeAwayDataListAll.length - 1];
						for (int i = 0, j = 0; i < homeAwayDataListAll.length; i++) {
							if (i == delRecord) {
								continue;
							}
							homeAwayDataListAllNew[j++] = homeAwayDataListAll[i];
						}
						homeAwayDataListAll = new String[homeAwayDataListAllNew.length];
						homeAwayDataListAll = homeAwayDataListAllNew;
						// 更新したseqを削除
						originGoalDelFlgIndList.remove(0);
					} while (goalDelFlgListAll.contains(true));
				}
			}

			// 現在登録されているレコードまで来たらbreak
			if (seq == cnt) {
				break;
			}

			// 次の通番のレコードに進む
			seq++;
		}

		logger.info("合計 {} 件の更新しました。そのうち再更新されたのは合計 {} 件でした。", updateResult, reUpdateResult);

		// 何らかのエラーでcondition_result_data_seq_id, judgeが更新できない場合
		//「conditionResultDataSeqResult」及び「予期せぬエラー」
		List<String> selectReqList = new ArrayList<String>();
		selectReqList.add("judge");
		selectReqList.add("condition_result_data_seq_id");
		String[] selList = new String[selectReqList.size()];
		for (int i = 0; i < selectReqList.size(); i++) {
			selList[i] = selectReqList.get(i);
		}

		List<List<String>> selectResultList = null;
		List<BookDataEntity> conditionList = new ArrayList<BookDataEntity>();
		SqlMainLogic select = new SqlMainLogic();
		String where = "condition_result_data_seq_id IS NULL or judge IS NULL ";
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M001, selList, where, null, null);
			//selectResultList = this.select.executeSelect(null, UniairConst.BM_M001, selList, where, null, null);
			if (!selectResultList.isEmpty()) {
				// Entityにマッピングする
				for (List<String> list : selectResultList) {
					BookDataEntity mapSelectDestination = mappingBookDataEntity(0, list);
					conditionList.add(mapSelectDestination);
				}
			}
		} catch (Exception e) {
			logger.error("get unexpected error select error -> ", e);
		}

		for (BookDataEntity entity : conditionList) {
			MatchNotificationJudgementEntity matchNotificationJudgementEntity
			= new MatchNotificationJudgementEntity();
			matchNotificationJudgementEntity.setSeq(entity.getSeq());
			matchNotificationJudgementEntity.setConditionResultDataSeqId(conditionResultDataSeqResult);
			matchNotificationJudgementEntity.setJudge(BookMakersCommonConst.UNEXPECTED_ERROR);
			// 決定した判定結果に更新
			this.matchNotificationJudgementRepository.updateJudgement(matchNotificationJudgementEntity);
		}

		if (Files.exists(p1)) {
			try {
				File file = new File(START_END_FILE);

				FileWriter filewriter = new FileWriter(file, true);
				filewriter.write("BookMakerDataAnalyzeBusinessLogic end time : "
						+ new Timestamp(System.currentTimeMillis()) + "\r\n");
				filewriter.close();
			} catch (IOException e) {
				System.out.println(e);
			}
		}

		//logger.info(" analyze businessLogic end : {} ", CLASS_NAME);

		return 0;
	}

	/**
	 * ListからDTOにマッピングをかける
	 * @param innerSeq 内部通番
	 * @param mapSource list構造
	 * @return BookDataSelectEntity DTO
	 */
	private BookDataEntity mappingBookDataEntity(int innerSeq, List<String> parts) {
		BookDataEntity mappingDto = new BookDataEntity();
		mappingDto.setJudge(parts.get(0));
		mappingDto.setConditionResultDataSeqId(parts.get(1));
		return mappingDto;
	}

}
