package dev.web.api.bm_w020;

import java.io.IOException;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExportCsvService {

	private final ExportCsv exportCsv;

	public ExportCsvResponse createCsv() throws IOException {
	    exportCsv.execute();

	    ExportCsvResponse res = new ExportCsvResponse();
	    res.setResponseCode("200");
	    res.setMessage("CSV作成処理が成功しました。");
	    return res;
	  }

}
