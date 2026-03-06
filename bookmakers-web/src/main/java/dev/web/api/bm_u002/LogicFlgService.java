package dev.web.api.bm_u002;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.common.constant.LogicFlgConst;
import dev.common.util.TableUtil;
import dev.web.repository.master.LogicFlgRepository;
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

		// 更新用全テーブル
		this.countryList = TableUtil.getCountryList();
		this.categoryList = TableUtil.getCategoryList();

		// 更新(データが空の場合は制限をかけていないため全てフラグ0にする)
		res = logicFlgAllUpdate(LogicFlgConst.LOGIC_FLG_1, res);
		if (!"200".equals(res.getResponseCode())) {
			return res;
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
