package dev.common.readfile;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.common.readfile.dto.ReadFileOutputDTO;

@SpringBootTest
class ReadFutureTest {

	/**
	 * CSV原本パス
	 */
	private static final String PATH = "/Users/shiraishitoshio/bookmaker/";

    @Autowired
    private ReadFuture readFuture;

    @Test
    void getFileBody_shouldReturnData_whenValidCsvProvided() {
        // Act
        ReadFileOutputDTO result = readFuture.getFileBody(PATH + "future_2.csv");

        // Assert
        assertNotNull(result);
        assertEquals("0000000000", result.getResultCd());
        assertNotNull(result.getFutureList());

        // さらに具体的な検証
        assertEquals("ブルガリア: パルヴァ・リーガ - ラウンド 6", result.getFutureList().get(0).getGameTeamCategory());
        assertEquals("スラヴィア・ソフィア", result.getFutureList().get(0).getHomeTeamName());
        assertEquals("アルダ・クルジャリ", result.getFutureList().get(0).getAwayTeamName());
    }
}
