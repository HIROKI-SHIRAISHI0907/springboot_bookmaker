package dev.common.convertcsvandread;

import java.io.IOException;

import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.convcsv.ConvertCSV;
import dev.common.convertcsvandread.dto.ConvertCsvAndReadFileOutputDTO;
import dev.common.readfile.ReadFile;
import dev.common.readfile.dto.ReadFileOutputDTO;


/**
 * CSV変換&読み取り
 * @author shiraishitoshio
 *
 */
@Component
public class ConvertCsvAndReadFile {

	/** .xlsx */
	private static final String XLSX = ".xlsx";

	/** .CSV */
	private static final String CSV = ".csv";

	/**
	 * CSV変換&読み取り
	 * @param filePath
	 * @return
	 */
	public ConvertCsvAndReadFileOutputDTO execute(String filePath) {
		ConvertCsvAndReadFileOutputDTO convertCsvAndReadFileOutputDTO = new ConvertCsvAndReadFileOutputDTO();

		// CSV変換成功フラグ, CSV読み取り成功フラグ初期化
		convertCsvAndReadFileOutputDTO.setConvertCsvFlg(true);
		convertCsvAndReadFileOutputDTO.setReadCsvFlg(true);

		// CSV変換ロジック
		String afterFilePath = null;
		try {
			String csvFilePath = filePath.replace(XLSX, CSV);
			// 変換後のcsvパスをfilePathとする
			afterFilePath = csvFilePath;
			ConvertCSV.convertExecute(filePath, csvFilePath);
			convertCsvAndReadFileOutputDTO.setAfterCsvPath(afterFilePath);
		} catch (IOException e) {
			convertCsvAndReadFileOutputDTO.setConvertCsvFlg(false);
		}

		ReadFile readFile = new ReadFile();
		ReadFileOutputDTO readFileOutputDTO = readFile.getFileBody(afterFilePath);
		//ReadFileOutputDTO readFileOutputDTO = this.readFile.getFileBody(path);
		if (!BookMakersCommonConst.NORMAL_CD.equals(readFileOutputDTO.getResultCd())) {
			convertCsvAndReadFileOutputDTO.setReadCsvFlg(false);
		}
		convertCsvAndReadFileOutputDTO.setReadDataList(readFileOutputDTO.getReadDataList());
		return convertCsvAndReadFileOutputDTO;
	}

}
