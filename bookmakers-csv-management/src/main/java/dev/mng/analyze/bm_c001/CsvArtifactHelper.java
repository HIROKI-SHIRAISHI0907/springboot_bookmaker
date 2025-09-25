package dev.mng.analyze.bm_c001;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.DataEntity;
import dev.common.util.ExecuteMainUtil;
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
	 * フラグが0のものの条件データを取得
	 * @return
	 */
	public List<StatSizeFinalizeMasterCsvEntity> getMaster() {
		List<StatSizeFinalizeMasterCsvEntity> flgData = null;
		try {
			flgData = this.statSizeFinalizeMasterRepository
					.findFlgData(STAT_SIZE_FINALIZE_FLG_0);
		} catch (Exception e) {
			throw e;
		}
		return flgData;
	}

	/**
	 * 条件データを設定するメインクラス
	 * @return
	 */
	public CsvArtifactResource getData() {
		List<StatSizeFinalizeMasterCsvEntity> flgData = getMaster();
		CsvArtifactResource csvArtifactResource = new CsvArtifactResource();
		csvArtifactResource = setOption1stNum(flgData, csvArtifactResource);
		csvArtifactResource = setOption2ndNum(flgData, csvArtifactResource);
		return csvArtifactResource;
	}

	/**
	 * フラグが0に設定されている選択肢1情報を設定する
	 * @param entities
	 * @param csvArtifactResource
	 * @return
	 */
	public CsvArtifactResource setOption1stNum(List<StatSizeFinalizeMasterCsvEntity> entities,
			CsvArtifactResource csvArtifactResource) {
		for (StatSizeFinalizeMasterCsvEntity entity : entities) {
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
		return csvArtifactResource;
	}

	/**
	 * フラグが0に設定されている選択肢2情報を設定する
	 * @param entities
	 * @param csvArtifactResource
	 * @return
	 */
	public CsvArtifactResource setOption2ndNum(List<StatSizeFinalizeMasterCsvEntity> entities,
			CsvArtifactResource csvArtifactResource) {
		List<String> countryList = new ArrayList<String>();
		List<String> leagueList = new ArrayList<String>();
		for (StatSizeFinalizeMasterCsvEntity entity : entities) {
			switch (entity.getOptionNum()) {
			// 国リーグ
			case "2": {
				String[] target = entity.getOptions().split(":");
				countryList.add(target[0]);
				leagueList.add(target[1]);
				break;
			}
			}
		}
		csvArtifactResource.setCountry(countryList);
		csvArtifactResource.setLeague(leagueList);
		return csvArtifactResource;
	}

	/**
	 * フラグ条件当てはまるか, ,異常データを削除できるか精査する
	 * @param result
	 * @param csvArtifactResource
	 * @return
	 */
	public boolean csvCondition(List<DataEntity> result, CsvArtifactResource csvArtifactResource) {
		// 条件に当てはまればそのまま素通りする処理を入れる箇所
		boolean option1stFlg = restrict1st(result, csvArtifactResource);
		boolean option2ndFlg = restrict2nd(result, csvArtifactResource);
		if (option1stFlg && option2ndFlg) {
			return true;
		}
		return false;
	}

	/**
	 * 統計データ用の条件ラッパーメソッド
	 * @param result
	 * @param csvArtifactResource
	 * @return
	 */
	public boolean statCondition(List<DataEntity> result) {
		// フラグ0
		List<StatSizeFinalizeMasterCsvEntity> flgData = getMaster();
		// 選択肢2(国リーグに関する制限付きINPUTを精査)
		CsvArtifactResource csvArtifactResource = setOption2ndNum(flgData, new CsvArtifactResource());
		return restrict2nd(result, csvArtifactResource);
	}

	/**
	 * スコアに関する制限付きINPUTを精査
	 * @param result
	 * @param csvArtifactResource
	 * @return
	 */
	private boolean restrict1st(List<DataEntity> result, CsvArtifactResource csvArtifactResource) {
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
			return true;
		}
		return true;
	}

	/**
	 * 国リーグに関する制限付きINPUTを精査
	 * @param result
	 * @param csvArtifactResource
	 * @return
	 */
	private boolean restrict2nd(List<DataEntity> result, CsvArtifactResource csvArtifactResource) {
		// 国リーグに関する制限付きINPUTを精査
		List<String> country = csvArtifactResource.getCountry();
		List<String> league = csvArtifactResource.getLeague();
		if (country != null && !country.isEmpty() && league != null && !league.isEmpty()) {
			// イタリア／セリエ A, セリエ B - プレーアウト, イングランド／プレミアリーグ
			// エールディビジ - カンファレンスリーグ：プレーオフ, プリメーラ A - アペルトゥーラ：クアルドラングラル
			// プリメーラ B - アペルトゥーラ：クアルドラングラル, スーペルリーガ - チャンピオンシップグループ
			// リーグ・アン - 降格プレーオフ, ジュピラー･プロリーグ - カンファレンスリーグ・グループ
			// リーガ 1 - アペルトゥラ, LFPB - スーパーファイナル, リーガ・ポルトガル - 降格戦
			// K リーグ 1 - チャンピオンシップグループ, WEリーグカップ - プレーオフ
			// などのリーグに付与されているものは付与分を削除
			// dataCategory
			String countryLeague = result.get(result.size() - 1).getDataCategory();
			String[] split = ExecuteMainUtil.splitLeagueInfo(countryLeague);
			String resultCountry = split[0].trim();
			String resultLeague = split[1].trim();
			for (int i = 0; i < country.size(); i++) {
				String newLeague = league.get(i);
				// スーペルリーガ - チャンピオンシップグループ
				if (league.get(i).contains("-")) {
					newLeague = newLeague.split("-")[0].trim();
					// イタリア／セリエ Aなど
				} else if (league.get(i).contains("／")) {
					newLeague = newLeague.split("／")[1].trim();
				}
				// 組み合わせが1つでも引っかかったらOK
				if (country.get(i).equals(resultCountry) && newLeague.equals(resultLeague)) {
					return true;
				}
			}
			return false;
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

	// 共通の正規化関数を用意
	private static String normalizeTimes(String times) {
		if (times == null)
			return null;
		// 特殊トークンはそのまま使う
		if (BookMakersCommonConst.FIN.equals(times)
				|| BookMakersCommonConst.HALF_TIME.equals(times)
				|| BookMakersCommonConst.FIRST_HALF_TIME.equals(times)) {
			return times;
		}
		// 45+1' や 90+3' などを "451" / "903" のような数値文字列に正規化
		return times.replace(":", "").replace("+", "").replace("'", "");
	}

	/**
	 * 異常データの削除
	 * 1. 同一時系列の削除
	 * 2. 異常な時系列の削除
	 */
	public List<DataEntity> abnormalChk(List<DataEntity> entityList) {
		if (entityList == null)
			return Collections.emptyList();

		List<DataEntity> result = new ArrayList<>();
		// 順序もわかりやすくしたいなら LinkedHashSet
		Set<String> timesSet = new LinkedHashSet<>();

		int prevMinute = -1; // 単調増加チェック用

		for (DataEntity d : entityList) {
			String raw = d.getTimes();

			// すでに FT が入っていれば、それ以降はゴミとみなして打ち切り
			if (timesSet.contains(BookMakersCommonConst.FIN))
				break;

			// 試合未実施／無効トークンはスキップ
			if (BookMakersCommonConst.POSTPONED.equals(raw)
					|| BookMakersCommonConst.SUPENDING_GAME.equals(raw)
					|| BookMakersCommonConst.REST.equals(raw)
					|| BookMakersCommonConst.WAITING_UPDATE.equals(raw)
					|| BookMakersCommonConst.WAITING_UPDATE_KANJI.equals(raw)
					|| BookMakersCommonConst.HOUR_DEAD.equals(raw)
					|| BookMakersCommonConst.ABANDONED_MATCH.equals(raw)) {
				continue;
			}

			// 正規化
			String norm = normalizeTimes(raw);

			// HT / 1H / FT 等はそのまま採用
			if (BookMakersCommonConst.HALF_TIME.equals(raw)
					|| BookMakersCommonConst.FIRST_HALF_TIME.equals(raw)
					|| BookMakersCommonConst.FIN.equals(raw)) {
				timesSet.add(norm); // ← ここで norm は特殊トークンそのまま
				continue;
			}

			// 分数（45+1' → "451" など）を数値化して単調増加チェック
			try {
				int minute = Integer.parseInt(norm);
				// 重複＋逆行を排除（同一“時刻”の2重データや巻き戻りを除去）
				if (!timesSet.contains(norm) && prevMinute < minute) {
					prevMinute = minute;
					timesSet.add(norm);
				}
			} catch (NumberFormatException ignore) {
				// もし未知の文字列が来たら採用しない（保守的に）
			}
		}

		// ★ フィルタ側も“正規化したキー”で判定するのがポイント
		// まだ拾っていない時刻の集合（true→初回、false→2回目以降）
		Set<String> remaining = new HashSet<>(timesSet);
		for (DataEntity d : entityList) {
			String norm = normalizeTimes(d.getTimes());
			// remaining.remove(norm) が true の時だけ追加（= 初めての時刻だけ通す）
			if (remaining.remove(norm)) {
				result.add(d);
			}
		}
		return result;
	}

}
