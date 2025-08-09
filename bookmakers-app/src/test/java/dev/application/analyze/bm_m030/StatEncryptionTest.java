package dev.application.analyze.bm_m030;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.application.domain.repository.StatEncryptionRepository;
import dev.application.enc.CommonEncHelper;

/**
 * BM_M030統計分析ロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
public class StatEncryptionTest extends CommonEncHelper {

	@Autowired
	private StatEncryptionRepository statEncryptionRepository;

	/**
	 * メモリ効率試験
	 * @throws Exception
	 */
	@Test
	void test_calcStat_shouldUpdateCorrectly() throws Exception {
		// Act
		String country = "スペイン";
		String league = "ラ・リーガ";
		String team = "バレンシア";
		String chk_body = "ALL";
		List<StatEncryptionEntity> result = this.statEncryptionRepository.findEncData(
				country, league, team, chk_body);
		// home_exo_info取得
		if (result.isEmpty()) return;
		String value = decChk(result.get(0).getHomeExpInfo());
		assertEquals("0.0,0.0,0.0,0.0,0.05,0.05,0.05,0.05,"
				+ "0.14,0.22,0.22,0.14,0.38,0.62,0.64,0.82,0.85,"
				+ "0.88,0.91,0.91,0.91,0.91", value);
	}

}
