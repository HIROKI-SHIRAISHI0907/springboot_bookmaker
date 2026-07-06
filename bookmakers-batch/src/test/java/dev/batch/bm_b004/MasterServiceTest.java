package dev.batch.bm_b004;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.HashedMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import dev.batch.common.BatchExecutionHistoryService;
import dev.batch.interf.JobExecControlIF;
import dev.batch.repository.master.BatchJobExecRepository;
import dev.batch.repository.master.InitialMasterCsvRepository;
import dev.common.config.PathConfig;
import dev.common.constant.MasterNameConstant;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.entity.InitialReadingMasterCsvEntity;
import dev.common.getinfo.GetTeamInfo;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = {
		"classpath:schema-bm.sql",
		"classpath:data-bm.sql"
}, config = @SqlConfig(dataSource = "bmDataSource", transactionManager = "bmTxManager", encoding = "UTF-8"))
@Sql(scripts = {
		"classpath:schema-master.sql",
		"classpath:data-master.sql"
}, config = @SqlConfig(dataSource = "masterDataSource", transactionManager = "masterTxManager", encoding = "UTF-8"))
public class MasterServiceTest {

	@Autowired
	private CountryLeagueMasterBatch masterMasterBatch;

	@MockBean(name = "batchJobExecControl")
	private JobExecControlIF jobExecControlIF;

	@MockBean
	private BatchJobExecRepository batchJobExecRepository;

	@MockBean
	private BatchExecutionHistoryService batchExecutionHistoryService;

	@MockBean
	private GetTeamInfo getTeamMasterInfo;

	@MockBean
	private PathConfig pathConfig;

	@Autowired
	private InitialMasterCsvRepository initialMasterCsvRepository;

	@Test
	void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
		String country = "日本";
		String league = "J2・J3リーグ";
		String team = "カターレ富山";
		List<CountryLeagueMasterEntity> entityList = new ArrayList<CountryLeagueMasterEntity>();
		CountryLeagueMasterEntity entity = new CountryLeagueMasterEntity();
		entity.setCountry(country);
		entity.setLeague(league);
		entity.setTeam(team);
		entityList.add(entity);
		Map<String, List<CountryLeagueMasterEntity>> map = new HashedMap<String, List<CountryLeagueMasterEntity>>();
		map.put("file", entityList);
		when(getTeamMasterInfo.getData()).thenReturn(map);
		when(jobExecControlIF.jobStart(anyString(), anyString())).thenReturn(true);
		// Act
		int result = masterMasterBatch.execute(false);

		List<InitialReadingMasterCsvEntity> results = this.initialMasterCsvRepository
				.select(MasterNameConstant.COUNTRY_LEAGUE_MASTER);
		assertEquals(country, results.get(0).getCountry());
		assertEquals(league, results.get(0).getLeague());
		assertEquals(MasterNameConstant.COUNTRY_LEAGUE_MASTER, results.get(0).getMasterName());
		assertEquals("0", results.get(0).getInitialFlg());
		// Assert
		assertEquals(1, results.size()); // 戻り値が1であること
		assertEquals(0, result); // 戻り値が0であること
	}

}
