package dev.common.delete;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.delete.dto.DeleteBookInputDTO;
import dev.common.delete.dto.DeleteBookOutputDTO;


/**
 * ブック削除クラス
 * @author shiraishitoshio
 *
 */
@Component
public class DeleteBook {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = DeleteBook.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = DeleteBook.class.getName();

	/**
	 * 削除するパスに存在するブックの情報を読み取る
	 * @param DeleteBookInputDTO
	 * @return DeleteBookOutputDTO
	 */
	public DeleteBookOutputDTO execute(DeleteBookInputDTO inputDTO) {

		DeleteBookOutputDTO deleteBookOutputDTO = new DeleteBookOutputDTO();
		// 削除対象リストを作成
		List<String> deletePathList = new ArrayList<String>();
		String findPath = inputDTO.getDataPath();
		String copyPath = inputDTO.getCopyPath();
		String originalPath = inputDTO.getOriginalPath();
		if (findPath != null)
			deletePathList.add(findPath);
		if (copyPath != null)
			deletePathList.add(copyPath);
		if (originalPath != null)
			deletePathList.add(originalPath);

		// ファイルの削除
		for (String deletePath : deletePathList) {
			try {
				deleteBookFiles(deletePath);
				deleteBookOutputDTO.setResultCd(BookMakersCommonConst.NORMAL_CD);
			} catch (Exception e) {
				deleteBookOutputDTO.setExceptionProject(PROJECT_NAME);
				deleteBookOutputDTO.setExceptionClass(CLASS_NAME);
				deleteBookOutputDTO.setExceptionMethod("deleteBookFiles");
				deleteBookOutputDTO.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_DELETES);
				deleteBookOutputDTO.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_ERR_FILE_DELETES);
				deleteBookOutputDTO.setThrowAble(e);
			}
		}
		return deleteBookOutputDTO;
	}

	/**
	 * パス内に存在するブックを削除する
	 * @param path チェックするパス
	 * @throws Exception Exception
	 */
	private static void deleteBookFiles(String path) throws Exception {
		File file = new File(path);
		if (!file.exists()) {
			System.out.println("ファイルが削除できませんでした。削除処理をスキップします。");
			return;
		}
		file.delete();
		// 数秒待つ
		Thread.sleep(2000);
	}
}
