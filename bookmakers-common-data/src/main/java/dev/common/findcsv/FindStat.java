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
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.find.dto.FindBookInputDTO;
import dev.common.find.dto.FindBookOutputDTO;
import dev.common.logger.ManageLoggerComponent;

/**
 * 統計データ読み取りクラス
 * @author shiraishitoshio
 *
 */
@Component
public class FindStat {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FindStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FindStat.class.getSimpleName();

	///** 探索件数 */
	//@Value("${bmbusiness.findbookcounter:1}")
	//private int findBookCounter = 200;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 探索するパスに存在するブックの情報を読み取る
	 * @param FindBookInputDTO
	 * @return FindBookOutputDTO
	 */
	public FindBookOutputDTO execute(FindBookInputDTO inputDTO) {
		final String METHOD_NAME = "execute";
		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

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
			} catch (IOException | InterruptedException e) {
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
		String logMethod = null;
		try {
			if (inputDTO.getTargetFile() != null) {
				logMethod = "getTargetFiles";
				bookList = getTargetFiles(findPath, inputDTO.getTargetFile());
			} else {
				if (inputDTO.isGetBookFlg()) {
					logMethod = "getFiles";
					bookList = getFiles(findPath, inputDTO.getPrefixFile(),
							inputDTO.getSuffixFile(),
							inputDTO.getContainsList(),
							inputDTO.getCsvNumber(),
							inputDTO.getCsvBackNumber());
				} else {
					logMethod = "getStatFiles";
					bookList = getStatFiles(findPath, inputDTO.getPrefixFile(),
							inputDTO.getSuffixFile(),
							inputDTO.getContainsList(),
							inputDTO.getCsvNumber(),
							inputDTO.getCsvBackNumber());
				}
			}
		} catch (IOException e) {
			readBookOutputDTO.setExceptionProject(PROJECT_NAME);
			readBookOutputDTO.setExceptionClass(CLASS_NAME);
			readBookOutputDTO.setExceptionMethod(logMethod);
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

		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		return readBookOutputDTO;
	}

	/**
	 * パス内に存在するブックを検索する
	 * @param path チェックするパス
	 * @param prefixFile 先頭情報
	 * @param suffixFile 拡張子情報
	 * @param containsList 含有情報
	 * @param csvNumber CSV情報
	 * @param csvBackNumber CSV情報
	 * @throws IOException IOException
	 */
	private static List<String> getFiles(String path, String prefixFile,
			String suffixFile,
			String[] containsList, String csvNumber, String csvBackNumber) throws IOException {
		List<String> bookPathList = new ArrayList<>();
		List<Path> bookPathPathList = new ArrayList<>();
		List<String> bookPathSortList = new ArrayList<>();
		Files.list(Paths.get(path))
				.filter(Files::isRegularFile) // CSVファイルのみ
				.filter(pathStr -> pathStr.toString().endsWith(suffixFile) &&
						pathStr.getFileName().toString().startsWith(prefixFile))
				.forEach(bookPathPathList::add);
		for (Path pathStr : bookPathPathList) {
			// パスが全て含まれていなかったらcontinue
			boolean chkFlg = false;
			for (String contains : containsList) {
				if (contains == null)
					continue;
				chkFlg = (pathStr.toString().contains(contains)) ? true : false;
				if (chkFlg) {
					break;
				}
			}
			if (chkFlg) {
				continue;
			}

			// 数字の部分のみ残す
			String convString = pathStr.toString().replace(path, "");
			convString = convString.replace(prefixFile, "");
			convString = convString.replace(suffixFile, "");
			bookPathSortList.add(convString);
		}
		// ソート(数字として)
		Collections.sort(bookPathSortList, Comparator.comparingInt(Integer::parseInt));
		// フィルタしてソート（csvNumberより大きいものだけ）
		List<String> filteredSortedList = null;
		if (csvBackNumber != null) {
			filteredSortedList = bookPathSortList.stream()
					.map(Integer::parseInt)
					.filter(num -> num > Integer.parseInt(csvNumber) &&
							num <= Integer.parseInt(csvBackNumber))
					.sorted()
					.map(String::valueOf)
					.collect(Collectors.toList());
		} else {
			filteredSortedList = bookPathSortList.stream()
					.map(Integer::parseInt)
					.filter(num -> num > Integer.parseInt(csvNumber))
					.sorted()
					.map(String::valueOf)
					.collect(Collectors.toList());
		}
		for (String pathStr : filteredSortedList) {
			// output_,future_をつける
			String convString = path + prefixFile + pathStr + suffixFile;
			bookPathList.add(convString);
		}
		if (bookPathList.isEmpty()) {
			// 上位層に伝播する
			throw new IOException();
		}
		return bookPathList;
	}

	/**
	 * パス内に存在するブックを検索する
	 * @param path チェックするパス
	 * @param prefixFile 先頭情報
	 * @param suffixFile 拡張子情報
	 * @param containsList 含有情報
	 * @param csvNumber CSV情報
	 * @param csvBackNumber CSV情報
	 * @throws IOException IOException
	 */
	private static List<String> getStatFiles(String path, String prefixFile,
			String suffixFile,
			String[] containsList, String csvNumber, String csvBackNumber) throws IOException {
		List<String> bookPathList = new ArrayList<>();
		List<Path> bookPathPathList = new ArrayList<>();
		List<String> bookPathSortList = new ArrayList<>();
		Files.walk(Paths.get(path))
				.filter(Files::isRegularFile) // CSVファイルのみ
				.filter(pathStr -> pathStr.toString().endsWith(suffixFile))
				.forEach(bookPathPathList::add);
		for (Path pathStr : bookPathPathList) {
			// パスが全て含まれていなかったらcontinue
			boolean chkFlg = false;
			for (String contains : containsList) {
				if (contains == null)
					continue;
				chkFlg = (pathStr.toString().contains(contains)) ? true : false;
				if (chkFlg) {
					break;
				}
			}
			if (chkFlg) {
				continue;
			}

			// 数字の部分のみ残す
			String convString = pathStr.toString().replace(path, "");
			convString = convString.replace(suffixFile, "");
			bookPathSortList.add(convString);
		}
		// ソート(数字として)
		Collections.sort(bookPathSortList, Comparator.comparingInt(Integer::parseInt));
		// フィルタしてソート（csvNumberより大きいものだけ）
		List<String> filteredSortedList = null;
		if (csvBackNumber != null) {
			filteredSortedList = bookPathSortList.stream()
					.map(Integer::parseInt)
					.filter(num -> num > Integer.parseInt(csvNumber) &&
							num <= Integer.parseInt(csvBackNumber))
					.sorted()
					.map(String::valueOf)
					.collect(Collectors.toList());
		} else {
			filteredSortedList = bookPathSortList.stream()
					.map(Integer::parseInt)
					.filter(num -> num > Integer.parseInt(csvNumber))
					.sorted()
					.map(String::valueOf)
					.collect(Collectors.toList());
		}
		for (String pathStr : filteredSortedList) {
			// output_,future_をつける
			String convString = path + pathStr + suffixFile;
			bookPathList.add(convString);
		}
		if (bookPathList.isEmpty()) {
			// 上位層に伝播する
			throw new IOException();
		}
		return bookPathList;
	}

	/**
	 * パス内に存在するブックを検索する
	 * @param targetFile 対象ファイル名
	 * @throws IOException IOException
	 */
	private static List<String> getTargetFiles(String path, String targetFile) throws IOException {
		List<String> bookPathList = new ArrayList<>();
		List<Path> bookPathPathList = new ArrayList<>();
		Files.list(Paths.get(path))
				.filter(pathStr -> pathStr.toString().contains(targetFile))
				.forEach(bookPathPathList::add);
		for (Path pathStr : bookPathPathList) {
			bookPathList.add(pathStr.toString());
		}
		if (bookPathList.isEmpty()) {
			// 上位層に伝播する
			throw new IOException();
		}
		return bookPathList;
	}

}
