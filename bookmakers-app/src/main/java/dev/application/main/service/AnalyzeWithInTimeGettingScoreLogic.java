package dev.application.main.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.application.analyze.bm_m003.TeamStatisticsDataSubLogic;
import dev.application.analyze.bm_m005.ZeroScoreDataSubLogic;
import dev.application.analyze.bm_m007_bm_m016.WithinTimeSubLogic;
import dev.application.analyze.bm_m017_bm_m018.WithinCounterSubLogic;
import dev.application.analyze.bm_m019_bm_m020.ClassifyScoreAISubLogic;
import dev.application.analyze.bm_m019_bm_m020.ClassifyScoreMakeCsvHelperLogic;
import dev.application.analyze.bm_m019_bm_m020.MakeClassifyScoreCsv;
import dev.application.analyze.bm_m019_bm_m020.MakeClassifyScoreDataCsv;
import dev.application.analyze.bm_m021.AverageFeatureSubLogic;
import dev.application.analyze.bm_m023.DecidePlayStyleAndThresHoldLogic;
import dev.application.analyze.bm_m024.CalcCorrelationSubLogic;
import dev.application.analyze.bm_m025.CalcCorrelationDetailSubLogic;
import dev.application.analyze.bm_m097.ExistsUpdCsvInfo;
import dev.application.analyze.bm_m099.ReadFileOperationChk;
import dev.application.analyze.bm_m099.ReadFileOperationChkOutputDTO;
import dev.application.analyze.common.entity.ThresHoldEntity;
import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.common.util.ExecuteMainUtil;
import dev.application.common.util.UniairColumnMapUtil;
import dev.application.db.BookDataSelectWrapper;
import dev.application.db.CsvRegisterImpl;
import dev.application.db.SqlMainLogic;
import dev.application.db.UpdateWrapper;
import dev.application.entity.TypeOfCountryLeagueDataEntity;
import dev.common.constant.UniairConst;
import dev.common.delete.DeleteFolderInFile;
import dev.common.exception.BusinessException;
import dev.common.find.dto.FindBookInputDTO;
import dev.common.find.dto.FindBookOutputDTO;
import dev.common.findcsv.FindThresHoldCsv;
import dev.common.readfile.ReadThresHoldFile;
import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * CSVを読み込み得点の閾値が各特徴量のどこなのかを集計するロジック
 * @author shiraishitoshio
 *
 */
public class AnalyzeWithInTimeGettingScoreLogic {

	/**
	 * CSV原本パス
	 */
	private static final String PATH = "/Users/shiraishitoshio/bookmaker/csv/";

	/**
	 * CSVコピー先パス
	 */
	private static final String COPY_PATH = "/Users/shiraishitoshio/bookmaker/csv/threshold/";

	/**
	 * DB項目,テーブル名Mapping
	 */
	public static final Map<String, String> TABLE_MAP;
	static {
		Map<String, String> ListMstDetailMap = new LinkedHashMap<>();
		ListMstDetailMap.put(UniairConst.BM_M006, "type_of_country_league_data");
		ListMstDetailMap.put(UniairConst.BM_M007, "within_data");
		ListMstDetailMap.put(UniairConst.BM_M008, "within_data_20minutes_home_scored");
		ListMstDetailMap.put(UniairConst.BM_M009, "within_data_20minutes_away_scored");
		ListMstDetailMap.put(UniairConst.BM_M010, "within_data_20minutes_same_scored");
		ListMstDetailMap.put(UniairConst.BM_M011, "within_data_45minutes_home_scored");
		ListMstDetailMap.put(UniairConst.BM_M012, "within_data_45minutes_away_scored");
		ListMstDetailMap.put(UniairConst.BM_M013, "within_data_20minutes_home_all_league");
		ListMstDetailMap.put(UniairConst.BM_M014, "within_data_20minutes_away_all_league");
		ListMstDetailMap.put(UniairConst.BM_M015, "within_data_45minutes_home_all_league");
		ListMstDetailMap.put(UniairConst.BM_M016, "within_data_45minutes_away_all_league");
		ListMstDetailMap.put(UniairConst.BM_M017, "within_data_scored_counter");
		ListMstDetailMap.put(UniairConst.BM_M018, "within_data_scored_counter_detail");
		ListMstDetailMap.put(UniairConst.BM_M019, "classify_result_data");
		ListMstDetailMap.put(UniairConst.BM_M020, "classify_result_data_detail");
		TABLE_MAP = Collections.unmodifiableMap(ListMstDetailMap);
	}

	/**
	 * 実行
	 * @throws Exception
	 */
	public void execute() throws Exception {

		DeleteFolderInFile deleteFolderInFile = new DeleteFolderInFile();
		deleteFolderInFile.delete(COPY_PATH);

		// 1. ブック探索クラスから特定のパスに存在する全ブックをリストで取得
		FindBookInputDTO findBookInputDTO = new FindBookInputDTO();
		findBookInputDTO.setDataPath(PATH);
		findBookInputDTO.setCopyFlg(false);
		findBookInputDTO.setGetBookFlg(true);
		String[] containsList = new String[6];
		containsList[0] = "breakfile";
		containsList[1] = "all.csv";
		containsList[2] = "csv/";
		containsList[3] = "conditiondata/";
		containsList[4] = "python_analytics/";
		containsList[5] = "average_stats/";
		findBookInputDTO.setContainsList(containsList);
		findBookInputDTO.setPrefixFile(BookMakersCommonConst.XLSX);
		findBookInputDTO.setSuffixFile(BookMakersCommonConst.CSV);
		FindThresHoldCsv findThresHoldCsv = new FindThresHoldCsv();
		FindBookOutputDTO findBookOutputDTO = findThresHoldCsv.execute(findBookInputDTO);
		// エラーの場合,戻り値の例外を業務例外に集約してスロー
		if (!BookMakersCommonConst.NORMAL_CD.equals(findBookOutputDTO.getResultCd())) {
			throw new BusinessException(
					findBookOutputDTO.getExceptionProject(),
					findBookOutputDTO.getExceptionClass(),
					findBookOutputDTO.getExceptionMethod(),
					findBookOutputDTO.getErrMessage(),
					findBookOutputDTO.getThrowAble());
		}

		ReadFileOperationChk readFileOperationChk = new ReadFileOperationChk();
		// 2. コピー先にコピーしたCSVリストを集計し,1ファイルずつ読み込む
		ReadThresHoldFile readThresHoldFile = new ReadThresHoldFile();
		int allcount = 1;
		int count = 1;
		List<Integer> searchList = new ArrayList<Integer>();
		searchList.add(1);
		searchList.add(1);
		for (String file : findBookOutputDTO.getBookList()) {
			System.out.println("all chk book: " + allcount + "/" + findBookOutputDTO.getBookList().size());
			System.out.println("chk book: " + count + "/" + findBookOutputDTO.getBookList().size());

			ReadFileOutputDTO readFileOutputDTO = readThresHoldFile.getFileBody(file);
			if (!BookMakersCommonConst.NORMAL_CD.equals(readFileOutputDTO.getResultCd())) {
				throw new BusinessException(
						readFileOutputDTO.getExceptionProject(),
						readFileOutputDTO.getExceptionClass(),
						readFileOutputDTO.getExceptionMethod(),
						readFileOutputDTO.getErrMessage(),
						readFileOutputDTO.getThrowAble());
			}

			List<ThresHoldEntity> entityList = readFileOutputDTO.getReadHoldDataList();
			System.out.println("このファイルを確認します。:" + file);
			System.out.println("国,リーグ: " + entityList.get(0).getDataCategory());

			List<List<String>> convertList = null;
			try {
				convertList = convertThresHoldInsertList(entityList);
			} catch (Exception e2) {
				System.out.println("変換でエラー。convertThresHoldInsertList:" + file + ", " + e2);
			}

			// すでに読み込んだファイルか
			ReadFileOperationChkOutputDTO readFileOperationChkOutputDTO = null;
			try {
				readFileOperationChkOutputDTO = readFileOperationChk.readFileChk(file, convertList);
			} catch (Exception e1) {
				System.out.println("ファイル読み込みチェックでエラー。:" + file + ", " + e1);
			}

			if (readFileOperationChkOutputDTO.isFileChkFlg() &&
					!readFileOperationChkOutputDTO.isFileTmpChkFlg()) {
				System.out.println("読み込み済みファイルもしくは過去に読み込んだものと同一ファイルのためスキップ。:" + file);
				allcount++;
				continue;
			} else if (!readFileOperationChkOutputDTO.isFileChkFlg() &&
					readFileOperationChkOutputDTO.isFileTmpChkFlg()) {
				System.out.println("データが更新されたファイルのため該当のマスタデータから取り消します。:" + file);
				// 取り消し
				UpdateMasterDataLogic cancelMasterDataLogic = new UpdateMasterDataLogic();
				String file_number = file.replace(PATH, "").replace(".csv", "");
				cancelMasterDataLogic.execute(file_number);
				allcount++;
			}

			// file_chk_tmpに存在したものか
			boolean fileChkTmpFlg = readFileOperationChkOutputDTO.isFileTmpChkFlg();

			// 国とリーグに分割
			String dataCategory = entityList.get(0).getDataCategory();
			String[] data_List = ExecuteMainUtil.splitLeagueInfo(dataCategory);

			// csv個数計算(file_chk_tmpに存在(すでにCSV化され中身のデータが追加データ分だけ書き換えられただけ)した場合はすでにカウント済なのでskip)
			if (!fileChkTmpFlg) {
				createTypeOfCountryLeagueDataVerCsv(data_List[0], data_List[1]);
			}

			String home = entityList.get(0).getHomeTeamName();
			String away = entityList.get(0).getAwayTeamName();
			// upd_csv_infoに登録
			List<List<String>> resultList = ExistsUpdCsvInfo.chk(data_List[0], data_List[1],
					UniairConst.BM_M025, home + "-" + away);
			if (resultList.isEmpty()) {
				try {
					ExistsUpdCsvInfo.insert(data_List[0], data_List[1], UniairConst.BM_M025,
							home + "-" + away);
				} catch (Exception e) {
					System.err.println("ExistsUpdCsvInfo err: tableId = " + UniairConst.BM_M025 + ", err: " + e);
				}
			}

			WithinTimeSubLogic withinTimeSubLogic = new WithinTimeSubLogic();
			try {
				searchList = withinTimeSubLogic.execute(entityList, searchList);
			} catch (Exception e) {
				System.err.println("withinTimeSubLogic err: " + e);
			}

			WithinCounterSubLogic withinCounterSubLogic = new WithinCounterSubLogic();
			try {
				withinCounterSubLogic.execute(entityList);
			} catch (Exception e) {
				System.err.println("withinCounterSubLogic err: " + e);
			}

			ClassifyScoreAISubLogic classifyScoreAISubLogic = new ClassifyScoreAISubLogic();
			try {
				classifyScoreAISubLogic.execute(entityList, file);
			} catch (Exception e) {
				System.err.println("ClassifyScoreSubLogic err: " + e);
			}

			ZeroScoreDataSubLogic teamStatisticsZeroDataMainLogic = new ZeroScoreDataSubLogic();
			try {
				teamStatisticsZeroDataMainLogic.execute(entityList, file);
			} catch (Exception e) {
				System.err.println("TeamStatisticsZeroDataMainLogic err: " + e);
			}

			TeamStatisticsDataSubLogic teamStatisticsDataSubLogic = new TeamStatisticsDataSubLogic();
			try {
				teamStatisticsDataSubLogic.execute(entityList, file);
			} catch (Exception e) {
				System.err.println("TeamStatisticsDataSubLogic err: " + e);
			}

			AverageFeatureSubLogic averageFeatureSubLogic = new AverageFeatureSubLogic();
			try {
				averageFeatureSubLogic.execute(entityList, file);
			} catch (Exception e) {
				System.err.println("AverageFeatureSubLogic err: " + e);
			}

			DecidePlayStyleAndThresHoldLogic decidePlayStyleAndThresHoldLogic = new DecidePlayStyleAndThresHoldLogic();
			try {
				decidePlayStyleAndThresHoldLogic.execute(entityList, file);
			} catch (Exception e) {
				System.err.println("DecidePlayStyleAndThresHoldLogic err: " + e);
			}

			CalcCorrelationSubLogic calcCorrelationSubLogic = new CalcCorrelationSubLogic();
			try {
				calcCorrelationSubLogic.execute(entityList, file);
			} catch (Exception e) {
				System.err.println("CalcCorrelationSubLogic err: " + e);
			}

			List<String> splitInfo = ExecuteMainUtil.getCountryLeagueByRegex(entityList.get(0).getDataCategory());

			ExecuteClusterLogic executeClusterLogic = new ExecuteClusterLogic();
			executeClusterLogic.execute(file, splitInfo.get(0), splitInfo.get(1),
					entityList.get(0).getHomeTeamName(), entityList.get(0).getAwayTeamName());

			allcount++;
			count++;
		}

		// ranking data
		CalcCorrelationDetailSubLogic calcCorrelationDetailSubLogic = new CalcCorrelationDetailSubLogic();
		calcCorrelationDetailSubLogic.execute();

		// type_of_country_league_data
		String[] selTypeOfCountryLeagueDataList = new String[4];
		selTypeOfCountryLeagueDataList[0] = "id";
		selTypeOfCountryLeagueDataList[1] = "country";
		selTypeOfCountryLeagueDataList[2] = "league";
		selTypeOfCountryLeagueDataList[3] = "csv_count";

		// within_data_Xminutesを一括で更新
		String[] selWithInDataXminutesList = new String[4];
		selWithInDataXminutesList[0] = "id";
		selWithInDataXminutesList[1] = "country";
		selWithInDataXminutesList[2] = "category";
		selWithInDataXminutesList[3] = "target";

		// within_data_Xminutesを一括で更新
		String[] selWithInDataXminutesAllLeagueList = new String[2];
		selWithInDataXminutesAllLeagueList[0] = "id";
		selWithInDataXminutesAllLeagueList[1] = "target";

		// upd_csv_flgにレコードが存在するか
		boolean updCsvFlg = ExistsUpdCsvInfo.exist();

		UpdateWrapper updateWrapper = new UpdateWrapper();
		SqlMainLogic select = new SqlMainLogic();
		for (int chk = 0; chk <= 8; chk++) {
			List<String> values = UniairColumnMapUtil.getWithInTableIdMap(chk);
			String tableId = values.get(0);

			BookDataSelectWrapper selectWrapper = new BookDataSelectWrapper();
			// レコード件数を取得する
			int cnt = -1;
			try {
				cnt = selectWrapper.executeCountSelect(UniairConst.BM_M006, null);
			} catch (Exception e) {
				return;
			}

			for (int seq = 1; seq <= cnt; seq++) {
				System.out.println("seq: " + seq + ", tableId: " + tableId);

				// 更新CSVテーブルに存在したものは更新対象
				if (updCsvFlg) {
					List<List<String>> selectsList = ExistsUpdCsvInfo.chk("", "",
							UniairConst.BM_M006, String.valueOf(seq));
					if (selectsList.isEmpty()) {
						continue;
					}
				}

				// type_of_country_league_dataからcountry,league,csv_countを読み込み
				List<List<String>> selectResultList = null;
				int csv_count = 0;
				try {
					if (chk <= 4) {
						String where = "id = '" + seq + "'";
						selectResultList = select.executeSelect(null, UniairConst.BM_M006,
								selTypeOfCountryLeagueDataList, where, null, null);
						csv_count = Integer.parseInt(selectResultList.get(0).get(3));
					} else {
						// csv集計数の合計を導出
						String sql = "SELECT SUM(csv_count) AS csv_count FROM type_of_country_league_data;";
						csv_count = Integer.parseInt(select.executeSomethingSelect(sql).replace(".0", ""));
					}
				} catch (Exception e) {
					System.err.println("within_data select err: tableId = " + tableId
							+ ", id = " + seq + ", " + e);
				}

				List<List<String>> selectResultSubList = null;
				try {
					// within_data_Xminutesから同一country,leagueを取得, それ以外は全データ取得
					if (chk <= 4) {
						String whereSub = "country = '" + selectResultList.get(0).get(1) + "' and "
								+ "category = '" + selectResultList.get(0).get(2) + "'";
						selectResultSubList = select.executeSelect(null, tableId,
								selWithInDataXminutesList, whereSub, null, null);
					} else {
						selectResultSubList = select.executeSelect(null, tableId,
								selWithInDataXminutesAllLeagueList, null, null, null);

					}
				} catch (Exception e) {
					System.err.println("within_data select err: tableId = " + tableId
							+ " seq = " + seq + ", " + e);
				}

				// 取得したIdにのみ更新をかける
				if (!selectResultSubList.isEmpty()) {
					for (List<String> selsList : selectResultSubList) {
						int target = 0;
						if (chk <= 4) {
							target = Integer.parseInt(selsList.get(3));
						} else {
							target = Integer.parseInt(selsList.get(1));
						}
						String upWhere = "id = '" + selsList.get(0) + "'";
						String ratio = String.format("%.1f", ((float) target / csv_count) * 100) + "%";
						StringBuilder sqlBuilder = new StringBuilder();
						sqlBuilder.append(" search = '" + csv_count + "' ,");
						sqlBuilder.append(" ratio = '" + ratio + "' ,");
						sqlBuilder.append(" update_time = '" + DateUtil.getSysDate() + "'");
						// 決定した判定結果に更新
						updateWrapper.updateExecute(tableId, upWhere,
								sqlBuilder.toString());
					}
				}
				// この1回のloopでall_leagueはすベて更新しおわっているためbreak
				if (chk >= 5) {
					break;
				}
			}
		}

		// within_data_20minutes_away_all_league.txtなど試合時間範囲,特徴量,閾値をキーに該当数を導出する
		MakeWithinCsvLogic makeWithinCsvLogic = new MakeWithinCsvLogic();
		makeWithinCsvLogic.makeLogic(updCsvFlg);

		// classify_XX.csvを作り国とカテゴリ単位で特定の時間帯(分類モード)に得点した件数を出力
		ClassifyScoreMakeCsvHelperLogic classifyScoreMakeCsvHelperLogic = new ClassifyScoreMakeCsvHelperLogic();
		classifyScoreMakeCsvHelperLogic.execute(updCsvFlg);

		// classify_XX.csvを読み込み,分類モード単位で国とカテゴリのデータ群をdataから抽出
		MakeClassifyScoreDataCsv makeClassifyScoreDataCsv = new MakeClassifyScoreDataCsv();
		makeClassifyScoreDataCsv.execute(updCsvFlg);

		// ClassifyScoreMakeCsvHelperLogicの全ての国とカテゴリの件数を表にしたものを出力
		MakeClassifyScoreCsv makeClassifyScoreCsv = new MakeClassifyScoreCsv();
		makeClassifyScoreCsv.execute();

		// upd_csv_infoをTruncate
		ExistsUpdCsvInfo.truncate();
	}

	/**
	 * BookDataInsertEntityをString型のリストに変換
	 * @param entity BookDataEntity型のリスト
	 * @return
	 * @throws Exception
	 */
	private List<List<String>> convertThresHoldInsertList(List<ThresHoldEntity> entity) throws Exception {
		List<List<String>> returnAllList = new ArrayList<List<String>>();
		for (int i = 0; i < entity.size(); i++) {
			List<String> returnList = new ArrayList<String>();
			returnList.add(entity.get(i).getConditionResultDataSeqId());
			returnList.add(entity.get(i).getDataCategory());
			// 試合時間がXX:XXの形式ではない場合修正
			String modifyTime = entity.get(i).getTimes();
			returnList.add(modifyTime);
			// ホーム順位が?.の形式ではない場合修正
			String modifyHomeRank = entity.get(i).getHomeRank();
			returnList.add(modifyHomeRank);
			returnList.add(entity.get(i).getHomeTeamName());
			// ホームスコアが?.の形式ではない場合修正
			String modifyHomeScore = entity.get(i).getHomeScore();
			returnList.add(modifyHomeScore);
			// アウェー順位が?.の形式ではない場合修正
			String modifyAwayRank = entity.get(i).getAwayRank();
			returnList.add(modifyAwayRank);
			returnList.add(entity.get(i).getAwayTeamName());
			// ホームスコアが?.の形式ではない場合修正
			String modifyAwayScore = entity.get(i).getAwayScore();
			returnList.add(modifyAwayScore);
			// ホーム期待値が.00000000の形式の場合修正
			String modifyHomeExp = entity.get(i).getHomeExp();
			returnList.add(modifyHomeExp);
			// アウェー期待値が.00000000の形式の場合修正
			String modifyAwayExp = entity.get(i).getAwayExp();
			returnList.add(modifyAwayExp);
			// ホーム支配率が%の形式ではない場合修正
			String modifyHomePossesion = entity.get(i).getHomeDonation();
			returnList.add(modifyHomePossesion);
			// アウェー支配率が%の形式ではない場合修正
			String modifyAwayPossesion = entity.get(i).getAwayDonation();
			returnList.add(modifyAwayPossesion);
			// ホームシュート数が?.?の形式の場合修正
			String modifyHomeShootAll = entity.get(i).getHomeShootAll();
			returnList.add(modifyHomeShootAll);
			// アウェーシュート数が?.?の形式の場合修正
			String modifyAwayShootAll = entity.get(i).getAwayShootAll();
			returnList.add(modifyAwayShootAll);
			// ホーム枠内シュート数が?.?の形式の場合修正
			String modifyHomeShootIn = entity.get(i).getHomeShootIn();
			returnList.add(modifyHomeShootIn);
			// アウェー枠内シュート数が?.?の形式の場合修正
			String modifyAwayShootIn = entity.get(i).getAwayShootIn();
			returnList.add(modifyAwayShootIn);
			// ホーム枠外シュート数が?.?の形式の場合修正
			String modifyHomeShootOut = entity.get(i).getHomeShootOut();
			returnList.add(modifyHomeShootOut);
			// アウェー枠外シュート数が?.?の形式の場合修正
			String modifyAwayShootOut = entity.get(i).getAwayShootOut();
			returnList.add(modifyAwayShootOut);
			// ホームブロックシュート数が?.?の形式の場合修正
			String modifyHomeBlock = entity.get(i).getHomeBlockShoot();
			returnList.add(modifyHomeBlock);
			// アウェーブロックシュート数が?.?の形式の場合修正
			String modifyAwayBlock = entity.get(i).getAwayBlockShoot();
			returnList.add(modifyAwayBlock);
			// ホームビックチャンス数が?.?の形式の場合修正
			String modifyHomeChance = entity.get(i).getHomeBigChance();
			returnList.add(modifyHomeChance);
			// アウェービックチャンス数が?.?の形式の場合修正
			String modifyAwayChance = entity.get(i).getAwayBigChance();
			returnList.add(modifyAwayChance);
			// ホームコーナーキック数が?.?の形式の場合修正
			String modifyHomeCorner = entity.get(i).getHomeCorner();
			returnList.add(modifyHomeCorner);
			// アウェーコーナーキック数が?.?の形式の場合修正
			String modifyAwayCorner = entity.get(i).getAwayCorner();
			returnList.add(modifyAwayCorner);
			// ホームボックスシュート数が?.?の形式の場合修正
			String modifyHomeBoxIn = entity.get(i).getHomeBoxShootIn();
			returnList.add(modifyHomeBoxIn);
			// アウェーボックスシュート数が?.?の形式の場合修正
			String modifyAwayBoxIn = entity.get(i).getAwayBoxShootIn();
			returnList.add(modifyAwayBoxIn);
			// ホームボックス外シュート数が?.?の形式の場合修正
			String modifyHomeBoxOut = entity.get(i).getHomeBoxShootOut();
			returnList.add(modifyHomeBoxOut);
			// アウェーボックス外シュート数が?.?の形式の場合修正
			String modifyAwayBoxOut = entity.get(i).getAwayBoxShootOut();
			returnList.add(modifyAwayBoxOut);
			// ホームゴールポスト数が?.?の形式の場合修正
			String modifyHomePost = entity.get(i).getHomeGoalPost();
			returnList.add(modifyHomePost);
			// アウェーゴールポスト数が?.?の形式の場合修正
			String modifyAwayPost = entity.get(i).getAwayGoalPost();
			returnList.add(modifyAwayPost);
			// ホームゴールヘッド数が?.?の形式の場合修正
			String modifyHomeGoalHead = entity.get(i).getHomeGoalHead();
			returnList.add(modifyHomeGoalHead);
			// アウェーゴールヘッド数が?.?の形式の場合修正
			String modifyAwayGoalHead = entity.get(i).getAwayGoalHead();
			returnList.add(modifyAwayGoalHead);
			// ホームゴールキーパーセーブ数が?.?の形式の場合修正
			String modifyHomeKeeper = entity.get(i).getHomeKeeperSave();
			returnList.add(modifyHomeKeeper);
			// アウェーゴールキーパーセーブ数が?.?の形式の場合修正
			String modifyAwayKeeper = entity.get(i).getAwayKeeperSave();
			returnList.add(modifyAwayKeeper);
			// ホームフリーキック数が?.?の形式の場合修正
			String modifyHomeFree = entity.get(i).getHomeFreeKick();
			returnList.add(modifyHomeFree);
			// アウェーフリーキック数が?.?の形式の場合修正
			String modifyAwayFree = entity.get(i).getAwayFreeKick();
			returnList.add(modifyAwayFree);
			// ホームオフサイド数が?.?の形式の場合修正
			String modifyHomeOffside = entity.get(i).getHomeOffside();
			returnList.add(modifyHomeOffside);
			// アウェーオフサイド数が?.?の形式の場合修正
			String modifyAwayOffside = entity.get(i).getAwayOffside();
			returnList.add(modifyAwayOffside);
			// ホームファール数が?.?の形式の場合修正
			String modifyHomeFoul = entity.get(i).getHomeFoul();
			returnList.add(modifyHomeFoul);
			// アウェーファール数が?.?の形式の場合修正
			String modifyAwayFoul = entity.get(i).getAwayFoul();
			returnList.add(modifyAwayFoul);
			// ホームイエローカード数が?.?の形式の場合修正
			String modifyHomeYellowCard = entity.get(i).getHomeYellowCard();
			returnList.add(modifyHomeYellowCard);
			// アウェーイエローカード数が?.?の形式の場合修正
			String modifyAwayYellowCard = entity.get(i).getAwayYellowCard();
			returnList.add(modifyAwayYellowCard);
			// ホームレッドカード数が?.?の形式の場合修正
			String modifyHomeRedCard = entity.get(i).getHomeRedCard();
			returnList.add(modifyHomeRedCard);
			// アウェーレッドカード数が?.?の形式の場合修正
			String modifyAwayRedCard = entity.get(i).getAwayRedCard();
			returnList.add(modifyAwayRedCard);
			// ホームスローインが?.?の形式の場合修正
			String modifyHomeSlowIn = entity.get(i).getHomeSlowIn();
			returnList.add(modifyHomeSlowIn);
			// アウェースローインが?.?の形式の場合修正
			String modifyAwaySlowIn = entity.get(i).getAwaySlowIn();
			returnList.add(modifyAwaySlowIn);
			// ホームボックスタッチが?.?の形式の場合修正
			String modifyHomeBoxTouch = entity.get(i).getHomeBoxTouch();
			returnList.add(modifyHomeBoxTouch);
			// アウェーボックスタッチが?.?の形式の場合修正
			String modifyAwayBoxTouch = entity.get(i).getAwayBoxTouch();
			returnList.add(modifyAwayBoxTouch);
			// ホームパス
			String modifyHomePass = entity.get(i).getHomePassCount();
			returnList.add(modifyHomePass);
			// アウェーパス
			String modifyAwayPass = entity.get(i).getAwayPassCount();
			returnList.add(modifyAwayPass);
			// ホームファイナルサードパス
			String modifyHomeFinalThirdPass = entity.get(i).getHomeFinalThirdPassCount();
			returnList.add(modifyHomeFinalThirdPass);
			// アウェーファイナルサードパス
			String modifyAwayFinalThirdPass = entity.get(i).getAwayFinalThirdPassCount();
			returnList.add(modifyAwayFinalThirdPass);
			// ホームクロス
			String modifyHomeCross = entity.get(i).getHomeCrossCount();
			returnList.add(modifyHomeCross);
			// アウェークロス
			String modifyAwayCross = entity.get(i).getAwayCrossCount();
			returnList.add(modifyAwayCross);
			// ホームタックル
			String modifyHomeTackle = entity.get(i).getHomeTackleCount();
			returnList.add(modifyHomeTackle);
			// アウェータックル
			String modifyAwayTackle = entity.get(i).getAwayTackleCount();
			returnList.add(modifyAwayTackle);
			// ホームクリアが?.?の形式の場合修正
			String modifyHomeClear = entity.get(i).getHomeClearCount();
			returnList.add(modifyHomeClear);
			// アウェークリアが?.?の形式の場合修正
			String modifyAwayClear = entity.get(i).getAwayClearCount();
			returnList.add(modifyAwayClear);
			// ホームインターセプトが?.?の形式の場合修正
			String modifyHomeIntercept = entity.get(i).getHomeInterceptCount();
			returnList.add(modifyHomeIntercept);
			// アウェーインターセプトが?.?の形式の場合修正
			String modifyAwayIntercept = entity.get(i).getAwayInterceptCount();
			returnList.add(modifyAwayIntercept);
			returnList.add(String.valueOf(entity.get(i).getRecordTime()));
			returnList.add(entity.get(i).getWeather());
			returnList.add(entity.get(i).getTemparature());
			returnList.add(entity.get(i).getHumid());
			returnList.add(entity.get(i).getJudgeMember());
			returnList.add(entity.get(i).getHomeManager());
			returnList.add(entity.get(i).getAwayManager());
			returnList.add(entity.get(i).getHomeFormation());
			returnList.add(entity.get(i).getAwayFormation());
			returnList.add(entity.get(i).getStudium());
			returnList.add(entity.get(i).getCapacity());
			returnList.add(entity.get(i).getAudience());
			returnList.add(entity.get(i).getHomeMaxGettingScorer());
			returnList.add(entity.get(i).getAwayMaxGettingScorer());
			returnList.add(entity.get(i).getHomeMaxGettingScorerGameSituation());
			returnList.add(entity.get(i).getAwayMaxGettingScorerGameSituation());
			returnList.add(entity.get(i).getHomeTeamHomeScore());
			returnList.add(entity.get(i).getHomeTeamHomeLost());
			returnList.add(entity.get(i).getAwayTeamHomeScore());
			returnList.add(entity.get(i).getAwayTeamHomeLost());
			returnList.add(entity.get(i).getHomeTeamAwayScore());
			returnList.add(entity.get(i).getHomeTeamAwayLost());
			returnList.add(entity.get(i).getAwayTeamAwayScore());
			returnList.add(entity.get(i).getAwayTeamAwayLost());
			returnList.add(entity.get(i).getNoticeFlg());
			returnList.add(entity.get(i).getGoalTime());
			returnList.add(entity.get(i).getGoalTeamMember());
			returnList.add(entity.get(i).getJudge());
			returnList.add(entity.get(i).getHomeTeamStyle());
			returnList.add(entity.get(i).getAwayTeamStyle());
			returnList.add(entity.get(i).getProbablity());
			returnList.add(entity.get(i).getPredictionScoreTime());
			returnAllList.add(returnList);
		}
		return returnAllList;
	}

	/**
	 * DBに登録する
	 * @param country 国
	 * @param league リーグ
	 * @throws Exception
	 */
	private void createTypeOfCountryLeagueDataVerCsv(String country, String league) throws Exception {
		List<String> selectList = new ArrayList<String>();
		selectList.add("id");
		selectList.add("csv_count");
		String[] selList = new String[selectList.size()];
		for (int i = 0; i < selectList.size(); i++) {
			selList[i] = selectList.get(i);
		}

		SqlMainLogic select = new SqlMainLogic();
		List<List<String>> selectResultList = null;
		String where = "country = '" + country + "' and league = '" + league + "'";
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M006, selList, where, null, "1");
		} catch (Exception e) {
			return;
		}

		if (!selectResultList.isEmpty()) {
			String new_where = "id = " + selectResultList.get(0).get(0);
			String new_csvdata = selectResultList.get(0).get(1);
			String set = "csv_count = '" + String.valueOf(Integer.parseInt(new_csvdata) + 1) + "' , "
					+ "update_time = '" + DateUtil.getSysDate() + "'";
			select.executeUpdate(null, UniairConst.BM_M006, new_where, set);

			// upd_csv_infoに登録
			List<List<String>> resultList = ExistsUpdCsvInfo.chk(country, league,
					UniairConst.BM_M006, selectResultList.get(0).get(0));
			if (resultList.isEmpty()) {
				try {
					ExistsUpdCsvInfo.insert(country, league,
							UniairConst.BM_M006, selectResultList.get(0).get(0));
				} catch (Exception e) {
					System.err.println("ExistsUpdCsvInfo err: tableId = BM_M006, err: " + e);
				}
			}
		} else {
			List<TypeOfCountryLeagueDataEntity> insertList = new ArrayList<TypeOfCountryLeagueDataEntity>();
			TypeOfCountryLeagueDataEntity typeOfCountryLeagueDataEntity = new TypeOfCountryLeagueDataEntity();
			typeOfCountryLeagueDataEntity.setCountry(country);
			typeOfCountryLeagueDataEntity.setLeague(league);
			typeOfCountryLeagueDataEntity.setDataCount("0");
			typeOfCountryLeagueDataEntity.setCsvCount("1");
			insertList.add(typeOfCountryLeagueDataEntity);
			CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
			csvRegisterImpl.executeInsert(UniairConst.BM_M006, insertList, 1, 1);
		}
	}

}
