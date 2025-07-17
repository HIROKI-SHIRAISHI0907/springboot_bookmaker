package dev.application.analyze.bm_m002;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.ConditionResultDataRepository;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadStat;
import jakarta.annotation.PostConstruct;

/**
 * condition_result_dataのbeanロジック
 * @author shiraishitoshio
 *
 */
@Component
public class BmM002ConditionResultDataBean {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BmM002ConditionResultDataBean.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BmM002ConditionResultDataBean.class.getSimpleName();

	/** パス */
	@Value("${bmbusiness.hashAlgorithm:SHA-256}")
	private String hashAlgorithm = "SHA-256";

	/** パス */
	@Value("${bmbusiness.aftercopypath:/Users/shiraishitoshio/bookmaker/conditiondata/conditiondata.csv}")
	private String findPath = "/Users/shiraishitoshio/bookmaker/conditiondata/conditiondata.csv";

	/** 統計データ読み取りクラス */
	@Autowired
	private ReadStat readStat;

	/** condition_result_dataレポジトリクラス */
	@Autowired
	private ConditionResultDataRepository conditionResultDataRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/** 更新フラグ */
	private boolean updFlg;

	/** ハッシュ */
	private String hash;

	/** 件数取得 */
	private String[] conditionCountList;

	/** 初期化 */
	@PostConstruct
	public void init() {
		final String METHOD_NAME = "init";
		// hashデータを取得
		String hash = null;
		try {
			String conditionData = getConditionData();
			if (conditionData == null) {
				throw new Exception("conditionData: null");
			}
			hash = extractHash(conditionData);
			if (hash == null) {
				this.conditionCountList = new String[] {
				        "0","0","0","0","0","0","0","0","0","0","0"
				    };
				this.updFlg = false;
			    return;
			}
		} catch (Exception e) {
			String fillChar = (e.getMessage() != null) ? e.getMessage() : null;
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e, fillChar);
			this.loggerComponent.createBusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					null,
					null);
		}

		// hashを条件にDBから値を取得(1件の想定)
		List<ConditionResultDataEntity> resultDataEntities =
				this.conditionResultDataRepository.findByHash(hash);

		// リストに設定
		if (!resultDataEntities.isEmpty()) {
		    ConditionResultDataEntity entity = resultDataEntities.get(0);
		    this.conditionCountList = new String[] {
		        entity.getMailTargetCount(),
		        entity.getMailAnonymousTargetCount(),
		        entity.getMailTargetSuccessCount(),
		        entity.getMailTargetFailCount(),
		        entity.getExMailTargetToNoResultCount(),
		        entity.getExNoFinDataToNoResultCount(),
		        entity.getGoalDelete(),
		        entity.getAlterTargetMailAnonymous(),
		        entity.getAlterTargetMailFail(),
		        entity.getNoResultCount(),
		        entity.getErrData()
		    };
		    this.updFlg = true;
		} else {
		    this.conditionCountList = new String[] {
		        "0","0","0","0","0","0","0","0","0","0","0"
		    };
		    this.updFlg = false;
		}

	}

	/**
	 * 条件分岐データをブックから読み取る
	 * @return conditionData
	 */
	private String getConditionData() throws Exception {
		// ファイル内のデータ取得
		String conditionData = this.readStat.getConditionDataFileBody(this.findPath);
		if (conditionData != null) {
			return conditionData;
		}
		return null;
	}

	/**
	 * 条件分岐データからハッシュ値を導出する
	 * @param conditionData 条件分岐データ
	 * @return new String(Base64.getEncoder().encodeToString(cipherBytes));
	 * @throws NoSuchAlgorithmException
	 */
	private String extractHash(String conditionData) throws NoSuchAlgorithmException {
		byte[] cipherBytes = null;
		try {
			MessageDigest md = MessageDigest.getInstance(this.hashAlgorithm);
			cipherBytes = md.digest(conditionData.getBytes());
		} catch (NoSuchAlgorithmException e) {
			throw e;
		}
		return Base64.getEncoder().encodeToString(cipherBytes);
	}

	/**
	 * ハッシュ
	 * @return hash
	 */
	public String getHash() {
		return hash;
	}

	/**
	 * 条件件数リスト
	 * @return conditionCountList
	 */
	public String[] getConditionCountList() {
		return conditionCountList;
	}

	/**
	 * 更新フラグ
	 * @return conditionCountList
	 */
	public boolean getUpdFlg() {
		return updFlg;
	}

}
