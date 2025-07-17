package dev.common.copy;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.exception.BusinessException;
import dev.common.find.FindBook;
import dev.common.find.dto.FindBookInputDTO;
import dev.common.find.dto.FindBookOutputDTO;

/**
 * ファイルコピー&ファイルパス取得
 * @author shiraishitoshio
 *
 */
@Component
public class CopyFileAndGetFile {

	/**
	 * ファイルコピー&ファイルパス取得
	 * @param path オリジナルパス
	 * @param copyPath コピー先パス
	 * @return
	 */
	public List<String> execute(String path, String copyPath) {
		// コピー先パスを保存
		String originalCopyPath = copyPath;
		// フォルダに残っている場合削除する
//		try {
//			File dir = new File(originalCopyPath);
//			FileUtils.deleteDirectory(dir);
//		} catch (Exception ex) {
//			ex.printStackTrace();
//		}

		// 1. ブック探索クラスから特定のパスに存在する全ブックをリストで取得
		FindBookInputDTO findBookInputDTO = new FindBookInputDTO();
		findBookInputDTO.setDataPath(path);
		findBookInputDTO.setCopyPath(originalCopyPath);
		findBookInputDTO.setCopyFlg(true);
		FindBook findBook = new FindBook();
		FindBookOutputDTO findBookOutputDTO = findBook.execute(findBookInputDTO);
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
