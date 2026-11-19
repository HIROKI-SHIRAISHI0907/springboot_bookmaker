package dev.batch.bm_b008.service;

// 2026-11-20 bookmakers-app（dev.application.main.service.SeqKeyDTO）から移動（B008 を bookmakers-batch で動かすため）

import lombok.Data;

@Data
public class SeqKeyDTO {

	private String seqKey;

	private String matchId;

	private String times;

}
