package dev.batch.bm_b001;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.CountryLeagueSeasonMasterBatchRepository;
import dev.common.entity.CountryLeagueSeasonMasterEntity;

@Component
public class TestDataInserter {

    private final CountryLeagueSeasonMasterBatchRepository repo;

    public TestDataInserter(CountryLeagueSeasonMasterBatchRepository repo) {
        this.repo = repo;
    }

    @Transactional(transactionManager = "masterTxManager")
    public void insertSeed() {
        CountryLeagueSeasonMasterEntity japan = new CountryLeagueSeasonMasterEntity();
        japan.setCountry("日本");
        japan.setLeague("J1 リーグ");
        japan.setStartSeasonDate("2025-02-28T09:00:00+09:00");
        japan.setEndSeasonDate("2025-12-19T09:00:00+09:00"); // expired対象
        japan.setRound("1");
        japan.setValidFlg("0");

        CountryLeagueSeasonMasterEntity england = new CountryLeagueSeasonMasterEntity();
        england.setCountry("イングランド");
        england.setLeague("プレミアリーグ");
        england.setStartSeasonDate("2025-02-28T09:00:00+09:00");
        england.setEndSeasonDate("2025-12-20T09:00:00+09:00");
        england.setRound("1");
        england.setValidFlg("0");

        repo.insert(japan);
        repo.insert(england);
    }
}
