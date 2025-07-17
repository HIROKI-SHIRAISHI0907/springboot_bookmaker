package dev.application.main.service.sub;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m098.UpdFileDataInfoEntity;
import dev.application.analyze.bm_m099.ReadFileOperationChk;
import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.common.constant.UniairConst;
import dev.common.delete.DeleteBook;
import dev.common.delete.dto.DeleteBookInputDTO;
import dev.common.delete.dto.DeleteBookOutputDTO;
import dev.common.entity.BookDataEntity;
import dev.common.exception.BusinessException;
import dev.common.exception.SystemException;
import dev.common.find.dto.FindBookInputDTO;
import dev.common.find.dto.FindBookOutputDTO;
import dev.common.findcsv.FindSoccerDataCsv;
import dev.common.readfile.ReadThresHoldFile;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.readtext.ReadText;
import dev.common.readtext.dto.ReadTextInputDTO;
import dev.common.readtext.dto.ReadTextOutputDTO;

/**
 * すでに作成されているCSVデータについて,後から追加されたデータを反映する
 * @author shiraishitoshio
 *
 */
@Component
public class ReadSoccerCsvDataUpd {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ReadSoccerCsvDataUpd.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ReadSoccerCsvDataUpd.class.getSimpleName();

	/** CSVパス */
	private static final String CSV_PATH = "/Users/shiraishitoshio/bookmaker/csv/";

	/** ログ出力 */
	private static final String FILE = "/Users/shiraishitoshio/bookmaker/csv/seqList.txt";

	/** csv初期化Map */
	private Map<String, List<String>> csvDataMap = null;

	/** 通番読み込みリスト初期化Map */
	private Map<String, List<String>> seqListDataMap = null;

	/**
	 * 初期化コンストラクタ
	 * <p>
	 * 現在作成されているcsvの数字の部分(csv番号)と(国カテゴリ,ホームチーム,アウェーチーム)をMapで保持し,初期化する。
	 * </p>
	 */
	public ReadSoccerCsvDataUpd() {
		// mapに設定
		setCsvMapData();
	}

	/**
	 * 実行
	 * <p>
	 * 1.読み込んだ通番リストに紐づくデータカテゴリ,ホームチーム,アウェーチームの組み合わせを調べる
	 * 2.1の組み合わせの条件をDBから改めて取得し,2.と3.のリストサイズが等しい(全部のデータが存在する)ならリストはそのまま
	 * 3.リストサイズが等しくない(新しく追加されたデータがある)場合,新しく追加されたデータをallSeqGroupAllListに追加する
	 * 4.リストサイズが等しくない場合は,4.のデータを1.のMapから探し,csv番号を取得後,新しいデータを追加したデータに更新する
	 * </p>
	 * @param allSeqGroupAllList
	 * @throws Exception
	 */
	public ReadSoccerCsvDataOutputDTO execute() throws Exception {
		// 現在登録されている通番の最大値を取得
		BookDataSelectWrapper selectWrapper = new BookDataSelectWrapper();
		int allCnt = -1;
		try {
			allCnt = selectWrapper.executeCountSelect(UniairConst.BM_M001, null);
		} catch (Exception e) {
			throw new SystemException(
					PROJECT_NAME,
					CLASS_NAME,
					"",
					"",
					e.getCause());
		}
		// 最大値までの連番リストを作成
		Set<String> allSeqList = new HashSet<String>();
		for (int seq = 1; seq <= allCnt; seq++) {
			System.out.println("seq: " + seq);
			allSeqList.add(String.valueOf(seq));
		}

		// 通番リストを読み込む(この時点ではまだ追加データが反映されていない)
		List<List<String>> allSeqGroupAllList = readSeqList();

		// 連番リストから読み込み通番リストを削除する(すでに組み合わせを保持しているものは考慮しないため)
		for (List<String> innerList : allSeqGroupAllList) {
			for (String rem : innerList) {
				allSeqList.remove(rem.trim());
			}
		}

		// 残った連番リストをloopし,以下のケースで処理を分割する
		// 1.対象通番がすでにCSV化されているデータの一部(keyMapに存在し,CSVの中に存在しないデータ)
		// 2.対象通番がすでにCSV化されているデータの一部(keyMapに存在し,CSVの中にすでに存在するデータ(ハーフタイム,終了済など))
		// 3.作成済のCSV(keyMap)に存在しないデータ(完全新規データ)であり,読み込み通番リストの組み合わせにもない
		// 4.作成済のCSV(keyMap)に存在しないデータ(CSV作成対象外)であり,読み込み通番リストには組み合わせがある
		List<String> select1List = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M001);
		String[] sel1List = new String[select1List.size()];
		for (int i = 0; i < select1List.size(); i++) {
			sel1List[i] = select1List.get(i);
		}

		// 現在作成されているCSV番号を取得
		int maxCsvNumber = searchMaxCsvFileNumber(CSV_PATH);

		Map<String, List<String>> newMakeCsvMap = new HashMap<String, List<String>>();
		List<String> finUpdCsv = new ArrayList<String>();
		int lis = 0;
		for (String seq : allSeqList) {
			System.out.println("allSeqList list size: " + lis + " / " + allSeqList.size());
			// 通番リストからcsv番号を取得する
			List<List<String>> selectResultList = null;
			SqlMainLogic select = new SqlMainLogic();
			try {
				String searchWhere = "seq = '" + seq + "'";
				selectResultList = select.executeSelect(null, UniairConst.BM_M001, sel1List,
						searchWhere, null, "1");
			} catch (Exception e) {
				System.err.println("ReadSoccerCsvDataUpd select err searchData: " + e);
				return null;
			}

			List<String> chkList = new ArrayList<String>();
			String dataCategory = selectResultList.get(0).get(2);
			String homeTeamName = selectResultList.get(0).get(5);
			String awayTeamName = selectResultList.get(0).get(8);
			chkList.add(dataCategory);
			chkList.add(homeTeamName);
			chkList.add(awayTeamName);

			List<List<String>> selectResultNewList = null;
			try {
				String searchWhere = "data_category = '" + dataCategory + "' and "
						+ "home_team_name = '" + homeTeamName + "' and "
						+ "away_team_name = '" + awayTeamName + "'";
				selectResultNewList = select.executeSelect(null, UniairConst.BM_M001, sel1List,
						searchWhere, null, null);
			} catch (Exception e) {
				System.err.println("selectResultNewList select err searchData: " + e);
				return null;
			}

			// 通番リストを取得
			String seqKey = dataCategory + "-" + homeTeamName + "-" + awayTeamName;

			// csv_numberが0の場合はCSVが作成されていないデータ→3.or 4.,
			// それ以外の場合すでに作成済のCSVに紐づくデータ→1. or 2.
			String csv_number = chkCsvNumber(chkList);
			boolean csvExistFlg = true;
			if ("0".equals(csv_number)) {
				csvExistFlg = false;
			}

			// CSVがすでに作成されている場合,新規で追加されたデータも含め更新する
			if (csvExistFlg) {
				if (!finUpdCsv.contains(csv_number)) {
					// 現在作成されているCSVを削除して,新しいデータで再作成するMapに格納
					List<String> seqNewList = new ArrayList<String>();
					for (List<String> list : selectResultNewList) {
						seqNewList.add(list.get(0));
					}
					System.out.println("右記csv_numberを更新しました。seq: " + seq + ", csv_number: " + csv_number);
					// CSV再作成とファイル読み込み済マスタのハッシュ値更新
					remakeCsvFileAndRegisterFileChkTmp(csv_number, seqKey, seqNewList, selectResultNewList);
					finUpdCsv.add(csv_number);
					this.seqListDataMap.put(seqKey, seqNewList);
				} else {
					System.out.println("すでに更新済のcsv_numberです。seq: " + seq + ", csv_number: " + csv_number);
				}
				// 3.// 4.
			} else {
				// 通番リストに通番を追加
				List<String> seqNewList = new ArrayList<String>();
				for (List<String> list : selectResultNewList) {
					seqNewList.add(list.get(0).trim());
				}
				// 取得したデータで作成
				System.out.println("新規のデータです。seq: " + seq + ", csv_number: " + csv_number);
				// すでに通番リストに紐付けできているか
				if (this.seqListDataMap.containsKey(seqKey)) {
					System.out.println("CSVは作成されていませんが,通番リストには存在します。seq: " + seq);
				}
				this.seqListDataMap.put(seqKey, seqNewList);
				newMakeCsvMap.put(seqKey, seqNewList);
			}
			System.out.println("seqListDataMap list size: " + this.seqListDataMap.size());
			lis++;
		}

		// 読み込み通番リストファイルを更新する
		File file2 = new File(FILE);
		if (file2.exists()) {
			DeleteBookInputDTO deleteBookInputDTO = new DeleteBookInputDTO();
			deleteBookInputDTO.setDataPath(FILE);
			DeleteBook deleteBook = new DeleteBook();
			DeleteBookOutputDTO deleteBookOutputDTO = deleteBook.execute(deleteBookInputDTO);
			if (!BookMakersCommonConst.NORMAL_CD.equals(deleteBookOutputDTO.getResultCd())) {
				System.err.println("ファイル削除に失敗しました: file: " + FILE);
				return new ReadSoccerCsvDataOutputDTO();
			}
		}

		FileWriter filewriter = new FileWriter(FILE);
		try {
			filewriter.write("csv making seq size : " + this.seqListDataMap.size() + "\r\n");
			for (Map.Entry<String, List<String>> map : this.seqListDataMap.entrySet()) {
				filewriter.write(map.getValue() + "\r\n");
			}
			filewriter.close();
		} catch (IOException e) {
			System.err.println("ファイルの作成に失敗しました: file: " + FILE + ", err: " + e);
		}

		// 完全新規のCSVを作成するためCSV化されていない通番リストと現在作成されているCSV番号を取得し、
		// CSV番号+1以降で作成する。CSVが存在しない場合は読み込み通番リストに入っているもの全てを取得し再度作り直す
		List<List<String>> registerList = new ArrayList<List<String>>();
		Map<String, List<String>> chkMap = (maxCsvNumber == 0) ? this.seqListDataMap
				: newMakeCsvMap;
		for (Map.Entry<String, List<String>> map : chkMap.entrySet()) {
			registerList.add(map.getValue());
		}

		ReadSoccerCsvDataOutputDTO readSoccerCsvDataOutputDTO = new ReadSoccerCsvDataOutputDTO();
		readSoccerCsvDataOutputDTO.setAllSeqList(registerList);
		readSoccerCsvDataOutputDTO.setCsvNumber(maxCsvNumber + 1);
		return readSoccerCsvDataOutputDTO;
	}

	/**
	 * seqList.txtのリスト群を読み込み,Mapに設定する
	 * @return
	 */
	private List<List<String>> readSeqList() {
		ReadTextInputDTO readTextInputDTO = new ReadTextInputDTO();
		readTextInputDTO.setDataPath(FILE);
		List<String> tagList = new ArrayList<String>();
		tagList.add("[");
		tagList.add("]");
		readTextInputDTO.setConvertTagList(tagList);
		readTextInputDTO.setHeaderFlg(true);
		readTextInputDTO.setSplitTag(",");
		ReadText readText = new ReadText();
		ReadTextOutputDTO readTextOutputDTO = readText.execute(readTextInputDTO);
		List<List<String>> allSeqGroupAllList = readTextOutputDTO.getReadDataList();

		String[] sel1List = new String[3];
		sel1List[0] = "data_category";
		sel1List[1] = "home_team_name";
		sel1List[2] = "away_team_name";

		Map<String, List<String>> seqListDataMap = new HashMap<String, List<String>>();
		int lis = 1;
		for (List<String> list : allSeqGroupAllList) {
			System.out.println("readSeqList list size: " + lis + " / " + allSeqGroupAllList.size());
			String seq = list.get(0);

			List<List<String>> selectResultList = null;
			SqlMainLogic select = new SqlMainLogic();
			try {
				String searchWhere = "seq = '" + seq + "'";
				selectResultList = select.executeSelect(null, UniairConst.BM_M001, sel1List,
						searchWhere, null, "1");
			} catch (Exception e) {
				System.err.println("readSeqList select err searchData: " + e);
				throw new BusinessException("", "", "", "readSeqList select err searchData");
			}

			String dataCategory = selectResultList.get(0).get(0);
			String homeTeamName = selectResultList.get(0).get(1);
			String awayTeamName = selectResultList.get(0).get(2);

			seqListDataMap.put(dataCategory + "-" + homeTeamName + "-" + awayTeamName, list);
			lis++;
		}
		this.seqListDataMap = seqListDataMap;

		return allSeqGroupAllList;
	}

	/**
	 * mapにcsv番号とデータカテゴリ,ホーム,アウェーのチームの組み合わせを保存する
	 */
	private void setCsvMapData() {
		final String METHOD = "setCsvMapData";
		FindBookInputDTO findBookInputDTO = new FindBookInputDTO();
		findBookInputDTO.setDataPath(CSV_PATH);
		findBookInputDTO.setCopyFlg(false);
		FindSoccerDataCsv findSoccerDataCsv = new FindSoccerDataCsv();
		FindBookOutputDTO findBookOutputDTO = findSoccerDataCsv.execute(findBookInputDTO);
		if (!BookMakersCommonConst.NORMAL_CD.equals(findBookOutputDTO.getResultCd())) {
			throw new SystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD,
					"",
					findBookOutputDTO.getThrowAble());
		}

		List<String> bookList = findBookOutputDTO.getBookList();
		ReadThresHoldFile readThresHoldFile = new ReadThresHoldFile();
		Map<String, List<String>> csvDataMap = new HashMap<String, List<String>>();
		// 取得したcsvファイルパスをキーにデータカテゴリ,ホームチーム,アウェーチームのリストをcsv番号と一緒にMappingする
		for (String path : bookList) {
			ReadFileOutputDTO readFileOutputDTO = readThresHoldFile.getFileBody(path);
			BookDataEntity entities = readFileOutputDTO.getReadHoldDataList().get(0);

			List<String> mapList = new ArrayList<String>();
			mapList.add(entities.getGameTeamCategory());
			mapList.add(entities.getHomeTeamName());
			mapList.add(entities.getAwayTeamName());

			String csvNumber = path.replace(CSV_PATH, "").replace(".csv", "");
			csvDataMap.put(csvNumber, mapList);
		}
		this.csvDataMap = csvDataMap;
	}

	/**
	 * リストからCSV番号を取得する。存在しない場合は現在作成されているCSVの最大番号+1を返す
	 * @param chkList
	 */
	private String chkCsvNumber(List<String> chkList) {
		if (this.csvDataMap == null || (chkList == null || chkList.isEmpty())) {
			throw new BusinessException("", "", "", "csvDataMapが初期化されていないかchkListがnullです。");
		}

		Set<String> chkLists = new HashSet<>(chkList);
		for (Map.Entry<String, List<String>> mapEntry : csvDataMap.entrySet()) {
			Set<String> chkSetList = new HashSet<>(mapEntry.getValue());
			if (chkLists.equals(chkSetList)) {
				return mapEntry.getKey();
			}
		}
		// Mapに存在しない組み合わせがきた場合,0を返す
		return "0";
	}

	/**
	 * リストからCSV番号を取得する。存在しない場合は現在作成されているCSVの最大番号+1を返す
	 * @param key
	 */
	private List<String> chkSeqList(String key) {
		if (this.seqListDataMap == null || key == null) {
			throw new BusinessException("", "", "", "seqListDataMapが初期化されていないかkeyがnullです。");
		}

		if (this.seqListDataMap.containsKey(key)) {
			return this.seqListDataMap.get(key);
		}
		throw new BusinessException("", "", "", "seqListDataMapに含まれていないkeyです。key = " + key);
	}

	/**
	 * 最大の数字を持つCSV番号を探す
	 * @param dirs
	 */
	private int searchMaxCsvFileNumber(String dirs) {
		// 対象ディレクトリのパス
		File directory = new File(dirs);

		// ディレクトリ内のCSVファイルをリストアップ
		File[] files = directory.listFiles((dir, name) -> name.matches("\\d+\\.csv")); // 数字＋.csv のファイル

		if (files != null && files.length > 0) {
			int maxFileNumber = Integer.MIN_VALUE; // 最大ファイル番号の初期値（最小の整数値）

			// 正規表現を使ってファイル名から数字部分を抽出
			Pattern pattern = Pattern.compile("(\\d+)\\.csv");

			for (File file : files) {
				String fileName = file.getName();
				Matcher matcher = pattern.matcher(fileName);

				if (matcher.matches()) {
					// ファイル名から数字部分を取得し、整数に変換
					int fileNumber = Integer.parseInt(matcher.group(1));

					// 最大のファイル番号を更新
					if (fileNumber > maxFileNumber) {
						maxFileNumber = fileNumber;
					}
				}
			}

			if (maxFileNumber != Integer.MIN_VALUE) {
				System.out.println("最大のファイル番号は: " + maxFileNumber + ".csv");
				return maxFileNumber;
			} else {
				throw new BusinessException("", "", "", "CSVファイルが見つかりませんでした。");
			}
		} else {
			return 0;
		}
	}

	/**
	 * CSV再作成andデータ情報更新
	 * @param csv_number
	 * @param dataCategoryKey
	 * @param seqList
	 * @param dataList
	 * @throws Exception
	 */
	private void remakeCsvFileAndRegisterFileChkTmp(String csv_number, String dataCategoryKey,
			List<String> seqList, List<List<String>> dataList) throws Exception {
		// 初期化時に保持している通番リストと同じなら更新なし
		Set<String> seqHashList = new HashSet<String>();
		for (String item : seqList) {
            // 文字列をtrimしてからSetに追加
			seqHashList.add(item.trim());
        }
		List<String> list = chkSeqList(dataCategoryKey);
		Set<String> seqDataMapList = new HashSet<String>();
		for (String item : list) {
            // 文字列をtrimしてからSetに追加
			seqDataMapList.add(item.trim());
        }

		System.out.println("seqHashList size: " + seqHashList.size());
		System.out.println("seqDataMapList size: " + seqDataMapList.size());

		if (seqHashList.equals(seqDataMapList)) {
			System.out.println("作成済のCSVデータと同一情報を持っているため更新しません: dataCategoryKey = "
					+ dataCategoryKey);
			return;
		}

		String file = CSV_PATH + String.valueOf(csv_number.trim()) + ".csv";
		// ファイルを削除
		File file2 = new File(file);
		if (file2.exists()) {
			DeleteBookInputDTO deleteBookInputDTO = new DeleteBookInputDTO();
			deleteBookInputDTO.setDataPath(file);
			DeleteBook deleteBook = new DeleteBook();
			DeleteBookOutputDTO deleteBookOutputDTO = deleteBook.execute(deleteBookInputDTO);
			if (!BookMakersCommonConst.NORMAL_CD.equals(deleteBookOutputDTO.getResultCd())) {
				System.err.println("ファイル削除に失敗しました: file: " + file);
				return;
			}
		}

		// 取得したデータで再作成
		RDataOutputSubLogic rDataOutputSubLogic = new RDataOutputSubLogic();
		rDataOutputSubLogic.execute(Integer.parseInt(csv_number.trim()), seqList, true);
		// 少しスリープ
		Thread.sleep(1000);

		// 差分を調べる
		Set<String> difference = null;
		if (seqHashList.size() > seqDataMapList.size()) {
			difference = new HashSet<>(seqHashList);
			difference.removeAll(seqDataMapList);
		} else if (seqDataMapList.size() > seqHashList.size()) {
			difference = new HashSet<>(seqDataMapList);
			difference.removeAll(seqHashList);
		}

		// 調べた結果がハーフタイムor終了済の場合,すでに取り込まれており作成の必要がないため処理は行わない
		for (List<String> lists : dataList) {
			String seq = lists.get(0);
			Iterator<String> iterator = difference.iterator();
	        while (iterator.hasNext()) {
	        	String element = iterator.next();
	        	if (seq.equals(element)) {
	        		return;
				}
	        }
		}

		if (file2.exists()) {
			String[] sel99List = new String[1];
			sel99List[0] = "file_hash";

			List<List<String>> selectResultList = null;
			SqlMainLogic select = new SqlMainLogic();
			try {
				String searchWhere = "file_name = '" + file + "'";
				selectResultList = select.executeSelect(null, UniairConst.BM_M099, sel99List,
						searchWhere, null, "1");
			} catch (Exception e) {
				System.err.println("SameDataSeqChkLogic chkLogic select err searchData: file_name = " +
						file + ", err: " + e);
			}

			if (selectResultList != null && !selectResultList.isEmpty()) {
				// 国カテゴリ取得
				String[] data = dataCategoryKey.split("-");
				String country_league = data[0];
				String[] data2 = country_league.split(":");
				String country = data2[0].trim();
				String league = data2[1].trim();

				// 更新前通番をソート
				Collections.sort(list, (a, b) -> Integer.compare(Integer.parseInt(a),
						Integer.parseInt(b)));
				// 更新後通番をソート
				Collections.sort(seqList, (a, b) -> Integer.compare(Integer.parseInt(a),
						Integer.parseInt(b)));
				StringBuilder sb = new StringBuilder();
				for (String st : list) {
					if (sb.toString().length() > 0) {
						sb.append(",");
					}
					sb.append(st);
				}
				String bef_seq_list = sb.toString();

				sb = new StringBuilder();
				for (String st : seqList) {
					if (sb.toString().length() > 0) {
						sb.append(",");
					}
					sb.append(st);
				}
				String af_seq_list = sb.toString();

				// 更新前のデータのハッシュ
				String bef_hash = selectResultList.get(0).get(0);

				// 更新後のデータのハッシュ
				ReadFileOperationChk readFileOperationChk = new ReadFileOperationChk();
				String af_hash = readFileOperationChk.getConditionData(dataList);

				List<UpdFileDataInfoEntity> insertList = new ArrayList<UpdFileDataInfoEntity>();
				UpdFileDataInfoEntity fileChkTmpEntity = new UpdFileDataInfoEntity();
				fileChkTmpEntity.setCountry(country);
				fileChkTmpEntity.setLeague(league);
				fileChkTmpEntity.setFileName(file);
				fileChkTmpEntity.setBefSeqList(bef_seq_list);
				fileChkTmpEntity.setAfSeqList(af_seq_list);
				fileChkTmpEntity.setBefFileHash(bef_hash);
				fileChkTmpEntity.setAfFileHash(af_hash);
				insertList.add(fileChkTmpEntity);

				CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
				try {
					csvRegisterImpl.executeInsert(UniairConst.BM_M098,
							insertList, 1, 1);
				} catch (Exception e) {
					System.err.println("file_chk_tmp insert err execute: " + e);
				}
			}
		}
	}

}
