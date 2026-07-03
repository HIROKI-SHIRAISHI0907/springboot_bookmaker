package dev.web.api.bm_a022;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.web.repository.master.LanguageRepository;

@Component
public class CountryLanguageResolver {

	@Autowired
	private LanguageRepository languageRepository;

    public String resolveTargetLanguageCode(String countryJa) {
        if (countryJa == null || countryJa.isBlank()) {
            return "en";
        }

        String v = countryJa.trim();
        LanguageSearchCondition condition = new LanguageSearchCondition();
        condition.setCountry(v);
        try {
        	return languageRepository.search(condition).get(0).getLangCd();
		} catch (Exception e) {
			 return "en";
		}

    }
}
