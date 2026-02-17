package dev.common.findcsv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.find.dto.FindBookInputDTO;
import dev.common.find.dto.FindBookOutputDTO;


/**
 * Csv読み取りクラス
 * @author shiraishitoshio
 *
 */
@Component
public class FindThresHoldCsv {

	/** Logger */
	//private static final Logger logger = LoggerFactory.getLogger(FindBook.class);

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FindThresHoldCsv.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FindThresHoldCsv.class.getName();

	/** .xlsx */
	private static final String CSV = ".csv";

	/**
	 * 探索するパスに存在するブックの情報を読み取る
	 * @param FindBookInputDTO
	 * @return FindBookOutputDTO
	 */
	public FindBookOutputDTO execute(FindBookInputDTO inputDTO) {
		//logger.info(" find book start : {} " , CLASS_NAME);

		final String METHOD_NAME = "execute";

		FindBookOutputDTO readBookOutputDTO = new FindBookOutputDTO();
		// コピーフラグを確認し,Trueならコピーしておく
		String findPath = inputDTO.getDataPath();
		String copyPath = null;
		if (inputDTO.isCopyFlg()) {
			copyPath = inputDTO.getCopyPath();
			try {
				// コピー先が存在しなければ作成
			    Path p2 = Paths.get(copyPath);
			    if (!Files.exists(p2)) {
			    	Files.createDirectory(p2);
			    }
				// 2秒ほど待機
				Thread.sleep(1200);
			} catch (IOException | InterruptedException e){
				readBookOutputDTO.setExceptionProject(PROJECT_NAME);
				readBookOutputDTO.setExceptionClass(CLASS_NAME);
				readBookOutputDTO.setExceptionMethod(METHOD_NAME);
				readBookOutputDTO.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FOLDER_MAKES);
				readBookOutputDTO.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_ERR_FOLDER_MAKES);
				readBookOutputDTO.setThrowAble(e);
				return readBookOutputDTO;
			}
		}

		List<String> bookList = null;
		// ファイルの存在確認
		try {
			bookList = getBookFiles(findPath, inputDTO.getTargetFile());
		} catch (IOException e) {
			readBookOutputDTO.setExceptionProject(PROJECT_NAME);
			readBookOutputDTO.setExceptionClass(CLASS_NAME);
			readBookOutputDTO.setExceptionMethod("getBookFiles");
			readBookOutputDTO.setResultCd(BookMakersCommonConst.ERR_CD_NO_FILE_EXISTS);
			readBookOutputDTO.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_NO_FILE_EXISTS);
			readBookOutputDTO.setThrowAble(e);
			return readBookOutputDTO;
		}

		if (copyPath != null) {
			try {
				// コピー先のパスに変換したリストに置換
				int index = 0;
				for (String bookFilePath : bookList) {
					Path befPath = Paths.get(bookFilePath);
					bookFilePath = bookFilePath.replace(findPath, copyPath);
					Path afPath = Paths.get(bookFilePath);
					Files.copy(befPath, afPath, StandardCopyOption.REPLACE_EXISTING);

					bookList.set(index, bookFilePath);
					index++;
				}
			} catch (IOException e) {
				readBookOutputDTO.setExceptionProject(PROJECT_NAME);
				readBookOutputDTO.setExceptionClass(CLASS_NAME);
				readBookOutputDTO.setExceptionMethod(METHOD_NAME);
				readBookOutputDTO.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_COPY);
				readBookOutputDTO.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_ERR_FILE_COPY);
				readBookOutputDTO.setThrowAble(e);
				return readBookOutputDTO;
			}
		}
		readBookOutputDTO.setResultCd(BookMakersCommonConst.NORMAL_CD);
		readBookOutputDTO.setBookList(bookList);

		//logger.info(" find book end : {} " , CLASS_NAME);

		return readBookOutputDTO;
	}

	/**
	 * パス内に存在するブックを検索する
	 * @param path チェックするパス
	 * @return bookPathList ブックリスト
	 * @param targetFile 対象ファイル名
	 * @throws IOException IOException
	 */
	private static List<String> getBookFiles(String path, String targetFile) throws IOException {
		List<String> bookPathList = new ArrayList<>();
		List<Path> bookPathPathList = new ArrayList<>();
		List<String> bookPathSortList = new ArrayList<>();
		Files.walk(Paths.get(path))
			.filter(Files::isRegularFile) // CSVファイルのみ
			.filter(pathStr -> pathStr.toString().endsWith(CSV))
			.forEach(bookPathPathList::add);
		for (Path pathStr : bookPathPathList) {
			if (targetFile != null && pathStr.toString().contains(targetFile)) {
				bookPathSortList.add(pathStr.toString());
				return bookPathSortList;
			}

			if (pathStr.toString().contains("breakfile") ||
					pathStr.toString().contains("conditiondata/") ||
					pathStr.toString().contains("python_analytics/")) {
				continue;
			}
			// 数字の部分のみ残す
			String convString = pathStr.toString().replace(path, "");
			convString = convString.replace(CSV, "");
			bookPathSortList.add(convString);
		}
		// ソート(数字として)
		Collections.sort(bookPathSortList, Comparator.comparingInt(Integer::parseInt));
		for (String pathStr : bookPathSortList) {
			// output_をつける
			String convString = path + pathStr + CSV;
			bookPathList.add(convString);
		}
		return bookPathList;
	}
}
