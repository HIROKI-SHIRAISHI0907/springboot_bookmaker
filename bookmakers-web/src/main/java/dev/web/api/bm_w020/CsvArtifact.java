package dev.web.api.bm_w020;

import java.util.List;

import dev.common.entity.DataEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** CSV作成用の小さなクラス（Java 8+対応） */
@Getter
@AllArgsConstructor
class CsvArtifact {

	/** ファイルパス */
	private final String filePath;

	/** コンテキスト */
	private final List<DataEntity> content;

}
