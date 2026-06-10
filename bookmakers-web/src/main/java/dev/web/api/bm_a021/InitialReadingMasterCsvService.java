package dev.web.api.bm_a021;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.common.entity.InitialReadingMasterCsvEntity;
import dev.web.repository.master.InitialReadingMasterCsvRepository;

/**
 * マスタ登録CSV初回読み込み確認サービス
 */
@Service
@Transactional(readOnly = true)
public class InitialReadingMasterCsvService {

	@Autowired
	private InitialReadingMasterCsvRepository initialReadingMasterCsvRepository;

	/**
	 * 指定マスタの初回読み込み状態を返却
	 *
	 * 判定ルール:
	 * - initial_reading_csv_master に initial_flg='0' のデータが存在する場合:
	 *     → 未初回読み込み
	 * - 存在しない場合:
	 *     → 初回読み込み済み
	 */
	public InitialReadingMasterCsvResponse getStatus(String masterName,
			String country, String league) {

		List<InitialReadingMasterCsvEntity> list =
				this.initialReadingMasterCsvRepository.findData(masterName
						,country, league);
		// 初回ならfalse、初回ではないならtrue
		boolean initialReadCompleted = (list == null || list.isEmpty());

		InitialReadingMasterCsvResponse response = new InitialReadingMasterCsvResponse();
		response.setMasterName(masterName);
		response.setChkFlg(initialReadCompleted ? "1" : "0");
		response.setMessage(initialReadCompleted
				? "初回読み込み済み"
				: "未初回読み込み");
		return response;
	}
}
