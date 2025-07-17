package dev.application.analyze.bm_m099;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import dev.common.constant.UniairConst;
import dev.common.util.DateUtil;


/**
 * すでに読み込んだファイルかどうかをチェックするロジック
 * @author shiraishitoshio
 *
 */
public class ReadFileOperationChk {

	/**
	 * 読み込んだファイルかどうかをチェックする
	 * @param filePath
	 * @param convertList
	 * @return
	 * @throws Exception
	 */
	public ReadFileOperationChkOutputDTO readFileChk(String filePath, List<List<String>> convertList) throws Exception {
		ReadFileOperationChkOutputDTO readFileOperationChkOutputDTO =
				new ReadFileOperationChkOutputDTO();

		String hashData = getConditionData(convertList);

		String[] sel98List = new String[2];
		sel98List[0] = "bef_file_hash";
		sel98List[1] = "af_file_hash";

		// file_chk_tmpに存在するかどうか調べる
		SqlMainLogic select = new SqlMainLogic();
		List<List<String>> selectResultTmpList = null;
		try {
			String where = "file_name = '" + filePath + "' and "
					+ "bef_file_hash = '" + hashData + "'";
			selectResultTmpList = select.executeSelect(null, UniairConst.BM_M098,
					sel98List, where, null, "1");
		} catch (Exception e) {
			return readFileOperationChkOutputDTO;
		}

		// 存在しない場合
		if (selectResultTmpList.isEmpty()) {
			String[] sel99List = new String[1];
			sel99List[0] = "id";

			List<List<String>> selectResultList = null;
			try {
				String where = "file_hash = '" + hashData + "'";
				selectResultList = select.executeSelect(null, UniairConst.BM_M099,
						sel99List, where, null, "1");
			} catch (Exception e) {
				return readFileOperationChkOutputDTO;
			}

			// 存在しなかった場合file_chkに登録
			if (selectResultList.isEmpty()) {
				List<CsvImportHistoryEntity> insertList = new ArrayList<CsvImportHistoryEntity>();
				CsvImportHistoryEntity fileChkEntity = new CsvImportHistoryEntity();
				fileChkEntity.setFileName(filePath);
				fileChkEntity.setFileHash(hashData);
				insertList.add(fileChkEntity);

				CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
				try {
					csvRegisterImpl.executeInsert(UniairConst.BM_M099,
							insertList, 1, 1);
				} catch (Exception e) {
					System.err.println("within_data insert err execute: " + e);
				}
				readFileOperationChkOutputDTO.setFileTmpChkFlg(false);
				readFileOperationChkOutputDTO.setFileChkFlg(false);
				return readFileOperationChkOutputDTO;
			}
			readFileOperationChkOutputDTO.setFileTmpChkFlg(false);
			readFileOperationChkOutputDTO.setFileChkFlg(true);
			return readFileOperationChkOutputDTO;
		// 存在する場合,更新対象のファイルであるためfile_hashをaf_file_hashに上書きする
		} else {
			UpdateWrapper updateWrapper = new UpdateWrapper();
			String where = "hash_data = '" + hashData + "'";
			String afHashData = selectResultTmpList.get(0).get(1);

			StringBuilder sqlBuilder = new StringBuilder();
			sqlBuilder.append(" file_hash = '" + afHashData + "' ,");
			sqlBuilder.append(" update_time = '" + DateUtil.getSysDate() + "'");
			// 決定した判定結果に更新
			updateWrapper.updateExecute(UniairConst.BM_M099, where,
					sqlBuilder.toString());
			readFileOperationChkOutputDTO.setFileTmpChkFlg(true);
			readFileOperationChkOutputDTO.setFileChkFlg(false);
			return readFileOperationChkOutputDTO;
		}
	}

	/**
	 * CSVから読み込んだデータをハッシュ化する
	 * @param readData
	 * @return
	 */
	public String getConditionData(List<List<String>> readData) throws Exception {
		StringBuilder sbAll = new StringBuilder();
		for (List<String> data : readData) {
			StringBuilder sb = new StringBuilder();
			for (String da : data) {
				if (sb.toString().length() > 0) {
					sb.append(",");
				}
				sb.append(da);
			}
			if (sb.toString().length() > 0) {
				sbAll.append(sb.toString());
			}
		}
		String subAllData = fileHash(sbAll.toString());
		if (subAllData.length() > 0) {
			return subAllData;
		}
		return "dummy";
	}

	/**
	 * データからハッシュ値を導出する
	 * @param conditionData 条件分岐データ
	 * @return new String(Base64.getEncoder().encodeToString(cipherBytes));
	 * @throws NoSuchAlgorithmException
	 */
	private String fileHash(String conditionData) throws NoSuchAlgorithmException {
		// ハッシュアルゴリズム
		String hashAlgorithm = "SHA-512";

		byte[] cipherBytes = null;
		try {
			MessageDigest md = MessageDigest.getInstance(hashAlgorithm);
			cipherBytes = md.digest(conditionData.getBytes());
		} catch (NoSuchAlgorithmException e) {
			throw e;
		}
		return new String(Base64.getEncoder().encodeToString(cipherBytes));
	}

}
