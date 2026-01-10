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
import dev.batch.repository.master.TeamMemberMasterRepository;
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

	@Autowired
	private TeamMemberMasterRepository teamMemberMasterRepository;

	/**
	 * 試験データ登録確認
	 * @param testInfo
	 * @throws Exception
	 */
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

		List<TeamMemberMasterEntity> result = teamMemberMasterRepository.findData();
		assertEquals(list.size() - 2, result.size());
	}

	/**
	 * 各データ更新、新規データ登録確認
	 * @param testInfo
	 * @throws Exception
	 */
	@Test
	void execute_TC_TS_002(TestInfo testInfo) throws Exception {
		String testMethodName = testInfo.getTestMethod()
				.map(m -> m.getName())
				.orElse("unknown");

		List<TeamMemberMasterEntity> list1 = CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b002/data/" + testMethodName + "_before.csv",
				TeamMemberMasterEntity.class,
				TEAM_MEMBER,
				(e, ctx) -> {
					e.setFile(ctx.getCsvPath().getFileName().toString());
					e.setDelFlg("0");
				});

		list1.forEach(System.out::println);
		Map<String, List<TeamMemberMasterEntity>> resultMap1 = new HashMap<>();
		// null または 空チェック
		if (list1 == null || list1.isEmpty())
			return;
		String file1 = list1.get(0).getFile();
		resultMap1
				.computeIfAbsent(file1, s -> new ArrayList<>())
				.addAll(list1);
		// Act
		teamMemberMasterStat.teamMemberStat(resultMap1);

		List<TeamMemberMasterEntity> result1 = teamMemberMasterRepository.findData();
		assertEquals(list1.size() - 2, result1.size());

		List<TeamMemberMasterEntity> list2 = CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b002/data/" + testMethodName + "_after.csv",
				TeamMemberMasterEntity.class,
				TEAM_MEMBER,
				(e, ctx) -> {
					e.setFile(ctx.getCsvPath().getFileName().toString());
					e.setDelFlg("0");
				});

		list2.forEach(System.out::println);
		Map<String, List<TeamMemberMasterEntity>> resultMap2 = new HashMap<>();
		// null または 空チェック
		if (list2 == null || list2.isEmpty())
			return;
		String file2 = list2.get(0).getFile();
		resultMap2
				.computeIfAbsent(file2, s -> new ArrayList<>())
				.addAll(list2);
		// Act
		teamMemberMasterStat.teamMemberStat(resultMap2);

		// 更新確認
		List<TeamMemberMasterEntity> result = teamMemberMasterRepository.findData();
		for (TeamMemberMasterEntity entity : result) {
			switch (entity.getMember()) {
			case "髙橋 耕平": {
				assertion("J3 リーグ", "ヴァンラーレ八戸", entity.getMember(),
						"26", "0", "N/A", "N/A",
						"https://static.flashscore.com/res/image/data/SviHZcCa-WMKwOgLC.png", "N/A",
						"ヴァンラーレ八戸", entity);
				break;
			}
			case "中野 誠也": {
				assertion("J3 リーグ", "ヴァンラーレ八戸", entity.getMember(),
						"99", "ヴァンラ=2", "", "大宮アルディージャ,サガン鳥栖",
						"https://static.flashscore.com/res/image/data/p0JgWjFa-vij8t2Hr.png", "",
						"ヴァンラーレ八戸", entity);
				break;
			}
			case "パク イルギュ": {
				assertion("J1 リーグ", "横浜F・マリノス", entity.getMember(),
						"19", "横浜F・=0", "€273k", "N/A",
						"https://static.flashscore.com/res/image/data/4KtGfECr-Wnrio1I1.png", "半月板損傷",
						"横浜F・マリノス", entity);
				break;
			}
			case "デン トーマス": {
				assertion("J1 リーグ", "アルビレックス新潟", entity.getMember(),
						"44", "横浜F・=0,アルビレ=0", "€515k→€1.2k", "N/A",
						"https://static.flashscore.com/res/image/data/rywgsieM-CA5BfAS2.png", "",
						"横浜F・マリノス,アルビレックス新潟", entity);
				break;
			}
			case "松原 健": {
				assertion("J1 リーグ", "横浜F・マリノス", entity.getMember(),
						"27", "横浜F・=0,浦和レッ=3", "€343k", "N/A",
						"https://static.flashscore.com/res/image/data/MuzPkAyB-GlwzJZf0.png", "",
						"横浜F・マリノス,浦和レッズ", entity);
				break;
			}
			case "植中 朝日": {
				assertion("J1 リーグ", "横浜F・マリノス", entity.getMember(),
						"14", "横浜F・=1", "€723k→€795k", "N/A",
						"https://static.flashscore.com/res/image/data/0IU0b8FG-GSLIhFV7.png", "",
						"横浜F・マリノス", entity);
				break;
			}
			case "谷村 海那": {
				assertion("J2 リーグ", "いわきFC", entity.getMember(),
						"48(横浜F・),51", "横浜F・=0,いわきF=10", "€855k", "N/A",
						"https://static.flashscore.com/res/image/data/6oW3CYZA-ERZaaY0A.png", "",
						"横浜F・マリノス,いわきFC", entity);
				break;
			}
			default:
				continue;
			}
		}
		assertEquals(25, result.size());

	}

	/**
	 * アサーション
	 * @param entity
	 */
	private static void assertion(
			String league,
			String team,
			String member,
			String jersey,
			String score,
			String marketValue,
			String loanBelong,
			String facePicPath,
			String outOfOrder,
			String belongList,
			TeamMemberMasterEntity entity) {
		System.out.println("[START] 日本: " + league +": " + member);
		assertEquals("[COUNTRY]: "+"日本" , "[COUNTRY]: "+entity.getCountry());
		assertEquals("[LEAGUE]: "+league , "[LEAGUE]: "+entity.getLeague());
		assertEquals("[MEMBER]: "+member , "[MEMBER]: "+entity.getMember());
		assertEquals("[JERSEY]: "+jersey , "[JERSEY]: "+entity.getJersey());
		assertEquals("[SCORE]: "+score , "[SCORE]: "+entity.getScore());
		assertEquals("[MARKETVALUE]: "+marketValue , "[MARKETVALUE]: "+entity.getMarketValue());
		assertEquals("[LOANBELONG]: "+loanBelong , "[LOANBELONG]: "+entity.getLoanBelong());
		assertEquals("[FACEPATH]: "+facePicPath , "[FACEPATH]: "+entity.getFacePicPath());
		assertEquals("[INJURY]: "+outOfOrder , "[INJURY]: "+entity.getInjury());
		assertEquals("[BELONGLIST]: "+belongList , "[BELONGLIST]: "+entity.getBelongList());
		System.out.println("[END] 日本: " + league +": " + member);
	}

}
