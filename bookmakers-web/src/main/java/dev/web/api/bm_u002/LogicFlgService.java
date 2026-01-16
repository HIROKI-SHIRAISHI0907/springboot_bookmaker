package dev.web.api.bm_u002;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.common.constant.LogicFlgConst;
import dev.common.util.TableUtil;
import dev.web.api.bm_w015.ConditionData;
import dev.web.repository.master.LogicFlgRepository;
import dev.web.util.CsvArtifactHelper;
import lombok.RequiredArgsConstructor;

/**
 * LogicFlgService
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class LogicFlgService {

	/**
	 * 論理削除レポジトリ
	 */
	@Autowired
	private LogicFlgRepository logicFlgRepository;

	/**
	 * CsvArtifactHelperクラス
	 */
	@Autowired
	private CsvArtifactHelper CsvArtifactHelper;

	/** 国リーグリスト */
	private List<String> countryList;

	/** カテゴリリスト */
	private List<String> categoryList;

	/**
	 * 実行メソッド
	 */
	public LogicFlgResponse execute() throws Exception {
		LogicFlgResponse res = new LogicFlgResponse();
		res.setResponseCode("200");
		res.setMessage("処理が成功しました。");

		// 設定統計データの取得
		List<ConditionData> stat = setUpdateStat();
		if (stat == null) {
			res.setResponseCode("404");
			res.setMessage("処理が失敗しました。");
			return res;
		}

		// 更新用全テーブル
		this.countryList = TableUtil.getCountryList();
		this.categoryList = TableUtil.getCategoryList();

		// 更新(データが空の場合は制限をかけていないため全てフラグ0にする)
		res = logicFlgAllUpdate(LogicFlgConst.LOGIC_FLG_1, res);
		if (!"200".equals(res.getResponseCode())) {
			return res;
		}
		if (!stat.isEmpty()) {
			for (ConditionData dto : stat) {
				String country = dto.getCountry();
				String league = dto.getLeague();
				res = logicFlgUpdate(country, league, LogicFlgConst.LOGIC_FLG_0, res);
				if (!"200".equals(res.getResponseCode())) {
					return res;
				}
			}
		} else {
			res = logicFlgAllUpdate(LogicFlgConst.LOGIC_FLG_0, res);
			if (!"200".equals(res.getResponseCode())) {
				return res;
			}
		}

		return res;
	}

	/**
	 * 統計データ
	 */
	private List<ConditionData> setUpdateStat() {
		// 設定した国、リーグ情報のみ適用させる
		List<ConditionData> returnList = this.CsvArtifactHelper.statCondition(null);
		return returnList;
	}

	/**
	 * 更新メソッド
	 */
	private synchronized LogicFlgResponse logicFlgUpdate(String country, String league,
			String flg, LogicFlgResponse res) {
		for (String table : this.countryList) {
			int result = this.logicFlgRepository.updateLogicFlgByCountryLeague(
					table, country, league, flg);
			if (result == 0) {
				res.setResponseCode("404");
				res.setMessage("処理が失敗しました。");
				return res;
			}
		}

		for (String table : this.categoryList) {
			int result = this.logicFlgRepository.updateLogicFlgByCategoryLike(
					table, country, league, flg);
			if (result == 0) {
				res.setResponseCode("404");
				res.setMessage("処理が失敗しました。");
				return res;
			}
		}
		return res;
	}

	/**
	 * 更新メソッド
	 */
	private synchronized LogicFlgResponse logicFlgAllUpdate(String flg, LogicFlgResponse res) {
		for (String table : this.countryList) {
			int result = this.logicFlgRepository.updateAllLogicFlg(
					table, flg);
			if (result == 0) {
				res.setResponseCode("404");
				res.setMessage("処理が失敗しました。");
				return res;
			}
		}
		return res;
	}

}
