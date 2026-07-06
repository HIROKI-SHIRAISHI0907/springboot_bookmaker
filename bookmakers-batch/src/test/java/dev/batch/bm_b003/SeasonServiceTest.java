package dev.batch.bm_b003;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

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
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.entity.InitialReadingMasterCsvEntity;
import dev.common.getinfo.GetSeasonInfo;


@SpringBootTest
@ActiveProfiles("test")
@Sql(
    scripts = {
        "classpath:schema-bm.sql",
        "classpath:data-bm.sql"
    },
    config = @SqlConfig(
        dataSource = "bmDataSource",
        transactionManager = "bmTxManager",
        encoding = "UTF-8"
    )
)
@Sql(
    scripts = {
        "classpath:schema-master.sql",
        "classpath:data-master.sql"
    },
    config = @SqlConfig(
        dataSource = "masterDataSource",
        transactionManager = "masterTxManager",
        encoding = "UTF-8"
    )
)
public class SeasonServiceTest {

	@Autowired
    private CountryLeagueSeasonMasterBatch seasonMasterBatch;

	@MockBean(name = "batchJobExecControl")
	private JobExecControlIF jobExecControlIF;

	@MockBean
    private BatchJobExecRepository batchJobExecRepository;

	@MockBean
	private BatchExecutionHistoryService batchExecutionHistoryService;

	@MockBean
	private GetSeasonInfo getSeasonInfo;

	@MockBean
	private PathConfig pathConfig;

	@Autowired
    private InitialMasterCsvRepository initialMasterCsvRepository;

    @Test
    void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
    	String country = "日本";
    	String league = "J2・J3リーグ";
    	String seasonYear = "2026";
    	String startSeasonDate = "4.2.";
    	String endSeasonDate = "4.5.";
    	String round = "16";
    	List<CountryLeagueSeasonMasterEntity> entityList = new ArrayList<CountryLeagueSeasonMasterEntity>();
    	CountryLeagueSeasonMasterEntity entity = new CountryLeagueSeasonMasterEntity();
    	entity.setCountry(country);
    	entity.setLeague(league);
    	entity.setSeasonYear(seasonYear);
    	entity.setStartSeasonDate(startSeasonDate);
    	entity.setEndSeasonDate(endSeasonDate);
    	entity.setRound(round);
    	entityList.add(entity);
    	when(getSeasonInfo.getData()).thenReturn(entityList);
    	when(jobExecControlIF.jobStart(anyString(), anyString())).thenReturn(true);

        // Act
        int result = seasonMasterBatch.execute(false);

        List<InitialReadingMasterCsvEntity> results =
        this.initialMasterCsvRepository.select(MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER);
        assertEquals(country, results.get(0).getCountry());
        assertEquals(league, results.get(0).getLeague());
        assertEquals(MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER, results.get(0).getMasterName());
        assertEquals("0", results.get(0).getInitialFlg());
        // Assert
        assertEquals(1, results.size()); // 戻り値が1であること
        assertEquals(0, result); // 戻り値が0であること
    }

}
