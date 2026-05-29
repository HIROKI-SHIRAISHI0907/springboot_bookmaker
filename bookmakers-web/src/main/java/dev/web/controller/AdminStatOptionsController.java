package dev.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w017.StatCountryLeagueOptionsResponseWrapper;
import dev.web.api.bm_w017.StatCountryLeagueOptionsService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/stat")
@RequiredArgsConstructor
public class AdminStatOptionsController {

	private final StatCountryLeagueOptionsService statCountryLeagueOptionsService;

	@GetMapping("/options")
	public StatCountryLeagueOptionsResponseWrapper getOptions() {
		return statCountryLeagueOptionsService.getOptions();
	}
}
