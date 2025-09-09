package dev.mng.analyze.bm_c001;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.common.logger.ManageLoggerComponent;
import dev.mng.analyze.interf.CsvEntityIF;
import dev.mng.domain.repository.StatSizeFinalizeMasterRepository;
import dev.mng.dto.CsvCommonInputDTO;
import dev.mng.dto.SubInput;

/**
 * BM_C001CSVロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class StatSizeFinalizeMasterCsv implements CsvEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = StatSizeFinalizeMasterCsv.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = StatSizeFinalizeMasterCsv.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_C001_STAT_SIZE_FINALIZE_CSV";

	/** StatSizeFinalizeMasterRepositoryクラス */
	@Autowired
	private StatSizeFinalizeMasterRepository statSizeFinalizeMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcCsv(CsvCommonInputDTO input) {
		final String METHOD_NAME = "calcCsv";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// リスト
		List<SubInput> list = input.getSubList();
		for (SubInput sub : list) {
			StatSizeFinalizeMasterCsvEntity entity = new StatSizeFinalizeMasterCsvEntity();
			entity.setOptions(sub.getOptions());
			entity.setFlg(sub.getFlg());
			int result = this.statSizeFinalizeMasterRepository.insert(entity);
			if (result != 1) {
				String messageCd = "新規登録エラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCd,
						null);
			}
			String messageCd = "登録件数";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, "BM_C001 登録件数: 1件");
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

}
