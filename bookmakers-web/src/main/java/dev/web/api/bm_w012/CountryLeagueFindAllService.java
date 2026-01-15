package dev.web.api.bm_w012;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.CountryLeagueMasterRepository;
import lombok.RequiredArgsConstructor;

/**
 * CountryLeagueFindAllService
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class CountryLeagueFindAllService {

    private final CountryLeagueMasterRepository repo;

    @Transactional(readOnly = true)
    public List<CountryLeagueDTO> findAll() {
        return repo.findAll();
    }
}
