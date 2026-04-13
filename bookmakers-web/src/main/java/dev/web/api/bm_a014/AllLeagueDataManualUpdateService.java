package dev.web.api.bm_a014;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.AllLeagueDataManualUpdateRepository;
import dev.web.repository.master.AllLeagueDataManualUpdateRepository.TeamSubLeagueRow;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllLeagueDataManualUpdateService {

	private final AllLeagueDataManualUpdateRepository repository;

	@Transactional(readOnly = true)
	public List<TeamSubLeagueRow> findBoardItems() {
		return repository.findAllBoardItems();
	}

	@Transactional
	public void save(AllLeagueDataManualUpdateRequest request) {
		repository.updateAllSubLeague(request);
	}

	@Transactional(readOnly = true)
	public List<AllLeagueDataManualUpdateRepository.CountryLeagueTargetRow> findTargets() {
		return repository.findAllCountryLeagueTargets();
	}

}
