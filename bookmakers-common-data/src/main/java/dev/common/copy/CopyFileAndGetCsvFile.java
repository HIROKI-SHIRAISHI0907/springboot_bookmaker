package dev.common.copy;

import java.util.List;

import dev.common.constant.BookMakersCommonConst;
import dev.common.exception.BusinessException;
import dev.common.find.dto.FindBookInputDTO;
import dev.common.find.dto.FindBookOutputDTO;
import dev.common.findcsv.FindCsv;

/**
 * ファイルコピー&ファイルパス取得
 * @author shiraishitoshio
 *
 */
public class CopyFileAndGetCsvFile {

	/**
	 * ファイルコピー&ファイルパス取得
	 * @param path オリジナルパス
	 * @param copyPath コピー先パス
	 * @param targetFile 対象ファイル名
	 * @return
	 */
	public List<String> execute(String path, String copyPath, String targetFile) {
		// コピー先パスを保存
		String originalCopyPath = copyPath;
		// フォルダに残っている場合削除する

		// 1. ブック探索クラスから特定のパスに存在する全ブックをリストで取得
		FindBookInputDTO findBookInputDTO = new FindBookInputDTO();
		findBookInputDTO.setDataPath(path);
		findBookInputDTO.setCopyPath(originalCopyPath);
		findBookInputDTO.setCopyFlg(true);
		findBookInputDTO.setTargetFile(targetFile);
		FindCsv findCsv = new FindCsv();
		FindBookOutputDTO findBookOutputDTO = findCsv.execute(findBookInputDTO);
		//FindBookOutputDTO findBookOutputDTO = this.findBook.execute(findBookInputDTO);
		// エラーの場合,戻り値の例外を業務例外に集約してスロー
		if (!BookMakersCommonConst.NORMAL_CD.equals(findBookOutputDTO.getResultCd())) {
			throw new BusinessException(
					findBookOutputDTO.getExceptionProject(),
					findBookOutputDTO.getExceptionClass(),
					findBookOutputDTO.getExceptionMethod(),
					findBookOutputDTO.getErrMessage(),
					findBookOutputDTO.getThrowAble());
		}
		return findBookOutputDTO.getBookList();
	}

}
