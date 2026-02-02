package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;

/**
 * ヘルスチェック用
 * @author shiraishitoshio
 *
 */
@RestController
public class HealthController {

	/**
	 * ヘルスチェック起動用
	 */
	@PostConstruct
	public void init() {
	  System.out.println("### HealthController loaded ###");
	}


	@GetMapping("/healthz")
	public ResponseEntity<String> healthz() {
		return ResponseEntity.ok("ok");
	}
}
