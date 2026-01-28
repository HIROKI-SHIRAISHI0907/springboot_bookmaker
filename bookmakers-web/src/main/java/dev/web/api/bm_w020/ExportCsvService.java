package dev.web.api.bm_w020;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExportCsvService {

	private final ExportCsv exportCsv;

	@Transactional
	public ExportCsvResponse createCsv() {
		ExportCsvResponse res = new ExportCsvResponse();

		try {
			this.exportCsv.execute();
			res.setResponseCode("200");
			res.setMessage("CSV作成処理が成功しました。");
			return res;

		} catch (Exception e) {
			res.setResponseCode("500");
			res.setMessage("システムエラーが発生しました。");
			return res;
		}
	}

}
