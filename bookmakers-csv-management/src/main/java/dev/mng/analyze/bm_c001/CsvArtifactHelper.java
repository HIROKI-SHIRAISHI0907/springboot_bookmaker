package dev.mng.analyze.bm_c001;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.entity.DataEntity;
import dev.mng.csvmng.CsvArtifactResource;
import dev.mng.domain.repository.StatSizeFinalizeMasterRepository;

/**
 * 特定の条件に応じたCSVを追加作成判断するためのヘルパークラス
 * @author shiraishitoshio
 *
 */
@Component
public class CsvArtifactHelper {

	/** フラグ: 0 */
	private static final String STAT_SIZE_FINALIZE_FLG_0 = "0";

	/** StatSizeFinalizeMasterRepositoryレポジトリクラス */
	@Autowired
	private StatSizeFinalizeMasterRepository statSizeFinalizeMasterRepository;

	/**
	 * 条件データを取得
	 * @return
	 */
	public CsvArtifactResource getData() {
		List<StatSizeFinalizeMasterCsvEntity> flgData = null;
		CsvArtifactResource csvArtifactResource = new CsvArtifactResource();
		try {
			flgData = this.statSizeFinalizeMasterRepository
					.findFlgData(STAT_SIZE_FINALIZE_FLG_0);
			for (StatSizeFinalizeMasterCsvEntity entity : flgData) {
				switch (entity.getOptionNum()) {
				// 0-0, 1-0など
				case "1": {
					String[] scores = entity.getOptions().split("-");
					csvArtifactResource.setHomeScore(scores[0]);
					csvArtifactResource.setAwayScore(scores[1]);
					break;
				}
				}
			}
		} catch (Exception e) {
			throw e;
		}
		return csvArtifactResource;
	}

	/**
	 * フラグ条件に当てはまるか精査する
	 * @param result
	 * @param csvArtifactResource
	 * @return
	 */
	public boolean condition(List<DataEntity> result, CsvArtifactResource csvArtifactResource) {
		// 条件に当てはまればそのまま素通りする処理を入れる箇所

		// スコアに関する制限付きINPUTを精査
		String hS = csvArtifactResource.getHomeScore();
		String aS = csvArtifactResource.getAwayScore();
		if (hS != null && aS != null) {
			Integer reqHome = Integer.parseInt(hS);
			Integer reqAway = Integer.parseInt(aS);

			// 完全一致で“含まれているか”をチェック
			Optional<DataEntity> scoreHit = findFirstScoreExact(result, reqHome, reqAway);
			if (!scoreHit.isPresent()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 引数で渡ってきたスコアの閾値以上のデータ群になっているかを判定する
	 * @param series
	 * @param homeScore
	 * @param awayScore
	 * @return
	 */
	private static Optional<DataEntity> findFirstScoreExact(
			List<DataEntity> series, Integer homeScore, Integer awayScore) {

		if ((homeScore == null) && (awayScore == null)) {
			// 条件なし → そのまま通す想定
			return Optional.ofNullable(series.isEmpty() ? null : series.get(0));
		}

		for (DataEntity e : series) {
			String hs = e.getHomeScore();
			String as = e.getAwayScore();
			if ((hs == null || "".equals(hs)) ||
					(as == null || "".equals(as))) {
				continue;
			}
			hs = hs.replace(".0", "");
			as = as.replace(".0", "");
			Integer h = Integer.parseInt(hs);
			Integer a = Integer.parseInt(as);
			boolean okH = (homeScore == null) || (h != null && h.equals(homeScore));
			boolean okA = (awayScore == null) || (a != null && a.equals(awayScore));
			if (okH && okA) {
				return Optional.of(e); // 最初に見つかった（時系列で最も早い）一致
			}
		}
		return Optional.empty();
	}

}
