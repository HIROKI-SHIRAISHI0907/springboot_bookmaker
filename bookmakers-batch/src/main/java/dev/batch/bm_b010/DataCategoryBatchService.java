package dev.batch.bm_b010;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.repository.bm.BookDataRepository;
import dev.batch.repository.master.CountryLeagueMasterBatchRepository;
import dev.batch.repository.master.FutureMasterRepository;
import dev.common.entity.CountryLeagueMasterEntity;

/**
 * data_category発番処理
 * @author shiraishitoshio
 *
 */
@Component
public class DataCategoryBatchService {

	private static final String ROUND = "ラウンド";

	@Autowired
	private BookDataRepository bookDataRepository;

	@Autowired
	private CountryLeagueMasterBatchRepository countryLeagueMasterBatchRepository;

	@Autowired
	private FutureMasterRepository futureMasterRepository;

	/**
	 * static_dataテーブルのdata_categoryを生成する。
	 *
	 * @param home 対象試合のホームチーム名
	 * @param away 対象試合のアウェーチーム名
	 * @param dataCategory 対象試合のホームチーム名
	 * @return 生成されたdataCategory（例: "日本: J1 リーグ - ラウンド 1"）
	 * @throws IllegalAccessException
	 */
	public synchronized String create(String home, String away, String dataCategory) throws IllegalAccessException {
		// dataCategoryがnullの場合は既存のデータをベースに羅列を生成
		// 同じhome/awayの組み合わせで既存の登録があるか確認
		List<DataCategoryDTO> existDto = bookDataRepository.findDataCategory(home, away);
		boolean sameTeamFlg = false;
		if (existDto != null && !existDto.isEmpty()) {
			sameTeamFlg = true;
		}

		// あるならこの中に「ラウンド」を含んだdata_categoryがあるか
		if (sameTeamFlg) {
			// あればそれをreturn(ラウンド名が含まれていない場合を考慮して全更新)
			String roundContainsDateCateory = searchCompleteDataCategoryChk(existDto);
			if (roundContainsDateCateory != null) {
				try {
					updateCompleteDataCategoryChk(home, away, roundContainsDateCateory);
					updateCompleteDataCategoryChkByFuture(home, away, roundContainsDateCateory);
				} catch (Exception e) {
					throw new IllegalAccessException("システムエラー");
				}
				return roundContainsDateCateory;
			}
		}

		// なければhome,awayをcountry_league_masterで調べて国およびリーグ名を取得する
		CountryLeagueMasterEntity entityHome = countryLeagueMasterBatchRepository.findCountryLeagueByTeam(home);
		CountryLeagueMasterEntity entityAway = countryLeagueMasterBatchRepository.findCountryLeagueByTeam(away);

		String country = null;
		String league = null;
		if (entityHome != null) {
			country = entityHome.getCountry();
			league = entityHome.getLeague();
		} else {
			if (entityAway != null) {
				country = entityAway.getCountry();
				league = entityAway.getLeague();
			}
		}

		if (country == null || league == null) return "XXX: YYY - " + ROUND + " 0";

		// 国とリーグを取得して連結
		StringBuilder connection = new StringBuilder(country + ": " + league);

		// future_masterから該当の予定チームを取得し「ラウンド」を確認する
		String containsRoundFuture = futureMasterRepository.findGameTeamCategoryByBothTeams(home, away);
		// 取得できない場合(未来マスタスクレイピングから撮り損ねているケース)
		if (containsRoundFuture == null) {
			// そのまま返す
			return connection.toString();
		}

		// 「ラウンド」の後ろの数字部分(X)を取得して連結
		Matcher roundMatcher = Pattern.compile(ROUND + "\\s*(\\d+)").matcher(containsRoundFuture);
		if (roundMatcher.find()) {
			String round = roundMatcher.group(1);
			connection.append(" - ").append(ROUND + " " + round);
		}

		try {
			updateCompleteDataCategoryChk(home, away, connection.toString());
			updateCompleteDataCategoryChkByFuture(home, away, connection.toString());
		} catch (Exception e) {
			throw new IllegalAccessException("システムエラー");
		}

		return connection.toString();
	}

	/**
	 * 「ラウンド」を含んだ完全なdata_categoryが含まれているかをチェックする
	 * @return 「ラウンド」を含んだ完全なdata_category
	 */
	private String searchCompleteDataCategoryChk(List<DataCategoryDTO> existDto) {
		for (DataCategoryDTO dto : existDto) {
			if (dto.getDataCategory() != null && dto.getDataCategory().contains(ROUND)) {
				return dto.getDataCategory();
			}
		}
		return null;
	}

	/**
	 * 「ラウンド」を含んだ完全なdata_categoryに更新する
	 * @param home 対象試合のホームチーム名
	 * @param away 対象試合のアウェーチーム名
	 * @param dataCategory 対象試合のホームチーム名
	 */
	private int updateCompleteDataCategoryChk(String home, String away, String dataCategory) {
		return bookDataRepository.updateByDataCategory(dataCategory, home, away);
	}

	/**
	 * 「ラウンド」を含んだ完全なdata_categoryに更新する
	 * @param home 対象試合のホームチーム名
	 * @param away 対象試合のアウェーチーム名
	 * @param dataCategory 対象試合のホームチーム名
	 */
	private int updateCompleteDataCategoryChkByFuture(String home, String away, String dataCategory) {
		return futureMasterRepository.updateGameTeamCategoryByTeams(dataCategory, home, away);
	}

}