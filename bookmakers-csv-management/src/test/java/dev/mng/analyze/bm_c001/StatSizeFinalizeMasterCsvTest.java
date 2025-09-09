package dev.mng.analyze.bm_c001;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import dev.mng.csvmng.ExportCsv;
import dev.mng.csvmng.ReaderCurrentCsvInfoBean;
import dev.mng.dto.CsvCommonInputDTO;
import dev.mng.dto.SubInput;

/**
 * BM_C001CSVロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
public class StatSizeFinalizeMasterCsvTest {

	@MockBean
	private ExportCsv exportCsv;

	@Autowired(required = false)
	private ReaderCurrentCsvInfoBean readerCurrentCsvInfoBean;

	@Autowired
	private StatSizeFinalizeMasterCsv statSizeFinalizeMasterCsv;

	/**
	 * 処理速度実験
	 */
	@Test
	void test_calcStat_shouldUpdateCorrectly_memory() {
		// Act

		CsvCommonInputDTO csvCommonInputDTO = new CsvCommonInputDTO();
		List<SubInput> list = new ArrayList<SubInput>();
		for (int i = 0; i < 20; i++) {
			SubInput subInput = new SubInput();
			subInput.setOptions("OPTION-" + i);
			subInput.setFlg("1");
			list.add(subInput);
		}
		csvCommonInputDTO.setSubList(list);
		this.statSizeFinalizeMasterCsv.calcCsv(csvCommonInputDTO);
	}

}
