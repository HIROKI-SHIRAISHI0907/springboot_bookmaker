package dev.web.api.bm_w017;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.CountryLeagueMasterWebRepository;
import lombok.RequiredArgsConstructor;

/**
 * CountryLeagueFindAllService
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class CountryLeagueFindAllService {

    private final CountryLeagueMasterWebRepository repo;

    @Transactional(readOnly = true)
    public List<CountryLeagueDTO> findAll() {
        return repo.findAll();
    }
}
