package dev.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w099.BatchExecuteRequestDTO;
import dev.web.api.bm_w099.BatchExecuteResponseDTO;
import dev.web.batch.WebBatchService;

@RestController
@RequestMapping("/admin/batch")
public class BatchExecuteController {

	@Autowired
	private WebBatchService runner;

	@GetMapping("/codes")
	public ResponseEntity<String[]> codes() {
		return ResponseEntity.ok(new String[] {
				"B001",
				"B002",
				"B003",
				"B004",
				"B005",
				"B005",
				"B006",
				"B007",
				"B008",
				"B009"
		});
	}

	@PostMapping("/execute")
	public ResponseEntity<BatchExecuteResponseDTO> execute(@RequestBody BatchExecuteRequestDTO request) {
		validate(request);
		return ResponseEntity.ok(this.runner.execute(request));
	}

	private void validate(BatchExecuteRequestDTO request) {
		if (request == null) {
			throw new IllegalArgumentException("request is required");
		}
		if (request.getBatchCode() == null || request.getBatchCode().isBlank()) {
			throw new IllegalArgumentException("batchCode is required");
		}

		String code = request.getBatchCode().trim();

		// 単体OK：B001~B009
		if (!code.matches("^B00(1|2|3|4|5|6|7|8|9)$")) {
			throw new IllegalArgumentException("invalid batchCode: " + code);
		}
	}
}
