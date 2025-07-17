package dev.application.main.service.sub;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.common.util.TableTruncateUtil;
import dev.application.domain.repository.BookDataRepository;
import dev.common.delete.DeleteBook;
import dev.common.delete.dto.DeleteBookInputDTO;
import dev.common.delete.dto.DeleteBookOutputDTO;
import dev.common.exception.SystemException;


/**
 * R言語用に用意するCSVファイルを作成するメインロジック
 * @author shiraishitoshio
 *
 */
@Component
public class RDataOutputMainLogic {

	/** Logger */
	private static final Logger logger = LoggerFactory.getLogger(RDataOutputMainLogic.class);

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RDataOutputMainLogic.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RDataOutputMainLogic.class.getSimpleName();

	/** ログ出力 */
	private static final String START_END_FILE = "/Users/shiraishitoshio/bookmaker/csv/seqList_time.txt";

	/** ログ出力 */
	private static final String FILE = "/Users/shiraishitoshio/bookmaker/csv/seqList.txt";

	/** CSVパス保存テキスト */
	private static final String PATH_TEXT = "/Users/shiraishitoshio/bookmaker/tmp_file/exists_path.txt";

	/**
	 * ブック削除クラス
	 */
	@Autowired
	private DeleteBook deleteBook;

	/**
	 * DB登録Repositoryクラス
	 */
	@Autowired
	private BookDataRepository bookDataRepository;

	/**
	 * 更新CSVデータ汎用ロジック
	 */
	@Autowired
	private ReadSoccerCsvDataUpd readCsvDataUpd;

	/**
	 * 処理実行
	 * @throws Exception
	 */
	public void execute() throws Exception {
		// データ取得
		final String METHOD = "execute";

		// CSVパス保存テキストを削除する
		DeleteBookInputDTO deleteBookInputDTO = new DeleteBookInputDTO();
		deleteBookInputDTO.setDataPath(PATH_TEXT);
		DeleteBookOutputDTO deleteBookOutputDTO = this.deleteBook.execute(deleteBookInputDTO);
		if (!BookMakersCommonConst.NORMAL_CD.equals(deleteBookOutputDTO.getResultCd())) {
			throw new SystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD,
					"",
					deleteBookOutputDTO.getThrowAble());
		}

		List<List<String>> allSeqGroupAllList = new ArrayList<List<String>>();

		// 時間ファイルを削除
		deleteBookInputDTO.setDataPath(START_END_FILE);
		deleteBookOutputDTO = this.deleteBook.execute(deleteBookInputDTO);
		if (!BookMakersCommonConst.NORMAL_CD.equals(deleteBookOutputDTO.getResultCd())) {
			throw new SystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD,
					"",
					deleteBookOutputDTO.getThrowAble());
		}

		try {
			File file = new File(START_END_FILE);

			FileWriter filewriter = new FileWriter(file);
			filewriter.write("RDataOutputMainLogic start time : " + new Timestamp(System.currentTimeMillis()) + "\r\n");
			filewriter.close();
		} catch (IOException e) {
			System.out.println(e);
		}

		// ファイルが存在するか
		boolean fileExistsFlg = false;
		Path p = Paths.get(FILE);
		int csvId = 1;
		if (!Files.exists(p)) {
			// 各分析テーブル削除
			TableTruncateUtil.executeTruncate();

			// レコード件数を取得する
			int allCnt = -1;
			try {
				allCnt = this.bookDataRepository.selectCount();
			} catch (Exception e) {
				throw new SystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD,
						"",
						e.getCause());
			}

			// スレッドセーフな多重リストを作成
			List<List<String>> allSeqGroupList = Collections.synchronizedList(new ArrayList<List<String>>());
			// 並列プール数
			final int SEQ_POOL_PARALLEL = 60;
			// 担当範囲決定
			int rangeSize = (int) Math.ceil((double) allCnt / SEQ_POOL_PARALLEL);
			ForkJoinPool forkJoinPool = new ForkJoinPool(SEQ_POOL_PARALLEL);
			for (int taskId = 1; taskId <= SEQ_POOL_PARALLEL; taskId++) {
				int start = taskId * rangeSize;
				int end = Math.min(start + rangeSize - 1, allCnt - 1);

				final int rangeStart = start;
				final int rangeEnd = end;

				forkJoinPool.submit(() -> {
					TeamStatisticsDataPartsMultiSeparateSequenceLogic teamStatisticsDataPartsMultiSeparateSequenceLogic = new TeamStatisticsDataPartsMultiSeparateSequenceLogic();
					for (int taskSubId = rangeStart; taskSubId <= rangeEnd; taskSubId++) {
						int task = taskSubId;
						// 通番リストを取得
						List<String> separateSeqList = teamStatisticsDataPartsMultiSeparateSequenceLogic
								.separateLogic(task);
						if (separateSeqList != null) {
							allSeqGroupList.add(separateSeqList);
						}
					}
				});
			}
			forkJoinPool.shutdown();
			try {
				if (forkJoinPool.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.SECONDS)) {
					logger.info("task all fin");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				logger.error("task were interrupted");
			}

			// allSeqGroupListで重複分を取り除く
			List<List<String>> uniqueList = removeDuplicates(allSeqGroupList);

			// CSV作成
			logger.info("csv making size : {} ", uniqueList.size());

			try {
				File file = new File(FILE);

				FileWriter filewriter = new FileWriter(file, true);
				filewriter.write("csv making seq size : " + uniqueList.size() + "\r\n");
				filewriter.close();
			} catch (IOException e) {
				System.out.println(e);
			}
			allSeqGroupAllList = uniqueList;
			fileExistsFlg = false;
			// seqList.txtファイル存在する場合
		} else {
			// 読み込んだallSeqGroupAllListを1行ずつチェックし,以下の処理を行う
			// 1.現在作成されているcsvの数字の部分(csv番号)と(国カテゴリ,ホームチーム,アウェーチーム)をMapで保持し,初期化する。
			// 2.読み込んだ通番リストに紐づくデータカテゴリ,ホームチーム,アウェーチームの組み合わせを調べる
			// 3.2の組み合わせの条件をDBから改めて取得し,2.と3.のリストサイズが等しい(全部のデータが存在する)ならリストはそのまま
			// 4.リストサイズが等しくない(新しく追加されたデータがある)場合,新しく追加されたデータをallSeqGroupAllListに追加する
			// 5. リストサイズが等しくない場合は,4.のデータを1.のMapから探し,csv番号を取得後,新しいデータを追加したデータに更新する
			ReadSoccerCsvDataOutputDTO readSoccerCsvDataOutputDTO = this.readCsvDataUpd.execute();
			// 返却されたcsvIdとallSeqGroupAllListに設定
			allSeqGroupAllList = readSoccerCsvDataOutputDTO.getAllSeqList();
			csvId = readSoccerCsvDataOutputDTO.getCsvNumber();
			fileExistsFlg = true;
		}

		RDataOutputSubLogic rDataOutputSubLogic = new RDataOutputSubLogic();
		for (List<String> groupList : allSeqGroupAllList) {
			csvId = rDataOutputSubLogic.execute(csvId, groupList, fileExistsFlg);
		}

		try {
			File file = new File(START_END_FILE);

			FileWriter filewriter = new FileWriter(file, true);
			filewriter
					.write("RDataOutputMainLogic end time : " + new Timestamp(System.currentTimeMillis()) + "\r\n");
			filewriter.close();
		} catch (IOException e) {
			System.out.println(e);
		}
	}

	/**
	 * 重複リストを排除する
	 * @param inputList
	 * @return
	 */
	public static List<List<String>> removeDuplicates(List<List<String>> inputList) {
		Set<List<String>> seen = new HashSet<>();
		List<List<String>> result = new ArrayList<>();

		for (List<String> list : inputList) {
			List<String> sortedList = new ArrayList<>(list);
			Collections.sort(sortedList); // 並び順を統一
			if (seen.add(sortedList)) {
				result.add(list); // 元の順番でリストに追加
			}
		}
		return result;
	}

}
