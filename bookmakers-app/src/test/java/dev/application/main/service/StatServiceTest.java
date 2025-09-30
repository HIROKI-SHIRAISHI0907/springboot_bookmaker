package dev.application.main.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.common.entity.BookDataEntity;
import dev.common.getstatinfo.GetStatInfo;

@SpringBootTest
class StatServiceTest {

	/**
	 * 統計情報取得管理クラス
	 */
	@Autowired
	private GetStatInfo getStatInfo;

	/** StatService */
	@Autowired
	private StatService statService;

	@Test
	void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
		// シーケンスデータから取得(最大値情報取得)
		String csvNumber = "0";
		String csvBackNumber = "4";

		// 直近のCSVデータ情報を取得
		Map<String, Map<String, List<BookDataEntity>>> getStatMap = this.getStatInfo.getData(csvNumber, csvBackNumber);
		// Act
		int result = statService.execute(getStatMap);

		// Assert
		assertEquals(0, result); // 戻り値が0であること
	}
}
