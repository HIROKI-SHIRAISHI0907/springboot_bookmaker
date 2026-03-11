package dev.application.main.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.common.entity.BookDataEntity;
import dev.common.getinfo.GetStatInfo;

@SpringBootTest(properties = {
	    "MASTER_DB_URL=jdbc:postgresql://localhost:54320/soccer_bm_master",
	    "MASTER_DB_USER=postgres",
	    "MASTER_DB_PASS=sonic3717",

	    "BM_DB_URL=jdbc:postgresql://localhost:54320/soccer_bm",
	    "BM_DB_USER=postgres",
	    "BM_DB_PASS=sonic3717",
	})
@ActiveProfiles("prod")
class CoreStatTest {

	/**
	 * 統計情報取得管理クラス
	 */
	@Autowired
	private GetStatInfo getStatInfo;

	/** StatService */
	@Autowired
	private CoreStat statService;

	@Test
	void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
		// シーケンスデータから取得(最大値情報取得)
		String csvNumber = "0";
		String csvBackNumber = null;

		// 直近のCSVデータ情報を取得
		List<String> list = this.getStatInfo.listCsvKeysInRange(csvNumber, csvBackNumber);
		Map<String, Map<String, List<BookDataEntity>>> entities = getStatInfo.getStatMapForSingleKey(list.get(0));
		// Act
		int result = statService.execute(entities);

		// Assert
		assertEquals(0, result); // 戻り値が0であること
	}

	@Test
	void execute_test() throws Exception {
		// シーケンスデータから取得(最大値情報取得)
		String csvNumber = "4101";
		String csvBackNumber = "4109";

		// 直近のCSVデータ情報を取得
		List<String> list = this.getStatInfo.listCsvKeysInRange(csvNumber, csvBackNumber);
		Map<String, Map<String, List<BookDataEntity>>> entities = getStatInfo.getStatMapForSingleKey(list.get(0));
		// Act
		int result = statService.execute(entities);

		// Assert
		assertEquals(0, result); // 戻り値が0であること
	}
}
