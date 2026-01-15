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

import dev.web.api.bm_w015.ExportCsv;
import dev.web.api.bm_w015.ReaderCurrentCsvInfoBean;


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
	private StatSizeFinalizeService sizeFinalizeService;

	/**
	 * 処理速度実験
	 */
	@Test
	void test_calcStat_shouldUpdateCorrectly_memory() {
		// Act

		StatSizeFinalizeRequest sizeFinalizeRequest = new StatSizeFinalizeRequest();
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
		sizeFinalizeRequest.setSubList(list);
		this.sizeFinalizeService.setStatFinalize(sizeFinalizeRequest);
	}

	/**
	 * 処理速度実験
	 */
	@Test
	void test_calcStat_shouldUpdateCorrectly_memory2() {
		// Act

		StatSizeFinalizeRequest sizeFinalizeRequest = new StatSizeFinalizeRequest();
		List<SubInput> list = new ArrayList<SubInput>();
		SubInput subInput = new SubInput();
		subInput.setOptionNum("1");
		subInput.setOptions("0-0");
		subInput.setFlg("0");
		list.add(subInput);
		sizeFinalizeRequest.setSubList(list);
		this.sizeFinalizeService.setStatFinalize(sizeFinalizeRequest);
	}


}
