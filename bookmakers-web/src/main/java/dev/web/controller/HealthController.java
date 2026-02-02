package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class HealthController {
	@GetMapping("/healthz")
	public ResponseEntity<String> healthz() {
		return ResponseEntity.ok("ok");
	}
}
