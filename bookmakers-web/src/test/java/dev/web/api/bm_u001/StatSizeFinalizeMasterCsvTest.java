package dev.web.api.bm_u001;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import dev.mng.csvmng.ExportCsv;
import dev.mng.csvmng.ReaderCurrentCsvInfoBean;
import dev.mng.dto.CsvTargetCommonInputDTO;
import dev.mng.dto.SubInput;
import dev.web.api.bm_w015.StatSizeFinalizeMasterCsv;

/**
 * BM_C001CSVロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
//★★ これを追加：自動で H2 に差し替えさせない
@AutoConfigureTestDatabase(replace = Replace.NONE)
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

		StatSizeFinalizeRequest csvCommonInputDTO = new StatSizeFinalizeRequest();
		List<SubInput> list = new ArrayList<SubInput>();
		SubInput subInput = new SubInput();
		subInput.setOptionNum("1");
		subInput.setOptions("4-0");
		subInput.setFlg("1");
		list.add(subInput);
		subInput = new SubInput();
		subInput.setOptionNum("1");
		subInput.setOptions("2-0");
		subInput.setFlg("1");
		list.add(subInput);
		subInput = new SubInput();
		subInput.setOptionNum("1");
		subInput.setOptions("1-0");
		subInput.setFlg("1");
		list.add(subInput);
		subInput = new SubInput();
		subInput.setOptionNum("1");
		subInput.setOptions("0-0");
		subInput.setFlg("0");
		list.add(subInput);
		csvCommonInputDTO.setSubList(list);
		this.statSizeFinalizeMasterCsv.calcCsv(csvCommonInputDTO);
	}

	/**
	 * 処理速度実験
	 */
	@Test
	void test_calcStat_shouldUpdateCorrectly_memory2() {
		// Act

		StatSizeFinalizeRequest csvCommonInputDTO = new StatSizeFinalizeRequest();
		List<SubInput> list = new ArrayList<SubInput>();
		SubInput subInput = new SubInput();
		subInput.setOptionNum("1");
		subInput.setOptions("0-0");
		subInput.setFlg("0");
		list.add(subInput);
		csvCommonInputDTO.setSubList(list);
		this.statSizeFinalizeMasterCsv.calcCsv(csvCommonInputDTO);
	}


}
