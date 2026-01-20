package dev.web.api.bm_w019;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.CountryLeagueSeasonMasterWebRepository;
import lombok.RequiredArgsConstructor;

/**
 * CountryLeagueSeasonFindAllService
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class CountryLeagueSeasonFindAllService {

    private final CountryLeagueSeasonMasterWebRepository repo;

    @Transactional(readOnly = true)
    public List<CountryLeagueSeasonDTO> findAll() {
        return repo.findAll();
    }
}
