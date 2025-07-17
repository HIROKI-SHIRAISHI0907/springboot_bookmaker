package dev.common.readfile;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.common.readfile.dto.ReadFileOutputDTO;

@SpringBootTest
class ReadStatTest {

	/**
	 * CSV原本パス
	 */
	private static final String PATH = "/Users/shiraishitoshio/bookmaker/csv/";

    @Autowired
    private ReadStat readStat;

    @Test
    void getFileBody_shouldReturnData_whenValidCsvProvided() {
        // Act
        ReadFileOutputDTO result = readStat.getFileBody(PATH + "4000.csv");

        // Assert
        assertNotNull(result);
        assertEquals("0000000000", result.getResultCd());
        assertNotNull(result.getReadHoldDataList());
        assertEquals(31, result.getReadHoldDataList().size());

        // さらに具体的な検証
        assertEquals("カメルーン: エリート 1 - ラウンド 30", result.getReadHoldDataList().get(0).getGameTeamCategory());
        assertEquals("ﾌｰｳﾞ･ｱｽﾞｰﾙ･ｴﾘｰﾄ", result.getReadHoldDataList().get(0).getHomeTeamName());
        assertEquals("ガゼル", result.getReadHoldDataList().get(0).getAwayTeamName());
    }
}
