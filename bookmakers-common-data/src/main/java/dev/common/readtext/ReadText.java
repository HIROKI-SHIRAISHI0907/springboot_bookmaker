package dev.common.readtext;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import dev.common.constant.BookMakersCommonConst;
import dev.common.readtext.dto.ReadTextInputDTO;
import dev.common.readtext.dto.ReadTextOutputDTO;

public class ReadText {

	/** Logger */
	//private static final Logger logger = LoggerFactory.getLogger(FindBook.class);

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ReadText.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ReadText.class.getSimpleName();

	/**
	 * 統計データファイルの中身を取得する
	 * @param readTextInputDTO 読み込みテキストInputDTO
	 * @return readFileOutputDTO
	 */
	public ReadTextOutputDTO execute(ReadTextInputDTO readTextInputDTO) {
		final String METHOD_NAME = "getFileBody";
		ReadTextOutputDTO readTextOutputDTO = new ReadTextOutputDTO();
		List<List<String>> resultList = new ArrayList<List<String>>();

		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(readTextInputDTO.getDataPath())))) {
			String text;
			int row = (readTextInputDTO.isHeaderFlg()) ? 0 : 1;
			while ((text = br.readLine()) != null) {
				List<String> resultSubList = new ArrayList<>();
				if (row > 0) {
					// 置換
					for (String conv : readTextInputDTO.getConvertTagList()) {
						text = text.replace(conv, "");
					}
					// 分割
					String[] splitList = text.split(readTextInputDTO.getSplitTag());
					for (String list : splitList) {
						resultSubList.add(list.trim());
					}
					resultList.add(resultSubList);
				} else {
					row++;
				}
			}
			readTextOutputDTO.setResultCd(BookMakersCommonConst.NORMAL_CD);
			readTextOutputDTO.setReadDataList(resultList);
		} catch (Exception e) {
			readTextOutputDTO.setExceptionProject(PROJECT_NAME);
			readTextOutputDTO.setExceptionClass(CLASS_NAME);
			readTextOutputDTO.setExceptionMethod(METHOD_NAME);
			readTextOutputDTO.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_READS);
			readTextOutputDTO.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_ERR_FILE_READS);
			readTextOutputDTO.setThrowAble(e);
		}
		return readTextOutputDTO;
	}

}
