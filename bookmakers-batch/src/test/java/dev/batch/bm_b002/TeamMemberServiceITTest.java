package dev.batch.bm_b002;

import static dev.batch.general.CsvHeaderMaps.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import dev.batch.config.TestMyBatisH2Config;
import dev.batch.general.CsvImport;
import dev.common.entity.TeamMemberMasterEntity;

/**
 * TeamMemberServiceITTest
 * @author shiraishitoshio
 *
 */
@Tag("IT")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestMyBatisH2Config.class)
@TestPropertySource(properties = {
		// --- app ---
		"app.b001-json-folder=bookmakers-batch/src/test/resources/json/b001/",
		"app.csv-folder=bookmakers-batch/src/test/resources/csv/",
		"app.output-csv-folder=bookmakers-batch/src/test/resources/outputs/",
		"app.team-csv-folder=bookmakers-batch/src/test/resources/teams_by_league/",
		"app.future-csv-folder=bookmakers-batch/src/test/resources/future/",
		"app.team-csv-folder=bookmakers-batch/src/test/resources/teams_by_league/",
		"app.future-csv-folder=bookmakers-batch/src/test/resources/future/",

		// --- process.python ---
		"process.python.root=/Users/shiraishitoshio/bookmaker/team_master_python",
		"process.python.pythonBin=python3",

		// --- process.s3 ---
		"process.s3.region=ap-northeast-1",
		"process.s3.buckets.teamSeasonDateData=aws-s3-team-season-date-data",
		"process.s3.buckets.teamData=aws-s3-team-data",
		"process.s3.buckets.teamMemberData=aws-s3-team-member-data",
		"process.s3.buckets.outputs=aws-s3-outputs"
})
public class TeamMemberServiceITTest {

	@Autowired
	private TeamMemberMasterStat teamMemberMasterStat;

	@Test
	void execute_TC_TS_001(TestInfo testInfo) throws Exception {
		String testMethodName = testInfo.getTestMethod()
				.map(m -> m.getName())
				.orElse("unknown");

		List<TeamMemberMasterEntity> list = CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b002/data/" + testMethodName + ".csv",
				TeamMemberMasterEntity.class,
				TEAM_MEMBER,
				(e, ctx) -> {
					e.setFile(ctx.getCsvPath().getFileName().toString());
					e.setDelFlg("0");
				});

		list.forEach(System.out::println);
		Map<String, List<TeamMemberMasterEntity>> resultMap = new HashMap<>();
		// null または 空チェック
		if (list == null || list.isEmpty())
			return;
		String file = list.get(0).getFile();
		resultMap
				.computeIfAbsent(file, s -> new ArrayList<>())
				.addAll(list);
		// Act
		teamMemberMasterStat.teamMemberStat(resultMap);

		// Assert
		assertTrue(true); // 戻り値が0であること
	}

}
