package dev.web.api.bm_w012;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.CountryLeagueMasterWebRepository;
import lombok.RequiredArgsConstructor;

/**
 * CountryLeagueSearchService
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class CountryLeagueSearchService {

    private final CountryLeagueMasterWebRepository repo;

    @Transactional(readOnly = true)
    public List<CountryLeagueDTO> search(CountryLeagueSearchCondition cond) {
        return repo.search(cond);
    }
}
