package dev.batch.bm_b008.service;

// 2026-11-20 bookmakers-app（dev.application.main.service.DataCategoryDTO）から移動（B008 を bookmakers-batch で動かすため）

import lombok.Data;

@Data
public class DataCategoryDTO {

	private String dataCategory;

	private String times;

}
