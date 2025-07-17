package dev.application.main.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.domain.repository.BookDataRepository;
import dev.common.convertcsvandread.ConvertCsvAndReadFile;
import dev.common.convertcsvandread.dto.ConvertCsvAndReadFileOutputDTO;
import dev.common.copy.CopyFileAndGetFile;
import dev.common.delete.DeleteBook;
import dev.common.delete.dto.DeleteBookInputDTO;
import dev.common.delete.dto.DeleteBookOutputDTO;
import dev.common.entity.BookDataEntity;
import dev.common.exception.SystemException;

/**
 * BMデータ登録業務ロジック
 * @author shiraishitoshio
 *
 */
@Transactional
@Service
public class BookMakerDataRegisterBusinessLogic {

	/** Logger */
	private static final Logger logger = LoggerFactory.getLogger(BookMakerDataRegisterBusinessLogic.class);

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BookMakerDataRegisterBusinessLogic.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BookMakerDataRegisterBusinessLogic.class.getSimpleName();

	/**
	 * ブック削除クラス
	 */
	@Autowired
	private DeleteBook deleteBook;

	/**
	 * コピー読み込みクラス
	 */
	@Autowired
	private CopyFileAndGetFile copyFileAndGetFile;

	/**
	 * CSV変換かつファイル読み込みクラス
	 */
	@Autowired
	private ConvertCsvAndReadFile convertCsvAndReadFile;

	/**
	 * DB登録Repositoryクラス
	 */
	@Autowired
	private BookDataRepository bookDataRepository;

	/** 処理単位登録件数 */
	@Value("${bmbusiness.eachcounter:1}")
	private int registerEachCounter = 5;

	/**
	 * コピー先パス(外部設定値)
	 */
	@Value("${bmbusiness.aftercopypath:/Users/shiraishitoshio/bookmaker/copyfolder/}")
	private String copyPath = "/Users/shiraishitoshio/bookmaker/copyfolder/";

	/**
	 * 処理実行
	 * <p>
	 * 1. ファイル内のデータ取得</br>
	 * 2. DB登録処理</br>
	 * 3. ファイル削除処理</br>
	 * @param path 探索パス
	 * @return 0:正常終了, 4:警告終了, 9:異常終了
	 */
	public int execute(String path) {
		logger.info(" db register businessLogic start : {} ", CLASS_NAME);

		List<String> bookList = this.copyFileAndGetFile.execute(path, this.copyPath);
		// ファイル内のデータ取得
		for (String filePath : bookList) {
			logger.info(" file name execute start : {} ", filePath);

			// CSV変換&読み取り
			ConvertCsvAndReadFileOutputDTO convertCsvAndReadFileOutputDTO = this.convertCsvAndReadFile.execute(filePath);
			String afterFilePath = convertCsvAndReadFileOutputDTO.getAfterCsvPath();

			// 読み取りができなかった場合次のループ
			List<BookDataEntity> dbList = convertCsvAndReadFileOutputDTO.getReadDataList();
			if (dbList == null || dbList.isEmpty()) {
				continue;
			}

			// 変換,読み取り結果フラグ
			boolean convertCsvFlg = convertCsvAndReadFileOutputDTO.isConvertCsvFlg();
			boolean readCsvFlg = convertCsvAndReadFileOutputDTO.isReadCsvFlg();

			int registerEach = registerEachCounter;

			boolean registerFlg = true;
			int registerAllCount = 0;
			while (true) {
				int registerWhichEach = Math.min(registerEach, dbList.size() - registerAllCount);
				List<BookDataEntity> workEntityList = dbList.subList(registerAllCount,
						registerAllCount + registerWhichEach);
				for (BookDataEntity inserEntity : workEntityList) {
					int registerResult = this.bookDataRepository.insert(inserEntity);
					// 登録件数が一致しない
					if (registerResult != 1) {
						throw new SystemException(
								"",
								"",
								"",
								"登録エラー: " + registerAllCount + "レコード目 / " + dbList.size() + "レコード");
					}
					registerAllCount += registerResult;
					workEntityList.clear();
				}
				if (registerAllCount == dbList.size()) {
					break;
				}
			}

			// ファイル削除処理
			// この時点でDB登録は完了しているため,削除できなかった場合,できるまで処理を実施
			int chkLoop = 0;
			while (true) {
				DeleteBookInputDTO deleteBookInputDTO = new DeleteBookInputDTO();
				deleteBookInputDTO.setDataPath(filePath);
				deleteBookInputDTO.setCopyPath(afterFilePath);
				// CSV変換,CSV読み取り,DB登録が全て完了した場合,オリジナルパスも削除
				if (convertCsvFlg && readCsvFlg && registerFlg) {
					// コピー元のオリジナルxlsxパスに置き換え
					String originalPath = filePath.replace("copyfolder/", "");
					deleteBookInputDTO.setOriginalPath(originalPath);
				}
				DeleteBookOutputDTO deleteBookOutputDTO = this.deleteBook.execute(deleteBookInputDTO);
				if (!BookMakersCommonConst.NORMAL_CD.equals(deleteBookOutputDTO.getResultCd())) {
					logger.error(" delete file error -> file_name : {}, project_name : {}, class_name : {},"
							+ " method_name : {}, err_cd : {}, cause : {} ",
							afterFilePath,
							deleteBookOutputDTO.getExceptionProject(),
							deleteBookOutputDTO.getExceptionClass(),
							deleteBookOutputDTO.getExceptionMethod(),
							deleteBookOutputDTO.getErrMessage(),
							deleteBookOutputDTO.getThrowAble());
					chkLoop++;
					if (chkLoop == 3) {
						logger.error(" delete file error -> {} ","削除が失敗しました。");
						break;
					}
				} else {
					break;
				}
			}

			logger.info(" file name execute end : {} ", filePath);
		}

		logger.info(" db register businessLogic end : {} ", CLASS_NAME);

		return 0;
	}
}
