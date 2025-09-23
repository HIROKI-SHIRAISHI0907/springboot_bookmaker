package dev.application.analyze.bm_m000;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m001.OriginDBService;
import dev.application.analyze.bm_m001.OriginStat;
import dev.application.analyze.interf.OriginIF;
import dev.common.entity.BookDataEntity;
import dev.common.entity.DataEntity;
import dev.common.getstatinfo.GetOriginInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M000統計分析ロジック(csvデータから逆投入)
 * @author shiraishitoshio
 *
 */
@Component
public class OriginCsvService implements OriginIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = OriginCsvService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = OriginCsvService.class.getSimpleName();

	/**
	 * CSVデータ取得管理クラス
	 */
	@Autowired
	private GetCsvInfo getCsvInfo;

	/**
	 * 起源データ取得管理クラス
	 */
	@Autowired
	private GetOriginInfo getOriginInfo;

	/** BM_M001起源データ登録ロジック */
	@Autowired
	private OriginStat originStat;

	/** Mapper */
	@Autowired
	private BookToDataMapper mapper;

	/** DBサービス */
	@Autowired
	private OriginDBService originDBService;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int execute() throws Exception {
		final String METHOD_NAME = "execute";

		// ログ出力
		this.loggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		// 既成CSVデータ情報を取得
		Map<String, List<BookDataEntity>> getStatMap = this.getCsvInfo.getDataByFile("0", null);
		for (Map.Entry<String, List<BookDataEntity>> datas : getStatMap.entrySet()) {
			List<BookDataEntity> datasEntities = datas.getValue();
			List<DataEntity> insertList = new ArrayList<DataEntity>();
			String files = null;
			for (BookDataEntity insEntity : datasEntities) {
				DataEntity entity = this.mapper.toData(insEntity);
				// 設定
				entity.setSeq(null);
				files = entity.getFile();
				DataEntity newEntry = entity;
				insertList.add(newEntry);
			}
			this.loggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, files + "::" + getStatMap.size() + ".csv");
			try {
				this.originDBService.insertInBatch(insertList);
			} catch (Exception e) {
				this.loggerComponent.createSystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						e.getMessage(),
						e.getCause());
			}
		}

		// 直近のCSVデータ情報を取得
		Map<String, List<DataEntity>> getOriginMap = this.getOriginInfo.getData();
		// BM_M001登録(Transactional)
		try {
			this.originStat.originStat(getOriginMap);
		} catch (Exception e) {
			this.loggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					e.getMessage(),
					e.getCause());
		}

		// endLog
		this.loggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		return 0;
	}

}
