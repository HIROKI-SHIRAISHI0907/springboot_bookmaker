package dev.web.api.bm_w017;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import dev.web.api.bm_a002.CountryLeagueSeasonDTO;
import dev.web.repository.master.CountryLeagueSeasonMasterWebRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StatCountryLeagueOptionsService {

	private final CountryLeagueSeasonMasterWebRepository repository;

	public StatCountryLeagueOptionsResponseWrapper getOptions() {
		List<CountryLeagueSeasonDTO> rows = repository.findAll();

		Map<String, TreeSet<String>> grouped = new TreeMap<>();

		for (CountryLeagueSeasonDTO dto : rows) {
			String country = trim(dto.getCountry());
			String league = trim(dto.getLeague());
			String delFlg = trim(dto.getDelFlg());

			if (country.isEmpty() || league.isEmpty()) {
				continue;
			}
			// 削除フラグが有効はスキップ
			if ("1".equals(delFlg)) {
				continue;
			}

			grouped.computeIfAbsent(country, k -> new TreeSet<>()).add(league);
		}

		List<StatCountryLeagueOptionWrapper> countries = new ArrayList<>();
		grouped.entrySet().stream()
				.sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
				.forEach(e -> countries.add(
						StatCountryLeagueOptionWrapper.builder()
								.country(e.getKey())
								.leagues(new ArrayList<>(e.getValue()))
								.build()));

		return StatCountryLeagueOptionsResponseWrapper.builder()
				.countries(countries)
				.build();
	}

	private static String trim(String s) {
		return s == null ? "" : s.trim();
	}
}
