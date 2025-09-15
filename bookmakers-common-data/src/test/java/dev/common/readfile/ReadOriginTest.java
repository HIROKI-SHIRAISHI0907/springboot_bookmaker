package dev.common.readfile;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.common.readfile.dto.ReadFileOutputDTO;

@SpringBootTest
@ActiveProfiles("test")
public class ReadOriginTest {

	/**
	 * CSV原本パス
	 */
	private static final String PATH = "/Users/shiraishitoshio/bookmaker/";

    @Autowired
    private ReadOrigin readOrigin;

    @Test
    void getFileBody_shouldReturnData_whenValidCsvProvided() {
        // Act
        ReadFileOutputDTO result = readOrigin.getFileBody(PATH + "output_260.csv");

        // Assert
        assertNotNull(result);
        assertEquals("0000000000", result.getResultCd());
        assertNotNull(result.getDataList());

        // さらに具体的な検証
        assertEquals("アルゼンチン: トルネオ・ベターノ - クラウスラ - ラウンド 8", result.getDataList().get(0).getDataCategory());
        assertEquals("CDリエストラ", result.getDataList().get(0).getHomeTeamName());
        assertEquals("ｾﾝﾄﾗﾙ･ｺﾙﾄﾞﾊﾞ", result.getDataList().get(0).getAwayTeamName());
        assertEquals("36%", result.getDataList().get(0).getHomeDonation());
        assertEquals("59%(51/86)", result.getDataList().get(0).getHomePassCount());
        assertEquals("14", result.getDataList().get(0).getHomeDuelCount());
        assertEquals("13", result.getDataList().get(0).getAwayDuelCount());
    }

}
